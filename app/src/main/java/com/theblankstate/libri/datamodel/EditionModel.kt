package com.theblankstate.libri.datamodel

import com.google.gson.annotations.SerializedName

data class EditionResponse(
    val entries: List<EditionModel> = emptyList()
)

data class EditionModel(
    val title: String? = null,
    val publishers: List<String>? = null,
    @SerializedName("publish_date")
    val publishDate: String? = null,
    val languages: List<LanguageKey>? = null,
    @SerializedName("isbn_13")
    val isbn13: List<String>? = null,
    @SerializedName("isbn_10")
    val isbn10: List<String>? = null,
    val key: String? = null,
    val covers: List<Int>? = null,
    val authors: List<AuthorKey>? = null,
    val works: List<WorkKey>? = null,
    val identifiers: Map<String, List<String>>? = null,
    val ocaid: String? = null,
    @SerializedName("number_of_pages")
    val numberOfPages: Int? = null,
    val pagination: String? = null,
    @SerializedName("dewey_decimal_class")
    val deweyDecimalClass: List<String>? = null,
    @SerializedName("lcc_number")
    val lccNumber: List<String>? = null
) {
    fun toBookModel(): bookModel {
        return bookModel(
            title = title ?: "Unknown Title",
            key = key,
            cover_i = covers?.firstOrNull(),
            first_publish_year = publishDate?.let { 
                // Try to extract year from date string
                it.filter { char -> char.isDigit() }.take(4).toIntOrNull()
            },
            id_standard_ebooks = identifiers?.get("standard_ebooks"),
            id_project_gutenberg = identifiers?.get("project_gutenberg") ?: identifiers?.get("gutenberg"),
            id_librivox = identifiers?.get("librivox"),
            ia = ocaid?.let { listOf(it) },
            isbn = (isbn13.orEmpty() + isbn10.orEmpty()).distinct(),
            publisher = publishers,
            publish_date = publishDate,
            number_of_pages = numberOfPages,
            dewey_decimal_class = deweyDecimalClass,
            lcc_number = lccNumber
        )
    }
}

data class LanguageKey(
    val key: String
)

data class AuthorKey(
    val key: String
)

data class WorkKey(
    val key: String
)
