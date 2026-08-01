package com.labeltruth.app.domain

import com.labeltruth.app.domain.model.Grade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanStatsTest {

    @Test
    fun `every band is present even when empty, so the row keeps its shape`() {
        val distribution = ScanStats.distribution(listOf(95))
        assertEquals(Grade.entries.size, distribution.size)
        assertEquals(1, distribution[Grade.EXCELLENT])
        assertEquals(0, distribution[Grade.BAD])
    }

    @Test
    fun `scores are counted into the band they belong to`() {
        // One per band, using each band's boundary value.
        val distribution = ScanStats.distribution(listOf(80, 60, 40, 20, 0))
        assertEquals(1, distribution[Grade.EXCELLENT])
        assertEquals(1, distribution[Grade.GOOD])
        assertEquals(1, distribution[Grade.FAIR])
        assertEquals(1, distribution[Grade.POOR])
        assertEquals(1, distribution[Grade.BAD])
    }

    @Test
    fun `the counts always add up to the number of scans`() {
        val scores = listOf(100, 91, 74, 55, 39, 12, 0, 83)
        val distribution = ScanStats.distribution(scores)
        assertEquals(scores.size, distribution.values.sum())
    }

    @Test
    fun `no scans produces an empty distribution rather than a crash`() {
        val distribution = ScanStats.distribution(emptyList())
        assertEquals(0, distribution.values.sum())
    }

    /**
     * Zero is a real score meaning "avoid this". Reporting it when there is
     * simply nothing to average would invent a verdict out of an empty list.
     */
    @Test
    fun `average is null with no scans, not zero`() {
        assertNull(ScanStats.averageScore(emptyList()))
    }

    @Test
    fun `average is the mean of the stored scores`() {
        assertEquals(50, ScanStats.averageScore(listOf(40, 60)))
        assertEquals(90, ScanStats.averageScore(listOf(90)))
    }
}
