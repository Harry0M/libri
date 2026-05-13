package com.theblankstate.libri.viewModel

import android.app.Application
import android.content.IntentFilter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theblankstate.libri.data.RecommendationSeeds
import com.theblankstate.libri.data.UserPreferencesRepository
import com.theblankstate.libri.data_retrieval.repository
import com.theblankstate.libri.data_retrieval.retrofitinatance
import com.theblankstate.libri.data_retrieval.DownloadsRepository
import com.theblankstate.libri.data_retrieval.DownloadNotificationManager
import com.theblankstate.libri.data_retrieval.DownloadCancelReceiver
import com.theblankstate.libri.datamodel.BookFormat
import com.theblankstate.libri.datamodel.BookSource
import com.theblankstate.libri.datamodel.DownloadedBook
import com.theblankstate.libri.datamodel.GutendexAuthor
import com.theblankstate.libri.datamodel.GutendexBook
import com.theblankstate.libri.datamodel.bookModel
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * ViewModel for Project Gutenberg book discovery and downloads via Gutendex API
 */
class GutenbergViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        // Limit items in memory to prevent crashes from indefinite list growth
        private const val MAX_ITEMS_IN_MEMORY = 200
    }
    
    private val context = application.applicationContext
    private val gutendexApi = retrofitinatance.gutendexApi
    private val userPreferencesRepository = UserPreferencesRepository(application)
    private val downloadsRepository = DownloadsRepository(application)
    private val notificationManager = DownloadNotificationManager(application)
    
    // Map to store book titles for notifications
    private val downloadingBookTitles = mutableMapOf<Int, String>()
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    // Search state
    private val _searchResults = MutableStateFlow<List<GutendexBook>>(emptyList())
    val searchResults: StateFlow<List<GutendexBook>> = _searchResults.asStateFlow()
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()
    
    // Popular books
    private val _popularBooks = MutableStateFlow<List<GutendexBook>>(emptyList())
    val popularBooks: StateFlow<List<GutendexBook>> = _popularBooks.asStateFlow()

    private val _recommendationState = MutableStateFlow(GutenbergRecommendationState())
    val recommendationState: StateFlow<GutenbergRecommendationState> = _recommendationState.asStateFlow()
    
    private val _isLoadingPopular = MutableStateFlow(false)
    val isLoadingPopular: StateFlow<Boolean> = _isLoadingPopular.asStateFlow()
    
    // Selected book
    private val _selectedBook = MutableStateFlow<GutendexBook?>(null)
    val selectedBook: StateFlow<GutendexBook?> = _selectedBook.asStateFlow()
    
    // Download state
    private val _downloadingBookIds = MutableStateFlow<Set<Int>>(emptySet())
    val downloadingBookIds: StateFlow<Set<Int>> = _downloadingBookIds.asStateFlow()
    
    private val _downloadProgress = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<Int, Float>> = _downloadProgress.asStateFlow()
    
    private val downloadJobs = mutableMapOf<Int, Job>()
    
    private val cancelReceiver = DownloadCancelReceiver()
    
    init {
        // Register broadcast receiver for cancel actions from notifications
        DownloadCancelReceiver.setCancelCallback { bookId ->
            cancelDownload(bookId)
        }
        
        val filter = IntentFilter(DownloadNotificationManager.ACTION_CANCEL_DOWNLOAD)
        ContextCompat.registerReceiver(
            context,
            cancelReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
    
    // Pagination for search
    private var currentPage = 1
    private var hasMoreResults = true
    private val _hasMoreSearchResults = MutableStateFlow(true)
    val hasMoreSearchResults: StateFlow<Boolean> = _hasMoreSearchResults.asStateFlow()
    private var currentSearchQuery: String? = null
    private var currentSearchLanguage: String? = null
    
    // Pagination for popular books
    private var popularNextPageUrl: String? = null
    private val _isLoadingMorePopular = MutableStateFlow(false)
    val isLoadingMorePopular: StateFlow<Boolean> = _isLoadingMorePopular.asStateFlow()
    
    // Pagination for topic books
    private var topicNextPageUrl: String? = null
    private var currentTopic: String? = null
    private val _isLoadingMoreTopic = MutableStateFlow(false)
    val isLoadingMoreTopic: StateFlow<Boolean> = _isLoadingMoreTopic.asStateFlow()
    
    /**
     * Search for books on Project Gutenberg
     */
    fun searchBooks(query: String, language: String? = null) {
        Log.d("GutenbergViewModel", "searchBooks called for query: $query")
        if (query.isBlank()) return
        
        currentSearchQuery = query
        currentSearchLanguage = language
        currentPage = 1
        hasMoreResults = true
        _hasMoreSearchResults.value = true
        
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            
            try {
                val response = withContext(Dispatchers.IO) {
                    gutendexApi.searchBooks(
                        search = query,
                        languages = language,
                        page = currentPage
                    )
                }
                android.util.Log.d("GutenbergViewModel", "searchBooks returned ${response.results.size} results for query=$query")
                _searchResults.value = response.results
                Log.d("GutenbergViewModel", "searchBooks got ${response.results.size} results for query: $query")
                hasMoreResults = response.next != null
                _hasMoreSearchResults.value = response.next != null
                currentPage++
                _searchError.value = null
            } catch (e: java.net.SocketTimeoutException) {
                loadOpenLibraryGutenbergSearchFallback(query, e)
            } catch (e: java.io.IOException) {
                loadOpenLibraryGutenbergSearchFallback(query, e)
            } catch (e: Exception) {
                loadOpenLibraryGutenbergSearchFallback(query, e)
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * Search for books by Gutendex topic/bookshelf
     */
    fun searchBooksByTopic(topic: String, language: String? = null) {
        if (topic.isBlank()) return

        currentSearchQuery = topic
        currentPage = 1
        hasMoreResults = false
        _hasMoreSearchResults.value = false

        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null

            try {
                val results = withContext(Dispatchers.IO) {
                    getBooksByTopic(topic = topic, language = language)
                }
                _searchResults.value = results
                _searchError.value = null
            } catch (e: java.net.SocketTimeoutException) {
                val fallback = searchProjectGutenbergViaOpenLibrary(topic = topic, limit = 24)
                _searchResults.value = fallback
                _searchError.value = if (fallback.isEmpty()) "Connection timed out. Please try again." else null
            } catch (e: java.io.IOException) {
                val fallback = searchProjectGutenbergViaOpenLibrary(topic = topic, limit = 24)
                _searchResults.value = fallback
                _searchError.value = if (fallback.isEmpty()) "Network error. Please check your connection." else null
            } catch (e: Exception) {
                val fallback = searchProjectGutenbergViaOpenLibrary(topic = topic, limit = 24)
                _searchResults.value = fallback
                _searchError.value = if (fallback.isEmpty()) "Topic search failed. Please try again." else null
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    /**
     * Retry last search
     */
    fun retrySearch() {
        currentSearchQuery?.let { searchBooks(it, currentSearchLanguage) }
    }
    
    /**
     * Load more search results (pagination)
     */
    fun loadMoreSearchResults() {
        if (!hasMoreResults || _isSearching.value || currentSearchQuery == null) return
        
        viewModelScope.launch {
            _isSearching.value = true
            
            try {
                val response = withContext(Dispatchers.IO) {
                    gutendexApi.searchBooks(
                        search = currentSearchQuery,
                        languages = currentSearchLanguage,
                        page = currentPage
                    )
                }
                val newResults = _searchResults.value + response.results
                // Keep only last MAX_ITEMS_IN_MEMORY items to prevent memory issues
                _searchResults.value = if (newResults.size > MAX_ITEMS_IN_MEMORY) {
                    newResults.takeLast(MAX_ITEMS_IN_MEMORY)
                } else {
                    newResults
                }
                hasMoreResults = response.next != null
                _hasMoreSearchResults.value = response.next != null
                currentPage++
            } catch (e: Exception) {
                // Silently fail for pagination
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    /**
     * Load popular books from Project Gutenberg
     */
    fun loadPopularBooks(language: String? = null) {
        viewModelScope.launch {
            _isLoadingPopular.value = true
            
            try {
                val preferredLanguage = language ?: preferredGutendexLanguage()
                val response = gutendexApi.getPopularBooks(languages = preferredLanguage)
                val resolvedResponse = if (response.results.size < 6 && preferredLanguage != null) {
                    gutendexApi.getPopularBooks(languages = null)
                } else {
                    response
                }
                _popularBooks.value = response.results.ifEmpty { resolvedResponse.results }
                    .ifTooSmallThen(resolvedResponse.results)
                popularNextPageUrl = resolvedResponse.next ?: response.next
            } catch (e: Exception) {
                Log.e("GutenbergViewModel", "Gutendex popular load failed; using Open Library fallback", e)
                val fallback = searchProjectGutenbergViaOpenLibrary(limit = 24)
                if (fallback.isNotEmpty()) {
                    _popularBooks.value = fallback
                    popularNextPageUrl = null
                }
            } finally {
                _isLoadingPopular.value = false
            }
        }
    }

    fun loadRecommendedBooks(force: Boolean = false) {
        if (!force && (_recommendationState.value.books.isNotEmpty() || _recommendationState.value.isLoading)) {
            return
        }

        viewModelScope.launch {
            val selectedGenres = userPreferencesRepository.getSelectedGenres().toList()
            val selectedLanguages = userPreferencesRepository.getSelectedLanguages().toList()
            val language = preferredGutendexLanguage()
            val topic = RecommendationSeeds.topicsFromGenres(selectedGenres, limit = 1).firstOrNull()

            _recommendationState.value = GutenbergRecommendationState(
                headline = RecommendationSeeds.gutenbergHeadline(selectedGenres, selectedLanguages),
                subtitle = RecommendationSeeds.gutenbergSubtitle(selectedGenres, selectedLanguages),
                isLoading = true
            )

            try {
                val response = if (topic != null) {
                    gutendexApi.getBooksByTopic(topic = topic, languages = language)
                } else {
                    gutendexApi.getPopularBooks(languages = language)
                }
                val fallback = if (response.results.size < 6 && language != null) {
                    if (topic != null) {
                        gutendexApi.getBooksByTopic(topic = topic, languages = null)
                    } else {
                        gutendexApi.getPopularBooks(languages = null)
                    }
                } else {
                    response
                }

                _recommendationState.value = GutenbergRecommendationState(
                    books = response.results.ifTooSmallThen(fallback.results),
                    headline = RecommendationSeeds.gutenbergHeadline(selectedGenres, selectedLanguages),
                    subtitle = RecommendationSeeds.gutenbergSubtitle(selectedGenres, selectedLanguages),
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("GutenbergViewModel", "Gutendex recommendations failed; using Open Library fallback", e)
                val fallback = searchProjectGutenbergViaOpenLibrary(topic = topic, limit = 18)
                _recommendationState.value = GutenbergRecommendationState(
                    books = fallback,
                    headline = RecommendationSeeds.gutenbergHeadline(selectedGenres, selectedLanguages),
                    subtitle = RecommendationSeeds.gutenbergSubtitle(selectedGenres, selectedLanguages),
                    isLoading = false,
                    error = if (fallback.isEmpty()) "Could not load Gutenberg recommendations." else null
                )
            }
        }
    }
    
    /**
     * Load more popular books (pagination)
     */
    fun loadMorePopularBooks() {
        if (popularNextPageUrl == null || _isLoadingMorePopular.value) return
        
        viewModelScope.launch {
            _isLoadingMorePopular.value = true
            
            try {
                val response = gutendexApi.getBooksFromUrl(popularNextPageUrl!!)
                val newBooks = _popularBooks.value + response.results
                // Keep only last MAX_ITEMS_IN_MEMORY items
                _popularBooks.value = if (newBooks.size > MAX_ITEMS_IN_MEMORY) {
                    newBooks.takeLast(MAX_ITEMS_IN_MEMORY)
                } else {
                    newBooks
                }
                popularNextPageUrl = response.next
            } catch (e: Exception) {
                // Silently fail for pagination
            } finally {
                _isLoadingMorePopular.value = false
            }
        }
    }
    
    /**
     * Load books by topic/genre
     */
    suspend fun getBooksByTopic(topic: String, language: String? = null): List<GutendexBook> {
        currentTopic = topic
        return try {
            val resolvedLanguage = language ?: preferredGutendexLanguage()
            val response = gutendexApi.getBooksByTopic(
                topic = topic,
                languages = resolvedLanguage
            )
            topicNextPageUrl = response.next
            if (response.results.size < 6 && resolvedLanguage != null) {
                val fallback = gutendexApi.getBooksByTopic(topic = topic, languages = null)
                topicNextPageUrl = fallback.next ?: response.next
                response.results.ifTooSmallThen(fallback.results)
            } else {
                response.results
            }
        } catch (e: Exception) {
            topicNextPageUrl = null
            Log.e("GutenbergViewModel", "Gutendex topic load failed; using Open Library fallback for $topic", e)
            searchProjectGutenbergViaOpenLibrary(topic = topic, limit = 24)
        }
    }
    
    /**
     * Load more topic books (pagination)
     */
    suspend fun loadMoreTopicBooks(): List<GutendexBook> {
        if (topicNextPageUrl == null || _isLoadingMoreTopic.value) return emptyList()
        
        _isLoadingMoreTopic.value = true
        return try {
            val response = gutendexApi.getBooksFromUrl(topicNextPageUrl!!)
            topicNextPageUrl = response.next
            // Return only new results, UI will handle limiting
            response.results
        } catch (e: Exception) {
            emptyList()
        } finally {
            _isLoadingMoreTopic.value = false
        }
    }
    
    /**
     * Get a specific book by ID
     */
    fun getBook(id: Int) {
        viewModelScope.launch {
            try {
                val book = gutendexApi.getBook(id)
                _selectedBook.value = book
            } catch (e: Exception) {
                Log.e("GutenbergViewModel", "Gutendex getBook failed; using generated Gutenberg fallback for $id", e)
                _selectedBook.value = getProjectGutenbergBookViaOpenLibrary(id) ?: generatedGutenbergBook(id)
            }
        }
    }
    
    /**
     * Select a book for viewing
     */
    fun selectBook(book: GutendexBook) {
        _selectedBook.value = book
    }
    
    /**
     * Clear search results
     */
    fun clearSearch() {
        _searchResults.value = emptyList()
        _searchError.value = null
        currentSearchQuery = null
        currentSearchLanguage = null
        currentPage = 1
        hasMoreResults = true
        _hasMoreSearchResults.value = true
    }

    private fun preferredGutendexLanguage(): String? {
        return RecommendationSeeds.preferredGutendexLanguage(
            userPreferencesRepository.getSelectedLanguages()
        )
    }

    private fun List<GutendexBook>.ifTooSmallThen(fallback: List<GutendexBook>): List<GutendexBook> {
        return if (size >= 6) this else (this + fallback).distinctBy { it.id }
    }

    private suspend fun loadOpenLibraryGutenbergSearchFallback(query: String, cause: Exception) {
        Log.e("GutenbergViewModel", "Gutendex search failed; using Open Library fallback for query=$query", cause)
        val fallback = searchProjectGutenbergViaOpenLibrary(search = query, limit = 24)
        _searchResults.value = fallback
        _searchError.value = if (fallback.isEmpty()) {
            "Gutenberg search is temporarily unavailable."
        } else {
            null
        }
        hasMoreResults = false
        _hasMoreSearchResults.value = false
    }

    private suspend fun searchProjectGutenbergViaOpenLibrary(
        search: String? = null,
        topic: String? = null,
        limit: Int = 20
    ): List<GutendexBook> = withContext(Dispatchers.IO) {
        runCatching {
            val queryParts = listOfNotNull(
                search?.takeIf { it.isNotBlank() },
                topic?.takeIf { it.isNotBlank() },
                "id_project_gutenberg:[* TO *]"
            )
            repository.getbooks(
                query = queryParts.joinToString(" "),
                sort = if (search.isNullOrBlank() && topic.isNullOrBlank()) "trending" else null,
                limit = limit
            )
                .mapNotNull { it.toGutendexFallback() }
                .distinctBy { it.id }
        }.onFailure { error ->
            Log.e("GutenbergViewModel", "Open Library Gutenberg fallback failed", error)
        }.getOrDefault(emptyList())
    }

    private suspend fun getProjectGutenbergBookViaOpenLibrary(id: Int): GutendexBook? = withContext(Dispatchers.IO) {
        runCatching {
            repository.getbooks(
                query = "id_project_gutenberg:$id",
                limit = 1
            ).firstOrNull()?.toGutendexFallback()
        }.getOrNull()
    }

    private fun bookModel.toGutendexFallback(): GutendexBook? {
        val gutenbergId = id_project_gutenberg
            ?.firstNotNullOfOrNull { raw -> raw.toIntOrNull() }
            ?: return null
        val cover = coverUrl ?: "https://www.gutenberg.org/cache/epub/$gutenbergId/pg$gutenbergId.cover.medium.jpg"
        return GutendexBook(
            id = gutenbergId,
            title = title,
            authors = author_name?.map { GutendexAuthor(it, null, null) },
            translators = emptyList(),
            subjects = subject,
            bookshelves = emptyList(),
            languages = language,
            copyright = false,
            mediaType = "Text",
            formats = projectGutenbergFormats(gutenbergId, cover),
            downloadCount = null
        )
    }

    private fun generatedGutenbergBook(id: Int): GutendexBook {
        val cover = "https://www.gutenberg.org/cache/epub/$id/pg$id.cover.medium.jpg"
        return GutendexBook(
            id = id,
            title = "Project Gutenberg #$id",
            authors = emptyList(),
            translators = emptyList(),
            subjects = emptyList(),
            bookshelves = emptyList(),
            languages = listOf("en"),
            copyright = false,
            mediaType = "Text",
            formats = projectGutenbergFormats(id, cover),
            downloadCount = null
        )
    }

    private fun projectGutenbergFormats(id: Int, coverUrl: String): Map<String, String> {
        return mapOf(
            "image/jpeg" to coverUrl,
            "application/epub+zip" to "https://www.gutenberg.org/ebooks/$id.epub.images",
            "text/html; charset=utf-8" to "https://www.gutenberg.org/ebooks/$id.html.images",
            "text/plain; charset=utf-8" to "https://www.gutenberg.org/ebooks/$id.txt.utf-8"
        )
    }
    
    /**
     * Download a book from Gutenberg
     */
    fun downloadBook(
        book: GutendexBook,
        onSuccess: (DownloadedBook) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val bestFormat = book.getBestDownloadFormat()
        if (bestFormat == null) {
            onError("No downloadable format available for this book")
            return
        }
        
        val (downloadUrl, format) = bestFormat
        
        // Check if already downloading
        if (_downloadingBookIds.value.contains(book.id)) {
            Toast.makeText(context, "${book.title} is already downloading", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Store book title for notifications
        downloadingBookTitles[book.id] = book.title
        
        val job = viewModelScope.launch {
            _downloadingBookIds.value = _downloadingBookIds.value + book.id
            _downloadProgress.value = _downloadProgress.value + (book.id to 0f)
            
            // Show initial notification
            notificationManager.showDownloadProgress(book.id, book.title, 0)
            
            try {
                val downloadedBook = downloadFile(book, downloadUrl, format)
                
                if (downloadedBook != null) {
                    // Show completion notification
                    notificationManager.showDownloadComplete(book.id, book.title)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Downloaded: ${book.title}", Toast.LENGTH_SHORT).show()
                        onSuccess(downloadedBook)
                    }
                } else {
                    notificationManager.showDownloadFailed(book.id, book.title, "Failed to download file")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                        onError("Failed to download file")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Download was cancelled
                notificationManager.showDownloadCancelled(book.id, book.title)
            } catch (e: Exception) {
                notificationManager.showDownloadFailed(book.id, book.title, e.message)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    onError(e.message ?: "Unknown error")
                }
            } finally {
                _downloadingBookIds.value = _downloadingBookIds.value - book.id
                _downloadProgress.value = _downloadProgress.value - book.id
                downloadJobs.remove(book.id)
                downloadingBookTitles.remove(book.id)
            }
        }
        
        downloadJobs[book.id] = job
    }

    fun downloadBookById(
        gutenbergId: Int,
        onSuccess: (DownloadedBook) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_downloadingBookIds.value.contains(gutenbergId)) {
            Toast.makeText(context, "This book is already downloading", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            try {
                val book = withContext(Dispatchers.IO) {
                    gutendexApi.getBook(gutenbergId)
                }
                downloadBook(book, onSuccess, onError)
            } catch (e: Exception) {
                val message = e.message ?: "Could not find a downloadable Gutenberg edition"
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                onError(message)
            }
        }
    }
    
    /**
     * Cancel a download
     */
    fun cancelDownload(bookId: Int) {
        val title = downloadingBookTitles[bookId] ?: "Book"
        downloadJobs[bookId]?.cancel()
        downloadJobs.remove(bookId)
        _downloadingBookIds.value = _downloadingBookIds.value - bookId
        _downloadProgress.value = _downloadProgress.value - bookId
        notificationManager.showDownloadCancelled(bookId, title)
        downloadingBookTitles.remove(bookId)
        Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
    }
    
    private suspend fun downloadFile(
        book: GutendexBook,
        downloadUrl: String,
        format: BookFormat
    ): DownloadedBook? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Libri/1.0 (Android)")
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }
                
                val contentLength = response.body?.contentLength() ?: -1
                val inputStream = response.body?.byteStream() ?: return@withContext null
                
                // Create temp file
                val extension = format.extension
                val tempFile = File.createTempFile("gutenberg_${book.id}_", ".$extension", context.cacheDir)
                
                var bytesRead: Long = 0
                
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        
                        if (contentLength > 0) {
                            val progress = (bytesRead.toFloat() / contentLength.toFloat())
                            _downloadProgress.value = _downloadProgress.value + (book.id to progress)
                            
                            // Update notification progress if this download is still active
                            if (_downloadingBookIds.value.contains(book.id)) {
                                notificationManager.showDownloadProgress(
                                    book.id,
                                    book.title,
                                    (progress * 100).toInt()
                                )
                            }
                            else {
                                Log.d("GutenbergViewModel", "Skipping progress update for ${book.id} because it's no longer marked as downloading")
                            }
                        }
                    }
                }
                
                // Save to downloads
                val filename = "${book.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.$extension"
                val savedUri = tempFile.inputStream().use { fileInput ->
                    downloadsRepository.saveFileToDownloads(
                        filename = filename,
                        mimeType = format.mimeType,
                        inputStream = fileInput,
                        subFolder = "Scribe/Gutenberg"
                    )
                }
                
                // Clean up temp file
                tempFile.delete()
                
                if (savedUri != null) {
                    val downloadedBook = DownloadedBook(
                        id = "gutenberg_${book.id}",
                        title = book.title,
                        author = book.authorNames,
                        coverUrl = book.coverUrl,
                        filePath = savedUri.toString(),
                        fileUri = savedUri.toString(),
                        format = format,
                        source = BookSource.GUTENBERG,
                        gutenbergId = book.id
                    )
                    
                    // Save to downloads repository
                    downloadsRepository.saveBook(downloadedBook)
                    
                    return@withContext downloadedBook
                }
                
                return@withContext null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    /**
     * Check if a book is already downloaded
     */
    fun isBookDownloaded(gutenbergId: Int): Boolean {
        return downloadsRepository.isGutenbergBookDownloaded(gutenbergId)
    }
    
    /**
     * Get downloaded book by Gutenberg ID
     */
    fun getDownloadedBook(gutenbergId: Int): DownloadedBook? {
        return downloadsRepository.getDownloadedBooks().find { 
            it.gutenbergId == gutenbergId 
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
        
        // Unregister broadcast receiver
        try {
            context.unregisterReceiver(cancelReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
        DownloadCancelReceiver.clearCancelCallback()
    }
}

/**
 * Common Gutenberg book topics/genres
 */
object GutenbergTopics {
    val topics = listOf(
        "Fiction" to "fiction",
        "Science Fiction" to "science fiction",
        "Mystery" to "mystery",
        "Romance" to "romance",
        "Adventure" to "adventure",
        "Horror" to "horror",
        "Poetry" to "poetry",
        "Drama" to "drama",
        "History" to "history",
        "Philosophy" to "philosophy",
        "Science" to "science",
        "Children" to "children",
        "Biography" to "biography",
        "Travel" to "travel"
    )
}

data class GutenbergRecommendationState(
    val books: List<GutendexBook> = emptyList(),
    val headline: String = "Popular free reads",
    val subtitle: String = "Project Gutenberg popular public-domain books",
    val isLoading: Boolean = false,
    val error: String? = null
)
