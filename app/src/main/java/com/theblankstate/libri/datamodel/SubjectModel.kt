package com.theblankstate.libri.datamodel

import com.google.gson.annotations.SerializedName

data class SubjectResponse(
    val key: String? = null,
    val name: String? = null,
    @SerializedName("subject_type")
    val subjectType: String? = null,
    @SerializedName("work_count")
    val workCount: Int? = null,
    val works: List<SubjectWork>? = null
)

data class SubjectWork(
    val key: String? = null,
    val title: String? = null,
    @SerializedName("cover_id")
    val coverId: Int? = null,
    @SerializedName("cover_edition_key")
    val coverEditionKey: String? = null,
    val subject: List<String>? = null,
    val authors: List<SubjectAuthor>? = null,
    @SerializedName("first_publish_year")
    val firstPublishYear: Int? = null
) {
    val coverUrl: String
        get() = "https://covers.openlibrary.org/b/id/${coverId}-L.jpg"
}

data class SubjectAuthor(
    val key: String? = null,
    val name: String? = null
)
