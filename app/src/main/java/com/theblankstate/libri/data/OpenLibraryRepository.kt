package com.theblankstate.libri.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class OpenLibraryUser(
    val username: String = "",
    val displayName: String = "",
    val key: String = "",
    val createdAt: String = ""
)

data class OpenLibraryLoan(
    val workKey: String = "",
    val editionKey: String = "",
    val title: String = "",
    val author: String = "",
    val coverId: Int? = null,
    val loanDate: String = "",
    val expireDate: String = ""
) {
    val coverUrl: String
        get() = coverId?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" } ?: ""
}

data class OpenLibraryListItem(
    val workKey: String = "",
    val title: String = "",
    val author: String = "",
    val coverId: Int? = null,
    val dateAdded: String = ""
) {
    val coverUrl: String
        get() = coverId?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" } ?: ""
}

class OpenLibraryRepository {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    companion object {
        private const val TAG = "OpenLibraryRepository"
        private const val BASE_URL = "https://openlibrary.org"
    }

    /**
     * Login to Open Library and return session cookie
     * Returns pair of (success, session cookie or error message)
     */
    suspend fun login(email: String, password: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("username", email)
                .add("password", password)
                .add("redirect", "/")
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/account/login")
                .post(formBody)
                .header("User-Agent", "ScribeApp/1.0 (Android)")
                .header("Accept", "text/html,application/xhtml+xml")
                .build()

            client.newCall(request).execute().use { response ->
                val cookies = collectCookies(response)
                val sessionCookie = cookies["session"]
                
                // Check if we got a session cookie which indicates successful login
                if (sessionCookie != null) {
                    return@use Pair(true, sessionCookie)
                }
                
                // Check for error in response body
                val bodyText = response.body?.string().orEmpty()
                if (bodyText.contains("Invalid") || bodyText.contains("incorrect", ignoreCase = true)) {
                    return@use Pair(false, "Invalid email or password")
                }
                
                return@use Pair(false, "Login failed. Please try again.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            return@withContext Pair(false, "Network error: ${e.message}")
        }
    }

    /**
     * Get Open Library user info using session
     */
    suspend fun getUserInfo(sessionCookie: String): OpenLibraryUser? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/account/loans")
                .header("Cookie", "session=$sessionCookie")
                .header("User-Agent", "ScribeApp/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                
                // Parse username from the page (look for logged-in user indicator)
                val usernameRegex = """href="/people/([^"]+)""".toRegex()
                val matchResult = usernameRegex.find(bodyText)
                val username = matchResult?.groupValues?.getOrNull(1) ?: ""
                
                if (username.isNotEmpty()) {
                    return@use OpenLibraryUser(
                        username = username,
                        displayName = username,
                        key = "/people/$username"
                    )
                }
                return@use null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get user info error", e)
            return@withContext null
        }
    }

    /**
     * Get user's current loans (borrowed books from Internet Archive)
     * Uses the JSON API endpoint /account/loans.json for reliable data
     */
    suspend fun getMyLoans(sessionCookie: String, username: String): List<OpenLibraryLoan> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching loans for user: $username")
            
            // Primary method: Use the JSON API endpoint
            val jsonLoans = fetchLoansFromJsonApi(sessionCookie)
            if (jsonLoans.isNotEmpty()) {
                Log.d(TAG, "Found ${jsonLoans.size} loans from JSON API")
                return@withContext jsonLoans
            }
            
            // Fallback: Try loan history endpoint
            val historyLoans = fetchLoanHistory(sessionCookie)
            if (historyLoans.isNotEmpty()) {
                Log.d(TAG, "Found ${historyLoans.size} loans from loan history")
                return@withContext historyLoans
            }
            
