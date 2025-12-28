package com.theblankstate.libri.datamodel

data class DownloadedBook(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val filePath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val fileUri: String? = null,
    val format: BookFormat = BookFormat.PDF,
    val source: BookSource = BookSource.ARCHIVE_ORG,
    val gutenbergId: Int? = null,
    val standardEbooksId: String? = null
)
