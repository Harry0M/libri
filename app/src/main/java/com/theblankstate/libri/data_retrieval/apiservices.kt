package com.theblankstate.libri.data_retrieval

import com.theblankstate.libri.datamodel.BookshelfModel
import com.theblankstate.libri.datamodel.EditionModel
import com.theblankstate.libri.datamodel.EditionResponse
import com.theblankstate.libri.datamodel.RatingsModel
import com.theblankstate.libri.datamodel.SearchResponse
import com.theblankstate.libri.datamodel.WorkDetailModel
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface apiservices {

    @GET("search.json")
    suspend fun getbooks(
        @Query("q") query: String? = null,
        @Query("title") title: String? = null,
        @Query("author") author: String? = null,
        @Query("subject") subject: String? = null,
        @Query("isbn") isbn: String? = null,
        @Query("publisher") publisher: String? = null,
        @Query("language") language: String? = null,
        @Query("sort") sort: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): SearchResponse

    @GET("works/{workId}.json")
    suspend fun getWorkDetails(
        @Path("workId") workId: String
    ): WorkDetailModel

    @GET("works/{workId}/editions.json")
    suspend fun getEditions(
        @Path("workId") workId: String,
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0
    ): EditionResponse

    @GET("works/{workId}/ratings.json")
    suspend fun getRatings(
        @Path("workId") workId: String
    ): RatingsModel

    @GET("works/{workId}/bookshelves.json")
    suspend fun getBookshelves(
        @Path("workId") workId: String
    ): BookshelfModel

    @GET("books/{editionId}.json")
    suspend fun getEditionDetails(
        @Path("editionId") editionId: String
    ): EditionModel

    @GET("subjects/{subject}.json")
    suspend fun getSubject(
        @Path("subject") subject: String,
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0
    ): com.theblankstate.libri.datamodel.SubjectResponse
}