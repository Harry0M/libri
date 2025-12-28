package com.theblankstate.libri.datamodel

import com.google.gson.annotations.SerializedName

data class BookshelfModel(
    val counts: BookshelfCounts? = null
)

data class BookshelfCounts(
    @SerializedName("want_to_read")
    val wantToRead: Int = 0,
    @SerializedName("currently_reading")
    val currentlyReading: Int = 0,
    @SerializedName("already_read")
    val alreadyRead: Int = 0
)
