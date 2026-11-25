package id.vern.antigravity.service

import id.vern.antigravity.executor.EngineExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LocalhostDevServerSupportTest {

    @Test
    fun `extractLocalhostUrls normalizes wildcard and loopback hosts`() {
        val viteLine = "  \u001B[32m➜\u001B[39m  Local:   http://localhost:5173/"
        val vite = DevServerManager.extractLocalhostUrls(viteLine)
        assertEquals(listOf(5173 to "http://127.0.0.1:5173/"), vite)

        val nextLine = "   - Local:        http://0.0.0.0:3000"
        val next = DevServerManager.extractLocalhostUrls(nextLine)
        assertEquals(listOf(3000 to "http://127.0.0.1:3000"), next)

        val ipv6Line = "Serving at http://[::1]:8080/app"
        val ipv6 = DevServerManager.extractLocalhostUrls(ipv6Line)
        assertEquals(listOf(8080 to "http://127.0.0.1:8080/app"), ipv6)

        val portOnly = "Server listening on port 4321"
        val pOnly = DevServerManager.extractLocalhostUrls(portOnly)
        assertEquals(listOf(4321 to "http://127.0.0.1:4321"), pOnly)

        val external = DevServerManager.extractLocalhostUrls("Fetching https://example.com:8443/api")
        assertTrue(external.isEmpty())
    }

    @Test
    fun `suggestDefaultCommand picks appropriate command per project files`() {
        val dir = Files.createTempDirectory("dev_srv_test").toFile()
        try {
            assertEquals("python3 -m http.server 8000 --bind 127.0.0.1", DevServerManager.suggestDefaultCommand(dir))

            dir.resolve("manage.py").writeText("# django")
            assertEquals("python3 manage.py runserver 127.0.0.1:8000", DevServerManager.suggestDefaultCommand(dir))

            dir.resolve("package.json").writeText("""{"scripts":{"start":"node server.js"}}""")
            assertEquals("npm start", DevServerManager.suggestDefaultCommand(dir))

            dir.resolve("package.json").writeText("""{"scripts":{"dev":"vite","start":"node server.js"}}""")
            assertEquals("npm run dev", DevServerManager.suggestDefaultCommand(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `EngineExecutor helpers handle node options and shared storage detection`() {
        val flag = "--require=/data/files/usr/lib/node_android_shim.js"
        assertEquals(flag, EngineExecutor.appendNodeOptionFlag(null, flag))
        assertEquals("--max-old-space-size=2048 $flag", EngineExecutor.appendNodeOptionFlag("--max-old-space-size=2048", flag))
        assertEquals("--max-old-space-size=2048 $flag", EngineExecutor.appendNodeOptionFlag("--max-old-space-size=2048 $flag", flag))

        assertTrue(EngineExecutor.isSharedAndroidStoragePath("/storage/emulated/0/Projects/app"))
        assertTrue(EngineExecutor.isSharedAndroidStoragePath("/sdcard/Projects/app"))
        assertFalse(EngineExecutor.isSharedAndroidStoragePath("/data/user/0/id.ikram.antigravity/files/home/app"))
    }
}
