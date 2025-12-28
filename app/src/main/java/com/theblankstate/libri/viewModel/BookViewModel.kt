package com.theblankstate.libri.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theblankstate.libri.data_retrieval.repository
import com.theblankstate.libri.datamodel.AdvancedSearchFilters
import com.theblankstate.libri.datamodel.SortOption
import com.theblankstate.libri.datamodel.WorkDetailModel
import com.theblankstate.libri.datamodel.bookModel
import com.theblankstate.libri.states.state
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
 
class BookViewModel : ViewModel() {

    private val bookRepository = repository()

    private val _bookState = MutableStateFlow<state>(state.loading)
    val bookState: StateFlow<state> = _bookState

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState

    private val _selectedBook = MutableStateFlow<bookModel?>(null)
    val selectedBook: StateFlow<bookModel?> = _selectedBook

    private val _similarBooks = MutableStateFlow<List<bookModel>>(emptyList())
    val similarBooks: StateFlow<List<bookModel>> = _similarBooks

    private val _workDetail = MutableStateFlow<WorkDetailModel?>(null)
    val workDetail: StateFlow<WorkDetailModel?> = _workDetail

    private val _editions = MutableStateFlow<List<com.theblankstate.libri.datamodel.EditionModel>>(emptyList())
    val editions: StateFlow<List<com.theblankstate.libri.datamodel.EditionModel>> = _editions

    private val _ratings = MutableStateFlow<com.theblankstate.libri.datamodel.RatingsModel?>(null)
    val ratings: StateFlow<com.theblankstate.libri.datamodel.RatingsModel?> = _ratings

    private val _bookshelves = MutableStateFlow<com.theblankstate.libri.datamodel.BookshelfModel?>(null)
    val bookshelves: StateFlow<com.theblankstate.libri.datamodel.BookshelfModel?> = _bookshelves

    private val _isLoadingMoreEditions = MutableStateFlow(false)
    val isLoadingMoreEditions: StateFlow<Boolean> = _isLoadingMoreEditions

    private var currentEditionsOffset = 0
    private var canLoadMoreEditions = true

    private var cachedBooks: List<bookModel> = emptyList()
    private var currentQuery: String? = null
    private var currentOffset = 0
    private val pageLimit = 20
    private var canLoadMore = true
    private var isLoadingMore = false

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory

    private val _advancedFilters = MutableStateFlow(AdvancedSearchFilters())
    val advancedFilters: StateFlow<AdvancedSearchFilters> = _advancedFilters

    fun addSearchHistoryItem(query: String) {
        if (query.isBlank()) return
        val currentHistory = _searchHistory.value.toMutableList()
        if (currentHistory.contains(query)) {
            currentHistory.remove(query)
        }
        currentHistory.add(0, query)
        _searchHistory.value = currentHistory
    }

    fun fetchBooksByQuery(query: String) {
        if (query.isBlank()) {
            _bookState.value = state.error("Please enter a search query.")
            return
        }
        currentQuery = query.trim()
        currentOffset = 0
        canLoadMore = true
        fetchBooksInternal(query = currentQuery)
    }

    fun searchByAuthor(author: String) {
        if (author.isBlank()) {
            _bookState.value = state.error("Please enter an author name.")
            return
        }
        fetchBooksInternal(author = author.trim())
    }

    fun searchByGenre(genre: String) {
        if (genre.isBlank()) {
            _bookState.value = state.error("Please enter a genre.")
            return
        }
        fetchBooksInternal(subject = genre.trim())
    }

    private fun fetchBooksInternal(
        query: String? = null,
        author: String? = null,
        subject: String? = null
    ) {
        viewModelScope.launch {
            _bookState.value = state.loading
            try {
                val books = bookRepository.getbooks(query = query, author = author, subject = subject, limit = pageLimit, offset = 0)
                cachedBooks = books
                currentOffset = books.size
                canLoadMore = books.size >= pageLimit
                _bookState.value = state.success(books)
                _filterState.value = FilterState(
                    availableAuthors = books.flatMap { it.author_name.orEmpty() }.distinct().sorted(),
                    availableGenres = books.flatMap { it.subject.orEmpty() }.distinct().sorted(),
                    selectedAuthor = null,
                    selectedGenre = null,
                    selectedLanguage = null,
                    selectedYearStart = null,
                    selectedYearEnd = null
                )
            } catch (e: Exception) {
                _bookState.value = state.error("Failed to fetch books: ${e.message}")
            }
        }
    }

