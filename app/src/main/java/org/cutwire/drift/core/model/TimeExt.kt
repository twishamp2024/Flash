package org.cutwire.drift.core.model

import java.util.Locale

fun Long.usToMs(): Long = this / 1_000L

fun Long.msToUs(): Long = this * 1_000L

fun Long.usToSeconds(): Double = this / 1_000_000.0

fun Double.secondsToUs(): Long = (this * 1_000_000.0).toLong()

/**
 * Format microseconds as timecode MM:SS.SS
 */
fun Long.formatTimecode(frameRate: Int = 30): String {
    val totalSeconds = this / 1_000_000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val remainingUs = this % 1_000_000L
    val frames = (((remainingUs * frameRate) + 500_000L) / 1_000_000L).toInt().coerceAtMost(frameRate - 1)

    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d.%02d", hours, minutes, seconds, frames)
    } else {
        String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, frames)
    }
}

/**
 * Format microseconds as duration string "Xm Ys"
 */
fun Long.formatDuration(): String {
    val totalSeconds = this / 1_000_000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}
