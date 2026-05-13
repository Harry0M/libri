package com.theblankstate.libri.view

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.theblankstate.libri.datamodel.BookFormat
import com.theblankstate.libri.view.components.LibriTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextReaderScreen(
    bookId: String,
    title: String? = null,
    author: String? = null,
    fileUri: String? = null,
    downloadUrl: String? = null,
    format: BookFormat = BookFormat.TXT,
    fallbackArchiveId: String? = null,
    onFallbackToArchiveReader: ((String) -> Unit)? = null,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember(bookId, fileUri, downloadUrl) { mutableStateOf(true) }
    var error by remember(bookId, fileUri, downloadUrl) { mutableStateOf<String?>(null) }
    var content by remember(bookId, fileUri, downloadUrl) { mutableStateOf("") }
    var downloadProgress by remember(bookId, fileUri, downloadUrl) { mutableStateOf(0f) }
    var isDarkMode by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(18) }
    var lineHeightMultiplier by remember { mutableStateOf(1.65f) }
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(error, fallbackArchiveId) {
        val archiveId = fallbackArchiveId
        if (error != null && !archiveId.isNullOrBlank() && onFallbackToArchiveReader != null) {
            delay(1200)
            onFallbackToArchiveReader.invoke(archiveId)
        }
    }

    LaunchedEffect(bookId, fileUri, downloadUrl, format) {
        withContext(Dispatchers.IO) {
            try {
                isLoading = true
                error = null
                content = when {
                    !fileUri.isNullOrBlank() -> {
                        context.contentResolver.openInputStream(Uri.parse(fileUri))
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?: throw IllegalStateException("Unable to open local file")
                    }
                    !downloadUrl.isNullOrBlank() -> {
                        val client = OkHttpClient.Builder()
                            .connectTimeout(60, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .build()
                        val request = Request.Builder()
                            .url(downloadUrl)
                            .header("User-Agent", "Libri/1.0 Android (https://github.com/Harry0M/libri)")
                            .build()
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                throw IllegalStateException("Download failed: ${response.code}")
                            }
                            val body = response.body ?: throw IllegalStateException("Empty response")
                            val length = body.contentLength()
                            val bytes = body.bytes()
                            if (length > 0) {
                                downloadProgress = 1f
                            }
                            bytes.toString(Charsets.UTF_8)
                        }
                    }
                    else -> throw IllegalStateException("No readable text source provided")
                }
            } catch (e: Exception) {
                error = e.message ?: "Unable to load this book."
            } finally {
                isLoading = false
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Reading Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column {
                        Text("Text size", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = fontSize.toFloat(),
                            onValueChange = { fontSize = it.toInt() },
                            valueRange = 14f..30f,
                            steps = 7
                        )
                        Text("${fontSize}px", style = MaterialTheme.typography.bodySmall)
                    }
                    Column {
                        Text("Line height", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = lineHeightMultiplier,
                            onValueChange = { lineHeightMultiplier = it },
                            valueRange = 1.2f..2.0f,
                            steps = 7
                        )
                        Text("${"%.1f".format(lineHeightMultiplier)}x", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (showSearch) {
        val searchResults = remember(searchQuery, content, format) {
            content.findTextSnippets(searchQuery)
        }
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("Search in book") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when {
                            searchQuery.isBlank() -> item {
                                Text("Type a word or phrase.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            searchResults.isEmpty() -> item {
                                Text("No matches found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            else -> items(searchResults) { snippet ->
                                ListItem(
                                    headlineContent = {
                                        Text(snippet, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    },
                                    modifier = Modifier.clickable { showSearch = false }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearch = false }) {
                    Text("Done")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            LibriTopAppBar(
                titleContent = {
                    Column {
                        Text(
                            text = title ?: if (format == BookFormat.HTML) "HTML Reader" else "Text Reader",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (!author.isNullOrBlank()) {
                            Text(
                                text = author,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                onBackClick = onBackClick,
                actions = {
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { isDarkMode = !isDarkMode }) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle night mode"
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Reading settings")
                    }
                }
            )
        }
    ) { padding ->
        val background = if (isDarkMode) Color(0xFF121212) else MaterialTheme.colorScheme.surface
        val foreground = if (isDarkMode) Color(0xFFEAE6DD) else MaterialTheme.colorScheme.onSurface

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(background)
        ) {
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
                                modifier = Modifier.width(220.dp)
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                        Text("Opening book...", color = foreground)
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
                        Text(
                            text = error ?: "Unable to open this book.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (!fallbackArchiveId.isNullOrBlank() && onFallbackToArchiveReader != null) {
                            Text(
                                text = "Opening the Internet Archive reader as a fallback...",
                                color = foreground,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            FilledTonalButton(onClick = { onFallbackToArchiveReader.invoke(fallbackArchiveId) }) {
                                Text("Open Archive Reader")
                            }
                        }
                        Button(onClick = onBackClick) {
                            Text("Go Back")
                        }
                    }
                }
                format == BookFormat.HTML -> {
                    HtmlReaderWebView(
                        html = content,
                        isDarkMode = isDarkMode,
                        fontSize = fontSize,
                        lineHeightMultiplier = lineHeightMultiplier
                    )
                }
                else -> {
                    Text(
                        text = content,
                        color = foreground,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * lineHeightMultiplier).sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp, vertical = 24.dp)
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlReaderWebView(
    html: String,
    isDarkMode: Boolean,
    fontSize: Int,
    lineHeightMultiplier: Float
) {
    val backgroundColor = if (isDarkMode) "#121212" else "#FFFFFF"
    val textColor = if (isDarkMode) "#EAE6DD" else "#222222"
    val styledHtml = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body {
                    color: $textColor;
                    background: $backgroundColor;
                    font-family: Georgia, 'Times New Roman', serif;
                    font-size: ${fontSize}px;
                    line-height: $lineHeightMultiplier;
                    padding: 20px;
                    margin: 0;
                }
                img { max-width: 100%; height: auto; }
                a { color: ${if (isDarkMode) "#9CCBFF" else "#0B57D0"}; }
            </style>
        </head>
        <body>$html</body>
        </html>
    """.trimIndent()

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.apply {
                    javaScriptEnabled = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                setBackgroundColor(android.graphics.Color.parseColor(backgroundColor))
            }
        },
        update = { webView ->
            webView.setBackgroundColor(android.graphics.Color.parseColor(backgroundColor))
            webView.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
        }
    )
}

private fun String.findTextSnippets(query: String): List<String> {
    val cleaned = query.trim()
    if (cleaned.length < 2) return emptyList()
    val plain = replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()

    val snippets = mutableListOf<String>()
    var startIndex = 0
    while (snippets.size < 60) {
        val index = plain.indexOf(cleaned, startIndex, ignoreCase = true)
        if (index < 0) break
        val start = (index - 72).coerceAtLeast(0)
        val end = (index + cleaned.length + 120).coerceAtMost(plain.length)
        snippets += plain.substring(start, end).trim()
        startIndex = index + cleaned.length
    }
    return snippets
}
