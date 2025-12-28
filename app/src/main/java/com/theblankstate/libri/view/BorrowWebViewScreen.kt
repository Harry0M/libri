package com.theblankstate.libri.view

import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.theblankstate.libri.data.UserPreferencesRepository
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowWebViewScreen(
    bookId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = UserPreferencesRepository(context)
    
    // Get Open Library session cookie
    val (_, _, olSession) = prefs.getOpenLibrarySession()
    
    // The bookId should be the book's Open Library edition key (e.g., OL9219606M)
    // or a full URL path. The URL should point to the book page where user can borrow.
    // URL format: https://openlibrary.org/books/OL{id}M/{title}
    val decodedBookId = try {
        URLDecoder.decode(bookId, StandardCharsets.UTF_8.toString())
    } catch (e: Exception) {
        bookId
    }
    
    // Construct the correct URL - just use the book page URL
    // Open Library's borrow button is on the book page itself
    val url = if (decodedBookId.startsWith("http")) {
        decodedBookId
    } else if (decodedBookId.startsWith("/")) {
        "https://openlibrary.org$decodedBookId"
    } else {
        "https://openlibrary.org/books/$decodedBookId"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Borrow Book") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                val webView = WebView(ctx)
                val settings: WebSettings = webView.settings
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                
                // Set up WebViewClient to handle navigation within the WebView
                webView.webViewClient = WebViewClient()

                // Set Open Library session cookie if available
                if (olSession != null) {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    
                    // Set session cookie for openlibrary.org
                    cookieManager.setCookie("https://openlibrary.org", "session=$olSession; Domain=.openlibrary.org; Path=/")
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        cookieManager.flush()
                    }
                }

                webView.loadUrl(url)
                webView
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
