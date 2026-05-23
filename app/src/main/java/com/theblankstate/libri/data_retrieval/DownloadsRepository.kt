package com.theblankstate.libri.data_retrieval

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.theblankstate.libri.datamodel.DownloadedBook
import com.theblankstate.libri.datamodel.BookFormat
import com.theblankstate.libri.datamodel.BookSource
import com.theblankstate.libri.util.BookFileUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream

class DownloadsRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("downloads_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // In-memory cache to avoid repeated JSON parsing on main thread
    @Volatile
    private var cachedBooks: List<DownloadedBook>? = null
    @Volatile
    private var cachedBookIds: Set<String>? = null
    @Volatile
    private var cachedGutenbergIds: Set<Int>? = null
    private var cacheTimestamp: Long = 0L
    private val cacheValidityMs = 5000L // 5 seconds cache validity

    companion object {
        const val DOWNLOADS_SUBFOLDER = "Scribe/Books"
        const val OFFLINE_BOOKS_SUBFOLDER = "Scribe/Import"
    }

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
        cachedBookIds = books.mapTo(HashSet()) { it.id }
        cachedGutenbergIds = books.mapNotNull { it.gutenbergId }.toHashSet()
        cacheTimestamp = now
        return books
    }
    
    private fun invalidateCache() {
        cachedBooks = null
        cachedBookIds = null
        cachedGutenbergIds = null
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
        // Ensure cache is populated
        if (cachedBookIds == null) getDownloadedBooks()
        return cachedBookIds?.contains(bookId) ?: false
    }
    
    fun isGutenbergBookDownloaded(gutenbergId: Int): Boolean {
        // Ensure cache is populated
        if (cachedGutenbergIds == null) getDownloadedBooks()
        return cachedGutenbergIds?.contains(gutenbergId) ?: false
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
            val format = BookFileUtils.detectFormat(context, sourceUri) ?: return null
            val resolvedTitle = title.ifBlank { BookFileUtils.titleFromUri(context, sourceUri) }
            val filename = BookFileUtils.safeFilename(resolvedTitle, format)
            
            val savedUri = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                saveFileToDownloads(
                    filename = filename,
                    mimeType = format.mimeType,
                    inputStream = inputStream,
                    subFolder = OFFLINE_BOOKS_SUBFOLDER
                )
            }
            
            if (savedUri != null) {
                val book = DownloadedBook(
                    id = BookFileUtils.stableLocalId(savedUri),
                    title = resolvedTitle,
                    author = "Local book",
                    coverUrl = null,
                    filePath = savedUri.toString(),
                    fileUri = savedUri.toString(),
                    format = format,
                    source = BookSource.LOCAL_IMPORT
                )
                saveBook(book)
                return book
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun persistReadableTree(treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun scanPersistedFolders(): List<DownloadedBook> {
        return context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .flatMap { permission -> scanFolder(permission.uri, persistAccess = false) }
    }

    fun scanFolder(treeUri: Uri, persistAccess: Boolean = true): List<DownloadedBook> {
        if (persistAccess) persistReadableTree(treeUri)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val discovered = mutableListOf<DownloadedBook>()
        walkDocumentTree(root, discovered)
        saveDiscoveredBooks(discovered)
        return discovered
    }

    fun scanDeviceBooks(): List<DownloadedBook> {
        val discovered = mutableListOf<DownloadedBook>()
        discovered += scanPersistedFolders()

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            discovered += scanMediaStoreFiles()
        }

        val unique = discovered.distinctBy { it.fileUri ?: it.filePath }
        saveDiscoveredBooks(unique)
        return unique
    }

    private fun walkDocumentTree(file: DocumentFile, output: MutableList<DownloadedBook>) {
        if (file.isDirectory) {
            file.listFiles().forEach { child -> walkDocumentTree(child, output) }
            return
        }

        val name = file.name ?: return
        val format = BookFileUtils.detectFormat(file.type, name) ?: return
        val uri = file.uri
        output += DownloadedBook(
            id = BookFileUtils.stableLocalId(uri),
            title = BookFileUtils.titleFromName(name),
            author = "Local book",
            coverUrl = null,
            filePath = uri.toString(),
            timestamp = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
            fileUri = uri.toString(),
            format = format,
            source = BookSource.LOCAL_IMPORT
        )
    }

    private fun scanMediaStoreFiles(): List<DownloadedBook> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val selection = listOf(
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
            "${MediaStore.Files.FileColumns.MIME_TYPE} IN (?, ?, ?, ?, ?)"
        ).joinToString(" OR ", prefix = "(", postfix = ")")
        val args = arrayOf(
            "%.pdf",
            "%.epub",
            "%.txt",
            "%.html",
            "%.htm",
            "%.xhtml",
            "application/pdf",
            "application/epub+zip",
            "text/plain",
            "text/html",
            "application/xhtml+xml"
        )

        val discovered = mutableListOf<DownloadedBook>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: continue
                val mimeType = cursor.getString(mimeColumn)
                val format = BookFileUtils.detectFormat(mimeType, name) ?: continue
                val uri = ContentUris.withAppendedId(collection, id)
                val modifiedSeconds = cursor.getLong(modifiedColumn)
                discovered += DownloadedBook(
                    id = BookFileUtils.stableLocalId(uri),
                    title = BookFileUtils.titleFromName(name),
                    author = "Local book",
                    coverUrl = null,
                    filePath = uri.toString(),
                    timestamp = if (modifiedSeconds > 0L) modifiedSeconds * 1000L else System.currentTimeMillis(),
                    fileUri = uri.toString(),
                    format = format,
                    source = BookSource.LOCAL_IMPORT
                )
            }
        }
        return discovered
    }

    private fun saveDiscoveredBooks(discovered: List<DownloadedBook>) {
        if (discovered.isEmpty()) return
        val current = getDownloadedBooks().toMutableList()
        val existingUris = current.mapTo(HashSet()) { it.fileUri ?: it.filePath }
        val newBooks = discovered.filter { (it.fileUri ?: it.filePath) !in existingUris }
        if (newBooks.isEmpty()) return

        current.addAll(0, newBooks)
        prefs.edit().putString("downloaded_books", gson.toJson(current)).apply()
        invalidateCache()
    }
}
