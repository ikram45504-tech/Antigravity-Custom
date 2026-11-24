package id.vern.antigravity.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLinkClassifierTest {

    @Test
    fun `isLocalFileLink distinguishes local files from external URLs`() {
        assertTrue(isLocalFileLink("file:///data/user/0/id.ikram.antigravity/files/implementation_plan.md"))
        assertTrue(isLocalFileLink("/storage/emulated/0/Projects/Antigravity-Custom/README.md"))
        assertTrue(isLocalFileLink("implementation_plan.md"))
        assertTrue(isLocalFileLink("app/src/main/kotlin/Main.kt"))

        assertFalse(isLocalFileLink("https://github.com/ikram45504-tech/Antigravity-Custom"))
        assertFalse(isLocalFileLink("http://127.0.0.1:45161/health"))
        assertFalse(isLocalFileLink("mailto:test@example.com"))
        assertFalse(isLocalFileLink("   "))
    }
}
