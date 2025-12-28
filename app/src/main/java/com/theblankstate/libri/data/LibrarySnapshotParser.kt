package com.theblankstate.libri.data

import com.google.firebase.database.DataSnapshot
import com.theblankstate.libri.datamodel.BookFormat
import com.theblankstate.libri.datamodel.LibraryBook
import com.theblankstate.libri.datamodel.ReadingStatus
import android.util.Log

/**
 * Utilities to safely parse a Firebase DataSnapshot into a LibraryBook without crashing
 * when enum values or types in the database are invalid.
 */
object LibrarySnapshotParser {
    fun parseBookFormat(value: String?): BookFormat? {
        val v0 = value?.trim()
        if (v0.isNullOrEmpty()) return null
        // Normalize to upper-case to accept 'epub' or 'EPUB'
        val v = v0.uppercase()
        return try {
            BookFormat.valueOf(v)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun buildLibraryBookFromMap(map: Map<String, Any?>, key: String?): LibraryBook {
        val id = map["id"] as? String ?: key ?: ""
        val title = map["title"] as? String ?: ""
        val author = map["author"] as? String ?: ""
        val coverUrl = map["coverUrl"] as? String
        val description = map["description"] as? String
        val isbn = map["isbn"] as? String
        val openLibraryId = map["openLibraryId"] as? String
        val internetArchiveId = map["internetArchiveId"] as? String
        val gutenbergId = (map["gutenbergId"] as? Number)?.toInt()
        val ebookAccess = map["ebookAccess"] as? String
        val status = map["status"] as? String ?: ReadingStatus.WANT_TO_READ.name
        val rating = (map["rating"] as? Number)?.toFloat() ?: 0f
        val comment = map["comment"] as? String ?: ""
        val isFavorite = map["isFavorite"] as? Boolean ?: false
        val dateAdded = (map["dateAdded"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val dateStarted = (map["dateStarted"] as? Number)?.toLong()
        val dateFinished = (map["dateFinished"] as? Number)?.toLong()
        val currentPage = (map["currentPage"] as? Number)?.toInt() ?: 0
        val totalPages = (map["totalPages"] as? Number)?.toInt() ?: 0
        val publisher = map["publisher"] as? String
        val localFilePath = map["localFilePath"] as? String
        val localFileFormatRaw = map["localFileFormat"] as? String
        val localFileFormat = parseBookFormat(localFileFormatRaw)

        return LibraryBook(
            id = id,
            title = title,
            author = author,
            coverUrl = coverUrl,
            description = description,
            isbn = isbn,
            openLibraryId = openLibraryId,
            internetArchiveId = internetArchiveId,
            ebookAccess = ebookAccess,
            status = status,
            rating = rating,
            comment = comment,
            isFavorite = isFavorite,
            dateAdded = dateAdded,
            dateStarted = dateStarted,
            dateFinished = dateFinished,
            currentPage = currentPage,
            totalPages = totalPages,
            publisher = publisher,
            localFilePath = localFilePath,
            localFileFormat = localFileFormat
            ,
            gutenbergId = gutenbergId
        )
    }

    fun buildLibraryBookFromSnapshot(snapshot: DataSnapshot): LibraryBook? {
        return try {
            val map = snapshot.value as? Map<String, Any?> ?: emptyMap()
            buildLibraryBookFromMap(map, snapshot.key)
        } catch (e: Exception) {
            Log.e("LibrarySnapshotParser", "Error building LibraryBook from snapshot key=${snapshot.key}", e)
            null
        }
    }
}
