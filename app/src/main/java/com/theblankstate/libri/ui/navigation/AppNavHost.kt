package com.theblankstate.libri.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.theblankstate.libri.data.UserPreferencesRepository
import com.theblankstate.libri.view.AdvancedSearchScreen
import com.theblankstate.libri.view.BookDetailScreen
import com.theblankstate.libri.view.BookSearchScreen
import com.theblankstate.libri.view.BorrowWebViewScreen
import com.theblankstate.libri.view.DownloadsScreen
import com.theblankstate.libri.view.EditionsListScreen
import com.theblankstate.libri.view.HomeScreen
import com.theblankstate.libri.view.OnboardingScreen
import com.theblankstate.libri.view.PdfReaderScreen
import com.theblankstate.libri.view.ReaderScreen
import com.theblankstate.libri.view.EpubReaderScreen
import com.theblankstate.libri.view.GutenbergBrowseScreen
import com.theblankstate.libri.view.GutenbergBookDetailScreen
import com.theblankstate.libri.view.ShelvesScreen
import com.theblankstate.libri.view.ShelfDetailScreen
import com.theblankstate.libri.view.SplashScreen
import com.theblankstate.libri.viewModel.BookViewModel
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import com.theblankstate.libri.view.LoginScreen
import com.theblankstate.libri.view.GoogleLoginScreen
import com.theblankstate.libri.view.ProfileSetupScreen
import com.theblankstate.libri.view.UserProfileScreen
import com.theblankstate.libri.view.EditPreferencesScreen
import com.theblankstate.libri.view.OpenLibraryLoginScreen
import com.theblankstate.libri.view.OpenSourceLicensesScreen
import com.theblankstate.libri.viewModel.AuthViewModel
import com.theblankstate.libri.viewModel.OpenLibraryViewModel
import com.theblankstate.libri.view.LibraryScreen
import com.theblankstate.libri.view.LibraryBookDetailScreen
import com.theblankstate.libri.view.components.BottomNavigationBar
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.layout.fillMaxSize
import com.theblankstate.libri.ui.theme.MotionTokens
import com.theblankstate.libri.viewModel.LibraryViewModel
import com.theblankstate.libri.data.LibraryRepository
import com.theblankstate.libri.datamodel.BookFormat

