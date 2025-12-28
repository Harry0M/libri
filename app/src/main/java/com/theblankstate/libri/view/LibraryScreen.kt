@file:OptIn(ExperimentalMaterial3Api::class)

package com.theblankstate.libri.view



import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theblankstate.libri.data.OpenLibraryLoan
import com.theblankstate.libri.data.OpenLibraryListItem
import com.theblankstate.libri.datamodel.LibraryBook
import com.theblankstate.libri.datamodel.ReadingStatus
import com.theblankstate.libri.viewModel.LibraryViewModel
import com.theblankstate.libri.viewModel.OpenLibraryViewModel
import com.theblankstate.libri.viewModel.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    onAddBookClick: () -> Unit,
    onAddGutenbergClick: () -> Unit = {},
    onDownloadedBookClick: (com.theblankstate.libri.datamodel.DownloadedBook) -> Unit,
    onReadLocalBook: (LibraryBook) -> Unit = {},
    onReadOnlineBook: (LibraryBook) -> Unit = {},
    onShelvesClick: () -> Unit = {},
    viewModel: LibraryViewModel,
    openLibraryViewModel: OpenLibraryViewModel = viewModel(),
    onOpenLibraryBookClick: (String) -> Unit = {},
    onConnectOpenLibrary: () -> Unit = {},
    onOpenBookDetails: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val userPreferencesRepository = remember { com.theblankstate.libri.data.UserPreferencesRepository(context) }
    val uid = userPreferencesRepository.getGoogleUser().third
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterStatus by viewModel.filterStatus.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val showDownloads by viewModel.showDownloads.collectAsStateWithLifecycle()
    val downloadedBooks by viewModel.downloadedBooks.collectAsStateWithLifecycle()
    
    // Open Library state
    val isOLLoggedIn = openLibraryViewModel.isLoggedIn()
    val olUsername = openLibraryViewModel.getUsername()
    val myLoans by openLibraryViewModel.myLoans.collectAsStateWithLifecycle()
    val wantToRead by openLibraryViewModel.wantToRead.collectAsStateWithLifecycle()
    val alreadyRead by openLibraryViewModel.alreadyRead.collectAsStateWithLifecycle()
    val currentlyReading by openLibraryViewModel.currentlyReading.collectAsStateWithLifecycle()
    val isLoadingOLLists by openLibraryViewModel.isLoadingLists.collectAsStateWithLifecycle()
    var showOpenLibrary by remember { mutableStateOf(false) }

    var showFilterDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    
    var showImportDialog by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<android.net.Uri?>(null) }

    var showAddOptions by remember { mutableStateOf(false) }
    
    // Download state - tracks which specific books are downloading (supports multiple)
    val downloadingBookIds by viewModel.downloadingBookIds.collectAsStateWithLifecycle()
    
    // Notification permission for download notifications
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }
    
    // Request notification permission on first composition for Android 13+
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    // Delete confirmation dialog state
    var bookToDelete by remember { mutableStateOf<LibraryBook?>(null) }
    var bookToDeleteDownload by remember { mutableStateOf<LibraryBook?>(null) }
    
    // Share helper
    fun shareBook(book: LibraryBook) {
        val shareText = if (!book.openLibraryId.isNullOrEmpty()) {
            "Check out '${book.title}' by ${book.author} on Open Library: https://openlibrary.org${book.openLibraryId}"
        } else {
            "Check out '${book.title}' by ${book.author}"
        }
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Check out this book: ${book.title}")
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Book"))
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val mimeType = try { context.contentResolver.getType(it) ?: "" } catch (e: Exception) { "" }
            val path = it.path?.lowercase() ?: ""
            val isPdf = mimeType.contains("pdf") || path.endsWith(".pdf")
            val isEpub = mimeType.contains("epub") || path.endsWith(".epub")
            if (isPdf || isEpub) {
                importUri = it
                showImportDialog = true
            } else {
                android.widget.Toast.makeText(context, "Please select a PDF or EPUB file.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        uid?.let { viewModel.loadLibrary(it) }
        // Load Open Library lists if logged in
        if (isOLLoggedIn) {
            openLibraryViewModel.loadUserLists()
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddOptions = true },
                icon = { Icon(Icons.Default.Add, "Add", modifier = Modifier.size(24.dp)) },
                text = { 
                    Text(
                        "Add Book",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(18.dp)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top Bar Area with Search, Filter, Import
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Search Bar
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search your library...") },
                    leadingIcon = { Icon(Icons.Default.Search, "Search") },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, "Clear")
                                }
                            }
                            IconButton(onClick = { showFilterDialog = true }) {
                                Icon(Icons.Default.FilterList, "Filter")
                            }
                            IconButton(onClick = { showSortDialog = true }) {
                                Icon(Icons.Default.Sort, "Sort")
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Horizontal Scrollable Filter Chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // All Books Chip
                    item {
                        FilterChip(
                            selected = filterStatus == null && !showDownloads && !showOpenLibrary,
                            onClick = { 
                                viewModel.setFilterStatus(null)
                                viewModel.setShowDownloads(false)
                                showOpenLibrary = false
                            },
                            label = { Text("All") },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = null
                        )
                    }
                    
                    // Open Library Chip (only show if logged in)
                    if (isOLLoggedIn) {
                        item {
                            FilterChip(
                                selected = showOpenLibrary,
                                onClick = { 
                                    showOpenLibrary = !showOpenLibrary
                                    if (showOpenLibrary) {
                                        viewModel.setShowDownloads(false)
                                        viewModel.setFilterStatus(null)
                                        openLibraryViewModel.loadUserLists()
                                    }
                                },
                                label = { Text("Open Library") },
                                leadingIcon = if (showOpenLibrary) {
                                    { Icon(Icons.Default.LibraryBooks, null, Modifier.size(16.dp)) }
                                } else null,
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = androidx.compose.ui.graphics.Color(0xFF4285F4),
                                    selectedLabelColor = androidx.compose.ui.graphics.Color.White
                                ),
                                border = null
                            )
                        }
                    } else {
                        // Show connect option when not logged in
                        item {
                            FilterChip(
                                selected = false,
                                onClick = { onConnectOpenLibrary() },
                                label = { Text("Connect OL") },
                                leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF4285F4).copy(alpha = 0.5f))
                            )
                        }
                    }
                    
                    // Downloads Chip
                    item {
                        FilterChip(
                            selected = showDownloads,
                            onClick = { 
                                viewModel.setShowDownloads(!showDownloads)
                                showOpenLibrary = false
                            },
                            label = { Text("Downloads") },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = null
                        )
                    }
                    
                    // Status Filter Chips
                    items(
                        count = ReadingStatus.values().size,
                        key = { ReadingStatus.values()[it].name }
                    ) { index ->
                        val status = ReadingStatus.values()[index]
                        FilterChip(
                            selected = filterStatus == status && !showOpenLibrary,
                            onClick = { 
                                viewModel.setFilterStatus(status)
                                showOpenLibrary = false
                            },
                            label = { Text(status.displayName) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = null
                        )
                    }

                    // Shelves chip - navigates to the Shelves screen (only if signed in)
                    if (uid != null) {
                        item {
                            FilterChip(
                                selected = false,
                                onClick = { onShelvesClick() },
                                label = { Text("My Shelves") },
                                leadingIcon = { Icon(Icons.Default.Folder, null, Modifier.size(16.dp)) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = null
                            )
                        }
                    }
                }

                // Shelves chip - moved inside the LazyRow below
            }

            // Content
            if (showOpenLibrary) {
                // Show Open Library Lists Only
                OpenLibraryListsContent(
                    isLoading = isLoadingOLLists,
                    myLoans = myLoans,
                    currentlyReading = currentlyReading,
                    wantToRead = wantToRead,
                    alreadyRead = alreadyRead,
                    onBookClick = onOpenLibraryBookClick,
                    onRefresh = { openLibraryViewModel.loadUserLists() },
                    username = olUsername
                )
            } else if (showDownloads) {
                // Show Downloads
                if (downloadedBooks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                "No downloads",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                "No Downloaded Books",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Books you download will appear here",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(
                            count = downloadedBooks.size,
                            key = { downloadedBooks[it].id }
                        ) { index ->
                            val book = downloadedBooks[index]
                            DownloadedBookListItem(
                                book = book,
                                onClick = { onDownloadedBookClick(book) },
                                onDelete = { viewModel.removeDownloadedBook(book.id) }
                            )
                        }
                    }
                }
            } else {
                // Show Library Books (Local + Open Library when "All" is selected)
                when (val state = uiState) {
                    is com.theblankstate.libri.viewModel.LibraryUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is com.theblankstate.libri.viewModel.LibraryUiState.Success -> {
                        // Check if we have any content (local books or OL books)
                        val hasLocalBooks = state.books.isNotEmpty()
                        val hasOLBooks = isOLLoggedIn && (myLoans.isNotEmpty() || currentlyReading.isNotEmpty() || wantToRead.isNotEmpty() || alreadyRead.isNotEmpty())
                        
                        if (!hasLocalBooks && !hasOLBooks) {
                            EmptyLibraryState(
                                onAddBookClick = onAddBookClick,
                                onShowAddOptions = { showAddOptions = true }
                            )
                        } else {
                            if (filterStatus == null) {
                                // Grouped View for "All" - includes both local and OL books
                                val booksByStatus = state.books.groupBy { it.readingStatusEnum }
                                
                                androidx.compose.foundation.lazy.LazyColumn(
                                    contentPadding = PaddingValues(bottom = 80.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Local Library Books grouped by status
                                    ReadingStatus.values().forEach { status ->
                                        val booksInStatus = booksByStatus[status]
                                        if (!booksInStatus.isNullOrEmpty()) {
                                            item {
                                                StatusGroupCard(
                                                    status = status,
                                                    books = booksInStatus,
                                                    onBookClick = onBookClick,
                                                    onDetailsClick = { book ->
                                                        book.openLibraryId?.let { olId ->
                                                            val cleanId = olId.substringAfterLast("/")
                                                            onOpenBookDetails(cleanId)
                                                        }
                                                    },
                                                    onDownloadClick = { book ->
                                                        uid?.let { userId ->
                                                            viewModel.downloadBook(userId, book)
                                                        }
                                                    },
                                                    onDeleteDownloadClick = { book ->
                                                        bookToDeleteDownload = book
                                                    },
                                                    onRemoveClick = { book ->
                                                        bookToDelete = book
                                                    },
                                                    onShareClick = { book ->
                                                        shareBook(book)
                                                    },
                                                    onCancelDownloadClick = { book ->
                                                        viewModel.cancelDownload(book.id)
                                                    },
                                                    onReadOfflineClick = { book ->
                                                        if (!book.localFilePath.isNullOrEmpty()) {
                                                            onReadLocalBook(book)
                                                        }
                                                    },
                                                    onReadOnlineClick = { book ->
                                                        onReadOnlineBook(book)
                                                    },
                                                    canDownloadBook = { book ->
                                                        viewModel.canDownloadBook(book)
                                                    },
                                                    downloadingBookIds = downloadingBookIds
                                                )
                                            }
                                        }
                                    }
                                    
                                    // Open Library Books Section (if logged in and has books)
                                    if (isOLLoggedIn && hasOLBooks) {
                                        // My Loans from Open Library
                                        if (myLoans.isNotEmpty()) {
                                            item {
                                                OpenLibraryGroupCard(
                                                    title = "Borrowed from Open Library",
                                                    subtitle = "${myLoans.size} books",
                                                    icon = Icons.Default.LocalLibrary,
                                                    color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                                    items = myLoans.map { loan ->
                                                        OpenLibraryDisplayItem(
                                                            workKey = loan.workKey,
                                                            title = loan.title,
                                                            author = loan.author,
                                                            coverUrl = loan.coverUrl,
                                                            extraInfo = "Borrowed"
                                                        )
                                                    },
                                                    onBookClick = onOpenLibraryBookClick
                                                )
                                            }
                                        }
                                        
                                        // Currently Reading from Open Library
                                        if (currentlyReading.isNotEmpty()) {
                                            item {
                                                OpenLibraryGroupCard(
                                                    title = "Reading on Open Library",
                                                    subtitle = "${currentlyReading.size} books",
                                                    icon = Icons.Default.AutoStories,
                                                    color = androidx.compose.ui.graphics.Color(0xFF2196F3),
                                                    items = currentlyReading.map { item ->
                                                        OpenLibraryDisplayItem(
                                                            workKey = item.workKey,
                                                            title = item.title,
                                                            author = item.author,
                                                            coverUrl = item.coverUrl,
                                                            extraInfo = null
                                                        )
                                                    },
                                                    onBookClick = onOpenLibraryBookClick
                                                )
                                            }
                                        }
                                        
                                        // Want to Read from Open Library
                                        if (wantToRead.isNotEmpty()) {
                                            item {
                                                OpenLibraryGroupCard(
                                                    title = "Want to Read (Open Library)",
                                                    subtitle = "${wantToRead.size} books",
                                                    icon = Icons.Default.BookmarkBorder,
                                                    color = androidx.compose.ui.graphics.Color(0xFFFF9800),
                                                    items = wantToRead.map { item ->
                                                        OpenLibraryDisplayItem(
                                                            workKey = item.workKey,
                                                            title = item.title,
                                                            author = item.author,
                                                            coverUrl = item.coverUrl,
                                                            extraInfo = null
                                                        )
                                                    },
                                                    onBookClick = onOpenLibraryBookClick
                                                )
                                            }
                                        }
                                        
                                        // Already Read from Open Library
                                        if (alreadyRead.isNotEmpty()) {
                                            item {
                                                OpenLibraryGroupCard(
                                                    title = "Finished (Open Library)",
                                                    subtitle = "${alreadyRead.size} books",
                                                    icon = Icons.Default.CheckCircle,
                                                    color = androidx.compose.ui.graphics.Color(0xFF9C27B0),
                                                    items = alreadyRead.map { item ->
                                                        OpenLibraryDisplayItem(
                                                            workKey = item.workKey,
                                                            title = item.title,
                                                            author = item.author,
                                                            coverUrl = item.coverUrl,
                                                            extraInfo = null
                                                        )
                                                    },
                                                    onBookClick = onOpenLibraryBookClick
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Flat List for specific filter
                                androidx.compose.foundation.lazy.LazyColumn(
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    items(
                                        count = state.books.size,
                                        key = { state.books[it].id }
                                    ) { index ->
                                        val book = state.books[index]
                                        LibraryBookListItem(
                                            book = book,
                                            onClick = { onBookClick(book.id) },
                                            onDetailsClick = {
                                                book.openLibraryId?.let { olId ->
                                                    val cleanId = olId.substringAfterLast("/")
                                                    onOpenBookDetails(cleanId)
                                                }
                                            },
                                            onDownloadClick = {
                                                uid?.let { userId ->
                                                    viewModel.downloadBook(userId, book)
                                                }
                                            },
                                            onDeleteDownloadClick = {
                                                bookToDeleteDownload = book
                                            },
                                            onRemoveClick = {
                                                bookToDelete = book
                                            },
                                            onShareClick = {
                                                shareBook(book)
                                            },
                                            onCancelDownloadClick = {
                                                viewModel.cancelDownload(book.id)
                                            },
                                            onReadOfflineClick = {
                                                if (!book.localFilePath.isNullOrEmpty()) {
                                                    onReadLocalBook(book)
                                                }
                                            },
                                            onReadOnlineClick = {
                                                onReadOnlineBook(book)
                                            },
                                            canDownload = viewModel.canDownloadBook(book),
                                            isDownloaded = !book.localFilePath.isNullOrEmpty(),
                                            isDownloading = downloadingBookIds.contains(book.id),
                                            canReadOnline = !book.internetArchiveId.isNullOrEmpty()
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is com.theblankstate.libri.viewModel.LibraryUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Error loading library")
                        }
                    }
                }
            }
        }

        // Filter Dialog
        if (showFilterDialog) {
            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                title = { 
                    Text(
                        "Filter by Status",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = {
                                viewModel.setFilterStatus(null)
                                showFilterDialog = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (filterStatus == null)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = if (filterStatus == null) 4.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.LibraryBooks,
                                    null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (filterStatus == null)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "All Books",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (filterStatus == null) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (filterStatus == null)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        ReadingStatus.values().forEach { status ->
                            Surface(
                                onClick = {
                                    viewModel.setFilterStatus(status)
                                    showFilterDialog = false
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (filterStatus == status)
                                    getStatusColor(status).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (filterStatus == status)
                                    BorderStroke(2.dp, getStatusColor(status))
                                else null,
                                tonalElevation = if (filterStatus == status) 4.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        getStatusIcon(status),
                                        null,
                                        modifier = Modifier.size(24.dp),
                                        tint = if (filterStatus == status)
                                            getStatusColor(status)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        getStatusText(status),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = if (filterStatus == status) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (filterStatus == status)
                                            getStatusColor(status)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    FilledTonalButton(
                        onClick = { showFilterDialog = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Close",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Sort Dialog
        if (showSortDialog) {
            AlertDialog(
                onDismissRequest = { showSortDialog = false },
                title = { 
                    Text(
                        "Sort By",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortOption.values().forEach { option ->
                            Surface(
                                onClick = {
                                    viewModel.setSortOption(option)
                                    showSortDialog = false
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (sortOption == option)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = if (sortOption == option) 4.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        getSortIcon(option),
                                        null,
                                        modifier = Modifier.size(24.dp),
                                        tint = if (sortOption == option)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        getSortText(option),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (sortOption == option)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    FilledTonalButton(
                        onClick = { showSortDialog = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Close",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Import Dialog
        if (showImportDialog) {
            ImportBookDialog(
                onDismiss = { showImportDialog = false },
                onConfirm = { metadata ->
                    uid?.let { userId ->
                        if (importUri != null) {
                            viewModel.importLibraryBook(userId, importUri!!, metadata)
                        } else {
                            // Paperback: create a LibraryBook entry without a local file
                            val newBook = (metadata.copy(
                                id = java.util.UUID.randomUUID().toString(),
                                dateAdded = System.currentTimeMillis()
                            ))
                            viewModel.addBookToLibrary(userId, newBook)
                        }
                    }
                    showImportDialog = false
                },
                viewModel = viewModel,
                hasFile = importUri != null
            )
        }

        // Add Options Bottom Sheet
        if (showAddOptions) {
            ModalBottomSheet(
                onDismissRequest = { showAddOptions = false },
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        "Add New Book",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Surface(
                        onClick = {
                            showAddOptions = false
                            // Allow selection of both PDF and EPUB files; filter later
                            launcher.launch("*/*")
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.UploadFile,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    "Import PDF / EPUB",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Import a book from your device storage (PDF or EPUB)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Surface(
                        onClick = {
                            showAddOptions = false
                            onAddBookClick()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    "From Open Library",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Search and add books from Open Library",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        onClick = {
                            showAddOptions = false
                            importUri = null
                            showImportDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Book,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    "From Paperback",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Add a paper copy without a digital file",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        onClick = {
                            showAddOptions = false
                            onAddGutenbergClick()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoStories,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    "From Gutenberg",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Browse and add Project Gutenberg books",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Delete confirmation dialog
        bookToDelete?.let { book ->
            AlertDialog(
                onDismissRequest = { bookToDelete = null },
                shape = RoundedCornerShape(24.dp),
                icon = {
                    Icon(
                        Icons.Default.Warning,
                        "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        "Remove from Library?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "Are you sure you want to remove \"${book.title}\" from your library? This action cannot be undone.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            uid?.let { userId ->
                                viewModel.removeBookFromLibrary(userId, book.id)
                            }
                            bookToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    FilledTonalButton(
                        onClick = { bookToDelete = null },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Delete download confirmation dialog
        bookToDeleteDownload?.let { book ->
            AlertDialog(
                onDismissRequest = { bookToDeleteDownload = null },
                shape = RoundedCornerShape(24.dp),
                icon = {
                    Icon(
                        Icons.Default.DeleteForever,
                        "Delete Download",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        "Delete Download?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "Are you sure you want to delete the downloaded file for \"${book.title}\"? The book will remain in your library.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            uid?.let { userId ->
                                viewModel.deleteDownloadedFile(userId, book)
                            }
                            bookToDeleteDownload = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    FilledTonalButton(
                        onClick = { bookToDeleteDownload = null },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun LibraryBookCard(
    book: LibraryBook,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box {
                // Cover Image with gradient overlay
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(book.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = book.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Gradient overlay for better badge visibility
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        androidx.compose.ui.graphics.Color.Transparent,
                                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f)
                                    ),
                                    startY = 0f,
                                    endY = 500f
                                )
                            )
                    ) {}
                }

                // Favorite Icon with circular background
                Surface(
                    onClick = onFavoriteClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    tonalElevation = 3.dp
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "Favorite",
                            tint = if (book.isFavorite)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Status Badge with icon
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    color = getStatusColor(book.readingStatusEnum),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = getStatusIcon(book.readingStatusEnum),
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = getStatusText(book.readingStatusEnum),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.author.isNotEmpty()) {
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                
                // Page Count
                if (book.totalPages > 0) {
                    Text(
                        text = "${book.totalPages} pages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Progress Bar for IN_PROGRESS books
                if (book.readingStatusEnum == ReadingStatus.IN_PROGRESS &&
                    book.totalPages > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Progress",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${book.readingProgress.toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    LinearProgressIndicator(
                        progress = (book.readingProgress / 100f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                // Rating with stars
                if (book.rating > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        repeat(5) { index ->
                            Icon(
                                if (index < book.rating) Icons.Default.Star else Icons.Default.StarOutline,
                                "Star",
                                tint = if (index < book.rating)
                                    androidx.compose.ui.graphics.Color(0xFFFFB800)
                                else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    onAddBookClick: () -> Unit,
    onShowAddOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Decorative circular background with icon
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(120.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Default.Book,
                        "Empty library",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Your Library Awaits",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Start building your personal collection and track your reading journey",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            FilledTonalButton(
                onClick = onShowAddOptions,
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, "Add", modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Add Your First Book",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // Removed direct Gutenberg browse button to avoid duplicate navigation and keep add options modal
        }
    }
}

@Composable
private fun FilterOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

private fun getStatusColor(status: ReadingStatus): androidx.compose.ui.graphics.Color {
    return when (status) {
        ReadingStatus.WANT_TO_READ -> androidx.compose.ui.graphics.Color(0xFF2196F3)
        ReadingStatus.IN_PROGRESS -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        ReadingStatus.FINISHED -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
        ReadingStatus.ON_HOLD -> androidx.compose.ui.graphics.Color(0xFFFF9800)
        ReadingStatus.DROPPED -> androidx.compose.ui.graphics.Color(0xFFF44336)
    }
}

private fun getStatusIcon(status: ReadingStatus): androidx.compose.ui.graphics.vector.ImageVector {
    return when (status) {
        ReadingStatus.WANT_TO_READ -> Icons.Default.BookmarkBorder
        ReadingStatus.IN_PROGRESS -> Icons.Default.AutoStories
        ReadingStatus.FINISHED -> Icons.Default.CheckCircle
        ReadingStatus.ON_HOLD -> Icons.Default.Pause
        ReadingStatus.DROPPED -> Icons.Default.Close
    }
}

private fun getStatusText(status: ReadingStatus?): String {
    return when (status) {
        ReadingStatus.WANT_TO_READ -> "Want to Read"
        ReadingStatus.IN_PROGRESS -> "In Progress"
        ReadingStatus.FINISHED -> "Finished"
        ReadingStatus.ON_HOLD -> "On Hold"
        ReadingStatus.DROPPED -> "Dropped"
        null -> "All Books"
    }
}

private fun getSortText(option: SortOption): String {
    return when (option) {
        SortOption.DATE_ADDED -> "Date Added"
        SortOption.TITLE -> "Title"
        SortOption.AUTHOR -> "Author"
        SortOption.RATING -> "Rating"
        SortOption.PROGRESS -> "Progress"
    }
}

private fun getSortIcon(option: SortOption): androidx.compose.ui.graphics.vector.ImageVector {
    return when (option) {
        SortOption.DATE_ADDED -> Icons.Default.CalendarToday
        SortOption.TITLE -> Icons.Default.Title
        SortOption.AUTHOR -> Icons.Default.Person
        SortOption.RATING -> Icons.Default.Star
        SortOption.PROGRESS -> Icons.Default.TrendingUp
    }
}

@Composable
fun LibraryBookListItem(
    book: LibraryBook,
    onClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteDownloadClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onShareClick: () -> Unit,
    onCancelDownloadClick: () -> Unit = {},
    onReadOfflineClick: () -> Unit = {},
    onReadOnlineClick: () -> Unit = {},
    canDownload: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean = false,
    canReadOnline: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(book.coverUrl)
                .crossfade(true)
                .build(),
            contentDescription = book.title,
            modifier = Modifier
                .width(60.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "by ${book.author}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Page Count and Date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                if (book.totalPages > 0) {
                    Text(
                        text = "${book.totalPages} pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Text(
                    text = formatDate(book.dateAdded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            // Downloaded badge
            if (isDownloaded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Downloaded",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        // 3-dot Menu or Cancel button
        Box {
            if (isDownloading) {
                // Show cancel button while downloading
                IconButton(onClick = onCancelDownloadClick) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel download",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                // Details - only show if has OpenLibrary ID
                if (!book.openLibraryId.isNullOrEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Details") },
                        onClick = {
                            showMenu = false
                            onDetailsClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Info, contentDescription = null)
                        }
                    )
                }
                
                if (isDownloaded) {
                    DropdownMenuItem(
                        text = { Text("Read Offline") },
                        onClick = {
                            showMenu = false
                            onReadOfflineClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.MenuBook, contentDescription = null)
                        }
                    )
                } else if (canReadOnline) {
                    DropdownMenuItem(
                        text = { Text("Read Online") },
                        onClick = {
                            showMenu = false
                            onReadOnlineClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                        }
                    )
                }

                // Download or Delete Download
                if (isDownloaded) {
                    DropdownMenuItem(
                        text = { Text("Delete Download") },
                        onClick = {
                            showMenu = false
                            onDeleteDownloadClick()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                } else if (canDownload) {
                    DropdownMenuItem(
                        text = { Text("Download") },
                        onClick = {
                            showMenu = false
                            onDownloadClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Download, contentDescription = null)
                        }
                    )
                }
                
                // Share
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = {
                        showMenu = false
                        onShareClick()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Share, contentDescription = null)
                    }
                )
                
                HorizontalDivider()
                
                // Remove from Library
                DropdownMenuItem(
                    text = { 
                        Text(
                            "Remove from Library",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        showMenu = false
                        onRemoveClick()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun DownloadedBookListItem(
    book: com.theblankstate.libri.datamodel.DownloadedBook,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover
        AsyncImage(
            model = book.coverUrl?.replace("http:", "https:"),
            contentDescription = book.title,
            modifier = Modifier
                .width(60.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "by ${book.author}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Downloaded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        
        // Delete Icon
        IconButton(onClick = onDelete) {
             Icon(
                Icons.Default.Delete,
                "Delete",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

@Composable
fun StatusGroupCard(
    status: ReadingStatus,
    books: List<LibraryBook>,
    onBookClick: (String) -> Unit,
    onDetailsClick: (LibraryBook) -> Unit,
    onDownloadClick: (LibraryBook) -> Unit,
    onDeleteDownloadClick: (LibraryBook) -> Unit,
    onRemoveClick: (LibraryBook) -> Unit,
    onShareClick: (LibraryBook) -> Unit,
    onCancelDownloadClick: (LibraryBook) -> Unit = {},
    onReadOfflineClick: (LibraryBook) -> Unit,
    onReadOnlineClick: (LibraryBook) -> Unit,
    canDownloadBook: (LibraryBook) -> Boolean,
    downloadingBookIds: Set<String> = emptySet()
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Surface(
                    color = getStatusColor(status).copy(alpha = 0.15f),
                    shape = CircleShape
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            getStatusIcon(status),
                            contentDescription = null,
                            tint = getStatusColor(status),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = getStatusText(status),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = getStatusColor(status)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "${books.size} books",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // List of books
            Column {
                books.forEach { book ->
                    LibraryBookListItem(
                        book = book,
                        onClick = { onBookClick(book.id) },
                        onDetailsClick = { onDetailsClick(book) },
                        onDownloadClick = { onDownloadClick(book) },
                        onDeleteDownloadClick = { onDeleteDownloadClick(book) },
                        onRemoveClick = { onRemoveClick(book) },
                        onShareClick = { onShareClick(book) },
                        onCancelDownloadClick = { onCancelDownloadClick(book) },
                        onReadOfflineClick = {
                            if (!book.localFilePath.isNullOrEmpty()) {
                                onReadOfflineClick(book)
                            }
                        },
                        onReadOnlineClick = {
                            onReadOnlineClick(book)
                        },
                        canDownload = canDownloadBook(book),
                        isDownloaded = !book.localFilePath.isNullOrEmpty(),
                        isDownloading = downloadingBookIds.contains(book.id),
                        canReadOnline = !book.internetArchiveId.isNullOrEmpty()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportBookDialog(
    onDismiss: () -> Unit,
    onConfirm: (LibraryBook) -> Unit,
    viewModel: LibraryViewModel
    , hasFile: Boolean = true
) {
    var searchQuery by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var publisher by remember { mutableStateOf("") }
    var isbn by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf(ReadingStatus.WANT_TO_READ) }
    var isStatusExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    
    // Temporary storage for fetched book to preserve other fields like coverUrl
    var fetchedBook by remember { mutableStateOf<LibraryBook?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Book Details") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Auto-fill details",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("ISBN or Open Library ID") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    
                    FilledTonalButton(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                isLoading = true
                                searchError = null
                                viewModel.fetchBookMetadata(searchQuery) { book ->
                                    isLoading = false
                                    if (book != null) {
                                        fetchedBook = book
                                        title = book.title
                                        author = book.author
                                        publisher = book.publisher ?: ""
                                        isbn = book.isbn ?: ""
                                        description = book.description ?: ""
                                    } else {
                                        searchError = "No book found"
                                    }
                                }
                            }
                        },
                        enabled = !isLoading && searchQuery.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Search, null)
                        }
                    }
                }
                
                if (searchError != null) {
                    Text(
                        text = searchError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    "Book Details",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Status Dropdown
                ExposedDropdownMenuBox(
                    expanded = isStatusExpanded,
                    onExpandedChange = { isStatusExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedStatus.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Reading Status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isStatusExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = isStatusExpanded,
                        onDismissRequest = { isStatusExpanded = false }
                    ) {
                        ReadingStatus.values().forEach { status ->
                            DropdownMenuItem(
                                text = { Text(status.displayName) },
                                onClick = {
                                    selectedStatus = status
                                    isStatusExpanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = publisher,
                    onValueChange = { publisher = it },
                    label = { Text("Publisher") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = isbn,
                    onValueChange = { isbn = it },
                    label = { Text("ISBN") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalBook = (fetchedBook ?: LibraryBook()).copy(
                        title = title,
                        author = author,
                        publisher = publisher,
                        isbn = isbn,
                        description = description,
                        status = selectedStatus.name
                    )
                    onConfirm(finalBook)
                },
                enabled = title.isNotBlank()
            ) {
                Text(if (hasFile) "Save & Import" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Open Library Lists Content
@Composable
fun OpenLibraryListsContent(
    isLoading: Boolean,
    myLoans: List<OpenLibraryLoan>,
    currentlyReading: List<OpenLibraryListItem>,
    wantToRead: List<OpenLibraryListItem>,
    alreadyRead: List<OpenLibraryListItem>,
    onBookClick: (String) -> Unit,
    onRefresh: () -> Unit,
    username: String? = null
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Loading Open Library data...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else if (myLoans.isEmpty() && currentlyReading.isEmpty() && wantToRead.isEmpty() && alreadyRead.isEmpty()) {
        // Empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = androidx.compose.ui.graphics.Color(0xFF4285F4).copy(alpha = 0.1f),
                    modifier = Modifier.size(100.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            Icons.Default.LibraryBooks,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = androidx.compose.ui.graphics.Color(0xFF4285F4)
                        )
                    }
                }
                
                Text(
                    "No Open Library Books",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Your borrowed books, reading lists, and reading history from Open Library will appear here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                FilledTonalButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh")
                }
            }
        }
    } else {
        androidx.compose.foundation.lazy.LazyColumn(
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with username
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF4285F4).copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LibraryBooks,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color(0xFF4285F4),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Open Library",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color(0xFF4285F4)
                                )
                                if (username != null) {
                                    Text(
                                        "Signed in as $username",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = androidx.compose.ui.graphics.Color(0xFF4285F4)
                            )
                        }
                    }
                }
            }
            
            // My Loans Section
            if (myLoans.isNotEmpty()) {
                item {
                    OpenLibraryGroupCard(
                        title = "My Loans",
                        subtitle = "${myLoans.size} borrowed",
                        icon = Icons.Default.LocalLibrary,
                        color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                        items = myLoans.map { loan ->
                            OpenLibraryDisplayItem(
                                workKey = loan.workKey,
                                title = loan.title,
                                author = loan.author,
                                coverUrl = loan.coverUrl,
                                extraInfo = "Borrowed"
                            )
                        },
                        onBookClick = onBookClick
                    )
                }
            }
            
            // Currently Reading Section
            if (currentlyReading.isNotEmpty()) {
                item {
                    OpenLibraryGroupCard(
                        title = "Currently Reading",
                        subtitle = "${currentlyReading.size} books",
                        icon = Icons.Default.AutoStories,
                        color = androidx.compose.ui.graphics.Color(0xFF2196F3),
                        items = currentlyReading.map { item ->
                            OpenLibraryDisplayItem(
                                workKey = item.workKey,
                                title = item.title,
                                author = item.author,
                                coverUrl = item.coverUrl,
                                extraInfo = null
                            )
                        },
                        onBookClick = onBookClick
                    )
                }
            }
            
            // Want to Read Section
            if (wantToRead.isNotEmpty()) {
                item {
                    OpenLibraryGroupCard(
                        title = "Want to Read",
                        subtitle = "${wantToRead.size} books",
                        icon = Icons.Default.BookmarkBorder,
                        color = androidx.compose.ui.graphics.Color(0xFFFF9800),
                        items = wantToRead.map { item ->
                            OpenLibraryDisplayItem(
                                workKey = item.workKey,
                                title = item.title,
                                author = item.author,
                                coverUrl = item.coverUrl,
                                extraInfo = null
                            )
                        },
                        onBookClick = onBookClick
                    )
                }
            }
            
            // Already Read Section
            if (alreadyRead.isNotEmpty()) {
                item {
                    OpenLibraryGroupCard(
                        title = "Already Read",
                        subtitle = "${alreadyRead.size} books",
                        icon = Icons.Default.CheckCircle,
                        color = androidx.compose.ui.graphics.Color(0xFF9C27B0),
                        items = alreadyRead.map { item ->
                            OpenLibraryDisplayItem(
                                workKey = item.workKey,
                                title = item.title,
                                author = item.author,
                                coverUrl = item.coverUrl,
                                extraInfo = null
                            )
                        },
                        onBookClick = onBookClick
                    )
                }
            }
        }
    }
}

// Helper data class for display
data class OpenLibraryDisplayItem(
    val workKey: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val extraInfo: String?
)

@Composable
fun OpenLibraryGroupCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    items: List<OpenLibraryDisplayItem>,
    onBookClick: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = CircleShape
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = color
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Open Library badge
                Surface(
                    color = androidx.compose.ui.graphics.Color(0xFF4285F4).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "OL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = androidx.compose.ui.graphics.Color(0xFF4285F4),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            // List of books
            Column {
                items.forEach { item ->
                    OpenLibraryBookListItem(
                        item = item,
                        onClick = { onBookClick(item.workKey) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenLibraryBookListItem(
    item: OpenLibraryDisplayItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover
        Card(
            modifier = Modifier
                .width(50.dp)
                .height(75.dp),
            shape = RoundedCornerShape(4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            if (item.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Book,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.author.isNotBlank()) {
                Text(
                    text = "by ${item.author}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.extraInfo != null) {
                Text(
                    text = item.extraInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        
        // Arrow icon
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "View",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}