            Log.d(TAG, "No loans found")
            return@withContext emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Get loans error", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Fetch loans from the JSON API endpoint /account/loans.json
     * This is the official API endpoint that returns structured loan data
     */
    private suspend fun fetchLoansFromJsonApi(sessionCookie: String): List<OpenLibraryLoan> {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/account/loans.json")
                .header("Cookie", "session=$sessionCookie")
                .header("User-Agent", "ScribeApp/1.0 (Android)")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Loans JSON API response code: ${response.code}")
                
                if (response.code == 403) {
                    Log.d(TAG, "Session may be invalid or expired (403)")
                    return emptyList()
                }
                
                if (!response.isSuccessful) {
                    Log.d(TAG, "Loans API request failed: ${response.code}")
                    return emptyList()
                }
                
                val bodyText = response.body?.string() ?: return emptyList()
                Log.d(TAG, "Loans JSON response: ${bodyText.take(1000)}")
                
                return parseLoansJsonResponse(bodyText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchLoansFromJsonApi error", e)
            return emptyList()
        }
    }
    
    /**
     * Parse the JSON response from /account/loans.json
     * Response format: { "loans": [ { "_key", "book", "ocaid", "expiry", "loaned_at", ... } ] }
     */
    private suspend fun parseLoansJsonResponse(jsonText: String): List<OpenLibraryLoan> {
        val loans = mutableListOf<OpenLibraryLoan>()
        
        try {
            val json = JSONObject(jsonText)
            val loansArray = json.optJSONArray("loans") ?: return emptyList()
            
            Log.d(TAG, "Found ${loansArray.length()} loans in JSON response")
            
            for (i in 0 until loansArray.length()) {
                val loanObj = loansArray.optJSONObject(i) ?: continue
                
                // Extract loan data
                val bookKey = loanObj.optString("book", "") // e.g., "/books/OL12345M"
                val ocaid = loanObj.optString("ocaid", "") // Internet Archive identifier
                val expiry = loanObj.optString("expiry", "")
                val loanedAt = loanObj.optLong("loaned_at", 0)
                
                // Convert loaned_at timestamp to readable date
                val loanDate = if (loanedAt > 0) {
                    try {
                        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                        sdf.format(java.util.Date(loanedAt * 1000))
                    } catch (e: Exception) { "" }
                } else ""
                
                // We need to fetch book details to get title, author, cover
                val bookDetails = if (bookKey.isNotEmpty()) {
                    fetchBookDetails(bookKey)
                } else null
                
                val loan = OpenLibraryLoan(
                    workKey = bookDetails?.first ?: bookKey, // workKey or bookKey
                    editionKey = bookKey.removePrefix("/books/"),
                    title = bookDetails?.second ?: "Borrowed Book",
                    author = bookDetails?.third ?: "Unknown Author",
                    coverId = bookDetails?.fourth,
                    loanDate = loanDate,
                    expireDate = expiry
                )
                
                // If we couldn't get details but have ocaid, try IA cover
                if (loan.title == "Borrowed Book" && ocaid.isNotEmpty()) {
                    loans.add(loan.copy(
                        title = ocaid.replace("_", " ").replaceFirstChar { it.uppercase() },
                        editionKey = ocaid
                    ))
                } else {
                    loans.add(loan)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing loans JSON", e)
        }
        
        return loans
    }
    
    /**
     * Fetch book details (title, author, cover) from book key
     * Returns: Triple of (workKey, title, author, coverId)
     */
    private fun fetchBookDetails(bookKey: String): Quadruple<String, String, String, Int?>? {
        try {
            val request = Request.Builder()
                .url("$BASE_URL$bookKey.json")
                .header("User-Agent", "ScribeApp/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                
                val bodyText = response.body?.string() ?: return null
                val json = JSONObject(bodyText)
                
                val title = json.optString("title", "Unknown Title")
                
                // Get work key if available
                val works = json.optJSONArray("works")
                val workKey = works?.optJSONObject(0)?.optString("key", "") ?: ""
                
                // Get author - might be in different formats
                var author = "Unknown Author"
                val authors = json.optJSONArray("authors")
                if (authors != null && authors.length() > 0) {
                    val authorObj = authors.optJSONObject(0)
                    val authorKey = authorObj?.optString("key", "") 
                        ?: authorObj?.optJSONObject("author")?.optString("key", "")
                    if (!authorKey.isNullOrEmpty()) {
                        author = fetchAuthorName(authorKey) ?: "Unknown Author"
                    }
                }
                
                // Get cover ID
                val covers = json.optJSONArray("covers")
                val coverId = covers?.optInt(0)?.takeIf { it > 0 }
                
                return Quadruple(workKey, title, author, coverId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchBookDetails error for $bookKey", e)
            return null
        }
    }
    
    /**
     * Fetch author name from author key
     */
    private fun fetchAuthorName(authorKey: String): String? {
        try {
            val request = Request.Builder()
                .url("$BASE_URL$authorKey.json")
                .header("User-Agent", "ScribeApp/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                
                val bodyText = response.body?.string() ?: return null
                val json = JSONObject(bodyText)
                
                return json.optString("name", null) 
                    ?: json.optString("personal_name", null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAuthorName error", e)
            return null
        }
    }
    
    /**
     * Fetch loan history as fallback
     */
    private suspend fun fetchLoanHistory(sessionCookie: String): List<OpenLibraryLoan> {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/account/loan-history.json")
                .header("Cookie", "session=$sessionCookie")
                .header("User-Agent", "ScribeApp/1.0 (Android)")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                
                val bodyText = response.body?.string() ?: return emptyList()
                Log.d(TAG, "Loan history response: ${bodyText.take(500)}")
                
                // Parse similar to loans.json
                return parseLoansJsonResponse(bodyText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchLoanHistory error", e)
            return emptyList()
        }
    }
    
    // Helper class since Kotlin doesn't have Quadruple
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    /**
     * Get user's "Want to Read" list
     */
    suspend fun getWantToRead(username: String): List<OpenLibraryListItem> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/people/$username/books/want-to-read.json")
                .header("User-Agent", "ScribeApp/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                parseReadingLogResponse(response.body?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get want to read error", e)
            return@withContext emptyList()
        }
    }

    /**
     * Get user's "Already Read" list
     */
    suspend fun getAlreadyRead(username: String): List<OpenLibraryListItem> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/people/$username/books/already-read.json")
                .header("User-Agent", "ScribeApp/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                parseReadingLogResponse(response.body?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get already read error", e)
            return@withContext emptyList()
        }
    }

    /**
     * Get user's "Currently Reading" list
     */
    suspend fun getCurrentlyReading(username: String): List<OpenLibraryListItem> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/people/$username/books/currently-reading.json")
                .header("User-Agent", "ScribeApp/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                parseReadingLogResponse(response.body?.string())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get currently reading error", e)
            return@withContext emptyList()
        }
    }

