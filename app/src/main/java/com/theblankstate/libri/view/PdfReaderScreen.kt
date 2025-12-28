package com.theblankstate.libri.view

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    bookId: String,
    title: String? = null,
    author: String? = null,
    coverUrl: String? = null,
    fileUri: String? = null,
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

    var downloadProgress by remember { mutableStateOf(0f) }
    
    // Enhanced Reader States
    var isNightMode by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var bookmarks by remember { 
        mutableStateOf(
            context.getSharedPreferences("bookmarks_$bookId", android.content.Context.MODE_PRIVATE)
                .getStringSet("pages", emptySet())?.map { it.toInt() }?.toSet() ?: emptySet()
        )
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
    LaunchedEffect(bookId, fileUri) {
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
                        // Download PDF from Archive.org
                        val url = URL("https://archive.org/download/$bookId/$bookId.pdf")
                        val connection = url.openConnection()
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

    DisposableEffect(Unit) {
        onDispose {
            pdfRenderer?.close()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title ?: "Reader",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Bookmarks List
                    IconButton(onClick = { showBookmarksDialog = true }) {
                        Icon(Icons.Default.List, "Bookmarks")
                    }

                    // Night Mode Toggle
                    IconButton(onClick = { isNightMode = !isNightMode }) {
                        Icon(
                            imageVector = if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Night Mode"
                        )
                    }
                    
                    IconButton(onClick = { scale = (scale + 0.5f).coerceIn(1f, 4f) }) {
                        Text("+", style = MaterialTheme.typography.headlineMedium)
                    }
                    IconButton(onClick = { scale = (scale - 0.5f).coerceIn(1f, 4f) }) {
                        Text("-", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(if (isNightMode) Color(0xFF121212) else Color.DarkGray)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.width(200.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        text = "Downloading... ${(downloadProgress * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (error != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(onClick = { onBackClick() }) {
                        Text("Go Back")
                    }
                }
            } else {
                pdfRenderer?.let { renderer ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 4f)
                                    // Only pan if zoomed in
                                    if (scale > 1f) {
                                        val maxOffset = (scale - 1f) * 1000f // Approximate bound
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
                                pageIndex = pageIndex,
                                isNightMode = isNightMode,
                                isBookmarked = bookmarks.contains(pageIndex),
                                onToggleBookmark = { toggleBookmark(pageIndex) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    isNightMode: Boolean,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.Default) {
            synchronized(renderer) {
                val page = renderer.openPage(pageIndex)
                val width = page.width * 2 // High quality
                val height = page.height * 2
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // Render with white background for night mode inversion to work correctly
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap = bmp
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
