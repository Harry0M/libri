package com.theblankstate.libri.datamodel

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class WorkDetailModel(
    val title: String? = null,
    val description: JsonElement? = null,
    val subjects: List<String>? = null,
    @SerializedName("subject_places")
    val subjectPlaces: List<String>? = null,
    @SerializedName("subject_people")
    val subjectPeople: List<String>? = null,
    @SerializedName("subject_times")
    val subjectTimes: List<String>? = null,
    val excerpts: List<Excerpt>? = null,
    val covers: List<Int>? = null
) {
    fun getDescriptionText(): String? {
        return if (description != null) {
            if (description.isJsonPrimitive) {
                description.asString
            } else if (description.isJsonObject) {
                description.asJsonObject.get("value")?.asString
            } else {
                null
            }
        } else {
            null
        }
    }
}

data class Excerpt(
    val text: String? = null,
    val comment: String? = null,
    val author: JsonElement? = null
)