    /**
     * Check if a book is in user's reading list
     */
    suspend fun checkBookStatus(sessionCookie: String, workKey: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL${workKey}.json")
                .header("Cookie", "session=$sessionCookie")
                .header("User-Agent", "ScribeApp/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                // The reading status is typically in a separate endpoint
                // or embedded in the page when user is logged in
                return@use null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check book status error", e)
            return@withContext null
        }
    }

    /**
     * Get the correct borrow URL for a book edition
     * The URL format should be: https://openlibrary.org/books/OL{id}M/{title}
     */
    fun getBorrowUrl(editionKey: String): String {
        // editionKey might be like "OL9219606M" or "/books/OL9219606M"
        val cleanKey = editionKey.removePrefix("/books/").removePrefix("/")
        return "$BASE_URL/books/$cleanKey"
    }

    private fun parseReadingLogResponse(bodyText: String?): List<OpenLibraryListItem> {
        if (bodyText == null) return emptyList()
        
        try {
            val json = JSONObject(bodyText)
            val readingLog = json.optJSONArray("reading_log_entries") ?: return emptyList()
            
            val items = mutableListOf<OpenLibraryListItem>()
            for (i in 0 until readingLog.length()) {
                val entry = readingLog.getJSONObject(i)
                val work = entry.optJSONObject("work") ?: continue
                
                items.add(OpenLibraryListItem(
                    workKey = work.optString("key", ""),
                    title = work.optString("title", "Unknown"),
                    author = work.optJSONArray("author_names")?.optString(0) ?: "Unknown Author",
                    coverId = work.optInt("cover_id").takeIf { it > 0 },
                    dateAdded = entry.optString("logged_date", "")
                ))
            }
            return items
        } catch (e: Exception) {
            Log.e(TAG, "Parse reading log error", e)
            return emptyList()
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
}
