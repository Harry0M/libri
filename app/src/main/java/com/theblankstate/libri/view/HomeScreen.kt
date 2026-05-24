package com.theblankstate.libri.view

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theblankstate.libri.data.UserPreferencesRepository
import com.theblankstate.libri.datamodel.GutendexBook
import com.theblankstate.libri.datamodel.SubjectWork
import com.theblankstate.libri.datamodel.bookModel
import com.theblankstate.libri.util.BookFileUtils
import com.theblankstate.libri.view.components.AddBookEntrySheet
import com.theblankstate.libri.view.components.CreateShelfDialog
import com.theblankstate.libri.view.components.ExpressiveLoadingIndicator
import com.theblankstate.libri.viewModel.HomeState
import com.theblankstate.libri.viewModel.HomeViewModel
import com.theblankstate.libri.viewModel.HomeViewModelFactory
import com.theblankstate.libri.viewModel.LibraryViewModel
import com.theblankstate.libri.viewModel.ShelvesViewModel
import java.util.Calendar

private enum class HomeFeedMode {
    OPEN_LIBRARY,
    GUTENBERG
}

@Composable
fun HomeScreen(
    onBookClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onFreeGutenbergBooksClick: () -> Unit = {},
    onScanClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onGutenbergBookClick: (GutendexBook) -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(LocalContext.current.applicationContext as Application))
) {
    val homeState by viewModel.homeState.collectAsState()
    val context = LocalContext.current
    val userPreferencesRepository = remember(context) { UserPreferencesRepository(context) }
    val userName = remember(userPreferencesRepository) {
        userPreferencesRepository.getGoogleUser().second ?: "Book Lover"
    }
    val uid = remember(userPreferencesRepository) { userPreferencesRepository.getGoogleUser().third }
    var showAddOptions by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var homeFeedMode by remember { mutableStateOf(HomeFeedMode.OPEN_LIBRARY) }
    val libraryViewModel: LibraryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as Application
        )
    )
    val shelvesViewModel: ShelvesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val allShelves by shelvesViewModel.allShelves.collectAsState()
    androidx.compose.runtime.LaunchedEffect(uid) {
        uid?.let { shelvesViewModel.loadShelves(it) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        uri?.let {
            if (BookFileUtils.isSupported(context, it)) {
                importUri = it
                showImportDialog = true
            } else {
                android.widget.Toast.makeText(context, "Please select a PDF, EPUB, TXT, or HTML file.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            HomeTopBar(
                selectedMode = homeFeedMode,
                onModeChange = { homeFeedMode = it },
                onAddClick = { showAddOptions = true }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = homeState) {
                is HomeState.Loading -> {
                    ExpressiveLoadingIndicator(
                        label = "Curating your shelf",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is HomeState.Error -> {
                    HomeErrorState(
                        message = state.message,
                        onRetry = viewModel::retry,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is HomeState.PartialSuccess -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        if (homeFeedMode == HomeFeedMode.OPEN_LIBRARY) {
                            item {
                                OpenLibraryRetryBanner(
                                    message = state.retryMessage,
                                    onRetry = viewModel::retry
                                )
                            }
                        }
                        itemsIndexed(
                            state.gutenbergSections,
                            key = { index, section -> "gutenberg-${section.first}-$index" }
                        ) { _, (title, books) ->
                            if (books.isNotEmpty()) {
                                HomeSection(
                                    title = title,
                                    count = books.size
                                ) {
                                    GutenbergBookCarousel(
                                        books = books,
                                        onBookClick = onGutenbergBookClick
                                    )
                                }
                            }
                        }
                    }
                }

                is HomeState.Success -> {
                    val continueBooks = remember(state.content) {
                        state.content.firstOrNull { it.first.equals("Continue Reading", ignoreCase = true) }
                            ?.second.orEmpty()
                    }
                    val recommendationBooks = remember(state.content) {
                        state.content
                            .filterNot { it.first.equals("Continue Reading", ignoreCase = true) }
                            .flatMap { it.second }
                    }
                    val featuredBooks = remember(continueBooks, recommendationBooks) {
                        (continueBooks + recommendationBooks)
                            .distinctBy { it.key ?: it.title }
                            .take(8)
                    }
                    val spotlightLabel = if (continueBooks.isNotEmpty()) "Continue" else "Start"
                    val heroMessage = remember(continueBooks, recommendationBooks) {
                        when {
                            continueBooks.size > 1 ->
                                "Continue ${continueBooks.first().title.ifBlank { "your current read" }} and keep your shelf moving."
                            continueBooks.size == 1 ->
                                "Continue ${continueBooks.first().title.ifBlank { "your current read" }} or start a fresh pick."
                            recommendationBooks.isNotEmpty() ->
                                "No current read yet. Pick one from today's recommendations."
                            else -> "Your next chapter is loading."
                        }
                    }
                    val feedSections = if (homeFeedMode == HomeFeedMode.OPEN_LIBRARY) {
                        state.content
                    } else {
                        emptyList()
                    }
                    val gutenbergSections = state.gutenbergSections
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        item {
                            HomeHero(
                                greeting = greeting,
                                userName = userName,
                                featuredBooks = featuredBooks,
                                message = heroMessage,
                                spotlightLabel = spotlightLabel,
                                onBookClick = onBookClick
                            )
                        }

                        if (homeFeedMode == HomeFeedMode.OPEN_LIBRARY) {
                            itemsIndexed(
                                feedSections,
                                key = { index, section -> "${section.first}-$index" }
                            ) { _, (title, books) ->
                                if (books.isNotEmpty()) {
                                    HomeSection(
                                        title = title,
                                        count = books.size
                                    ) {
                                        RelatedBookCarousel(
                                            books = books,
                                            onBookClick = onBookClick
                                        )
                                    }
                                }
                            }
                        } else {
                            if (gutenbergSections.isEmpty()) {
                                item {
                                    GutenbergLoadingPanel(
                                        title = state.gutenbergTitle,
                                        subtitle = state.gutenbergSubtitle
                                    )
                                }
                            }
                            itemsIndexed(
                                gutenbergSections,
                                key = { index, section -> "home-gutenberg-${section.first}-$index" }
                            ) { _, (title, books) ->
                                if (books.isNotEmpty()) {
                                    HomeSection(
                                        title = title,
                                        count = books.size
                                    ) {
                                        GutenbergBookCarousel(
                                            books = books,
                                            onBookClick = onGutenbergBookClick
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

    if (showAddOptions) {
        AddBookEntrySheet(
            onDismiss = { showAddOptions = false },
            onScanIsbn = {
                showAddOptions = false
                onScanClick()
            },
            onManualIsbn = {
                showAddOptions = false
                importUri = null
                showImportDialog = true
            },
            onImportFile = {
                showAddOptions = false
                launcher.launch(BookFileUtils.supportedMimeTypes)
            },
            onSearchOnline = {
                showAddOptions = false
                onSearchClick()
            },
            onAddPaperback = {
                showAddOptions = false
                importUri = null
                showImportDialog = true
            },
            onBrowseGutenberg = {
                showAddOptions = false
                onFreeGutenbergBooksClick()
            }
        )
    }

    if (showImportDialog) {
        ImportBookDialog(
            onDismiss = { showImportDialog = false },
            onConfirm = { metadata ->
                uid?.let { userId ->
                    if (importUri != null) {
                        libraryViewModel.importLibraryBook(userId, importUri!!, metadata)
                    } else {
                        libraryViewModel.addBookToLibrary(
                            userId,
                            metadata.copy(
                                id = java.util.UUID.randomUUID().toString(),
                                dateAdded = System.currentTimeMillis()
                            )
                        )
                    }
                }
                showImportDialog = false
            },
            viewModel = libraryViewModel,
            hasFile = importUri != null,
            shelves = allShelves,
            onCreateNewShelf = { showCreateShelfDialog = true },
            onScanIsbn = {
                showImportDialog = false
                onScanClick()
            },
            onConfirmWithShelves = { metadata, shelfIds ->
                uid?.let { userId ->
                    if (importUri != null) {
                        libraryViewModel.importLibraryBook(userId, importUri!!, metadata, shelfIds)
                    } else {
                        libraryViewModel.addBookToLibraryWithShelves(
                            userId,
                            metadata.copy(
                                id = java.util.UUID.randomUUID().toString(),
                                dateAdded = System.currentTimeMillis()
                            ),
                            shelfIds
                        )
                    }
                }
                showImportDialog = false
            }
        )
    }

    if (showCreateShelfDialog) {
        CreateShelfDialog(
            onDismiss = { showCreateShelfDialog = false },
            onConfirm = { name, description ->
                uid?.let { userId ->
                    shelvesViewModel.createShelf(
                        uid = userId,
                        name = name,
                        description = description,
                        onSuccess = { showCreateShelfDialog = false }
                    )
                }
            }
        )
    }
}

@Composable
private fun HomeTopBar(
    selectedMode: HomeFeedMode,
    onModeChange: (HomeFeedMode) -> Unit,
    onAddClick: () -> Unit = {}
) {
    Surface(
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 2.dp)
        ) {
            HomeSourceSwitch(
                selectedMode = selectedMode,
                onModeChange = onModeChange,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                FilledTonalIconButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = "Add book")
                }
            }
        }
    }
}

@Composable
private fun HomeSourceSwitch(
    selectedMode: HomeFeedMode,
    onModeChange: (HomeFeedMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(224.dp)
            .height(44.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeSourceSwitchItem(
                selected = selectedMode == HomeFeedMode.OPEN_LIBRARY,
                label = "Open Library",
                onClick = { onModeChange(HomeFeedMode.OPEN_LIBRARY) },
                modifier = Modifier.weight(1f)
            )
            HomeSourceSwitchItem(
                selected = selectedMode == HomeFeedMode.GUTENBERG,
                label = "Gutenberg",
                onClick = { onModeChange(HomeFeedMode.GUTENBERG) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HomeSourceSwitchItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedWeight by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        label = "HomeSourceSwitchWeight"
    )
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = selectedWeight
                scaleY = selectedWeight
            }
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeHero(
    greeting: String,
    userName: String,
    featuredBooks: List<bookModel>,
    message: String,
    spotlightLabel: String,
    onBookClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = MaterialShapes.Flower.toShape(),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    tonalElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$greeting, $userName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (featuredBooks.isNotEmpty()) {
            HomeSpotlightCarousel(
                books = featuredBooks,
                spotlightLabel = spotlightLabel,
                onBookClick = onBookClick
            )
        } else {
            HomeSpotlightEmptyCard(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeSpotlightCarousel(
    books: List<bookModel>,
    spotlightLabel: String,
    onBookClick: (String) -> Unit
) {
    val carouselState = rememberCarouselState { books.size }

    HorizontalCenteredHeroCarousel(
        state = carouselState,
        modifier = Modifier
            .fillMaxWidth()
            .height(334.dp),
        maxItemWidth = 318.dp,
        itemSpacing = 14.dp,
        minSmallItemWidth = 70.dp,
        maxSmallItemWidth = 102.dp,
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) { index ->
        val book = books[index]
        val isFocused = carouselState.currentItem == index
        val shape = RoundedCornerShape(24.dp)

        HomeSpotlightBookCard(
            book = book,
            isFocused = isFocused,
            spotlightLabel = spotlightLabel,
            onClick = { book.key?.let(onBookClick) },
            modifier = Modifier
                .fillMaxSize()
                .maskClip(shape),
            shape = shape
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeSpotlightBookCard(
    book: bookModel,
    isFocused: Boolean,
    spotlightLabel: String,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 2.dp else 0.dp)
    ) {
        if (isFocused) {
            SpotlightOpenBookContent(
                book = book,
                spotlightLabel = spotlightLabel,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            BookSpineContent(
                title = book.title,
                coverUrl = book.coverUrl,
                yearText = book.first_publish_year?.toString(),
                useVerticalTitleRail = true,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SpotlightOpenBookContent(
    book: bookModel,
    spotlightLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ExpressiveBookCover(
            title = book.title,
            coverUrl = book.coverUrl,
            badgeText = null,
            modifier = Modifier
                .width(118.dp)
                .fillMaxHeight()
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    spotlightLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    book.title.ifBlank { "Untitled book" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    book.author_name?.firstOrNull().orEmpty().ifBlank { "Unknown author" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        book.first_publish_year?.toString(),
                        book.ebook_access?.replace("_", " ")
                    ).joinToString(" | ").ifBlank { "Open Library" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BookSignalPill(
                text = book.first_publish_year?.toString() ?: "Book",
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)
            )
        }
    }
}

@Composable
private fun GutenbergLoadingPanel(
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeSpotlightEmptyCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.AutoStories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Your next chapter is loading",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun HeroCoverPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.54f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.AutoStories,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
fun HomeSection(
    title: String,
    count: Int,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        content()
    }
}

@Composable
fun BookCarousel(
    books: List<SubjectWork>,
    onBookClick: (String) -> Unit
) {
    if (books.isEmpty()) return
    val carouselState = rememberCarouselState { books.size }

    HomeCarouselFrame { preferredItemWidth, smallItemWidth, itemSpacing, horizontalPadding ->
        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = preferredItemWidth,
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp),
            itemSpacing = itemSpacing,
            minSmallItemWidth = smallItemWidth,
            maxSmallItemWidth = smallItemWidth,
            contentPadding = PaddingValues(horizontal = horizontalPadding)
        ) { index ->
            val book = books[index]
            ExpressiveHomeBookCard(
                title = book.title.orEmpty(),
                author = book.authors?.firstOrNull()?.name.orEmpty(),
                coverUrl = book.coverUrl,
                badgeText = book.firstPublishYear?.toString(),
                onClick = { book.key?.let { onBookClick(it) } },
                modifier = Modifier
                    .fillMaxSize()
                    .maskClip(RoundedCornerShape(24.dp))
            )
        }
    }
}

@Composable
fun RelatedBookCarousel(
    books: List<bookModel>,
    onBookClick: (String) -> Unit
) {
    if (books.isEmpty()) return
    val carouselState = rememberCarouselState { books.size }

    HomeCarouselFrame { preferredItemWidth, smallItemWidth, itemSpacing, horizontalPadding ->
        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = preferredItemWidth,
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp),
            itemSpacing = itemSpacing,
            minSmallItemWidth = smallItemWidth,
            maxSmallItemWidth = smallItemWidth,
            contentPadding = PaddingValues(horizontal = horizontalPadding)
        ) { index ->
            val book = books[index]
            ExpressiveHomeBookCard(
                title = book.title,
                author = book.author_name?.firstOrNull().orEmpty(),
                coverUrl = book.coverUrl,
                badgeText = book.first_publish_year?.toString(),
                onClick = { book.key?.let { onBookClick(it) } },
                modifier = Modifier
                    .fillMaxSize()
                    .maskClip(RoundedCornerShape(24.dp))
            )
        }
    }
}

@Composable
fun GutenbergBookCarousel(
    books: List<GutendexBook>,
    onBookClick: (GutendexBook) -> Unit
) {
    if (books.isEmpty()) return
    val carouselState = rememberCarouselState { books.size }

    HomeCarouselFrame { preferredItemWidth, smallItemWidth, itemSpacing, horizontalPadding ->
        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = preferredItemWidth,
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp),
            itemSpacing = itemSpacing,
            minSmallItemWidth = smallItemWidth,
            maxSmallItemWidth = smallItemWidth,
            contentPadding = PaddingValues(horizontal = horizontalPadding)
        ) { index ->
            val book = books[index]
            ExpressiveHomeBookCard(
                title = book.title,
                author = book.authorNames,
                coverUrl = book.coverUrl,
                badgeText = "Free",
                onClick = { onBookClick(book) },
                modifier = Modifier
                    .fillMaxSize()
                    .maskClip(RoundedCornerShape(24.dp))
            )
        }
    }
}

@Composable
private fun HomeCarouselFrame(
    content: @Composable (Dp, Dp, Dp, Dp) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val horizontalPadding = 12.dp
        val itemSpacing = 10.dp
        val spineWidth = 68.dp
        val preferredItemWidth =
            ((maxWidth - horizontalPadding - horizontalPadding - itemSpacing - itemSpacing - spineWidth) / 2f)
                .coerceAtLeast(112.dp)

        content(preferredItemWidth, spineWidth, itemSpacing, horizontalPadding)
    }
}

@Composable
private fun ExpressiveHomeBookCard(
    title: String,
    author: String,
    coverUrl: String?,
    badgeText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth < 108.dp) {
                BookSpineContent(
                    title = title,
                    coverUrl = coverUrl,
                    yearText = badgeText,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveBookCover(
                        title = title,
                        coverUrl = coverUrl,
                        badgeText = badgeText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(204.dp)
                    )
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title.ifBlank { "Unknown Title" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = author.ifBlank { "Unknown Author" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookSpineContent(
    title: String,
    coverUrl: String?,
    yearText: String?,
    useVerticalTitleRail: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                )
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.18f),
                            Color.Black.copy(alpha = 0.38f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(7.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.38f))
        )
        if (useVerticalTitleRail) {
            VerticalSpineTitleRail(
                title = title,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(88.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f))
            ) {
                Text(
                    text = title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                )
            }
        }
        if (!yearText.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 7.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                tonalElevation = 0.dp
            ) {
                Text(
                    text = yearText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun VerticalSpineTitleRail(
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.96f)
                    )
                )
            )
    ) {
        Text(
            text = title.ifBlank { "Untitled" },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .width(220.dp)
                .graphicsLayer { rotationZ = -90f }
                .padding(horizontal = 10.dp)
        )
        Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.34f))
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveBookCover(
    title: String,
    coverUrl: String?,
    badgeText: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        HeroCoverPlaceholder(modifier = Modifier.fillMaxSize())
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverUrl)
                .crossfade(true)
                .build(),
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (!badgeText.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = MaterialShapes.Cookie6Sided.toShape(),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                tonalElevation = 0.dp
            ) {
                Text(
                    badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun BookSignalPill(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        tonalElevation = 0.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
@Composable
private fun OpenLibraryRetryBanner(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Open Library Unavailable",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
            FilledTonalIconButton(onClick = onRetry) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Retry",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Could not load your home shelf",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f),
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
