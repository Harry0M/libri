package com.theblankstate.libri.datamodel

data class RatingsModel(
    val summary: RatingsSummary? = null,
    val counts: Map<String, Int>? = null
)

data class RatingsSummary(
    val average: Double? = null,
    val count: Int? = null
)
