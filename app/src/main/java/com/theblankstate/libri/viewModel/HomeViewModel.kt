package com.theblankstate.libri.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theblankstate.libri.data.LibraryRepository
import com.theblankstate.libri.data.RecommendationSeeds
import com.theblankstate.libri.datamodel.bookModel
import com.theblankstate.libri.datamodel.GutendexBook
import com.theblankstate.libri.data.UserPreferencesRepository
import com.theblankstate.libri.data_retrieval.retrofitinatance
import com.theblankstate.libri.recommendation.RecommendationEngine
import com.theblankstate.libri.recommendation.RecommendationStore
import com.theblankstate.libri.recommendation.RecommendationSyncRepository
import com.theblankstate.libri.recommendation.RecommendationSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface HomeState {
    object Loading : HomeState
    data class Success(
        val content: List<Pair<String, List<bookModel>>>,
        val gutenbergSections: List<Pair<String, List<GutendexBook>>> = emptyList(),
        val gutenbergTitle: String,
        val gutenbergSubtitle: String
    ) : HomeState
    /**
     * When Open Library is unavailable, show Gutenberg content as fallback
     * so the home screen is never empty.
     */
    data class PartialSuccess(
        val gutenbergSections: List<Pair<String, List<GutendexBook>>>,
        val retryMessage: String
    ) : HomeState
    data class Error(val message: String) : HomeState
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val apiRepository = com.theblankstate.libri.data_retrieval.repository
    private val gutendexApi = retrofitinatance.gutendexApi
    private val userPreferencesRepository = UserPreferencesRepository(application)
    private val libraryRepository = LibraryRepository()
    private val recommendationStore = RecommendationStore(application)
    private val recommendationEngine = RecommendationEngine()
    private val recommendationSyncRepository = RecommendationSyncRepository(application)
    private val gson = Gson()

    private val _homeState = MutableStateFlow<HomeState>(HomeState.Loading)
    val homeState: StateFlow<HomeState> = _homeState

    init {
        fetchHomeContent()
    }

    fun retry() {
        fetchHomeContent()
    }

    private fun fetchHomeContent() {
        viewModelScope.launch {
            val selectedLanguages = userPreferencesRepository.getSelectedLanguages().toList()
            val selectedGenresForCopy = userPreferencesRepository.getSelectedGenres().toList()
            val cachedContent = recommendationStore.getHomeSnapshot()
            if (cachedContent.isNullOrEmpty()) {
                _homeState.value = HomeState.Loading
            } else {
                _homeState.value = HomeState.Success(
                    content = cachedContent,
                    gutenbergSections = emptyList(),
                    gutenbergTitle = RecommendationSeeds.gutenbergHeadline(selectedGenresForCopy, selectedLanguages),
                    gutenbergSubtitle = RecommendationSeeds.gutenbergSubtitle(selectedGenresForCopy, selectedLanguages)
                )
            }

            try {
                val selectedAuthors = userPreferencesRepository.getSelectedAuthors().toList().shuffled().take(4)
                val selectedGenres = userPreferencesRepository.getSelectedGenres().toList().shuffled().take(6)
                val preferredLanguage = RecommendationSeeds.preferredOpenLibraryLanguage(selectedLanguages)
                val recentBookIds = userPreferencesRepository.getRecentBooks()

                val (recentBooks, trendingBooks, contentResults, gutenbergSections) = supervisorScope {
                    val deferredContent = mutableListOf<kotlinx.coroutines.Deferred<Pair<String, List<bookModel>>>>()
                    val gutenbergDeferred = async(Dispatchers.IO) {
                        buildGutenbergSections(selectedGenresForCopy, selectedLanguages)
                    }

                    // Fetch Trending Books
                    val trendingBooksDeferred = async {
                        val query = "trending_score_hourly_sum:[1 TO *] -subject:\"content_warning:cover\""
                        val books = safeGetBooks(
                            query = query,
                            lang = preferredLanguage?.twoLetter,
                            sort = "trending",
                            limit = 20
                        )
                        "Trending on Open Library" to books.filterReadable().take(10)
                    }

                    // Continue Reading (Fetch details for recent books)
                    val recentBooksDeferred = async {
                        val books = recentBookIds.mapNotNull { id ->
                            try {
                                val normalizedKey = when {
                                    id.startsWith("/works/") || id.startsWith("/books/") -> id
                                    id.endsWith("M", ignoreCase = true) -> "/books/$id"
                                    else -> "/works/${id.removePrefix("works/")}"
                                }

                                safeGetBooks(query = "key:$normalizedKey", limit = 1).firstOrNull()
                                    ?: if (normalizedKey.startsWith("/works/")) {
                                        val workId = normalizedKey.removePrefix("/works/")
                                        val details = safeGetWorkDetails(workId)
                                        details?.let {
                                            bookModel(
                                                key = normalizedKey,
                                                title = it.title ?: "Unknown",
                                                cover_i = it.covers?.firstOrNull(),
                                                subject = it.subjects,
                                                has_fulltext = true
                                            )
                                        }
                                    } else {
                                        null
                                    }
                            } catch (e: Exception) {
                                null
                            }
                        }
                        "Continue Reading" to books
                    }

                    // Fetch for Authors
                    selectedAuthors.distinct().forEach { author ->
                        deferredContent.add(async {
                            val books = safeGetBooks(
                                author = author,
                                lang = preferredLanguage?.twoLetter,
                                limit = 12
                            )
                            "More from $author" to books.filterReadable()
                        })
                    }

                    // Fetch for Genres
                    selectedGenres.distinct().forEach { genre ->
                        deferredContent.add(async {
                            val books = safeGetBooks(
                                subject = RecommendationSeeds.normalizeTopic(genre),
                                lang = preferredLanguage?.twoLetter,
                                sort = "random",
                                limit = 12
                            )
                            "Because you like $genre" to books.filterReadable()
                        })
                    }
                    
                    // Fallback if no preferences
                    if (selectedAuthors.isEmpty() && selectedGenres.isEmpty()) {
                        deferredContent.add(async {
                            val books = safeGetBooks(
                                subject = "literature",
                                lang = preferredLanguage?.twoLetter,
                                sort = "random",
                                limit = 12
                            )
                            "Classic Literature" to books.filterReadable()
                        })
                    }

                    HomeFetchResult(
                        recentBooksDeferred.await(),
                        trendingBooksDeferred.await(),
                        deferredContent.awaitAll(),
                        gutenbergDeferred.await()
                    )
                }
                
                val rawContent = mutableListOf<Pair<String, List<bookModel>>>()

                // Add Continue Reading if it has any books
                if (recentBooks.second.isNotEmpty()) {
                    rawContent.add(recentBooks)
                }
                // Only add sections with at least 4 books
                contentResults.forEach { (title, books) ->
                    if (books.size >= 4) {
                        rawContent.add(title to books)
                    }
                }
                // Add Trending after personalized content so the hero reflects preferences first.
                if (trendingBooks.second.size >= 4) {
                    rawContent.add(trendingBooks)
                }

                if (rawContent.isEmpty() || rawContent.all { it.second.isEmpty() }) {
                    // Open Library returned nothing — fallback to Gutenberg
                    if (cachedContent.isNullOrEmpty()) {
                        loadGutenbergFallback("Open Library is currently unavailable. Showing free classics instead.")
                    }
                } else {
                    recommendationStore.upsertBooks(rawContent.flatMap { it.second })
                    val libraryBooks = loadLibraryBooks()
                    val signals = recommendationStore.loadSignals(userPreferencesRepository, libraryBooks)
                    val finalContent = recommendationEngine.rankHomeSections(rawContent, signals)
                    recommendationStore.saveHomeSnapshot(finalContent)
                    syncRecommendations(signals)

                    _homeState.value = HomeState.Success(
                        content = finalContent,
                        gutenbergSections = gutenbergSections,
                        gutenbergTitle = RecommendationSeeds.gutenbergHeadline(selectedGenres, selectedLanguages),
                        gutenbergSubtitle = RecommendationSeeds.gutenbergSubtitle(selectedGenres, selectedLanguages)
                    )
                }
            } catch (e: Exception) {
                // Network failure — try Gutenberg as fallback
                if (cachedContent.isNullOrEmpty()) {
                    loadGutenbergFallback("Could not connect to Open Library. Showing free classics from Project Gutenberg.")
                }
            }
        }
    }

    private suspend fun loadLibraryBooks() = withContext(Dispatchers.IO) {
        val uid = userPreferencesRepository.getGoogleUser().third ?: return@withContext emptyList()
        withTimeoutOrNull(2_500L) {
            runCatching { libraryRepository.getLibraryBooks(uid).first() }
                .getOrDefault(emptyList())
        }.orEmpty()
    }

    private data class HomeFetchResult(
        val recentBooks: Pair<String, List<bookModel>>,
        val trendingBooks: Pair<String, List<bookModel>>,
        val contentResults: List<Pair<String, List<bookModel>>>,
        val gutenbergSections: List<Pair<String, List<GutendexBook>>>
    )

    private fun syncRecommendations(signals: com.theblankstate.libri.recommendation.RecommendationSignals) {
        RecommendationSyncWorker.enqueue(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val profile = recommendationStore.profileSnapshot(signals)
                val sections = recommendationStore.latestHomeSnapshotForSync()
                val events = recommendationStore.latestEvents()
                recommendationSyncRepository.syncHomeRecommendations(profile, sections, events)
            }
        }
    }

    /**
     * When Open Library fails, fill the home screen with Gutenberg content
     * so the user always has something to browse.
     */
    private suspend fun loadGutenbergFallback(message: String) {
        try {
            val selectedGenres = userPreferencesRepository.getSelectedGenres().toList()
            val selectedLanguages = userPreferencesRepository.getSelectedLanguages().toList()
            val sections = buildGutenbergSections(selectedGenres, selectedLanguages)

            if (sections.isEmpty()) {
                // Even Gutenberg failed — show full error
                _homeState.value = HomeState.Error(
                    "No internet connection. Please check your network and try again."
                )
            } else {
                _homeState.value = HomeState.PartialSuccess(
                    gutenbergSections = sections,
                    retryMessage = message
                )
            }
        } catch (e: Exception) {
            _homeState.value = HomeState.Error(
                "No internet connection. Please check your network and try again."
            )
        }
    }

    private suspend fun buildGutenbergSections(
        selectedGenres: List<String>,
        selectedLanguages: List<String>
    ): List<Pair<String, List<GutendexBook>>> = supervisorScope {
        val language = RecommendationSeeds.preferredGutendexLanguage(selectedLanguages) ?: "en"
        val displayTopics = RecommendationSeeds.displayTopicsFromGenres(selectedGenres, limit = 9)
        val topicPairs = displayTopics.map { display ->
            display to RecommendationSeeds.normalizeTopic(display)
        }.distinctBy { it.second }

        val popularDeferred = async(Dispatchers.IO) {
            safeGutendexPopular(language).take(18)
        }
        val topicDeferreds = topicPairs.map { (display, topic) ->
            display to async(Dispatchers.IO) {
                safeGutendexTopic(topic, language).take(14)
            }
        }
        val fictionDeferred = async(Dispatchers.IO) {
            safeGutendexTopic("fiction", language).take(14)
        }

        val sections = mutableListOf<Pair<String, List<GutendexBook>>>()
        val firstTopic = topicDeferreds.firstOrNull()
        if (firstTopic != null) {
            val books = firstTopic.second.await()
            if (books.size >= 3) {
                sections.add("Recommended Free ${firstTopic.first} Books" to books)
            }
        }

        val popular = popularDeferred.await()
        if (popular.isNotEmpty()) {
            sections.add("Popular on Gutenberg" to popular)
        }

        topicDeferreds.drop(1).forEach { (display, deferred) ->
            val books = deferred.await()
            if (books.size >= 3) {
                sections.add("Free $display Books" to books)
            }
        }

        val fiction = fictionDeferred.await()
        if (fiction.isNotEmpty() && sections.none { it.first.contains("fiction", ignoreCase = true) }) {
            sections.add("Classic Fiction" to fiction)
        }

        sections
            .map { (title, books) -> title to books.distinctBy { it.id } }
            .filter { it.second.isNotEmpty() }
            .distinctBy { it.first }
            .take(10)
    }

    private suspend fun safeGutendexPopular(language: String?): List<GutendexBook> {
        val primary = runCatching {
            gutendexApi.getPopularBooks(languages = language).results
        }.getOrDefault(emptyList())
        return if (primary.size >= 6 || language == null) {
            primary
        } else {
            (primary + runCatching {
                gutendexApi.getPopularBooks(languages = null).results
            }.getOrDefault(emptyList())).distinctBy { it.id }
        }
    }

    private suspend fun safeGutendexTopic(topic: String, language: String?): List<GutendexBook> {
        val primary = runCatching {
            gutendexApi.getBooksByTopic(topic = topic, languages = language).results
        }.getOrDefault(emptyList())
        return if (primary.size >= 6 || language == null) {
            primary
        } else {
            (primary + runCatching {
                gutendexApi.getBooksByTopic(topic = topic, languages = null).results
            }.getOrDefault(emptyList())).distinctBy { it.id }
        }
    }

    private suspend fun safeGetBooks(
        query: String? = null,
        title: String? = null,
        author: String? = null,
        subject: String? = null,
        isbn: String? = null,
        publisher: String? = null,
        language: String? = null,
        lang: String? = null,
        sort: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): List<bookModel> = withContext(Dispatchers.IO) {
        val cacheKey = listOf(
            "open_library_search",
            query,
            title,
            author,
            subject,
            isbn,
            publisher,
            language,
            lang,
            sort,
            limit,
            offset
        ).joinToString("|") { it?.toString().orEmpty() }
        val cached = recommendationStore.getApiCache(cacheKey)
        if (cached != null) {
            val type = object : TypeToken<List<bookModel>>() {}.type
            return@withContext runCatching {
                gson.fromJson<List<bookModel>>(cached, type)
            }.getOrDefault(emptyList())
        }

        runCatching {
            val books = apiRepository.getbooks(
                query = query,
                title = title,
                author = author,
                subject = subject,
                isbn = isbn,
                publisher = publisher,
                language = language,
                lang = lang,
                sort = sort,
                limit = limit,
                offset = offset
            )
            recommendationStore.putApiCache(
                cacheKey = cacheKey,
                responseJson = gson.toJson(books),
                ttlMillis = 6L * 60L * 60L * 1000L
            )
            books
        }.getOrDefault(emptyList())
    }

    private suspend fun safeGetWorkDetails(workId: String) = withContext(Dispatchers.IO) {
        runCatching { apiRepository.getWorkDetails(workId) }.getOrNull()
    }

    private fun List<bookModel>.filterReadable(): List<bookModel> {
        return filter { it.has_fulltext == true && !it.ia.isNullOrEmpty() }
            .distinctBy { it.key ?: it.title }
    }
}

class HomeViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
