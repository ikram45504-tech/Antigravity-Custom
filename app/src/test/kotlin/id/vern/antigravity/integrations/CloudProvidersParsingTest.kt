package id.vern.antigravity.integrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CloudProvidersParsingTest {

    @Test
    fun `CloudAccountVault round-trip preserves all provider accounts`() {
        val accounts = listOf(
            CloudAccount("1", CloudProvider.VERCEL, "Personal", "me@v.dev", "tok_v"),
            CloudAccount("2", CloudProvider.SUPABASE, "Work SB", "2 orgs", "sbp_123"),
            CloudAccount("3", CloudProvider.CLOUDFLARE, "CF Pages", "active", "cf_456")
        )
        val parsed = CloudAccountVault.parseAccountsJson(CloudAccountVault.serializeAccountsJson(accounts))
        assertEquals(3, parsed.size)
        assertEquals(CloudProvider.VERCEL, parsed[0].provider)
        assertEquals("sbp_123", parsed[1].token)
        assertEquals(CloudProvider.CLOUDFLARE, parsed[2].provider)
    }

    @Test
    fun `VercelApiClient parses teams, projects, deployments, and writes workspace files`() {
        val teams = VercelApiClient.parseTeamsJson("""{"teams":[{"id":"team_1","slug":"acme","name":"Acme"}]}""")
        assertEquals("Acme", teams.single().name)

        val projects = VercelApiClient.parseProjectsJson(
            """{"projects":[{"id":"prj_1","name":"web-ui","framework":"nextjs","accountId":"team_1","targets":{"production":{"alias":["web-ui.vercel.app"]}}}]}""",
            teamId = "team_1"
        )
        assertEquals("web-ui", projects.single().name)
        assertEquals("web-ui.vercel.app", projects.single().productionUrl)

        val deps = VercelApiClient.parseDeploymentsJson(
            """{"deployments":[{"uid":"dpl_1","name":"web-ui","url":"web-ui-abc.vercel.app","state":"READY","target":"production"}]}"""
        )
        assertEquals("READY", deps.single().state)

        val envs = VercelApiClient.parseEnvVarsJson(
            """{"envs":[{"id":"e1","key":"API_URL","value":"https://api.test","target":["development"]}]}"""
        )
        val tmpDir = Files.createTempDirectory("vercel_test").toFile()
        try {
            val count = VercelApiClient.pullEnvToDotEnvLocal(tmpDir, envs)
            assertEquals(1, count)
            VercelApiClient.writeWorkspaceProjectJson(tmpDir, "team_1", "prj_1")
            assertTrue(tmpDir.resolve(".env.local").readText().contains("API_URL=https://api.test"))
            assertTrue(tmpDir.resolve(".vercel/project.json").readText().contains("\"projectId\": \"prj_1\""))
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `SupabaseApiClient parses organizations, projects, api keys and writes env`() {
        val orgs = SupabaseApiClient.parseOrganizationsJson("""[{"id":"org_1","name":"My Org"}]""")
        assertEquals("My Org", orgs.single().name)

        val projects = SupabaseApiClient.parseProjectsJson(
            """[{"id":"p1","ref":"abcd1234","name":"prod-db","organization_id":"org_1","region":"us-east-1","status":"ACTIVE_HEALTHY"}]"""
        )
        assertEquals("https://abcd1234.supabase.co", projects.single().apiUrl)

        val keys = SupabaseApiClient.parseApiKeysJson(
            """[{"name":"anon","api_key":"eyJ_anon"},{"name":"service_role","api_key":"eyJ_srv"}]"""
        )
        assertEquals(2, keys.size)

        val tmpDir = Files.createTempDirectory("supabase_test").toFile()
        try {
            SupabaseApiClient.writeSupabaseEnvToWorkspace(tmpDir, "https://abcd1234.supabase.co", "eyJ_anon")
            val envText = tmpDir.resolve(".env.local").readText()
            assertTrue(envText.contains("NEXT_PUBLIC_SUPABASE_URL=https://abcd1234.supabase.co"))
            assertTrue(envText.contains("SUPABASE_ANON_KEY=eyJ_anon"))
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `SupabaseApiClient verifyToken falls back to projects when organizations_read is missing`() {
        val calls = mutableListOf<String>()
        val result = SupabaseApiClient.verifyTokenWithFetcher { url ->
            calls += url
            if (url.endsWith("/organizations")) {
                HttpResponseResult(403, """{"message":"Missing required permission(s): organizations_read"}""")
            } else {
                HttpResponseResult(
                    200,
                    """[{"id":"p1","ref":"abcd1234","name":"travelhisab-db","organization_id":"org_1","region":"ap-southeast-1","status":"ACTIVE_HEALTHY"}]"""
                )
            }
        }
        assertTrue(result.isValid)
        assertEquals("travelhisab-db", result.displayName)
        assertEquals("1 project(s)", result.detail)
        assertEquals("Verified (1 project)", result.message)
        assertEquals(2, calls.size)
    }

    @Test
    fun `SupabaseApiClient verifyToken does not fall back on 401 unauthorized`() {
        val calls = mutableListOf<String>()
        val result = SupabaseApiClient.verifyTokenWithFetcher { url ->
            calls += url
            HttpResponseResult(401, """{"message":"Unauthorized"}""")
        }
        assertFalse(result.isValid)
        assertEquals(401, result.statusCode)
        assertEquals(1, calls.size)
    }

    @Test
    fun `CloudflareApiClient parses accounts, pages projects, and deployments`() {
        val accounts = CloudflareApiClient.parseAccountsJson("""{"result":[{"id":"cf_1","name":"Main Account"}]}""")
        assertEquals("Main Account", accounts.single().name)

        val pages = CloudflareApiClient.parsePagesProjectsJson(
            """{"result":[{"name":"docs-site","subdomain":"docs-site","production_branch":"main","latest_deployment":{"url":"https://123.docs-site.pages.dev","latest_stage":{"status":"success"}}}]}""",
            accountId = "cf_1"
        )
        assertEquals("docs-site", pages.single().name)
        assertEquals("success", pages.single().latestDeploymentState)

        val deps = CloudflareApiClient.parsePagesDeploymentsJson(
            """{"result":[{"id":"dep_1","environment":"production","url":"https://123.docs-site.pages.dev","deployment_trigger":{"metadata":{"branch":"main"}},"latest_stage":{"status":"success"},"created_on":"2026-11-25T00:00:00Z"}]}"""
        )
        assertEquals("main", deps.single().branch)
        assertEquals("success", deps.single().stageStatus)
    }
}
