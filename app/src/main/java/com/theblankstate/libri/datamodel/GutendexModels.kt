package com.theblankstate.libri.datamodel

import com.google.gson.annotations.SerializedName

/**
 * Gutendex API Response Models
 * API Documentation: https://gutendex.com/
 */

data class GutendexResponse(
    @SerializedName("count") val count: Int,
    @SerializedName("next") val next: String?,
    @SerializedName("previous") val previous: String?,
    @SerializedName("results") val results: List<GutendexBook>
)

data class GutendexBook(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("authors") val authors: List<GutendexAuthor>?,
    @SerializedName("translators") val translators: List<GutendexAuthor>?,
    @SerializedName("subjects") val subjects: List<String>?,
    @SerializedName("bookshelves") val bookshelves: List<String>?,
    @SerializedName("languages") val languages: List<String>?,
    @SerializedName("copyright") val copyright: Boolean?,
    @SerializedName("media_type") val mediaType: String?,
    @SerializedName("formats") val formats: Map<String, String>?,
    @SerializedName("download_count") val downloadCount: Int?
) {
    val authorNames: String
        get() = authors?.joinToString(", ") { it.name } ?: "Unknown Author"
    
    val coverUrl: String?
        get() = formats?.get("image/jpeg")
    
    val epubUrl: String?
        get() = formats?.get("application/epub+zip")
    
    val textUrl: String?
        get() = formats?.get("text/plain; charset=utf-8") 
            ?: formats?.get("text/plain; charset=us-ascii")
            ?: formats?.get("text/plain")
    
    val htmlUrl: String?
        get() = formats?.get("text/html; charset=utf-8")
            ?: formats?.get("text/html")
    
    val pdfUrl: String?
        get() = formats?.get("application/pdf")
    
    // Get the best available download format in preference order
    fun getBestDownloadFormat(): Pair<String, BookFormat>? {
        return when {
            epubUrl != null -> epubUrl!! to BookFormat.EPUB
            pdfUrl != null -> pdfUrl!! to BookFormat.PDF
            textUrl != null -> textUrl!! to BookFormat.TXT
            else -> null
        }
    }
}

data class GutendexAuthor(
    @SerializedName("name") val name: String,
    @SerializedName("birth_year") val birthYear: Int?,
    @SerializedName("death_year") val deathYear: Int?
)

/**
 * Book format enumeration
 */
enum class BookFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    EPUB("epub", "application/epub+zip"),
    TXT("txt", "text/plain"),
    HTML("html", "text/html")
}

/**
 * Book source enumeration
 */
enum class BookSource(val displayName: String) {
    ARCHIVE_ORG("Internet Archive"),
    GUTENBERG("Project Gutenberg"),
    STANDARD_EBOOKS("Standard Ebooks"),
    LIBRIVOX("LibriVox"),
    LOCAL_IMPORT("Local Import")
}
