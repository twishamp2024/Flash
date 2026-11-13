package org.cutwire.drift

import org.cutwire.drift.core.model.formatDuration
import org.cutwire.drift.core.model.formatTimecode
import org.cutwire.drift.core.model.msToUs
import org.cutwire.drift.core.model.usToMs
import org.cutwire.drift.core.model.usToSeconds
import org.cutwire.drift.core.model.secondsToUs
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeExtTest {

    @Test
    fun testTimeConversions() {
        val us = 1_500_000L
        assertEquals(1500L, us.usToMs())
        assertEquals(1.5, us.usToSeconds(), 0.0001)

        val ms = 2500L
        assertEquals(2_500_000L, ms.msToUs())

        val sec = 3.25
        assertEquals(3_250_000L, sec.secondsToUs())
    }

    @Test
    fun testFormatTimecode() {
        // Exactly 0
        assertEquals("00:00.00", 0L.formatTimecode(30))

        // 1 second and 15 frames at 30fps = 1_500_000 Us
        assertEquals("00:01.15", 1_500_000L.formatTimecode(30))

        // 1 minute, 23 seconds, 10 frames = 83_333_333 Us
        val timeUs = (83L * 1_000_000L) + (10L * 1_000_000L / 30L)
        assertEquals("01:23.10", timeUs.formatTimecode(30))
    }

    @Test
    fun testFormatDuration() {
        assertEquals("45s", 45_000_000L.formatDuration())
        assertEquals("2m 15s", 135_000_000L.formatDuration())
    }
}
