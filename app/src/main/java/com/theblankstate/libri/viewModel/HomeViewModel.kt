package com.theblankstate.libri.viewModel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.theblankstate.libri.data_retrieval.repository
import com.theblankstate.libri.datamodel.bookModel
import com.theblankstate.libri.data.UserPreferencesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface HomeState {
    object Loading : HomeState
    data class Success(val content: List<Pair<String, List<bookModel>>>) : HomeState
    data class Error(val message: String) : HomeState
}

class HomeViewModel(application: Application) : ViewModel() {
    private val repository = repository()
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
                val recentBookIds = userPreferencesRepository.getRecentBooks()

                val deferredContent = mutableListOf<kotlinx.coroutines.Deferred<Pair<String, List<bookModel>>>>()

                // Fetch Trending Books
                val trendingBooksDeferred = async {
                    val query = "trending_score_hourly_sum:[1 TO *] -subject:\"content_warning:cover\" language:eng"
                    val books = repository.getbooks(query = query, sort = "trending", limit = 20)
                    "Trending Now" to books.filter { it.has_fulltext == true && !it.ia.isNullOrEmpty() }.take(10)
                }

                // Continue Reading (Fetch details for recent books)
                val recentBooksDeferred = async {
                    val books = recentBookIds.mapNotNull { id ->
                        try {
                            // Try to fetch from cache or API
                            // This is a simplified approach; ideally we'd have a local DB
                            val workId = id.removePrefix("/works/")
                            val details = repository.getWorkDetails(workId)
                            details?.let {
                                bookModel(
                                    key = "/works/$workId",
                                    title = it.title ?: "Unknown",
                                    cover_i = it.covers?.firstOrNull(),
                                    subject = it.subjects,
                                    has_fulltext = true // Assuming recently read books were available
                                )
                            }
                        } catch (e: Exception) { null }
                    }.filter { it.has_fulltext == true && !it.ia.isNullOrEmpty() } // Check both has_fulltext and ia
                    "Continue Reading" to books
                }

                // Fetch for Authors
                selectedAuthors.forEach { author ->
                    deferredContent.add(async {
                        val books = repository.getbooks(author = author, limit = 10)
                        author to books.filter { it.has_fulltext == true && !it.ia.isNullOrEmpty() }
                    })
                }

                // Fetch for Genres
                selectedGenres.forEach { genre ->
                    deferredContent.add(async {
                        val books = repository.getbooks(subject = genre, limit = 10)
                        genre to books.filter { it.has_fulltext == true && !it.ia.isNullOrEmpty() }
                    })
                }
                
                // Fallback if no preferences
                if (selectedAuthors.isEmpty() && selectedGenres.isEmpty()) {
                     deferredContent.add(async { 
                         val books = repository.getbooks(subject = "literature", limit = 10)
                         "Classic Literature" to books.filter { it.has_fulltext == true && !it.ia.isNullOrEmpty() }
                     })
                }

                val recentBooks = recentBooksDeferred.await()
                val trendingBooks = trendingBooksDeferred.await()
                val contentResults = deferredContent.awaitAll()
                
                val finalContent = mutableListOf<Pair<String, List<bookModel>>>()

                // Add Trending Now
                if (trendingBooks.second.size >= 4) {
                    finalContent.add(trendingBooks)
                }

                // Only add Continue Reading if it has at least 4 books
                if (recentBooks.second.size >= 4) {
                    finalContent.add(recentBooks)
                }
                // Only add sections with at least 4 books
                contentResults.forEach { (title, books) ->
                    if (books.size >= 4) {
                        finalContent.add(title to books)
                    }
                }

                _homeState.value = HomeState.Success(finalContent)
            } catch (e: Exception) {
                _homeState.value = HomeState.Error("Failed to load home content: ${e.message}")
            }
        }
    }
}

class HomeViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
