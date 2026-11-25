package id.vern.antigravity.integrations

enum class CloudProvider(val displayName: String, val tokenPortalUrl: String) {
    VERCEL("Vercel", "https://vercel.com/account/tokens"),
    SUPABASE("Supabase", "https://supabase.com/dashboard/account/tokens"),
    CLOUDFLARE("Cloudflare", "https://dash.cloudflare.com/profile/api-tokens")
}

data class CloudAccount(
    val id: String,
    val provider: CloudProvider,
    val label: String,
    val handleOrEmail: String = "",
    val token: String,
    val defaultScopeId: String = ""
)

data class CloudVerificationResult(
    val isValid: Boolean,
    val displayName: String = "",
    val detail: String = "",
    val statusCode: Int = 0,
    val message: String = ""
)

data class VercelTeamItem(
    val id: String,
    val slug: String,
    val name: String
)

data class VercelProjectItem(
    val id: String,
    val name: String,
    val framework: String = "",
    val teamId: String? = null,
    val orgId: String = "",
    val updatedAt: Long = 0L,
    val productionUrl: String = ""
)

data class VercelDeploymentItem(
    val uid: String,
    val name: String,
    val url: String,
    val state: String,
    val target: String = "",
    val createdAt: Long = 0L
)

data class VercelEnvVarItem(
    val id: String,
    val key: String,
    val value: String,
    val targets: List<String> = emptyList(),
    val type: String = ""
)

data class SupabaseOrgItem(
    val id: String,
    val name: String
)

data class SupabaseProjectItem(
    val id: String,
    val ref: String,
    val name: String,
    val organizationId: String,
    val region: String,
    val status: String,
    val apiUrl: String
)

data class SupabaseApiKeyItem(
    val name: String,
    val apiKey: String
)

data class CloudflareAccountItem(
    val id: String,
    val name: String
)

data class CloudflarePagesProjectItem(
    val name: String,
    val subdomain: String,
    val productionBranch: String,
    val accountId: String,
    val latestDeploymentState: String = "",
    val latestDeploymentUrl: String = "",
    val domains: List<String> = emptyList()
)

data class CloudflarePagesDeploymentItem(
    val id: String,
    val environment: String,
    val url: String,
    val branch: String,
    val stageStatus: String,
    val createdOn: String
)

data class GitHubProjectBinding(
    val accountLogin: String,
    val repoFullName: String = "",
    val repoCloneUrl: String = ""
)

data class VercelProjectBinding(
    val accountId: String,
    val teamId: String = "",
    val orgId: String = "",
    val projectId: String,
    val projectName: String,
    val injectEnv: Boolean = true,
    val syncMcp: Boolean = false
)

data class SupabaseProjectBinding(
    val accountId: String,
    val organizationId: String = "",
    val projectRef: String,
    val projectName: String,
    val apiUrl: String,
    val anonKey: String = "",
    val injectEnv: Boolean = true,
    val syncMcp: Boolean = true
)

data class CloudflarePagesBinding(
    val accountId: String,
    val cfAccountId: String,
    val cfAccountName: String = "",
    val pagesProjectName: String,
    val subdomain: String = "",
    val injectEnv: Boolean = true,
    val syncMcp: Boolean = false
)

data class ProjectIntegrationBinding(
    val projectPath: String,
    val github: GitHubProjectBinding? = null,
    val vercel: VercelProjectBinding? = null,
    val supabase: SupabaseProjectBinding? = null,
    val cloudflare: CloudflarePagesBinding? = null
) {
    val linkedCount: Int
        get() = listOfNotNull(github, vercel, supabase, cloudflare).size
}
