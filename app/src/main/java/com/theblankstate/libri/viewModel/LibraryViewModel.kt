package com.theblankstate.libri.viewModel

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theblankstate.libri.MainActivity
import com.theblankstate.libri.data.LibraryRepository
import com.theblankstate.libri.data.ShelvesRepository
import com.theblankstate.libri.data_retrieval.DownloadsRepository
import com.theblankstate.libri.data_retrieval.repository
import com.theblankstate.libri.data_retrieval.retrofitinatance
import com.theblankstate.libri.datamodel.DownloadedBook
import com.theblankstate.libri.datamodel.BookSource
import com.theblankstate.libri.datamodel.LibraryBook
import com.theblankstate.libri.datamodel.ReadingStatus
import com.theblankstate.libri.datamodel.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed class LibraryUiState {
    object Loading : LibraryUiState()
    data class Success(val books: List<LibraryBook>) : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
}

enum class SortOption(val displayName: String) {
    DATE_ADDED("Recently Added"),
    TITLE("Title"),
    AUTHOR("Author"),
    RATING("Rating"),
    PROGRESS("Progress")
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val libraryRepository = LibraryRepository()
    private val shelvesRepository = ShelvesRepository()
    private val apiRepository = repository()
    private val downloadsRepository = DownloadsRepository(application)
    private val context = application.applicationContext
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val DOWNLOAD_CHANNEL_ID = "download_channel"
        private const val BASE_NOTIFICATION_ID = 1001
        const val ACTION_CANCEL_DOWNLOAD = "com.example.learncompose.CANCEL_DOWNLOAD"
        const val EXTRA_BOOK_ID = "book_id"
        
