package id.vern.antigravity.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repo URLs and sub-paths are interpolated into `git clone '...'` and
 * `cp -r '$clone/$sub'` shell strings, so these functions are a security
 * boundary. They must accept ordinary GitHub inputs and reject anything that
 * could break out of the quoting.
 */
class RepoUrlSanitizerTest {

    // ---- normalizeGitRepoUrl: accepted forms -------------------------------

    @Test
    fun `plain owner repo is expanded to a github https url`() {
        val (url, sub) = CustomizationTracker.normalizeGitRepoUrl("user/repo")
        assertEquals("https://github.com/user/repo.git", url)
        assertNull(sub)
    }

    @Test
    fun `https url gets dot git suffix`() {
        val (url, _) = CustomizationTracker.normalizeGitRepoUrl("https://github.com/user/repo")
        assertEquals("https://github.com/user/repo.git", url)
    }

    @Test
    fun `tree url extracts repo and sub path`() {
        val (url, sub) = CustomizationTracker.normalizeGitRepoUrl(
            "https://github.com/user/repo/tree/main/skills/anti-slop"
        )
        assertEquals("https://github.com/user/repo.git", url)
        assertEquals("skills/anti-slop", sub)
    }

    @Test
    fun `blob url to a markdown file yields the containing directory`() {
        val (_, sub) = CustomizationTracker.normalizeGitRepoUrl(
            "https://github.com/user/repo/blob/main/skills/thing/SKILL.md"
        )
        assertEquals("skills/thing", sub)
    }

    @Test
    fun `already suffixed url is left alone`() {
        val (url, _) = CustomizationTracker.normalizeGitRepoUrl("https://github.com/user/repo.git")
        assertEquals("https://github.com/user/repo.git", url)
    }

    @Test
    fun `git ssh url is preserved`() {
        val (url, _) = CustomizationTracker.normalizeGitRepoUrl("git@github.com:user/repo.git")
        assertEquals("git@github.com:user/repo.git", url)
    }

    // ---- normalizeGitRepoUrl: rejected forms ------------------------------

    @Test
    fun `command separator is rejected`() {
        val (url, _) = CustomizationTracker.normalizeGitRepoUrl("https://github.com/u/r.git; rm -rf /")
        assertEquals("", url)
    }

    @Test
    fun `command substitution is rejected`() {
        assertEquals("", CustomizationTracker.normalizeGitRepoUrl("https://github.com/u/r\$(id).git").first)
        assertEquals("", CustomizationTracker.normalizeGitRepoUrl("https://github.com/u/r`id`.git").first)
    }

    @Test
    fun `embedded quote is rejected`() {
        assertEquals("", CustomizationTracker.normalizeGitRepoUrl("https://github.com/u/r'x.git").first)
        assertEquals("", CustomizationTracker.normalizeGitRepoUrl("https://github.com/u/r\"x.git").first)
    }

    @Test
    fun `pipe and newline are rejected`() {
        assertEquals("", CustomizationTracker.normalizeGitRepoUrl("https://github.com/u/r|x.git").first)
        assertEquals("", CustomizationTracker.normalizeGitRepoUrl("https://github.com/u/r\nrm -rf /.git").first)
    }

    @Test
    fun `blank input yields empty url`() {
        assertEquals("", CustomizationTracker.normalizeGitRepoUrl("   ").first)
    }

    // ---- sanitizeSubPath ---------------------------------------------------

    @Test
    fun `ordinary sub paths survive`() {
        assertEquals("skills/anti-slop", CustomizationTracker.sanitizeSubPath("skills/anti-slop"))
        assertEquals("skills/anti-slop", CustomizationTracker.sanitizeSubPath("/skills/anti-slop/"))
        assertNull(CustomizationTracker.sanitizeSubPath(null))
        assertNull(CustomizationTracker.sanitizeSubPath("   "))
    }

    @Test
    fun `dangerous sub paths are dropped`() {
        assertNull(CustomizationTracker.sanitizeSubPath("skills; rm -rf /"))
        assertNull(CustomizationTracker.sanitizeSubPath("skills/\$(id)"))
        assertNull(CustomizationTracker.sanitizeSubPath("skills/a b"))
        assertNull(CustomizationTracker.sanitizeSubPath("skills/a'b"))
        assertNull(CustomizationTracker.sanitizeSubPath("skills/a|b"))
    }

    // ---- sanitizeRepoUrl ---------------------------------------------------

    @Test
    fun `sanitizeRepoUrl keeps safe values and blanks unsafe ones`() {
        assertEquals(
            "https://github.com/user/repo.git",
            CustomizationTracker.sanitizeRepoUrl("https://github.com/user/repo.git")
        )
        assertEquals("", CustomizationTracker.sanitizeRepoUrl("https://github.com/u/r.git; rm -rf /"))
        assertEquals("", CustomizationTracker.sanitizeRepoUrl(null))
    }

    @Test
    fun `sanitized values never contain shell metacharacters`() {
        // Property check over the rejection set.
        val nasty = listOf(";", "&", "|", "'", "\"", "`", "\$", "\n", " ", "<", ">", "(", ")", "*", "?")
        for (ch in nasty) {
            val candidate = "https://github.com/u/r${ch}x.git"
            assertTrue(
                "expected empty for character $ch",
                CustomizationTracker.sanitizeRepoUrl(candidate).isEmpty()
            )
        }
    }
}