@Composable
fun AppNavHost(
    viewModel: BookViewModel
) {
    val context = LocalContext.current
    val userPreferencesRepository = remember { UserPreferencesRepository(context) }

    
    // Determine start destination based on Google login and onboarding status
    val googleUser = userPreferencesRepository.getGoogleUser()
    val isGoogleLoggedIn = googleUser.first != null
    val isOnboardingCompleted = userPreferencesRepository.isOnboardingCompleted()
    val isUserLoggedIn = isGoogleLoggedIn
    
    val navController = rememberNavController()
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route
    val currentTopLevelRoute = currentRoute?.substringBefore("/")
    val topLevelRoutes = remember { setOf("home", "library", "search", "profile") }
    val showBottomBar = currentTopLevelRoute in topLevelRoutes
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo("home") {
                saveState = true
                inclusive = false
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.fillMaxSize()
        ) {
        composable("splash") {
            SplashScreen(
                onSplashFinished = {
                    val actualStartDestination = when {
                        !isGoogleLoggedIn -> "googleLogin"
                        !isOnboardingCompleted -> "profileSetup"
                        else -> "home"
                    }
                    navController.navigate(actualStartDestination) {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }
        composable("googleLogin") {
            GoogleLoginScreen(
                onLoginSuccess = {
                    // New user - go through setup flow
                    navController.navigate("profileSetup") {
                        popUpTo("googleLogin") { inclusive = true }
                    }
                },
                onExistingUserLogin = {
                    // Existing user with completed profile - go directly to home
                    navController.navigate("home") {
                        popUpTo("googleLogin") { inclusive = true }
                    }
                }
            )
        }
        composable("profileSetup") {
            ProfileSetupScreen(
                onProfileComplete = {
                    navController.navigate("openLibraryConnect") {
                        popUpTo("profileSetup") { inclusive = true }
                    }
                }
            )
        }
        composable("openLibraryConnect") {
            val openLibraryViewModel: OpenLibraryViewModel = viewModel()
            OpenLibraryLoginScreen(
                onBackClick = {
                    // Skip directly to onboarding when user skips Open Library connect
                    navController.navigate("onboarding") {
                        popUpTo("openLibraryConnect") { inclusive = true }
                    }
                },
                onLoginSuccess = { username ->
                    navController.navigate("onboarding") {
                        popUpTo("openLibraryConnect") { inclusive = true }
                    }
                },
                viewModel = openLibraryViewModel
            )
        }
        composable("login") {
            LoginScreen(
                onBackClick = { navController.popBackStack() },
                onLoginSuccess = {
                    navController.popBackStack()
                }
            )
        }
        composable("onboarding") {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            HomeScreen(
                onBookClick = { bookId ->
                    val cleanId = bookId.substringAfterLast("/")
                    viewModel.setSelectedBookById(cleanId)
                    navController.navigate("detail/$cleanId")
                },
                onProfileClick = {
                    navController.navigate("profile")
                },
                onFreeGutenbergBooksClick = {
                    navController.navigate("gutenbergBrowse")
                }
            )
        }
        composable("library") {
            val libraryViewModel: LibraryViewModel = viewModel(
                factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
                    context.applicationContext as android.app.Application
                )
            )
            val openLibraryViewModel: OpenLibraryViewModel = viewModel(
                factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
                    context.applicationContext as android.app.Application
                )
            )
            
            LibraryScreen(
                        onBookClick = { bookId ->
                            navController.navigate("libraryBookDetail/$bookId")
                        },
                        onAddBookClick = {
                            navController.navigate("search")
                        },
                        onAddGutenbergClick = {
                            navController.navigate("gutenbergBrowse")
                        },
                        onShelvesClick = { navController.navigate("shelves") },
                        onDownloadedBookClick = { book ->
                            val encodedTitle = URLEncoder.encode(book.title, StandardCharsets.UTF_8.toString())
                            val encodedAuthor = URLEncoder.encode(book.author, StandardCharsets.UTF_8.toString())
                            val encodedCover = URLEncoder.encode(book.coverUrl ?: "", StandardCharsets.UTF_8.toString())
                            val encodedFileUri = URLEncoder.encode(book.fileUri.orEmpty(), StandardCharsets.UTF_8.toString())
                            when (book.format) {
                                BookFormat.EPUB -> {
                                    navController.navigate("epubReader/${book.id}?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover&fileUri=$encodedFileUri")
                                }
                                else -> {
                                    navController.navigate("reader/${book.id}?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover&fileUri=$encodedFileUri")
                                }
                            }
                        },
                        onReadLocalBook = { book ->
                            val localPath = book.localFilePath ?: return@LibraryScreen
                            val encodedTitle = URLEncoder.encode(book.title, StandardCharsets.UTF_8.toString())
                            val encodedAuthor = URLEncoder.encode(book.author, StandardCharsets.UTF_8.toString())
                            val encodedCover = URLEncoder.encode(book.coverUrl ?: "", StandardCharsets.UTF_8.toString())
                            val encodedFileUri = URLEncoder.encode(localPath, StandardCharsets.UTF_8.toString())
                            val resolvedFormat = book.localFileFormat ?: when {
                                localPath.lowercase().endsWith(".epub") -> BookFormat.EPUB
                                else -> BookFormat.PDF
                            }
                            when (resolvedFormat) {
                                BookFormat.EPUB -> navController.navigate("epubReader/${book.id}?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover&fileUri=$encodedFileUri")
                                else -> navController.navigate("reader/${book.id}?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover&fileUri=$encodedFileUri")
                            }
                        },
                        onReadOnlineBook = { book ->
                            val iaId = book.internetArchiveId ?: return@LibraryScreen
                            val encodedTitle = URLEncoder.encode(book.title, StandardCharsets.UTF_8.toString())
                            val encodedAuthor = URLEncoder.encode(book.author, StandardCharsets.UTF_8.toString())
                            val encodedCover = URLEncoder.encode(book.coverUrl ?: "", StandardCharsets.UTF_8.toString())
                            navController.navigate("reader/$iaId?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover")
                        },
                        viewModel = libraryViewModel,
                        openLibraryViewModel = openLibraryViewModel,
                        onOpenLibraryBookClick = { workKey ->
                            // workKey is like "/works/OL1234W"
                            val cleanId = workKey.substringAfterLast("/")
                            viewModel.setSelectedBookById(workKey)
                            navController.navigate("detail/$cleanId")
                        },
                        onConnectOpenLibrary = {
                            navController.navigate("openLibraryLogin")
                        },
                        onOpenBookDetails = { bookId ->
                            // Navigate to book detail screen from Open Library
                            viewModel.setSelectedBookById(bookId)
                            navController.navigate("detail/$bookId")
                        }
            )
        }
        composable(
            route = "libraryBookDetail/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) {
            val bookId = it.arguments?.getString("bookId") ?: return@composable
            val libraryViewModel: LibraryViewModel = viewModel()
            
            LaunchedEffect(bookId) {
                val uid = userPreferencesRepository.getGoogleUser().third
                uid?.let { userId ->
                    val repository = LibraryRepository()
                    repository.getBook(userId, bookId)?.let { book ->
                        libraryViewModel.selectBook(book)
                    }
                }
            }
            
            LibraryBookDetailScreen(
                bookId = bookId,
                onBackClick = { navController.popBackStack() },
                viewModel = libraryViewModel,
                onReadClick = { iaId, title, author, coverUrl ->
                    val encodedTitle = URLEncoder.encode(title ?: "", StandardCharsets.UTF_8.toString())
                    val encodedAuthor = URLEncoder.encode(author ?: "", StandardCharsets.UTF_8.toString())
                    val encodedCover = URLEncoder.encode(coverUrl ?: "", StandardCharsets.UTF_8.toString())
                    navController.navigate("reader/$iaId?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover")
                },
                onReadLocalFile = { filePath, format ->
                    // Navigate to appropriate reader based on stored format or fallback to file extension
                    val book = libraryViewModel.selectedBook.value
                    val encodedTitle = URLEncoder.encode(book?.title ?: "Book", StandardCharsets.UTF_8.toString())
                    val encodedAuthor = URLEncoder.encode(book?.author ?: "", StandardCharsets.UTF_8.toString())
                    val encodedCover = URLEncoder.encode(book?.coverUrl ?: "", StandardCharsets.UTF_8.toString())
                    val encodedFileUri = URLEncoder.encode(filePath, StandardCharsets.UTF_8.toString())
                    val bookId = book?.id ?: "local"
                    
                    val resolvedFormat = format ?: when {
                        filePath.lowercase().endsWith(".epub") -> BookFormat.EPUB
                        else -> BookFormat.PDF
                    }
                    when (resolvedFormat) {
                        BookFormat.EPUB -> navController.navigate("epubReader/$bookId?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover&fileUri=$encodedFileUri")
                        else -> navController.navigate("reader/$bookId?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover&fileUri=$encodedFileUri")
                    }
                },
                isUserLoggedIn = userPreferencesRepository.isOpenLibraryLoggedIn(),
                onBorrowConfirm = { borrowKey ->
                    val encodedBookKey = URLEncoder.encode(borrowKey, StandardCharsets.UTF_8.toString())
                    navController.navigate("borrow/$encodedBookKey")
                },
                onLoginRequired = {
                    navController.navigate("openLibraryLogin")
                }
            )
        }
        
        // Shelves Screen
        composable("shelves") {
            ShelvesScreen(
                uid = userPreferencesRepository.getGoogleUser().third,
                onShelfClick = { shelfId ->
                    navController.navigate("shelf/$shelfId")
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        
        // Shelf Detail Screen
        composable(
            route = "shelf/{shelfId}",
            arguments = listOf(navArgument("shelfId") { type = NavType.StringType })
        ) {
            val shelfId = it.arguments?.getString("shelfId") ?: return@composable
            ShelfDetailScreen(
                uid = userPreferencesRepository.getGoogleUser().third,
                shelfId = shelfId,
                onBackClick = { navController.popBackStack() },
                onBookClick = { bookId ->
                    navController.navigate("libraryBookDetail/$bookId")
                }
            )
        }
        composable("profile") {
            val authViewModel: AuthViewModel = viewModel()
            val openLibraryViewModel: OpenLibraryViewModel = viewModel()
            UserProfileScreen(
                onBackClick = { navigateTopLevel("home") },
                onEditPreferences = {
                    navController.navigate("editPreferences")
                },
                onConnectOpenLibrary = {
                    navController.navigate("openLibraryLogin")
                },
                onLogout = {
                    authViewModel.logout()
                    openLibraryViewModel.logout()
                    navController.navigate("googleLogin") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenSourceLicensesClick = {
                    navController.navigate("openSourceLicenses")
                },
                openLibraryViewModel = openLibraryViewModel
            )
        }
        composable("openLibraryLogin") {
            val openLibraryViewModel: OpenLibraryViewModel = viewModel()
            OpenLibraryLoginScreen(
                onBackClick = { navController.popBackStack() },
                onLoginSuccess = { username ->
                    navController.popBackStack()
                },
                viewModel = openLibraryViewModel
            )
        }
        composable("openSourceLicenses") {
            OpenSourceLicensesScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable("editPreferences") {
            EditPreferencesScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable("search") {
            BookSearchScreen(
                viewModel = viewModel,
                onBookClick = { bookId ->
                    val cleanId = bookId.substringAfterLast("/")
                    viewModel.setSelectedBookById(cleanId)
                    navController.navigate("detail/$cleanId")
                },
                onGutenbergClick = { book ->
                    navController.navigate("gutenbergDetail/${book.id}")
                },
                onReadGutenbergClick = { book ->
                    val encodedTitle = URLEncoder.encode(book.title, StandardCharsets.UTF_8.toString())
                    val encodedAuthor = URLEncoder.encode(book.authorNames, StandardCharsets.UTF_8.toString())
                    val encodedCover = URLEncoder.encode(book.coverUrl ?: "", StandardCharsets.UTF_8.toString())
                    val downloadUrl = URLEncoder.encode("https://www.gutenberg.org/ebooks/${book.id}.epub.images", StandardCharsets.UTF_8.toString())
                    navController.navigate("epubReader/gutenberg_${book.id}?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover&downloadUrl=$downloadUrl")
                },
                onAdvancedSearchClick = {
                    navController.navigate("advancedSearch")
                },
                onReadClick = { bookId, title, author, coverUrl ->
                    val encodedTitle = URLEncoder.encode(title ?: "", StandardCharsets.UTF_8.toString())
                    val encodedAuthor = URLEncoder.encode(author ?: "", StandardCharsets.UTF_8.toString())
                    val encodedCover = URLEncoder.encode(coverUrl ?: "", StandardCharsets.UTF_8.toString())
                    navController.navigate("reader/$bookId?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover")
                }
            )
        }
        composable(
            route = "reader/{bookId}?title={title}&author={author}&coverUrl={coverUrl}&fileUri={fileUri}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; nullable = true },
                navArgument("author") { type = NavType.StringType; nullable = true },
                navArgument("coverUrl") { type = NavType.StringType; nullable = true },
                navArgument("fileUri") { type = NavType.StringType; nullable = true }
            )
        ) {
            val bookId = it.arguments?.getString("bookId") ?: return@composable
            val title = it.arguments?.getString("title")?.let { t -> URLDecoder.decode(t, StandardCharsets.UTF_8.toString()) }
            val author = it.arguments?.getString("author")?.let { a -> URLDecoder.decode(a, StandardCharsets.UTF_8.toString()) }
            val coverUrl = it.arguments?.getString("coverUrl")?.let { c -> URLDecoder.decode(c, StandardCharsets.UTF_8.toString()) }
            val fileUri = it.arguments?.getString("fileUri")
                ?.let { f -> URLDecoder.decode(f, StandardCharsets.UTF_8.toString()) }
                ?.takeIf { f -> f.isNotBlank() }
            
            if (fileUri != null) {
                PdfReaderScreen(
                    bookId = bookId,
                    title = title,
                    author = author,
                    coverUrl = coverUrl,
                    fileUri = fileUri,
                    onBackClick = { navController.popBackStack() }
                )
            } else {
                ReaderScreen(
                    bookId = bookId,
                    title = title,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
        // EPUB Reader route
        composable(
            route = "epubReader/{bookId}?title={title}&author={author}&coverUrl={coverUrl}&fileUri={fileUri}&downloadUrl={downloadUrl}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; nullable = true },
                navArgument("author") { type = NavType.StringType; nullable = true },
                navArgument("coverUrl") { type = NavType.StringType; nullable = true },
                navArgument("fileUri") { type = NavType.StringType; nullable = true },
                navArgument("downloadUrl") { type = NavType.StringType; nullable = true }
            )
        ) {
            val bookId = it.arguments?.getString("bookId") ?: return@composable
            val title = it.arguments?.getString("title")?.let { t -> URLDecoder.decode(t, StandardCharsets.UTF_8.toString()) }
            val author = it.arguments?.getString("author")?.let { a -> URLDecoder.decode(a, StandardCharsets.UTF_8.toString()) }
            val coverUrl = it.arguments?.getString("coverUrl")?.let { c -> URLDecoder.decode(c, StandardCharsets.UTF_8.toString()) }
            val fileUri = it.arguments?.getString("fileUri")?.let { f -> URLDecoder.decode(f, StandardCharsets.UTF_8.toString()) }
            val downloadUrl = it.arguments?.getString("downloadUrl")?.let { d -> URLDecoder.decode(d, StandardCharsets.UTF_8.toString()) }
            
            EpubReaderScreen(
                bookId = bookId,
                title = title,
                author = author,
                coverUrl = coverUrl,
                fileUri = fileUri,
                downloadUrl = downloadUrl,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable("downloads") {
            DownloadsScreen(
                onBackClick = { navController.popBackStack() },
                onBookClick = { book ->
                    val encodedTitle = URLEncoder.encode(book.title, StandardCharsets.UTF_8.toString())
                    val encodedAuthor = URLEncoder.encode(book.author, StandardCharsets.UTF_8.toString())
                    val encodedCover = URLEncoder.encode(book.coverUrl ?: "", StandardCharsets.UTF_8.toString())
                    val encodedFileUri = URLEncoder.encode(book.fileUri ?: "", StandardCharsets.UTF_8.toString())
                    
                    // Route to appropriate reader based on format
                    when (book.format) {
                        BookFormat.EPUB -> {
                            navController.navigate("epubReader/${book.id}?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover&fileUri=$encodedFileUri")
                        }
                        else -> {
                            navController.navigate("reader/${book.id}?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover&fileUri=$encodedFileUri")
                        }
                    }
                }
            )
        }
        composable("advancedSearch") {
            AdvancedSearchScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onSearchComplete = {
                    navController.popBackStack()
                }
            )
        }
        composable("detail/{bookId}") {
            val bookId = it.arguments?.getString("bookId") ?: return@composable
            
            // Trigger ViewModel update when bookId changes (important for back navigation)
            LaunchedEffect(bookId) {
                // Reconstruct the full key based on whether it looks like an edition or work
                val fullKey = if (bookId.startsWith("OL") && bookId.contains("M")) {
                    "/books/$bookId"
                } else {
                    "/works/$bookId"
                }
                viewModel.setSelectedBookById(fullKey)
                
                // Add to recent books
                userPreferencesRepository.addRecentBook(fullKey)
            }
            
            BookDetailScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onBookClick = { bookId ->
                    // Pass the full key first so ViewModel can detect if it's an edition
                    viewModel.setSelectedBookById(bookId)
                    // Then use cleaned ID for navigation
                    val cleanId = bookId.substringAfterLast("/")
                    // Use launchSingleTop to reuse the detail screen instead of creating new instances
                    navController.navigate("detail/$cleanId")
                },
                onSeeAllEditionsClick = { workId ->
                    navController.navigate("editions/$workId")
                },
                onReadClick = { id, title, author, coverUrl ->
                    val encodedTitle = URLEncoder.encode(title ?: "", StandardCharsets.UTF_8.toString())
                    val encodedAuthor = URLEncoder.encode(author ?: "", StandardCharsets.UTF_8.toString())
                    val encodedCover = URLEncoder.encode(coverUrl ?: "", StandardCharsets.UTF_8.toString())
                    navController.navigate("reader/$id?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover")
                },
                onReadGutenbergBook = { gutenbergId, title, author, coverUrl ->
                    // Navigate to EPUB reader for Gutenberg book
                    val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                    val encodedAuthor = URLEncoder.encode(author, StandardCharsets.UTF_8.toString())
                    val encodedCover = URLEncoder.encode(coverUrl ?: "", StandardCharsets.UTF_8.toString())
                    val downloadUrl = URLEncoder.encode("https://www.gutenberg.org/ebooks/$gutenbergId.epub.images", StandardCharsets.UTF_8.toString())
                    navController.navigate("epubReader/gutenberg_$gutenbergId?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover&downloadUrl=$downloadUrl")
                },
                isUserLoggedIn = userPreferencesRepository.isOpenLibraryLoggedIn(),
                onBorrowConfirm = { bookKey ->
                    // bookKey is the edition key like "OL9219606M" or full path "/books/OL9219606M"
                    // Pass it to the borrow screen which will construct the correct URL
                    val encodedBookKey = URLEncoder.encode(bookKey, StandardCharsets.UTF_8.toString())
                    navController.navigate("borrow/$encodedBookKey")
                },
                onLoginRequired = {
                    navController.navigate("openLibraryLogin")
                }
            )
        }
        composable(
            route = "borrow/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) {
            val bookId = it.arguments?.getString("bookId") ?: return@composable
            BorrowWebViewScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() }
            )
        }
        composable("editions/{workId}") {
            val workId = it.arguments?.getString("workId") ?: return@composable
            EditionsListScreen(
                workId = workId,
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onEditionClick = { editionKey ->
                    // Pass the full key to setSelectedBookById so it can detect it's an edition
                    viewModel.setSelectedBookById(editionKey)
                    // Use the cleanId for navigation route
                    val cleanId = editionKey.substringAfterLast("/")
                    navController.navigate("detail/$cleanId")
                }
            )
        }
        
        // Gutenberg Browse Screen - for browsing Project Gutenberg free books
        composable("gutenbergBrowse") {
            GutenbergBrowseScreen(
                onBackClick = { navController.popBackStack() },
                onBookClick = { book ->
                    // Navigate to book detail screen
                    navController.navigate("gutenbergDetail/${book.id}")
                },
                onReadBook = { book ->
                    // Navigate to EPUB reader for Gutenberg book
                    val encodedTitle = URLEncoder.encode(book.title, StandardCharsets.UTF_8.toString())
                    val encodedAuthor = URLEncoder.encode(book.authorNames, StandardCharsets.UTF_8.toString())
                    val encodedCover = URLEncoder.encode(book.coverUrl ?: "", StandardCharsets.UTF_8.toString())
                    val downloadUrl = book.epubUrl ?: book.textUrl ?: ""
                    val encodedDownloadUrl = URLEncoder.encode(downloadUrl, StandardCharsets.UTF_8.toString())
                    navController.navigate("epubReader/gutenberg_${book.id}?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover&downloadUrl=$encodedDownloadUrl")
                }
            )
        }
        
        // Gutenberg Book Detail Screen
        composable(
            route = "gutenbergDetail/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.IntType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getInt("bookId") ?: return@composable
            GutenbergBookDetailScreen(
                bookId = bookId,
                onBackClick = { navController.popBackStack() },
                onReadClick = { book, fileUri ->
                    val encodedTitle = URLEncoder.encode(book.title, StandardCharsets.UTF_8.toString())
                    val encodedAuthor = URLEncoder.encode(book.authorNames, StandardCharsets.UTF_8.toString())
                    val encodedCover = URLEncoder.encode(book.coverUrl ?: "", StandardCharsets.UTF_8.toString())
                    
                    // If we have a local file, use it; otherwise provide download URL
                    if (fileUri != null) {
                        val encodedFileUri = URLEncoder.encode(fileUri, StandardCharsets.UTF_8.toString())
                        navController.navigate("epubReader/gutenberg_${book.id}?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover&fileUri=$encodedFileUri")
                    } else {
                        val downloadUrl = book.epubUrl ?: book.textUrl ?: ""
                        val encodedDownloadUrl = URLEncoder.encode(downloadUrl, StandardCharsets.UTF_8.toString())
                        navController.navigate("epubReader/gutenberg_${book.id}?title=$encodedTitle&author=$encodedAuthor&coverUrl=$encodedCover&downloadUrl=$encodedDownloadUrl")
                    }
                }
            )
        }
        }
        AnimatedVisibility(
            visible = showBottomBar && !isImeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(MotionTokens.springGentle()) +
                slideInVertically(MotionTokens.springGentle()) { it / 2 } +
                scaleIn(MotionTokens.springGentle(), initialScale = 0.96f),
            exit = fadeOut(MotionTokens.springGentle()) +
                slideOutVertically(MotionTokens.springGentle()) { it / 2 } +
                scaleOut(MotionTokens.springGentle(), targetScale = 0.96f)
        ) {
            BottomNavigationBar(
                currentRoute = currentTopLevelRoute ?: "home",
                onNavigate = ::navigateTopLevel
            )
        }
    }
}
