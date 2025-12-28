package com.theblankstate.libri

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.theblankstate.libri.ui.navigation.AppNavHost
import com.theblankstate.libri.ui.theme.LearncomposeTheme
import com.theblankstate.libri.viewModel.BookViewModel

class MainActivity : ComponentActivity() {

    private val bookViewModel: BookViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        // Install the platform splash screen to ensure a smooth launch animation
        try {
            installSplashScreen()
        } catch (_: Throwable) {
            // Ignore if splash screen API isn't available on older devices or build config
        }

        setContent {
            LearncomposeTheme {
                AppNavHost(viewModel = bookViewModel)
            }
        }
    }
}




