package com.theblankstate.libri.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.theblankstate.libri.data.RecommendationSeeds
import com.theblankstate.libri.datamodel.bookModel
import com.theblankstate.libri.data.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

sealed interface HomeState {
    object Loading : HomeState
    data class Success(
        val content: List<Pair<String, List<bookModel>>>,
        val gutenbergTitle: String,
        val gutenbergSubtitle: String
    ) : HomeState
    data class Error(val message: String) : HomeState
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val apiRepository = com.theblankstate.libri.data_retrieval.repository
    private val userPreferencesRepository = UserPreferencesRepository(application)

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
            _homeState.value = HomeState.Loading
            try {
                val selectedAuthors = userPreferencesRepository.getSelectedAuthors().toList().shuffled().take(3)
                val selectedGenres = userPreferencesRepository.getSelectedGenres().toList().shuffled().take(3)
                val selectedLanguages = userPreferencesRepository.getSelectedLanguages().toList()
                val preferredLanguage = RecommendationSeeds.preferredOpenLibraryLanguage(selectedLanguages)
                val recentBookIds = userPreferencesRepository.getRecentBooks()

                val (recentBooks, trendingBooks, contentResults) = supervisorScope {
                    val deferredContent = mutableListOf<kotlinx.coroutines.Deferred<Pair<String, List<bookModel>>>>()

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

                    Triple(
                        recentBooksDeferred.await(),
                        trendingBooksDeferred.await(),
                        deferredContent.awaitAll()
                    )
                }
                
                val finalContent = mutableListOf<Pair<String, List<bookModel>>>()

                // Add Continue Reading if it has any books
                if (recentBooks.second.isNotEmpty()) {
                    finalContent.add(recentBooks)
                }
                // Only add sections with at least 4 books
                contentResults.forEach { (title, books) ->
                    if (books.size >= 4) {
                        finalContent.add(title to books)
                    }
                }
                // Add Trending after personalized content so the hero reflects preferences first.
                if (trendingBooks.second.size >= 4) {
                    finalContent.add(trendingBooks)
                }

                if (finalContent.isEmpty() || finalContent.all { it.second.isEmpty() }) {
                    _homeState.value = HomeState.Error("Could not connect to Open Library. Please check your internet connection and try again.")
                } else {
                    _homeState.value = HomeState.Success(
                        content = finalContent,
                        gutenbergTitle = RecommendationSeeds.gutenbergHeadline(selectedGenres, selectedLanguages),
                        gutenbergSubtitle = RecommendationSeeds.gutenbergSubtitle(selectedGenres, selectedLanguages)
                    )
                }
            } catch (e: Exception) {
                _homeState.value = HomeState.Error("Failed to load home content. Check your connection and try again.")
            }
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
        runCatching {
            apiRepository.getbooks(
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
