package com.theblankstate.libri.view

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.List
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.theblankstate.libri.view.components.LibriTopAppBar
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    bookId: String,
    title: String? = null,
    author: String? = null,
    coverUrl: String? = null,
    fileUri: String? = null,
    downloadUrl: String? = null,
    fallbackArchiveId: String? = null,
    onFallbackToArchiveReader: ((String) -> Unit)? = null,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val listState = rememberLazyListState()
    val pageCache = remember(bookId) { PdfPageBitmapCache() }
    val rendererActive = remember(bookId, fileUri, downloadUrl) { AtomicBoolean(true) }
    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) }
    }

    var downloadProgress by remember { mutableStateOf(0f) }
    
    // Enhanced Reader States
    var isNightMode by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showPageJumpDialog by remember { mutableStateOf(false) }
    var bookmarks by remember { 
        mutableStateOf(
            context.getSharedPreferences("bookmarks_$bookId", android.content.Context.MODE_PRIVATE)
                .getStringSet("pages", emptySet())?.map { it.toInt() }?.toSet() ?: emptySet()
        )
    }

    LaunchedEffect(pageCount) {
        if (pageCount > 0) {
            val savedPage = context.getSharedPreferences("pdf_progress_$bookId", android.content.Context.MODE_PRIVATE)
                .getInt("page", 0)
                .coerceIn(0, pageCount - 1)
            if (savedPage > 0) {
                listState.scrollToItem(savedPage)
            }
        }
    }

    LaunchedEffect(currentPage, pageCount) {
        if (pageCount > 0) {
            context.getSharedPreferences("pdf_progress_$bookId", android.content.Context.MODE_PRIVATE)
                .edit()
                .putInt("page", currentPage)
                .apply()
        }
    }

    LaunchedEffect(error, fallbackArchiveId) {
        val archiveId = fallbackArchiveId
        if (error != null && !archiveId.isNullOrBlank() && onFallbackToArchiveReader != null) {
            delay(1200)
            onFallbackToArchiveReader.invoke(archiveId)
        }
    }
    
    fun toggleBookmark(page: Int) {
        val newBookmarks = if (bookmarks.contains(page)) {
            bookmarks - page
        } else {
            bookmarks + page
        }
        bookmarks = newBookmarks
        
        context.getSharedPreferences("bookmarks_$bookId", android.content.Context.MODE_PRIVATE)
            .edit()
            .putStringSet("pages", newBookmarks.map { it.toString() }.toSet())
            .apply()
    }

    // Download and initialize PDF
    LaunchedEffect(bookId, fileUri, downloadUrl) {
        rendererActive.set(true)
        withContext(Dispatchers.IO) {
            try {
                if (fileUri != null) {
                    val uri = android.net.Uri.parse(fileUri)
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val renderer = PdfRenderer(pfd)
                        pdfRenderer = renderer
                        pageCount = renderer.pageCount
                        isLoading = false
                    } else {
                        error = "Failed to open file"
                        isLoading = false
                    }
                } else {
                    // Use internal storage "books" directory for persistence
                    val booksDir = File(context.filesDir, "books")
                    if (!booksDir.exists()) booksDir.mkdirs()
                    val file = File(booksDir, "$bookId.pdf")
                    
                    if (!file.exists()) {
                        val url = URL(downloadUrl ?: "https://archive.org/download/$bookId/$bookId.pdf")
                        val connection = url.openConnection()
                        connection.setRequestProperty("User-Agent", "Libri/1.0 Android (https://github.com/Harry0M/libri)")
                        connection.connect()
                        val length = connection.contentLength
                        
                        connection.getInputStream().use { input ->
                            file.outputStream().use { output ->
                                val buffer = ByteArray(8 * 1024)
                                var bytesRead: Int
                                var totalBytesRead = 0L
                                
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead
                                    if (length > 0) {
                                        downloadProgress = totalBytesRead.toFloat() / length.toFloat()
                                    }
                                }
                            }
                        }
                    }
                    
                    val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(fileDescriptor)
                    pdfRenderer = renderer
                    pageCount = renderer.pageCount
                    isLoading = false
                }
            } catch (e: Exception) {
                error = "Failed to load PDF: ${e.message}"
                isLoading = false
            }
        }
    }

    DisposableEffect(bookId, fileUri, downloadUrl) {
        onDispose {
            rendererActive.set(false)
            try {
                pdfRenderer?.let { renderer ->
                    synchronized(renderer) {
                        renderer.close()
                    }
                }
            } catch (_: IllegalStateException) {
                // Page was still open from a background render — safe to ignore,
                // the renderer will be GC'd and the file descriptor closed.
            }
            pageCache.clear()
        }
    }

    LaunchedEffect(currentPage, pageCount, pdfRenderer) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        if (pageCount <= 0) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            ((currentPage - 1)..(currentPage + 2))
                .filter { it in 0 until pageCount && pageCache.get(it) == null }
                .forEach { page ->
                    val rendered = renderPdfPage(renderer, page, rendererActive)
                    if (rendered != null) {
                        pageCache.put(page, rendered)
                    }
                }
        }
    }

    if (showBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            title = { Text("Bookmarks") },
            text = {
                if (bookmarks.isEmpty()) {
                    Text("No bookmarks yet.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(bookmarks.sorted().size) { index ->
                            val page = bookmarks.sorted()[index]
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        listState.scrollToItem(page)
                                        showBookmarksDialog = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Page ${page + 1}")
                                    IconButton(onClick = { toggleBookmark(page) }) {
                                        Icon(Icons.Default.Bookmark, "Remove Bookmark")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookmarksDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showPageJumpDialog && pageCount > 0) {
        var targetPage by remember(showPageJumpDialog, currentPage) { mutableStateOf(currentPage.toFloat()) }
        AlertDialog(
            onDismissRequest = { showPageJumpDialog = false },
            title = { Text("Go to page") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Page ${targetPage.toInt() + 1} of $pageCount",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = targetPage,
                        onValueChange = { targetPage = it },
                        valueRange = 0f..(pageCount - 1).toFloat(),
                        steps = 0
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { listState.animateScrollToItem(targetPage.toInt()) }
                        showPageJumpDialog = false
                    }
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPageJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showChrome by remember { mutableStateOf(true) }

    // Auto-hide chrome after 4 seconds
    LaunchedEffect(showChrome) {
        if (showChrome && !isLoading && error == null) {
            delay(4000)
            showChrome = false
        }
    }

    val bgColor = if (isNightMode) Color(0xFF121212) else Color(0xFF2A2A2A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
    ) {
        // Content layer
        when {
            isLoading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (downloadProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.width(220.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Downloading… ${(downloadProgress * 100).toInt()}%",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        com.theblankstate.libri.view.components.ExpressiveLoadingIndicator(
                            label = "Preparing PDF",
                            color = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }
            }

            error != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.errorContainer,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = error ?: "Unknown error",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    if (!fallbackArchiveId.isNullOrBlank() && onFallbackToArchiveReader != null) {
                        Text(
                            text = "Opening the Internet Archive reader as a fallback…",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        FilledTonalButton(onClick = { onFallbackToArchiveReader.invoke(fallbackArchiveId) }) {
                            Text("Open Archive Reader")
                        }
                    }
                    OutlinedButton(onClick = onBackClick) {
                        Text("Go Back")
                    }
                }
            }

            else -> {
                pdfRenderer?.let { renderer ->
                    // Tap to toggle chrome
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 4f)
                                    if (scale > 1f) {
                                        val maxOffset = (scale - 1f) * 1000f
                                        offset = Offset(
                                            (offset.x + pan.x).coerceIn(-maxOffset, maxOffset),
                                            (offset.y + pan.y).coerceIn(-maxOffset, maxOffset)
                                        )
                                    } else {
                                        offset = Offset.Zero
                                    }
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(pageCount) { pageIndex ->
                            PdfPage(
                                renderer = renderer,
                                rendererActive = rendererActive,
                                pageIndex = pageIndex,
                                isNightMode = isNightMode,
                                isBookmarked = bookmarks.contains(pageIndex),
                                cache = pageCache,
                                onToggleBookmark = { toggleBookmark(pageIndex) }
                            )
                        }
                    }
                }

                // Invisible tap target to toggle chrome
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.TopCenter)
                )
            }
        }

        // ── Top chrome: back + title + action chips ──
        androidx.compose.animation.AnimatedVisibility(
            visible = showChrome,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        ) {
            Surface(
                color = bgColor.copy(alpha = 0.92f),
                tonalElevation = 0.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(
                                text = title ?: "PDF Reader",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            if (pageCount > 0) {
                                Text(
                                    text = "Page ${currentPage + 1} of $pageCount",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val chipColors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        val chipBorder = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = false, borderColor = Color.Transparent
                        )
                        FilterChip(selected = false, onClick = { showPageJumpDialog = true },
                            label = { Text("Go to page") },
                            leadingIcon = { Icon(Icons.Default.List, null, modifier = Modifier.size(16.dp)) },
                            colors = chipColors, border = chipBorder)
                        FilterChip(selected = false, onClick = { showBookmarksDialog = true },
                            label = { Text("Bookmarks") },
                            leadingIcon = { Icon(Icons.Default.Bookmark, null, modifier = Modifier.size(16.dp)) },
                            colors = chipColors, border = chipBorder)
                        FilterChip(
                            selected = bookmarks.contains(currentPage),
                            onClick = {
                                toggleBookmark(currentPage)
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            },
                            label = { Text(if (bookmarks.contains(currentPage)) "Saved" else "Mark") },
                            leadingIcon = { Icon(if (bookmarks.contains(currentPage)) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, modifier = Modifier.size(16.dp)) },
                            colors = chipColors, border = chipBorder)
                    }
                }
            }
        }

        // ── Bottom chrome: page slider with haptic ticks ──
        androidx.compose.animation.AnimatedVisibility(
            visible = showChrome && pageCount > 1,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            Surface(
                color = bgColor.copy(alpha = 0.94f),
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    var sliderValue by remember(currentPage) { mutableStateOf(currentPage.toFloat()) }
                    var lastHapticTick by remember { mutableStateOf(-1) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${sliderValue.toInt() + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )

                        Slider(
                            value = sliderValue,
                            onValueChange = { newVal ->
                                sliderValue = newVal
                                val rounded = newVal.toInt()
                                if (rounded != lastHapticTick) {
                                    lastHapticTick = rounded
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onValueChangeFinished = {
                                val target = sliderValue.toInt().coerceIn(0, pageCount - 1)
                                scope.launch { listState.animateScrollToItem(target) }
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            },
                            valueRange = 0f..(pageCount - 1).toFloat().coerceAtLeast(0f),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )

                        Text(
                            text = "$pageCount",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    // Zoom slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("Zoom", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                        Slider(
                            value = scale,
                            onValueChange = {
                                scale = it
                                if (scale <= 1f) offset = Offset.Zero
                            },
                            valueRange = 1f..4f,
                            steps = 5,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.tertiary,
                                activeTrackColor = MaterialTheme.colorScheme.tertiary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                        Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // ── Floating mini FABs ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (pageCount > 1) 150.dp else 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { isNightMode = !isNightMode },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(
                    if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    "Night mode",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun PdfPage(
    renderer: PdfRenderer,
    rendererActive: AtomicBoolean,
    pageIndex: Int,
    isNightMode: Boolean,
    isBookmarked: Boolean,
    cache: PdfPageBitmapCache,
    onToggleBookmark: () -> Unit
) {
    var bitmap by remember(pageIndex) { mutableStateOf(cache.get(pageIndex)) }

    LaunchedEffect(pageIndex) {
        if (bitmap == null) {
            withContext(Dispatchers.Default) {
                val cached = cache.get(pageIndex)
                if (cached != null) {
                    bitmap = cached
                } else {
                    val rendered = renderPdfPage(renderer, pageIndex, rendererActive)
                    if (rendered != null) {
                        cache.put(pageIndex, rendered)
                        bitmap = rendered
                    }
                }
            }
        }
    }

    bitmap?.let { bmp ->
        Box(modifier = Modifier.fillMaxWidth()) {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                    colorFilter = if (isNightMode) {
                        ColorFilter.colorMatrix(
                            ColorMatrix(
                                floatArrayOf(
                                    -1f, 0f, 0f, 0f, 255f,
                                    0f, -1f, 0f, 0f, 255f,
                                    0f, 0f, -1f, 0f, 255f,
                                    0f, 0f, 0f, 1f, 0f
                                )
                            )
                        )
                    } else null
                )
            }
            
            // Bookmark Icon
            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (isBookmarked) "Remove Bookmark" else "Add Bookmark",
                    tint = if (isNightMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Page Number
            Text(
                text = "${pageIndex + 1}",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    } ?: Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .background(if (isNightMode) Color(0xFF1E1E1E) else Color.White),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

class PdfPageBitmapCache(
    maxSizeBytes: Int = (Runtime.getRuntime().maxMemory() / 10L).coerceAtMost(48L * 1024L * 1024L).toInt()
) {
    private val cache = object : LruCache<Int, Bitmap>(maxSizeBytes) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount
    }

    fun get(pageIndex: Int): Bitmap? = cache.get(pageIndex)

    fun put(pageIndex: Int, bitmap: Bitmap) {
        cache.put(pageIndex, bitmap)
    }

    fun clear() {
        cache.evictAll()
    }
}

private fun renderPdfPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    rendererActive: AtomicBoolean
): Bitmap? {
    synchronized(renderer) {
        if (!rendererActive.get()) return null
        val page = try {
            renderer.openPage(pageIndex)
        } catch (_: IllegalStateException) {
            return null
        }
        try {
            // Cap rendered size to avoid OOM — 2048px max on longest edge is plenty for phones
            val maxDim = 2048
            val scale = if (page.width > page.height) {
                maxDim.toFloat() / page.width
            } else {
                maxDim.toFloat() / page.height
            }.coerceAtMost(2f) // Never exceed 2x native

            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()

            // Try RGB_565 first (2 bytes/pixel, lower memory). Some PDFs with
            // transparency or unusual colour spaces don't support it, so fall
            // back to ARGB_8888 (4 bytes/pixel) if the renderer throws.
            return try {
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            } catch (_: IllegalArgumentException) {
                // Unsupported pixel format for RGB_565 — retry with ARGB_8888
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        } finally {
            page.close()
        }
    }
}
