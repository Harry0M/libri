package com.theblankstate.libri

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.theblankstate.libri.ui.navigation.AppNavHost
import com.theblankstate.libri.ui.theme.LearncomposeTheme
import com.theblankstate.libri.viewModel.BookViewModel

class MainActivity : ComponentActivity() {

    private val bookViewModel: BookViewModel by viewModels()
    private val externalBookUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Install splash screen FIRST — must be before super.onCreate()
        try {
            installSplashScreen()
        } catch (_: Throwable) {
            // Ignore if splash screen API isn't available
        }

        super.onCreate(savedInstanceState)
        externalBookUri.value = extractBookUri(intent)

        // 2. Enable edge-to-edge AFTER super.onCreate() and AFTER splash screen
        //    This ensures the window is properly configured even if the
        //    postSplashScreenTheme reset some window flags.
        enableEdgeToEdge()

        // 3. Explicitly force decor to NOT fit system windows.
        //    This is the definitive edge-to-edge flag. Without this, the system
        //    may add padding for the status bar, causing the blank gap at the top.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            LearncomposeTheme {
                AppNavHost(
                    viewModel = bookViewModel,
                    externalBookUri = externalBookUri.value,
                    onExternalBookUriConsumed = { externalBookUri.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalBookUri.value = extractBookUri(intent)
    }

    @Suppress("DEPRECATION")
    private fun extractBookUri(intent: Intent?): Uri? {
        return when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
    }
}
