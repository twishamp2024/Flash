package org.cutwire.drift.feature.timeline

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object TimelineScaleHelper {

    // Microseconds per dp constants
    const val MIN_US_PER_DP = 10_000L   // Max Zoom: 1 second = 100 dp
    const val DEFAULT_US_PER_DP = 50_000L // Normal Zoom: 1 second = 20 dp
    const val MAX_US_PER_DP = 500_000L  // Min Zoom: 1 second = 2 dp

    fun timeUsToDp(timeUs: Long, usPerDp: Long): Dp {
        val safeUsPerDp = usPerDp.coerceAtLeast(1L)
        return (timeUs.toFloat() / safeUsPerDp).dp
    }

    fun dpToTimeUs(dpValue: Float, usPerDp: Long): Long {
        return (dpValue * usPerDp).toLong().coerceAtLeast(0L)
    }

    /**
     * Determines the optimal tick step in microseconds for the timeline ruler.
     */
    fun calculateRulerTickStepUs(usPerDp: Long): Long {
        return when {
            usPerDp <= 20_000L -> 500_000L       // 0.5s ticks
            usPerDp <= 80_000L -> 1_000_000L     // 1s ticks
            usPerDp <= 200_000L -> 2_000_000L    // 2s ticks
            else -> 5_000_000L                   // 5s ticks
        }
    }
}
