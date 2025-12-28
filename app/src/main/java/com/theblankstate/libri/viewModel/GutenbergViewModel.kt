package com.theblankstate.libri.viewModel

import android.app.Application
import android.content.IntentFilter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theblankstate.libri.data_retrieval.retrofitinatance
import com.theblankstate.libri.data_retrieval.DownloadsRepository
import com.theblankstate.libri.data_retrieval.DownloadNotificationManager
import com.theblankstate.libri.data_retrieval.DownloadCancelReceiver
import com.theblankstate.libri.datamodel.BookFormat
import com.theblankstate.libri.datamodel.BookSource
import com.theblankstate.libri.datamodel.DownloadedBook
import com.theblankstate.libri.datamodel.GutendexBook
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
    fun searchBooks(query: String, language: String? = "en") {
        Log.d("GutenbergViewModel", "searchBooks called for query: $query")
        if (query.isBlank()) return
        
        currentSearchQuery = query
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
                _searchError.value = "Connection timed out. Please try again."
                // Keep existing results if any
            } catch (e: java.io.IOException) {
                _searchError.value = "Network error. Please check your connection."
            } catch (e: Exception) {
                _searchError.value = "Search failed. Please try again."
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
                _searchError.value = "Connection timed out. Please try again."
            } catch (e: java.io.IOException) {
                _searchError.value = "Network error. Please check your connection."
            } catch (e: Exception) {
                _searchError.value = "Topic search failed. Please try again."
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    /**
     * Retry last search
     */
    fun retrySearch() {
        currentSearchQuery?.let { searchBooks(it) }
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
                        languages = "en",
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
    fun loadPopularBooks(language: String? = "en") {
        viewModelScope.launch {
            _isLoadingPopular.value = true
            
            try {
                val response = gutendexApi.getPopularBooks(
                    languages = language
                )
                _popularBooks.value = response.results
                popularNextPageUrl = response.next
            } catch (e: Exception) {
                // Silently fail, keep existing data
            } finally {
                _isLoadingPopular.value = false
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
    suspend fun getBooksByTopic(topic: String, language: String? = "en"): List<GutendexBook> {
        currentTopic = topic
        return try {
            val response = gutendexApi.getBooksByTopic(
                topic = topic,
                languages = language
            )
            topicNextPageUrl = response.next
            response.results
        } catch (e: Exception) {
            topicNextPageUrl = null
            emptyList()
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
                // Handle error
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
        currentPage = 1
        hasMoreResults = true
        _hasMoreSearchResults.value = true
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
                    // Mark completed to avoid late progress updates showing after completion
                    _downloadingBookIds.value = _downloadingBookIds.value - book.id
                    _downloadProgress.value = _downloadProgress.value - book.id

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
                _downloadingBookIds.value = _downloadingBookIds.value - book.id
                _downloadProgress.value = _downloadProgress.value - book.id
                notificationManager.showDownloadCancelled(book.id, book.title)
            } catch (e: Exception) {
                _downloadingBookIds.value = _downloadingBookIds.value - book.id
                _downloadProgress.value = _downloadProgress.value - book.id
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
                .header("User-Agent", "ScribeApp/1.0 (Android)")
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
        return downloadsRepository.getDownloadedBooks().any { 
            it.gutenbergId == gutenbergId 
        }
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
