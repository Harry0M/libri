package com.theblankstate.libri.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnUtilsTest {
    @Test
    fun normalizesValidIsbn13FromNoisyBarcodeText() {
        assertEquals("9780134685991", IsbnUtils.normalize("ISBN 978-0-13-468599-1 51299"))
    }

    @Test
    fun rejectsFiveDigitSupplement() {
        assertNull(IsbnUtils.normalize("51299"))
    }

    @Test
    fun validatesIsbn10WithXCheckDigit() {
        assertEquals("097522980X", IsbnUtils.normalize("0-9752298-0-X"))
        assertTrue(IsbnUtils.isValidIsbn10("097522980X"))
    }

    @Test
    fun rejectsBadChecksum() {
        assertFalse(IsbnUtils.isValidIsbn13("9780134685990"))
        assertNull(IsbnUtils.normalize("9780134685990"))
    }
}
