package com.theblankstate.libri.data

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.theblankstate.libri.datamodel.LibraryBook
import com.theblankstate.libri.datamodel.ReadingStatus

class LibraryRepository {
    private val database = FirebaseDatabase.getInstance()
    private val libraryRef = database.getReference("library")
    
    fun getLibraryBooks(uid: String): Flow<List<LibraryBook>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val books = mutableListOf<LibraryBook>()
                for (child in snapshot.children) {
                    // Try the normal POJO mapping first, but fallback to manual parsing for invalid enum values
                    val book = try {
                        child.getValue(LibraryBook::class.java)
                    } catch (e: Exception) {
                        // Log and fallback
                        Log.w("LibraryRepository", "Failed to map LibraryBook via Firebase POJO; falling back to manual parsing for key=${child.key}", e)
                        LibrarySnapshotParser.buildLibraryBookFromSnapshot(child)
                    }
                    book?.let { books.add(it) }
                }
                trySend(books.sortedByDescending { it.dateAdded })
            }
            
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        
        libraryRef.child(uid).addValueEventListener(listener)
        
        awaitClose {
            libraryRef.child(uid).removeEventListener(listener)
        }
    }
    
    suspend fun addBookToLibrary(uid: String, book: LibraryBook): Result<Unit> {
        return try {
            libraryRef.child(uid).child(book.id).setValue(book).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun removeBookFromLibrary(uid: String, bookId: String, shelvesRepository: ShelvesRepository? = null): Result<Unit> {
        return try {
            // Remove book from library
            libraryRef.child(uid).child(bookId).removeValue().await()
            
            // Remove book from all shelves if repository is provided
            shelvesRepository?.removeBookFromAllShelves(uid, bookId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateReadingStatus(uid: String, bookId: String, status: ReadingStatus): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>("status" to status.name)
            
            when (status) {
                ReadingStatus.IN_PROGRESS -> {
                    if (getBook(uid, bookId)?.dateStarted == null) {
                        updates["dateStarted"] = System.currentTimeMillis()
                    }
                }
                ReadingStatus.FINISHED -> {
                    updates["dateFinished"] = System.currentTimeMillis()
                    updates["currentPage"] = getBook(uid, bookId)?.totalPages ?: 0
                }
                else -> {}
            }
            
            libraryRef.child(uid).child(bookId).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateReadingProgress(uid: String, bookId: String, currentPage: Int, totalPages: Int): Result<Unit> {
        return try {
            val updates = mapOf(
                "currentPage" to currentPage,
                "totalPages" to totalPages
            )
            libraryRef.child(uid).child(bookId).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateRating(uid: String, bookId: String, rating: Float): Result<Unit> {
        return try {
            libraryRef.child(uid).child(bookId).child("rating").setValue(rating).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateComment(uid: String, bookId: String, comment: String): Result<Unit> {
        return try {
            libraryRef.child(uid).child(bookId).child("comment").setValue(comment).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun toggleFavorite(uid: String, bookId: String): Result<Unit> {
        return try {
            val book = getBook(uid, bookId)
            val newFavoriteStatus = !(book?.isFavorite ?: false)
            libraryRef.child(uid).child(bookId).child("isFavorite").setValue(newFavoriteStatus).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getBook(uid: String, bookId: String): LibraryBook? {
        return try {
            val snapshot = libraryRef.child(uid).child(bookId).get().await()
            try {
                snapshot.getValue(LibraryBook::class.java)
            } catch (e: Exception) {
                Log.w("LibraryRepository", "Failed to map single LibraryBook via Firebase POJO for bookId=$bookId; falling back to manual parsing", e)
                LibrarySnapshotParser.buildLibraryBookFromSnapshot(snapshot)
            }
        } catch (e: Exception) {
            null
        }
    }

    // NOTE: buildFromSnapshot and parseBookFormat are moved to LibrarySnapshotParser for reuse & testing
    
    suspend fun isBookInLibrary(uid: String, bookId: String): Boolean {
        return try {
            val snapshot = libraryRef.child(uid).child(bookId).get().await()
            snapshot.exists()
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun updateIsbn(uid: String, bookId: String, isbn: String): Result<Unit> {
        return try {
            libraryRef.child(uid).child(bookId).child("isbn").setValue(isbn).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
