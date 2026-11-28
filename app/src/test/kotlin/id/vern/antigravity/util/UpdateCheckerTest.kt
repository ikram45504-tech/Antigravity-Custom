package id.vern.antigravity.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `resolveTargetFileName includes release version to prevent stale APK reuse`() {
        val v31 = UpdateChecker.UpdateInfo(
            version = "v1.0.31",
            changelog = "",
            downloadUrl = "https://github.com/ikram45504-tech/Antigravity-Custom/releases/download/v1.0.31/app-release.apk",
            fileName = "app-release.apk"
        )
        val v32 = UpdateChecker.UpdateInfo(
            version = "v1.0.32",
            changelog = "",
            downloadUrl = "https://github.com/ikram45504-tech/Antigravity-Custom/releases/download/v1.0.32/app-release.apk",
            fileName = "app-release.apk"
        )

        assertEquals("app-release-v1.0.31.apk", UpdateChecker.resolveTargetFileName(v31))
        assertEquals("app-release-v1.0.32.apk", UpdateChecker.resolveTargetFileName(v32))
        assertNotEquals(
            UpdateChecker.resolveTargetFileName(v31),
            UpdateChecker.resolveTargetFileName(v32)
        )
    }
}
