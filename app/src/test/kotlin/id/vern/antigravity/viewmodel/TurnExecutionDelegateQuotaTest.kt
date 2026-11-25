package id.vern.antigravity.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnExecutionDelegateQuotaTest {

    @Test
    fun `isQuotaLimitError detects quota exhaustion and ignores normal output`() {
        assertTrue(TurnExecutionDelegate.isQuotaLimitError(1, "Error: RESOURCE_EXHAUSTED for model"))
        assertTrue(TurnExecutionDelegate.isQuotaLimitError(1, "HTTP 429 Too Many Requests"))
        assertTrue(TurnExecutionDelegate.isQuotaLimitError(0, "Quota exceeded for Gemini 4 Hydrogen"))
        assertTrue(TurnExecutionDelegate.isQuotaLimitError(1, "Weekly limit reached"))

        assertFalse(TurnExecutionDelegate.isQuotaLimitError(0, "Build completed cleanly."))
        assertFalse(TurnExecutionDelegate.isQuotaLimitError(0, "Updated rate limit check in docs."))
        assertFalse(TurnExecutionDelegate.isQuotaLimitError(1, ""))
    }
}
