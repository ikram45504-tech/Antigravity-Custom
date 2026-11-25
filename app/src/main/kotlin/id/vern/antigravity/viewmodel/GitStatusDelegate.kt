package id.vern.antigravity.viewmodel

import android.content.Context
import id.vern.antigravity.data.ChangedFileItem
import id.vern.antigravity.executor.EngineExecutor
import id.vern.antigravity.executor.shellQuote
import id.vern.antigravity.integrations.ProjectIntegrationsManager
import id.vern.antigravity.oauth.GitHubAuthManager
import id.vern.antigravity.util.NetworkDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class GitRepoSummary(
    val isGitRepo: Boolean = false,
    val branch: String = "",
    val aheadCount: Int = 0,
    val behindCount: Int = 0,
    val remoteUrl: String = "",
    val branches: List<String> = emptyList()
)

data class GitActionResult(
    val success: Boolean,
    val message: String
)

class GitStatusDelegate {
    private val mutableChangedFiles = MutableStateFlow<List<ChangedFileItem>>(emptyList())
    val changedFiles: StateFlow<List<ChangedFileItem>> = mutableChangedFiles.asStateFlow()

    private val mutableDiffCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val diffCache: StateFlow<Map<String, String>> = mutableDiffCache.asStateFlow()

    private val mutableLoadingDiffs = MutableStateFlow<Set<String>>(emptySet())
    val loadingDiffs: StateFlow<Set<String>> = mutableLoadingDiffs.asStateFlow()

    private val mutableRepoSummary = MutableStateFlow(GitRepoSummary())
    val repoSummary: StateFlow<GitRepoSummary> = mutableRepoSummary.asStateFlow()

    private val mutableIsGitActionRunning = MutableStateFlow(false)
    val isGitActionRunning: StateFlow<Boolean> = mutableIsGitActionRunning.asStateFlow()

    private val mutableGitActionStatus = MutableStateFlow<GitActionResult?>(null)
    val gitActionStatus: StateFlow<GitActionResult?> = mutableGitActionStatus.asStateFlow()

    companion object {
        private val aheadRegex = Regex("""ahead\s+(\d+)""")
        private val behindRegex = Regex("""behind\s+(\d+)""")
        private val porcelainRegex = Regex("""^[ MADRCU?!]{1,2}\s+(.+)$""")

        internal fun parseBranchStatusLine(line: String): Triple<String, Int, Int> {
            val raw = line.removePrefix("##").trim()
            if (raw.isEmpty()) return Triple("", 0, 0)
            val ahead = aheadRegex.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val behind = behindRegex.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val headPart = raw.substringBefore(" [").trim()
            val branch = when {
                headPart.startsWith("No commits yet on ") -> headPart.removePrefix("No commits yet on ").trim()
                headPart.contains("...") -> headPart.substringBefore("...").trim()
                else -> headPart
            }
            return Triple(branch, ahead, behind)
        }
    }

