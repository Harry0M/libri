package com.theblankstate.libri.data

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.theblankstate.libri.datamodel.ArchiveDownloadOption
import com.theblankstate.libri.datamodel.BookFormat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "InternetArchiveRepo"

class InternetArchiveRepository {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getDownloadOptions(identifier: String): List<ArchiveDownloadOption> {
        if (identifier.isBlank()) return emptyList()

        return try {
            val request = Request.Builder()
                .url("https://archive.org/metadata/$identifier")
                .header("User-Agent", "Libri/1.0 Android (https://github.com/Harry0M/libri)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()

                val body = response.body?.string() ?: return emptyList()
                val files = JsonParser.parseString(body)
                    .asJsonObject
                    .getAsJsonArray("files")
                    ?: return emptyList()

                files.mapNotNull { element ->
                    val file = element as? JsonObject ?: return@mapNotNull null
                    val name = file.get("name")?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val format = file.get("format")?.asString.orEmpty()
                    val size = file.get("size")?.asString?.toLongOrNull()
                    if (!isPublicDownloadCandidate(name, format, file)) return@mapNotNull null

                    val option = resolveOption(identifier, name, format, size)
                    option
                }
                    .distinctBy { it.label }
                    .sortedBy { option ->
                        when (option.readerFormat) {
                            BookFormat.EPUB -> 0
                            BookFormat.PDF -> 1
                            BookFormat.TXT -> 2
                            BookFormat.HTML -> 3
                            null -> 4
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load download options for $identifier", e)
            emptyList()
        }
    }

    /**
     * Search Internet Archive by ISBN. Returns the first matching identifier, or null.
     * Used as a fallback when a book's direct IA identifier is unavailable.
     */
    suspend fun searchByIsbn(isbn: String): String? {
        if (isbn.isBlank()) return null
        return try {
            val query = URLEncoder.encode("isbn:$isbn", "UTF-8")
            val url = "https://archive.org/advancedsearch.php?q=$query&fl[]=identifier&rows=1&output=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Libri/1.0 Android (https://github.com/Harry0M/libri)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val docs = JsonParser.parseString(body)
                    .asJsonObject
                    .getAsJsonObject("response")
                    ?.getAsJsonArray("docs")
                    ?: return null
                docs.firstOrNull()
                    ?.asJsonObject
                    ?.get("identifier")
                    ?.asString
            }
        } catch (e: Exception) {
            Log.e(TAG, "ISBN search failed for $isbn", e)
            null
        }
    }

    /**
     * Search Internet Archive by title+author. Returns the first matching identifier, or null.
     * Last-resort fallback when no identifier or ISBN is available.
     */
    suspend fun searchByTitleAuthor(title: String, author: String?): String? {
        if (title.isBlank()) return null
        return try {
            val query = buildString {
                append("title:(${URLEncoder.encode(title, "UTF-8")})")
                if (!author.isNullOrBlank()) {
                    append("+AND+creator:(${URLEncoder.encode(author, "UTF-8")})")
                }
                append("+AND+mediatype:texts")
            }
            val url = "https://archive.org/advancedsearch.php?q=$query&fl[]=identifier&rows=1&output=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Libri/1.0 Android (https://github.com/Harry0M/libri)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val docs = JsonParser.parseString(body)
                    .asJsonObject
                    .getAsJsonObject("response")
                    ?.getAsJsonArray("docs")
                    ?: return null
                docs.firstOrNull()
                    ?.asJsonObject
                    ?.get("identifier")
                    ?.asString
            }
        } catch (e: Exception) {
            Log.e(TAG, "Title/author search failed for $title", e)
            null
        }
    }

    private fun resolveOption(
        identifier: String,
        fileName: String,
        archiveFormat: String,
        sizeBytes: Long?
    ): ArchiveDownloadOption? {
        val lowerName = fileName.lowercase()
        val lowerFormat = archiveFormat.lowercase()
        val type = when {
            lowerName.endsWith(".epub") || lowerFormat.contains("epub") -> ArchiveType.EPUB
            lowerName.endsWith(".pdf") || lowerFormat.contains("pdf") -> ArchiveType.PDF
            lowerName.endsWith("_djvu.txt") || lowerName.endsWith(".txt") || lowerFormat == "djvutxt" -> ArchiveType.TEXT
            lowerName.endsWith(".html") || lowerName.endsWith(".htm") || lowerFormat.contains("html") -> ArchiveType.HTML
            lowerName.endsWith(".mobi") || lowerFormat.contains("mobi") -> ArchiveType.MOBI
            else -> null
        } ?: return null

        return ArchiveDownloadOption(
            label = type.label,
            fileName = fileName,
            url = "https://archive.org/download/$identifier/${encodeArchivePath(fileName)}",
            mimeType = type.mimeType,
            extension = type.extension,
            readerFormat = type.readerFormat,
            sizeBytes = sizeBytes
        )
    }

    private fun isPublicDownloadCandidate(
        fileName: String,
        archiveFormat: String,
        file: JsonObject
    ): Boolean {
        val lowerName = fileName.lowercase()
        val lowerFormat = archiveFormat.lowercase()
        val isPrivate = file.get("private")?.asString?.equals("true", ignoreCase = true) == true
        val protected = lowerName.contains("encrypted") ||
            lowerName.contains("_lcp") ||
            lowerName.endsWith(".acsm") ||
            lowerFormat.contains("encrypted") ||
            lowerFormat.contains("lcp") ||
            lowerFormat.contains("acs")

        val metadataFile = lowerName.contains("_meta.") ||
            lowerName.contains("_files.") ||
            lowerName.endsWith("_reviews.xml") ||
            lowerName.endsWith("_djvu.xml") ||
            lowerName.endsWith(".torrent")

        return !isPrivate && !protected && !metadataFile
    }

    private fun encodeArchivePath(path: String): String {
        return path.split("/").joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
    }

    private enum class ArchiveType(
        val label: String,
        val extension: String,
        val mimeType: String,
        val readerFormat: BookFormat?
    ) {
        EPUB("ePub", "epub", BookFormat.EPUB.mimeType, BookFormat.EPUB),
        PDF("PDF", "pdf", BookFormat.PDF.mimeType, BookFormat.PDF),
        TEXT("Plain text", "txt", BookFormat.TXT.mimeType, BookFormat.TXT),
        HTML("HTML", "html", BookFormat.HTML.mimeType, BookFormat.HTML),
        MOBI("MOBI", "mobi", "application/x-mobipocket-ebook", null)
    }
}