    fun loadMoreBooks() {
        val query = currentQuery ?: return
        if (!canLoadMore || isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch {
            try {
                val newOffset = currentOffset
                val books = bookRepository.getbooks(query = query, limit = pageLimit, offset = newOffset)
                if (books.isEmpty()) {
                    canLoadMore = false
                } else {
                    currentOffset = newOffset + books.size
                    val combined = cachedBooks + books
                    // Keep only last 1000 to prevent memory blow-ups if needed
                    cachedBooks = combined
                    _bookState.value = state.success(combined)
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun applyAdvancedSearch(filters: AdvancedSearchFilters) {
        viewModelScope.launch {
            _bookState.value = state.loading
            _advancedFilters.value = filters
            try {
                val books = bookRepository.getbooks(
                    query = filters.query,
                    title = filters.title,
                    author = filters.author,
                    subject = filters.subject,
                    isbn = filters.isbn,
                    publisher = filters.publisher,
                    language = if (filters.language == "und") null else filters.language,
                    sort = filters.sortBy.value.takeIf { it.isNotEmpty() }
                )
                cachedBooks = books
                _bookState.value = state.success(books)
                _filterState.value = FilterState(
                    availableAuthors = books.flatMap { it.author_name.orEmpty() }.distinct().sorted(),
                    availableGenres = books.flatMap { it.subject.orEmpty() }.distinct().sorted(),
                    selectedAuthor = null,
                    selectedGenre = null,
                    selectedLanguage = null,
                    selectedYearStart = null,
                    selectedYearEnd = null
                )
            } catch (e: Exception) {
                _bookState.value = state.error("Failed to fetch books: ${e.message}")
            }
        }
    }

    fun updateSortOption(sortOption: SortOption) {
        val currentFilters = _advancedFilters.value
        applyAdvancedSearch(currentFilters.copy(sortBy = sortOption))
    }

    fun updateAuthorFilter(author: String?) {
        val cleanedAuthor = author?.takeIf { it.isNotBlank() }
        _filterState.value = _filterState.value.copy(selectedAuthor = cleanedAuthor)
        applyFilters()
    }

    fun updateGenreFilter(genre: String?) {
        val cleanedGenre = genre?.takeIf { it.isNotBlank() }
        _filterState.value = _filterState.value.copy(selectedGenre = cleanedGenre)
        applyFilters()
    }

    fun updateLanguageFilter(language: String?) {
        val cleanedLanguage = language?.takeIf { it.isNotBlank() }
        _filterState.value = _filterState.value.copy(selectedLanguage = cleanedLanguage)
        applyFilters()
    }

    fun updateYearRangeFilter(startYear: Int?, endYear: Int?) {
        _filterState.value = _filterState.value.copy(
            selectedYearStart = startYear,
            selectedYearEnd = endYear
        )
        applyFilters()
    }

    fun clearFilters() {
        _filterState.value = _filterState.value.copy(
            selectedAuthor = null,
            selectedGenre = null,
            selectedLanguage = null,
            selectedYearStart = null,
            selectedYearEnd = null
        )
        applyFilters()
    }

    private fun applyFilters() {
        val selectedAuthor = _filterState.value.selectedAuthor
        val selectedGenre = _filterState.value.selectedGenre
        val selectedLanguage = _filterState.value.selectedLanguage
        val yearStart = _filterState.value.selectedYearStart
        val yearEnd = _filterState.value.selectedYearEnd
        
        val filtered = cachedBooks.filter { book ->
            val authorMatches = selectedAuthor?.let { author ->
                book.author_name.orEmpty().any { it.equals(author, ignoreCase = true) }
            } ?: true
            
            val genreMatches = selectedGenre?.let { genre ->
                book.subject.orEmpty().any { it.equals(genre, ignoreCase = true) }
            } ?: true
            
            val languageMatches = selectedLanguage?.let { lang ->
                book.language.orEmpty().any { it.equals(lang, ignoreCase = true) }
            } ?: true
            
            val yearMatches = if (yearStart != null || yearEnd != null) {
                val publishYear = book.first_publish_year
                when {
                    publishYear == null -> false
                    yearStart != null && yearEnd != null -> publishYear in yearStart..yearEnd
                    yearStart != null -> publishYear >= yearStart
                    yearEnd != null -> publishYear <= yearEnd
                    else -> true
                }
            } else true
            
            authorMatches && genreMatches && languageMatches && yearMatches
        }
        _bookState.value = state.success(filtered)
    }

    fun selectBook(book: bookModel) {
        _selectedBook.value = book
        fetchSimilarBooks(book)
        val workId = normalizeKey(book.key) ?: return
        fetchWorkDetails(workId)
    }

    fun setSelectedBookById(bookId: String) {
        viewModelScope.launch {
            if (bookId.startsWith("/books/")) {
                val editionId = bookId.removePrefix("/books/")
                val edition = bookRepository.getEditionDetails(editionId)
                edition?.let { ed ->
                    val book = ed.toBookModel()
                    _selectedBook.value = book
                    _similarBooks.value = emptyList()
                    
                    val workKey = ed.works?.firstOrNull()?.key
                    if (workKey != null) {
                        fetchWorkDetails(workKey)
                    } else {
                        _workDetail.value = null
                        _editions.value = emptyList()
                        _ratings.value = null
                        _bookshelves.value = null
                    }
                }
            } else {
                val normalizedId = normalizeKey(bookId)
                val selected = cachedBooks.firstOrNull { existing ->
                    normalizeKey(existing.key) == normalizedId
                }
                
                val bookToSelect = selected ?: _selectedBook.value?.takeIf { 
                    normalizeKey(it.key) == normalizedId 
                }
                
                if (bookToSelect != null) {
                    _selectedBook.value = bookToSelect
                    fetchSimilarBooks(bookToSelect)
                    fetchWorkDetails(bookToSelect.key)
                } else {
                    // Not in cache. 
                    val workKey = if (bookId.startsWith("/works/")) bookId else "/works/$bookId"
                    
                    // Try to fetch via search API to get 'ia' field (needed for Read button)
                    val searchResult = try {
                        bookRepository.getbooks(query = "key:$workKey").firstOrNull()
                    } catch (e: Exception) { null }

                    if (searchResult != null) {
                        _selectedBook.value = searchResult
                        fetchWorkDetails(searchResult.key)
                        fetchSimilarBooks(searchResult)
                    } else {
                        // Fallback to WorkDetails if search fails
                        val workDetails = bookRepository.getWorkDetails(normalizedId ?: bookId)
                        
                        if (workDetails != null) {
                            val newBook = bookModel(
                                key = workKey,
                                title = workDetails.title ?: "Unknown Title",
                                cover_i = workDetails.covers?.firstOrNull(),
                                subject = workDetails.subjects
                            )
                            _selectedBook.value = newBook
                            _workDetail.value = workDetails
                            
                            // Fetch other details
                            launch { _editions.value = bookRepository.getEditions(normalizedId ?: bookId) }
                            launch { _ratings.value = bookRepository.getRatings(normalizedId ?: bookId) }
                            launch { _bookshelves.value = bookRepository.getBookshelves(normalizedId ?: bookId) }
                            
                            // Try to fetch similar books using title/subject
                            fetchSimilarBooks(newBook)
                        }
                    }
                }
            }
        }
    }

    private fun normalizeKey(key: String?): String? = key?.removePrefix("/works/")

    private fun fetchWorkDetails(key: String?) {
        val workId = normalizeKey(key) ?: return
        viewModelScope.launch {
            _workDetail.value = null
            _editions.value = emptyList()
            _ratings.value = null
            _bookshelves.value = null

            launch { _workDetail.value = bookRepository.getWorkDetails(workId) }
            launch { 
                val fetchedEditions = bookRepository.getEditions(workId)
                _editions.value = fetchedEditions
                
                // If the currently selected book has no ISBN (e.g. fallback creation),
                // try to populate it from the first edition found.
                val currentBook = _selectedBook.value
                if (currentBook != null && currentBook.isbn.isNullOrEmpty() && fetchedEditions.isNotEmpty()) {
                    // Find an edition with ISBNs
                    val editionWithIsbn = fetchedEditions.firstOrNull { 
                        !it.isbn13.isNullOrEmpty() || !it.isbn10.isNullOrEmpty() 
                    }
                    
                    if (editionWithIsbn != null) {
                        val isbns = (editionWithIsbn.isbn13.orEmpty() + editionWithIsbn.isbn10.orEmpty()).distinct()
                        if (isbns.isNotEmpty()) {
                            _selectedBook.value = currentBook.copy(isbn = isbns)
                        }
                    }
                }
            }
            launch { _ratings.value = bookRepository.getRatings(workId) }
            launch { _bookshelves.value = bookRepository.getBookshelves(workId) }
        }
    }

    private fun fetchSimilarBooks(book: bookModel) {
        viewModelScope.launch {
            try {
                val preferredAuthor = book.author_name?.firstOrNull()
                val preferredGenre = book.subject?.firstOrNull()
                val searchQuery = if (preferredAuthor == null && preferredGenre == null) book.title else null
                val similar = bookRepository.getbooks(
                    query = searchQuery,
                    author = preferredAuthor,
                    subject = preferredGenre
                )
                    .filter { normalizeKey(it.key) != normalizeKey(book.key) }
                    .take(12)
                _similarBooks.value = similar
            } catch (e: Exception) {
                _similarBooks.value = emptyList()
            }
        }
    }

    fun loadEditions(workId: String, offset: Int = 0) {
        viewModelScope.launch {
            _isLoadingMoreEditions.value = true
            try {
                val response = bookRepository.getEditionsPaged(workId, limit = 20, offset = offset)
                if (offset == 0) {
                    _editions.value = response
                    currentEditionsOffset = response.size
                } else {
                    _editions.value = _editions.value + response
                    currentEditionsOffset += response.size
                }
                canLoadMoreEditions = response.size >= 20
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoadingMoreEditions.value = false
            }
        }
    }

    fun loadMoreEditions(workId: String) {
        if (!canLoadMoreEditions || _isLoadingMoreEditions.value) return
        loadEditions(workId, currentEditionsOffset)
    }
}


data class FilterState(
    val availableAuthors: List<String> = emptyList(),
    val availableGenres: List<String> = emptyList(),
    val selectedAuthor: String? = null,
    val selectedGenre: String? = null,
    val selectedLanguage: String? = null,
    val selectedYearStart: Int? = null,
    val selectedYearEnd: Int? = null
)