package id.vern.antigravity.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileResolverTest {

    @Test
    fun `normalizePathOrUri strips file scheme quotes and line anchors`() {
        assertEquals(
            "/data/user/0/id.ikram.antigravity/files/home/.gemini/antigravity-cli/brain/abc/implementation_plan.md",
            FileResolver.normalizePathOrUri(
                "file:///data/user/0/id.ikram.antigravity/files/home/.gemini/antigravity-cli/brain/abc/implementation_plan.md#L12-L40"
            )
        )
        assertEquals(
            "/tmp/Main.kt",
            FileResolver.normalizePathOrUri("  \"/tmp/Main.kt#L5\"  ")
        )
        assertEquals(
            "implementation_plan.md",
            FileResolver.normalizePathOrUri("implementation_plan.md")
        )
        assertEquals("", FileResolver.normalizePathOrUri("   "))
    }
}
