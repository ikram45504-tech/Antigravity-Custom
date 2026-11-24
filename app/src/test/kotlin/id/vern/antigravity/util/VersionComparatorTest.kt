package id.vern.antigravity.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version comparison decides whether installed code is replaced, so the
 * regressions covered here are security-relevant, not cosmetic.
 */
class VersionComparatorTest {

    @Test
    fun `numeric components compare by value not lexicographically`() {
        // The previous implementation fell back to String comparison, where
        // "1.0.10" < "1.0.9", silently suppressing the update.
        assertTrue(VersionComparator.isNewer("1.0.10", "1.0.9"))
        assertFalse(VersionComparator.isNewer("1.0.9", "1.0.10"))
        assertTrue(VersionComparator.isNewer("1.10.0", "1.9.0"))
    }

    @Test
    fun `missing components are treated as zero`() {
        assertEquals(0, VersionComparator.compare("1.2", "1.2.0"))
        assertEquals(0, VersionComparator.compare("1.2.0", "1.2"))
        assertTrue(VersionComparator.isNewer("1.2.1", "1.2"))
    }

    @Test
    fun `only a leading v is stripped`() {
        assertEquals(0, VersionComparator.compare("v1.0.6", "1.0.6"))
        assertEquals(0, VersionComparator.compare("V1.0.6", "1.0.6"))
        // The old replace("v","") turned "dev1.2" into "de1.2" and could not
        // parse it at all; parsing must simply fail cleanly instead.
        assertNull(VersionComparator.parse("dev1.2"))
    }

    @Test
    fun `pre-release suffixes compare on the numeric part`() {
        assertEquals(0, VersionComparator.compare("1.0.6-beta1", "1.0.6"))
        assertTrue(VersionComparator.isNewer("1.0.7-rc1", "1.0.6"))
    }

    @Test
    fun `equal versions are not newer`() {
        assertFalse(VersionComparator.isNewer("1.0.6", "1.0.6"))
        assertFalse(VersionComparator.isNewer("v1.0.6", "1.0.6"))
    }

    @Test
    fun `unparseable input falls back to string compare without throwing`() {
        // Must never throw: this runs inside update checks.
        VersionComparator.compare("nightly", "1.0")
        VersionComparator.compare("", "1.0")
        VersionComparator.compare("1.0", "")
        assertFalse(VersionComparator.isNewer("1.0", "1.0"))
    }

    @Test
    fun `out of range components do not overflow`() {
        // Integer.MAX_VALUE-ish segments must not throw.
        VersionComparator.compare("99999999999999999999.1", "1.0")
        VersionComparator.compare("1.0", "99999999999999999999.1")
    }
}
