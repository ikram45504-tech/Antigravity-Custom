package id.vern.antigravity.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest
import java.util.Base64

class CoreUpdateManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `isVersionNewer compares semantic segments accurately`() {
        assertTrue(CoreUpdateManager.isVersionNewer("1.5.17", "1.5.16"))
        assertTrue(CoreUpdateManager.isVersionNewer("1.6.0", "1.5.99"))
        assertTrue(CoreUpdateManager.isVersionNewer("2.0.0", "1.9.9"))
        assertTrue(CoreUpdateManager.isVersionNewer("1.5.16.1", "1.5.16"))

        assertFalse(CoreUpdateManager.isVersionNewer("1.5.16", "1.5.16"))
        assertFalse(CoreUpdateManager.isVersionNewer("1.5.15", "1.5.16"))
        assertFalse(CoreUpdateManager.isVersionNewer("1.4.99", "1.5.0"))
        assertFalse(CoreUpdateManager.isVersionNewer("1.5", "1.5.1"))
    }

    @Test
    fun `verifySha512 validates hex and base64 digests and rejects mismatches`() {
        val file = tempFolder.newFile("core.tar.gz")
        val payload = "antigravity-core-binary-payload".toByteArray(Charsets.UTF_8)
        file.writeBytes(payload)

        val digestBytes = MessageDigest.getInstance("SHA-512").digest(payload)
        val expectedHex = digestBytes.joinToString("") { "%02x".format(it) }
        val expectedBase64 = Base64.getEncoder().encodeToString(digestBytes)

        assertTrue(CoreUpdateManager.verifySha512(file, expectedHex))
        assertTrue(CoreUpdateManager.verifySha512(file, expectedHex.uppercase()))
        assertTrue(CoreUpdateManager.verifySha512(file, "sha512-$expectedBase64"))
        assertFalse(CoreUpdateManager.verifySha512(file, "0".repeat(128)))
        assertFalse(CoreUpdateManager.verifySha512(file, ""))
    }
}
