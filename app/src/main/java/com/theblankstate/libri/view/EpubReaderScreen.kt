package com.theblankstate.libri.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.theblankstate.libri.view.components.LibriTopAppBar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import androidx.compose.foundation.clickable

/**
 * EPUB Reader Screen
 * 
 * A WebView-based EPUB reader that parses and renders EPUB files.
 * Supports navigation between chapters, dark mode, and basic text customization.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(
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
    val gson = remember { Gson() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }
    
    // EPUB content state
    var epubContent by remember { mutableStateOf<EpubContent?>(null) }
    var currentChapterIndex by remember { mutableStateOf(0) }
    var currentChapterHtml by remember { mutableStateOf<String?>(null) }
    
    // Reader settings
    var isDarkMode by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(18) }
    var lineHeight by remember { mutableStateOf(1.65f) }
    var pagePadding by remember { mutableStateOf(18) }
    var fontFamily by remember { mutableStateOf(ReaderFont.SERIF) }
    var readerTheme by remember { mutableStateOf(ReaderThemeMode.LIGHT) }
    var showTableOfContents by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showHighlightsDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var highlightNote by remember { mutableStateOf("") }
    var pendingHighlightText by remember { mutableStateOf("") }
    var highlights by remember {
        mutableStateOf(loadReaderHighlights(context, bookId, gson))
    }
    
    // Bookmarks
    var bookmarks by remember { 
        mutableStateOf(
            context.getSharedPreferences("epub_bookmarks_$bookId", Context.MODE_PRIVATE)
                .getStringSet("chapters", emptySet())?.map { it.toInt() }?.toSet() ?: emptySet()
        )
    }
    
    fun toggleBookmark(chapter: Int) {
        val newBookmarks = if (bookmarks.contains(chapter)) {
            bookmarks - chapter
        } else {
            bookmarks + chapter
        }
        bookmarks = newBookmarks
        
        context.getSharedPreferences("epub_bookmarks_$bookId", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("chapters", newBookmarks.map { it.toString() }.toSet())
            .apply()
    }
    
    // Save reading progress
    fun saveProgress() {
        context.getSharedPreferences("epub_progress_$bookId", Context.MODE_PRIVATE)
            .edit()
            .putInt("chapter", currentChapterIndex)
            .apply()
    }
    
    // Load saved progress
    fun loadProgress(): Int {
        return context.getSharedPreferences("epub_progress_$bookId", Context.MODE_PRIVATE)
            .getInt("chapter", 0)
    }

    fun saveHighlights(updated: List<ReaderHighlight>) {
        highlights = updated
        persistReaderHighlights(context, bookId, gson, updated)
    }

    fun requestSelectedText(onText: (String) -> Unit) {
        webView?.evaluateJavascript("window.getSelection ? window.getSelection().toString() : ''") { result ->
            val selected = runCatching {
                gson.fromJson(result, String::class.java)
            }.getOrNull().orEmpty().trim()
            onText(selected)
        } ?: onText("")
    }

    fun startHighlightFlow() {
        requestSelectedText { selected ->
            if (selected.isBlank()) {
                Toast.makeText(context, "Select text in the page first", Toast.LENGTH_SHORT).show()
            } else {
                pendingHighlightText = selected.take(500)
                highlightNote = ""
                showAddNoteDialog = true
            }
        }
    }

    fun defineSelectedText() {
        requestSelectedText { selected ->
            if (selected.isBlank()) {
                Toast.makeText(context, "Select a word first", Toast.LENGTH_SHORT).show()
            } else {
                openDictionary(context, selected)
            }
        }
    }

    LaunchedEffect(error, fallbackArchiveId) {
        val archiveId = fallbackArchiveId
        if (error != null && !archiveId.isNullOrBlank() && onFallbackToArchiveReader != null) {
            delay(1200)
            onFallbackToArchiveReader.invoke(archiveId)
        }
    }
    
    // Load and parse EPUB
    LaunchedEffect(bookId, fileUri, downloadUrl) {
        withContext(Dispatchers.IO) {
            try {
                val epubFile: File
                
                if (fileUri != null) {
                    // Load from local file
                    val uri = Uri.parse(fileUri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val booksDir = File(context.filesDir, "epub_books")
                        if (!booksDir.exists()) booksDir.mkdirs()
                        epubFile = File(booksDir, "$bookId.epub")
                        
                        if (!epubFile.exists()) {
                            epubFile.outputStream().use { output ->
                                inputStream.copyTo(output)
                            }
                        }
                        inputStream.close()
                    } else {
                        error = "Failed to open file"
                        isLoading = false
                        return@withContext
                    }
                } else if (downloadUrl != null) {
                    // Download EPUB
                    val booksDir = File(context.filesDir, "epub_books")
                    if (!booksDir.exists()) booksDir.mkdirs()
                    epubFile = File(booksDir, "$bookId.epub")
                    
                    if (!epubFile.exists()) {
                        val client = OkHttpClient.Builder()
                            .connectTimeout(60, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .build()
                        
                        val request = Request.Builder()
                            .url(downloadUrl)
                            .header("User-Agent", "Libri/1.0 (Android)")
                            .build()
                        
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                error = "Failed to download: ${response.code}"
                                isLoading = false
                                return@withContext
                            }
                            
                            val body = response.body
                            val contentLength = body?.contentLength() ?: -1
                            val inputStream = body?.byteStream() ?: run {
                                error = "Empty response"
                                isLoading = false
                                return@withContext
                            }
                            
                            epubFile.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                var totalBytesRead = 0L
                                
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead
                                    if (contentLength > 0) {
                                        downloadProgress = totalBytesRead.toFloat() / contentLength.toFloat()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    error = "No file or download URL provided"
                    isLoading = false
                    return@withContext
                }
                
                // Parse EPUB
                val content = parseEpub(epubFile)
                epubContent = content
                
                // Load saved progress
                val savedChapter = loadProgress()
                currentChapterIndex = savedChapter.coerceIn(0, content.chapters.lastIndex.coerceAtLeast(0))
                
                // Load first chapter
                if (content.chapters.isNotEmpty()) {
                    currentChapterHtml = content.chapters[currentChapterIndex].content
                }
                
                isLoading = false
            } catch (e: Exception) {
                e.printStackTrace()
                error = "Failed to load EPUB: ${e.message}"
                isLoading = false
            }
        }
    }
    
    // Update chapter content when index changes
    LaunchedEffect(currentChapterIndex, epubContent) {
        epubContent?.let { content ->
            if (currentChapterIndex in content.chapters.indices) {
                currentChapterHtml = content.chapters[currentChapterIndex].content
                saveProgress()
            }
        }
    }
    
    // Table of Contents Dialog
    if (showTableOfContents && epubContent != null) {
        AlertDialog(
            onDismissRequest = { showTableOfContents = false },
            title = { Text("Table of Contents") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(epubContent!!.chapters.size) { index ->
                        val chapter = epubContent!!.chapters[index]
                        val isBookmarked = bookmarks.contains(index)
                        
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = chapter.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (index == currentChapterIndex) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingContent = {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                if (isBookmarked) {
                                    Icon(
                                        Icons.Default.Bookmark,
                                        contentDescription = "Bookmarked",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentChapterIndex = index
                                    showTableOfContents = false
                                }
                                .background(
                                    if (index == currentChapterIndex) 
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else 
                                        Color.Transparent
                                )
                        )
                        
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTableOfContents = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showSearchDialog && epubContent != null) {
        val results = remember(searchQuery, epubContent) {
            epubContent!!.chapters.search(searchQuery)
        }
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search in book") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        label = { Text("Search") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when {
                            searchQuery.isBlank() -> item {
                                Text(
                                    "Type a word or phrase.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            results.isEmpty() -> item {
                                Text(
                                    "No matches found.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            else -> items(results.size) { index ->
                                val result = results[index]
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            result.chapterTitle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            result.snippet,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingContent = {
                                        Text(
                                            "${result.chapterIndex + 1}",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        currentChapterIndex = result.chapterIndex
                                        showSearchDialog = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (showHighlightsDialog) {
        AlertDialog(
            onDismissRequest = { showHighlightsDialog = false },
            title = { Text("Highlights & Notes") },
            text = {
                if (highlights.isEmpty()) {
                    Text("No highlights yet. Select text in the reader, then tap the highlighter.")
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(highlights.size) { index ->
                            val highlight = highlights[index]
                            ListItem(
                                headlineContent = {
                                    Text(
                                        highlight.text,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    val note = highlight.note.takeIf { it.isNotBlank() } ?: "Chapter ${highlight.chapterIndex + 1}"
                                    Text(note, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                },
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            saveHighlights(highlights.filterNot { it.id == highlight.id })
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete highlight")
                                    }
                                },
                                modifier = Modifier.clickable {
                                    currentChapterIndex = highlight.chapterIndex
                                    showHighlightsDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHighlightsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showAddNoteDialog) {
        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            title = { Text("Save Highlight") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        pendingHighlightText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    OutlinedTextField(
                        value = highlightNote,
                        onValueChange = { highlightNote = it },
                        label = { Text("Note") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val highlight = ReaderHighlight(
                            id = System.currentTimeMillis().toString(),
                            chapterIndex = currentChapterIndex,
                            text = pendingHighlightText,
                            note = highlightNote.trim(),
                            createdAt = System.currentTimeMillis()
                        )
                        saveHighlights(highlights + highlight)
                        showAddNoteDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNoteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Reading Settings") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Font Size", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = { fontSize = it.toInt() },
                        valueRange = 12f..32f,
                        steps = 9
                    )
                    Text("${fontSize}px", style = MaterialTheme.typography.bodyLarge)

                    Text("Line Height", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = lineHeight,
                        onValueChange = { lineHeight = it },
                        valueRange = 1.2f..2.2f,
                        steps = 9
                    )
                    Text("${"%.1f".format(lineHeight)}x", style = MaterialTheme.typography.bodyLarge)

                    Text("Page Padding", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = pagePadding.toFloat(),
                        onValueChange = { pagePadding = it.toInt() },
                        valueRange = 10f..34f,
                        steps = 5
                    )

                    Text("Font", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderFont.entries.forEach { option ->
                            FilterChip(
                                selected = fontFamily == option,
                                onClick = { fontFamily = option },
                                label = { Text(option.label) }
                            )
                        }
                    }

                    Text("Theme", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderThemeMode.entries.forEach { option ->
                            FilterChip(
                                selected = readerTheme == option,
                                onClick = {
                                    readerTheme = option
                                    isDarkMode = option == ReaderThemeMode.DARK
                                },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    
                    HorizontalDivider()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark Mode", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = readerTheme == ReaderThemeMode.DARK || isDarkMode,
                            onCheckedChange = {
                                isDarkMode = it
                                readerTheme = if (it) ReaderThemeMode.DARK else ReaderThemeMode.LIGHT
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Done")
                }
}
        )
    }
    
    val isReaderDark = readerTheme == ReaderThemeMode.DARK || isDarkMode
    val totalChapters = epubContent?.chapters?.size ?: 0

    // Immersive chrome auto-hide state
    var showChrome by remember { mutableStateOf(true) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Auto-hide chrome after 4 seconds of inactivity
    LaunchedEffect(showChrome) {
        if (showChrome && !isLoading && error == null) {
            delay(4000)
            showChrome = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(readerTheme.backgroundColor(isReaderDark))
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = readerTheme.textColor(isReaderDark)
                        )
                    } else {
                        com.theblankstate.libri.view.components.ExpressiveLoadingIndicator(
                            label = "Preparing your read",
                            color = if (isReaderDark) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.primary
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
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = error ?: "Unknown error",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    if (!fallbackArchiveId.isNullOrBlank() && onFallbackToArchiveReader != null) {
                        Text(
                            text = "Opening the Internet Archive reader as a fallback…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = readerTheme.textColor(isReaderDark),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

            currentChapterHtml != null -> {
                // Tap content area to toggle chrome
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) { showChrome = !showChrome }
                ) {
                    EpubWebView(
                        html = currentChapterHtml!!,
                        isDarkMode = isReaderDark,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        pagePadding = pagePadding,
                        fontFamily = fontFamily,
                        readerTheme = readerTheme,
                        searchQuery = searchQuery,
                        highlights = highlights.filter { it.chapterIndex == currentChapterIndex },
                        baseUrl = epubContent?.baseUrl,
                        onWebViewReady = { webView = it }
                    )
                }
            }

            else -> {
                Text(
                    text = "No content available",
                    modifier = Modifier.align(Alignment.Center),
                    color = readerTheme.textColor(isReaderDark)
                )
            }
        }

        // ── Top chrome: back button + title + FAB options ──
        androidx.compose.animation.AnimatedVisibility(
            visible = showChrome,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Surface(
                color = readerTheme.backgroundColor(isReaderDark).copy(alpha = 0.92f),
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
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = readerTheme.textColor(isReaderDark)
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = title ?: "EPUB Reader",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                color = readerTheme.textColor(isReaderDark)
                            )
                            if (totalChapters > 0) {
                                val chapterName = epubContent?.chapters?.getOrNull(currentChapterIndex)?.title
                                Text(
                                    text = chapterName ?: "Chapter ${currentChapterIndex + 1} of $totalChapters",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = readerTheme.textColor(isReaderDark).copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Scrollable action chip row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val chipColors = FilterChipDefaults.filterChipColors(
                            containerColor = if (isReaderDark)
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            else
                                MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        val chipBorder = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = false,
                            borderColor = Color.Transparent
                        )
                        FilterChip(selected = false, onClick = { showSearchDialog = true }, label = { Text("Search") },
                            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp)) },
                            colors = chipColors, border = chipBorder)
                        FilterChip(selected = false, onClick = { showTableOfContents = true }, label = { Text("TOC") },
                            leadingIcon = { Icon(Icons.Default.List, null, modifier = Modifier.size(16.dp)) },
                            colors = chipColors, border = chipBorder)
                        FilterChip(
                            selected = bookmarks.contains(currentChapterIndex),
                            onClick = {
                                toggleBookmark(currentChapterIndex)
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            },
                            label = { Text(if (bookmarks.contains(currentChapterIndex)) "Saved" else "Mark") },
                            leadingIcon = { Icon(if (bookmarks.contains(currentChapterIndex)) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, modifier = Modifier.size(16.dp)) },
                            colors = chipColors, border = chipBorder)
                        FilterChip(selected = false, onClick = ::startHighlightFlow, label = { Text("Highlight") },
                            leadingIcon = { Icon(Icons.Default.BorderColor, null, modifier = Modifier.size(16.dp)) },
                            colors = chipColors, border = chipBorder)
                        FilterChip(selected = false, onClick = ::defineSelectedText, label = { Text("Define") },
                            leadingIcon = { Icon(Icons.Default.Translate, null, modifier = Modifier.size(16.dp)) },
                            colors = chipColors, border = chipBorder)
                    }
                }
            }
        }

        // ── Bottom chrome: chapter slider with haptic ticks ──
        androidx.compose.animation.AnimatedVisibility(
            visible = showChrome && totalChapters > 1,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Surface(
                color = readerTheme.backgroundColor(isReaderDark).copy(alpha = 0.94f),
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = epubContent?.chapters?.getOrNull(currentChapterIndex)?.title ?: "Chapter ${currentChapterIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = readerTheme.textColor(isReaderDark).copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                if (currentChapterIndex > 0) {
                                    currentChapterIndex--
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                }
                            },
                            enabled = currentChapterIndex > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, "Previous chapter", modifier = Modifier.size(20.dp))
                        }

                        var sliderValue by remember(currentChapterIndex) { mutableStateOf(currentChapterIndex.toFloat()) }
                        var lastHapticTick by remember { mutableStateOf(-1) }

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
                                val target = sliderValue.toInt().coerceIn(0, totalChapters - 1)
                                currentChapterIndex = target
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            },
                            valueRange = 0f..(totalChapters - 1).toFloat().coerceAtLeast(0f),
                            steps = (totalChapters - 2).coerceAtLeast(0),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = if (isReaderDark)
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )

                        FilledTonalIconButton(
                            onClick = {
                                if (currentChapterIndex < totalChapters - 1) {
                                    currentChapterIndex++
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                }
                            },
                            enabled = currentChapterIndex < totalChapters - 1,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, "Next chapter", modifier = Modifier.size(20.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ch. ${currentChapterIndex + 1}", style = MaterialTheme.typography.labelSmall,
                            color = readerTheme.textColor(isReaderDark).copy(alpha = 0.5f))
                        Text("${((currentChapterIndex + 1).toFloat() / totalChapters * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = readerTheme.textColor(isReaderDark).copy(alpha = 0.5f))
                        Text("Ch. $totalChapters", style = MaterialTheme.typography.labelSmall,
                            color = readerTheme.textColor(isReaderDark).copy(alpha = 0.5f))
                    }
                }
            }
        }

        // ── Floating mini FABs (always visible, bottom-right) ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (totalChapters > 1) 130.dp else 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { showHighlightsDialog = true },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Icon(Icons.Default.StickyNote2, "Notes", modifier = Modifier.size(20.dp))
            }
            SmallFloatingActionButton(
                onClick = { showSettingsDialog = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(Icons.Default.Tune, "Settings", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EpubWebView(
    html: String,
    isDarkMode: Boolean,
    fontSize: Int,
    lineHeight: Float,
    pagePadding: Int,
    fontFamily: ReaderFont,
    readerTheme: ReaderThemeMode,
    searchQuery: String,
    highlights: List<ReaderHighlight>,
    baseUrl: String? = null,
    onWebViewReady: (WebView) -> Unit
) {
    val backgroundColor = readerTheme.backgroundHex(isDarkMode)
    val textColor = readerTheme.textHex(isDarkMode)
    val highlightTermsJson = remember(highlights) {
        Gson().toJson(highlights.map { it.text }.filter { it.isNotBlank() })
    }
    val safeHtml = remember(html) { sanitizeReaderHtml(html) }
    
    val styledHtml = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * {
                    box-sizing: border-box;
                }
                body {
                    font-family: ${fontFamily.cssStack};
                    font-size: ${fontSize}px;
                    line-height: $lineHeight;
                    color: $textColor;
                    background-color: $backgroundColor;
                    padding: ${pagePadding}px;
                    margin: 0;
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                }
                img {
                    max-width: 100%;
                    height: auto;
                    display: block;
                    margin: 16px auto;
                }
                h1, h2, h3, h4, h5, h6 {
                    color: $textColor;
                    line-height: 1.3;
                    margin-top: 1.5em;
                    margin-bottom: 0.5em;
                }
                h1 { font-size: 1.5em; }
                h2 { font-size: 1.3em; }
                h3 { font-size: 1.2em; }
                p {
                    margin: 1em 0;
                    text-align: justify;
                }
                a {
                    color: ${if (isDarkMode) "#90CAF9" else "#1976D2"};
                }
                blockquote {
                    border-left: 3px solid ${if (isDarkMode) "#555" else "#CCC"};
                    margin: 1em 0;
                    padding-left: 1em;
                    font-style: italic;
                }
                pre, code {
                    background-color: ${if (isDarkMode) "#1E1E1E" else "#F5F5F5"};
                    padding: 2px 4px;
                    border-radius: 4px;
                    font-family: monospace;
                    font-size: 0.9em;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin: 1em 0;
                }
                th, td {
                    border: 1px solid ${if (isDarkMode) "#444" else "#DDD"};
                    padding: 8px;
                    text-align: left;
                }
                mark.libri-highlight {
                    background: ${if (isDarkMode) "#5B4A12" else "#FFF2A8"};
                    color: inherit;
                    border-radius: 4px;
                    padding: 0 2px;
                }
            </style>
        </head>
        <body>
            $safeHtml
            <script>
                (function() {
                    const terms = $highlightTermsJson;
                    function escapeRegExp(value) {
                        return value.replace(/[.*+?^${'$'}{}()|[\]\\]/g, '\\${'$'}&');
                    }
                    function highlightTerm(term) {
                        if (!term || term.length < 2) return;
                        const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
                        const nodes = [];
                        while (walker.nextNode()) nodes.push(walker.currentNode);
                        const pattern = new RegExp(escapeRegExp(term), 'i');
                        nodes.forEach(function(node) {
                            if (!pattern.test(node.nodeValue)) return;
                            const span = document.createElement('span');
                            span.innerHTML = node.nodeValue.replace(pattern, function(match) {
                                return '<mark class="libri-highlight">' + match + '</mark>';
                            });
                            node.parentNode.replaceChild(span, node);
                        });
                    }
                    terms.slice(0, 100).forEach(highlightTerm);
                })();
            </script>
        </body>
        </html>
    """.trimIndent()
    
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                onWebViewReady(this)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (searchQuery.isNotBlank()) {
                            view?.findAllAsync(searchQuery)
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        // Handle internal links within EPUB
                        return false
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }
                setBackgroundColor(android.graphics.Color.parseColor(backgroundColor))
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                baseUrl ?: "file:///android_asset/",
                styledHtml,
                "text/html",
                "UTF-8",
                null
            )
            if (searchQuery.isNotBlank()) {
                webView.postDelayed({ webView.findAllAsync(searchQuery) }, 250)
            }
            webView.setBackgroundColor(android.graphics.Color.parseColor(backgroundColor))
        },
        modifier = Modifier.fillMaxSize()
    )
}

private enum class ReaderFont(val label: String, val cssStack: String) {
    SERIF("Serif", "Georgia, 'Times New Roman', serif"),
    SANS("Sans", "Arial, Helvetica, sans-serif"),
    DYSLEXIC("Readable", "'Trebuchet MS', Verdana, sans-serif")
}

private enum class ReaderThemeMode(val label: String) {
    LIGHT("Light"),
    SEPIA("Sepia"),
    DARK("Dark");

    fun backgroundColor(isDark: Boolean): Color {
        return when {
            this == DARK || isDark -> Color(0xFF121212)
            this == SEPIA -> Color(0xFFF4ECD8)
            else -> Color.White
        }
    }

    fun textColor(isDark: Boolean): Color {
        return when {
            this == DARK || isDark -> Color(0xFFEAE6DD)
            this == SEPIA -> Color(0xFF3B2F24)
            else -> Color(0xFF202124)
        }
    }

    fun backgroundHex(isDark: Boolean): String {
        return when {
            this == DARK || isDark -> "#121212"
            this == SEPIA -> "#F4ECD8"
            else -> "#FFFFFF"
        }
    }

    fun textHex(isDark: Boolean): String {
        return when {
            this == DARK || isDark -> "#EAE6DD"
            this == SEPIA -> "#3B2F24"
            else -> "#202124"
        }
    }
}

data class ReaderHighlight(
    val id: String,
    val chapterIndex: Int,
    val text: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

private data class EpubSearchResult(
    val chapterIndex: Int,
    val chapterTitle: String,
    val snippet: String
)

private fun List<EpubChapter>.search(query: String): List<EpubSearchResult> {
    val cleaned = query.trim()
    if (cleaned.length < 2) return emptyList()
    return mapIndexedNotNull { index, chapter ->
        val plain = chapter.content.toPlainReaderText()
        val matchIndex = plain.indexOf(cleaned, ignoreCase = true)
        if (matchIndex < 0) {
            null
        } else {
            val start = (matchIndex - 64).coerceAtLeast(0)
            val end = (matchIndex + cleaned.length + 96).coerceAtMost(plain.length)
            EpubSearchResult(
                chapterIndex = index,
                chapterTitle = chapter.title,
                snippet = plain.substring(start, end).trim()
            )
        }
    }.take(80)
}

private fun String.toPlainReaderText(): String {
    return replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun sanitizeReaderHtml(html: String): String {
    return html
        .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("\\son\\w+\\s*=\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\son\\w+\\s*=\\s*'[^']*'", RegexOption.IGNORE_CASE), "")
}

private fun loadReaderHighlights(context: Context, bookId: String, gson: Gson): List<ReaderHighlight> {
    val json = context.getSharedPreferences("reader_highlights", Context.MODE_PRIVATE)
        .getString("epub_$bookId", null)
        ?: return emptyList()
    return runCatching {
        val type = object : TypeToken<List<ReaderHighlight>>() {}.type
        gson.fromJson<List<ReaderHighlight>>(json, type)
    }.getOrDefault(emptyList())
}

private fun persistReaderHighlights(
    context: Context,
    bookId: String,
    gson: Gson,
    highlights: List<ReaderHighlight>
) {
    context.getSharedPreferences("reader_highlights", Context.MODE_PRIVATE)
        .edit()
        .putString("epub_$bookId", gson.toJson(highlights))
        .apply()
}

private fun openDictionary(context: Context, selectedText: String) {
    val query = selectedText
        .trim()
        .split(Regex("\\s+"))
        .take(4)
        .joinToString(" ")
    if (query.isBlank()) return

    val defineIntent = Intent(Intent.ACTION_DEFINE).apply {
        putExtra(Intent.EXTRA_TEXT, query)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fallbackIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/search?q=${Uri.encode("define $query")}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching {
        context.startActivity(defineIntent)
    }.recoverCatching {
        context.startActivity(fallbackIntent)
    }
}

/**
 * EPUB Content data class
 */
