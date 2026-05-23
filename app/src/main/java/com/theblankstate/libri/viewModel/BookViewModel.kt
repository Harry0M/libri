package com.theblankstate.libri.viewModel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theblankstate.libri.data.InternetArchiveRepository
import com.theblankstate.libri.data.RecommendationSeeds
import com.theblankstate.libri.data.UserPreferencesRepository
import com.theblankstate.libri.data_retrieval.repository
import com.theblankstate.libri.datamodel.AdvancedSearchFilters
import com.theblankstate.libri.datamodel.ArchiveDownloadOption
import com.theblankstate.libri.datamodel.SortOption
import com.theblankstate.libri.datamodel.WorkDetailModel
import com.theblankstate.libri.datamodel.bookModel
import com.theblankstate.libri.recommendation.RecommendationEngine
import com.theblankstate.libri.recommendation.RecommendationStore
import com.theblankstate.libri.states.state
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
 
class BookViewModel(application: Application) : AndroidViewModel(application) {

    private val searchPrefs: SharedPreferences =
        application.getSharedPreferences("search_history", android.content.Context.MODE_PRIVATE)

    private val bookRepository = repository
    private val internetArchiveRepository = InternetArchiveRepository()
    private val userPreferencesRepository = UserPreferencesRepository(application)
    private val recommendationStore = RecommendationStore(application)
    private val recommendationEngine = RecommendationEngine()

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

    private val _archiveDownloadOptions = MutableStateFlow<List<ArchiveDownloadOption>>(emptyList())
    val archiveDownloadOptions: StateFlow<List<ArchiveDownloadOption>> = _archiveDownloadOptions

    private val _isLoadingArchiveDownloadOptions = MutableStateFlow(false)
    val isLoadingArchiveDownloadOptions: StateFlow<Boolean> = _isLoadingArchiveDownloadOptions

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

    private val _searchRecommendations = MutableStateFlow(SearchRecommendationState())
    val searchRecommendations: StateFlow<SearchRecommendationState> = _searchRecommendations

    init {
        // Load persisted search history
        val saved = searchPrefs.getStringSet("history", emptySet()) ?: emptySet()
        val ordered = searchPrefs.getString("history_ordered", null)
        if (ordered != null) {
            _searchHistory.value = ordered.split("\u001F").filter { it.isNotBlank() }
        } else if (saved.isNotEmpty()) {
            _searchHistory.value = saved.toList()
        }
    }

    fun addSearchHistoryItem(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            recommendationStore.recordSearch(query)
        }
        val currentHistory = _searchHistory.value.toMutableList()
        currentHistory.remove(query)
        currentHistory.add(0, query)
        // Keep only last 20 items
        val trimmed = currentHistory.take(20)
        _searchHistory.value = trimmed
        // Persist to disk (ordered using unit separator)
        searchPrefs.edit()
            .putString("history_ordered", trimmed.joinToString("\u001F"))
            .apply()
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

    fun clearSearchResults() {
        currentQuery = null
        currentOffset = 0
        canLoadMore = true
        cachedBooks = emptyList()
        _bookState.value = state.success(emptyList())
    }

    fun loadSearchRecommendations(force: Boolean = false) {
        if (!force && (_searchRecommendations.value.openLibraryPicks.isNotEmpty() || _searchRecommendations.value.isLoading)) {
            return
        }

        viewModelScope.launch {
            _searchRecommendations.value = _searchRecommendations.value.copy(isLoading = true, error = null)
            try {
                val selectedGenres = userPreferencesRepository.getSelectedGenres().toList()
                val selectedAuthors = userPreferencesRepository.getSelectedAuthors().toList()
                val selectedLanguages = userPreferencesRepository.getSelectedLanguages().toList()
                val preferredLanguage = RecommendationSeeds.preferredOpenLibraryLanguage(selectedLanguages)
                val preferredGenre = selectedGenres.firstOrNull()
                val preferredAuthor = selectedAuthors.firstOrNull()

                val authorPicks = selectedAuthors.take(2).flatMap { author ->
                    bookRepository.getbooks(
                        author = author,
                        lang = preferredLanguage?.twoLetter,
                        limit = 8
                    )
                }
                val genrePicks = selectedGenres.take(3).flatMap { genre ->
                    bookRepository.getbooks(
                        subject = RecommendationSeeds.normalizeTopic(genre),
                        lang = preferredLanguage?.twoLetter,
                        sort = "random",
                        limit = 8
                    )
                }
                val trendingPicks = bookRepository.getbooks(
                    query = "trending_score_hourly_sum:[1 TO *]",
                    lang = preferredLanguage?.twoLetter,
                    sort = "trending",
                    limit = 12
                )
                val rawBooks = (authorPicks + genrePicks + trendingPicks)
                    .filter { it.title.isNotBlank() }
                    .distinctBy { it.key ?: it.title }
                recommendationStore.upsertBooks(rawBooks)
                val signals = recommendationStore.loadSignals(userPreferencesRepository)
                val books = rawBooks
                    .sortedByDescending { recommendationEngine.score(it, "Search recommendations", signals) }
                    .take(12)

                val topics = RecommendationSeeds.displayTopicsFromGenres(selectedGenres, limit = 10)

                _searchRecommendations.value = SearchRecommendationState(
                    openLibraryPicks = books,
                    topics = topics,
                    headline = when {
                        preferredAuthor != null -> "Because you follow $preferredAuthor"
                        preferredGenre != null -> "Because you like $preferredGenre"
                        else -> "Trending on Open Library"
                    },
                    isLoading = false
                )
            } catch (e: Exception) {
                _searchRecommendations.value = SearchRecommendationState(
                    error = "Could not load recommendations.",
                    isLoading = false
                )
            }
        }
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
                val preferredLanguage = RecommendationSeeds.preferredOpenLibraryLanguage(
                    userPreferencesRepository.getSelectedLanguages()
                )
                val books = bookRepository.getbooks(
                    query = query,
                    author = author,
                    subject = subject,
                    lang = preferredLanguage?.twoLetter,
                    limit = pageLimit,
                    offset = 0
                )
                cachedBooks = books
                recommendationStore.upsertBooks(books)
                query?.let { recommendationStore.recordSearch(it) }
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
                val preferredLanguage = RecommendationSeeds.preferredOpenLibraryLanguage(
                    userPreferencesRepository.getSelectedLanguages()
                )
                val books = bookRepository.getbooks(
                    query = query,
                    lang = preferredLanguage?.twoLetter,
                    limit = pageLimit,
                    offset = newOffset
                )
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
                    lang = preferredTwoLetterLanguage(filters.language),
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
        // Clear stale state from previous book IMMEDIATELY so the UI never flashes old data
        clearBookDetailState()
        updateSelectedBook(book)
        fetchSimilarBooks(book)
        val workId = normalizeKey(book.key) ?: return
        fetchWorkDetails(workId)
    }

