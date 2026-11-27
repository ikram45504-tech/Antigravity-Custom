package id.vern.antigravity.integrations

import org.json.JSONArray
import java.io.File
import java.net.URLEncoder

object SupabaseApiClient {
    private const val BASE_URL = "https://api.supabase.com/v1"

    fun verifyToken(token: String): CloudVerificationResult {
        return verifyTokenWithFetcher { url -> CloudHttpHelper.get(url, token) }
    }

    internal fun verifyTokenWithFetcher(
        get: (String) -> HttpResponseResult
    ): CloudVerificationResult {
        return try {
            val res = get("$BASE_URL/organizations")
            if (res.statusCode in 200..299) {
                val orgs = parseOrganizationsJson(res.body)
                val label = orgs.firstOrNull()?.name ?: "Supabase Account"
                CloudVerificationResult(
                    isValid = true,
                    displayName = label,
                    detail = "${orgs.size} organization(s)",
                    statusCode = res.statusCode,
                    message = "Verified (${orgs.size} organization${if (orgs.size == 1) "" else "s"})"
                )
            } else if (res.statusCode == 403) {
                // Project-scoped Supabase PATs (sbp_fc...) often omit organizations_read while allowing /projects.
                val projRes = get("$BASE_URL/projects")
                if (projRes.statusCode in 200..299) {
                    val projects = parseProjectsJson(projRes.body)
                    val label = projects.firstOrNull()?.name ?: "Supabase Account"
                    CloudVerificationResult(
                        isValid = true,
                        displayName = label,
                        detail = "${projects.size} project(s)",
                        statusCode = projRes.statusCode,
                        message = "Verified (${projects.size} project${if (projects.size == 1) "" else "s"})"
                    )
                } else {
                    CloudVerificationResult(
                        isValid = false,
                        statusCode = projRes.statusCode,
                        message = CloudHttpHelper.extractErrorMessage(projRes.body, projRes.statusCode)
                    )
                }
            } else {
                CloudVerificationResult(
                    isValid = false,
                    statusCode = res.statusCode,
                    message = CloudHttpHelper.extractErrorMessage(res.body, res.statusCode)
                )
            }
        } catch (e: Exception) {
            CloudVerificationResult(isValid = false, message = e.message ?: "Failed to reach Supabase")
        }
    }

    fun fetchProjects(token: String): Result<List<SupabaseProjectItem>> {
        return try {
            val res = CloudHttpHelper.get("$BASE_URL/projects", token)
            if (res.statusCode !in 200..299) {
                return Result.failure(Exception(CloudHttpHelper.extractErrorMessage(res.body, res.statusCode)))
            }
            Result.success(parseProjectsJson(res.body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fetchProjectApiKeys(token: String, projectRef: String): Result<List<SupabaseApiKeyItem>> {
        return try {
            val res = CloudHttpHelper.get("$BASE_URL/projects/${URLEncoder.encode(projectRef, "UTF-8")}/api-keys", token)
            if (res.statusCode !in 200..299) {
                return Result.failure(Exception(CloudHttpHelper.extractErrorMessage(res.body, res.statusCode)))
            }
            Result.success(parseApiKeysJson(res.body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun writeSupabaseEnvToWorkspace(
        projectDir: File,
        apiUrl: String,
        anonKey: String,
        serviceRoleKey: String? = null
    ) {
        val entries = LinkedHashMap<String, String>()
        if (apiUrl.isNotBlank()) {
            entries["NEXT_PUBLIC_SUPABASE_URL"] = apiUrl
            entries["VITE_SUPABASE_URL"] = apiUrl
            entries["SUPABASE_URL"] = apiUrl
        }
        if (anonKey.isNotBlank()) {
            entries["NEXT_PUBLIC_SUPABASE_ANON_KEY"] = anonKey
            entries["VITE_SUPABASE_ANON_KEY"] = anonKey
            entries["SUPABASE_ANON_KEY"] = anonKey
        }
        if (!serviceRoleKey.isNullOrBlank()) {
            entries["SUPABASE_SERVICE_ROLE_KEY"] = serviceRoleKey
        }
        CloudHttpHelper.upsertDotEnvKeys(File(projectDir, ".env.local"), entries)
    }

    internal fun parseOrganizationsJson(jsonText: String): List<SupabaseOrgItem> {
        val arr = JSONArray(jsonText)
        val list = mutableListOf<SupabaseOrgItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            if (id.isBlank()) continue
            list.add(
                SupabaseOrgItem(
                    id = id,
                    name = obj.optString("name").ifBlank { id }
                )
            )
        }
        return list
    }

    internal fun parseProjectsJson(jsonText: String): List<SupabaseProjectItem> {
        val arr = JSONArray(jsonText)
        val list = mutableListOf<SupabaseProjectItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val ref = obj.optString("ref").ifBlank { id }
            if (ref.isBlank()) continue
            list.add(
                SupabaseProjectItem(
                    id = id.ifBlank { ref },
                    ref = ref,
                    name = obj.optString("name").ifBlank { ref },
                    organizationId = obj.optString("organization_id"),
                    region = obj.optString("region"),
                    status = obj.optString("status"),
                    apiUrl = "https://$ref.supabase.co"
                )
            )
        }
        return list
    }

    internal fun parseApiKeysJson(jsonText: String): List<SupabaseApiKeyItem> {
        val arr = JSONArray(jsonText)
        val list = mutableListOf<SupabaseApiKeyItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name")
            val key = obj.optString("api_key")
            if (name.isBlank() || key.isBlank()) continue
            list.add(SupabaseApiKeyItem(name = name, apiKey = key))
        }
        return list
    }
}