    suspend fun fetchGitChangedFiles(context: Context, workspacePath: String?): List<ChangedFileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ChangedFileItem>()
        val rootDir = workspacePath?.let { File(it) }
        if (rootDir != null && rootDir.exists() && rootDir.isDirectory && File(rootDir, ".git").exists()) {
            var branchName = ""
            var ahead = 0
            var behind = 0
            try {
                EngineExecutor.executeCommand(context, "git status -sb --porcelain 2>/dev/null", workingDir = rootDir) { line, _ ->
                    val trimmed = line.trimEnd()
                    if (trimmed.startsWith("##")) {
                        val (b, a, bh) = parseBranchStatusLine(trimmed)
                        branchName = b
                        ahead = a
                        behind = bh
                    } else {
                        val match = porcelainRegex.find(trimmed)
                        if (match != null) {
                            val rawPath = NetworkDownloader.cleanPath(match.groupValues[1].trim())
                            val f = File(rawPath)
                            val name = f.name
                            val rel = f.parent ?: ""
                            list.add(ChangedFileItem(name = name, relativePath = rel, isKotlin = name.endsWith(".kt") || name.endsWith(".kts")))
                        }
                    }
                }
                val branches = mutableListOf<String>()
                EngineExecutor.executeCommand(context, "git branch --format='%(refname:short)' 2>/dev/null", workingDir = rootDir) { line, _ ->
                    val b = line.trim()
                    if (b.isNotEmpty()) branches.add(b)
                }
                val remoteSb = StringBuilder()
                EngineExecutor.executeCommand(context, "git remote get-url origin 2>/dev/null", workingDir = rootDir) { line, _ ->
                    if (remoteSb.isEmpty() && line.isNotBlank()) remoteSb.append(line.trim())
                }
                mutableRepoSummary.value = GitRepoSummary(
                    isGitRepo = true,
                    branch = branchName.ifBlank { branches.firstOrNull().orEmpty() },
                    aheadCount = ahead,
                    behindCount = behind,
                    remoteUrl = remoteSb.toString(),
                    branches = branches.distinct()
                )
            } catch (_: Exception) {
                mutableRepoSummary.value = GitRepoSummary(isGitRepo = true)
            }
        } else {
            mutableRepoSummary.value = GitRepoSummary(isGitRepo = false)
        }
        mutableChangedFiles.value = list
        list
    }

    suspend fun fetchGitDiff(context: Context, workspacePath: String?, relativePath: String): String = withContext(Dispatchers.IO) {
        val cached = mutableDiffCache.value[relativePath]
        if (cached != null) return@withContext cached

        mutableLoadingDiffs.value = mutableLoadingDiffs.value + relativePath
        val root = workspacePath?.let { File(it) }
        if (root == null || !root.exists()) {
            mutableLoadingDiffs.value = mutableLoadingDiffs.value - relativePath
            return@withContext ""
        }
        val diffBuilder = StringBuilder()
        try {
            EngineExecutor.executeCommand(
                context,
                "git diff HEAD -- ${relativePath.shellQuote()} 2>/dev/null",
                workingDir = root
            ) { line, _ ->
                diffBuilder.append(line).append("\n")
            }
        } catch (_: Exception) {}
        val diff = diffBuilder.toString()
        val result = if (diff.isNotBlank()) {
            diff
        } else {
            val targetF = File(root, relativePath)
            if (targetF.exists() && targetF.isFile) {
                targetF.readLines().take(40).joinToString("\n")
            } else {
                "[File modified or newly created]"
            }
        }
        mutableDiffCache.value = mutableDiffCache.value + (relativePath to result)
        mutableLoadingDiffs.value = mutableLoadingDiffs.value - relativePath
        result
    }

    suspend fun commitChanges(
        context: Context,
        workspacePath: String?,
        message: String,
        stageAll: Boolean = true
    ): GitActionResult {
        val cleanMsg = message.trim()
        if (cleanMsg.isBlank()) {
            val err = GitActionResult(false, "Commit message cannot be empty.")
            mutableGitActionStatus.value = err
            return err
        }
        val cmd = if (stageAll) {
            "git add -A && git commit -m ${cleanMsg.shellQuote()}"
        } else {
            "git commit -m ${cleanMsg.shellQuote()}"
        }
        return runGitAction(context, workspacePath, cmd, "Committed changes")
    }

    private fun checkGitHubBindingError(context: Context, workspacePath: String?): GitActionResult? {
        val rootDir = workspacePath?.let { File(it) } ?: return null
        if (!rootDir.isDirectory) return null
        val isGitHubRemote = GitHubAuthManager.detectGitHubRemoteRepo(rootDir) != null
        if (!isGitHubRemote) return null
        val bound = ProjectIntegrationsManager.getBindingForProject(context, rootDir.absolutePath)?.github
        return if (bound == null) {
            GitActionResult(
                false,
                "No GitHub account linked to this project. Link an account in Settings → GitHub."
            )
        } else null
    }

    suspend fun pullChanges(context: Context, workspacePath: String?): GitActionResult {
        checkGitHubBindingError(context, workspacePath)?.let { err ->
            mutableGitActionStatus.value = err
            return err
        }
        return runGitAction(context, workspacePath, "git pull --rebase", "Pulled latest changes")
    }

    suspend fun pushChanges(context: Context, workspacePath: String?): GitActionResult {
        checkGitHubBindingError(context, workspacePath)?.let { err ->
            mutableGitActionStatus.value = err
            return err
        }
        return runGitAction(context, workspacePath, "git push", "Pushed changes to remote")
    }

    suspend fun checkoutBranch(
        context: Context,
        workspacePath: String?,
        branchName: String,
        createNew: Boolean = false
    ): GitActionResult {
        val cleanBranch = branchName.trim()
        if (cleanBranch.isBlank()) {
            val err = GitActionResult(false, "Branch name cannot be empty.")
            mutableGitActionStatus.value = err
            return err
        }
        val cmd = if (createNew) {
            "git checkout -b ${cleanBranch.shellQuote()}"
        } else {
            "git checkout ${cleanBranch.shellQuote()}"
        }
        return runGitAction(context, workspacePath, cmd, "Switched to $cleanBranch")
    }

    suspend fun cloneRepository(
        context: Context,
        cloneUrl: String,
        destinationParentDir: File,
        repoName: String,
        gitEnvOverride: Map<String, String> = emptyMap()
    ): Pair<GitActionResult, File?> = withContext(Dispatchers.IO) {
        val safeName = repoName.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        if (safeName.isBlank()) {
            val res = GitActionResult(false, "Invalid repository folder name.")
            mutableGitActionStatus.value = res
            return@withContext res to null
        }
        destinationParentDir.mkdirs()
        val targetDir = File(destinationParentDir, safeName)
        if (targetDir.exists()) {
            val res = GitActionResult(false, "Folder already exists: ${targetDir.absolutePath}")
            mutableGitActionStatus.value = res
            return@withContext res to null
        }
        mutableIsGitActionRunning.value = true
        val output = StringBuilder()
        val exitCode = try {
            EngineExecutor.executeCommand(
                context = context,
                command = "git clone -- ${cloneUrl.shellQuote()} ${targetDir.absolutePath.shellQuote()}",
                workingDir = destinationParentDir,
                envOverrides = gitEnvOverride
            ) { line, _ ->
                if (line.isNotBlank()) output.appendLine(line)
            }
        } catch (e: Exception) {
            output.append(e.message ?: "Clone failed")
            -1
        } finally {
            mutableIsGitActionRunning.value = false
        }
        val result = if (exitCode == 0 && targetDir.isDirectory) {
            GitActionResult(true, "Cloned $safeName")
        } else {
            val msg = output.toString().trim().lines().lastOrNull()?.take(180) ?: "git clone failed (code $exitCode)"
            GitActionResult(false, msg)
        }
        mutableGitActionStatus.value = result
        result to (if (result.success) targetDir else null)
    }

    private suspend fun runGitAction(
        context: Context,
        workspacePath: String?,
        command: String,
        successMessage: String
    ): GitActionResult = withContext(Dispatchers.IO) {
        val rootDir = workspacePath?.let { File(it) }
        if (rootDir == null || !rootDir.isDirectory || !File(rootDir, ".git").exists()) {
            val err = GitActionResult(false, "Active workspace is not a Git repository.")
            mutableGitActionStatus.value = err
            return@withContext err
        }
        mutableIsGitActionRunning.value = true
        val output = StringBuilder()
        val exitCode = try {
            EngineExecutor.executeCommand(context, command, workingDir = rootDir) { line, _ ->
                if (line.isNotBlank()) output.appendLine(line)
            }
        } catch (e: Exception) {
            output.append(e.message ?: "Git error")
            -1
        } finally {
            mutableIsGitActionRunning.value = false
        }
        clearDiffCache()
        fetchGitChangedFiles(context, workspacePath)
        val outText = output.toString().trim()
        val result = if (exitCode == 0) {
            GitActionResult(true, outText.lines().lastOrNull()?.takeIf { it.isNotBlank() } ?: successMessage)
        } else {
            GitActionResult(false, outText.lines().takeLast(2).joinToString(" ").take(200).ifBlank { "Git failed (code $exitCode)" })
        }
        mutableGitActionStatus.value = result
        result
    }

    fun clearGitActionStatus() {
        mutableGitActionStatus.value = null
    }

    fun clearDiffCache() {
        mutableDiffCache.value = emptyMap()
        mutableLoadingDiffs.value = emptySet()
    }
}
