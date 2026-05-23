package com.theblankstate.libri.util

import com.theblankstate.libri.datamodel.LibraryBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookIdentityTest {
    @Test
    fun prefersNormalizedIsbn() {
        val key = BookIdentity.forLibraryBook(
            LibraryBook(title = "Effective Java", author = "Joshua Bloch", isbn = "978-0-13-468599-1")
        )

        assertEquals(BookIdentityType.ISBN, key.type)
        assertEquals("isbn_9780134685991", key.key)
    }

    @Test
    fun fallsBackToOpenLibraryId() {
        val key = BookIdentity.forLibraryBook(
            LibraryBook(title = "Book", author = "Author", openLibraryId = "/works/OL123W")
        )

        assertEquals(BookIdentityType.OPEN_LIBRARY, key.type)
        assertEquals("ol_works_ol123w", key.key)
    }

    @Test
    fun customFingerprintIsStableAndFirebaseSafe() {
        val first = BookIdentity.forLibraryBook(LibraryBook(title = "A Book!", author = "Some Author"))
        val second = BookIdentity.forLibraryBook(LibraryBook(title = "A Book", author = "Some Author"))

        assertEquals(first, second)
        assertTrue(first.key.startsWith("custom_a_book_some_author_"))
        assertFalse(first.key.any { it in ".#$[]/" })
    }
}
