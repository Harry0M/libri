package com.theblankstate.libri.data

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

data class TermsAcceptance(
    val timestamp: Long = 0,
    val uid: String = "",
    val email: String = "",
    val acceptedVersion: String = "1.0"
)

class TermsRepository {
    private val database = FirebaseDatabase.getInstance()
    private val termsRef = database.getReference("terms_acceptances")
    
    suspend fun recordTermsAcceptance(uid: String, email: String): Result<Unit> {
        return try {
            val acceptance = TermsAcceptance(
                timestamp = System.currentTimeMillis(),
                uid = uid,
                email = email,
                acceptedVersion = "1.0"
            )
            termsRef.child(uid).push().setValue(acceptance).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
