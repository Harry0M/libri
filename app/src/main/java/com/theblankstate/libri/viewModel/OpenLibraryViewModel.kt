package com.theblankstate.libri.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theblankstate.libri.data.OpenLibraryRepository
import com.theblankstate.libri.data.OpenLibraryUser
import com.theblankstate.libri.data.OpenLibraryLoan
import com.theblankstate.libri.data.OpenLibraryListItem
import com.theblankstate.libri.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class OpenLibraryAuthState {
    object Idle : OpenLibraryAuthState()
    object Loading : OpenLibraryAuthState()
    object Success : OpenLibraryAuthState()
    data class Error(val message: String) : OpenLibraryAuthState()
}

class OpenLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val openLibraryRepository = OpenLibraryRepository()
    private val userPreferences = UserPreferencesRepository(application)

    private val _authState = MutableStateFlow<OpenLibraryAuthState>(OpenLibraryAuthState.Idle)
    val authState: StateFlow<OpenLibraryAuthState> = _authState

    private val _openLibraryUser = MutableStateFlow<OpenLibraryUser?>(null)
    val openLibraryUser: StateFlow<OpenLibraryUser?> = _openLibraryUser

    private val _myLoans = MutableStateFlow<List<OpenLibraryLoan>>(emptyList())
    val myLoans: StateFlow<List<OpenLibraryLoan>> = _myLoans

    private val _wantToRead = MutableStateFlow<List<OpenLibraryListItem>>(emptyList())
    val wantToRead: StateFlow<List<OpenLibraryListItem>> = _wantToRead

    private val _alreadyRead = MutableStateFlow<List<OpenLibraryListItem>>(emptyList())
    val alreadyRead: StateFlow<List<OpenLibraryListItem>> = _alreadyRead

    private val _currentlyReading = MutableStateFlow<List<OpenLibraryListItem>>(emptyList())
    val currentlyReading: StateFlow<List<OpenLibraryListItem>> = _currentlyReading

    private val _isLoadingLists = MutableStateFlow(false)
    val isLoadingLists: StateFlow<Boolean> = _isLoadingLists

    init {
        // Check if already logged in
        checkExistingSession()
    }

    private fun checkExistingSession() {
        val (email, username, session) = userPreferences.getOpenLibrarySession()
        if (session != null && username != null) {
            _openLibraryUser.value = OpenLibraryUser(
                username = username,
                displayName = username,
                key = "/people/$username"
            )
            _authState.value = OpenLibraryAuthState.Success
        }
    }

    fun loginToOpenLibrary(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = OpenLibraryAuthState.Loading
            
            val (success, result) = openLibraryRepository.login(email, password)
            
            if (success && result != null) {
                // Get user info
                val user = openLibraryRepository.getUserInfo(result)
                
                if (user != null) {
                    // Save session
                    userPreferences.saveOpenLibrarySession(email, user.username, result)
                    _openLibraryUser.value = user
                    _authState.value = OpenLibraryAuthState.Success
                    
                    // Load user's reading lists
                    loadUserLists(user.username)
                } else {
                    // Even if we can't get user info, we might still be logged in
                    // Extract username from email as fallback
                    val fallbackUsername = email.substringBefore("@")
                    userPreferences.saveOpenLibrarySession(email, fallbackUsername, result)
                    _openLibraryUser.value = OpenLibraryUser(
                        username = fallbackUsername,
                        displayName = fallbackUsername,
                        key = "/people/$fallbackUsername"
                    )
                    _authState.value = OpenLibraryAuthState.Success
                }
            } else {
                _authState.value = OpenLibraryAuthState.Error(result ?: "Login failed")
            }
        }
    }

    fun loadUserLists(username: String? = null) {
        val user = username ?: _openLibraryUser.value?.username ?: userPreferences.getOpenLibraryUsername()
        if (user == null) return

        viewModelScope.launch {
            _isLoadingLists.value = true
            try {
                // Load all lists in parallel
                val session = userPreferences.getOpenLibrarySession().third
                
                if (session != null) {
                    _myLoans.value = openLibraryRepository.getMyLoans(session, user)
                }
                
                _wantToRead.value = openLibraryRepository.getWantToRead(user)
                _alreadyRead.value = openLibraryRepository.getAlreadyRead(user)
                _currentlyReading.value = openLibraryRepository.getCurrentlyReading(user)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingLists.value = false
            }
        }
    }

    fun isLoggedIn(): Boolean {
        return userPreferences.isOpenLibraryLoggedIn()
    }

    fun getUsername(): String? {
        return userPreferences.getOpenLibraryUsername()
    }

    fun getSessionCookie(): String? {
        return userPreferences.getOpenLibrarySession().third
    }

    fun logout() {
        userPreferences.clearOpenLibrarySession()
        _openLibraryUser.value = null
        _myLoans.value = emptyList()
        _wantToRead.value = emptyList()
        _alreadyRead.value = emptyList()
        _currentlyReading.value = emptyList()
        _authState.value = OpenLibraryAuthState.Idle
    }

    fun getBorrowUrl(editionKey: String): String {
        return openLibraryRepository.getBorrowUrl(editionKey)
    }

    fun resetAuthState() {
        _authState.value = OpenLibraryAuthState.Idle
    }
}