        // Static reference for broadcast receiver to access
        private var instance: LibraryViewModel? = null
    }
    
    // Track notification IDs per book
    private val bookNotificationIds = mutableMapOf<String, Int>()
    private var nextNotificationId = BASE_NOTIFICATION_ID
    
    // Track download jobs for cancellation
    private val downloadJobs = mutableMapOf<String, Job>()
    private val cancelledDownloads = mutableSetOf<String>()
    
    // BroadcastReceiver for handling cancel actions from notifications
    private val downloadCancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CANCEL_DOWNLOAD) {
                val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
                if (bookId != null) {
                    cancelDownload(bookId)
                }
            }
        }
    }
    
    init {
        createNotificationChannel()
        instance = this
        
        // Register broadcast receiver for cancel actions
        ContextCompat.registerReceiver(
            context,
            downloadCancelReceiver,
            IntentFilter(ACTION_CANCEL_DOWNLOAD),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    // Gutendex API client for Gutenberg downloads
    private val gutendexApi = retrofitinatance.gutendexApi
    
    override fun onCleared() {
        super.onCleared()
        instance = null
        // Unregister broadcast receiver
        try {
            context.unregisterReceiver(downloadCancelReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }
        // Cancel all ongoing downloads
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
    }
    
    /**
     * Cancel a download by book ID
     */
    fun cancelDownload(bookId: String) {
        cancelledDownloads.add(bookId)
        downloadJobs[bookId]?.cancel()
        downloadJobs.remove(bookId)
        
        // Remove from downloading state
        _downloadingBookIds.value = _downloadingBookIds.value - bookId
        _downloadProgressMap.value = _downloadProgressMap.value - bookId
        
        // Cancel notification
        cancelDownloadNotification(bookId)
        
        // Show cancelled toast
        Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
    }
    
    // Static method for broadcast receiver
    fun cancelDownloadStatic(bookId: String) {
        instance?.cancelDownload(bookId)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Book download notifications"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    private fun getNotificationIdForBook(bookId: String): Int {
        return bookNotificationIds.getOrPut(bookId) {
            nextNotificationId++
        }
    }
    
    private fun showDownloadProgressNotification(bookId: String, bookTitle: String, progress: Int) {
        if (!hasNotificationPermission()) return
        
        val notificationId = getNotificationIdForBook(bookId)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Cancel action intent
        val cancelIntent = Intent(ACTION_CANCEL_DOWNLOAD).apply {
            putExtra(EXTRA_BOOK_ID, bookId)
            setPackage(context.packageName)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 10000, // Unique request code for cancel
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Downloading: $bookTitle")
            .setContentText(if (progress < 100) "$progress% complete" else "Finishing up...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
        
        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted
        }
    }
    
    private fun showDownloadCompleteNotification(bookId: String, bookTitle: String) {
        if (!hasNotificationPermission()) return
        
        val notificationId = getNotificationIdForBook(bookId)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Cancel any existing progress notification first to ensure UI shows completed state
        cancelDownloadNotification(bookId)

        val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText("$bookTitle is ready to read offline")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            // Clear any progress state
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted
        }
        
        // Remove from tracking after a delay
        bookNotificationIds.remove(bookId)
    }
    
    private fun showDownloadFailedNotification(bookId: String, bookTitle: String, reason: String) {
        if (!hasNotificationPermission()) return
        
        val notificationId = getNotificationIdForBook(bookId)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Cancel any existing progress notification first
        cancelDownloadNotification(bookId)

        val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$bookTitle: $reason")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$bookTitle: $reason"))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            // Clear any progress state
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted
        }
        
        // Remove from tracking
        bookNotificationIds.remove(bookId)
    }
    
    private fun cancelDownloadNotification(bookId: String) {
        val notificationId = bookNotificationIds[bookId] ?: return
        notificationManager.cancel(notificationId)
        bookNotificationIds.remove(bookId)
    }
    
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    private val _selectedBook = MutableStateFlow<LibraryBook?>(null)
    val selectedBook: StateFlow<LibraryBook?> = _selectedBook.asStateFlow()
    
    private val _filterStatus = MutableStateFlow<ReadingStatus?>(null)
    val filterStatus: StateFlow<ReadingStatus?> = _filterStatus.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _sortOption = MutableStateFlow(SortOption.DATE_ADDED)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()
    
    private val _showDownloads = MutableStateFlow(false)
    val showDownloads: StateFlow<Boolean> = _showDownloads.asStateFlow()
    
    private val _downloadedBooks = MutableStateFlow<List<DownloadedBook>>(emptyList())
    val downloadedBooks: StateFlow<List<DownloadedBook>> = _downloadedBooks.asStateFlow()
    
    // Track multiple downloads simultaneously
    private val _downloadingBookIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingBookIds: StateFlow<Set<String>> = _downloadingBookIds.asStateFlow()
    
    // Progress per book
    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Float>> = _downloadProgressMap.asStateFlow()
    
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    
    // Helper to check if a specific book is downloading
    fun isBookDownloading(bookId: String): Boolean = _downloadingBookIds.value.contains(bookId)
    
    private var allBooks: List<LibraryBook> = emptyList()
    
    fun loadLibrary(uid: String) {
        viewModelScope.launch {
            try {
                libraryRepository.getLibraryBooks(uid).collect { books ->
                    allBooks = books
                    _uiState.value = LibraryUiState.Success(filterAndSortBooks(books))
                    
                    // Update selected book if it exists in the new list
                    _selectedBook.value?.let { current ->
                        val updated = books.find { it.id == current.id }
                        if (updated != null) {
                            _selectedBook.value = updated
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = LibraryUiState.Error(e.message ?: "Failed to load library")
            }
        }
    }
    
    fun addBookToLibrary(uid: String, book: LibraryBook, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            val result = libraryRepository.addBookToLibrary(uid, book)
            result.fold(
                onSuccess = { 
                    onSuccess()
                    // Attempt to fetch ISBN if missing and openLibraryId exists
                    if (book.isbn.isNullOrBlank() && !book.openLibraryId.isNullOrBlank()) {
                        fetchAndUpdateIsbn(uid, book.id, book.openLibraryId)
                    }
                },
                onFailure = { onError(it.message ?: "Failed to add book") }
            )
        }
    }
    
    fun fetchAndUpdateIsbn(uid: String, bookId: String, openLibraryId: String) {
        viewModelScope.launch {
            try {
                // Get editions for this work
                val editions = apiRepository.getEditions(openLibraryId)
                
                // Find first edition with ISBN-13
                val isbn = editions.firstNotNullOfOrNull { edition ->
                    edition.isbn13?.firstOrNull()
                }
                
                // Update the book if ISBN found
                isbn?.let {
                    libraryRepository.updateIsbn(uid, bookId, it)
                }
            } catch (e: Exception) {
                // Silently fail - ISBN fetch is not critical
            }
        }
    }
    
    fun removeBookFromLibrary(uid: String, bookId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            val result = libraryRepository.removeBookFromLibrary(uid, bookId, shelvesRepository)
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Failed to remove book") }
            )
        }
    }
    
    fun updateReadingStatus(uid: String, bookId: String, status: ReadingStatus) {
        viewModelScope.launch {
            libraryRepository.updateReadingStatus(uid, bookId, status)
            // If status is IN_PROGRESS, ensure dateStarted is set
            if (status == ReadingStatus.IN_PROGRESS) {
                // Repository handles this logic, but we can double check or refresh
            }
        }
    }
    
    fun updateReadingProgress(uid: String, bookId: String, currentPage: Int, totalPages: Int) {
        viewModelScope.launch {
            libraryRepository.updateReadingProgress(uid, bookId, currentPage, totalPages)
        }
    }
    
    fun updateRating(uid: String, bookId: String, rating: Float) {
        viewModelScope.launch {
            libraryRepository.updateRating(uid, bookId, rating)
        }
    }
    
    fun updateComment(uid: String, bookId: String, comment: String) {
        viewModelScope.launch {
            libraryRepository.updateComment(uid, bookId, comment)
        }
    }
    
    fun toggleFavorite(uid: String, bookId: String) {
        viewModelScope.launch {
            libraryRepository.toggleFavorite(uid, bookId)
        }
    }
    
    fun selectBook(book: LibraryBook) {
        _selectedBook.value = book
    }
    
    fun setFilterStatus(status: ReadingStatus?) {
        _filterStatus.value = status
        _showDownloads.value = false
        refreshFilters()
    }
    
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        refreshFilters()
    }
    
    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        refreshFilters()
    }
    
    fun setShowDownloads(show: Boolean) {
        _showDownloads.value = show
        if (show) {
            _filterStatus.value = null
            refreshDownloads()
        } else {
            refreshFilters()
        }
    }
    
    fun refreshDownloads() {
        viewModelScope.launch {
            _downloadedBooks.value = downloadsRepository.getDownloadedBooks()
        }
    }
    
    fun removeDownloadedBook(bookId: String) {
        viewModelScope.launch {
            downloadsRepository.removeBook(bookId)
            refreshDownloads()
        }
    }
    
    fun importBook(uri: android.net.Uri, title: String) {
        viewModelScope.launch {
            val book = downloadsRepository.importBook(uri, title)
            if (book != null) {
                refreshDownloads()
            }
        }
    }
    
    fun fetchBookMetadata(query: String, onResult: (LibraryBook?) -> Unit) {
        viewModelScope.launch {
            try {
                // Try searching as ISBN first
                var books = apiRepository.getbooks(isbn = query)
                if (books.isEmpty()) {
                    // Try as general query
                    books = apiRepository.getbooks(query = query)
                }
                
                val bookModel = books.firstOrNull()
                if (bookModel != null) {
                    // Fetch full work details for description
                    var description = bookModel.firstSentence?.firstOrNull()
                    var isbn = bookModel.isbn?.firstOrNull()
                    var publisher = bookModel.publisher?.firstOrNull()
                    var totalPages = bookModel.number_of_pages ?: 0
                    
                    val workId = bookModel.key?.removePrefix("/works/")
                    
                    if (!workId.isNullOrBlank()) {
                        try {
                            // Fetch Work Details for Description
                            val workDetails = apiRepository.getWorkDetails(workId)
                            workDetails?.getDescriptionText()?.let { fullDesc ->
                                if (fullDesc.isNotBlank()) {
                                    description = fullDesc
                                }
                            }
                            
                            // Fetch Editions for ISBN, Publisher, Pages if missing
                            if (isbn == null || publisher == null || totalPages == 0) {
                                val editions = apiRepository.getEditions(workId)
                                val bestEdition = editions.firstOrNull { !it.isbn13.isNullOrEmpty() } 
                                    ?: editions.firstOrNull { !it.isbn10.isNullOrEmpty() }
                                    ?: editions.firstOrNull()
                                
                                if (bestEdition != null) {
                                    if (isbn == null) {
                                        isbn = bestEdition.isbn13?.firstOrNull() ?: bestEdition.isbn10?.firstOrNull()
                                    }
                                    if (publisher == null) {
                                        publisher = bestEdition.publishers?.firstOrNull()
                                    }
                                    if (totalPages == 0) {
                                        totalPages = bestEdition.numberOfPages ?: 0
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore errors during extra detail fetch
                        }
                    }

                    val libraryBook = LibraryBook(
                        title = bookModel.title,
                        author = bookModel.author_name?.firstOrNull() ?: "",
                        coverUrl = bookModel.coverUrl,
                        isbn = isbn,
                        openLibraryId = bookModel.key,
                        totalPages = totalPages,
                        description = description,
                        publisher = publisher
                    )
                    onResult(libraryBook)
                } else {
                    onResult(null)
                }
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun importLibraryBook(uid: String, uri: android.net.Uri, metadata: LibraryBook) {
        viewModelScope.launch {
            // Save file using DownloadsRepository
            val downloadedBook = downloadsRepository.importBook(uri, metadata.title)
            
            if (downloadedBook != null) {
                // Create LibraryBook with metadata and local file path
                val libraryBook = metadata.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    localFilePath = downloadedBook.filePath,
                    localFileFormat = downloadedBook.format,
                    dateAdded = System.currentTimeMillis(),
                    // Ensure title/author are set if metadata was empty
                    title = if (metadata.title.isBlank()) downloadedBook.title else metadata.title,
                    author = if (metadata.author.isBlank()) "Imported" else metadata.author
                )
                
                libraryRepository.addBookToLibrary(uid, libraryBook)
                refreshDownloads()
            }
        }
    }
    
    fun fetchAndUpdateBookDetails(uid: String, book: LibraryBook) {
        viewModelScope.launch {
            try {
                var updatedBook = book
                var hasUpdates = false
                var pages = 0
                
                // If pages are missing, try to fetch them
                if (book.totalPages == 0) {
                    // Strategy 1: Use OpenLibrary ID (Work ID)
                    if (!book.openLibraryId.isNullOrBlank()) {
                        val workId = book.openLibraryId.removePrefix("/works/")
                        val editions = apiRepository.getEditions(workId)
                        val bestEdition = editions.firstOrNull { !it.isbn13.isNullOrEmpty() } 
                            ?: editions.firstOrNull { !it.isbn10.isNullOrEmpty() }
                            ?: editions.firstOrNull()
                        
                        pages = bestEdition?.numberOfPages ?: 0
                    }
                    
                    // Strategy 2: Use ISBN if Strategy 1 failed or ID was missing
                    if (pages == 0 && !book.isbn.isNullOrBlank()) {
                         val searchResults = apiRepository.getbooks(isbn = book.isbn)
                         pages = searchResults.firstOrNull()?.number_of_pages ?: 0
                    }
                    
                    if (pages > 0) {
                        updatedBook = updatedBook.copy(totalPages = pages)
                        hasUpdates = true
                    }
                }
                
                if (hasUpdates) {
                    libraryRepository.addBookToLibrary(uid, updatedBook)
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }
    
    private fun refreshFilters() {
        _uiState.value = LibraryUiState.Success(filterAndSortBooks(allBooks))
    }
    
    private fun filterAndSortBooks(books: List<LibraryBook>): List<LibraryBook> {
        var filtered = books
        
        _filterStatus.value?.let { status ->
            filtered = filtered.filter { it.readingStatusEnum == status }
        }
        
        if (_searchQuery.value.isNotBlank()) {
            filtered = filtered.filter { book ->
                book.title.contains(_searchQuery.value, ignoreCase = true) ||
                book.author.contains(_searchQuery.value, ignoreCase = true)
            }
        }
        
        return when (_sortOption.value) {
            SortOption.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
            SortOption.TITLE -> filtered.sortedBy { it.title }
            SortOption.AUTHOR -> filtered.sortedBy { it.author }
            SortOption.RATING -> filtered.sortedByDescending { it.rating }
            SortOption.PROGRESS -> filtered.sortedByDescending { it.readingProgress }
        }
    }
    
    /**
     * Check if a book can be downloaded (has IA id and is NOT borrowable)
     */
    fun canDownloadBook(book: LibraryBook): Boolean {
        val hasIaId = !book.internetArchiveId.isNullOrEmpty()
        val hasGutenbergId = book.gutenbergId != null
        val isBorrowable = book.ebookAccess == "borrowable"
        val isPublic = book.ebookAccess == "public"
        val alreadyDownloaded = !book.localFilePath.isNullOrEmpty()
        
        // Can download via Internet Archive if: has IA ID, is public (not borrowable), and not already downloaded
        val canDownloadViaIA = hasIaId && (isPublic || book.ebookAccess.isNullOrEmpty()) && !isBorrowable && !alreadyDownloaded
        // Can download via Gutenberg if: has gutenberg id and not already downloaded and not borrowable
        val canDownloadViaGutenberg = hasGutenbergId && !alreadyDownloaded && !isBorrowable
        return canDownloadViaIA || canDownloadViaGutenberg
    }
    
    /**
     * Download a book from Internet Archive
     */
    fun downloadBook(uid: String, book: LibraryBook, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val gId = book.gutenbergId
        // Handle Gutenberg downloads first
        if (gId != null) {
            if (book.ebookAccess == "borrowable") {
                val errorMsg = "This book requires borrowing, download not available"
                showDownloadFailedNotification(book.id, book.title, errorMsg)
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                onError(errorMsg)
                return
            }

            if (_downloadingBookIds.value.contains(book.id)) {
                Toast.makeText(context, "${book.title} is already downloading", Toast.LENGTH_SHORT).show()
                return
            }

            val job = viewModelScope.launch {
                _downloadingBookIds.value = _downloadingBookIds.value + book.id
                _downloadProgressMap.value = _downloadProgressMap.value + (book.id to 0f)
                showDownloadProgressNotification(book.id, book.title, 0)

                try {
                    val gutBook = gutendexApi.getBook(gId)
                    val bestFormat = gutBook.getBestDownloadFormat()
                    if (bestFormat == null) {
                        val err = "No downloadable format available for this book"
                        withContext(Dispatchers.Main) {
                            showDownloadFailedNotification(book.id, book.title, err)
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            onError(err)
                        }
                        return@launch
                    }
                    val (downloadUrl, format) = bestFormat
                    val filename = "${book.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.${format.extension}"
                    val savedUri = downloadFromUrlWithProgress(downloadUrl, filename, book.id, book.title, mimeType = format.mimeType, subFolder = "Scribe/Gutenberg")

                    if (savedUri != null) {
                        val updatedBook = book.copy(
                            localFilePath = savedUri,
                            localFileFormat = format
                        )
                        libraryRepository.addBookToLibrary(uid, updatedBook)

                        val downloadedBook = DownloadedBook(
                            id = book.id,
                            title = book.title,
                            author = book.author,
                            coverUrl = book.coverUrl,
                            filePath = savedUri,
                            fileUri = savedUri,
                            format = format,
                            source = BookSource.GUTENBERG,
                            gutenbergId = gId
                        )
                        downloadsRepository.saveBook(downloadedBook)

                        if (_selectedBook.value?.id == book.id) {
                            _selectedBook.value = updatedBook
                        }

                        withContext(Dispatchers.Main) {
                            _downloadingBookIds.value = _downloadingBookIds.value - book.id
                            _downloadProgressMap.value = _downloadProgressMap.value - book.id
                            showDownloadCompleteNotification(book.id, book.title)
                            Toast.makeText(context, "Download complete: ${book.title}", Toast.LENGTH_SHORT).show()
                            onSuccess()
                        }
                    } else {
                        val err = "Failed to download file - file not found or server error"
                        withContext(Dispatchers.Main) {
                            showDownloadFailedNotification(book.id, book.title, err)
                            Toast.makeText(context, "Download failed: $err", Toast.LENGTH_LONG).show()
                            onError(err)
                        }
                    }
                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Unknown error occurred"
                    withContext(Dispatchers.Main) {
                        _downloadingBookIds.value = _downloadingBookIds.value - book.id
                        _downloadProgressMap.value = _downloadProgressMap.value - book.id
                        showDownloadFailedNotification(book.id, book.title, errorMsg)
                        Toast.makeText(context, "Download failed: $errorMsg", Toast.LENGTH_LONG).show()
                        onError(errorMsg)
                    }
                } finally {
                    _downloadingBookIds.value = _downloadingBookIds.value - book.id
                    _downloadProgressMap.value = _downloadProgressMap.value - book.id
                    downloadJobs.remove(book.id)
                }
            }
            downloadJobs[book.id] = job
            return
        }
        
        val iaId = book.internetArchiveId
        if (iaId.isNullOrEmpty()) {
            val errorMsg = "No Internet Archive ID available"
            showDownloadFailedNotification(book.id, book.title, errorMsg)
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            onError(errorMsg)
            return
        }
        
        if (book.ebookAccess == "borrowable") {
            val errorMsg = "This book requires borrowing, download not available"
            showDownloadFailedNotification(book.id, book.title, errorMsg)
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            onError(errorMsg)
            return
        }
        
        // Check if already downloading this book
        if (_downloadingBookIds.value.contains(book.id)) {
            Toast.makeText(context, "${book.title} is already downloading", Toast.LENGTH_SHORT).show()
            return
        }
        
        val job = viewModelScope.launch {
            // Add to downloading set
            _downloadingBookIds.value = _downloadingBookIds.value + book.id
            _downloadProgressMap.value = _downloadProgressMap.value + (book.id to 0f)
            
            // Show initial notification
            showDownloadProgressNotification(book.id, book.title, 0)
            
            try {
                val downloadUrl = "https://archive.org/download/$iaId/${iaId}.pdf"
                val savedUri = downloadFromUrlWithProgress(downloadUrl, "${book.title}.pdf", book.id, book.title)
                
                if (savedUri != null) {
                    // Update the library book with the local file path
                    val updatedBook = book.copy(
                        localFilePath = savedUri,
                        localFileFormat = BookFormat.PDF
                    )
                    libraryRepository.addBookToLibrary(uid, updatedBook)
                    
                    // Also save to downloads repository for tracking
                    val downloadedBook = DownloadedBook(
                        id = book.id,
                        title = book.title,
                        author = book.author,
                        coverUrl = book.coverUrl,
                        filePath = savedUri,
                        fileUri = savedUri,
                        format = BookFormat.PDF
                    )
                    downloadsRepository.saveBook(downloadedBook)
                    
                    // Update selected book if it's the same
                    if (_selectedBook.value?.id == book.id) {
                        _selectedBook.value = updatedBook
                    }
                    
                    withContext(Dispatchers.Main) {
                        // Mark completed to avoid late progress updates
                        _downloadingBookIds.value = _downloadingBookIds.value - book.id
                        _downloadProgressMap.value = _downloadProgressMap.value - book.id
                        showDownloadCompleteNotification(book.id, book.title)
                        Toast.makeText(context, "Download complete: ${book.title}", Toast.LENGTH_SHORT).show()
                        onSuccess()
                    }
                } else {
                    val errorMsg = "Failed to download file - file not found or server error"
                    withContext(Dispatchers.Main) {
                        showDownloadFailedNotification(book.id, book.title, errorMsg)
                        Toast.makeText(context, "Download failed: $errorMsg", Toast.LENGTH_LONG).show()
                        onError(errorMsg)
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error occurred"
                withContext(Dispatchers.Main) {
                    // Remove downloading tracking before showing failure
                    _downloadingBookIds.value = _downloadingBookIds.value - book.id
                    _downloadProgressMap.value = _downloadProgressMap.value - book.id
                    showDownloadFailedNotification(book.id, book.title, errorMsg)
                    Toast.makeText(context, "Download failed: $errorMsg", Toast.LENGTH_LONG).show()
                    onError(errorMsg)
                }
            } finally {
                // Remove from downloading set and jobs map
                _downloadingBookIds.value = _downloadingBookIds.value - book.id
                _downloadProgressMap.value = _downloadProgressMap.value - book.id
                downloadJobs.remove(book.id)
            }
        }
        
        // Store the job for cancellation support
        downloadJobs[book.id] = job
    }
    
    private suspend fun downloadFromUrlWithProgress(
        url: String,
        filename: String,
        bookId: String,
        bookTitle: String,
        mimeType: String = "application/pdf",
        subFolder: String = "Scribe/Books"
    ): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ScribeApp/1.0 (Android)")
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Try alternative PDF URL format
                    val altUrl = url.replace(".pdf", "_text.pdf")
                    return@withContext tryAlternativeDownload(altUrl, filename, bookId, bookTitle, mimeType, subFolder)
                }
                
                val contentLength = response.body?.contentLength() ?: -1
                val inputStream = response.body?.byteStream() ?: return@withContext null
                
                // Download with progress tracking
                val tempFile = java.io.File.createTempFile("download_", ".pdf", context.cacheDir)
                var bytesRead: Long = 0
                var lastProgress = 0
                
                try {
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            // Check if cancelled
                            ensureActive()
                            if (cancelledDownloads.contains(bookId)) {
                                throw kotlinx.coroutines.CancellationException("Download cancelled by user")
                            }
                            
                            output.write(buffer, 0, read)
                            bytesRead += read
                            
                            if (contentLength > 0) {
                                val progress = ((bytesRead * 100) / contentLength).toInt()
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    _downloadProgressMap.value = _downloadProgressMap.value + (bookId to progress.toFloat())
                                    withContext(Dispatchers.Main) {
                                        if (_downloadingBookIds.value.contains(bookId)) {
                                            showDownloadProgressNotification(bookId, bookTitle, progress)
                                        } else {
                                            Log.d("LibraryViewModel", "Skipping progress update for $bookId: not in downloading set")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Clean up temp file on cancellation
                    tempFile.delete()
                    cancelledDownloads.remove(bookId)
                    throw e
                }
                
                // Save to downloads
                val savedUri = tempFile.inputStream().use { fileInput ->
                    downloadsRepository.saveFileToDownloads(
                        filename = filename.replace(Regex("[^a-zA-Z0-9.-]"), "_"),
                        mimeType = mimeType,
                        inputStream = fileInput,
                        subFolder = subFolder
                    )
                }
                
                // Clean up temp file
                tempFile.delete()
                
                return@withContext savedUri?.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
    
    private suspend fun tryAlternativeDownload(url: String, filename: String, bookId: String, bookTitle: String, mimeType: String = "application/pdf", subFolder: String = "Scribe/Books"): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ScribeApp/1.0 (Android)")
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val contentLength = response.body?.contentLength() ?: -1
                val inputStream = response.body?.byteStream() ?: return@withContext null
                
                // Download with progress tracking
                val tempFile = java.io.File.createTempFile("download_", ".pdf", context.cacheDir)
                var bytesRead: Long = 0
                var lastProgress = 0
                
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        
                        if (contentLength > 0) {
                            val progress = ((bytesRead * 100) / contentLength).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                _downloadProgressMap.value = _downloadProgressMap.value + (bookId to progress.toFloat())
                                withContext(Dispatchers.Main) {
                                    if (_downloadingBookIds.value.contains(bookId)) {
                                        showDownloadProgressNotification(bookId, bookTitle, progress)
                                    } else {
                                        Log.d("LibraryViewModel", "Skipping progress update for $bookId: not in downloading set")
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Save to downloads
                    val savedUri = tempFile.inputStream().use { fileInput ->
                    downloadsRepository.saveFileToDownloads(
                        filename = filename.replace(Regex("[^a-zA-Z0-9.-]"), "_"),
                        mimeType = mimeType,
                        inputStream = fileInput,
                        subFolder = subFolder
                    )
                }
                
                // Clean up temp file
                tempFile.delete()
                
                return@withContext savedUri?.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    /**
     * Delete downloaded file for a book (keeps book in library)
     */
    fun deleteDownloadedFile(uid: String, book: LibraryBook) {
        viewModelScope.launch {
            try {
                // Try to delete the file if it exists
                book.localFilePath?.let { filePath ->
                    try {
                        val uri = android.net.Uri.parse(filePath)
                        context.contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        // File might already be deleted or inaccessible
                    }
                }
                
                // Update the book in Firebase to remove localFilePath
                val updatedBook = book.copy(
                    localFilePath = null,
                    localFileFormat = null
                )
                libraryRepository.addBookToLibrary(uid, updatedBook)
                
                // Remove from downloads repository
                downloadsRepository.removeBook(book.id)
                
                // Update selected book if it's the same
                if (_selectedBook.value?.id == book.id) {
                    _selectedBook.value = updatedBook
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download deleted", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
