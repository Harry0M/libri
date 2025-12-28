package com.theblankstate.libri.data_retrieval

import com.theblankstate.libri.datamodel.WorkDetailModel
import com.theblankstate.libri.datamodel.bookModel

class repository {

    private val api = retrofitinatance.api

    suspend fun getbooks(
        query: String? = null,
        title: String? = null,
        author: String? = null,
        subject: String? = null,
        isbn: String? = null,
        publisher: String? = null,
        language: String? = null,
        sort: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): List<bookModel> {
        return try {
            // Build query string for year range if needed
            val response = api.getbooks(
                query = query,
                title = title,
                author = author,
                subject = subject,
                isbn = isbn,
                publisher = publisher,
                language = language,
                sort = sort,
                limit = limit,
                offset = offset
            )
            response.docs
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getWorkDetails(workId: String): WorkDetailModel? {
        return try {
            api.getWorkDetails(workId)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getEditions(workId: String): List<com.theblankstate.libri.datamodel.EditionModel> {
        return try {
            api.getEditions(workId).entries
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getEditionsPaged(workId: String, limit: Int = 20, offset: Int = 0): List<com.theblankstate.libri.datamodel.EditionModel> {
        return try {
            api.getEditions(workId, limit = limit, offset = offset).entries
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getRatings(workId: String): com.theblankstate.libri.datamodel.RatingsModel? {
        return try {
            api.getRatings(workId)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getBookshelves(workId: String): com.theblankstate.libri.datamodel.BookshelfModel? {
        return try {
            api.getBookshelves(workId)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getEditionDetails(editionId: String): com.theblankstate.libri.datamodel.EditionModel? {
        return try {
            api.getEditionDetails(editionId)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getBooksBySubject(subject: String, limit: Int = 10): com.theblankstate.libri.datamodel.SubjectResponse? {
        return try {
            api.getSubject(subject, limit = limit)
        } catch (e: Exception) {
            null
        }
    }
}
