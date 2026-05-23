package com.theblankstate.libri.recommendation

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theblankstate.libri.data.RecommendationSeeds
import com.theblankstate.libri.data.UserPreferencesRepository
import com.theblankstate.libri.datamodel.GutendexBook
import com.theblankstate.libri.datamodel.LibraryBook
import com.theblankstate.libri.datamodel.ReadingStatus
import com.theblankstate.libri.datamodel.bookModel
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max

class RecommendationStore(context: Context) {
    private val dao = RecommendationDatabase.getInstance(context).recommendationDao()
    private val gson = Gson()

    suspend fun recordBookOpen(book: bookModel) {
        upsertBooks(listOf(book))
        recordEvent(
            RecommendationEvent(
                eventId = UUID.randomUUID().toString(),
                type = RecommendationEventType.BOOK_OPEN,
                itemId = RecommendationEngine.canonicalId(book),
                source = RecommendationEngine.sourceFor(book),
                title = book.title,
                authors = book.author_name.orEmpty(),
                subjects = book.subject.orEmpty(),
                languages = book.language.orEmpty()
            )
        )
    }

    suspend fun recordBookOpen(bookKey: String) {
        recordEvent(
            RecommendationEvent(
                eventId = UUID.randomUUID().toString(),
                type = RecommendationEventType.BOOK_OPEN,
                itemId = RecommendationEngine.normalizeItemId(bookKey),
                source = RecommendationItemSource.OPEN_LIBRARY
            )
        )
    }

    suspend fun recordGutenbergOpen(book: GutendexBook) {
        recordEvent(
            RecommendationEvent(
                eventId = UUID.randomUUID().toString(),
                type = RecommendationEventType.BOOK_OPEN,
                itemId = "gutenberg:${book.id}",
                source = RecommendationItemSource.GUTENBERG,
                title = book.title,
                authors = book.authors?.map { it.name }.orEmpty(),
                subjects = (book.subjects.orEmpty() + book.bookshelves.orEmpty()).distinct(),
                languages = book.languages.orEmpty()
            )
        )
    }

    suspend fun recordSearch(query: String) {
        if (query.isBlank()) return
        recordEvent(
            RecommendationEvent(
                eventId = UUID.randomUUID().toString(),
                type = RecommendationEventType.SEARCH,
                query = query.trim(),
                value = 1.0
            )
        )
    }

    suspend fun recordHide(book: bookModel) {
        recordEvent(
            RecommendationEvent(
                eventId = UUID.randomUUID().toString(),
                type = RecommendationEventType.HIDE,
                itemId = RecommendationEngine.canonicalId(book),
                source = RecommendationEngine.sourceFor(book),
                title = book.title
            )
        )
    }

    suspend fun recordEvent(event: RecommendationEvent) {
        dao.upsertEvent(event.toEntity())
        dao.pruneEvents(MAX_EVENT_HISTORY)
    }

    suspend fun upsertBooks(books: List<bookModel>) {
        val now = System.currentTimeMillis()
        dao.upsertItems(
            books.distinctBy { RecommendationEngine.canonicalId(it) }.map { book ->
                RecommendationItemEntity(
                    itemId = RecommendationEngine.canonicalId(book),
                    source = RecommendationEngine.sourceFor(book).wireName,
                    sourceKey = book.key,
                    title = book.title,
                    authorsJson = gson.toJson(book.author_name.orEmpty()),
                    subjectsJson = gson.toJson(book.subject.orEmpty()),
                    languagesJson = gson.toJson(book.language.orEmpty()),
                    publishYear = book.first_publish_year,
                    coverUrl = book.coverUrl,
                    availability = availabilityFor(book),
                    popularity = book.ratings_average,
                    bookJson = gson.toJson(book),
                    lastSeenAt = now
                )
            }
        )
    }

    suspend fun saveHomeSnapshot(sections: List<Pair<String, List<bookModel>>>) {
        val snapshot = sections.map { (title, books) ->
            RecommendationSectionSnapshot(
                title = title,
                books = books,
                items = books.map { book ->
                    RecommendationBookSnapshot(
                        id = RecommendationEngine.canonicalId(book),
                        title = book.title,
                        authors = book.author_name.orEmpty(),
                        subjects = book.subject.orEmpty().take(8),
                        languages = book.language.orEmpty(),
                        source = RecommendationEngine.sourceFor(book).wireName,
                        coverUrl = book.coverUrl,
                        scoreReason = RecommendationEngine.reasonFor(book, title)
                    )
                }
            )
        }
        dao.upsertSnapshot(
            RecommendationSnapshotEntity(
                snapshotKey = HOME_SNAPSHOT_KEY,
                sectionsJson = gson.toJson(snapshot),
                updatedAt = System.currentTimeMillis()
            )
        )
        upsertBooks(sections.flatMap { it.second })
    }

