package com.theblankstate.libri.datamodel

fun bookModel.coverImageUrl(size: String = "L"): String? =
    cover_i?.let { "https://covers.openlibrary.org/b/id/${it}-${size}.jpg" }

fun bookModel.primaryAuthor(): String? = author_name?.firstOrNull()

fun bookModel.primaryGenre(): String? = subject?.firstOrNull()
