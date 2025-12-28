package com.theblankstate.libri.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theblankstate.libri.data.FirebaseUserRepository
import com.theblankstate.libri.data.OnboardingData
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EditPreferencesViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseUserRepository = FirebaseUserRepository()
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep

    private val _selectedLanguages = MutableStateFlow<Set<String>>(emptySet())
    val selectedLanguages: StateFlow<Set<String>> = _selectedLanguages

    private val _selectedAuthors = MutableStateFlow<Set<String>>(emptySet())
    val selectedAuthors: StateFlow<Set<String>> = _selectedAuthors

    private val _selectedGenres = MutableStateFlow<Set<String>>(emptySet())
    val selectedGenres: StateFlow<Set<String>> = _selectedGenres

    private val _availableAuthors = MutableStateFlow<List<String>>(emptyList())
    val availableAuthors: StateFlow<List<String>> = _availableAuthors

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    fun loadCurrentPreferences() {
        val currentUser = firebaseAuth.currentUser ?: return
        
        viewModelScope.launch {
            try {
                val profile = firebaseUserRepository.getUserProfile(currentUser.uid)
                profile?.let {
                    _selectedLanguages.value = it.languages.toSet()
                    _selectedAuthors.value = it.authors.toSet()
                    _selectedGenres.value = it.genres.toSet()
                    updateAvailableAuthors()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleLanguage(language: String) {
        val current = _selectedLanguages.value.toMutableSet()
        if (current.contains(language)) {
            current.remove(language)
        } else {
            if (current.size < 4) {
                current.add(language)
            }
        }
        _selectedLanguages.value = current
        updateAvailableAuthors()
    }

    private fun updateAvailableAuthors() {
        val authors = _selectedLanguages.value.flatMap { lang ->
            OnboardingData.authorsByLanguage[lang] ?: emptyList()
        }
        _availableAuthors.value = authors
    }

    fun toggleAuthor(author: String) {
        val current = _selectedAuthors.value.toMutableSet()
        if (current.contains(author)) {
            current.remove(author)
        } else {
            if (current.size < 40) {
                current.add(author)
            }
        }
        _selectedAuthors.value = current
    }

    fun toggleGenre(genre: String) {
        val current = _selectedGenres.value.toMutableSet()
        if (current.contains(genre)) {
            current.remove(genre)
        } else {
            if (current.size < 5) {
                current.add(genre)
            }
        }
        _selectedGenres.value = current
    }

    fun nextStep() {
        if (_currentStep.value < 2) {
            _currentStep.value += 1
        }
    }

    fun previousStep() {
        if (_currentStep.value > 0) {
            _currentStep.value -= 1
        }
    }

    fun savePreferences(onSuccess: () -> Unit) {
        val currentUser = firebaseAuth.currentUser ?: return
        
        _isSaving.value = true
        viewModelScope.launch {
            try {
                firebaseUserRepository.updateUserPreferences(
                    uid = currentUser.uid,
                    languages = _selectedLanguages.value.toList(),
                    authors = _selectedAuthors.value.toList(),
                    genres = _selectedGenres.value.toList()
                )
                _isSaving.value = false
                onSuccess()
            } catch (e: Exception) {
                _isSaving.value = false
                e.printStackTrace()
            }
        }
    }
}
