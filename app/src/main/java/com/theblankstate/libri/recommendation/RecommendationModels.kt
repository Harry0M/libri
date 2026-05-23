package com.theblankstate.libri.recommendation

import com.theblankstate.libri.datamodel.LibraryBook
import com.theblankstate.libri.datamodel.bookModel

enum class RecommendationEventType {
    BOOK_IMPRESSION,
    BOOK_OPEN,
    READ_START,
    READ_PROGRESS,
    READ_FINISH,
    DOWNLOAD,
    LIBRARY_ADD,
    RATING,
    HIDE,
    SEARCH
}

enum class RecommendationItemSource(val wireName: String) {
    OPEN_LIBRARY("open_library"),
    GUTENBERG("gutenberg"),
    LOCAL_LIBRARY("local_library"),
    UNKNOWN("unknown")
}

data class RecommendationEvent(
    val eventId: String,
    val type: RecommendationEventType,
    val itemId: String? = null,
    val source: RecommendationItemSource = RecommendationItemSource.UNKNOWN,
    val title: String? = null,
    val authors: List<String> = emptyList(),
    val subjects: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val value: Double? = null,
    val query: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class RecommendationSignals(
    val selectedLanguages: Set<String>,
    val selectedAuthors: Set<String>,
    val selectedGenres: Set<String>,
    val recentBookIds: List<String>,
    val recentEvents: List<RecommendationEvent>,
    val hiddenItemIds: Set<String>,
    val finishedItemIds: Set<String>,
    val libraryBooks: List<LibraryBook>,
    val authorWeights: Map<String, Double>,
    val subjectWeights: Map<String, Double>,
    val languageWeights: Map<String, Double>
)

data class RecommendationCandidate(
    val book: bookModel,
    val generator: String,
    val score: Double,
    val reason: String
)

data class RecommendationBookSnapshot(
    val id: String,
    val title: String,
    val authors: List<String>,
    val subjects: List<String>,
    val languages: List<String>,
    val source: String,
    val coverUrl: String?,
    val scoreReason: String
)

data class RecommendationSectionSnapshot(
    val title: String,
    val books: List<bookModel>,
    val items: List<RecommendationBookSnapshot> = emptyList()
)

data class RecommendationProfileSnapshot(
    val authorWeights: Map<String, Double>,
    val subjectWeights: Map<String, Double>,
    val languageWeights: Map<String, Double>,
    val recentBookIds: List<String>,
    val hiddenItemIds: List<String>,
    val finishedItemIds: List<String>,
    val updatedAt: Long = System.currentTimeMillis()
)
