package id.vern.antigravity.integrations

import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

object VercelApiClient {
    private const val BASE_URL = "https://api.vercel.com"

    fun verifyToken(token: String): CloudVerificationResult {
        return try {
            val res = CloudHttpHelper.get("$BASE_URL/v2/user", token)
            if (res.statusCode in 200..299) {
                val user = JSONObject(res.body).optJSONObject("user") ?: JSONObject(res.body)
                val username = user.optString("username").ifBlank { user.optString("name") }
                val email = user.optString("email")
                val id = user.optString("id").ifBlank { user.optString("uid") }
                CloudVerificationResult(
                    isValid = true,
                    displayName = username.ifBlank { email }.ifBlank { "Vercel User" },
                    detail = listOfNotNull(email.takeIf { it.isNotBlank() }, id.takeIf { it.isNotBlank() }).joinToString(" • "),
                    statusCode = res.statusCode,
                    message = "Verified as ${username.ifBlank { email }}"
                )
            } else {
                CloudVerificationResult(
                    isValid = false,
                    statusCode = res.statusCode,
                    message = CloudHttpHelper.extractErrorMessage(res.body, res.statusCode)
                )
            }
        } catch (e: Exception) {
            CloudVerificationResult(isValid = false, message = e.message ?: "Failed to reach Vercel")
        }
    }

