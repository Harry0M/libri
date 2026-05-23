package com.theblankstate.libri.datamodel

import com.google.firebase.database.IgnoreExtraProperties

/**
 * A public book review that other users can see.
 * Stored at: reviews/{isbn}/{uid}
 */
@IgnoreExtraProperties
data class BookReview(
    val uid: String = "",
    val userName: String = "",
    val rating: Float = 0f,
    val reviewText: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val updatedAt: Long = timestamp,
    val reviewKey: String = "",
    val bookIdentityType: String = "",
    val bookTitle: String = "",
    val bookAuthor: String = "",
    val bookCoverUrl: String? = null
)
