package com.theblankstate.libri.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theblankstate.libri.data.FirebaseUserRepository
import com.theblankstate.libri.data.UserProfile
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import com.theblankstate.libri.data.LibraryRepository
import com.theblankstate.libri.datamodel.ReadingStatus

data class UserStats(
    val booksRead: Int = 0,
    val pagesRead: Int = 0,
    val booksInProgress: Int = 0,
    val booksWantToRead: Int = 0
)

class UserProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseUserRepository = FirebaseUserRepository()
    private val libraryRepository = LibraryRepository()
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile
    
    private val _userStats = MutableStateFlow(UserStats())
    val userStats: StateFlow<UserStats> = _userStats

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _showEditDialog = MutableStateFlow<String?>(null)
    val showEditDialog: StateFlow<String?> = _showEditDialog

    private val _editName = MutableStateFlow("")
    val editName: StateFlow<String> = _editName

    private val _editGender = MutableStateFlow("")
    val editGender: StateFlow<String> = _editGender

    fun loadUserProfile() {
        val currentUser = firebaseAuth.currentUser ?: return
        
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Load Profile
                val profile = firebaseUserRepository.getUserProfile(currentUser.uid)
                _userProfile.value = profile
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
        
        // Load Stats separately (this flow stays active)
        viewModelScope.launch {
            try {
                libraryRepository.getLibraryBooks(currentUser.uid).collect { books ->
                    val read = books.count { it.readingStatusEnum == ReadingStatus.FINISHED }
                    val inProgress = books.count { it.readingStatusEnum == ReadingStatus.IN_PROGRESS }
                    val wantToRead = books.count { it.readingStatusEnum == ReadingStatus.WANT_TO_READ }
                    
                    // Calculate pages read:
                    // For finished books: totalPages
                    // For in-progress books: currentPage
                    val pages = books.sumOf { book ->
                        when (book.readingStatusEnum) {
                            ReadingStatus.FINISHED -> book.totalPages
                            ReadingStatus.IN_PROGRESS -> book.currentPage
                            else -> 0
                        }
                    }
                    
                    _userStats.value = UserStats(
                        booksRead = read,
                        pagesRead = pages,
                        booksInProgress = inProgress,
                        booksWantToRead = wantToRead
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun showEditNameDialog() {
        _editName.value = _userProfile.value?.name ?: ""
        _showEditDialog.value = "name"
    }

    fun showEditGenderDialog() {
        _editGender.value = _userProfile.value?.gender ?: ""
        _showEditDialog.value = "gender"
    }

    fun hideEditDialog() {
        _showEditDialog.value = null
    }

    fun setEditName(value: String) {
        _editName.value = value
    }

    fun setEditGender(value: String) {
        _editGender.value = value
    }

    fun saveProfile() {
        val currentUser = firebaseAuth.currentUser ?: return
        val currentProfile = _userProfile.value ?: return
        
        _isSaving.value = true
        viewModelScope.launch {
            try {
                val updatedProfile = when (_showEditDialog.value) {
                    "name" -> currentProfile.copy(name = _editName.value)
                    "gender" -> currentProfile.copy(gender = _editGender.value)
                    else -> currentProfile
                }
                
                firebaseUserRepository.saveUserProfile(updatedProfile)
                _userProfile.value = updatedProfile
                _showEditDialog.value = null
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSaving.value = false
            }
        }
    }
}
