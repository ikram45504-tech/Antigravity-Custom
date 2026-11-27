package id.vern.antigravity.integrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectIntegrationsManagerTest {

    private val accountsById = mapOf(
        "v-acc" to CloudAccount("v-acc", CloudProvider.VERCEL, "Personal Vercel", "dev@v.test", "vercel_tok_1"),
        "s-acc" to CloudAccount("s-acc", CloudProvider.SUPABASE, "Personal Supabase", "org", "sbp_tok_1"),
        "c-acc" to CloudAccount("c-acc", CloudProvider.CLOUDFLARE, "Personal CF", "cf", "cf_tok_1")
    )

    @Test
    fun `bindings serialize and parse round-trip across all providers`() {
        val original = listOf(
            ProjectIntegrationBinding(
                projectPath = "/storage/emulated/0/Projects/app-a",
                github = GitHubProjectBinding("personal", "personal/app-a", "https://github.com/personal/app-a.git"),
                vercel = VercelProjectBinding("v-acc", "team_1", "org_1", "prj_1", "app-a", injectEnv = true, syncMcp = true),
                supabase = SupabaseProjectBinding("s-acc", "org_s", "ref_123", "app-a-db", "https://ref_123.supabase.co", "anon_abc", injectEnv = true, syncMcp = true),
                cloudflare = CloudflarePagesBinding("c-acc", "cf_acc_1", "Main CF", "app-a-pages", "app-a", injectEnv = true, syncMcp = false)
            ),
            ProjectIntegrationBinding(
                projectPath = "/storage/emulated/0/Projects/app-b",
                github = GitHubProjectBinding("personal", "personal/app-b", "https://github.com/personal/app-b.git")
            )
        )

        val json = ProjectIntegrationsManager.serializeBindingsJson(original)
        val parsed = ProjectIntegrationsManager.parseBindingsJson(json)

        assertEquals(2, parsed.size)
        assertEquals("personal", parsed[0].github?.accountLogin)
        assertEquals("personal/app-a", parsed[0].github?.repoFullName)
        assertEquals("prj_1", parsed[0].vercel?.projectId)
        assertEquals("ref_123", parsed[0].supabase?.projectRef)
        assertEquals("app-a-pages", parsed[0].cloudflare?.pagesProjectName)
        assertEquals("personal/app-b", parsed[1].github?.repoFullName)
        assertNull(parsed[1].vercel)
    }

    @Test
    fun `findBindingForPath isolates sibling projects and matches subdirectories`() {
        val bindings = listOf(
            ProjectIntegrationBinding(
                projectPath = "/projects/app-a",
                github = GitHubProjectBinding("acct-a", "acct-a/app-a")
            )
        )

        assertEquals("acct-a", ProjectIntegrationsManager.findBindingForPath(bindings, "/projects/app-a")?.github?.accountLogin)
        assertEquals("acct-a", ProjectIntegrationsManager.findBindingForPath(bindings, "/projects/app-a/packages/web")?.github?.accountLogin)
        assertNull(ProjectIntegrationsManager.findBindingForPath(bindings, "/projects/app-a-2"))
        assertNull(ProjectIntegrationsManager.findBindingForPath(bindings, "/projects/app-b"))
    }

    @Test
    fun `buildRuntimeEnvFromBinding injects only bound enabled providers`() {
        val binding = ProjectIntegrationBinding(
            projectPath = "/projects/app-a",
            vercel = VercelProjectBinding("v-acc", "", "org_1", "prj_1", "app-a", injectEnv = true),
            supabase = SupabaseProjectBinding("s-acc", "", "ref_123", "app-a-db", "https://ref_123.supabase.co", "anon_abc", injectEnv = false),
            cloudflare = CloudflarePagesBinding("c-acc", "cf_acc_1", "Main CF", "app-a-pages", "app-a", injectEnv = true)
        )

        val env = ProjectIntegrationsManager.buildRuntimeEnvFromBinding(binding, accountsById)
        assertEquals("vercel_tok_1", env["VERCEL_TOKEN"])
        assertEquals("prj_1", env["VERCEL_PROJECT_ID"])
        assertEquals("org_1", env["VERCEL_ORG_ID"])
        assertEquals("cf_tok_1", env["CLOUDFLARE_API_TOKEN"])
        assertEquals("cf_acc_1", env["CLOUDFLARE_ACCOUNT_ID"])
        assertEquals("app-a-pages", env["CF_PAGES_PROJECT_NAME"])
        assertTrue(!env.containsKey("SUPABASE_ACCESS_TOKEN"))
        assertTrue(!env.containsKey("VITE_SUPABASE_URL"))
        assertTrue(ProjectIntegrationsManager.buildRuntimeEnvFromBinding(null, accountsById).isEmpty())
    }

    @Test
    fun `buildManagedMcpServersFromBinding emits bearer headers only when syncMcp is enabled`() {
        val binding = ProjectIntegrationBinding(
            projectPath = "/projects/app-a",
            supabase = SupabaseProjectBinding("s-acc", "", "ref_123", "db", "https://ref_123.supabase.co", "", injectEnv = true, syncMcp = true),
            vercel = VercelProjectBinding("v-acc", "", "org_1", "prj_1", "app-a", injectEnv = true, syncMcp = false),
            cloudflare = null
        )

        val servers = ProjectIntegrationsManager.buildManagedMcpServersFromBinding(binding, accountsById)
        val sb = servers[ProjectIntegrationsManager.MCP_KEY_SUPABASE]!!
        assertTrue(sb.isRemote)
        assertEquals("https://mcp.supabase.com/mcp?project_ref=ref_123", sb.url)
        assertEquals("Bearer sbp_tok_1", sb.headers["Authorization"])
        assertNull(servers[ProjectIntegrationsManager.MCP_KEY_VERCEL])
        assertNull(servers[ProjectIntegrationsManager.MCP_KEY_CLOUDFLARE])
    }
}
