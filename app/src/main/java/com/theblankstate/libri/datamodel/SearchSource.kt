package com.theblankstate.libri.datamodel

/**
 * Controls which source the search should target.
 */
enum class SearchSource {
    OPEN_LIBRARY,
    GUTENBERG,
    // BOTH removed; searches will either target Open Library or Gutenberg only
}
