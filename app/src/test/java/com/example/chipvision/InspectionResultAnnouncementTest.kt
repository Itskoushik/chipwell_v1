package com.example.chipvision

import com.example.chipvision.audio.InspectionResultAnnouncement
import com.example.chipvision.engine.InspectionResultDelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InspectionResultAnnouncementTest {
    @Test
    fun passAnnouncementContainsOnlyTheAggregateResult() {
        assertEquals("Pass", InspectionResultAnnouncement.PASS)
        assertEquals(1_000L, InspectionResultDelay.PASS_DELAY_MS)
        assertFalse(InspectionResultAnnouncement.PASS.contains("chip", ignoreCase = true))
        assertFalse(InspectionResultAnnouncement.PASS.contains("orientation", ignoreCase = true))
    }

    @Test
    fun failAnnouncementContainsOnlyTheAggregateResult() {
        assertEquals("Fail", InspectionResultAnnouncement.FAIL)
        assertFalse(InspectionResultAnnouncement.FAIL.contains("chip", ignoreCase = true))
        assertFalse(InspectionResultAnnouncement.FAIL.contains("orientation", ignoreCase = true))
    }
}
