package com.theblankstate.libri.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
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
import com.theblankstate.libri.datamodel.LibraryBook
import com.theblankstate.libri.datamodel.ReadingStatus
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        // key() on the book's identity ensures all local Compose state (scroll position,
        // expanded states, etc.) resets when navigating to a different book.
        key(bookIdentityKey) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Header Background (Partial height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(book.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 20.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.7f
                )
                // Dark overlay for header text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
                // Gradient to blend into surface
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                )
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = paddingValues.calculateBottomPadding())
                    .statusBarsPadding()
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White // Keep white as it's on the dark header
                        )
                    }

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
                            contentDescription = "Share",
                            tint = Color.White // Keep white as it's on the dark header
                        )
                    }
                }

                // Book Cover
                Card(
                    modifier = Modifier
                        .height(280.dp)
                        .width(180.dp)
                        .align(Alignment.CenterHorizontally),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(book.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Book Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Book Info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface, // Standard text color
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = book.author_name?.joinToString(", ") ?: "Unknown Author",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, // Standard variant color
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Genre
                    val subjects = workDetail?.subjects?.take(5) ?: book.subject?.take(3)
                    subjects?.let { subs ->
                        Text(
                            text = subs.joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    book.first_publish_year?.let {
                        Text(
                            text = "Published: $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Reading Status (Bookshelves)
                bookshelves?.counts?.let { counts ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatusItem(count = counts.wantToRead, label = "Want to Read", color = MaterialTheme.colorScheme.onSurface)
                        StatusItem(count = counts.currentlyReading, label = "Reading", color = MaterialTheme.colorScheme.onSurface)
                        StatusItem(count = counts.alreadyRead, label = "Read", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val isBorrowable = book.ebook_access == "borrowable"
                    val isPublic = book.ebook_access == "public" || book.has_fulltext == true

                    // Smart Read Now — prefer native reader for downloadable formats
                    val bestNativeOption = archiveDownloadOptions
                        .firstOrNull { it.readerFormat == BookFormat.EPUB }
                        ?: archiveDownloadOptions.firstOrNull { it.readerFormat == BookFormat.PDF }
                        ?: archiveDownloadOptions.firstOrNull { it.readerFormat == BookFormat.TXT }
                        ?: archiveDownloadOptions.firstOrNull { it.readerFormat == BookFormat.HTML }
                    val canReadNatively = bestNativeOption != null && effectiveArchiveId != null
                    val canReadEmbedded = effectiveArchiveId != null

                    if (isBorrowable) {
                        Button(
                            onClick = {
                                if (isUserLoggedIn) {
                                    showBorrowDialog = true
                                } else {
                                    onLoginRequired()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(text = "Borrow for Free", color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = {
                                if (canReadNatively) {
                                    // Route to native EPUB/PDF/TXT reader
                                    onReadArchiveOption(
                                        effectiveArchiveId!!,
                                        book.title,
                                        book.author_name?.firstOrNull(),
                                        book.coverUrl,
                                        bestNativeOption!!,
                                        null
                                    )
                                } else if (canReadEmbedded) {
                                    // Fallback to IA embedded web reader (never breaks)
                                    onReadClick(
                                        effectiveArchiveId!!,
                                        book.title,
                                        book.author_name?.firstOrNull(),
                                        book.coverUrl
                                    )
                                }
                            },
                            enabled = canReadNatively || canReadEmbedded || isLoadingArchiveDownloadOptions,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            if (isLoadingArchiveDownloadOptions) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(
                                    text = if (canReadNatively) {
                                        "Read in App"
                                    } else {
                                        "Read Now"
                                    }
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { 
                            if (uid != null) {
                                showAddToLibraryDialog = true
                            } else {
                                onLoginRequired()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    ) {
                        Text(text = "Add to Library")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

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

                // Description / Metadata Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "About this book",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                val description = workDetail?.getDescriptionText() 
                    ?: book.firstSentence?.firstOrNull() 
                    ?: "Description not available. Sorry for the inconvenience."
                
                var expanded by remember(description) { mutableStateOf(false) }
                var hasOverflow by remember(description) { mutableStateOf(false) }

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
                    
                    // Additional Metadata
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.weight(1f)) {
                                MetadataItem(
                                    label = "Pages", 
                                    value = book.number_of_pages?.toString() ?: book.edition_count?.toString() ?: "N/A", 
                                    icon = Icons.Default.MenuBook,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                MetadataItem(
                                    label = "Language", 
                                    value = book.language?.firstOrNull()?.uppercase() ?: "ENG", 
                                    icon = Icons.Default.Language,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.weight(1f)) {
                                MetadataItem(
                                    label = "Rating", 
                                    value = ratings?.summary?.average?.let { String.format("%.1f", it) } ?: book.ratings_average?.toString()?.take(3) ?: "N/A", 
                                    icon = Icons.Default.Star,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                val displayIsbn = book.isbn?.firstOrNull() 
                                    ?: editions.firstOrNull { !it.isbn13.isNullOrEmpty() }?.isbn13?.firstOrNull()
                                    ?: editions.firstOrNull { !it.isbn10.isNullOrEmpty() }?.isbn10?.firstOrNull()
                                    ?: "N/A"
                                MetadataItem(
                                    label = "ISBN", 
                                    value = displayIsbn, 
                                    icon = Icons.Default.QrCode,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Publisher and Date
                        if (!book.publisher.isNullOrEmpty() || !book.publish_date.isNullOrEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                book.publisher?.firstOrNull()?.let {
                                    MetadataItem(label = "Publisher", value = it, color = MaterialTheme.colorScheme.onSurface)
                                }
                                book.displayPublishDate?.let {
                                    MetadataItem(label = "Published", value = it, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Classifications
                        if (!book.dewey_decimal_class.isNullOrEmpty() || !book.lcc_number.isNullOrEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                book.dewey_decimal_class?.firstOrNull()?.let {
                                    MetadataItem(label = "Dewey", value = it, color = MaterialTheme.colorScheme.onSurface)
                                }
                                book.lcc_number?.firstOrNull()?.let {
                                    MetadataItem(label = "LCC", value = it, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    
                    // Subject Places/People/Times
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        workDetail?.subjectPlaces?.take(5)?.let { places ->
                            Text(
                                text = "Places: ${places.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        workDetail?.subjectPeople?.take(5)?.let { people ->
                            Text(
                                text = "People: ${people.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        workDetail?.subjectTimes?.take(5)?.let { times ->
                            Text(
                                text = "Times: ${times.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Excerpts
                    workDetail?.excerpts?.firstOrNull()?.let { excerpt ->
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Excerpt",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
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

                // Ratings Breakdown
                ratings?.counts?.let { counts ->
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            text = "Ratings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val total = counts.values.sum().toFloat()
                        (5 downTo 1).forEach { star ->
                            val count = counts[star.toString()] ?: 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "$star ★", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(30.dp))
                                LinearProgressIndicator(
                                    progress = { if (total > 0) count / total else 0f },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .padding(horizontal = 8.dp),
                                    color = Color(0xFFFFC107),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Text(text = count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Editions
                if (editions.isNotEmpty()) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            text = "Editions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        editions.take(5).forEach { edition ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                onClick = {
                                    edition.key?.let { key ->
                                        onBookClick(key)
                                    }
                                }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = edition.title ?: "Unknown Title",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${edition.publishers?.joinToString(", ") ?: "Unknown Publisher"} • ${edition.publishDate ?: "Unknown Date"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        // See All Editions button
                        OutlinedButton(
                            onClick = {
                                book.key?.let { key ->
                                    val workId = key.removePrefix("/works/")
                                    onSeeAllEditionsClick(workId)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(text = "See All Editions")
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Related Books Section
                if (similarBooks.isNotEmpty()) {
                    Text(
                        text = "You might also like",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 24.dp, bottom = 16.dp)
                    )
                    RelatedBookCarousel(books = similarBooks, onBookClick = onBookClick)
                    Spacer(modifier = Modifier.height(32.dp))
                }
                
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
        
        } // end key(bookIdentityKey)
        
        // Add to Library Dialog with Shelves Support
        if (showAddToLibraryDialog && book != null) {
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
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
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
                    tint = MaterialTheme.colorScheme.secondary,
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
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
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

@Composable
fun StatusItem(count: Int, label: String, color: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.6f)
        )
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
    val isDownloadingArchiveOption = libraryDownloadingBookIds.contains(archiveLibraryBook.id)
    val archiveDownloadProgress = libraryDownloadProgress[archiveLibraryBook.id] ?: 0f

    // Check if already downloaded
    val isGutenbergDownloaded = gutenbergId?.let { gutenbergViewModel.isBookDownloaded(it) } ?: false

    val hasExternalSources = gutenbergId != null || standardEbooksId != null || librivoxId != null

    val isBorrowableBook = book.ebook_access == "borrowable"

    // For borrowable books, show purchase options instead of download options
    if (isBorrowableBook) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Get This Book",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Purchase Hard Copy
            val isbn = book.isbn?.firstOrNull()
            if (isbn != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
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
                            tint = MaterialTheme.colorScheme.tertiary,
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
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    } else if (isLoadingArchiveDownloadOptions || archiveDownloadOptions.isNotEmpty()) {
        // For public/free books, show download options
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Internet Archive Downloads",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoadingArchiveDownloadOptions) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (hasExternalSources) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Free Ebook Sources",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Project Gutenberg - In-app reading
            gutenbergId?.let { id ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
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
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://standardebooks.org/ebooks/$id") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Get from Standard Ebooks")
                }
            }

            // LibriVox (Audio)
            librivoxId?.let { id ->
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://librivox.org/search?q=$id&search_form=advanced") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "LibriVox (Audiobook)")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
