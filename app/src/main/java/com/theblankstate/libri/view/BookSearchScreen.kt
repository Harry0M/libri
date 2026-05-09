package com.theblankstate.libri.view

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theblankstate.libri.datamodel.GutendexBook
import com.theblankstate.libri.datamodel.SearchSource
import com.theblankstate.libri.datamodel.bookModel
import com.theblankstate.libri.states.state
import com.theblankstate.libri.view.components.ExpressiveLoadingIndicator
import com.theblankstate.libri.viewModel.BookViewModel
import com.theblankstate.libri.viewModel.GutenbergViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookSearchScreen(
    viewModel: BookViewModel = viewModel(),
    onBookClick: (String) -> Unit,
    onGutenbergClick: (GutendexBook) -> Unit = {},
    onReadGutenbergClick: (GutendexBook) -> Unit = {},
    onAdvancedSearchClick: () -> Unit = {},
    onReadClick: (String, String?, String?, String?) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val gutenbergViewModel: GutenbergViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as Application
        )
    )

    val bookState by viewModel.bookState.collectAsState()
    val recommendations by viewModel.searchRecommendations.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val gutSearchResults by gutenbergViewModel.searchResults.collectAsState()
    val gutIsSearching by gutenbergViewModel.isSearching.collectAsState()
    val gutSearchError by gutenbergViewModel.searchError.collectAsState()
    val gutRecommendations by gutenbergViewModel.recommendationState.collectAsState()
    val gutPopularBooks by gutenbergViewModel.popularBooks.collectAsState()
    val gutIsLoadingPopular by gutenbergViewModel.isLoadingPopular.collectAsState()
    val gutHasMoreResults by gutenbergViewModel.hasMoreSearchResults.collectAsState()
    val gutDownloadingBookIds by gutenbergViewModel.downloadingBookIds.collectAsState()
    val gutDownloadProgress by gutenbergViewModel.downloadProgress.collectAsState()

    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    var submittedQuery by remember { mutableStateOf("") }
    var autoSearchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf(SearchSource.ALL) }

    fun collapseSearch() {
        active = false
        focusManager.clearFocus()
    }

    fun runSearch(rawQuery: String, saveHistory: Boolean) {
        val cleaned = rawQuery.trim()
        if (cleaned.length < 2) return
        submittedQuery = cleaned
        if (selectedSource == SearchSource.ALL || selectedSource == SearchSource.OPEN_LIBRARY) {
            viewModel.fetchBooksByQuery(cleaned)
        } else {
            viewModel.clearSearchResults()
        }
        if (selectedSource == SearchSource.ALL || selectedSource == SearchSource.GUTENBERG) {
            gutenbergViewModel.searchBooks(cleaned)
        } else {
            gutenbergViewModel.clearSearch()
        }
        if (saveHistory) {
            viewModel.addSearchHistoryItem(cleaned)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadSearchRecommendations()
        gutenbergViewModel.loadRecommendedBooks()
        gutenbergViewModel.loadPopularBooks()
    }

    LaunchedEffect(query) {
        val cleaned = query.trim()
        if (cleaned.isBlank()) {
            submittedQuery = ""
            autoSearchQuery = ""
            viewModel.clearSearchResults()
            gutenbergViewModel.clearSearch()
            return@LaunchedEffect
        }

        if (cleaned.length >= 2 && cleaned != autoSearchQuery) {
            delay(420)
            if (query.trim() == cleaned) {
                autoSearchQuery = cleaned
                runSearch(cleaned, saveHistory = false)
            }
        }
    }

    LaunchedEffect(selectedSource) {
        if (submittedQuery.isNotBlank()) {
            runSearch(submittedQuery, saveHistory = false)
        }
    }

    val openLibraryBooks = if (bookState is state.success) {
        (bookState as state.success).data
    } else {
        emptyList()
    }
    val openLibraryLoading = submittedQuery.isNotBlank() && bookState is state.loading
    val openLibraryError = if (submittedQuery.isNotBlank() && bookState is state.error) {
        (bookState as state.error).message
    } else {
        null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            DockedSearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = {
                            collapseSearch()
                            runSearch(it, saveHistory = true)
                        },
                        expanded = active,
                        onExpandedChange = { active = it },
                        placeholder = { Text("Search books, authors, topics") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        },
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.clickable {
                                        query = ""
                                        collapseSearch()
                                    }
                                )
                            }
                        }
                    )
                },
                expanded = active,
                onExpandedChange = { active = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 0.dp)
            ) {
                SearchSuggestions(
                    history = searchHistory,
                    topics = recommendations.topics,
                    onSelect = { selected ->
                        query = selected
                        collapseSearch()
                        runSearch(selected, saveHistory = true)
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceChip("All", selectedSource == SearchSource.ALL) { selectedSource = SearchSource.ALL }
                SourceChip("Open Library", selectedSource == SearchSource.OPEN_LIBRARY) { selectedSource = SearchSource.OPEN_LIBRARY }
                SourceChip("Gutenberg", selectedSource == SearchSource.GUTENBERG) { selectedSource = SearchSource.GUTENBERG }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = onAdvancedSearchClick,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Filters")
                }
            }

            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (submittedQuery.isBlank()) {
                    item("default_header") {
                        SearchDefaultHeader()
                    }

                    if (searchHistory.isNotEmpty() || recommendations.topics.isNotEmpty()) {
                        item("idea_chips") {
                            SearchSuggestions(
                                history = searchHistory.take(5),
                                topics = recommendations.topics,
                                onSelect = { selected ->
                                    query = selected
                                    collapseSearch()
                                    runSearch(selected, saveHistory = true)
                                }
                            )
                        }
                    }

                    item("ol_recommendations") {
                        RecommendationSection(
                            title = recommendations.headline,
                            isLoading = recommendations.isLoading,
                            error = recommendations.error,
                            books = recommendations.openLibraryPicks,
                            onBookClick = { book ->
                                book.key?.let(onBookClick)
                            },
                            onTopicClick = { topic ->
                                query = topic
                                runSearch(topic, saveHistory = true)
                            }
                        )
                    }

                    item("gutenberg_popular") {
                        GutenbergCarouselSection(
                            title = gutRecommendations.headline,
                            subtitle = gutRecommendations.subtitle,
                            isLoading = gutRecommendations.isLoading || (gutRecommendations.books.isEmpty() && gutIsLoadingPopular),
                            error = gutRecommendations.error,
                            books = gutRecommendations.books.ifEmpty { gutPopularBooks },
                            onBookClick = onGutenbergClick,
                            onReadClick = onReadGutenbergClick
                        )
                    }
                } else {
                    item("result_summary") {
                        SearchResultSummary(
                            query = submittedQuery,
                            openLibraryCount = openLibraryBooks.size,
                            gutenbergCount = gutSearchResults.size,
                            isLoading = openLibraryLoading || gutIsSearching
                        )
                    }

                    if (selectedSource == SearchSource.ALL || selectedSource == SearchSource.OPEN_LIBRARY) {
                        item("open_library_header") {
                            ResultSectionHeader(
                                title = "Open Library",
                                subtitle = "Catalog matches, online reads, editions",
                                count = openLibraryBooks.size,
                                isLoading = openLibraryLoading
                            )
                        }

                        when {
                            openLibraryLoading && openLibraryBooks.isEmpty() -> {
                                item("open_library_loading") {
                                    LoadingRow("Searching Open Library")
                                }
                            }
                            openLibraryError != null -> {
                                item("open_library_error") {
                                    ErrorRow(openLibraryError)
                                }
                            }
                            openLibraryBooks.isEmpty() -> {
                                item("open_library_empty") {
                                    EmptySourceRow("No Open Library matches yet.")
                                }
                            }
                            else -> {
                                items(openLibraryBooks, key = { "ol_${it.key ?: it.title}" }) { book ->
                                    OpenLibraryResultCard(
                                        book = book,
                                        onClick = { book.key?.let(onBookClick) },
                                        onReadClick = {
                                            val iaId = book.ia?.firstOrNull()
                                            if (iaId != null) {
                                                onReadClick(
                                                    iaId,
                                                    book.title,
                                                    book.author_name?.firstOrNull(),
                                                    book.coverUrl
                                                )
                                            }
                                        }
                                    )
                                }

                                if (openLibraryBooks.size >= 20) {
                                    item("open_library_more") {
                                        LoadMoreButton(
                                            text = "More from Open Library",
                                            enabled = !openLibraryLoading,
                                            onClick = viewModel::loadMoreBooks
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (selectedSource == SearchSource.ALL || selectedSource == SearchSource.GUTENBERG) {
                        item("gutenberg_header") {
                            ResultSectionHeader(
                                title = "Project Gutenberg",
                                subtitle = "Free public-domain ebooks",
                                count = gutSearchResults.size,
                                isLoading = gutIsSearching
                            )
                        }

                        when {
                            gutIsSearching && gutSearchResults.isEmpty() -> {
                                item("gutenberg_loading") {
                                    LoadingRow("Searching Gutenberg")
                                }
                            }
                            gutSearchError != null -> {
                                item("gutenberg_error") {
                                    ErrorRow(gutSearchError ?: "Gutenberg search failed.")
                                }
                            }
                            gutSearchResults.isEmpty() -> {
                                item("gutenberg_empty") {
                                    EmptySourceRow("No Gutenberg matches yet.")
                                }
                            }
                            else -> {
                                items(gutSearchResults, key = { "gut_${it.id}" }) { book ->
                                    GutenbergResultCard(
                                        book = book,
                                        onClick = { onGutenbergClick(book) },
                                        onReadClick = { onReadGutenbergClick(book) },
                                        onDownloadClick = { gutenbergViewModel.downloadBook(book) },
                                        isDownloading = gutDownloadingBookIds.contains(book.id),
                                        downloadProgress = gutDownloadProgress[book.id] ?: 0f,
                                        isDownloaded = gutenbergViewModel.isBookDownloaded(book.id)
                                    )
                                }

                                if (gutHasMoreResults) {
                                    item("gutenberg_more") {
                                        LoadMoreButton(
                                            text = "More from Gutenberg",
                                            enabled = !gutIsSearching,
                                            onClick = gutenbergViewModel::loadMoreSearchResults
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchSuggestions(
    history: List<String>,
    topics: List<String>,
    onSelect: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        history.forEach { item ->
            AssistChip(
                onClick = { onSelect(item) },
                label = { Text(item, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        topics.forEach { topic ->
            AssistChip(
                onClick = { onSelect(topic) },
                label = { Text(topic, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
    }
}

@Composable
private fun SearchDefaultHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column {
                Text(
                    text = "Search across both catalogs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Open Library metadata plus Gutenberg free ebooks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecommendationSection(
    title: String,
    isLoading: Boolean,
    error: String?,
    books: List<bookModel>,
    onBookClick: (bookModel) -> Unit,
    onTopicClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ResultSectionHeader(
            title = title,
            subtitle = "A lightweight local recommendation pass from your preferences",
            count = books.size,
            isLoading = isLoading
        )
        when {
            isLoading -> LoadingRow("Finding recommendations")
            error != null -> ErrorRow(error)
            books.isEmpty() -> EmptySourceRow("No recommendations yet. Try a topic chip above.")
            else -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(books.take(10), key = { it.key ?: it.title }) { book ->
                        CompactBookTile(
                            title = book.title,
                            author = book.author_name?.firstOrNull() ?: "Unknown Author",
                            coverUrl = book.coverUrl,
                            source = "Open Library",
                            onClick = { onBookClick(book) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GutenbergCarouselSection(
    title: String,
    subtitle: String,
    isLoading: Boolean,
    error: String?,
    books: List<GutendexBook>,
    onBookClick: (GutendexBook) -> Unit,
    onReadClick: (GutendexBook) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ResultSectionHeader(
            title = title,
            subtitle = subtitle,
            count = books.size,
            isLoading = isLoading
        )
        when {
            isLoading -> LoadingRow("Loading free reads")
            error != null -> ErrorRow(error)
            books.isEmpty() -> EmptySourceRow("Popular Gutenberg books could not be loaded.")
            else -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(books.take(10), key = { it.id }) { book ->
                        CompactBookTile(
                            title = book.title,
                            author = book.authorNames,
                            coverUrl = book.coverUrl,
                            source = "Gutenberg",
                            onClick = { onBookClick(book) },
                            actionLabel = "Read",
                            onAction = { onReadClick(book) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactBookTile(
    title: String,
    author: String,
    coverUrl: String?,
    source: String,
    onClick: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    ElevatedCard(
        modifier = Modifier
            .width(156.dp)
            .height(276.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CoverImage(
                coverUrl = coverUrl,
                title = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
                    .padding(8.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AssistChip(
                    onClick = {},
                    label = { Text(source) },
                    modifier = Modifier.height(28.dp)
                )
                if (actionLabel != null && onAction != null) {
                    FilledTonalButton(
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultSummary(
    query: String,
    openLibraryCount: Int,
    gutenbergCount: Int,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Results for \"$query\"",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$openLibraryCount Open Library · $gutenbergCount Gutenberg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun ResultSectionHeader(
    title: String,
    subtitle: String,
    count: Int,
    isLoading: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (count > 0) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                if (isLoading) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun OpenLibraryResultCard(
    book: bookModel,
    onClick: () -> Unit,
    onReadClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CoverImage(
                coverUrl = book.coverUrl,
                title = book.title,
                modifier = Modifier
                    .width(78.dp)
                    .height(116.dp)
                    .clip(MaterialTheme.shapes.medium)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(116.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = book.author_name?.joinToString(", ") ?: "Unknown Author",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ResultBadges(
                        source = "Open Library",
                        meta = book.first_publish_year?.toString(),
                        available = book.has_fulltext == true
                    )
                }
                if (book.has_fulltext == true && !book.ia.isNullOrEmpty()) {
                    FilledTonalButton(
                        onClick = onReadClick,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Read online", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun GutenbergResultCard(
    book: GutendexBook,
    onClick: () -> Unit,
    onReadClick: () -> Unit,
    onDownloadClick: () -> Unit,
    isDownloading: Boolean,
    downloadProgress: Float,
    isDownloaded: Boolean
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CoverImage(
                coverUrl = book.coverUrl,
                title = book.title,
                modifier = Modifier
                    .width(78.dp)
                    .height(116.dp)
                    .clip(MaterialTheme.shapes.medium)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(116.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = book.authorNames,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ResultBadges(
                        source = "Gutenberg",
                        meta = book.downloadCount?.let { "$it downloads" },
                        available = true
                    )
                }

                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = onReadClick,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Read", style = MaterialTheme.typography.labelLarge)
                        }
                        if (!isDownloaded) {
                            OutlinedButton(
                                onClick = onDownloadClick,
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("Download", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultBadges(
    source: String,
    meta: String?,
    available: Boolean
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AssistChip(
            onClick = {},
            label = { Text(source, style = MaterialTheme.typography.labelSmall) },
            leadingIcon = {
                Icon(
                    imageVector = if (source == "Gutenberg") Icons.Default.Public else Icons.Default.LocalLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            modifier = Modifier.height(28.dp)
        )
        if (meta != null) {
            AssistChip(
                onClick = {},
                label = { Text(meta, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp)
            )
        }
        if (available) {
            AssistChip(
                onClick = {},
                label = { Text("Readable", style = MaterialTheme.typography.labelSmall) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                modifier = Modifier.height(28.dp)
            )
        }
    }
}

@Composable
private fun CoverImage(
    coverUrl: String?,
    title: String,
    modifier: Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
                modifier = Modifier.size(32.dp)
            )
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun LoadingRow(label: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        ExpressiveLoadingIndicator(
            label = label,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
private fun ErrorRow(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun EmptySourceRow(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun LoadMoreButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!enabled) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text)
    }
}
