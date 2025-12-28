package com.theblankstate.libri.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class UserPreferencesRepository(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_LANGUAGES = "selected_languages"
        private const val KEY_AUTHORS = "selected_authors"
        private const val KEY_GENRES = "selected_genres"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_RECENT_BOOKS = "recent_books"
        private const val KEY_IA_COOKIES = "ia_cookies"
        private const val KEY_IA_EMAIL = "ia_email"
        private const val KEY_GOOGLE_EMAIL = "google_email"
        private const val KEY_GOOGLE_NAME = "google_name"
        private const val KEY_GOOGLE_UID = "google_uid"
        // Open Library session keys
        private const val KEY_OL_SESSION = "ol_session"
        private const val KEY_OL_USERNAME = "ol_username"
        private const val KEY_OL_EMAIL = "ol_email"
    }

    fun saveIASession(email: String, cookies: Map<String, String>) {
        val json = gson.toJson(cookies)
        sharedPreferences.edit()
            .putString(KEY_IA_EMAIL, email)
            .putString(KEY_IA_COOKIES, json)
            .apply()
    }

    fun getIASession(): Pair<String?, Map<String, String>?> {
        val email = sharedPreferences.getString(KEY_IA_EMAIL, null)
        val json = sharedPreferences.getString(KEY_IA_COOKIES, null)
        val cookies = if (json != null) {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(json, type)
        } else {
            null
        }
        return email to cookies
    }

    fun clearIASession() {
        sharedPreferences.edit()
            .remove(KEY_IA_EMAIL)
            .remove(KEY_IA_COOKIES)
            .apply()
    }

    fun saveGoogleUser(email: String, displayName: String, uid: String) {
        sharedPreferences.edit()
            .putString(KEY_GOOGLE_EMAIL, email)
            .putString(KEY_GOOGLE_NAME, displayName)
            .putString(KEY_GOOGLE_UID, uid)
            .apply()
    }

    fun getGoogleUser(): Triple<String?, String?, String?> {
        val email = sharedPreferences.getString(KEY_GOOGLE_EMAIL, null)
        val name = sharedPreferences.getString(KEY_GOOGLE_NAME, null)
        val uid = sharedPreferences.getString(KEY_GOOGLE_UID, null)
        return Triple(email, name, uid)
    }

    fun clearGoogleUser() {
        sharedPreferences.edit()
            .remove(KEY_GOOGLE_EMAIL)
            .remove(KEY_GOOGLE_NAME)
            .remove(KEY_GOOGLE_UID)
            .apply()
    }

    fun saveSelectedLanguages(languages: Set<String>) {
        sharedPreferences.edit().putStringSet(KEY_LANGUAGES, languages).apply()
    }

    fun getSelectedLanguages(): Set<String> {
        return sharedPreferences.getStringSet(KEY_LANGUAGES, emptySet()) ?: emptySet()
    }

    fun saveSelectedAuthors(authors: Set<String>) {
        sharedPreferences.edit().putStringSet(KEY_AUTHORS, authors).apply()
    }

    fun getSelectedAuthors(): Set<String> {
        return sharedPreferences.getStringSet(KEY_AUTHORS, emptySet()) ?: emptySet()
    }

    fun saveSelectedGenres(genres: Set<String>) {
        sharedPreferences.edit().putStringSet(KEY_GENRES, genres).apply()
    }

    fun getSelectedGenres(): Set<String> {
        return sharedPreferences.getStringSet(KEY_GENRES, emptySet()) ?: emptySet()
    }

    fun setOnboardingCompleted(completed: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun isOnboardingCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun addRecentBook(bookId: String) {
        val currentList = getRecentBooks().toMutableList()
        currentList.remove(bookId) // Remove if exists to move to top
        currentList.add(0, bookId)
        if (currentList.size > 10) {
            currentList.removeAt(currentList.lastIndex)
        }
        val json = gson.toJson(currentList)
        sharedPreferences.edit().putString(KEY_RECENT_BOOKS, json).apply()
    }

    fun getRecentBooks(): List<String> {
        val json = sharedPreferences.getString(KEY_RECENT_BOOKS, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type)
    }

    // Open Library Session Management
    fun saveOpenLibrarySession(email: String, username: String, sessionCookie: String) {
        sharedPreferences.edit()
            .putString(KEY_OL_EMAIL, email)
            .putString(KEY_OL_USERNAME, username)
            .putString(KEY_OL_SESSION, sessionCookie)
            .apply()
    }

    fun getOpenLibrarySession(): Triple<String?, String?, String?> {
        val email = sharedPreferences.getString(KEY_OL_EMAIL, null)
        val username = sharedPreferences.getString(KEY_OL_USERNAME, null)
        val session = sharedPreferences.getString(KEY_OL_SESSION, null)
        return Triple(email, username, session)
    }

    fun getOpenLibraryUsername(): String? {
        return sharedPreferences.getString(KEY_OL_USERNAME, null)
    }

    fun isOpenLibraryLoggedIn(): Boolean {
        return sharedPreferences.getString(KEY_OL_SESSION, null) != null
    }

    fun clearOpenLibrarySession() {
        sharedPreferences.edit()
            .remove(KEY_OL_EMAIL)
            .remove(KEY_OL_USERNAME)
            .remove(KEY_OL_SESSION)
            .apply()
    }
}
