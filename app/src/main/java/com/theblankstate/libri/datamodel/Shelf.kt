package com.theblankstate.libri.datamodel

import com.google.firebase.database.PropertyName

data class Shelf(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    @PropertyName("dateCreated")
    val dateCreated: Long = System.currentTimeMillis(),
    @PropertyName("bookCount")
    val bookCount: Int = 0
)
