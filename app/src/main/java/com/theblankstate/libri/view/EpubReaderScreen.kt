package com.theblankstate.libri.view

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

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
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
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
    var showTableOfContents by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
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
                            .header("User-Agent", "ScribeApp/1.0 (Android)")
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
    
    // Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Reading Settings") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Font Size
                    Text("Font Size", style = MaterialTheme.typography.labelLarge)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(onClick = { fontSize = (fontSize - 2).coerceAtLeast(12) }) {
                            Text("A-", style = MaterialTheme.typography.titleLarge)
                        }
                        Text("${fontSize}px", style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { fontSize = (fontSize + 2).coerceAtMost(32) }) {
                            Text("A+", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    
                    HorizontalDivider()
                    
                    // Dark Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark Mode", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { isDarkMode = it }
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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title ?: "EPUB Reader",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (epubContent != null && epubContent!!.chapters.isNotEmpty()) {
                            Text(
                                text = "Chapter ${currentChapterIndex + 1} of ${epubContent!!.chapters.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                    // Table of Contents
                    IconButton(onClick = { showTableOfContents = true }) {
                        Icon(Icons.Default.List, "Table of Contents")
                    }
                    
                    // Bookmark current chapter
                    IconButton(onClick = { toggleBookmark(currentChapterIndex) }) {
                        Icon(
                            imageVector = if (bookmarks.contains(currentChapterIndex)) 
                                Icons.Default.Bookmark 
                            else 
                                Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark"
                        )
                    }
                    
                    // Settings
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        bottomBar = {
            if (epubContent != null && epubContent!!.chapters.size > 1) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Chapter
                        TextButton(
                            onClick = { 
                                if (currentChapterIndex > 0) {
                                    currentChapterIndex--
                                }
                            },
                            enabled = currentChapterIndex > 0
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Previous")
                        }
                        
                        // Progress indicator
                        Text(
                            text = "${currentChapterIndex + 1} / ${epubContent!!.chapters.size}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        // Next Chapter
                        TextButton(
                            onClick = { 
                                if (currentChapterIndex < epubContent!!.chapters.lastIndex) {
                                    currentChapterIndex++
                                }
                            },
                            enabled = currentChapterIndex < epubContent!!.chapters.lastIndex
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(if (isDarkMode) Color(0xFF121212) else Color.White)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (downloadProgress > 0f) {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.width(200.dp)
                            )
                            Text(
                                text = "Downloading... ${(downloadProgress * 100).toInt()}%",
                                color = if (isDarkMode) Color.White else Color.DarkGray
                            )
                        } else {
                            CircularProgressIndicator()
                            Text(
                                text = "Loading EPUB...",
                                color = if (isDarkMode) Color.White else Color.DarkGray
                            )
                        }
                    }
                }
                
                error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBackClick) {
                            Text("Go Back")
                        }
                    }
                }
                
                currentChapterHtml != null -> {
                    EpubWebView(
                        html = currentChapterHtml!!,
                        isDarkMode = isDarkMode,
                        fontSize = fontSize,
                        baseUrl = epubContent?.baseUrl
                    )
                }
                
                else -> {
                    Text(
                        text = "No content available",
                        modifier = Modifier.align(Alignment.Center),
                        color = if (isDarkMode) Color.White else Color.DarkGray
                    )
                }
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
    baseUrl: String? = null
) {
    val backgroundColor = if (isDarkMode) "#121212" else "#FFFFFF"
    val textColor = if (isDarkMode) "#E0E0E0" else "#212121"
    
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
                    font-family: Georgia, 'Times New Roman', serif;
                    font-size: ${fontSize}px;
                    line-height: 1.6;
                    color: $textColor;
                    background-color: $backgroundColor;
                    padding: 16px;
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
            </style>
        </head>
        <body>
            $html
        </body>
        </html>
    """.trimIndent()
    
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        // Handle internal links within EPUB
                        return false
                    }
                }
                settings.apply {
                    javaScriptEnabled = false
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
            webView.setBackgroundColor(android.graphics.Color.parseColor(backgroundColor))
        },
        modifier = Modifier.fillMaxSize()
    )
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