data class EpubContent(
    val title: String?,
    val author: String?,
    val chapters: List<EpubChapter>,
    val baseUrl: String? = null
)

data class EpubChapter(
    val id: String,
    val title: String,
    val href: String,
    val content: String
)

/**
 * Parse an EPUB file and extract its content
 */
private fun parseEpub(epubFile: File): EpubContent {
    val zipFile = ZipFile(epubFile)
    val chapters = mutableListOf<EpubChapter>()
    var bookTitle: String? = null
    var bookAuthor: String? = null
    
    try {
        // Find container.xml to get the OPF file location
        val containerEntry = zipFile.getEntry("META-INF/container.xml")
        val containerXml = containerEntry?.let { zipFile.getInputStream(it).bufferedReader().readText() }
        
        // Parse container.xml to find OPF location
        val opfPath = containerXml?.let { parseContainerXml(it) } ?: "OEBPS/content.opf"
        val opfDir = opfPath.substringBeforeLast("/", "")
        
        // Read and parse OPF file
        val opfEntry = zipFile.getEntry(opfPath)
        val opfContent = opfEntry?.let { zipFile.getInputStream(it).bufferedReader().readText() }
        
        if (opfContent != null) {
            val opfData = parseOpfFile(opfContent)
            bookTitle = opfData.title
            bookAuthor = opfData.author
            
            // Get spine order (reading order of chapters)
            val spineItems = opfData.spine
            val manifest = opfData.manifest
            
            // Load chapters in spine order
            for ((index, spineItem) in spineItems.withIndex()) {
                val manifestItem = manifest[spineItem]
                if (manifestItem != null) {
                    val href = manifestItem.href
                    val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
                    
                    val chapterEntry = zipFile.getEntry(fullPath)
                    if (chapterEntry != null) {
                        val content = zipFile.getInputStream(chapterEntry).bufferedReader().readText()
                        val cleanedContent = extractBodyContent(content)
                        val chapterTitle = extractChapterTitle(content) ?: manifestItem.title ?: "Chapter ${index + 1}"
                        
                        chapters.add(
                            EpubChapter(
                                id = spineItem,
                                title = chapterTitle,
                                href = href,
                                content = cleanedContent
                            )
                        )
                    }
                }
            }
        }
    } finally {
        zipFile.close()
    }
    
    return EpubContent(
        title = bookTitle,
        author = bookAuthor,
        chapters = chapters,
        baseUrl = "file://${epubFile.absolutePath}"
    )
}

