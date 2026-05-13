package com.theblankstate.libri.datamodel

/**
 * Controls which source the search should target.
 */
enum class SearchSource(val label: String) {
    ALL("All"),
    OPEN_LIBRARY("Open Library"),
    GUTENBERG("Gutenberg"),
    READABLE("Readable"),
    STANDARD_EBOOKS("Standard Ebooks"),
    LIBRIVOX("LibriVox")
}