    suspend fun getHomeSnapshot(maxAgeMillis: Long = TimeUnit.HOURS.toMillis(24)): List<Pair<String, List<bookModel>>>? {
        val snapshot = dao.getSnapshot(HOME_SNAPSHOT_KEY) ?: return null
        if (System.currentTimeMillis() - snapshot.updatedAt > maxAgeMillis) return null
        val type = object : TypeToken<List<RecommendationSectionSnapshot>>() {}.type
        val sections = runCatching {
            gson.fromJson<List<RecommendationSectionSnapshot>>(snapshot.sectionsJson, type)
        }.getOrNull().orEmpty()
        return sections
            .map { it.title to it.books }
            .filter { it.second.isNotEmpty() }
            .takeIf { it.isNotEmpty() }
    }

    suspend fun latestHomeSnapshotForSync(): List<RecommendationSectionSnapshot> {
        val snapshot = dao.getSnapshot(HOME_SNAPSHOT_KEY) ?: return emptyList()
        val type = object : TypeToken<List<RecommendationSectionSnapshot>>() {}.type
        return runCatching {
            gson.fromJson<List<RecommendationSectionSnapshot>>(snapshot.sectionsJson, type)
        }.getOrDefault(emptyList())
    }

    suspend fun latestEvents(limit: Int = 25): List<RecommendationEvent> {
        return dao.getRecentEvents(limit).map { it.toModel() }
    }

    suspend fun loadCachedBooks(limit: Int = 200): List<bookModel> {
        return dao.getRecentItems(limit).mapNotNull { entity ->
            runCatching { gson.fromJson(entity.bookJson, bookModel::class.java) }.getOrNull()
        }
    }

    suspend fun loadSignals(
        preferences: UserPreferencesRepository,
        libraryBooks: List<LibraryBook> = emptyList()
    ): RecommendationSignals {
        val selectedLanguages = preferences.getSelectedLanguages()
        val selectedAuthors = preferences.getSelectedAuthors()
        val selectedGenres = preferences.getSelectedGenres()
        val recentBookIds = preferences.getRecentBooks()
        val events = latestEvents(200)
        val hidden = events
            .filter { it.type == RecommendationEventType.HIDE }
            .mapNotNull { it.itemId }
            .toSet()
        val finished = events
            .filter { it.type == RecommendationEventType.READ_FINISH }
            .mapNotNull { it.itemId }
            .toMutableSet()
        libraryBooks
            .filter { it.readingStatusEnum == ReadingStatus.FINISHED }
            .mapTo(finished) { RecommendationEngine.normalizeItemId(it.openLibraryId ?: it.id) }

        val authorWeights = mutableMapOf<String, Double>()
        val subjectWeights = mutableMapOf<String, Double>()
        val languageWeights = mutableMapOf<String, Double>()

        selectedAuthors.forEach { authorWeights.bump(it, 4.0) }
        selectedGenres.forEach { genre ->
            subjectWeights.bump(RecommendationSeeds.normalizeTopic(genre), 3.5)
            subjectWeights.bump(genre, 2.0)
        }
        selectedLanguages.forEach { languageWeights.bump(it, 2.5) }

        events.forEach { event ->
            val weight = eventSignalWeight(event.type, event.value)
            event.authors.forEach { authorWeights.bump(it, weight) }
            event.subjects.take(8).forEach { subjectWeights.bump(it, weight) }
            event.languages.forEach { languageWeights.bump(it, max(0.5, weight / 2.0)) }
        }

        libraryBooks.forEach { book ->
            val weight = when (book.readingStatusEnum) {
                ReadingStatus.FINISHED -> 2.5 + book.rating.coerceAtLeast(0f).toDouble()
                ReadingStatus.IN_PROGRESS -> 3.0
                ReadingStatus.WANT_TO_READ -> 1.5
                ReadingStatus.ON_HOLD -> 0.5
                ReadingStatus.DROPPED -> -4.0
            }
            if (book.author.isNotBlank()) authorWeights.bump(book.author, weight)
        }

        return RecommendationSignals(
            selectedLanguages = selectedLanguages,
            selectedAuthors = selectedAuthors,
            selectedGenres = selectedGenres,
            recentBookIds = recentBookIds,
            recentEvents = events,
            hiddenItemIds = hidden,
            finishedItemIds = finished,
            libraryBooks = libraryBooks,
            authorWeights = authorWeights.normalizedCopy(),
            subjectWeights = subjectWeights.normalizedCopy(),
            languageWeights = languageWeights.normalizedCopy()
        )
    }

