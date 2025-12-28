package com.theblankstate.libri.datamodel

import com.google.firebase.database.PropertyName

enum class ReadingStatus(val displayName: String) {
    WANT_TO_READ("Want to Read"),
    IN_PROGRESS("In Progress"),
    FINISHED("Finished"),
    ON_HOLD("On Hold"),
    DROPPED("Dropped")
}

data class LibraryBook(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val coverUrl: String? = null,
    val description: String? = null,
    val isbn: String? = null,
    val openLibraryId: String? = null,
    val internetArchiveId: String? = null,
    val gutenbergId: Int? = null,
    val ebookAccess: String? = null,
    
    @PropertyName("status")
    val status: String = ReadingStatus.WANT_TO_READ.name,
    
    val rating: Float = 0f,
    val comment: String = "",
    val isFavorite: Boolean = false,
    
    val dateAdded: Long = System.currentTimeMillis(),
    val dateStarted: Long? = null,
    val dateFinished: Long? = null,
    
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    
    val publisher: String? = null,
    val localFilePath: String? = null,
    val localFileFormat: BookFormat? = null
) {
    val readingStatusEnum: ReadingStatus
        get() = try {
            ReadingStatus.valueOf(status)
        } catch (e: Exception) {
            ReadingStatus.WANT_TO_READ
        }
    
    val readingProgress: Float
        get() = if (totalPages > 0) (currentPage.toFloat() / totalPages.toFloat()) * 100 else 0f
}
