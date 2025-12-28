package com.theblankstate.libri.view

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theblankstate.libri.data.LibraryRepository
import com.theblankstate.libri.data.UserPreferencesRepository
import com.theblankstate.libri.datamodel.GutendexBook
import com.theblankstate.libri.datamodel.LibraryBook
import com.theblankstate.libri.datamodel.ReadingStatus
import com.theblankstate.libri.viewModel.GutenbergViewModel
import kotlinx.coroutines.launch

/**
 * Detail screen for a Project Gutenberg book
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GutenbergBookDetailScreen(
    bookId: Int,
    onBackClick: () -> Unit,
    onReadClick: (GutendexBook, String?) -> Unit, // book and optional fileUri
    viewModel: GutenbergViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val libraryRepository = remember { LibraryRepository() }
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }
    val uid = userPreferencesRepository.getGoogleUser().third // Use third for UID
    
    val selectedBook by viewModel.selectedBook.collectAsState()
    val downloadingBookIds by viewModel.downloadingBookIds.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isInLibrary by remember { mutableStateOf(false) }
    var showAddedToast by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedCategoryType by remember { mutableStateOf(CategoryType.SUBJECT) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var sheetSearchQuery by rememberSaveable(showCategorySheet) { mutableStateOf("") }

    val handleCategoryClick: (String, CategoryType) -> Unit = { category, type ->
        selectedCategory = category
        selectedCategoryType = type
        showCategorySheet = true
        sheetSearchQuery = ""
    }
    
    val book = selectedBook
    val currentBookId = book?.id ?: bookId
    
    // Get downloaded book info
    val downloadedBook = remember(currentBookId) { viewModel.getDownloadedBook(currentBookId) }
    
    // Load book details
    LaunchedEffect(bookId) {
        isLoading = true
        loadError = null
        viewModel.getBook(bookId)
    }
    
    // Update loading state when book is loaded
    LaunchedEffect(selectedBook) {
        if (selectedBook != null && selectedBook?.id == bookId) {
            isLoading = false
        }
    }
    
    // Check if current selection exists in library
    LaunchedEffect(selectedBook?.id, uid) {
        val currentId = selectedBook?.id
        val userId = uid
        if (currentId != null && userId != null) {
            isInLibrary = libraryRepository.isBookInLibrary(userId, "gutenberg_$currentId")
        }
    }
    
    // Show toast when added to library
    LaunchedEffect(showAddedToast) {
        if (showAddedToast) {
            Toast.makeText(context, "Added to library!", Toast.LENGTH_SHORT).show()
            showAddedToast = false
        }
    }

    LaunchedEffect(selectedCategory, selectedCategoryType, showCategorySheet) {
        val category = selectedCategory
        if (showCategorySheet && !category.isNullOrBlank()) {
            when (selectedCategoryType) {
                CategoryType.SUBJECT -> viewModel.searchBooks(category)
                CategoryType.BOOKSHELF -> viewModel.searchBooksByTopic(category, language = null)
            }
        }
    }

    LaunchedEffect(selectedCategory) {
        sheetSearchQuery = ""
    }
    
    val isDownloading = downloadingBookIds.contains(currentBookId)
    val currentProgress = downloadProgress[currentBookId] ?: 0f
    val isDownloaded = viewModel.isBookDownloaded(currentBookId)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (book != null && uid != null) {
                        IconButton(
                            onClick = {
                                if (!isInLibrary) {
                                    showStatusDialog = true
                                }
                            },
                            enabled = !isInLibrary
                        ) {
                            Icon(
                                if (isInLibrary) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isInLibrary) "In Library" else "Add to Library",
                                tint = if (isInLibrary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                loadError != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            loadError!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.getBook(bookId) }) {
                            Text("Retry")
                        }
                    }
                }
                book != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Header with cover
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Cover image
                                Card(
                                    modifier = Modifier
                                        .width(150.dp)
                                        .fillMaxHeight(),
                                    elevation = CardDefaults.cardElevation(8.dp)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(book.coverUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = book.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                
                                // Book info
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        text = book.authorNames,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Source: Project Gutenberg",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Source badge
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text(
                                            text = "Project Gutenberg",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                        )
                                    }
                                    
                                    // Download count
                                    book.downloadCount?.let { count ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Download,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${formatCount(count)} downloads",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Action buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (isDownloaded) {
                                Button(
                                    onClick = { 
                                        // Pass the downloaded book's fileUri if available
                                        onReadClick(book, downloadedBook?.fileUri)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Read Now")
                                }
                            } else if (isDownloading) {
                                OutlinedButton(
                                    onClick = { viewModel.cancelDownload(bookId) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { currentProgress },
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${(currentProgress * 100).toInt()}% - Cancel")
                                }
                            } else {
                                Button(
                                    onClick = { 
                                        viewModel.downloadBook(
                                            book = book,
                                            onSuccess = { downloaded ->
                                                if (uid != null) {
                                                    scope.launch {
                                                        val libraryId = "gutenberg_${book.id}"
                                                        val existing = libraryRepository.getBook(uid, libraryId)
                                                        val base = existing ?: LibraryBook(
                                                            id = libraryId,
                                                            title = book.title,
                                                            author = book.authorNames,
                                                            coverUrl = book.coverUrl,
                                                            description = book.subjects?.joinToString(", "),
                                                            ebookAccess = "public",
                                                            gutenbergId = book.id,
                                                            status = ReadingStatus.WANT_TO_READ.name
                                                        )
                                                        val localReference = downloaded.fileUri ?: downloaded.filePath
                                                        val updated = base.copy(
                                                            localFilePath = localReference,
                                                            localFileFormat = downloaded.format,
                                                            coverUrl = base.coverUrl ?: book.coverUrl,
                                                            title = base.title.ifBlank { book.title },
                                                            author = base.author.ifBlank { book.authorNames }
                                                        )
                                                        libraryRepository.addBookToLibrary(uid, updated)
                                                        if (!isInLibrary) {
                                                            isInLibrary = true
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download")
                                }
                            }
                            
                            // Add to library button
                            if (uid != null) {
                                OutlinedButton(
                                    onClick = {
                                        if (!isInLibrary) {
                                            showStatusDialog = true
                                        }
                                    },
                                    enabled = !isInLibrary
                                ) {
                                    Icon(
                                        if (isInLibrary) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isInLibrary) "In Library" else "Add")
                                }
                            }
                        }
                        
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        // Available formats
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Available Formats",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (book.epubUrl != null) {
                                    FormatChip(format = "EPUB", icon = Icons.Default.Book)
                                }
                                if (book.pdfUrl != null) {
                                    FormatChip(format = "PDF", icon = Icons.Default.PictureAsPdf)
                                }
                                if (book.textUrl != null) {
                                    FormatChip(format = "TXT", icon = Icons.Default.Description)
                                }
                                if (book.htmlUrl != null) {
                                    FormatChip(format = "HTML", icon = Icons.Default.Language)
                                }
                            }
                        }
                        
                        // Subjects/Categories
                        if (!book.subjects.isNullOrEmpty()) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Subjects",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(book.subjects.take(10)) { subject ->
                                        SuggestionChip(
                                            onClick = { handleCategoryClick(subject, CategoryType.SUBJECT) },
                                            label = { Text(subject.take(30)) }
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Bookshelves
                        if (!book.bookshelves.isNullOrEmpty()) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Bookshelves",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(book.bookshelves) { shelf ->
                                        AssistChip(
                                            onClick = { handleCategoryClick(shelf, CategoryType.BOOKSHELF) },
                                            label = { Text(shelf) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Languages
                        if (!book.languages.isNullOrEmpty()) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Languages",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = book.languages.joinToString(", ") { getLanguageName(it) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // Copyright info
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Copyright",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (book.copyright == false) "Public Domain - Free to read and download" else "May have copyright restrictions",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
    if (showStatusDialog && book != null && uid != null) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text("Choose reading status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    ReadingStatus.values().forEach { status ->
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val libraryBook = LibraryBook(
                                        id = "gutenberg_${book.id}",
                                        title = book.title,
                                        author = book.authorNames,
                                        coverUrl = book.coverUrl,
                                        description = book.subjects?.joinToString(", "),
                                        openLibraryId = null,
                                        internetArchiveId = null,
                                        gutenbergId = book.id,
                                        ebookAccess = "public",
                                        status = status.name
                                    )
                                    val result = libraryRepository.addBookToLibrary(uid, libraryBook)
                                    if (result.isSuccess) {
                                        isInLibrary = true
                                        showAddedToast = true
                                    }
                                    showStatusDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(status.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatusDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCategorySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

        LaunchedEffect(showCategorySheet) {
            if (showCategorySheet) {
                sheetState.expand()
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    sheetState.hide()
                }.invokeOnCompletion {
                    showCategorySheet = false
                    selectedCategory = null
                    sheetSearchQuery = ""
                }
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
                Text(
                    text = selectedCategory ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = sheetSearchQuery,
                    onValueChange = { sheetSearchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search within results") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (sheetSearchQuery.isNotBlank()) {
                            IconButton(onClick = { sheetSearchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    }
                )
                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.4f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val results = searchResults
                    val trimmedQuery = sheetSearchQuery.trim()
                    val filteredResults = if (trimmedQuery.isBlank()) {
                        results
                    } else {
                        results.filter { result ->
                            result.title.contains(trimmedQuery, ignoreCase = true) ||
                                result.authorNames.contains(trimmedQuery, ignoreCase = true)
                        }
                    }

                    if (results.isEmpty()) {
                        Text(
                            text = "No books found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (filteredResults.isEmpty()) {
                        Text(
                            text = "No matches for \"$trimmedQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredResults) { result ->
                                ListItem(
                                    headlineContent = { Text(result.title) },
                                    supportingContent = {
                                        Text(
                                            result.authorNames,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    leadingContent = {
                                        AsyncImage(
                                            model = result.coverUrl,
                                            contentDescription = result.title,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            sheetState.hide()
                                        }.invokeOnCompletion {
                                            showCategorySheet = false
                                            selectedCategory = null
                                            sheetSearchQuery = ""
                                            isLoading = true
                                            loadError = null
                                            viewModel.getBook(result.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatChip(
    format: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = format,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }
}

private fun getLanguageName(code: String): String {
    return when (code.lowercase()) {
        "en" -> "English"
        "fr" -> "French"
        "de" -> "German"
        "es" -> "Spanish"
        "it" -> "Italian"
        "pt" -> "Portuguese"
        "nl" -> "Dutch"
        "la" -> "Latin"
        "el" -> "Greek"
        "zh" -> "Chinese"
        "ja" -> "Japanese"
        "ru" -> "Russian"
        else -> code.uppercase()
    }
}

private enum class CategoryType {
    SUBJECT,
    BOOKSHELF
}
