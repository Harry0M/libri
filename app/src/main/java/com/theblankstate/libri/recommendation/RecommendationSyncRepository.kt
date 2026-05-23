package com.theblankstate.libri.recommendation

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.theblankstate.libri.data.UserPreferencesRepository
import kotlinx.coroutines.tasks.await

class RecommendationSyncRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val preferences = UserPreferencesRepository(appContext)

    suspend fun syncHomeRecommendations(
        profile: RecommendationProfileSnapshot,
        sections: List<RecommendationSectionSnapshot>,
        events: List<RecommendationEvent>
    ) {
        if (!preferences.isRecommendationSyncEnabled()) return
        val uid = auth.currentUser?.uid ?: preferences.getGoogleUser().third ?: return
        val userRef = database.getReference("users").child(uid)

        val profilePayload = mapOf(
            "authorWeights" to profile.authorWeights,
            "subjectWeights" to profile.subjectWeights,
            "languageWeights" to profile.languageWeights,
            "recentBookIds" to profile.recentBookIds,
            "hiddenItemIds" to profile.hiddenItemIds,
            "finishedItemIds" to profile.finishedItemIds,
            "updatedAt" to profile.updatedAt
        )

        val snapshotPayload = mapOf(
            "updatedAt" to System.currentTimeMillis(),
            "sections" to sections.map { section ->
                mapOf(
                    "title" to section.title,
                    "items" to section.items.ifEmpty {
                        section.books.map { book ->
                            RecommendationBookSnapshot(
                                id = RecommendationEngine.canonicalId(book),
                                title = book.title,
                                authors = book.author_name.orEmpty(),
                                subjects = book.subject.orEmpty().take(8),
                                languages = book.language.orEmpty(),
                                source = RecommendationEngine.sourceFor(book).wireName,
                                coverUrl = book.coverUrl,
                                scoreReason = RecommendationEngine.reasonFor(book, section.title)
                            )
                        }
                    }.map { item ->
                        mapOf(
                            "id" to item.id,
                            "title" to item.title,
                            "authors" to item.authors.take(4),
                            "subjects" to item.subjects.take(8),
                            "languages" to item.languages.take(4),
                            "source" to item.source,
                            "coverUrl" to item.coverUrl,
                            "scoreReason" to item.scoreReason
                        )
                    }
                )
            }
        )

        val eventsPayload = events.take(25).associate { event ->
            event.eventId to mapOf(
                "type" to event.type.name,
                "itemId" to event.itemId,
                "source" to event.source.wireName,
                "title" to event.title,
                "authors" to event.authors.take(4),
                "subjects" to event.subjects.take(8),
                "languages" to event.languages.take(4),
                "value" to event.value,
                "query" to event.query,
                "timestamp" to event.timestamp
            )
        }

        val updates = mapOf<String, Any>(
            "recommendationProfile" to profilePayload,
            "recommendationSnapshots/home" to snapshotPayload,
            "recommendationEvents" to eventsPayload
        )
        userRef.updateChildren(updates).await()
    }

    suspend fun clearRemoteRecommendationData() {
        val uid = auth.currentUser?.uid ?: preferences.getGoogleUser().third ?: return
        val userRef = database.getReference("users").child(uid)
        val updates = mapOf<String, Any?>(
            "recommendationProfile" to null,
            "recommendationSnapshots" to null,
            "recommendationEvents" to null
        )
        userRef.updateChildren(updates).await()
    }
}
