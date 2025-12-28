package com.theblankstate.libri.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theblankstate.libri.data.FirebaseUserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseUserRepository = FirebaseUserRepository()
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _gender = MutableStateFlow("")
    val gender: StateFlow<String> = _gender

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun setName(value: String) {
        _name.value = value
    }

    fun setGender(value: String) {
        _gender.value = value
    }

    fun saveProfile(onSuccess: () -> Unit) {
        val currentUser = firebaseAuth.currentUser ?: return
        
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Get existing profile to preserve any data
                val existingProfile = firebaseUserRepository.getUserProfile(currentUser.uid)
                
                // Create updated profile with name and gender
                val updatedProfile = existingProfile?.copy(
                    name = _name.value,
                    gender = _gender.value
                ) ?: com.theblankstate.libri.data.UserProfile(
                    uid = currentUser.uid,
                    email = currentUser.email ?: "",
                    displayName = currentUser.displayName ?: "",
                    name = _name.value,
                    gender = _gender.value
                )
                
                firebaseUserRepository.saveUserProfile(updatedProfile)
                _isLoading.value = false
                onSuccess()
            } catch (e: Exception) {
                _isLoading.value = false
                e.printStackTrace()
            }
        }
    }
}
