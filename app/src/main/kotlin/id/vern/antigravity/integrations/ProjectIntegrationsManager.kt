package id.vern.antigravity.integrations

import android.content.Context
import id.vern.antigravity.oauth.EncryptedStoreCrypto
import id.vern.antigravity.service.McpConfigService
import id.vern.antigravity.service.McpServerItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages strict per-project bindings for GitHub, Vercel, Supabase, and Cloudflare Pages.
 * Each project path stores independent bindings; unlinked projects receive no tokens or Git creds.
 */
object ProjectIntegrationsManager {
    private const val STORE_FILE_NAME = "project_integrations.json"
    private const val KEYSTORE_ALIAS = "antigravity_project_bindings_aes_v1"

    const val MCP_KEY_VERCEL = "vercel-project"
    const val MCP_KEY_SUPABASE = "supabase-project"
    const val MCP_KEY_CLOUDFLARE = "cloudflare-pages-project"

    private val lock = Any()

    private fun getStoreFile(context: Context): File {
        val dir = File(context.filesDir, ".gemini")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, STORE_FILE_NAME)
    }

    internal fun normalizePath(rawPath: String?): String {
        val trimmed = rawPath?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        return runCatching { File(trimmed).canonicalPath }
            .getOrElse { File(trimmed).absolutePath }
            .trimEnd('/')
            .ifEmpty { "/" }
    }

    internal fun findBindingForPath(
        bindings: List<ProjectIntegrationBinding>,
        workingDirPath: String?
    ): ProjectIntegrationBinding? {
        val normalizedTarget = normalizePath(workingDirPath)
        if (normalizedTarget.isEmpty()) return null
        val exact = bindings.firstOrNull { normalizePath(it.projectPath) == normalizedTarget }
        if (exact != null) return exact

        // Match nearest registered parent directory so sub-folder commands inherit their project binding.
        return bindings
            .map { it to normalizePath(it.projectPath) }
            .filter { (_, p) ->
                p.isNotEmpty() && (normalizedTarget.startsWith("$p/") || (p == "/" && normalizedTarget.startsWith("/")))
            }
            .maxByOrNull { (_, p) -> p.length }
            ?.first
    }

    internal fun parseBindingsJson(jsonText: String): List<ProjectIntegrationBinding> {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) return emptyList()
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("projects") ?: return emptyList()
        val list = mutableListOf<ProjectIntegrationBinding>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val path = normalizePath(obj.optString("projectPath"))
            if (path.isEmpty()) continue

            val ghObj = obj.optJSONObject("github")
            val github = ghObj?.let {
                val login = it.optString("accountLogin").trim()
                if (login.isBlank()) null else GitHubProjectBinding(
                    accountLogin = login,
                    repoFullName = it.optString("repoFullName"),
                    repoCloneUrl = it.optString("repoCloneUrl")
                )
            }

            val vObj = obj.optJSONObject("vercel")
            val vercel = vObj?.let {
                val accId = it.optString("accountId").trim()
                val projId = it.optString("projectId").trim()
                if (accId.isBlank() || projId.isBlank()) null else VercelProjectBinding(
                    accountId = accId,
                    teamId = it.optString("teamId"),
                    orgId = it.optString("orgId"),
                    projectId = projId,
                    projectName = it.optString("projectName").ifBlank { projId },
                    injectEnv = it.optBoolean("injectEnv", true),
                    syncMcp = it.optBoolean("syncMcp", false)
                )
            }

            val sObj = obj.optJSONObject("supabase")
            val supabase = sObj?.let {
                val accId = it.optString("accountId").trim()
                val ref = it.optString("projectRef").trim()
                if (accId.isBlank() || ref.isBlank()) null else SupabaseProjectBinding(
                    accountId = accId,
                    organizationId = it.optString("organizationId"),
                    projectRef = ref,
                    projectName = it.optString("projectName").ifBlank { ref },
                    apiUrl = it.optString("apiUrl").ifBlank { "https://$ref.supabase.co" },
                    anonKey = it.optString("anonKey"),
                    injectEnv = it.optBoolean("injectEnv", true),
                    syncMcp = it.optBoolean("syncMcp", true)
                )
            }

            val cfObj = obj.optJSONObject("cloudflare")
            val cloudflare = cfObj?.let {
                val accId = it.optString("accountId").trim()
                val cfAccId = it.optString("cfAccountId").trim()
                val pagesName = it.optString("pagesProjectName").trim()
                if (accId.isBlank() || cfAccId.isBlank() || pagesName.isBlank()) null else CloudflarePagesBinding(
                    accountId = accId,
                    cfAccountId = cfAccId,
                    cfAccountName = it.optString("cfAccountName"),
                    pagesProjectName = pagesName,
                    subdomain = it.optString("subdomain"),
                    injectEnv = it.optBoolean("injectEnv", true),
                    syncMcp = it.optBoolean("syncMcp", false)
                )
            }

            val binding = ProjectIntegrationBinding(
                projectPath = path,
                github = github,
                vercel = vercel,
                supabase = supabase,
                cloudflare = cloudflare
            )
            if (binding.linkedCount > 0) {
                list.add(binding)
            }
        }
        return list
    }

    internal fun serializeBindingsJson(bindings: List<ProjectIntegrationBinding>): String {
        val arr = JSONArray()
        bindings.filter { it.linkedCount > 0 }.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("projectPath", normalizePath(b.projectPath))
                    b.github?.let { gh ->
                        put("github", JSONObject().apply {
                            put("accountLogin", gh.accountLogin)
                            put("repoFullName", gh.repoFullName)
                            put("repoCloneUrl", gh.repoCloneUrl)
                        })
                    }
                    b.vercel?.let { v ->
                        put("vercel", JSONObject().apply {
                            put("accountId", v.accountId)
                            put("teamId", v.teamId)
                            put("orgId", v.orgId)
                            put("projectId", v.projectId)
                            put("projectName", v.projectName)
                            put("injectEnv", v.injectEnv)
                            put("syncMcp", v.syncMcp)
                        })
                    }
                    b.supabase?.let { s ->
                        put("supabase", JSONObject().apply {
                            put("accountId", s.accountId)
                            put("organizationId", s.organizationId)
                            put("projectRef", s.projectRef)
                            put("projectName", s.projectName)
                            put("apiUrl", s.apiUrl)
                            put("anonKey", s.anonKey)
                            put("injectEnv", s.injectEnv)
                            put("syncMcp", s.syncMcp)
                        })
                    }
                    b.cloudflare?.let { cf ->
                        put("cloudflare", JSONObject().apply {
                            put("accountId", cf.accountId)
                            put("cfAccountId", cf.cfAccountId)
                            put("cfAccountName", cf.cfAccountName)
                            put("pagesProjectName", cf.pagesProjectName)
                            put("subdomain", cf.subdomain)
                            put("injectEnv", cf.injectEnv)
                            put("syncMcp", cf.syncMcp)
                        })
                    }
                }
            )
        }
        return JSONObject().apply { put("projects", arr) }.toString()
    }

    private fun readAllInternal(context: Context): List<ProjectIntegrationBinding> {
        val file = getStoreFile(context)
        if (!file.isFile || file.length() == 0L) return emptyList()
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
        val plain = EncryptedStoreCrypto.decryptPayload(KEYSTORE_ALIAS, raw) ?: return emptyList()
        val parsed = parseBindingsJson(plain)
        if (!EncryptedStoreCrypto.isEncrypted(raw) && parsed.isNotEmpty()) {
            writeAllInternal(context, parsed)
        }
        return parsed
    }

    private fun writeAllInternal(context: Context, bindings: List<ProjectIntegrationBinding>) {
        val file = getStoreFile(context)
        val nonEmpty = bindings.filter { it.linkedCount > 0 }
        if (nonEmpty.isEmpty()) {
            if (file.exists()) file.delete()
            return
        }
        file.parentFile?.mkdirs()
        val json = serializeBindingsJson(nonEmpty)
        file.writeText(EncryptedStoreCrypto.encryptPayload(KEYSTORE_ALIAS, json), Charsets.UTF_8)
        EncryptedStoreCrypto.applyOwnerOnlyPermissions(file)
    }

    fun getAllBindings(context: Context): List<ProjectIntegrationBinding> = synchronized(lock) {
        readAllInternal(context)
    }

    fun getBindingForProject(context: Context, projectPath: String?): ProjectIntegrationBinding? = synchronized(lock) {
        findBindingForPath(readAllInternal(context), projectPath)
    }

    fun getExactBinding(context: Context, projectPath: String?): ProjectIntegrationBinding {
        val norm = normalizePath(projectPath)
        if (norm.isEmpty()) return ProjectIntegrationBinding(projectPath = "")
        return synchronized(lock) {
            readAllInternal(context).firstOrNull { normalizePath(it.projectPath) == norm }
                ?: ProjectIntegrationBinding(projectPath = norm)
        }
    }

    private fun updateProjectBinding(
        context: Context,
        projectPath: String,
        transform: (ProjectIntegrationBinding) -> ProjectIntegrationBinding
    ): ProjectIntegrationBinding? {
        val norm = normalizePath(projectPath)
        if (norm.isEmpty()) return null
        return synchronized(lock) {
            val all = readAllInternal(context).toMutableList()
            val idx = all.indexOfFirst { normalizePath(it.projectPath) == norm }
            val current = if (idx >= 0) all[idx] else ProjectIntegrationBinding(projectPath = norm)
            val updated = transform(current).copy(projectPath = norm)
            if (idx >= 0) {
                if (updated.linkedCount > 0) all[idx] = updated else all.removeAt(idx)
            } else if (updated.linkedCount > 0) {
                all.add(updated)
            }
            writeAllInternal(context, all)
            updated
        }
    }

    fun bindGitHub(
        context: Context,
        projectPath: String,
        accountLogin: String,
        repoFullName: String = "",
        repoCloneUrl: String = ""
    ) {
        updateProjectBinding(context, projectPath) {
            it.copy(
                github = GitHubProjectBinding(
                    accountLogin = accountLogin.trim(),
                    repoFullName = repoFullName.trim(),
                    repoCloneUrl = repoCloneUrl.trim()
                )
            )
        }
    }

    fun unbindGitHub(context: Context, projectPath: String) {
        updateProjectBinding(context, projectPath) { it.copy(github = null) }
    }

    fun bindVercel(context: Context, projectPath: String, binding: VercelProjectBinding) {
        updateProjectBinding(context, projectPath) { it.copy(vercel = binding) }
    }

    fun unbindVercel(context: Context, projectPath: String) {
        updateProjectBinding(context, projectPath) { it.copy(vercel = null) }
    }

    fun bindSupabase(context: Context, projectPath: String, binding: SupabaseProjectBinding) {
        updateProjectBinding(context, projectPath) { it.copy(supabase = binding) }
    }

    fun unbindSupabase(context: Context, projectPath: String) {
        updateProjectBinding(context, projectPath) { it.copy(supabase = null) }
    }

    fun bindCloudflare(context: Context, projectPath: String, binding: CloudflarePagesBinding) {
        updateProjectBinding(context, projectPath) { it.copy(cloudflare = binding) }
    }

    fun unbindCloudflare(context: Context, projectPath: String) {
        updateProjectBinding(context, projectPath) { it.copy(cloudflare = null) }
    }

    fun removeProjectBindings(context: Context, projectPath: String) {
        val norm = normalizePath(projectPath)
        if (norm.isEmpty()) return
        synchronized(lock) {
            val remaining = readAllInternal(context).filterNot { normalizePath(it.projectPath) == norm }
            writeAllInternal(context, remaining)
        }
    }

    /**
     * When a cloud account is removed from the vault, clean up any project bindings referencing it.
     */
    fun removeBindingsForCloudAccount(context: Context, accountId: String) {
        synchronized(lock) {
            val updated = readAllInternal(context).mapNotNull { b ->
                val cleaned = b.copy(
                    vercel = b.vercel?.takeUnless { it.accountId == accountId },
                    supabase = b.supabase?.takeUnless { it.accountId == accountId },
                    cloudflare = b.cloudflare?.takeUnless { it.accountId == accountId }
                )
                cleaned.takeIf { it.linkedCount > 0 }
            }
            writeAllInternal(context, updated)
        }
    }

    /**
     * When a GitHub account is removed from the vault, clean up project bindings referencing it.
     */
    fun removeBindingsForGitHubLogin(context: Context, login: String) {
        synchronized(lock) {
            val updated = readAllInternal(context).mapNotNull { b ->
                val cleaned = b.copy(
                    github = b.github?.takeUnless { it.accountLogin.equals(login, ignoreCase = true) }
                )
                cleaned.takeIf { it.linkedCount > 0 }
            }
            writeAllInternal(context, updated)
        }
    }

    internal fun buildRuntimeEnvFromBinding(
        binding: ProjectIntegrationBinding?,
        accountsById: Map<String, CloudAccount>
    ): Map<String, String> {
        if (binding == null) return emptyMap()
        val env = LinkedHashMap<String, String>()

        binding.vercel?.takeIf { it.injectEnv }?.let { v ->
            val acc = accountsById[v.accountId]
            if (acc != null && acc.provider == CloudProvider.VERCEL && acc.token.isNotBlank()) {
                env["VERCEL_TOKEN"] = acc.token
                env["VERCEL_PROJECT_ID"] = v.projectId
                val effectiveOrg = v.orgId.ifBlank { v.teamId }
                if (effectiveOrg.isNotBlank()) {
                    env["VERCEL_ORG_ID"] = effectiveOrg
                }
            }
        }

        binding.supabase?.takeIf { it.injectEnv }?.let { s ->
            val acc = accountsById[s.accountId]
            if (acc != null && acc.provider == CloudProvider.SUPABASE && acc.token.isNotBlank()) {
                env["SUPABASE_ACCESS_TOKEN"] = acc.token
                env["SUPABASE_PROJECT_REF"] = s.projectRef
                if (s.apiUrl.isNotBlank()) {
                    env["SUPABASE_URL"] = s.apiUrl
                    env["VITE_SUPABASE_URL"] = s.apiUrl
                }
                if (s.anonKey.isNotBlank()) {
                    env["SUPABASE_ANON_KEY"] = s.anonKey
                    env["VITE_SUPABASE_ANON_KEY"] = s.anonKey
                }
            }
        }

        binding.cloudflare?.takeIf { it.injectEnv }?.let { cf ->
            val acc = accountsById[cf.accountId]
            if (acc != null && acc.provider == CloudProvider.CLOUDFLARE && acc.token.isNotBlank()) {
                env["CLOUDFLARE_API_TOKEN"] = acc.token
                env["CLOUDFLARE_ACCOUNT_ID"] = cf.cfAccountId
                env["CF_PAGES_PROJECT_NAME"] = cf.pagesProjectName
            }
        }

        return env
    }

    fun buildRuntimeEnv(context: Context, workingDir: File?): Map<String, String> {
        val dirPath = workingDir?.absolutePath ?: return emptyMap()
        val binding = getBindingForProject(context, dirPath) ?: return emptyMap()
        val accountsById = CloudAccountVault.getAccounts(context).associateBy { it.id }
        return buildRuntimeEnvFromBinding(binding, accountsById)
    }

    internal fun buildManagedMcpServersFromBinding(
        binding: ProjectIntegrationBinding?,
        accountsById: Map<String, CloudAccount>
    ): Map<String, McpServerItem?> {
        val updates = LinkedHashMap<String, McpServerItem?>()

        val sb = binding?.supabase?.takeIf { it.syncMcp }
        val sbAcc = sb?.let { accountsById[it.accountId] }?.takeIf { it.provider == CloudProvider.SUPABASE }
        updates[MCP_KEY_SUPABASE] = if (sb != null && sbAcc != null) {
            McpServerItem(
                name = MCP_KEY_SUPABASE,
                url = "https://mcp.supabase.com/mcp?project_ref=${sb.projectRef}",
                headers = mapOf("Authorization" to "Bearer ${sbAcc.token}"),
                source = "Project",
                isRemote = true,
                isEnabled = true
            )
        } else {
            null
        }

        val vb = binding?.vercel?.takeIf { it.syncMcp }
        val vbAcc = vb?.let { accountsById[it.accountId] }?.takeIf { it.provider == CloudProvider.VERCEL }
        updates[MCP_KEY_VERCEL] = if (vb != null && vbAcc != null) {
            McpServerItem(
                name = MCP_KEY_VERCEL,
                url = "https://mcp.vercel.com",
                headers = mapOf("Authorization" to "Bearer ${vbAcc.token}"),
                source = "Project",
                isRemote = true,
                isEnabled = true
            )
        } else {
            null
        }

        val cf = binding?.cloudflare?.takeIf { it.syncMcp }
        val cfAcc = cf?.let { accountsById[it.accountId] }?.takeIf { it.provider == CloudProvider.CLOUDFLARE }
        updates[MCP_KEY_CLOUDFLARE] = if (cf != null && cfAcc != null) {
            McpServerItem(
                name = MCP_KEY_CLOUDFLARE,
                url = "https://bindings.mcp.cloudflare.com/mcp",
                headers = mapOf("Authorization" to "Bearer ${cfAcc.token}"),
                source = "Project",
                isRemote = true,
                isEnabled = true
            )
        } else {
            null
        }

        return updates
    }

    suspend fun syncActiveWorkspaceMcp(context: Context, workspacePath: String?) {
        val binding = getBindingForProject(context, workspacePath)
        val accountsById = CloudAccountVault.getAccounts(context).associateBy { it.id }
        val managed = buildManagedMcpServersFromBinding(binding, accountsById)
        McpConfigService.upsertManagedServers(context, managed)
    }
}
