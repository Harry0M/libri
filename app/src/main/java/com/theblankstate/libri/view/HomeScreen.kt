package com.theblankstate.libri.view

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theblankstate.libri.data.UserPreferencesRepository
import com.theblankstate.libri.datamodel.SubjectWork
import com.theblankstate.libri.datamodel.bookModel
import com.theblankstate.libri.view.components.BookCard
import com.theblankstate.libri.view.components.ExpressiveLoadingIndicator
import com.theblankstate.libri.viewModel.HomeState
import com.theblankstate.libri.viewModel.HomeViewModel
import com.theblankstate.libri.viewModel.HomeViewModelFactory
import java.util.Calendar

@Composable
fun HomeScreen(
    onBookClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onFreeGutenbergBooksClick: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(LocalContext.current.applicationContext as Application))
) {
    val homeState by viewModel.homeState.collectAsState()
    val context = LocalContext.current
    val userPreferencesRepository = remember(context) { UserPreferencesRepository(context) }
    val userName = remember(userPreferencesRepository) {
        userPreferencesRepository.getGoogleUser().second ?: "Book Lover"
    }

    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HomeTopBar(
                onProfileClick = onProfileClick
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

                is HomeState.Success -> {
                    val featuredBooks = state.content.flatMap { it.second }.take(3)
                    val heroMessage = remember(state.content) {
                        state.content.firstOrNull()?.let { (title, books) ->
                            when {
                                title.equals("Continue Reading", ignoreCase = true) ->
                                    "Continue with ${books.firstOrNull()?.title ?: "your current read"}."
                                title.startsWith("Because", ignoreCase = true) ->
                                    "$title. Fresh picks are ready."
                                title.startsWith("More from", ignoreCase = true) ->
                                    "$title is ready."
                                title.contains("Trending", ignoreCase = true) ->
                                    "Trending picks are ready for you."
                                books.isNotEmpty() ->
                                    "Fresh picks from $title are ready."
                                else -> "Your next chapter is ready."
                            }
                        } ?: "Your next chapter is ready."
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        item {
                            HomeHero(
                                greeting = greeting,
                                userName = userName,
                                featuredBooks = featuredBooks,
                                message = heroMessage
                            )
                        }

                        item {
                            HomeActionRail(
                                title = state.gutenbergTitle,
                                subtitle = state.gutenbergSubtitle,
                                onFreeGutenbergBooksClick = onFreeGutenbergBooksClick
                            )
                        }

                        itemsIndexed(
                            state.content,
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
                    }
                }
            }
        }
    }
}

@Composable
fun HomeTopBar(
    onProfileClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, top = 0.dp, end = 16.dp, bottom = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Libri",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Read, collect, continue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = onProfileClick) {
                    Icon(Icons.Default.Person, contentDescription = "Profile")
                }
            }
        }
    }
}

@Composable
private fun HomeHero(
    greeting: String,
    userName: String,
    featuredBooks: List<bookModel>,
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 104.dp)
        ) {
            Text(
                text = "$greeting,",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = userName,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            )
        }

        HeroCoverStack(
            books = featuredBooks,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun BoxScope.HeroCoverStack(
    books: List<bookModel>,
    modifier: Modifier = Modifier
) {
    val covers = books.filter { it.coverUrl != null }.take(3)
    Box(
        modifier = modifier
            .width(112.dp)
            .height(150.dp)
    ) {
        if (covers.isEmpty()) {
            HeroCoverPlaceholder(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 92.dp, height = 132.dp)
            )
        } else {
            covers.forEachIndexed { index, book ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(book.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = book.title,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = ((index - 1) * 18).dp, y = (index * 4).dp)
                        .size(width = 78.dp, height = 120.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )
            }
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
private fun HomeActionRail(
    title: String,
    subtitle: String,
    onFreeGutenbergBooksClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeActionTile(
            title = title,
            subtitle = subtitle,
            icon = {
                Icon(Icons.Default.Explore, contentDescription = null)
            },
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
            onClick = onFreeGutenbergBooksClick
        )
    }
}

@Composable
private fun HomeActionTile(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(96.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
fun HomeSection(
    title: String,
    count: Int,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "$count curated picks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
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
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(books, key = { it.key ?: it.title.hashCode() }) { book ->
            BookCard(
                title = book.title.orEmpty(),
                author = book.authors?.firstOrNull()?.name.orEmpty(),
                coverUrl = book.coverUrl,
                badgeText = book.firstPublishYear?.toString(),
                onClick = { book.key?.let { onBookClick(it) } }
            )
        }
    }
}

@Composable
fun RelatedBookCarousel(
    books: List<bookModel>,
    onBookClick: (String) -> Unit
) {
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(
            books,
            key = { index, book -> "${book.key ?: book.title}-$index" }
        ) { _, book ->
            BookCard(
                title = book.title,
                author = book.author_name?.firstOrNull().orEmpty(),
                coverUrl = book.coverUrl,
                badgeText = book.first_publish_year?.toString(),
                onClick = { book.key?.let { onBookClick(it) } }
            )
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
