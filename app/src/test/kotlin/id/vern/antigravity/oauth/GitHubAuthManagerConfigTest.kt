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
}
