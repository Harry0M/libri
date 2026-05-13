package com.theblankstate.libri.datamodel

data class ArchiveDownloadOption(
    val label: String,
    val fileName: String,
    val url: String,
    val mimeType: String,
    val extension: String,
    val readerFormat: BookFormat?,
    val sizeBytes: Long? = null
) {
    val canReadInApp: Boolean
        get() = readerFormat != null
}
