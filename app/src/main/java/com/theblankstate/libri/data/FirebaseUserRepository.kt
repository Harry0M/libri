package com.theblankstate.libri.data

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val name: String = "",
    val gender: String = "",
    val languages: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val recentBooks: List<String> = emptyList()
)

class FirebaseUserRepository {
    private val database = FirebaseDatabase.getInstance()
    private val usersRef = database.getReference("users")

    suspend fun saveUserProfile(userProfile: UserProfile) {
        try {
            usersRef.child(userProfile.uid).setValue(userProfile).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            val snapshot = usersRef.child(uid).get().await()
            try {
                snapshot.getValue(UserProfile::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to manual parsing
                val map = snapshot.value as? Map<String, Any?> ?: return null
                UserProfile(
                    uid = map["uid"] as? String ?: uid,
                    email = map["email"] as? String ?: "",
                    displayName = map["displayName"] as? String ?: "",
                    name = map["name"] as? String ?: "",
                    gender = map["gender"] as? String ?: "",
                    languages = (map["languages"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    authors = (map["authors"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    genres = (map["genres"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    recentBooks = (map["recentBooks"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun updateUserPreferences(
        uid: String,
        languages: List<String>,
        authors: List<String>,
        genres: List<String>
    ) {
        try {
            val updates = mapOf(
                "languages" to languages,
                "authors" to authors,
                "genres" to genres
            )
            usersRef.child(uid).updateChildren(updates).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addRecentBook(uid: String, bookId: String) {
        try {
            val profile = getUserProfile(uid)
            val currentList = profile?.recentBooks?.toMutableList() ?: mutableListOf()
            currentList.remove(bookId) // Remove if exists to move to top
            currentList.add(0, bookId)
            if (currentList.size > 10) {
                currentList.removeAt(currentList.lastIndex)
            }
            usersRef.child(uid).child("recentBooks").setValue(currentList).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getRecentBooks(uid: String): List<String> {
        return try {
            val profile = getUserProfile(uid)
            profile?.recentBooks ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
