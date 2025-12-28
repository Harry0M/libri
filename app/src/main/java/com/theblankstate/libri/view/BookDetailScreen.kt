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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun BookDetailScreen(
    viewModel: BookViewModel = viewModel(),
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onSeeAllEditionsClick: (String) -> Unit = {},
    onReadClick: (String, String?, String?, String?) -> Unit = { _, _, _, _ -> },
    onReadGutenbergBook: (Int, String, String, String?) -> Unit = { _, _, _, _ -> },
    isUserLoggedIn: Boolean,
    onBorrowConfirm: (String) -> Unit = {},
    onLoginRequired: () -> Unit = {}
) {
    val libraryViewModel: LibraryViewModel = composeViewModel()
    val gutenbergViewModel: GutenbergViewModel = composeViewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
        )
    )
    val selectedBook by viewModel.selectedBook.collectAsState()
    val similarBooks by viewModel.similarBooks.collectAsState()
    val workDetail by viewModel.workDetail.collectAsState()
    val editions by viewModel.editions.collectAsState()
    val ratings by viewModel.ratings.collectAsState()
    val bookshelves by viewModel.bookshelves.collectAsState()
    val book = selectedBook
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val userPreferencesRepository = remember { com.theblankstate.libri.data.UserPreferencesRepository(context) }
    val uid = userPreferencesRepository.getGoogleUser().third
    var showBorrowDialog by remember { mutableStateOf(false) }
    var showAddToLibraryDialog by remember { mutableStateOf(false) }
    var showSuccessSnackbar by remember { mutableStateOf(false) }

    val iaId = book?.ia?.firstOrNull()
    // For borrowing, we need the edition key (like OL9219606M)
    // The book.key is typically like "/works/OL1234W" for works or "/books/OL5678M" for editions
    // We want to pass the full book page URL path for borrowing
    val borrowKey = book?.key  // e.g., "/works/OL1234W" or "/books/OL5678M"

    if (showBorrowDialog && borrowKey != null) {
        AlertDialog(
            onDismissRequest = { showBorrowDialog = false },
            title = { Text("Borrow on Open Library") },
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
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
                        .padding(16.dp),
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
                    val iaId = book.ia?.firstOrNull()
                    val isBorrowable = book.ebook_access == "borrowable"
                    val isPublic = book.ebook_access == "public" || book.has_fulltext == true

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
                            Text(text = "Borrow", color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = { 
                                iaId?.let { id ->
                                    onReadClick(
                                        id,
                                        book.title,
                                        book.author_name?.firstOrNull(),
                                        book.coverUrl
                                    )
                                }
                            },
                            enabled = iaId != null,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(text = "Read Now")
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

                // Download Options (External Sources)
                val gutenbergId = book.id_project_gutenberg?.firstOrNull()?.toIntOrNull()
                val standardEbooksId = book.id_standard_ebooks?.firstOrNull()
                val librivoxId = book.id_librivox?.firstOrNull()
                
                // Gutenberg download state
                val downloadingBookIds by gutenbergViewModel.downloadingBookIds.collectAsState()
                val downloadProgress by gutenbergViewModel.downloadProgress.collectAsState()
                val isDownloadingGutenberg = gutenbergId != null && downloadingBookIds.contains(gutenbergId)
                val gutenbergProgress = gutenbergId?.let { downloadProgress[it] } ?: 0f
                
                // Check if already downloaded
                val isGutenbergDownloaded = gutenbergId?.let { gutenbergViewModel.isBookDownloaded(it) } ?: false

                val hasExternalSources = gutenbergId != null || standardEbooksId != null || librivoxId != null

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
                                            // Read button if already downloaded
                                            Button(
                                                onClick = {
                                                    onReadGutenbergBook(
                                                        id,
                                                        book.title,
                                                        book.author_name?.firstOrNull() ?: "Unknown",
                                                        book.coverUrl
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
                                book.publish_date?.let {
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
                            description = workDetail?.description?.toString()?.take(500),
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