    suspend fun profileSnapshot(signals: RecommendationSignals): RecommendationProfileSnapshot {
        return RecommendationProfileSnapshot(
            authorWeights = signals.authorWeights.topWeights(25),
            subjectWeights = signals.subjectWeights.topWeights(30),
            languageWeights = signals.languageWeights.topWeights(12),
            recentBookIds = signals.recentBookIds.take(20),
            hiddenItemIds = signals.hiddenItemIds.take(50),
            finishedItemIds = signals.finishedItemIds.take(50)
        )
    }

    suspend fun clearAll() {
        dao.clearEvents()
        dao.clearItems()
        dao.clearSnapshots()
        dao.clearApiCache()
    }

    suspend fun putApiCache(cacheKey: String, responseJson: String, ttlMillis: Long) {
        val now = System.currentTimeMillis()
        dao.upsertApiCache(
            RecommendationApiCacheEntity(
                cacheKey = cacheKey,
                responseJson = responseJson,
                updatedAt = now,
                expiresAt = now + ttlMillis
            )
        )
        dao.pruneExpiredApiCache()
    }

    suspend fun getApiCache(cacheKey: String): String? {
        return dao.getApiCache(cacheKey)?.responseJson
    }

    private fun RecommendationEvent.toEntity(): RecommendationEventEntity {
        return RecommendationEventEntity(
            eventId = eventId,
            type = type.name,
            itemId = itemId,
            source = source.wireName,
            title = title,
            authorsJson = gson.toJson(authors),
            subjectsJson = gson.toJson(subjects),
            languagesJson = gson.toJson(languages),
            value = value,
            query = query,
            timestamp = timestamp
        )
    }

    private fun RecommendationEventEntity.toModel(): RecommendationEvent {
        val stringListType = object : TypeToken<List<String>>() {}.type
        return RecommendationEvent(
            eventId = eventId,
            type = runCatching { RecommendationEventType.valueOf(type) }
                .getOrDefault(RecommendationEventType.BOOK_OPEN),
            itemId = itemId,
            source = RecommendationItemSource.values().firstOrNull { it.wireName == source }
                ?: RecommendationItemSource.UNKNOWN,
            title = title,
            authors = runCatching { gson.fromJson<List<String>>(authorsJson, stringListType) }.getOrDefault(emptyList()),
            subjects = runCatching { gson.fromJson<List<String>>(subjectsJson, stringListType) }.getOrDefault(emptyList()),
            languages = runCatching { gson.fromJson<List<String>>(languagesJson, stringListType) }.getOrDefault(emptyList()),
            value = value,
            query = query,
            timestamp = timestamp
        )
    }

    private fun MutableMap<String, Double>.bump(rawKey: String, delta: Double) {
        val key = rawKey.trim().lowercase()
        if (key.isBlank()) return
        this[key] = (this[key] ?: 0.0) + delta
    }

    private fun MutableMap<String, Double>.normalizedCopy(): Map<String, Double> {
        return filterValues { it != 0.0 }
            .mapValues { (_, value) -> value.coerceIn(-8.0, 20.0) }
    }

    private fun Map<String, Double>.topWeights(limit: Int): Map<String, Double> {
        return entries
            .sortedByDescending { it.value }
            .take(limit)
            .associate { it.key to it.value }
    }

    private fun eventSignalWeight(type: RecommendationEventType, value: Double?): Double {
        return when (type) {
            RecommendationEventType.BOOK_OPEN -> 1.2
            RecommendationEventType.READ_START -> 3.0
            RecommendationEventType.READ_PROGRESS -> 2.0 + ((value ?: 0.0) / 50.0)
            RecommendationEventType.READ_FINISH -> 5.0
            RecommendationEventType.DOWNLOAD -> 3.5
            RecommendationEventType.LIBRARY_ADD -> 2.5
            RecommendationEventType.RATING -> value ?: 1.0
            RecommendationEventType.SEARCH -> 0.6
            RecommendationEventType.BOOK_IMPRESSION -> 0.1
            RecommendationEventType.HIDE -> -6.0
        }
    }

    private fun availabilityFor(book: bookModel): String {
        return when {
            !book.id_project_gutenberg.isNullOrEmpty() -> "gutenberg"
            book.has_fulltext == true && !book.ia.isNullOrEmpty() -> "open_library_readable"
            book.public_scan_b == true -> "public_scan"
            else -> "metadata_only"
        }
    }

    companion object {
        const val HOME_SNAPSHOT_KEY = "home"
        private const val MAX_EVENT_HISTORY = 500
    }
}
