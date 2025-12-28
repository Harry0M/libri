package com.theblankstate.libri.data_retrieval

import android.content.Context
import android.content.SharedPreferences
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.theblankstate.libri.datamodel.DownloadedBook
import com.theblankstate.libri.datamodel.BookFormat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream

class DownloadsRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("downloads_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // In-memory cache to avoid repeated JSON parsing on main thread
    @Volatile
    private var cachedBooks: List<DownloadedBook>? = null
    private var cacheTimestamp: Long = 0L
    private val cacheValidityMs = 5000L // 5 seconds cache validity

    fun getDownloadedBooks(): List<DownloadedBook> {
        val now = System.currentTimeMillis()
        cachedBooks?.let { cached ->
            if (now - cacheTimestamp < cacheValidityMs) {
                return cached
            }
        }
        
        val json = prefs.getString("downloaded_books", null) ?: return emptyList()
        val type = object : TypeToken<List<DownloadedBook>>() {}.type
        val books: List<DownloadedBook> = gson.fromJson(json, type)
        cachedBooks = books
        cacheTimestamp = now
        return books
    }
    
    private fun invalidateCache() {
        cachedBooks = null
        cacheTimestamp = 0L
    }

    fun saveBook(book: DownloadedBook) {
        val currentList = getDownloadedBooks().toMutableList()
        // Remove existing if any to update it
        currentList.removeAll { it.id == book.id }
        currentList.add(0, book) // Add to top
        
        val json = gson.toJson(currentList)
        prefs.edit().putString("downloaded_books", json).apply()
        invalidateCache()
    }

    fun removeBook(bookId: String) {
        val currentList = getDownloadedBooks().toMutableList()
        currentList.removeAll { it.id == bookId }
        val json = gson.toJson(currentList)
        prefs.edit().putString("downloaded_books", json).apply()
        invalidateCache()
    }
    
    fun isBookDownloaded(bookId: String): Boolean {
        return getDownloadedBooks().any { it.id == bookId }
    }

    fun saveFileToDownloads(
        filename: String,
        mimeType: String,
        inputStream: InputStream,
        subFolder: String
    ): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            val relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + subFolder.trim('/')
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val uri = resolver.insert(collection, contentValues)

        uri?.let { outputUri ->
            try {
                resolver.openOutputStream(outputUri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(outputUri, contentValues, null, null)
                }
                return outputUri
            } catch (e: Exception) {
                e.printStackTrace()
                resolver.delete(outputUri, null, null)
                return null
            }
        }
        return null
    }

    fun importBook(sourceUri: Uri, title: String): DownloadedBook? {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val mimeType = try { context.contentResolver.getType(sourceUri) } catch (e: Exception) { null }
            val path = sourceUri.path?.lowercase() ?: ""
            val format = when {
                mimeType?.contains("epub") == true || path.endsWith(".epub") -> BookFormat.EPUB
                mimeType?.contains("pdf") == true || path.endsWith(".pdf") -> BookFormat.PDF
                else -> BookFormat.PDF
            }
            val filename = "${title.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.${format.extension}"
            
            val savedUri = saveFileToDownloads(
                filename = filename,
                mimeType = format.mimeType,
                inputStream = inputStream,
                subFolder = "Scribe/Import"
            )
            
            if (savedUri != null) {
                val book = DownloadedBook(
                    id = java.util.UUID.randomUUID().toString(),
                    title = title,
                    author = "Imported",
                    coverUrl = null,
                    filePath = savedUri.toString(),
                    fileUri = savedUri.toString()
                    ,format = format
                )
                saveBook(book)
                return book
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