private fun parseContainerXml(xml: String): String? {
    try {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                val fullPath = parser.getAttributeValue(null, "full-path")
                if (fullPath != null) return fullPath
            }
            eventType = parser.next()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

data class OpfData(
    val title: String?,
    val author: String?,
    val manifest: Map<String, ManifestItem>,
    val spine: List<String>
)

data class ManifestItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val title: String? = null
)

private fun parseOpfFile(opfContent: String): OpfData {
    val manifest = mutableMapOf<String, ManifestItem>()
    val spine = mutableListOf<String>()
    var title: String? = null
    var author: String? = null
    
    try {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(opfContent))
        
        var eventType = parser.eventType
        var currentTag = ""
        var inMetadata = false
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    
                    when (currentTag) {
                        "metadata" -> inMetadata = true
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            val mediaType = parser.getAttributeValue(null, "media-type")
                            
                            if (id != null && href != null && mediaType != null) {
                                if (mediaType.contains("html") || mediaType.contains("xml")) {
                                    manifest[id] = ManifestItem(id, href, mediaType)
                                }
                            }
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (idref != null) {
                                spine.add(idref)
                            }
                        }
                    }
                }
                
                XmlPullParser.TEXT -> {
                    if (inMetadata) {
                        when (currentTag) {
                            "title", "dc:title" -> {
                                if (title == null) title = parser.text?.trim()
                            }
                            "creator", "dc:creator" -> {
                                if (author == null) author = parser.text?.trim()
                            }
                        }
                    }
                }
                
                XmlPullParser.END_TAG -> {
                    if (parser.name == "metadata") {
                        inMetadata = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    return OpfData(title, author, manifest, spine)
}

private fun extractBodyContent(html: String): String {
    // Extract content between <body> tags
    val bodyRegex = Regex("<body[^>]*>(.*?)</body>", RegexOption.DOT_MATCHES_ALL)
    val match = bodyRegex.find(html)
    return match?.groupValues?.get(1)?.trim() ?: html
}

private fun extractChapterTitle(html: String): String? {
    // Try to extract title from <title> tag
    val titleRegex = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    val titleMatch = titleRegex.find(html)
    val title = titleMatch?.groupValues?.get(1)?.trim()
    if (!title.isNullOrBlank()) return title
    
    // Try to extract from first <h1> or <h2>
    val h1Regex = Regex("<h[12][^>]*>(.*?)</h[12]>", RegexOption.DOT_MATCHES_ALL)
    val h1Match = h1Regex.find(html)
    val h1Title = h1Match?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()
    if (!h1Title.isNullOrBlank()) return h1Title
    
    return null
}