    /**
     * Instantly clears all book-detail state so that navigating to a new book
     * never shows the previous book's data for a few frames.
     */
    private fun clearBookDetailState() {
        _selectedBook.value = null
        _workDetail.value = null
        _editions.value = emptyList()
        _ratings.value = null
        _bookshelves.value = null
        _similarBooks.value = emptyList()
        _archiveDownloadOptions.value = emptyList()
        _isLoadingArchiveDownloadOptions.value = false
        _resolvedArchiveIdentifier.value = null
        fallbackJob?.cancel()
        lastLoadedCacheKey = null
    }

    private fun updateSelectedBook(book: bookModel) {
        _selectedBook.value = book
        viewModelScope.launch {
            recommendationStore.recordBookOpen(book)
        }
    }

    fun setSelectedBookById(bookId: String) {
        // Clear stale state from previous book IMMEDIATELY
        clearBookDetailState()
        viewModelScope.launch {
            if (bookId.startsWith("/books/")) {
                val editionId = bookId.removePrefix("/books/")
                val edition = bookRepository.getEditionDetails(editionId)
                edition?.let { ed ->
                    val book = ed.toBookModel()
                    updateSelectedBook(book)
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
                    updateSelectedBook(bookToSelect)
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
                        updateSelectedBook(searchResult)
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
                            updateSelectedBook(newBook)
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
            // Note: we do NOT clear state here because clearBookDetailState() already
            // handled it at the start of selectBook/setSelectedBookById. Clearing here
            // would race with loadArchiveDownloadOptionsWithFallback and cause glitches.

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

    fun loadArchiveDownloadOptions(identifier: String?) {
        if (identifier.isNullOrBlank()) {
            _archiveDownloadOptions.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isLoadingArchiveDownloadOptions.value = true
            try {
                _archiveDownloadOptions.value = internetArchiveRepository.getDownloadOptions(identifier)
            } catch (e: Exception) {
                _archiveDownloadOptions.value = emptyList()
            } finally {
                _isLoadingArchiveDownloadOptions.value = false
            }
        }
    }

    /**
     * Intelligent cascading download options resolver.
     * Tries multiple strategies to find downloadable files for a book:
     *   1. Primary IA identifier (from book.ia)
     *   2. Edition ocaid fields (from fetched editions)
     *   3. ISBN-based Internet Archive search
     *   4. Title+author Internet Archive search (last resort)
     *
     * Stops at the first strategy that returns valid options.
     * The resolved identifier is emitted via [resolvedArchiveIdentifier].
     */
    private val _resolvedArchiveIdentifier = MutableStateFlow<String?>(null)
    val resolvedArchiveIdentifier: StateFlow<String?> = _resolvedArchiveIdentifier

    private var fallbackJob: kotlinx.coroutines.Job? = null
    private var lastLoadedCacheKey: String? = null

    fun loadArchiveDownloadOptionsWithFallback(
        primaryIdentifier: String?,
        editions: List<com.theblankstate.libri.datamodel.EditionModel>,
        isbn: List<String>?,
        title: String?,
        author: String?
    ) {
        // Build a cache key from the book's identity — include edition ocaids so
        // we re-fetch when editions arrive asynchronously
        val editionOcaids = editions.mapNotNull { it.ocaid }.sorted().joinToString(",")
        val cacheKey = "${primaryIdentifier}|${isbn?.firstOrNull()}|${title}|${editionOcaids}"

        // If we already have results for this exact book + editions combo, skip
        if (cacheKey == lastLoadedCacheKey && (_archiveDownloadOptions.value.isNotEmpty() || !_isLoadingArchiveDownloadOptions.value)) {
            return
        }

        // Cancel any in-flight fallback from a previous book
        fallbackJob?.cancel()
        lastLoadedCacheKey = cacheKey
        fallbackJob = viewModelScope.launch {
            _isLoadingArchiveDownloadOptions.value = true
            _resolvedArchiveIdentifier.value = null
            // Don't clear existing options here — clearBookDetailState already did it.
            // Clearing again would cause a flash of empty state if editions arrive
            // while we're already loading from the primary identifier.

            try {
                withContext(Dispatchers.IO) {
                    // Step 1: Try primary IA identifier
                    if (!primaryIdentifier.isNullOrBlank()) {
                        val options = internetArchiveRepository.getDownloadOptions(primaryIdentifier)
                        if (options.isNotEmpty()) {
                            _archiveDownloadOptions.value = options
                            _resolvedArchiveIdentifier.value = primaryIdentifier
                            return@withContext
                        }
                    }

                    // Step 2: Try ocaid from each loaded edition
                    for (edition in editions) {
                        val ocaid = edition.ocaid
                        if (!ocaid.isNullOrBlank() && ocaid != primaryIdentifier) {
                            val options = internetArchiveRepository.getDownloadOptions(ocaid)
                            if (options.isNotEmpty()) {
                                _archiveDownloadOptions.value = options
                                _resolvedArchiveIdentifier.value = ocaid
                                return@withContext
                            }
                        }
                    }

                    // Step 3: Try ISBN-based search on Internet Archive
                    val isbns = isbn?.take(3) ?: emptyList()
                    for (isbnValue in isbns) {
                        val iaId = internetArchiveRepository.searchByIsbn(isbnValue)
                        if (iaId != null) {
                            val options = internetArchiveRepository.getDownloadOptions(iaId)
                            if (options.isNotEmpty()) {
                                _archiveDownloadOptions.value = options
                                _resolvedArchiveIdentifier.value = iaId
                                return@withContext
                            }
                        }
                    }

                    // Step 4: Title + author search (last resort)
                    if (!title.isNullOrBlank()) {
                        val iaId = internetArchiveRepository.searchByTitleAuthor(title, author)
                        if (iaId != null) {
                            val options = internetArchiveRepository.getDownloadOptions(iaId)
                            if (options.isNotEmpty()) {
                                _archiveDownloadOptions.value = options
                                _resolvedArchiveIdentifier.value = iaId
                                return@withContext
                            }
                        }
                    }

                    // No downloadable source found from any strategy
                    _archiveDownloadOptions.value = emptyList()
                }
            } catch (e: Exception) {
                _archiveDownloadOptions.value = emptyList()
            } finally {
                _isLoadingArchiveDownloadOptions.value = false
            }
        }
    }

    private fun fetchSimilarBooks(book: bookModel) {
        viewModelScope.launch {
            try {
                val preferredAuthor = book.author_name?.firstOrNull()
                val preferredGenre = book.subject?.firstOrNull()
                val searchQuery = if (preferredAuthor == null && preferredGenre == null) book.title else null
                val rawSimilar = bookRepository.getbooks(
                    query = searchQuery,
                    author = preferredAuthor,
                    subject = preferredGenre,
                    lang = RecommendationSeeds.preferredOpenLibraryLanguage(
                        userPreferencesRepository.getSelectedLanguages()
                    )?.twoLetter
                )
                    .filter { normalizeKey(it.key) != normalizeKey(book.key) }
                recommendationStore.upsertBooks(rawSimilar)
                val signals = recommendationStore.loadSignals(userPreferencesRepository)
                val similar = rawSimilar
                    .sortedByDescending { recommendationEngine.score(it, "Similar books", signals) }
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

    private fun preferredTwoLetterLanguage(searchCode: String?): String? {
        return when (searchCode) {
            "eng" -> "en"
            "spa" -> "es"
            "fre" -> "fr"
            "ger" -> "de"
            "ita" -> "it"
            "por" -> "pt"
            "rus" -> "ru"
            "jpn" -> "ja"
            "chi" -> "zh"
            "ara" -> "ar"
            "hin" -> "hi"
            else -> RecommendationSeeds.preferredOpenLibraryLanguage(
                userPreferencesRepository.getSelectedLanguages()
            )?.twoLetter
        }
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

data class SearchRecommendationState(
    val openLibraryPicks: List<bookModel> = emptyList(),
    val topics: List<String> = emptyList(),
    val headline: String = "Recommended for you",
    val isLoading: Boolean = false,
    val error: String? = null
)
