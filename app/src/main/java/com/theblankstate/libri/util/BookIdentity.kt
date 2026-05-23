package com.theblankstate.libri.util

import com.theblankstate.libri.datamodel.LibraryBook
import java.security.MessageDigest
import java.util.Locale

enum class BookIdentityType {
    ISBN,
    OPEN_LIBRARY,
    INTERNET_ARCHIVE,
    GUTENBERG,
    TITLE_AUTHOR
}

data class BookIdentityKey(
    val key: String,
    val type: BookIdentityType
)

object BookIdentity {
    fun forLibraryBook(book: LibraryBook): BookIdentityKey {
        book.publicReviewKey?.takeIf { it.isNotBlank() }?.let { stored ->
            return BookIdentityKey(stored, inferType(stored))
        }

        IsbnUtils.normalize(book.isbn)?.let {
            return BookIdentityKey(firebaseSafe("isbn_$it"), BookIdentityType.ISBN)
        }

        book.openLibraryId?.takeIf { it.isNotBlank() }?.let {
            return BookIdentityKey(firebaseSafe("ol_${it.trim().lowercase(Locale.US)}"), BookIdentityType.OPEN_LIBRARY)
        }

        book.internetArchiveId?.takeIf { it.isNotBlank() }?.let {
            return BookIdentityKey(firebaseSafe("ia_${it.trim().lowercase(Locale.US)}"), BookIdentityType.INTERNET_ARCHIVE)
        }

        book.gutenbergId?.let {
            return BookIdentityKey("gutenberg_$it", BookIdentityType.GUTENBERG)
        }

        return custom(book.title, book.author)
    }

    fun custom(title: String, author: String): BookIdentityKey {
        val normalized = "${slug(title)}_${slug(author)}".trim('_').ifBlank { "unknown_book" }
        val digest = sha1(normalized).take(12)
        return BookIdentityKey(firebaseSafe("custom_${normalized.take(80)}_$digest"), BookIdentityType.TITLE_AUTHOR)
    }

    fun legacyIsbnKey(book: LibraryBook): String? = IsbnUtils.normalize(book.isbn)

    fun firebaseSafe(raw: String): String {
        return raw.trim()
            .lowercase(Locale.US)
            .replace(Regex("""[.#$\[\]/]"""), "_")
            .replace(Regex("""\s+"""), "_")
            .replace(Regex("""_+"""), "_")
            .trim('_')
            .ifBlank { "unknown_book" }
    }

    private fun inferType(key: String): BookIdentityType {
        return when {
            key.startsWith("isbn_") -> BookIdentityType.ISBN
            key.startsWith("ol_") -> BookIdentityType.OPEN_LIBRARY
            key.startsWith("ia_") -> BookIdentityType.INTERNET_ARCHIVE
            key.startsWith("gutenberg_") -> BookIdentityType.GUTENBERG
            else -> BookIdentityType.TITLE_AUTHOR
        }
    }

    private fun slug(value: String): String {
        return value.lowercase(Locale.US)
            .replace(Regex("""[^a-z0-9]+"""), "_")
            .replace(Regex("""_+"""), "_")
            .trim('_')
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
