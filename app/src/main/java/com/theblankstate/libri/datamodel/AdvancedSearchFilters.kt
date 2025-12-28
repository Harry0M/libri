package com.theblankstate.libri.datamodel

data class AdvancedSearchFilters(
    // Basic search
    val query: String? = null,
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    
    // Advanced filters
    val isbn: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val yearStart: Int? = null,
    val yearEnd: Int? = null,
    val hasFulltext: Boolean? = null,
    
    // Sorting
    val sortBy: SortOption = SortOption.RELEVANCE
)

enum class SortOption(val value: String, val displayName: String) {
    RELEVANCE("", "Relevance"),
    NEWEST("new", "Newest First"),
    OLDEST("old", "Oldest First"),
    RATING("rating", "Highest Rated"),
    EDITIONS("editions", "Most Editions"),
    RANDOM("random", "Random")
}

// Common language options
object Languages {
    val options = listOf(
        "eng" to "English",
        "spa" to "Spanish",
        "fre" to "French",
        "ger" to "German",
        "ita" to "Italian",
        "por" to "Portuguese",
        "rus" to "Russian",
        "jpn" to "Japanese",
        "chi" to "Chinese",
        "ara" to "Arabic",
        "hin" to "Hindi",
        "und" to "Any Language"
    )
}
