package com.theblankstate.libri

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.theblankstate.libri.ui.navigation.AppNavHost
import com.theblankstate.libri.ui.theme.LearncomposeTheme
import com.theblankstate.libri.viewModel.BookViewModel

class MainActivity : ComponentActivity() {

    private val bookViewModel: BookViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Install splash screen FIRST — must be before super.onCreate()
        try {
            installSplashScreen()
        } catch (_: Throwable) {
            // Ignore if splash screen API isn't available
        }

        super.onCreate(savedInstanceState)

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
                AppNavHost(viewModel = bookViewModel)
            }
        }
    }
}
