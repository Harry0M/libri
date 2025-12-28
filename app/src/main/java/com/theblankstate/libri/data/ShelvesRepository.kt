package com.theblankstate.libri.data

import android.util.Log
import com.theblankstate.libri.datamodel.BookShelfRelation
import com.theblankstate.libri.datamodel.LibraryBook
import com.theblankstate.libri.datamodel.Shelf
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ShelvesRepository {
    private val database = FirebaseDatabase.getInstance()
    private val shelvesRef = database.getReference("shelves")
    private val shelfBooksRef = database.getReference("shelf_books")
    private val libraryRef = database.getReference("library")

    /**
     * Get all shelves for a user with real-time updates
     */
    fun getUserShelves(uid: String): Flow<List<Shelf>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val shelves = mutableListOf<Shelf>()
                for (child in snapshot.children) {
                    val shelf = try {
                        child.getValue(Shelf::class.java)
                    } catch (e: Exception) {
                        Log.w("ShelvesRepository", "Failed to parse shelf", e)
                        null
                    }
                    shelf?.let { shelves.add(it) }
                }
                trySend(shelves.sortedByDescending { it.dateCreated })
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        shelvesRef.child(uid).addValueEventListener(listener)

        awaitClose {
            shelvesRef.child(uid).removeEventListener(listener)
        }
    }

    /**
     * Create a new shelf
     */
    suspend fun createShelf(uid: String, shelf: Shelf): Result<Unit> {
        return try {
            shelvesRef.child(uid).child(shelf.id).setValue(shelf).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ShelvesRepository", "Failed to create shelf", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a shelf and all its book relations
     */
    suspend fun deleteShelf(uid: String, shelfId: String): Result<Unit> {
        return try {
            // Delete the shelf
            shelvesRef.child(uid).child(shelfId).removeValue().await()
            // Delete all book relations for this shelf
            shelfBooksRef.child(uid).child(shelfId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ShelvesRepository", "Failed to delete shelf", e)
            Result.failure(e)
        }
    }

    /**
     * Update shelf information
     */
    suspend fun updateShelf(uid: String, shelf: Shelf): Result<Unit> {
        return try {
            val updates = mapOf(
                "name" to shelf.name,
                "description" to shelf.description
            )
            shelvesRef.child(uid).child(shelf.id).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ShelvesRepository", "Failed to update shelf", e)
            Result.failure(e)
        }
    }

    /**
     * Add a book to a shelf
     */
    suspend fun addBookToShelf(uid: String, bookId: String, shelfId: String): Result<Unit> {
        return try {
            val relation = BookShelfRelation(
                bookId = bookId,
                shelfId = shelfId,
                dateAdded = System.currentTimeMillis()
            )
            shelfBooksRef.child(uid).child(shelfId).child(bookId).setValue(relation).await()
            
            // Update book count
            updateBookCount(uid, shelfId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ShelvesRepository", "Failed to add book to shelf", e)
            Result.failure(e)
        }
    }

    /**
     * Remove a book from a shelf
     */
    suspend fun removeBookFromShelf(uid: String, bookId: String, shelfId: String): Result<Unit> {
        return try {
            shelfBooksRef.child(uid).child(shelfId).child(bookId).removeValue().await()
            
            // Update book count
            updateBookCount(uid, shelfId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ShelvesRepository", "Failed to remove book from shelf", e)
            Result.failure(e)
        }
    }

    /**
     * Get all books in a specific shelf
     */
    fun getBooksInShelf(uid: String, shelfId: String): Flow<List<LibraryBook>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val bookIds = mutableListOf<String>()
                for (child in snapshot.children) {
                    val relation = try {
                        child.getValue(BookShelfRelation::class.java)
                    } catch (e: Exception) {
                        Log.w("ShelvesRepository", "Failed to parse book relation", e)
                        null
                    }
                    relation?.bookId?.let { bookIds.add(it) }
                }
                
                // Fetch book details from library
                fetchBookDetails(uid, bookIds) { books ->
                    trySend(books.sortedByDescending { book ->
                        // Sort by date added to shelf
                        snapshot.children.find { 
                            it.getValue(BookShelfRelation::class.java)?.bookId == book.id 
                        }?.getValue(BookShelfRelation::class.java)?.dateAdded ?: 0L
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        shelfBooksRef.child(uid).child(shelfId).addValueEventListener(listener)

        awaitClose {
            shelfBooksRef.child(uid).child(shelfId).removeEventListener(listener)
        }
    }

    /**
     * Get all shelves that contain a specific book
     */
    fun getShelvesForBook(uid: String, bookId: String): Flow<List<Shelf>> = callbackFlow {
        val shelfBooksListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val shelfIds = mutableListOf<String>()
                
                // Find all shelves containing this book
                for (shelfSnapshot in snapshot.children) {
                    val shelfId = shelfSnapshot.key ?: continue
                    if (shelfSnapshot.child(bookId).exists()) {
                        shelfIds.add(shelfId)
                    }
                }
                
                // Fetch shelf details
                fetchShelfDetails(uid, shelfIds) { shelves ->
                    trySend(shelves)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        shelfBooksRef.child(uid).addValueEventListener(shelfBooksListener)

        awaitClose {
            shelfBooksRef.child(uid).removeEventListener(shelfBooksListener)
        }
    }

    /**
     * Check if a book is in a specific shelf
     */
    suspend fun isBookInShelf(uid: String, bookId: String, shelfId: String): Boolean {
        return try {
            val snapshot = shelfBooksRef.child(uid).child(shelfId).child(bookId).get().await()
            snapshot.exists()
        } catch (e: Exception) {
            Log.e("ShelvesRepository", "Failed to check if book is in shelf", e)
            false
        }
    }

    /**
     * Get a single shelf by ID
     */
    suspend fun getShelf(uid: String, shelfId: String): Shelf? {
        return try {
            val snapshot = shelvesRef.child(uid).child(shelfId).get().await()
            snapshot.getValue(Shelf::class.java)
        } catch (e: Exception) {
            Log.e("ShelvesRepository", "Failed to get shelf", e)
            null
        }
    }

    // Helper functions

    private suspend fun updateBookCount(uid: String, shelfId: String) {
        try {
            val snapshot = shelfBooksRef.child(uid).child(shelfId).get().await()
            val count = snapshot.childrenCount.toInt()
            shelvesRef.child(uid).child(shelfId).child("bookCount").setValue(count).await()
        } catch (e: Exception) {
            Log.e("ShelvesRepository", "Failed to update book count", e)
        }
    }

    private fun fetchBookDetails(uid: String, bookIds: List<String>, onResult: (List<LibraryBook>) -> Unit) {
        if (bookIds.isEmpty()) {
            onResult(emptyList())
            return
        }

        libraryRef.child(uid).get().addOnSuccessListener { snapshot ->
            val books = mutableListOf<LibraryBook>()
            for (bookId in bookIds) {
                val bookSnapshot = snapshot.child(bookId)
                if (bookSnapshot.exists()) {
                    val book = try {
                        bookSnapshot.getValue(LibraryBook::class.java)
                    } catch (e: Exception) {
                        Log.w("ShelvesRepository", "Failed to parse book", e)
                        LibrarySnapshotParser.buildLibraryBookFromSnapshot(bookSnapshot)
                    }
                    book?.let { books.add(it) }
                }
            }
            onResult(books)
        }.addOnFailureListener { e ->
            Log.e("ShelvesRepository", "Failed to fetch book details", e)
            onResult(emptyList())
        }
    }

    private fun fetchShelfDetails(uid: String, shelfIds: List<String>, onResult: (List<Shelf>) -> Unit) {
        if (shelfIds.isEmpty()) {
            onResult(emptyList())
            return
        }

        shelvesRef.child(uid).get().addOnSuccessListener { snapshot ->
            val shelves = mutableListOf<Shelf>()
            for (shelfId in shelfIds) {
                val shelfSnapshot = snapshot.child(shelfId)
                if (shelfSnapshot.exists()) {
                    val shelf = try {
                        shelfSnapshot.getValue(Shelf::class.java)
                    } catch (e: Exception) {
                        Log.w("ShelvesRepository", "Failed to parse shelf", e)
                        null
                    }
                    shelf?.let { shelves.add(it) }
                }
            }
            onResult(shelves)
        }.addOnFailureListener { e ->
            Log.e("ShelvesRepository", "Failed to fetch shelf details", e)
            onResult(emptyList())
        }
    }

    /**
     * Remove a book from all shelves (used when deleting book from library)
     */
    suspend fun removeBookFromAllShelves(uid: String, bookId: String): Result<Unit> {
        return try {
            // Get all shelf-book relations for this user
            val snapshot = shelfBooksRef.child(uid).get().await()
            
            Log.d("ShelvesRepository", "removeBookFromAllShelves: bookId=$bookId, found ${snapshot.childrenCount} shelves")
            
            // Find all shelves containing this book and remove it
            for (shelfSnapshot in snapshot.children) {
                val shelfId = shelfSnapshot.key ?: continue
                if (shelfSnapshot.child(bookId).exists()) {
                    Log.d("ShelvesRepository", "Removing book $bookId from shelf $shelfId")
                    // Remove the book from this shelf
                    shelfBooksRef.child(uid).child(shelfId).child(bookId).removeValue().await()
                    // Update book count for this shelf
                    updateBookCount(uid, shelfId)
                    Log.d("ShelvesRepository", "Updated book count for shelf $shelfId")
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ShelvesRepository", "Failed to remove book from all shelves", e)
            Result.failure(e)
        }
    }
}
