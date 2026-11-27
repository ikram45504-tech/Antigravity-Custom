package id.vern.antigravity.integrations

import org.json.JSONObject
import java.net.URLEncoder

object CloudflareApiClient {
    private const val BASE_URL = "https://api.cloudflare.com/client/v4"

    fun verifyToken(token: String): CloudVerificationResult {
        return try {
            val res = CloudHttpHelper.get("$BASE_URL/user/tokens/verify", token)
            if (res.statusCode in 200..299) {
                val root = JSONObject(res.body)
                val success = root.optBoolean("success", false)
                val resultObj = root.optJSONObject("result")
                val status = resultObj?.optString("status").orEmpty()
                if (success) {
                    val accounts = fetchAccounts(token).getOrDefault(emptyList())
                    val firstAcc = accounts.firstOrNull()
                    CloudVerificationResult(
                        isValid = true,
                        displayName = firstAcc?.name ?: "Cloudflare Token",
                        detail = listOfNotNull(
                            status.takeIf { it.isNotBlank() }?.let { "Status: $it" },
                            if (accounts.isNotEmpty()) "${accounts.size} account(s)" else null
                        ).joinToString(" • "),
                        statusCode = res.statusCode,
                        message = "Verified (${status.ifBlank { "active" }})"
                    )
                } else {
                    CloudVerificationResult(
                        isValid = false,
                        statusCode = res.statusCode,
                        message = CloudHttpHelper.extractErrorMessage(res.body, res.statusCode)
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
            CloudVerificationResult(isValid = false, message = e.message ?: "Failed to reach Cloudflare")
        }
    }

    fun fetchAccounts(token: String): Result<List<CloudflareAccountItem>> {
        return try {
            val res = CloudHttpHelper.get("$BASE_URL/accounts?per_page=50", token)
            if (res.statusCode !in 200..299) {
                return Result.failure(Exception(CloudHttpHelper.extractErrorMessage(res.body, res.statusCode)))
            }
            Result.success(parseAccountsJson(res.body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fetchPagesProjects(token: String, accountId: String): Result<List<CloudflarePagesProjectItem>> {
        return try {
            val allProjects = mutableListOf<CloudflarePagesProjectItem>()
            var page = 1
            val maxPages = 20
            while (page <= maxPages) {
                val res = CloudHttpHelper.get(
                    "$BASE_URL/accounts/${enc(accountId)}/pages/projects?per_page=10&page=$page",
                    token
                )
                if (res.statusCode !in 200..299) {
                    return Result.failure(Exception(CloudHttpHelper.extractErrorMessage(res.body, res.statusCode)))
                }
                allProjects.addAll(parsePagesProjectsJson(res.body, accountId))
                val totalPages = JSONObject(res.body).optJSONObject("result_info")?.optInt("total_pages", 1) ?: 1
                if (page >= totalPages) break
                page++
            }
            Result.success(allProjects)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fetchPagesDeployments(
        token: String,
        accountId: String,
        projectName: String
    ): Result<List<CloudflarePagesDeploymentItem>> {
        return try {
            val res = CloudHttpHelper.get(
                "$BASE_URL/accounts/${enc(accountId)}/pages/projects/${enc(projectName)}/deployments?per_page=10",
                token
            )
            if (res.statusCode !in 200..299) {
                return Result.failure(Exception(CloudHttpHelper.extractErrorMessage(res.body, res.statusCode)))
            }
            Result.success(parsePagesDeploymentsJson(res.body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun triggerPagesDeployment(
        token: String,
        accountId: String,
        projectName: String
    ): Result<CloudflarePagesDeploymentItem> {
        return try {
            val res = CloudHttpHelper.postJson(
                "$BASE_URL/accounts/${enc(accountId)}/pages/projects/${enc(projectName)}/deployments",
                token,
                "{}"
            )
            if (res.statusCode !in 200..299) {
                return Result.failure(Exception(CloudHttpHelper.extractErrorMessage(res.body, res.statusCode)))
            }
            val resultObj = JSONObject(res.body).optJSONObject("result")
                ?: return Result.failure(Exception("Unexpected Cloudflare response"))
            val branch = resultObj.optJSONObject("deployment_trigger")
                ?.optJSONObject("metadata")
                ?.optString("branch")
                .orEmpty()
            val stageStatus = resultObj.optJSONObject("latest_stage")?.optString("status").orEmpty()
            Result.success(
                CloudflarePagesDeploymentItem(
                    id = resultObj.optString("id"),
                    environment = resultObj.optString("environment").ifBlank { "production" },
                    url = resultObj.optString("url"),
                    branch = branch,
                    stageStatus = stageStatus.ifBlank { "active" },
                    createdOn = resultObj.optString("created_on")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun parseAccountsJson(jsonText: String): List<CloudflareAccountItem> {
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("result") ?: return emptyList()
        val list = mutableListOf<CloudflareAccountItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            if (id.isBlank()) continue
            list.add(
                CloudflareAccountItem(
                    id = id,
                    name = obj.optString("name").ifBlank { id }
                )
            )
        }
        return list
    }

    internal fun parsePagesProjectsJson(jsonText: String, accountId: String): List<CloudflarePagesProjectItem> {
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("result") ?: return emptyList()
        val list = mutableListOf<CloudflarePagesProjectItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name")
            if (name.isBlank()) continue
            val latest = obj.optJSONObject("latest_deployment")
            val latestStage = latest?.optJSONObject("latest_stage")?.optString("status").orEmpty()
            val latestUrl = latest?.optString("url").orEmpty()
            val domainsArr = obj.optJSONArray("domains")
            val domains = mutableListOf<String>()
            if (domainsArr != null) {
                for (d in 0 until domainsArr.length()) {
                    val dom = domainsArr.optString(d)
                    if (dom.isNotBlank()) domains.add(dom)
                }
            }
            list.add(
                CloudflarePagesProjectItem(
                    name = name,
                    subdomain = obj.optString("subdomain"),
                    productionBranch = obj.optString("production_branch").ifBlank { "main" },
                    accountId = accountId,
                    latestDeploymentState = latestStage,
                    latestDeploymentUrl = latestUrl,
                    domains = domains
                )
            )
        }
        return list
    }

    internal fun parsePagesDeploymentsJson(jsonText: String): List<CloudflarePagesDeploymentItem> {
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("result") ?: return emptyList()
        val list = mutableListOf<CloudflarePagesDeploymentItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            if (id.isBlank()) continue
            val branch = obj.optJSONObject("deployment_trigger")
                ?.optJSONObject("metadata")
                ?.optString("branch")
                .orEmpty()
            val stageStatus = obj.optJSONObject("latest_stage")?.optString("status").orEmpty()
            list.add(
                CloudflarePagesDeploymentItem(
                    id = id,
                    environment = obj.optString("environment"),
                    url = obj.optString("url"),
                    branch = branch,
                    stageStatus = stageStatus,
                    createdOn = obj.optString("created_on")
                )
            )
        }
        return list
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
