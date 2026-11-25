package id.vern.antigravity.oauth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubAuthManagerConfigTest {

    @Test
    fun `buildSyncedGitConfig strips legacy PAT insteadOf block and configures credential store`() {
        val legacyLines = listOf(
            "[safe]",
            "\tdirectory = *",
            "[url \"https://octocat:ghp_secret123@github.com/\"]",
            "\tinsteadOf = https://github.com/"
        )

        val synced = GitHubAuthManager.buildSyncedGitConfig(
            existingLines = legacyLines,
            name = "Octo Cat",
            email = "octo@example.com"
        )

        assertFalse(synced.contains("ghp_secret123"))
        assertFalse(synced.contains("insteadOf"))
        assertTrue(synced.contains("[safe]"))
        assertTrue(synced.contains("[credential \"https://github.com\"]"))
        assertTrue(synced.contains("helper = store"))
        assertTrue(synced.contains("name = Octo Cat"))
        assertTrue(synced.contains("email = octo@example.com"))
    }

    @Test
    fun `cleanGitConfigLines removes github credential helper and legacy url block on signOut`() {
        val lines = listOf(
            "[safe]",
            "\tdirectory = *",
            "[url \"https://user:gho_tok@github.com/\"]",
            "\tinsteadOf = https://github.com/",
            "[credential \"https://github.com\"]",
            "\thelper = store",
            "[user]",
            "\tname = Dev"
        )

        val cleaned = GitHubAuthManager.cleanGitConfigLines(lines, removeCredentialHelper = true).joinToString("\n")
        assertTrue(cleaned.contains("[safe]"))
        assertTrue(cleaned.contains("[user]"))
        assertFalse(cleaned.contains("gho_tok"))
        assertFalse(cleaned.contains("insteadOf"))
        assertFalse(cleaned.contains("[credential \"https://github.com\"]"))
        assertFalse(cleaned.contains("helper = store"))
    }

    @Test
    fun `buildSyncedGitConfig replaces existing user section when switching accounts`() {
        val existing = listOf(
            "[safe]",
            "\tdirectory = *",
            "[credential \"https://github.com\"]",
            "\thelper = store",
            "[user]",
            "\tname = Old User",
            "\temail = old@example.com"
        )

        val synced = GitHubAuthManager.buildSyncedGitConfig(
            existingLines = existing,
            name = "New User",
            email = "new@example.com"
        )

        assertFalse(synced.contains("Old User"))
        assertFalse(synced.contains("old@example.com"))
        assertTrue(synced.contains("name = New User"))
        assertTrue(synced.contains("email = new@example.com"))
    }

    @Test
    fun `parseScopesHeader and parseRepositoriesJson extract scopes and repo metadata`() {
        val scopes = GitHubAuthManager.parseScopesHeader("repo, workflow, read:user ")
        org.junit.Assert.assertEquals(listOf("repo", "workflow", "read:user"), scopes)

        val repos = GitHubAuthManager.parseRepositoriesJson(
            """
            [
              {
                "id": 42,
                "name": "Antigravity-Custom",
                "full_name": "ikram45504-tech/Antigravity-Custom",
                "clone_url": "https://github.com/ikram45504-tech/Antigravity-Custom.git",
                "html_url": "https://github.com/ikram45504-tech/Antigravity-Custom",
                "description": null,
                "private": false,
                "default_branch": "main",
                "updated_at": "2026-11-25T00:00:00Z"
              }
            ]
            """.trimIndent()
        )
        org.junit.Assert.assertEquals(1, repos.size)
        org.junit.Assert.assertEquals("Antigravity-Custom", repos[0].name)
        org.junit.Assert.assertEquals("", repos[0].description)
        org.junit.Assert.assertEquals("main", repos[0].defaultBranch)
    }
}
