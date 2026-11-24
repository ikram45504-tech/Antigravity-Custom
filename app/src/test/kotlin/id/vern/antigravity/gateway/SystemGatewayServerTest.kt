package id.vern.antigravity.gateway

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SystemGatewayServerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createServer(): SystemGatewayServer {
        val fakeContext = object : ContextWrapper(null) {
            override fun getFilesDir(): File = tempFolder.root
        }
        return SystemGatewayServer(context = fakeContext, port = 0)
    }

    @Test
    fun `health endpoint bypasses auth without token`() {
        val server = createServer()
        val (status, json) = server.routeRequest("/health", "", "")
        assertEquals("HTTP/1.1 200 OK", status)
        assertTrue(json.getBoolean("ok"))
        assertEquals("running", json.getString("status"))
    }

    @Test
    fun `protected endpoints reject missing or invalid token with 401`() {
        val server = createServer()
        val (missingStatus, missingJson) = server.routeRequest("/system_info", "", "")
        assertEquals("HTTP/1.1 401 Unauthorized", missingStatus)
        assertFalse(missingJson.getBoolean("ok"))
        assertEquals("Unauthorized", missingJson.getString("error"))

        val (wrongStatus, wrongJson) = server.routeRequest("/open_url", """{"url":"https://example.com"}""", "wrong-token")
        assertEquals("HTTP/1.1 401 Unauthorized", wrongStatus)
        assertFalse(wrongJson.getBoolean("ok"))
    }

    @Test
    fun `valid token dispatches system_info and validates open_url schemes`() {
        val server = createServer()
        val token = server.getToken()
        assertTrue(token.isNotBlank())

        val (infoStatus, infoJson) = server.routeRequest("/system_info", "", token)
        assertEquals("HTTP/1.1 200 OK", infoStatus)
        assertTrue(infoJson.getBoolean("ok"))
        assertTrue(infoJson.has("battery"))
        assertTrue(infoJson.has("sdk"))

        val (badUrlStatus, badUrlJson) = server.routeRequest("/open_url", """{"url":"javascript:alert(1)"}""", token)
        assertEquals("HTTP/1.1 200 OK", badUrlStatus)
        assertFalse(badUrlJson.getBoolean("ok"))
        assertEquals("Unsupported URL scheme", badUrlJson.getString("error"))

        val (fileUrlStatus, fileUrlJson) = server.routeRequest("/open_url", """{"url":"file:///etc/passwd"}""", token)
        assertEquals("HTTP/1.1 200 OK", fileUrlStatus)
        assertFalse(fileUrlJson.getBoolean("ok"))

        val (okUrlStatus, okUrlJson) = server.routeRequest("/open_url", """{"url":"https://example.com/docs"}""", token)
        assertEquals("HTTP/1.1 200 OK", okUrlStatus)
        assertTrue(okUrlJson.getBoolean("ok"))
        assertEquals("https://example.com/docs", okUrlJson.getString("opened"))
    }
}
