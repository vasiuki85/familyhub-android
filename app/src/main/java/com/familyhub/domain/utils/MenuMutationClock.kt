package com.familyhub.domain.utils

/**
 * Monotonic local timestamps for menu mutations.
 *
 * [nextUpdatedAt] must never compute `current + 1` without [Math.addExact].
 * Replace operations pass [maxCurrentUpdatedAt] of the entire changed/removed cohort
 * so the stamp is strictly greater than every mutated row.
 */
fun interface MenuMutationClock {
    /**
     * @param maxCurrentUpdatedAt max `updatedAt` among parent + changed/removed children
     * @return stamp strictly greater than [maxCurrentUpdatedAt], and ≥ wall clock when possible
     */
    fun nextUpdatedAt(maxCurrentUpdatedAt: Long): Long
}

class DefaultMenuMutationClock(
    private val wallClock: () -> Long = { System.currentTimeMillis() }
) : MenuMutationClock {
    override fun nextUpdatedAt(maxCurrentUpdatedAt: Long): Long {
        require(maxCurrentUpdatedAt >= 0L) {
            "updatedAt must be non-negative, got $maxCurrentUpdatedAt"
        }
        val incremented = try {
            Math.addExact(maxCurrentUpdatedAt, 1L)
        } catch (_: ArithmeticException) {
            throw MenuTimestampOverflowException()
        }
        val now = wallClock().coerceAtLeast(0L)
        return maxOf(now, incremented)
    }
}
