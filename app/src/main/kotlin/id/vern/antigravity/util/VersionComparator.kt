package id.vern.antigravity.util

/**
 * Single source of truth for comparing version strings.
 *
 * The app previously had two divergent implementations (one here, one in
 * `CoreUpdateManager`), and the one in `UpdateChecker` had two defects:
 *  - `replace("v", "")` stripped every `v` anywhere in the string, so
 *    `dev1.2` became `de1.2`;
 *  - its fallback compared the remaining strings lexicographically, which makes
 *    `"1.0.10" > "1.0.9"` false and can suppress a real update.
 *
 * Because this decides whether code on the device gets replaced, it is kept
 * small, pure, and unit-testable (no Android dependencies).
 */
object VersionComparator {

    /**
     * Parses a version into its leading numeric run.
     *
     * Strips a leading `v`/`V` only, then keeps the numeric components until
     * the first non-numeric segment (so `1.0.6-beta1` -> [1, 0, 6]).
     * Returns null when no numeric component can be read at all.
     */
    fun parse(version: String): List<Int>? {
        val trimmed = version.trim().removePrefix("v").removePrefix("V")
        if (trimmed.isEmpty()) return null
        val parts = mutableListOf<Int>()
        for (segment in trimmed.split(".", "-", "+", "_")) {
            val numeric = segment.takeWhile { it.isDigit() }
            if (numeric.isEmpty()) break
            parts.add(numeric.toIntOrNull() ?: break)
        }
        return parts.ifEmpty { null }
    }

    /**
     * Compares two versions by numeric precedence.
     *
     * Returns a negative number when [a] < [b], zero when equal, and a positive
     * number when [a] > [b]. Missing components count as zero, so `1.2` equals
     * `1.2.0`. Unparseable input falls back to a case-insensitive string
     * comparison rather than throwing, so update checks never crash.
     */
    fun compare(a: String, b: String): Int {
        val left = parse(a)
        val right = parse(b)
        if (left == null || right == null) {
            return a.trim().lowercase().compareTo(b.trim().lowercase())
        }
        val length = maxOf(left.size, right.size)
        for (i in 0 until length) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    /** True when [candidate] is strictly newer than [current]. */
    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0
}
