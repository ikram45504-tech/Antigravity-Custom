package id.vern.antigravity.executor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * shellQuote is the last line of defence between a user-controlled path or a
 * `git status` filename and a `bash -c` command string.
 */
class EngineExecutorShellQuoteTest {

    @Test
    fun `plain value is wrapped in single quotes`() {
        assertEquals("'/data/user/0/app/files/home'", EngineExecutor.shellQuote("/data/user/0/app/files/home"))
    }

    @Test
    fun `embedded single quote is escaped as quote backslash quote quote`() {
        // bash cannot escape a quote inside '...'; the string must be closed,
        // an escaped quote emitted, and the string reopened.
        assertEquals("'it'\\''s'", EngineExecutor.shellQuote("it's"))
    }

    @Test
    fun `injection attempt cannot terminate the quoted argument`() {
        val quoted = EngineExecutor.shellQuote("x'; rm -rf /; echo '")
        // Must not contain an unescaped closing quote followed by a command.
        assertEquals("'x'\\''; rm -rf /; echo '\\'''", quoted)
    }

    @Test
    fun `empty value produces empty quoted string`() {
        assertEquals("''", EngineExecutor.shellQuote(""))
    }

    @Test
    fun `spaces and newlines stay inside the quotes`() {
        assertEquals("'a b'", EngineExecutor.shellQuote("a b"))
        assertEquals("'a\nb'", EngineExecutor.shellQuote("a\nb"))
    }
}
