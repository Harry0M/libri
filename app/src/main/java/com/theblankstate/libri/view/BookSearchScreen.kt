package com.theblankstate.libri.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.theblankstate.libri.datamodel.SortOption
import com.theblankstate.libri.datamodel.bookModel
import com.theblankstate.libri.states.state
import com.theblankstate.libri.viewModel.BookViewModel
import com.theblankstate.libri.viewModel.GutenbergViewModel
import com.theblankstate.libri.datamodel.GutendexBook
import com.theblankstate.libri.datamodel.SearchSource

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
    val bookState by viewModel.bookState.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    // Track last searched query to avoid re-fetching
    var lastSearchedQuery by remember { mutableStateOf("") }
    val searchHistory by viewModel.searchHistory.collectAsState()
    var sortExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val gutenbergViewModel: GutenbergViewModel = viewModel<GutenbergViewModel>(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as android.app.Application
        )
    )

    val gutSearchResults by gutenbergViewModel.searchResults.collectAsState()
    val gutIsSearching by gutenbergViewModel.isSearching.collectAsState()
    val gutSearchError by gutenbergViewModel.searchError.collectAsState()
    val gutDownloadingBookIds by gutenbergViewModel.downloadingBookIds.collectAsState()
    val gutDownloadProgress by gutenbergViewModel.downloadProgress.collectAsState()
    val gutHasMoreResults by gutenbergViewModel.hasMoreSearchResults.collectAsState()

    // No auto-search - only search when user explicitly triggers it

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                onSearch = {
                    active = false
                    if (query.isNotEmpty() && query != lastSearchedQuery) {
                        lastSearchedQuery = query
                        // Search BOTH sources simultaneously
                        viewModel.fetchBooksByQuery(query)
                        viewModel.addSearchHistoryItem(query)
                        gutenbergViewModel.searchBooks(query)
                    }
                },
                active = active,
                onActiveChange = { active = it },
                placeholder = { Text("Search by title, author, subject...") },
                leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = "Search Icon") },
                trailingIcon = {
                    if (active) {
                        Icon(
                            modifier = Modifier.clickable {
                                if (query.isNotEmpty()) {
                                    query = ""
                                } else {
                                    active = false
                                }
                            },
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close Icon"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (active) 0.dp else 16.dp)
            ) {
                searchHistory.forEach { historyItem ->
                    ListItem(
                        headlineContent = { Text(historyItem) },
                        leadingContent = { Icon(imageVector = Icons.Filled.Refresh, contentDescription = null) },
                        modifier = Modifier
                            .clickable {
                                query = historyItem
                                active = false
                                if (historyItem != lastSearchedQuery) {
                                    lastSearchedQuery = historyItem
                                    // Search BOTH sources
                                    viewModel.fetchBooksByQuery(historyItem)
                                    viewModel.addSearchHistoryItem(historyItem)
                                    gutenbergViewModel.searchBooks(historyItem)
                                }
                            }
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // Action Row: Advanced Search and Sort (for Open Library filters)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onAdvancedSearchClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Advanced Search",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Advanced")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { sortExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Sort",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sort")
                    }
                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false }
                    ) {
                        SortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    viewModel.updateSortOption(option)
                                    sortExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Source filter chips - exclusive selection (only one active at a time)
            var selectedSource by remember { mutableStateOf(SearchSource.OPEN_LIBRARY) }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSource == SearchSource.OPEN_LIBRARY,
                    onClick = { selectedSource = SearchSource.OPEN_LIBRARY },
                    label = { Text("Open Library") },
                    leadingIcon = if (selectedSource == SearchSource.OPEN_LIBRARY) {
                        { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp)) }
                    } else null
                )
                FilterChip(
                    selected = selectedSource == SearchSource.GUTENBERG,
                    onClick = { selectedSource = SearchSource.GUTENBERG },
                    label = { Text("Gutenberg") },
                    leadingIcon = if (selectedSource == SearchSource.GUTENBERG) {
                        { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp)) }
                    } else null
                )
            }

            // Unified Results - show both OL and Gutenberg in one scrollable list
            val olListState = rememberLazyListState()
            val olBooks = if (bookState is state.success) (bookState as state.success).data else emptyList()
            val olIsLoading = bookState is state.loading
            val olError = if (bookState is state.error) (bookState as state.error).message else null
            
            LazyColumn(
                state = olListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Open Library Section
                if (selectedSource == SearchSource.OPEN_LIBRARY && (olIsLoading || olBooks.isNotEmpty() || olError != null)) {
                    item(key = "ol_header") {
                        SectionHeader(
                            title = "Open Library",
                            count = olBooks.size,
                            isLoading = olIsLoading
                        )
                    }
                }
                
                if (selectedSource == SearchSource.OPEN_LIBRARY && olIsLoading && olBooks.isEmpty()) {
                    item(key = "ol_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (selectedSource == SearchSource.OPEN_LIBRARY && olError != null) {
                    item(key = "ol_error") {
                        Text(
                            text = olError,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else if (selectedSource == SearchSource.OPEN_LIBRARY && olBooks.isNotEmpty()) {
                    items(olBooks, key = { "ol_${it.key ?: it.title.hashCode()}" }) { book ->
                        BookItem(
                            book = book,
                            onClick = { onBookClick(book.key ?: "") },
                            onReadClick = {
                                book.ia?.firstOrNull()?.let { iaId ->
                                    onReadClick(
                                        iaId,
                                        book.title,
                                        book.author_name?.firstOrNull(),
                                        "https://covers.openlibrary.org/b/id/${book.cover_i}-M.jpg"
                                    )
                                }
                            }
                        )
                    }
                    
                    // Load more button for Open Library
                    if (olBooks.size >= 10) {
                        item(key = "ol_load_more") {
                            OutlinedButton(
                                onClick = { viewModel.loadMoreBooks() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                enabled = !olIsLoading
                            ) {
                                if (olIsLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Load more from Open Library")
                            }
                        }
                    }
                }
                
                // Gutenberg Section
                if (selectedSource == SearchSource.GUTENBERG && (gutIsSearching || gutSearchResults.isNotEmpty() || gutSearchError != null)) {
                    item(key = "gut_header") {
                        Spacer(modifier = Modifier.height(16.dp))
                        SectionHeader(
                            title = "Project Gutenberg",
                            subtitle = "Free eBooks",
                            count = gutSearchResults.size,
                            isLoading = gutIsSearching
                        )
                    }
                }
                
                if (selectedSource == SearchSource.GUTENBERG && gutIsSearching && gutSearchResults.isEmpty()) {
                    item(key = "gut_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (selectedSource == SearchSource.GUTENBERG && gutSearchError != null) {
                    item(key = "gut_error") {
                        Text(
                            text = gutSearchError ?: "Search failed",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else if (selectedSource == SearchSource.GUTENBERG && gutSearchResults.isNotEmpty()) {
                    items(gutSearchResults, key = { "gut_${it.id}" }) { book ->
                        GutenbergBookListItem(
                            book = book,
                            onClick = { onGutenbergClick(book) },
                            onDownloadClick = { gutenbergViewModel.downloadBook(book) },
                            onReadClick = { onReadGutenbergClick(book) },
                            isDownloading = gutDownloadingBookIds.contains(book.id),
                            downloadProgress = gutDownloadProgress[book.id] ?: 0f,
                            isDownloaded = gutenbergViewModel.isBookDownloaded(book.id)
                        )
                    }
                    
                    // Load more button for Gutenberg
                    if (gutHasMoreResults) {
                        item(key = "gut_load_more") {
                            OutlinedButton(
                                onClick = { gutenbergViewModel.loadMoreSearchResults() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                enabled = !gutIsSearching
                            ) {
                                if (gutIsSearching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Load more from Gutenberg")
                            }
                        }
                    }
                }
                
                // Empty state when no search performed yet
                if (lastSearchedQuery.isEmpty() && olBooks.isEmpty() && gutSearchResults.isEmpty() && !olIsLoading && !gutIsSearching) {
                    item(key = "empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Search for books",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Results from Open Library & Gutenberg",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                
                // Bottom padding
                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    count: Int,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (count > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
        }
    }
    HorizontalDivider()
}

@Composable
fun GutenbergBookListItem(
    book: GutendexBook,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onReadClick: () -> Unit,
    isDownloading: Boolean,
    downloadProgress: Float,
    isDownloaded: Boolean
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Book Cover
            Card(
                modifier = Modifier
                    .width(80.dp)
                    .height(120.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(book.formats?.get("image/jpeg"))
                        .crossfade(true)
                        .placeholder(android.graphics.drawable.ColorDrawable(0xFF424242.toInt()))
                        .error(android.graphics.drawable.ColorDrawable(0xFF424242.toInt()))
                        .build(),
                    contentDescription = "Book cover",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }

            // Book Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = book.authors?.joinToString(", ") { it.name } ?: "Unknown Author",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    // Free badge
                    AssistChip(
                        onClick = {},
                        label = { Text("Free eBook", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                        )
                    } else {
                        FilledTonalButton(
                            onClick = onReadClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Read", style = MaterialTheme.typography.labelMedium)
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
fun BookItem(
    book: bookModel, 
    onClick: () -> Unit,
    onReadClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Book Cover
            Card(
                modifier = Modifier
                    .width(80.dp)
                    .height(120.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data("https://covers.openlibrary.org/b/id/${book.cover_i}-M.jpg")
                        .crossfade(true)
                        .placeholder(android.graphics.drawable.ColorDrawable(0xFF424242.toInt()))
                        .error(android.graphics.drawable.ColorDrawable(0xFF424242.toInt()))
                        .build(),
                    contentDescription = "Book cover",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }

            // Book Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = book.author_name?.joinToString(", ") ?: "Unknown Author",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        book.first_publish_year?.let {
                            AssistChip(
                                onClick = {},
                                label = { Text(it.toString(), style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                        
                        if (book.has_fulltext == true) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Available", style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }

                // Read Button
                if (book.has_fulltext == true && !book.ia.isNullOrEmpty()) {
                    FilledTonalButton(
                        onClick = onReadClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Read Online", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
