package id.vern.antigravity.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TurnExecutionDelegateFileChangesTest {

    private val delegate = TurnExecutionDelegate()

    @Test
    fun `parseGitPorcelainLine handles modified untracked deleted and renamed paths`() {
        assertEquals("app/Foo.kt" to "M", delegate.parseGitPorcelainLine(" M app/Foo.kt"))
        assertEquals("new file.kt" to "A", delegate.parseGitPorcelainLine("?? \"new file.kt\""))
        assertEquals("old/Bar.kt" to "D", delegate.parseGitPorcelainLine(" D old/Bar.kt"))
        assertEquals("new/Name.kt" to "M", delegate.parseGitPorcelainLine("R  old/Name.kt -> new/Name.kt"))
        assertNull(delegate.parseGitPorcelainLine(""))
    }

    @Test
    fun `parseGitNumstatLine handles normal binary and spaced paths`() {
        assertEquals(Triple("src/Main.kt", 12, 4), delegate.parseGitNumstatLine("12\t4\tsrc/Main.kt"))
        assertEquals(Triple("assets/icon.png", 0, 0), delegate.parseGitNumstatLine("-\t-\tassets/icon.png"))
        assertEquals(Triple("dir/my file.kt", 3, 1), delegate.parseGitNumstatLine("3\t1\t\"dir/my file.kt\""))
        assertNull(delegate.parseGitNumstatLine("invalid"))
    }
}
