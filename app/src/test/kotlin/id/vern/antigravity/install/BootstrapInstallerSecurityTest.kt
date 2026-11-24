package id.vern.antigravity.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BootstrapInstallerSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `isSafeZipDestination allows valid entries and rejects traversal and sibling prefix tricks`() {
        val targetDir = tempFolder.newFolder("usr")

        assertTrue(BootstrapInstaller.isSafeZipDestination(targetDir, "bin/bash"))
        assertTrue(BootstrapInstaller.isSafeZipDestination(targetDir, "lib/libc.so.6"))

        assertFalse(BootstrapInstaller.isSafeZipDestination(targetDir, "../evil"))
        assertFalse(BootstrapInstaller.isSafeZipDestination(targetDir, "../../etc/passwd"))
        assertFalse(BootstrapInstaller.isSafeZipDestination(targetDir, "../usr-sibling/file"))
    }

    @Test
    fun `patchGitShellPath rewrites hardcoded Termux shell path to system sh preserving size`() {
        val prefix = tempFolder.newFolder("prefix")
        val binDir = File(prefix, "bin").apply { mkdirs() }
        val fakeGit = File(binDir, "git")
        val originalPayload = "ELF_HEADER\u0000/data/data/com.termux/files/usr/bin/sh\u0000TAIL".toByteArray(Charsets.US_ASCII)
        fakeGit.writeBytes(originalPayload)

        assertTrue(BootstrapInstaller.patchGitShellPath(prefix))
        val patchedBytes = fakeGit.readBytes()
        assertEquals(originalPayload.size, patchedBytes.size)
        val patchedText = patchedBytes.toString(Charsets.US_ASCII)
        assertFalse(patchedText.contains("/data/data/com.termux/files/usr/bin/sh"))
        assertTrue(patchedText.startsWith("ELF_HEADER\u0000/system/bin/sh\u0000"))
        assertTrue(patchedText.endsWith("TAIL"))
        assertFalse(BootstrapInstaller.patchGitShellPath(prefix))
    }
}
