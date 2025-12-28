package com.theblankstate.libri.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.snapshotFlow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theblankstate.libri.datamodel.GutendexBook
import com.theblankstate.libri.viewModel.GutenbergViewModel
import com.theblankstate.libri.viewModel.GutenbergTopics

/**
 * Screen for browsing and searching Project Gutenberg's free ebook collection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GutenbergBrowseScreen(
    onBackClick: () -> Unit,
    onBookClick: (GutendexBook) -> Unit,
    onReadBook: (GutendexBook) -> Unit,
    viewModel: GutenbergViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val popularBooks by viewModel.popularBooks.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingPopular by viewModel.isLoadingPopular.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val downloadingBookIds by viewModel.downloadingBookIds.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchMode by remember { mutableStateOf(false) }
    var selectedTopic by remember { mutableStateOf<String?>(null) }
    var topicBooks by remember { mutableStateOf<List<GutendexBook>>(emptyList()) }
    var isLoadingTopic by remember { mutableStateOf(false) }
    val isLoadingMoreTopic by viewModel.isLoadingMoreTopic.collectAsState()
    val isLoadingMorePopular by viewModel.isLoadingMorePopular.collectAsState()
    
    // Load popular books on first launch
    LaunchedEffect(Unit) {
        viewModel.loadPopularBooks()
    }
    
    // Debounced search - triggers 500ms after user stops typing
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && isSearchMode) {
            kotlinx.coroutines.delay(500) // Debounce
            viewModel.searchBooks(searchQuery)
        }
    }
    
    // Load topic books when selected
    LaunchedEffect(selectedTopic) {
        selectedTopic?.let { topic ->
            isLoadingTopic = true
            topicBooks = viewModel.getBooksByTopic(topic)
            isLoadingTopic = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSearchMode) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search free ebooks...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Search
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        viewModel.searchBooks(searchQuery)
                                    }
                                }
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { 
                                        searchQuery = ""
                                        viewModel.clearSearch()
                                    }) {
                                        Icon(Icons.Default.Clear, "Clear")
                                    }
                                }
                            }
                        )
                    } else {
                        Text("Free Ebooks") 
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchMode) {
                            isSearchMode = false
                            searchQuery = ""
                            viewModel.clearSearch()
                        } else if (selectedTopic != null) {
                            selectedTopic = null
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!isSearchMode) {
                        IconButton(onClick = { isSearchMode = true }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                    } else {
                        IconButton(onClick = { 
                            if (searchQuery.isNotBlank()) {
                                viewModel.searchBooks(searchQuery)
                            }
                        }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isSearchMode && (searchQuery.isNotEmpty() || searchResults.isNotEmpty()) -> {
                    // Search Results
                    if (isSearching) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (searchError != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    searchError ?: "Search failed",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Button(
                                    onClick = { viewModel.retrySearch() }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Retry")
                                }
                            }
                        }
                    } else if (searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No results found")
                        }
                    } else {
                        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                        
                        // Detect when user reaches end for infinite scroll
                        LaunchedEffect(gridState) {
                            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                .collect { lastIndex ->
                                    if (lastIndex != null && lastIndex >= searchResults.size - 4) {
                                        viewModel.loadMoreSearchResults()
                                    }
                                }
                        }
                        
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(160.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(searchResults) { book ->
                                GutenbergBookCard(
                                    book = book,
                                    onClick = { onBookClick(book) },
                                    onDownloadClick = { viewModel.downloadBook(book) },
                                    onReadClick = { onReadBook(book) },
                                    isDownloading = downloadingBookIds.contains(book.id),
                                    downloadProgress = downloadProgress[book.id] ?: 0f,
                                    isDownloaded = viewModel.isBookDownloaded(book.id)
                                )
                            }
                            
                            // Loading indicator at bottom
                            if (isSearching) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                
                selectedTopic != null -> {
                    // Topic Books
                    Column {
                        Text(
                            text = GutenbergTopics.topics.find { it.second == selectedTopic }?.first ?: selectedTopic!!,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp)
                        )
                        
                        if (isLoadingTopic) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                            
                            // Detect when user reaches end for infinite scroll
                            LaunchedEffect(gridState) {
                                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                    .collect { lastIndex ->
                                        if (lastIndex != null && lastIndex >= topicBooks.size - 4 && !isLoadingMoreTopic) {
                                            val moreBooks = viewModel.loadMoreTopicBooks()
                                            val newList = topicBooks + moreBooks
                                            // Keep only last 200 items to prevent memory issues
                                            topicBooks = if (newList.size > 200) {
                                                newList.takeLast(200)
                                            } else {
                                                newList
                                            }
                                        }
                                    }
                            }
                            
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(160.dp),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(topicBooks) { book ->
                                    GutenbergBookCard(
                                        book = book,
                                        onClick = { onBookClick(book) },
                                        onDownloadClick = { viewModel.downloadBook(book) },
                                        onReadClick = { onReadBook(book) },
                                        isDownloading = downloadingBookIds.contains(book.id),
                                        downloadProgress = downloadProgress[book.id] ?: 0f,
                                        isDownloaded = viewModel.isBookDownloaded(book.id)
                                    )
                                }
                                
                                // Loading indicator at bottom
                                if (isLoadingMoreTopic) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                else -> {
                    // Browse Mode - Show topics and popular books
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Topics Section
                        item {
                            Text(
                                text = "Browse by Genre",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(GutenbergTopics.topics) { (name, topic) ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { selectedTopic = topic },
                                        label = { Text(name) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Category,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Popular Books Section
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Most Popular",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Text(
                                text = "Top downloaded free ebooks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        
                        if (isLoadingPopular) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else {
                            items(popularBooks.chunked(2)) { rowBooks ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowBooks.forEach { book ->
                                        GutenbergBookCard(
                                            book = book,
                                            onClick = { onBookClick(book) },
                                            onDownloadClick = { viewModel.downloadBook(book) },
                                            onReadClick = { onReadBook(book) },
                                            isDownloading = downloadingBookIds.contains(book.id),
                                            downloadProgress = downloadProgress[book.id] ?: 0f,
                                            isDownloaded = viewModel.isBookDownloaded(book.id),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    // Fill empty space if odd number
                                    if (rowBooks.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            
                            // Loading indicator or Load More button
                            if (isLoadingMorePopular) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            } else {
                                item {
                                    FilledTonalButton(
                                        onClick = { viewModel.loadMorePopularBooks() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Load More Books")
                                    }
                                }
                            }
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GutenbergBookCard(
    book: GutendexBook,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onReadClick: () -> Unit,
    isDownloading: Boolean,
    downloadProgress: Float,
    isDownloaded: Boolean,
    modifier: Modifier = Modifier
) {
    // Simple card matching OpenLibrary style
    Card(
        modifier = modifier
            .width(160.dp)
            .height(280.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Cover Image with download overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(book.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Downloaded badge
                if (isDownloaded) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 8.dp),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            modifier = Modifier
                                .size(24.dp)
                                .padding(4.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                
                // Download progress overlay
                if (isDownloading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            
            // Book Info
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = book.authorNames,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDownloadCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M downloads"
        count >= 1_000 -> "${count / 1_000}K downloads"
        else -> "$count downloads"
    }
}
