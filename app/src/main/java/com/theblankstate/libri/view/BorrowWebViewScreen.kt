package com.theblankstate.libri.view

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.theblankstate.libri.data.UserPreferencesRepository
import com.theblankstate.libri.view.components.LibriTopAppBar
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowWebViewScreen(
    bookId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { UserPreferencesRepository(context) }
    val (_, _, olSession) = prefs.getOpenLibrarySession()
    val decodedBookId = try {
        URLDecoder.decode(bookId, StandardCharsets.UTF_8.toString())
    } catch (e: Exception) {
        bookId
    }
    val url = remember(decodedBookId) { buildOpenLibraryBorrowUrl(decodedBookId) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var progress by remember(url) { mutableStateOf(0f) }
    var errorMessage by remember(url) { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
        }
    }

    Scaffold(
        topBar = {
            LibriTopAppBar(
                title = "Borrow Book",
                onBackClick = onBack
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        val settings: WebSettings = this.settings
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        }

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            cookieManager.setAcceptThirdPartyCookies(this, true)
                        }

                        olSession?.let { session ->
                            cookieManager.setCookie(
                                "https://openlibrary.org",
                                "session=$session; Domain=.openlibrary.org; Path=/"
                            )
                            cookieManager.setCookie(
                                "https://www.openlibrary.org",
                                "session=$session; Domain=.openlibrary.org; Path=/"
                            )
                        }

                        prefs.getIASession().second?.forEach { (name, value) ->
                            cookieManager.setCookie(
                                "https://archive.org",
                                "$name=$value; Domain=.archive.org; Path=/"
                            )
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            cookieManager.flush()
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                                if (newProgress >= 100) {
                                    isLoading = false
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                errorMessage = null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean = false

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    errorMessage = error?.description?.toString()
                                        ?: "Unable to load Open Library."
                                    isLoading = false
                                }
                            }
                        }

                        loadUrl(url)
                    }
                },
                update = { currentWebView ->
                    if (currentWebView.url.isNullOrBlank()) {
                        currentWebView.loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            errorMessage?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = {
                                errorMessage = null
                                isLoading = true
                                webView?.loadUrl(url)
                            }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

private fun buildOpenLibraryBorrowUrl(bookIdOrPath: String): String {
    val trimmed = bookIdOrPath.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.startsWith("/books/") || trimmed.startsWith("/works/") -> "https://openlibrary.org$trimmed"
        trimmed.endsWith("M", ignoreCase = true) -> "https://openlibrary.org/books/$trimmed"
        trimmed.endsWith("W", ignoreCase = true) -> "https://openlibrary.org/works/$trimmed"
        else -> "https://openlibrary.org/books/$trimmed"
    }
}
