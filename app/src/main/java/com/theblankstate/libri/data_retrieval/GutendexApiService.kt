package com.theblankstate.libri.data_retrieval

import com.theblankstate.libri.datamodel.GutendexBook
import com.theblankstate.libri.datamodel.GutendexResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Gutendex API Service
 * API Documentation: https://gutendex.com/
 * 
 * Gutendex is a simple, self-hosted web API for Project Gutenberg ebook metadata.
 * All books in the catalog are public domain in the United States.
 */
interface GutendexApiService {
    
    /**
     * Get a list of books with optional filters
     * 
     * @param search Search query for title/author
     * @param languages Comma-separated list of language codes (e.g., "en,fr")
     * @param topic Search in subjects and bookshelves
     * @param ids Comma-separated list of Gutenberg IDs
     * @param authorYearStart Filter by author birth year (start)
     * @param authorYearEnd Filter by author death year (end)
     * @param mimeType Filter by available format (e.g., "application/epub+zip")
     * @param sort Sort order: "popular" (default), "ascending", "descending"
     * @param page Page number for pagination (default: 1)
     */
    @GET("books")
    suspend fun searchBooks(
        @Query("search") search: String? = null,
        @Query("languages") languages: String? = null,
        @Query("topic") topic: String? = null,
        @Query("ids") ids: String? = null,
        @Query("author_year_start") authorYearStart: Int? = null,
        @Query("author_year_end") authorYearEnd: Int? = null,
        @Query("mime_type") mimeType: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int = 1
    ): GutendexResponse
    
    /**
     * Get a specific book by its Gutenberg ID
     * 
     * @param id The Gutenberg book ID
     */
    @GET("books/{id}")
    suspend fun getBook(
        @Path("id") id: Int
    ): GutendexBook
    
    /**
     * Get popular books (sorted by download count)
     */
    @GET("books")
    suspend fun getPopularBooks(
        @Query("sort") sort: String = "popular",
        @Query("languages") languages: String? = "en",
        @Query("page") page: Int = 1
    ): GutendexResponse
    
    /**
     * Get books by topic/genre
     */
    @GET("books")
    suspend fun getBooksByTopic(
        @Query("topic") topic: String,
        @Query("languages") languages: String? = null,
        @Query("sort") sort: String = "popular",
        @Query("page") page: Int = 1
    ): GutendexResponse
    
    /**
     * Fetch books from a full pagination URL
     * Used for "next" and "previous" links in GutendexResponse
     */
    @GET
    suspend fun getBooksFromUrl(
        @retrofit2.http.Url url: String
    ): GutendexResponse
}
