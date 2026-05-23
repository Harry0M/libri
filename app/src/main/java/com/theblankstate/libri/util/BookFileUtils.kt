package com.theblankstate.libri.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.theblankstate.libri.datamodel.BookFormat
import java.security.MessageDigest
import java.util.Locale

object BookFileUtils {
    val supportedMimeTypes = arrayOf(
        "application/pdf",
        "application/epub+zip",
        "application/octet-stream",
        "text/plain",
        "text/html",
        "application/xhtml+xml"
    )

    private val supportedExtensions = setOf("pdf", "epub", "txt", "text", "html", "htm", "xhtml")

    fun detectFormat(context: Context, uri: Uri): BookFormat? {
        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val displayName = displayName(context, uri)
        val path = uri.path
        return detectFormat(mimeType, displayName ?: path)
    }

    fun detectFormat(mimeType: String?, nameOrPath: String?): BookFormat? {
        val mime = mimeType.orEmpty().lowercase(Locale.US)
        val name = nameOrPath.orEmpty().lowercase(Locale.US)
        return when {
            mime.contains("epub") || name.endsWith(".epub") -> BookFormat.EPUB
            mime.contains("pdf") || name.endsWith(".pdf") -> BookFormat.PDF
            mime.contains("html") || mime.contains("xhtml") ||
                name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".xhtml") -> BookFormat.HTML
            mime.startsWith("text/plain") || name.endsWith(".txt") || name.endsWith(".text") -> BookFormat.TXT
            else -> null
        }
    }

    fun isSupported(context: Context, uri: Uri): Boolean = detectFormat(context, uri) != null

    fun isSupportedName(name: String): Boolean {
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.US)
        return ext in supportedExtensions
    }

    fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else {
                        null
                    }
                }
        }.getOrNull() ?: uri.lastPathSegment
    }

    fun titleFromName(name: String): String {
        val cleaned = name.substringBeforeLast('.', missingDelimiterValue = name)
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
        return cleaned.ifBlank { "Local book" }
    }

    fun titleFromUri(context: Context, uri: Uri): String {
        return titleFromName(displayName(context, uri) ?: uri.lastPathSegment ?: "Local book")
    }

    fun safeFilename(title: String, format: BookFormat): String {
        val base = title.ifBlank { "local_book" }
            .replace(Regex("[^a-zA-Z0-9.-]"), "_")
            .trim('_')
            .ifBlank { "local_book" }
        return "$base.${format.extension}"
    }

    fun stableLocalId(uri: Uri): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(uri.toString().toByteArray(Charsets.UTF_8))
        val hash = bytes.joinToString("") { "%02x".format(it) }.take(24)
        return "local_$hash"
    }
}
