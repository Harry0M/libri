package com.theblankstate.libri.view
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.theblankstate.libri.datamodel.LibraryBook
import com.theblankstate.libri.view.components.LibriTopAppBar
import com.theblankstate.libri.viewModel.ShelfDetailUiState
import com.theblankstate.libri.viewModel.ShelvesViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShelfDetailScreen(
    uid: String?,
    shelfId: String,
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    viewModel: ShelvesViewModel = viewModel()
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var bookPendingRemoval by remember { mutableStateOf<LibraryBook?>(null) }
    val uiState by viewModel.shelfDetailUiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Load shelf details on first composition
    LaunchedEffect(uid, shelfId) {
        uid?.let { viewModel.loadShelfDetail(it, shelfId) }
    }

    // Show operation messages
    val operationMessage by viewModel.operationMessage.collectAsStateWithLifecycle()
    LaunchedEffect(operationMessage) {
        operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearOperationMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LibriTopAppBar(
                title = when (val state = uiState) {
                    is ShelfDetailUiState.Success -> state.shelf.name
                    else -> "Shelf"
                },
                onBackClick = onBackClick,
                actions = {
                    // Delete shelf action
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Shelf")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ShelfDetailUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ShelfDetailUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Error: ${state.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { uid?.let { viewModel.loadShelfDetail(it, shelfId) } }) {
                            Text("Retry")
                        }
                    }
                }
                is ShelfDetailUiState.Success -> {
                    if (state.books.isEmpty()) {
                        EmptyShelfState(modifier = Modifier.fillMaxSize())
                    } else {
                        ShelfBooksVerticalSlider(
                            shelfName = state.shelf.name,
                            shelfDescription = state.shelf.description,
                            books = state.books,
                            onBookClick = { book -> onBookClick(book.id) },
                            onRemoveBookClick = { book -> bookPendingRemoval = book },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog inside ShelfDetailScreen
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Shelf?") },
            text = { Text("Are you sure you want to delete this shelf? Books in the shelf will remain in your library.") },
            confirmButton = {
                Button(
                    onClick = {
                        uid?.let { userId ->
                            viewModel.deleteShelf(
                                uid = userId,
                                shelfId = shelfId,
                                onSuccess = {
                                    showDeleteDialog = false
                                    onBackClick()
                                },
                                onError = { error ->
                                    kotlinx.coroutines.MainScope().launch {
                                        snackbarHostState.showSnackbar(error)
                                    }
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    bookPendingRemoval?.let { book ->
        AlertDialog(
            onDismissRequest = { bookPendingRemoval = null },
            title = { Text("Remove from Shelf?") },
            text = { Text("Remove \"${book.title}\" from this shelf? The book will remain in your library.") },
            confirmButton = {
                Button(
                    onClick = {
                        uid?.let { userId ->
                            viewModel.removeBookFromShelf(
                                uid = userId,
                                bookId = book.id,
                                shelfId = shelfId,
                                onSuccess = { bookPendingRemoval = null },
                                onError = { error ->
                                    kotlinx.coroutines.MainScope().launch {
                                        snackbarHostState.showSnackbar(error)
                                    }
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { bookPendingRemoval = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyShelfState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(86.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            "No books in this shelf",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Add books to this shelf from the book detail page",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShelfBooksVerticalSlider(
    shelfName: String,
    shelfDescription: String,
    books: List<LibraryBook>,
    onBookClick: (LibraryBook) -> Unit,
    onRemoveBookClick: (LibraryBook) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { books.size })

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            pageSpacing = 18.dp
        ) { page ->
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            val focus = 1f - pageOffset.coerceIn(0f, 1f)

            ShelfBookSlide(
                book = books[page],
                shelfName = shelfName,
                shelfDescription = shelfDescription,
                page = page,
                total = books.size,
                onOpenClick = { onBookClick(books[page]) },
                onRemoveClick = { onRemoveBookClick(books[page]) },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 0.92f + (focus * 0.08f)
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.56f + (focus * 0.44f)
                    }
            )
        }

        ShelfSliderRail(
            currentPage = pagerState.currentPage,
            total = books.size,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShelfBookSlide(
    book: LibraryBook,
    shelfName: String,
    shelfDescription: String,
    page: Int,
    total: Int,
    onOpenClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onOpenClick,
        modifier = modifier,
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShelfContextLabel(
                    shelfName = shelfName,
                    shelfDescription = shelfDescription,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalIconButton(onClick = onRemoveClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove from shelf",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 220.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (book.coverUrl != null) {
                    AsyncImage(
                        model = book.coverUrl,
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    BookCoverPlaceholder(modifier = Modifier.fillMaxSize())
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = MaterialShapes.Pill.toShape(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                    tonalElevation = 0.dp
                ) {
                    Text(
                        "${page + 1} / $total",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    book.title.ifBlank { "Untitled book" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.author.isNotBlank()) {
                    Text(
                        book.author,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BookMetaPill(book.readingStatusEnum.displayName)
                    if (book.rating > 0f) {
                        BookMetaPill("${book.rating}/5")
                    }
                    book.localFileFormat?.let { format ->
                        BookMetaPill(format.name)
                    }
                }

                if (book.totalPages > 0) {
                    LinearProgressIndicator(
                        progress = { book.readingProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(MaterialTheme.shapes.extraLarge),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }

                if (!book.description.isNullOrBlank()) {
                    Text(
                        book.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = onOpenClick,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(Icons.Default.AutoStories, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Open book")
            }
        }
    }
}

@Composable
private fun ShelfContextLabel(
    shelfName: String,
    shelfDescription: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            shelfName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (shelfDescription.isNotBlank()) {
            Text(
                shelfDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookCoverPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(108.dp),
            shape = MaterialShapes.Flower.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun BookMetaPill(text: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShelfSliderRail(
    currentPage: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val dotCount = total.coerceAtMost(6)
    val activeDot = if (total <= 1) {
        0
    } else {
        ((currentPage.toFloat() / (total - 1)) * (dotCount - 1)).roundToInt()
    }

    Surface(
        modifier = modifier,
        shape = MaterialShapes.Pill.toShape(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            repeat(dotCount) { index ->
                Surface(
                    modifier = Modifier.size(if (index == activeDot) 10.dp else 6.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (index == activeDot) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
                    },
                    tonalElevation = 0.dp
                ) {}
            }
            Text(
                "${currentPage + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
