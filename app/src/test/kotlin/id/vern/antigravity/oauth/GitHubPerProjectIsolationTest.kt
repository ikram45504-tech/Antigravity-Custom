package id.vern.antigravity.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubPerProjectIsolationTest {

    @Test
    fun `buildProfileGitConfig includes scoped credential helper and author when bound`() {
        val cfg = GitHubAuthManager.buildProfileGitConfig(
            certPath = "/data/files/usr/etc/tls/cert.pem",
            credentialsFilePath = "/data/files/.gemini/git-profiles/octocat/credentials",
            name = "Octo Cat",
            email = "octo@example.com"
        )

        assertTrue(cfg.contains("[safe]\n\tdirectory = *"))
        assertTrue(cfg.contains("sslCAInfo = /data/files/usr/etc/tls/cert.pem"))
        assertTrue(cfg.contains("helper = store --file /data/files/.gemini/git-profiles/octocat/credentials"))
        assertTrue(cfg.contains("name = Octo Cat"))
        assertTrue(cfg.contains("email = octo@example.com"))
    }

    @Test
    fun `buildProfileGitConfig omits credentials and user when project is unbound`() {
        val cfg = GitHubAuthManager.buildProfileGitConfig(
            certPath = "/data/files/usr/etc/tls/cert.pem",
            credentialsFilePath = null,
            name = null,
            email = null
        )

        assertTrue(cfg.contains("[safe]"))
        assertTrue(cfg.contains("sslCAInfo ="))
        assertFalse(cfg.contains("credential"))
        assertFalse(cfg.contains("[user]"))
    }

    @Test
    fun `parseGitHubRemoteRepo handles https and ssh remotes`() {
        assertEquals(
            "ikram45504-tech/Antigravity-Custom" to "https://github.com/ikram45504-tech/Antigravity-Custom.git",
            GitHubAuthManager.parseGitHubRemoteRepo("https://github.com/ikram45504-tech/Antigravity-Custom.git")
        )
        assertEquals(
            "acme/web-app" to "https://github.com/acme/web-app.git",
            GitHubAuthManager.parseGitHubRemoteRepo("git@github.com:acme/web-app.git")
        )
        assertNull(GitHubAuthManager.parseGitHubRemoteRepo("https://gitlab.com/acme/web-app.git"))
    }

    @Test
    fun `safeProfileDirName normalizes login cleanly`() {
        assertEquals("octo_cat-1", GitHubAuthManager.safeProfileDirName(" Octo/Cat-1 "))
    }
}
