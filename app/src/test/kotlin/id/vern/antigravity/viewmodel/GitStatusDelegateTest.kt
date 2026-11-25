package id.vern.antigravity.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class GitStatusDelegateTest {

    @Test
    fun `parseBranchStatusLine parses tracking ahead behind and initial branch`() {
        assertEquals(
            Triple("main", 2, 1),
            GitStatusDelegate.parseBranchStatusLine("## main...origin/main [ahead 2, behind 1]")
        )
        assertEquals(
            Triple("feature/ui", 3, 0),
            GitStatusDelegate.parseBranchStatusLine("## feature/ui...origin/feature/ui [ahead 3]")
        )
        assertEquals(
            Triple("main", 0, 0),
            GitStatusDelegate.parseBranchStatusLine("## No commits yet on main")
        )
        assertEquals(
            Triple("HEAD (no branch)", 0, 0),
            GitStatusDelegate.parseBranchStatusLine("## HEAD (no branch)")
        )
    }
}
