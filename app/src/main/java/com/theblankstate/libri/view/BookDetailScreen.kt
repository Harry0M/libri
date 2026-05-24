package com.theblankstate.libri.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalUriHandler
import coil.request.ImageRequest
import com.theblankstate.libri.viewModel.BookViewModel
import com.theblankstate.libri.viewModel.LibraryViewModel
import com.theblankstate.libri.datamodel.BookshelfCounts
import com.theblankstate.libri.datamodel.EditionModel
import com.theblankstate.libri.datamodel.LibraryBook
import com.theblankstate.libri.datamodel.RatingsModel
import com.theblankstate.libri.datamodel.ReadingStatus
import com.theblankstate.libri.datamodel.WorkDetailModel
import com.theblankstate.libri.datamodel.bookModel
import com.theblankstate.libri.view.components.LibriTopAppBar
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.compose.ui.graphics.vector.ImageVector
import com.theblankstate.libri.viewModel.GutenbergViewModel
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Book
import com.theblankstate.libri.datamodel.ArchiveDownloadOption
import com.theblankstate.libri.datamodel.BookFormat

@Composable
fun BookDetailScreen(
    viewModel: BookViewModel = viewModel(),
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onSeeAllEditionsClick: (String) -> Unit = {},
    onReadClick: (String, String?, String?, String?) -> Unit = { _, _, _, _ -> },
    onReadGutenbergBook: (Int, String, String, String?, String?, BookFormat?) -> Unit = { _, _, _, _, _, _ -> },
    onReadArchiveOption: (String, String?, String?, String?, ArchiveDownloadOption, String?) -> Unit = { _, _, _, _, _, _ -> },
    isUserLoggedIn: Boolean,
    onBorrowConfirm: (String) -> Unit = {},
    onLoginRequired: () -> Unit = {}
) {
    // Scope to activity so download state survives navigation away and back
    val activity = LocalContext.current as androidx.activity.ComponentActivity
    val libraryViewModel: LibraryViewModel = composeViewModel(
        viewModelStoreOwner = activity,
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            activity.application
        )
    )
    val gutenbergViewModel: GutenbergViewModel = composeViewModel(
        viewModelStoreOwner = activity,
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            activity.application
        )
    )
    val selectedBook by viewModel.selectedBook.collectAsState()
    val similarBooks by viewModel.similarBooks.collectAsState()
    val workDetail by viewModel.workDetail.collectAsState()
    val editions by viewModel.editions.collectAsState()
    val ratings by viewModel.ratings.collectAsState()
    val bookshelves by viewModel.bookshelves.collectAsState()
    val archiveDownloadOptions by viewModel.archiveDownloadOptions.collectAsState()
    val isLoadingArchiveDownloadOptions by viewModel.isLoadingArchiveDownloadOptions.collectAsState()
    val resolvedArchiveId by viewModel.resolvedArchiveIdentifier.collectAsState()
    val downloadedBooks by libraryViewModel.downloadedBooks.collectAsState()
    val libraryDownloadingBookIds by libraryViewModel.downloadingBookIds.collectAsState()
    val libraryDownloadProgress by libraryViewModel.downloadProgressMap.collectAsState()
    val book = selectedBook
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val userPreferencesRepository = remember { com.theblankstate.libri.data.UserPreferencesRepository(context) }
    val uid = userPreferencesRepository.getGoogleUser().third
    var showBorrowDialog by remember { mutableStateOf(false) }
    var showAddToLibraryDialog by remember { mutableStateOf(false) }
    var showSuccessSnackbar by remember { mutableStateOf(false) }

    // Unique identity of the currently displayed book, used to key() UI state
    val bookIdentityKey = book?.key ?: book?.title

    val iaId = book?.ia?.firstOrNull()
    val archiveIdentifier = remember(book, editions) {
        book?.ia?.firstOrNull()
            ?: editions.firstOrNull { !it.ocaid.isNullOrBlank() }?.ocaid
    }
    val borrowKey = remember(book, editions) {
        book?.key?.takeIf { it.startsWith("/books/") }
            ?: book?.cover_edition_key?.takeIf { it.isNotBlank() }?.let { "/books/$it" }
            ?: editions.firstOrNull { !it.ocaid.isNullOrBlank() }?.key
            ?: editions.firstOrNull { !it.key.isNullOrBlank() }?.key
            ?: book?.key
    }

    if (showBorrowDialog && borrowKey != null) {
        AlertDialog(
            onDismissRequest = { showBorrowDialog = false },
            title = { Text("Borrow for Free on Open Library") },
            text = {
                Text("You'll be redirected to Open Library to complete the borrow. Continue?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showBorrowDialog = false
                    // Pass the full key - the WebView will construct the correct URL
                    onBorrowConfirm(borrowKey)
                }) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBorrowDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (book == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Use cascading fallback — tries: primary IA id → edition ocaid → ISBN search → title search
    // Skip for borrowable books since we show purchase options instead of downloads.
    //
    // KEY DESIGN: We use a single stable LaunchedEffect(book.key) and observe
    // archiveIdentifier + editions changes via snapshotFlow. This prevents the
    // LaunchedEffect from re-launching a new coroutine every time editions load,
    // which was causing the "loads twice" behaviour.
    //
    // collectLatest ensures that when editions arrive mid-flight, the previous
    // (incomplete) cascade is canceled and a fresh one starts with the new data.
    LaunchedEffect(book.key) {
        if (book.ebook_access == "borrowable") return@LaunchedEffect

        snapshotFlow {
            Triple(archiveIdentifier, editions, book.isbn)
        }.collectLatest { (id, eds, isbns) ->
            // If we have no primary identifier and no editions yet, wait briefly
            // for async data to arrive (editions usually load within 300-500ms).
            // collectLatest will cancel this delay if new data arrives sooner.
            // This avoids a wasteful first run that finds nothing via Steps 1-3,
            // only for editions to arrive 200ms later and trigger a second run.
            // We do NOT skip the call entirely — if nothing arrives after the
            // delay, we still run the cascade (Step 4: title+author search).
            if (id.isNullOrBlank() && eds.isEmpty()) {
                kotlinx.coroutines.delay(600)
            }

            viewModel.loadArchiveDownloadOptionsWithFallback(
                primaryIdentifier = id,
                editions = eds,
                isbn = isbns,
                title = book.title,
                author = book.author_name?.firstOrNull()
            )
        }
    }

    LaunchedEffect(book.key) {
        libraryViewModel.refreshDownloads()
    }

    // Use the resolved identifier (which may differ from archiveIdentifier if fallback found it)
    val effectiveArchiveId = resolvedArchiveId ?: archiveIdentifier

    val archiveLibraryBook = remember(book, workDetail, editions, archiveIdentifier) {
        LibraryBook(
            id = book.key?.substringAfterLast("/") ?: archiveIdentifier ?: book.title.hashCode().toString(),
            title = book.title,
            author = book.author_name?.firstOrNull() ?: "Unknown Author",
            coverUrl = book.coverUrl,
            description = workDetail?.getDescriptionText()?.take(500),
            isbn = book.isbn?.firstOrNull(),
            openLibraryId = book.key,
            internetArchiveId = archiveIdentifier,
            gutenbergId = book.id_project_gutenberg?.firstOrNull()?.toIntOrNull(),
            ebookAccess = book.ebook_access ?: "public",
            status = ReadingStatus.WANT_TO_READ.name,
            dateAdded = System.currentTimeMillis(),
            publisher = book.publisher?.firstOrNull(),
            totalPages = book.number_of_pages
                ?: editions.firstOrNull { (it.numberOfPages ?: 0) > 0 }?.numberOfPages
                ?: 0
        )
    }
    val activeArchiveDownloadId = archiveDownloadOptions
        .map { "${archiveLibraryBook.id}_${it.extension}" }
        .firstOrNull { libraryDownloadingBookIds.contains(it) }
    val activeArchiveDownloadProgress = activeArchiveDownloadId?.let { libraryDownloadProgress[it] }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LibriTopAppBar(
                title = "Book Details",
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Check out this book: ${book.title}")
                            val shareText = "Check out '${book.title}' by ${book.author_name?.firstOrNull() ?: "Unknown Author"} on Open Library: https://openlibrary.org${book.key}"
                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Book"))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        // key() on the book's identity ensures all local Compose state (scroll position,
        // expanded states, etc.) resets when navigating to a different book.
        key(bookIdentityKey) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                OpenLibraryBookHeroHeader(
                    book = book,
                    workDetail = workDetail,
                    ratings = ratings,
                    isCheckingFormats = isLoadingArchiveDownloadOptions,
                    downloadProgress = activeArchiveDownloadProgress,
                    availableFormatLabels = archiveDownloadOptions
                        .mapNotNull { it.readerFormat?.name ?: it.label.takeIf { label -> label.isNotBlank() } }
                        .distinct()
                        .take(4)
                )

                OpenLibraryActionSection(
                    book = book,
                    archiveDownloadOptions = archiveDownloadOptions,
                    effectiveArchiveId = effectiveArchiveId,
                    isLoadingArchiveDownloadOptions = isLoadingArchiveDownloadOptions,
                    isUserLoggedIn = isUserLoggedIn,
                    uid = uid,
                    onBorrowClick = { showBorrowDialog = true },
                    onLoginRequired = onLoginRequired,
                    onAddToLibraryClick = { showAddToLibraryDialog = true },
                    onReadClick = onReadClick,
                    onReadArchiveOption = onReadArchiveOption
                )

                bookshelves?.counts?.let { counts ->
                    OpenLibraryStatsSection(counts = counts)
                }

                // Download Options & Free Ebook Sources
                // (Extracted to separate composable to fix compiler instruction limit)
                BookDownloadSourcesSection(
                    book = book,
                    editions = editions,
                    archiveDownloadOptions = archiveDownloadOptions,
                    isLoadingArchiveDownloadOptions = isLoadingArchiveDownloadOptions,
                    archiveLibraryBook = archiveLibraryBook,
                    effectiveArchiveId = effectiveArchiveId,
                    downloadedBooks = downloadedBooks,
                    gutenbergViewModel = gutenbergViewModel,
                    libraryViewModel = libraryViewModel,
                    uid = uid,
                    onLoginRequired = onLoginRequired,
                    onShowBorrowDialog = { showBorrowDialog = true },
                    onReadGutenbergBook = onReadGutenbergBook,
                    onReadArchiveOption = onReadArchiveOption,
                    borrowKey = borrowKey,
                    onShowSuccessSnackbar = { showSuccessSnackbar = true }
                )

                OpenLibrarySubjectsSection(book = book, workDetail = workDetail)
                OpenLibraryAboutSection(
                    book = book,
                    workDetail = workDetail,
                    editions = editions,
                    ratings = ratings
                )
                OpenLibraryRatingsSection(ratings = ratings)
                OpenLibraryEditionsSection(
                    book = book,
                    editions = editions,
                    onBookClick = onBookClick,
                    onSeeAllEditionsClick = onSeeAllEditionsClick
                )
                OpenLibraryRelatedSection(
                    books = similarBooks,
                    onBookClick = onBookClick
                )
                
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
        
        } // end key(bookIdentityKey)
        
        // Add to Library Dialog with Shelves Support
        if (showAddToLibraryDialog) {
            val shelvesViewModel: com.theblankstate.libri.viewModel.ShelvesViewModel = composeViewModel()
            val allShelves by shelvesViewModel.allShelves.collectAsState()
            var showCreateShelfDialog by remember { mutableStateOf(false) }

            // Load shelves when dialog is shown
            LaunchedEffect(uid) {
                uid?.let { shelvesViewModel.loadShelves(it) }
            }

            com.theblankstate.libri.view.components.AddToLibraryDialog(
                onDismiss = { showAddToLibraryDialog = false },
                onConfirm = { selectedStatus, selectedShelfIds ->
                    uid?.let { userId ->
                        // Try to find pages from editions if main book doesn't have it
                        val pages = book.number_of_pages 
                            ?: editions.firstOrNull { (it.numberOfPages ?: 0) > 0 }?.numberOfPages
                            ?: 0

                        val libraryBook = LibraryBook(
                            id = book.key?.substringAfterLast("/") ?: book.title.hashCode().toString(),
                            title = book.title,
                            author = book.author_name?.firstOrNull() ?: "Unknown Author",
                            coverUrl = book.coverUrl,
                            description = workDetail?.getDescriptionText()?.take(500),
                            isbn = book.isbn?.firstOrNull(),
                            openLibraryId = book.key,
                            internetArchiveId = iaId,
                            gutenbergId = book.id_project_gutenberg?.firstOrNull()?.toIntOrNull(),
                            ebookAccess = book.ebook_access,
                            status = selectedStatus?.name ?: ReadingStatus.WANT_TO_READ.name,
                            dateAdded = System.currentTimeMillis(),
                            publisher = book.publisher?.firstOrNull(),
                            totalPages = pages
                        )

                        // Add book to library (with status if selected)
                        libraryViewModel.addBookToLibrary(
                            userId,
                            libraryBook,
                            onSuccess = {
                                // If shelves were selected, add book to those shelves
                                if (selectedShelfIds.isNotEmpty()) {
                                    shelvesViewModel.addBookToShelves(
                                        uid = userId,
                                        bookId = libraryBook.id,
                                        shelfIds = selectedShelfIds,
                                        onSuccess = {
                                            showAddToLibraryDialog = false
                                            showSuccessSnackbar = true
                                        },
                                        onError = { error ->
                                            // Handle error - book was added to library but not to shelves
                                        }
                                    )
                                } else {
                                    showAddToLibraryDialog = false
                                    showSuccessSnackbar = true
                                }
                            },
                            onError = { error ->
                                // Handle error
                            }
                        )
                    }
                },
                shelves = allShelves,
                onCreateNewShelf = {
                    showCreateShelfDialog = true
                },
                bookTitle = book.title
            )

            // Create shelf dialog (nested)
            if (showCreateShelfDialog) {
                com.theblankstate.libri.view.components.CreateShelfDialog(
                    onDismiss = { showCreateShelfDialog = false },
                    onConfirm = { name, description ->
                        uid?.let { userId ->
                            shelvesViewModel.createShelf(
                                uid = userId,
                                name = name,
                                description = description,
                                onSuccess = {
                                    showCreateShelfDialog = false
                                    // Shelves list will auto-update via Flow
                                },
                                onError = { /* Handle error */ }
                            )
                        }
                    }
                )
            }
        }
        
        // Success Snackbar
        if (showSuccessSnackbar) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000)
                showSuccessSnackbar = false
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        "Added to library!",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenLibraryBookHeroHeader(
    book: bookModel,
    workDetail: WorkDetailModel?,
    ratings: RatingsModel?,
    isCheckingFormats: Boolean,
    downloadProgress: Float?,
    availableFormatLabels: List<String>
) {
    val context = LocalContext.current
    val subjects = workDetail?.subjects?.take(2) ?: book.subject?.take(2).orEmpty()
    val accessLabel = when {
        book.ebook_access == "borrowable" -> "Borrowable"
        book.ebook_access == "public" || book.has_fulltext == true -> "Readable"
        else -> "Catalog"
    }
    val ratingText = ratings?.summary?.average?.let { String.format("%.1f ★", it) }
        ?: book.ratings_average?.let { String.format("%.1f ★", it) }
    val availabilityText = listOfNotNull(
        accessLabel,
        book.language?.firstOrNull()?.uppercase()
    ).joinToString(" • ").takeIf { it.isNotBlank() }
    val metadata = listOfNotNull(
        book.first_publish_year?.let { "Published $it" },
        availabilityText,
        ratingText
    )

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
            Card(
                modifier = Modifier
                    .width(150.dp)
                    .fillMaxHeight(),
                elevation = CardDefaults.cardElevation(8.dp),
                shape = RoundedCornerShape(12.dp)
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
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = book.author_name?.joinToString(", ") ?: "Unknown Author",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                OpenLibraryHeroPill(
                    text = "Open Library",
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )

                if (metadata.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    metadata.take(3).forEach { item ->
                        Text(
                            text = item,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                if (subjects.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = subjects.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                when {
                    downloadProgress != null -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Downloading ${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
                        )
                    }
                    isCheckingFormats -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Checking formats...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
                        )
                    }
                    availableFormatLabels.isNotEmpty() -> {
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
                                text = "${availableFormatLabels.joinToString(" • ")} available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenLibraryHeroPill(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OpenLibraryActionSection(
    book: bookModel,
    archiveDownloadOptions: List<ArchiveDownloadOption>,
    effectiveArchiveId: String?,
    isLoadingArchiveDownloadOptions: Boolean,
    isUserLoggedIn: Boolean,
    uid: String?,
    onBorrowClick: () -> Unit,
    onLoginRequired: () -> Unit,
    onAddToLibraryClick: () -> Unit,
    onReadClick: (String, String?, String?, String?) -> Unit,
    onReadArchiveOption: (String, String?, String?, String?, ArchiveDownloadOption, String?) -> Unit
) {
    val isBorrowable = book.ebook_access == "borrowable"
    val bestNativeOption = archiveDownloadOptions
        .firstOrNull { it.readerFormat == BookFormat.EPUB }
        ?: archiveDownloadOptions.firstOrNull { it.readerFormat == BookFormat.PDF }
        ?: archiveDownloadOptions.firstOrNull { it.readerFormat == BookFormat.TXT }
        ?: archiveDownloadOptions.firstOrNull { it.readerFormat == BookFormat.HTML }
    val canReadNatively = bestNativeOption != null && effectiveArchiveId != null
    val canReadEmbedded = effectiveArchiveId != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isBorrowable) {
            Button(
                onClick = {
                    if (isUserLoggedIn) {
                        onBorrowClick()
                    } else {
                        onLoginRequired()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Borrow Free", color = Color.White, maxLines = 1)
            }
        } else {
            Button(
                onClick = {
                    if (canReadNatively) {
                        onReadArchiveOption(
                            effectiveArchiveId!!,
                            book.title,
                            book.author_name?.firstOrNull(),
                            book.coverUrl,
                            bestNativeOption!!,
                            null
                        )
                    } else if (canReadEmbedded) {
                        onReadClick(
                            effectiveArchiveId!!,
                            book.title,
                            book.author_name?.firstOrNull(),
                            book.coverUrl
                        )
                    }
                },
                enabled = canReadNatively || canReadEmbedded || isLoadingArchiveDownloadOptions,
                modifier = Modifier.weight(1f)
            ) {
                if (isLoadingArchiveDownloadOptions) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (canReadNatively) "Read in App" else "Read Now", maxLines = 1)
                }
            }
        }

        OutlinedButton(
            onClick = {
                if (uid != null) {
                    onAddToLibraryClick()
                } else {
                    onLoginRequired()
                }
            },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add", maxLines = 1)
        }
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun OpenLibraryStatsSection(counts: BookshelfCounts) {
    OpenLibraryDetailSection(title = "Community") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OpenLibraryStatusCard(
                count = counts.wantToRead,
                label = "Want",
                modifier = Modifier.weight(1f)
            )
            OpenLibraryStatusCard(
                count = counts.currentlyReading,
                label = "Reading",
                modifier = Modifier.weight(1f)
            )
            OpenLibraryStatusCard(
                count = counts.alreadyRead,
                label = "Read",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun OpenLibraryStatusCard(
    count: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OpenLibrarySubjectsSection(
    book: bookModel,
    workDetail: WorkDetailModel?
) {
    val subjects = (workDetail?.subjects ?: book.subject.orEmpty()).distinct().take(12)
    if (subjects.isEmpty()) return

    OpenLibraryDetailSection(title = "Subjects") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(subjects) { subject ->
                OpenLibraryTagPill(text = subject)
            }
        }
    }
}

@Composable
private fun OpenLibraryAboutSection(
    book: bookModel,
    workDetail: WorkDetailModel?,
    editions: List<EditionModel>,
    ratings: RatingsModel?
) {
    val description = workDetail?.getDescriptionText()
        ?: book.firstSentence?.firstOrNull()
        ?: "Description not available. Sorry for the inconvenience."
    var expanded by remember(description) { mutableStateOf(false) }
    var hasOverflow by remember(description) { mutableStateOf(false) }

    OpenLibraryDetailSection(title = "About this book") {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5,
            maxLines = if (expanded) Int.MAX_VALUE else 15,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            onTextLayout = { textLayoutResult ->
                if (textLayoutResult.hasVisualOverflow) {
                    hasOverflow = true
                }
            }
        )

        if (hasOverflow) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(if (expanded) "Read Less" else "Read More")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OpenLibraryMetadataGrid(
            items = buildOpenLibraryMetadataItems(book, editions, ratings)
        )

        OpenLibraryContextCards(workDetail = workDetail)
    }

    workDetail?.excerpts?.firstOrNull()?.let { excerpt ->
        OpenLibraryDetailSection(title = "Excerpt") {
            OpenLibraryInfoCard {
                Text(
                    text = "\"${excerpt.text}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!excerpt.comment.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "- ${excerpt.comment}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenLibraryRatingsSection(ratings: RatingsModel?) {
    val counts = ratings?.counts ?: return
    val total = counts.values.sum().toFloat()

    OpenLibraryDetailSection(title = "Ratings") {
        OpenLibraryInfoCard {
            (5 downTo 1).forEach { star ->
                val count = counts[star.toString()] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$star ★",
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(36.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                    LinearProgressIndicator(
                        progress = { if (total > 0) count / total else 0f },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .padding(horizontal = 8.dp),
                        color = Color(0xFFFFC107),
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    Text(
                        text = count.toString(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(40.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (star > 1) Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun OpenLibraryEditionsSection(
    book: bookModel,
    editions: List<EditionModel>,
    onBookClick: (String) -> Unit,
    onSeeAllEditionsClick: (String) -> Unit
) {
    if (editions.isEmpty()) return

    OpenLibraryDetailSection(title = "Editions") {
        editions.take(5).forEach { edition ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                onClick = {
                    edition.key?.let { key -> onBookClick(key) }
                },
                enabled = edition.key != null
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = edition.title ?: "Unknown Title",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${edition.publishers?.joinToString(", ") ?: "Unknown Publisher"} • ${edition.publishDate ?: "Unknown Date"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }

        OutlinedButton(
            onClick = {
                book.key?.let { key ->
                    val workId = key.removePrefix("/works/")
                    onSeeAllEditionsClick(workId)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(text = "See All Editions")
        }
    }
}

@Composable
private fun OpenLibraryRelatedSection(
    books: List<bookModel>,
    onBookClick: (String) -> Unit
) {
    if (books.isEmpty()) return

    OpenLibraryDetailSection(title = "You might also like") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(books) { book ->
                OpenLibraryRelatedBookCard(book = book, onBookClick = onBookClick)
            }
        }
    }
}

@Composable
private fun OpenLibraryRelatedBookCard(
    book: bookModel,
    onBookClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.width(148.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        onClick = { book.key?.let(onBookClick) },
        enabled = book.key != null
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(12.dp)
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
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = book.author_name?.firstOrNull() ?: "Unknown Author",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OpenLibraryDetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun OpenLibraryInfoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun OpenLibraryMetadataGrid(items: List<OpenLibraryMetadataUiItem>) {
    items.chunked(2).forEachIndexed { index, rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowItems.forEach { item ->
                OpenLibraryMetadataTile(
                    item = item,
                    modifier = Modifier.weight(1f)
                )
            }
            if (rowItems.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        if (index < items.chunked(2).lastIndex) {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OpenLibraryMetadataTile(
    item: OpenLibraryMetadataUiItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        MetadataItem(
            label = item.label,
            value = item.value,
            icon = item.icon,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun OpenLibraryContextCards(workDetail: WorkDetailModel?) {
    val rows = listOfNotNull(
        workDetail?.subjectPlaces?.take(5)?.joinToString(", ")?.let { "Places" to it },
        workDetail?.subjectPeople?.take(5)?.joinToString(", ")?.let { "People" to it },
        workDetail?.subjectTimes?.take(5)?.joinToString(", ")?.let { "Times" to it }
    )
    if (rows.isEmpty()) return

    Spacer(modifier = Modifier.height(12.dp))
    rows.forEach { (label, value) ->
        OpenLibraryInfoCard {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun OpenLibraryTagPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

private data class OpenLibraryMetadataUiItem(
    val label: String,
    val value: String,
    val icon: ImageVector?
)

@Composable
private fun buildOpenLibraryMetadataItems(
    book: bookModel,
    editions: List<EditionModel>,
    ratings: RatingsModel?
): List<OpenLibraryMetadataUiItem> {
    val displayIsbn = book.isbn?.firstOrNull()
        ?: editions.firstOrNull { !it.isbn13.isNullOrEmpty() }?.isbn13?.firstOrNull()
        ?: editions.firstOrNull { !it.isbn10.isNullOrEmpty() }?.isbn10?.firstOrNull()
        ?: "N/A"

    return listOfNotNull(
        OpenLibraryMetadataUiItem(
            label = "Pages",
            value = book.number_of_pages?.toString() ?: book.edition_count?.toString() ?: "N/A",
            icon = Icons.AutoMirrored.Filled.MenuBook
        ),
        OpenLibraryMetadataUiItem(
            label = "Language",
            value = book.language?.firstOrNull()?.uppercase() ?: "ENG",
            icon = Icons.Default.Language
        ),
        OpenLibraryMetadataUiItem(
            label = "Rating",
            value = ratings?.summary?.average?.let { String.format("%.1f", it) }
                ?: book.ratings_average?.toString()?.take(3)
                ?: "N/A",
            icon = Icons.Default.Star
        ),
        OpenLibraryMetadataUiItem(
            label = "ISBN",
            value = displayIsbn,
            icon = Icons.Default.QrCode
        ),
        book.publisher?.firstOrNull()?.let {
            OpenLibraryMetadataUiItem("Publisher", it, Icons.Default.Book)
        },
        book.displayPublishDate?.let {
            OpenLibraryMetadataUiItem("Published", it, Icons.AutoMirrored.Filled.MenuBook)
        },
        book.dewey_decimal_class?.firstOrNull()?.let {
            OpenLibraryMetadataUiItem("Dewey", it, Icons.Default.Book)
        },
        book.lcc_number?.firstOrNull()?.let {
            OpenLibraryMetadataUiItem("LCC", it, Icons.Default.Book)
        }
    )
}

@Composable
private fun OpenLibraryExternalSourceCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            OutlinedButton(onClick = onClick) {
                Text("Open")
            }
        }
    }
}

@Composable
private fun ArchiveDownloadOptionRow(
    option: ArchiveDownloadOption,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Float,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpenExternal: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val detail = listOfNotNull(
                        option.fileName,
                        option.sizeBytes?.let { formatArchiveOptionSize(it) }
                    ).joinToString(" • ")
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onOpenExternal) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Open file in browser",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isDownloading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    // Smooth animated progress
                    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = downloadProgress.coerceIn(0f, 1f),
                        animationSpec = androidx.compose.animation.core.tween(300)
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (option.canReadInApp) {
                    if (isDownloaded) {
                        Button(onClick = onRead) {
                            Text("Read")
                        }
                    } else if (isDownloading) {
                        OutlinedButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                    } else {
                        Button(onClick = onDownload) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download")
                        }
                    }
                } else {
                    Text(
                        text = "Open on archive.org",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onOpenExternal) {
                        Text("Open")
                    }
                }
            }
        }
    }
}

private fun formatArchiveOptionSize(bytes: Long): String {
    val mb = bytes / (1024f * 1024f)
    return if (mb >= 1f) {
        String.format("%.1f MB", mb)
    } else {
        "${(bytes / 1024f).toInt().coerceAtLeast(1)} KB"
    }
}

fun getStatusDisplayName(status: ReadingStatus): String {
    return when (status) {
        ReadingStatus.WANT_TO_READ -> "Want to Read"
        ReadingStatus.IN_PROGRESS -> "Currently Reading"
        ReadingStatus.FINISHED -> "Finished"
        ReadingStatus.ON_HOLD -> "On Hold"
        ReadingStatus.DROPPED -> "Dropped"
    }
}

@Composable
fun MetadataItem(
    label: String, 
    value: String, 
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Extracted from BookDetailScreen's Scaffold lambda to fix:
 * "Method exceeds compiler instruction limit: 16978"
 *
 * Contains: borrowable purchase options, IA download options, and free ebook sources.
 */
@Composable
private fun BookDownloadSourcesSection(
    book: com.theblankstate.libri.datamodel.bookModel,
    editions: List<com.theblankstate.libri.datamodel.EditionModel>,
    archiveDownloadOptions: List<ArchiveDownloadOption>,
    isLoadingArchiveDownloadOptions: Boolean,
    archiveLibraryBook: LibraryBook,
    effectiveArchiveId: String?,
    downloadedBooks: List<com.theblankstate.libri.datamodel.DownloadedBook>,
    gutenbergViewModel: GutenbergViewModel,
    libraryViewModel: LibraryViewModel,
    uid: String?,
    onLoginRequired: () -> Unit,
    onShowBorrowDialog: () -> Unit,
    onReadGutenbergBook: (Int, String, String, String?, String?, BookFormat?) -> Unit,
    onReadArchiveOption: (String, String?, String?, String?, ArchiveDownloadOption, String?) -> Unit,
    borrowKey: String?,
    onShowSuccessSnackbar: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    val gutenbergId = book.id_project_gutenberg?.firstOrNull()?.toIntOrNull()
    val standardEbooksId = book.id_standard_ebooks?.firstOrNull()
    val librivoxId = book.id_librivox?.firstOrNull()

    // Gutenberg download state
    val downloadingBookIds by gutenbergViewModel.downloadingBookIds.collectAsState()
    val downloadProgress by gutenbergViewModel.downloadProgress.collectAsState()
    val isDownloadingGutenberg = gutenbergId != null && downloadingBookIds.contains(gutenbergId)
    val gutenbergProgress = gutenbergId?.let { downloadProgress[it] } ?: 0f
    val libraryDownloadingBookIds by libraryViewModel.downloadingBookIds.collectAsState()
    val libraryDownloadProgress by libraryViewModel.downloadProgressMap.collectAsState()

    // Check if already downloaded
    val isGutenbergDownloaded = gutenbergId?.let { gutenbergViewModel.isBookDownloaded(it) } ?: false

    val hasExternalSources = gutenbergId != null || standardEbooksId != null || librivoxId != null

    val isBorrowableBook = book.ebook_access == "borrowable"

    // For borrowable books, show purchase options instead of download options
    if (isBorrowableBook) {
        OpenLibraryDetailSection(title = "Get This Book") {
            // Purchase Hard Copy
            val isbn = book.isbn?.firstOrNull()
            if (isbn != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Purchase Hard Copy",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Search online bookstores",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = {
                            uriHandler.openUri("https://www.google.com/search?tbm=shop&q=isbn+$isbn")
                        }) {
                            Text("Search")
                        }
                    }
                }
            }

            // Digital Purchase from Open Library
            val olKey = book.key
            if (olKey != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "View on Open Library",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Borrow digitally or find purchase links",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = {
                            uriHandler.openUri("https://openlibrary.org$olKey")
                        }) {
                            Text("Open")
                        }
                    }
                }
            }
        }
    } else if (isLoadingArchiveDownloadOptions || archiveDownloadOptions.isNotEmpty()) {
        // For public/free books, show download options
        OpenLibraryDetailSection(title = "Available Formats") {
            if (isLoadingArchiveDownloadOptions) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Checking available files...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            archiveDownloadOptions.forEach { option ->
                // Each option gets a unique download ID based on format
                val optionBookId = "${archiveLibraryBook.id}_${option.extension}"
                val optionBook = archiveLibraryBook.copy(id = optionBookId)
                val isThisOptionDownloading = libraryDownloadingBookIds.contains(optionBookId)
                val thisOptionProgress = libraryDownloadProgress[optionBookId] ?: 0f
                val downloadedOption = downloadedBooks.firstOrNull { it.id == optionBookId }
                val isThisOptionDownloaded = downloadedOption != null
                ArchiveDownloadOptionRow(
                    option = option,
                    isDownloading = isThisOptionDownloading,
                    isDownloaded = isThisOptionDownloaded,
                    downloadProgress = thisOptionProgress,
                    onRead = {
                        effectiveArchiveId?.let { id ->
                            onReadArchiveOption(
                                id,
                                book.title,
                                book.author_name?.firstOrNull(),
                                book.coverUrl,
                                option,
                                downloadedOption?.fileUri ?: downloadedOption?.filePath
                            )
                        }
                    },
                    onDownload = {
                        val userId = uid
                        if (userId == null) {
                            onLoginRequired()
                        } else {
                            libraryViewModel.downloadArchiveOption(
                                uid = userId,
                                book = optionBook,
                                option = option,
                                onSuccess = {
                                    libraryViewModel.refreshDownloads()
                                    onShowSuccessSnackbar()
                                }
                            )
                        }
                    },
                    onCancel = { libraryViewModel.cancelDownload(optionBookId) },
                    onOpenExternal = { uriHandler.openUri(option.url) }
                )
            }
        }
    }

    if (hasExternalSources) {
        OpenLibraryDetailSection(title = "Free Ebook Sources") {
            // Project Gutenberg - In-app reading
            gutenbergId?.let { id ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Book,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Project Gutenberg",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                "Free EPUB/PDF available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isDownloadingGutenberg) {
                                LinearProgressIndicator(
                                    progress = { gutenbergProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isGutenbergDownloaded) {
                                val downloadedGutenbergBook = gutenbergViewModel.getDownloadedBook(id)
                                // Read button if already downloaded
                                Button(
                                    onClick = {
                                        onReadGutenbergBook(
                                            id,
                                            book.title,
                                            book.author_name?.firstOrNull() ?: "Unknown",
                                            book.coverUrl,
                                            downloadedGutenbergBook?.fileUri ?: downloadedGutenbergBook?.filePath,
                                            downloadedGutenbergBook?.format
                                        )
                                    }
                                ) {
                                    Text("Read")
                                }
                            } else if (isDownloadingGutenberg) {
                                // Cancel button while downloading
                                OutlinedButton(
                                    onClick = { gutenbergViewModel.cancelDownload(id) }
                                ) {
                                    Text("Cancel")
                                }
                            } else {
                                // Download button
                                Button(
                                    onClick = {
                                        // Fetch book details and download
                                        gutenbergViewModel.getBook(id)
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download")
                                }
                            }

                            // Option to view on website
                            IconButton(
                                onClick = { uriHandler.openUri("https://www.gutenberg.org/ebooks/$id") }
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = "Open in browser",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Handle Gutenberg book fetch and download
                val gutenbergBook by gutenbergViewModel.selectedBook.collectAsState()
                LaunchedEffect(gutenbergBook) {
                    gutenbergBook?.let { fetchedBook ->
                        if (fetchedBook.id == id && !isDownloadingGutenberg && !isGutenbergDownloaded) {
                            gutenbergViewModel.downloadBook(
                                book = fetchedBook,
                                onSuccess = { downloadedBook ->
                                    // Could trigger navigation to reader here
                                }
                            )
                        }
                    }
                }
            }

            // Standard Ebooks
            standardEbooksId?.let { id ->
                OpenLibraryExternalSourceCard(
                    title = "Standard Ebooks",
                    subtitle = "Carefully proofed public-domain edition",
                    onClick = { uriHandler.openUri("https://standardebooks.org/ebooks/$id") }
                )
            }

            // LibriVox (Audio)
            librivoxId?.let { id ->
                OpenLibraryExternalSourceCard(
                    title = "LibriVox",
                    subtitle = "Audiobook availability",
                    onClick = { uriHandler.openUri("https://librivox.org/search?q=$id&search_form=advanced") }
                )
            }
        }
    }
}
