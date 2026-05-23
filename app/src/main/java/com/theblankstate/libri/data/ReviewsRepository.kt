package com.theblankstate.libri.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.theblankstate.libri.datamodel.BookReview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for public book reviews.
 * Reviews are stored at: reviews/{isbn}/{uid}
 * This allows any user to see reviews for a book by its ISBN.
 */
class ReviewsRepository {
    private val database = FirebaseDatabase.getInstance()
    private val reviewsRef = database.getReference("reviews")

    fun getReviewsForBook(reviewKey: String, legacyIsbn: String?): Flow<List<BookReview>> = callbackFlow {
        val refs = listOfNotNull(
            reviewKey.takeIf { it.isNotBlank() }?.let { reviewsRef.child(it) },
            legacyIsbn?.takeIf { it.isNotBlank() && it != reviewKey }?.let { reviewsRef.child(it) }
        ).distinctBy { it.key }

        val snapshots = mutableMapOf<String, List<BookReview>>()
        fun publish() {
            val merged = snapshots.values.flatten()
                .groupBy { it.uid }
                .map { (_, reviews) -> reviews.maxBy { it.timestamp } }
                .sortedByDescending { it.timestamp }
            trySend(merged)
        }

        val listeners = refs.map { ref ->
            val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reviews = mutableListOf<BookReview>()
                for (child in snapshot.children) {
                    try {
                        child.getValue(BookReview::class.java)?.let { reviews.add(it) }
                    } catch (e: Exception) {
                        Log.w("ReviewsRepository", "Failed to parse review: ${child.key}", e)
                    }
                }
                    snapshots[ref.key.orEmpty()] = reviews
                    publish()
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
            ref.addValueEventListener(listener)
            ref to listener
        }

        awaitClose {
            listeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        }
    }

    /**
     * Get all reviews for a book by ISBN as a live Flow.
     */
    fun getReviewsForBook(isbn: String): Flow<List<BookReview>> = getReviewsForBook(
        reviewKey = isbn,
        legacyIsbn = null
    )

    suspend fun submitReview(reviewKey: String, review: BookReview, legacyIsbn: String?): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            reviewsRef.child(reviewKey).child(review.uid)
                .setValue(review.copy(reviewKey = reviewKey, updatedAt = now)).await()
            if (!legacyIsbn.isNullOrBlank() && legacyIsbn != reviewKey) {
                reviewsRef.child(legacyIsbn).child(review.uid).removeValue().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submit or update a review for a book.
     */
    suspend fun submitReview(isbn: String, review: BookReview): Result<Unit> {
        return try {
            reviewsRef.child(isbn).child(review.uid).setValue(review).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteReview(reviewKey: String, uid: String, legacyIsbn: String?): Result<Unit> {
        return try {
            reviewsRef.child(reviewKey).child(uid).removeValue().await()
            if (!legacyIsbn.isNullOrBlank() && legacyIsbn != reviewKey) {
                reviewsRef.child(legacyIsbn).child(uid).removeValue().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a review (user can only delete their own).
     */
    suspend fun deleteReview(isbn: String, uid: String): Result<Unit> {
        return try {
            reviewsRef.child(isbn).child(uid).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the average rating for a book across all reviews.
     */
    suspend fun getAverageRating(isbn: String): Float {
        return try {
            val snapshot = reviewsRef.child(isbn).get().await()
            val ratings = snapshot.children.mapNotNull {
                it.getValue(BookReview::class.java)?.rating
            }
            if (ratings.isEmpty()) 0f else ratings.average().toFloat()
        } catch (e: Exception) {
            0f
        }
    }
}
