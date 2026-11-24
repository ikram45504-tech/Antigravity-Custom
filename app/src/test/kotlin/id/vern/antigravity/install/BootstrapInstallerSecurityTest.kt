package id.vern.antigravity.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
}
