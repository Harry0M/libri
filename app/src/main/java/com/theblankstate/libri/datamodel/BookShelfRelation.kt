package com.theblankstate.libri.datamodel

import com.google.firebase.database.PropertyName

data class BookShelfRelation(
    @PropertyName("bookId")
    val bookId: String = "",
    @PropertyName("shelfId")
    val shelfId: String = "",
    @PropertyName("dateAdded")
    val dateAdded: Long = System.currentTimeMillis()
)
