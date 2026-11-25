package id.vern.antigravity.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpConfigServiceHeadersTest {

    @Test
    fun `applyManagedServersJson upserts headers and preserves custom servers`() {
        val existing = """
            {
              "mcpServers": {
                "custom-local": {
                  "command": "node",
                  "args": ["server.js"]
                },
                "vercel-project": {
                  "url": "https://mcp.vercel.com/old"
                }
              }
            }
        """.trimIndent()

        val updated = McpConfigService.applyManagedServersJson(
            existingJson = existing,
            managedServers = mapOf(
                "supabase-project" to McpServerItem(
                    name = "supabase-project",
                    url = "https://mcp.supabase.com/mcp?project_ref=ref_123",
                    headers = mapOf("Authorization" to "Bearer sbp_secret"),
                    isRemote = true
                ),
                "vercel-project" to null
            )
        )

        val root = JSONObject(updated).getJSONObject("mcpServers")
        assertTrue(root.has("custom-local"))
        assertFalse(root.has("vercel-project"))
        val sb = root.getJSONObject("supabase-project")
        assertEquals("https://mcp.supabase.com/mcp?project_ref=ref_123", sb.getString("url"))
        assertEquals("Bearer sbp_secret", sb.getJSONObject("headers").getString("Authorization"))
    }
}