    fun fetchTeams(token: String): Result<List<VercelTeamItem>> {
        return try {
            val res = CloudHttpHelper.get("$BASE_URL/v2/teams?limit=50", token)
            if (res.statusCode !in 200..299) {
                return Result.failure(Exception(CloudHttpHelper.extractErrorMessage(res.body, res.statusCode)))
            }
            Result.success(parseTeamsJson(res.body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fetchProjects(token: String, teamId: String? = null): Result<List<VercelProjectItem>> {
        return try {
            val teamQuery = teamId?.takeIf { it.isNotBlank() }?.let { "&teamId=${enc(it)}" }.orEmpty()
            val res = CloudHttpHelper.get("$BASE_URL/v9/projects?limit=50$teamQuery", token)
            if (res.statusCode !in 200..299) {
                return Result.failure(Exception(CloudHttpHelper.extractErrorMessage(res.body, res.statusCode)))
            }
            Result.success(parseProjectsJson(res.body, teamId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fetchDeployments(token: String, projectId: String, teamId: String? = null): Result<List<VercelDeploymentItem>> {
        return try {
            val teamQuery = teamId?.takeIf { it.isNotBlank() }?.let { "&teamId=${enc(it)}" }.orEmpty()
            val res = CloudHttpHelper.get(
                "$BASE_URL/v6/deployments?projectId=${enc(projectId)}&limit=10$teamQuery",
                token
            )
            if (res.statusCode !in 200..299) {
                return Result.failure(Exception(CloudHttpHelper.extractErrorMessage(res.body, res.statusCode)))
            }
            Result.success(parseDeploymentsJson(res.body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fetchProjectEnvVars(token: String, projectId: String, teamId: String? = null): Result<List<VercelEnvVarItem>> {
        return try {
            val teamQuery = teamId?.takeIf { it.isNotBlank() }?.let { "?teamId=${enc(it)}&decrypt=true" } ?: "?decrypt=true"
            val res = CloudHttpHelper.get("$BASE_URL/v9/projects/${enc(projectId)}/env$teamQuery", token)
            if (res.statusCode !in 200..299) {
                return Result.failure(Exception(CloudHttpHelper.extractErrorMessage(res.body, res.statusCode)))
            }
            Result.success(parseEnvVarsJson(res.body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun writeWorkspaceProjectJson(projectDir: File, orgId: String, projectId: String) {
        val vercelDir = File(projectDir, ".vercel").apply { mkdirs() }
        val payload = JSONObject().apply {
            put("orgId", orgId)
            put("projectId", projectId)
        }
        File(vercelDir, "project.json").writeText(payload.toString(2) + "\n", Charsets.UTF_8)
        CloudHttpHelper.ensureGitIgnoreEntries(projectDir, listOf(".vercel"))
    }

    fun triggerLatestRedeploy(
        token: String,
        projectId: String,
        projectName: String,
        teamId: String? = null
    ): Result<VercelDeploymentItem> {
        return try {
            val latest = fetchDeployments(token, projectId, teamId).getOrElse { return Result.failure(it) }
                .firstOrNull { it.uid.isNotBlank() }
                ?: return Result.failure(Exception("No existing Vercel deployment found to redeploy. Run 'vercel' CLI first."))

            val teamQuery = teamId?.takeIf { it.isNotBlank() }?.let { "?teamId=${enc(it)}" }.orEmpty()
            val body = JSONObject().apply {
                put("name", projectName)
                put("deploymentId", latest.uid)
                put("target", latest.target.ifBlank { "production" })
            }.toString()

            val res = CloudHttpHelper.postJson("$BASE_URL/v13/deployments$teamQuery", token, body)
            if (res.statusCode !in 200..299) {
                return Result.failure(Exception(CloudHttpHelper.extractErrorMessage(res.body, res.statusCode)))
            }
            val obj = JSONObject(res.body)
            Result.success(
                VercelDeploymentItem(
                    uid = obj.optString("id").ifBlank { obj.optString("uid") },
                    name = obj.optString("name").ifBlank { projectName },
                    url = obj.optString("url"),
                    state = obj.optString("readyState").ifBlank { obj.optString("status").ifBlank { "QUEUED" } },
                    target = obj.optString("target").ifBlank { "production" },
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun pullEnvToDotEnvLocal(projectDir: File, envVars: List<VercelEnvVarItem>, targetFilter: String = "development"): Int {
        val entries = LinkedHashMap<String, String>()
        envVars.forEach { item ->
            if (item.key.isBlank()) return@forEach
            if (item.targets.isEmpty() || item.targets.any { it.equals(targetFilter, ignoreCase = true) }) {
                if (item.value.isNotEmpty() || !entries.containsKey(item.key)) {
                    entries[item.key] = item.value
                }
            }
        }
        if (entries.isEmpty()) return 0
        CloudHttpHelper.upsertDotEnvKeys(File(projectDir, ".env.local"), entries)
        return entries.size
    }

    internal fun parseTeamsJson(jsonText: String): List<VercelTeamItem> {
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("teams") ?: return emptyList()
        val list = mutableListOf<VercelTeamItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            if (id.isBlank()) continue
            list.add(
                VercelTeamItem(
                    id = id,
                    slug = obj.optString("slug"),
                    name = obj.optString("name").ifBlank { obj.optString("slug").ifBlank { id } }
                )
            )
        }
        return list
    }

    internal fun parseProjectsJson(jsonText: String, teamId: String? = null): List<VercelProjectItem> {
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("projects") ?: return emptyList()
        val list = mutableListOf<VercelProjectItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val name = obj.optString("name")
            if (id.isBlank() || name.isBlank()) continue
            val prodAlias = obj.optJSONObject("targets")
                ?.optJSONObject("production")
                ?.optJSONArray("alias")
                ?.optString(0)
                .orEmpty()
            val orgId = obj.optString("accountId").ifBlank { teamId.orEmpty() }
            list.add(
                VercelProjectItem(
                    id = id,
                    name = name,
                    framework = obj.optString("framework").takeIf { it != "null" }.orEmpty(),
                    teamId = teamId?.takeIf { it.isNotBlank() },
                    orgId = orgId,
                    updatedAt = obj.optLong("updatedAt", 0L),
                    productionUrl = prodAlias
                )
            )
        }
        return list
    }

    internal fun parseDeploymentsJson(jsonText: String): List<VercelDeploymentItem> {
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("deployments") ?: return emptyList()
        val list = mutableListOf<VercelDeploymentItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val uid = obj.optString("uid")
            if (uid.isBlank()) continue
            list.add(
                VercelDeploymentItem(
                    uid = uid,
                    name = obj.optString("name"),
                    url = obj.optString("url"),
                    state = obj.optString("state").ifBlank { obj.optString("readyState") },
                    target = obj.optString("target").takeIf { it != "null" }.orEmpty(),
                    createdAt = obj.optLong("createdAt", obj.optLong("created", 0L))
                )
            )
        }
        return list
    }

    internal fun parseEnvVarsJson(jsonText: String): List<VercelEnvVarItem> {
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("envs") ?: return emptyList()
        val list = mutableListOf<VercelEnvVarItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val key = obj.optString("key")
            if (key.isBlank()) continue
            val targetsArr = obj.optJSONArray("target")
            val targets = mutableListOf<String>()
            if (targetsArr != null) {
                for (t in 0 until targetsArr.length()) {
                    val v = targetsArr.optString(t)
                    if (v.isNotBlank()) targets.add(v)
                }
            }
            list.add(
                VercelEnvVarItem(
                    id = obj.optString("id"),
                    key = key,
                    value = obj.optString("value"),
                    targets = targets,
                    type = obj.optString("type")
                )
            )
        }
        return list
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
