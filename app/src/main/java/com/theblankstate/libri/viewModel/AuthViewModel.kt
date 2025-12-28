package com.theblankstate.libri.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theblankstate.libri.data.UserPreferencesRepository
import com.theblankstate.libri.data.FirebaseUserRepository
import com.theblankstate.libri.data.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class GoogleAuthState {
    object Idle : GoogleAuthState()
    object Loading : GoogleAuthState()
    object Success : GoogleAuthState()
    data class ExistingUser(val displayName: String) : GoogleAuthState() // User already has profile in Firebase
    data class Error(val message: String) : GoogleAuthState()
}

sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    object Success : AuthState
    data class Error(val message: String) : AuthState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferences = UserPreferencesRepository(application)
    private val firebaseUserRepository = FirebaseUserRepository()
    private val client = OkHttpClient()
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val _googleAuthState = MutableStateFlow<GoogleAuthState>(GoogleAuthState.Idle)
    val googleAuthState: StateFlow<GoogleAuthState> = _googleAuthState

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val success = performLogin(email, pass)
                if (success) {
                    _authState.value = AuthState.Success
                } else {
                    _authState.value = AuthState.Error("Invalid credentials or login failed.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Network error: ${e.message}")
            }
        }
    }

    private suspend fun performLogin(email: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("username", email)
            .add("email", email)
            .add("password", pass)
            .add("remember", "1")
            .add("submit_by_js", "true")
            .build()

        val request = Request.Builder()
            .url("https://archive.org/account/login")
            .post(formBody)
            .header("User-Agent", "Mozilla/5.0 (Android) LearnCompose/1.0")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            val cookies = collectCookies(response)
            val hasSession = cookies.keys.any { key ->
                key.equals("logged-in-user", ignoreCase = true) ||
                key.equals("logged-in-sig", ignoreCase = true)
            }

            if (hasSession) {
                userPreferences.saveIASession(email, cookies)
                return@use true
            }

            val bodyText = response.body?.string().orEmpty()
            if (bodyText.contains("Incorrect email", ignoreCase = true) ||
                bodyText.contains("password", ignoreCase = true)) {
                return@use false
            }

            return@use false
        }
    }

    private fun collectCookies(response: okhttp3.Response): Map<String, String> {
        val cookies = mutableMapOf<String, String>()
        var current: okhttp3.Response? = response
        while (current != null) {
            current.headers("Set-Cookie").forEach { header ->
                val parts = header.split(";", limit = 2)
                if (parts.isNotEmpty()) {
                    val cookiePart = parts[0]
                    val cookieSplit = cookiePart.split("=", limit = 2)
                    if (cookieSplit.size == 2) {
                        cookies[cookieSplit[0].trim()] = cookieSplit[1].trim()
                    }
                }
            }
            current = current.priorResponse
        }
        return cookies
    }
    
    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit = {}, onExistingUser: () -> Unit = {}) {
        viewModelScope.launch {
            _googleAuthState.value = GoogleAuthState.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = firebaseAuth.signInWithCredential(credential).await()
                
                result.user?.let { user ->
                    // Save user info to preferences
                    val email = user.email ?: ""
                    val displayName = user.displayName ?: ""
                    userPreferences.saveGoogleUser(email, displayName, user.uid)
                    
                    // Check if user profile already exists in Firebase
                    val existingProfile = firebaseUserRepository.getUserProfile(user.uid)
                    
                    if (existingProfile != null && existingProfile.name.isNotEmpty()) {
                        // Existing user with completed profile - restore local onboarding status
                        userPreferences.setOnboardingCompleted(true)
                        // Restore preferences locally
                        userPreferences.saveSelectedLanguages(existingProfile.languages.toSet())
                        userPreferences.saveSelectedAuthors(existingProfile.authors.toSet())
                        userPreferences.saveSelectedGenres(existingProfile.genres.toSet())
                        
                        _googleAuthState.value = GoogleAuthState.ExistingUser(existingProfile.name)
                        onExistingUser()
                    } else {
                        // New user - create initial profile
                        val userProfile = UserProfile(
                            uid = user.uid,
                            email = email,
                            displayName = displayName
                        )
                        firebaseUserRepository.saveUserProfile(userProfile)
                        
                        _googleAuthState.value = GoogleAuthState.Success
                        onSuccess()
                    }
                } ?: run {
                    _googleAuthState.value = GoogleAuthState.Error("Failed to sign in with Google")
                }
            } catch (e: Exception) {
                _googleAuthState.value = GoogleAuthState.Error("Google sign-in failed: ${e.message}")
            }
        }
    }

    fun logout() {
        firebaseAuth.signOut()
        userPreferences.clearIASession()
        userPreferences.clearGoogleUser()
        _authState.value = AuthState.Idle
        _googleAuthState.value = GoogleAuthState.Idle
    }

    fun isUserLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }

    fun getCurrentUserEmail(): String? {
        return firebaseAuth.currentUser?.email
    }
}
