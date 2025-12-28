package com.theblankstate.libri.view



import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theblankstate.libri.view.components.TopBarActionButton
import coil.compose.AsyncImage
import com.theblankstate.libri.datamodel.BookFormat
import com.theblankstate.libri.datamodel.ReadingStatus
import com.theblankstate.libri.viewModel.LibraryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryBookDetailScreen(
    bookId: String,
    onBackClick: () -> Unit,
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
    onReadClick: (String, String?, String?, String?) -> Unit = { _, _, _, _ -> },
    onReadLocalFile: (String, BookFormat?) -> Unit = { _, _ -> },
    isUserLoggedIn: Boolean = true,
    onBorrowConfirm: (String) -> Unit = {},
    onLoginRequired: () -> Unit = {}
) {
    val selectedBook by viewModel.selectedBook.collectAsStateWithLifecycle()

    val downloadingBookIds by viewModel.downloadingBookIds.collectAsStateWithLifecycle()
    val downloadProgressMap by viewModel.downloadProgressMap.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val userPreferencesRepository = remember { com.theblankstate.libri.data.UserPreferencesRepository(context) }
    val uid = userPreferencesRepository.getGoogleUser().third
    
    var showStatusDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteDownloadDialog by remember { mutableStateOf(false) }
    var currentPageInput by remember { mutableStateOf("") }
    var totalPagesInput by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }
    var currentRating by remember { mutableStateOf(0f) }
    var showBorrowDialog by remember { mutableStateOf(false) }

    val book = selectedBook ?: return
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    LaunchedEffect(book) {
        currentPageInput = book.currentPage.toString()
        totalPagesInput = book.totalPages.toString()
        commentText = book.comment
        currentRating = book.rating
        
        // Auto-fetch details if pages are missing
        if (book.totalPages == 0 && !book.openLibraryId.isNullOrEmpty()) {
            uid?.let { userId ->
                viewModel.fetchAndUpdateBookDetails(userId, book)
            }
        }
    }

    // Derived states used in top bar actions
    val hasLocalFile = !book.localFilePath.isNullOrEmpty()
    val iaId = book.internetArchiveId
    val isBorrowable = book.ebookAccess == "borrowable"
    val canRead = hasLocalFile || iaId != null
    val canDownload = viewModel.canDownloadBook(book) && !hasLocalFile
    val isDownloading = downloadingBookIds.contains(book.id)
    val progress = downloadProgressMap[book.id] ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 300)
    )
    val normalizedProgress = (animatedProgress / 100f).coerceIn(0f, 1f)

    val detailsBringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Gradient Background with Blurred Cover
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        ) {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(80.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.3f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        FilledIconButton(
                            onClick = onBackClick,
                            modifier = Modifier.padding(8.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            )
                        ) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        // Details (scroll to section)
                        TopBarActionButton(
                            onClick = {
                                coroutineScope.launch { detailsBringIntoViewRequester.bringIntoView() }
                            },
                            icon = Icons.Default.Info,
                            contentDescription = "Details",
                            modifier = Modifier.padding(8.dp)
                        )

                        // Read/Download/Delete/Share/Remove icons to mirror list menu
                        // Read offline (if downloaded)
                        if (hasLocalFile) {
                            TopBarActionButton(
                                onClick = {
                                    book.localFilePath?.let { path ->
                                        onReadLocalFile(path, book.localFileFormat)
                                    }
                                },
                                icon = Icons.Default.MenuBook,
                                contentDescription = "Read Offline"
                            )
                        }

                        // Download or cancel if downloading
                        if (canDownload) {
                            if (isDownloading) {
                                FilledIconButton(
                                    onClick = { viewModel.cancelDownload(book.id) },
                                    modifier = Modifier.padding(8.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        val scale = animateFloatAsState(
                                            targetValue = if (isDownloading) 1.05f else 1f,
                                            animationSpec = tween(durationMillis = 300)
                                        )
                                        CircularProgressIndicator(
                                            progress = normalizedProgress,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .graphicsLayer(scaleX = scale.value, scaleY = scale.value),
                                            strokeWidth = 2.dp
                                        )
                                        Icon(
                                            Icons.Default.Close,
                                            "Cancel download",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            } else {
                                FilledIconButton(
                                    onClick = { uid?.let { viewModel.downloadBook(it, book) } },
                                    modifier = Modifier.padding(8.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                    )
                                ) {
                                    Icon(Icons.Default.Download, "Download")
                                }
                            }
                        } else if (hasLocalFile) {
                            // Delete download if present
                            TopBarActionButton(
                                onClick = { showDeleteDownloadDialog = true },
                                icon = Icons.Default.DeleteForever,
                                contentDescription = "Delete Download"
                            )
                        }
                        // Share button
                        TopBarActionButton(
                            onClick = {
                                val shareUrl = if (!book.openLibraryId.isNullOrEmpty()) {
                                    "https://openlibrary.org${book.openLibraryId}"
                                } else {
                                    null
                                }
                                val shareText = buildString {
                                    append("Check out \"${book.title}\"")
                                    if (book.author.isNotEmpty()) {
                                        append(" by ${book.author}")
                                    }
                                    shareUrl?.let {
                                        append("\n\n$it")
                                    }
                                }
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, "Share book")
                                context.startActivity(shareIntent)
                            },
                            icon = Icons.Default.Share,
                            contentDescription = "Share"
                        )

                            // Remove from library button (move after share for consistency with the list menu order)
                            FilledIconButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.padding(8.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                )
                            ) {
                                Icon(Icons.Default.Delete, "Remove from Library")
                            }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // Hero Section with Book Cover
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Elevated Book Cover with Shadow
                    Card(
                        modifier = Modifier
                            .width(150.dp)
                            .height(225.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 12.dp
                        )
                    ) {
                        AsyncImage(
                            model = book.coverUrl,
                            contentDescription = book.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title and Author with Favorite
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (book.author.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "by ${book.author}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                            }
                        }
                        FilledIconButton(
                            onClick = {
                                uid?.let { userId ->
                                    viewModel.toggleFavorite(userId, book.id)
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (book.isFavorite)
                                    MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(
                                if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                "Favorite",
                                tint = if (book.isFavorite)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Unified Read/Borrow Button
                
                if (canRead || isBorrowable || canDownload) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Download button if applicable
                        if (canDownload) {
                            if (isDownloading) {
                                OutlinedButton(
                                    onClick = { viewModel.cancelDownload(book.id) },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    val scale2 = animateFloatAsState(
                                        targetValue = if (isDownloading) 1.05f else 1f,
                                        animationSpec = tween(durationMillis = 300)
                                    )
                                    CircularProgressIndicator(
                                        progress = normalizedProgress,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .graphicsLayer(scaleX = scale2.value, scaleY = scale2.value),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${progress.toInt()}% - Cancel")
                                }
                            } else {
                                Button(
                                    onClick = { uid?.let { viewModel.downloadBook(it, book) } },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download")
                                }
                            }
                        }
                        
                        // Read/Borrow button
                        if (canRead || isBorrowable) {
                            if (isBorrowable && !hasLocalFile) {
                                // Borrow button for borrowable books without local file
                                Button(
                                    onClick = {
                                        if (isUserLoggedIn) {
                                            showBorrowDialog = true
                                        } else {
                                            onLoginRequired()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LibraryBooks,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Borrow Book",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                // Read Now button - prioritizes local file, falls back to online
                                Button(
                                    onClick = { 
                                        if (hasLocalFile) {
                                            // Read from local file
                                            book.localFilePath?.let { path ->
                                                onReadLocalFile(path, book.localFileFormat)
                                            }
                                        } else {
                                            // Read from internet archive
                                            iaId?.let { id ->
                                                onReadClick(
                                                    id,
                                                    book.title,
                                                    book.author,
                                                    book.coverUrl
                                                )
                                            }
                                        }
                                    },
                                    enabled = hasLocalFile || iaId != null,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(
                                        if (hasLocalFile) Icons.Default.OfflinePin else Icons.Default.MenuBook,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = if (hasLocalFile) "Read Offline" else "Read Now",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content Cards
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .bringIntoViewRequester(detailsBringIntoViewRequester),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Reading Status Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showStatusDialog = true },
                        color = getStatusColor(book.readingStatusEnum).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    color = getStatusColor(book.readingStatusEnum),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            when (book.readingStatusEnum) {
                                                ReadingStatus.WANT_TO_READ -> Icons.Default.BookmarkAdd
                                                ReadingStatus.IN_PROGRESS -> Icons.Default.AutoStories
                                                ReadingStatus.FINISHED -> Icons.Default.CheckCircle
                                                ReadingStatus.ON_HOLD -> Icons.Default.Pause
                                                ReadingStatus.DROPPED -> Icons.Default.Close
                                            },
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Reading Status",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = getStatusText(book.readingStatusEnum),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = getStatusColor(book.readingStatusEnum)
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                "Change status",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Reading Progress Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoStories,
                                    "Reading Progress",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Reading Progress",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            if (book.totalPages > 0 && book.readingStatusEnum != ReadingStatus.WANT_TO_READ) {
                                // Circular Progress Indicator
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            progress = (book.readingProgress / 100f),
                                            modifier = Modifier.size(120.dp),
                                            strokeWidth = 12.dp,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "${book.readingProgress.toInt()}%",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = if (book.readingStatusEnum == ReadingStatus.FINISHED) "Complete" else "Read",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Current Page Input
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Current Page",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                                    )
                                    TextField(
                                        value = currentPageInput,
                                        onValueChange = {
                                            currentPageInput = it
                                            val current = it.toIntOrNull() ?: 0
                                            val total = totalPagesInput.toIntOrNull() ?: 0
                                            // Allow update even if total is 0, but prefer having total
                                            uid?.let { userId ->
                                                viewModel.updateReadingProgress(
                                                    userId,
                                                    book.id,
                                                    current,
                                                    total
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = TextFieldDefaults.colors(
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            disabledIndicatorColor = Color.Transparent,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.titleMedium.copy(
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                        )
                                    )
                                }

                                Text(
                                    "/",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 24.dp)
                                )

                                // Total Pages Input
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Total Pages",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                                    )
                                    TextField(
                                        value = totalPagesInput,
                                        onValueChange = {
                                            totalPagesInput = it
                                            val current = currentPageInput.toIntOrNull() ?: 0
                                            val total = it.toIntOrNull() ?: 0
                                            uid?.let { userId ->
                                                viewModel.updateReadingProgress(
                                                    userId,
                                                    book.id,
                                                    current,
                                                    total
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = TextFieldDefaults.colors(
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            disabledIndicatorColor = Color.Transparent,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.titleMedium.copy(
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Rating Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    "Your Rating",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Your Rating",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                for (i in 1..5) {
                                    FilledIconButton(
                                        onClick = {
                                            currentRating = i.toFloat()
                                            uid?.let { userId ->
                                                viewModel.updateRating(userId, book.id, i.toFloat())
                                            }
                                        },
                                        modifier = Modifier
                                            .padding(horizontal = 3.dp)
                                            .size(48.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = if (i <= currentRating.toInt())
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Icon(
                                            if (i <= currentRating.toInt())
                                                Icons.Default.Star
                                            else Icons.Default.StarBorder,
                                            "Rate $i stars",
                                            modifier = Modifier.size(28.dp),
                                            tint = if (i <= currentRating.toInt())
                                                MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (currentRating > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = when (currentRating.toInt()) {
                                        5 -> "⭐ Masterpiece!"
                                        4 -> "⭐ Loved it!"
                                        3 -> "⭐ Good read"
                                        2 -> "⭐ It's okay"
                                        else -> "⭐ Not for me"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Personal Notes Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.EditNote,
                                    "Personal Notes",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Personal Notes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = commentText,
                                onValueChange = {
                                    commentText = it
                                    uid?.let { userId ->
                                        viewModel.updateComment(userId, book.id, it)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Add your thoughts, quotes, or memories...") },
                                minLines = 4,
                                maxLines = 8,
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = {
                                    Icon(Icons.Default.Create, "Write notes")
                                }
                            )
                        }
                    }

                    // Timeline Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Timeline,
                                    "Timeline",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Reading Timeline",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            DateItem("Added", book.dateAdded)
                            book.dateStarted?.let { DateItem("Started", it) }
                            book.dateFinished?.let { DateItem("Finished", it) }
                        }
                    }

                    // Book Description Card
                    book.description?.let { desc ->
                        if (desc.isNotEmpty()) {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Description,
                                            "Description",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            "About This Book",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    var isExpanded by remember { mutableStateOf(false) }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = desc,
                                                style = MaterialTheme.typography.bodyMedium,
                                                lineHeight = 20.sp,
                                                maxLines = if (isExpanded) Int.MAX_VALUE else 6,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (desc.length > 300) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                FilledTonalButton(
                                                    onClick = { isExpanded = !isExpanded },
                                                    modifier = Modifier.align(Alignment.End),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text(
                                                        if (isExpanded) "Show Less" else "Read More",
                                                        style = MaterialTheme.typography.labelLarge
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // ISBN and Book Details Card
                    if (!book.isbn.isNullOrEmpty() || !book.openLibraryId.isNullOrEmpty()) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            "Book Details",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            "Book Details",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    
                                    // Show Fetch ISBN button if ISBN is missing but Open Library ID exists
                                    if (book.isbn.isNullOrEmpty() && !book.openLibraryId.isNullOrEmpty()) {
                                        var isFetchingIsbn by remember { mutableStateOf(false) }
                                        
                                        FilledTonalButton(
                                            onClick = {
                                                uid?.let { userId ->
                                                    isFetchingIsbn = true
                                                    viewModel.fetchAndUpdateIsbn(userId, book.id, book.openLibraryId)
                                                }
                                            },
                                            enabled = !isFetchingIsbn && uid != null,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            if (isFetchingIsbn) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Icon(
                                                Icons.Default.CloudDownload,
                                                null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                if (isFetchingIsbn) "Fetching..." else "Fetch ISBN"
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    book.isbn?.let { isbnValue ->
                                        if (isbnValue.isNotEmpty()) {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Tag,
                                                        "ISBN",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            "ISBN",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                        )
                                                        Text(
                                                            isbnValue,
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Show placeholder if ISBN is missing
                                    if (book.isbn.isNullOrEmpty() && !book.openLibraryId.isNullOrEmpty()) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.CloudOff,
                                                    "ISBN",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Column {
                                                    Text(
                                                        "ISBN",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                    )
                                                    Text(
                                                        "Not available - Click 'Fetch ISBN' above",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    book.openLibraryId?.let { olId ->
                                        if (olId.isNotEmpty()) {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.LibraryBooks,
                                                        "Open Library ID",
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            "Open Library ID",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                                        )
                                                        Text(
                                                            olId.substringAfterLast("/"),
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
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

                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        // Status Change Dialog
        if (showStatusDialog) {
            AlertDialog(
                onDismissRequest = { showStatusDialog = false },
                shape = RoundedCornerShape(24.dp),
                icon = {
                    Icon(
                        Icons.Default.MenuBook,
                        "Reading Status",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        "Change Reading Status",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReadingStatus.values().forEach { status ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        uid?.let { userId ->
                                            viewModel.updateReadingStatus(userId, book.id, status)
                                        }
                                        showStatusDialog = false
                                    },
                                color = if (book.readingStatusEnum == status)
                                    getStatusColor(status).copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            when (status) {
                                                ReadingStatus.WANT_TO_READ -> Icons.Default.BookmarkAdd
                                                ReadingStatus.IN_PROGRESS -> Icons.Default.AutoStories
                                                ReadingStatus.FINISHED -> Icons.Default.CheckCircle
                                                ReadingStatus.ON_HOLD -> Icons.Default.Pause
                                                ReadingStatus.DROPPED -> Icons.Default.Close
                                            },
                                            contentDescription = null,
                                            tint = if (book.readingStatusEnum == status)
                                                getStatusColor(status)
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            getStatusText(status),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (book.readingStatusEnum == status)
                                                FontWeight.Bold
                                            else FontWeight.Normal
                                        )
                                    }
                                    if (book.readingStatusEnum == status) {
                                        Icon(
                                            Icons.Default.Check,
                                            "Selected",
                                            tint = getStatusColor(status),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    FilledTonalButton(
                        onClick = { showStatusDialog = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Borrow Dialog
        if (showBorrowDialog) {
            val borrowKey = book.openLibraryId?.substringAfterLast("/")
            AlertDialog(
                onDismissRequest = { showBorrowDialog = false },
                shape = RoundedCornerShape(24.dp),
                icon = {
                    Icon(
                        Icons.Default.LibraryBooks,
                        "Borrow",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        "Borrow on Open Library",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "You'll be redirected to Open Library to complete the borrow. Continue?",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBorrowDialog = false
                            borrowKey?.let { onBorrowConfirm(it) }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Continue", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    FilledTonalButton(
                        onClick = { showBorrowDialog = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Delete Confirmation Dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
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
                            showDeleteDialog = false
                            onBackClick()
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
                        onClick = { showDeleteDialog = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Delete download confirmation dialog
        if (showDeleteDownloadDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDownloadDialog = false },
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
                            showDeleteDownloadDialog = false
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
                        onClick = { showDeleteDownloadDialog = false },
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
private fun DateItem(label: String, timestamp: Long) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when (label) {
                        "Added" -> Icons.Default.AddCircle
                        "Started" -> Icons.Default.PlayArrow
                        "Finished" -> Icons.Default.CheckCircle
                        else -> Icons.Default.Event
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = dateFormat.format(Date(timestamp)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun getStatusColor(status: ReadingStatus): Color {
    return when (status) {
        ReadingStatus.WANT_TO_READ -> Color(0xFF2196F3)
        ReadingStatus.IN_PROGRESS -> Color(0xFF4CAF50)
        ReadingStatus.FINISHED -> Color(0xFF9C27B0)
        ReadingStatus.ON_HOLD -> Color(0xFFFF9800)
        ReadingStatus.DROPPED -> Color(0xFFF44336)
    }
}

private fun getStatusText(status: ReadingStatus): String {
    return when (status) {
        ReadingStatus.WANT_TO_READ -> "Want to Read"
        ReadingStatus.IN_PROGRESS -> "In Progress"
        ReadingStatus.FINISHED -> "Finished"
        ReadingStatus.ON_HOLD -> "On Hold"
        ReadingStatus.DROPPED -> "Dropped"
    }
}