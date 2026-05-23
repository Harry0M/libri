package com.theblankstate.libri.util

import com.theblankstate.libri.datamodel.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingProgressUtilsTest {
    @Test
    fun clampsCurrentPageToTotal() {
        val update = ReadingProgressUtils.normalize(250, 200, ReadingStatus.IN_PROGRESS)

        assertEquals(200, update.currentPage)
        assertEquals(200, update.totalPages)
        assertEquals(ReadingStatus.FINISHED, update.status)
    }

    @Test
    fun startsReadingWhenProgressIsAddedFromWantToRead() {
        val update = ReadingProgressUtils.normalize(10, 200, ReadingStatus.WANT_TO_READ)

        assertEquals(10, update.currentPage)
        assertEquals(ReadingStatus.IN_PROGRESS, update.status)
    }

    @Test
    fun keepsUnknownTotalProgressNonNegative() {
        val update = ReadingProgressUtils.normalize(-5, 0, ReadingStatus.IN_PROGRESS)

        assertEquals(0, update.currentPage)
        assertEquals(0, update.totalPages)
        assertNull(update.status)
    }
}
