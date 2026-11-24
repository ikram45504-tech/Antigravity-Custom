package id.vern.antigravity.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class OAuthManagerProfileTest {

    private fun buildJwt(payloadJson: String): String {
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"RS256"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.toByteArray())
        return "$header.$payload.sig"
    }

    @Test
    fun `parseProfileFromTokenJson reads nested token object and JWT id_token payload`() {
        val idToken = buildJwt("""{"email":"jwt@example.com","name":"JWT User","picture":"https://img.test/a.png"}""")
        val json = """
            {
              "token": {
                "access_token": "ya29.test",
                "id_token": "$idToken"
              }
            }
        """.trimIndent()

        val profile = OAuthManager.parseProfileFromTokenJson(json)
        assertEquals("jwt@example.com", profile.email)
        assertEquals("JWT User", profile.name)
        assertEquals("https://img.test/a.png", profile.picture)
    }

    @Test
    fun `parseProfileFromTokenJson falls back to top-level and nested metadata fields`() {
        val topLevelJson = """
            {
              "access_token": "ya29.top",
              "email": "top@example.com",
              "name": "Top User",
              "picture": "https://img.test/top.png"
            }
        """.trimIndent()

        val topProfile = OAuthManager.parseProfileFromTokenJson(topLevelJson)
        assertEquals("top@example.com", topProfile.email)
        assertEquals("Top User", topProfile.name)
        assertEquals("https://img.test/top.png", topProfile.picture)

        val nestedFallbackJson = """
            {
              "token": {
                "email": "nested@example.com",
                "name": "Nested User"
              }
            }
        """.trimIndent()
        val nestedProfile = OAuthManager.parseProfileFromTokenJson(nestedFallbackJson)
        assertEquals("nested@example.com", nestedProfile.email)
        assertEquals("Nested User", nestedProfile.name)
        assertNull(nestedProfile.picture)
    }

    @Test
    fun `parseProfileFromTokenJson handles blank or malformed input safely`() {
        val blank = OAuthManager.parseProfileFromTokenJson("   ")
        assertNull(blank.email)
        assertNull(blank.name)
        assertNull(blank.picture)

        val invalid = OAuthManager.parseProfileFromTokenJson("{not-json")
        assertNull(invalid.email)
        assertNull(invalid.name)
        assertNull(invalid.picture)
    }
}
