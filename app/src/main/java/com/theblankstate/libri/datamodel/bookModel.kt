package com.theblankstate.libri.datamodel

import com.google.gson.annotations.SerializedName

data class bookModel(
    val author_key: List<String>? = null,
    val author_name: List<String>? = null,
    val cover_edition_key: String? = null,
    val cover_i: Int? = null,
    val ebook_access: String? = null,
    val edition_count: Int? = null,
    val first_publish_year: Int? = null,
    val has_fulltext: Boolean? = null,
    val key: String? = null,
    val public_scan_b: Boolean? = null,
    val title: String = "",
    val subject: List<String>? = null,
    val language: List<String>? = null,
    val ia: List<String>? = null,
    val ratings_average: Double? = null,
    @SerializedName("first_sentence")
    val firstSentence: List<String>? = null,
    @SerializedName("id_standard_ebooks")
    val id_standard_ebooks: List<String>? = null,
    @SerializedName("id_project_gutenberg")
    val id_project_gutenberg: List<String>? = null,
    @SerializedName("id_librivox")
    val id_librivox: List<String>? = null,
    val isbn: List<String>? = null,
    val publisher: List<String>? = null,
    val publish_date: String? = null,
    val number_of_pages: Int? = null,
    val dewey_decimal_class: List<String>? = null,
    val lcc_number: List<String>? = null
) {
    val coverUrl: String
        get() = "https://covers.openlibrary.org/b/id/${cover_i}-L.jpg"
}

data class SearchResponse(
    @SerializedName(value = "numFound", alternate = ["num_found"])
    val numFound: Int = 0,
    val docs: List<bookModel> = emptyList()
)