package com.theblankstate.libri.recommendation

import com.theblankstate.libri.datamodel.bookModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {
    private val engine = RecommendationEngine()

    @Test
    fun rankHomeSections_boostsExplicitAuthorAndGenreMatches() {
        val matching = book(
            key = "/works/OL1W",
            title = "Pride and Prejudice",
            authors = listOf("Jane Austen"),
            subjects = listOf("Romance", "Classic fiction"),
            languages = listOf("eng")
        )
        val generic = book(
            key = "/works/OL2W",
            title = "General Essays",
            authors = listOf("Unknown Author"),
            subjects = listOf("Essays"),
            languages = listOf("eng")
        )
        val fillerOne = book(key = "/works/OL3W", title = "Another Classic")
        val fillerTwo = book(key = "/works/OL4W", title = "Collected Tales")

        val ranked = engine.rankHomeSections(
            sourceSections = listOf("Because you like Romance" to listOf(generic, matching, fillerOne, fillerTwo)),
            signals = signals(
                authorWeights = mapOf("jane austen" to 4.0),
                subjectWeights = mapOf("romance" to 3.5),
                languageWeights = mapOf("eng" to 2.0)
            )
        )

        val forYou = ranked.first { it.first == "For You" }.second
        assertEquals("Pride and Prejudice", forYou.first().title)
    }

    @Test
    fun rankHomeSections_filtersHiddenAndFinishedItems() {
        val hidden = book(key = "/works/OL10W", title = "Hidden Book")
        val finished = book(key = "/works/OL11W", title = "Finished Book")
        val visible = book(key = "/works/OL12W", title = "Visible Book")

        val ranked = engine.rankHomeSections(
            sourceSections = listOf("Trending on Open Library" to listOf(hidden, finished, visible)),
            signals = signals(
                hiddenItemIds = setOf(RecommendationEngine.canonicalId(hidden)),
                finishedItemIds = setOf(RecommendationEngine.canonicalId(finished))
            )
        )

        val titles = ranked.flatMap { it.second }.map { it.title }
        assertFalse(titles.contains("Hidden Book"))
        assertFalse(titles.contains("Finished Book"))
        assertTrue(titles.contains("Visible Book"))
    }

    @Test
    fun rankHomeSections_dedupesSameOpenLibraryWorkAcrossSections() {
        val first = book(key = "/works/OL50W", title = "Same Work", subjects = listOf("Adventure"))
        val duplicate = book(key = "/works/OL50W", title = "Same Work", subjects = listOf("Adventure"))
        val other = book(key = "/works/OL51W", title = "Other Work", subjects = listOf("Adventure"))
        val third = book(key = "/works/OL52W", title = "Third Work", subjects = listOf("Adventure"))
        val fourth = book(key = "/works/OL53W", title = "Fourth Work", subjects = listOf("Adventure"))

        val ranked = engine.rankHomeSections(
            sourceSections = listOf(
                "Because you like Adventure" to listOf(first, other, third, fourth),
                "Trending on Open Library" to listOf(duplicate)
            ),
            signals = signals()
        )

        val forYouIds = ranked.first { it.first == "For You" }.second.map(RecommendationEngine::canonicalId)
        assertEquals(forYouIds.distinct().size, forYouIds.size)
    }

    private fun signals(
        authorWeights: Map<String, Double> = emptyMap(),
        subjectWeights: Map<String, Double> = emptyMap(),
        languageWeights: Map<String, Double> = emptyMap(),
        hiddenItemIds: Set<String> = emptySet(),
        finishedItemIds: Set<String> = emptySet()
    ) = RecommendationSignals(
        selectedLanguages = setOf("English"),
        selectedAuthors = setOf("Jane Austen"),
        selectedGenres = setOf("Romance"),
        recentBookIds = emptyList(),
        recentEvents = emptyList(),
        hiddenItemIds = hiddenItemIds,
        finishedItemIds = finishedItemIds,
        libraryBooks = emptyList(),
        authorWeights = authorWeights,
        subjectWeights = subjectWeights,
        languageWeights = languageWeights
    )

    private fun book(
        key: String,
        title: String,
        authors: List<String> = listOf("Author"),
        subjects: List<String> = listOf("Fiction"),
        languages: List<String> = listOf("eng")
    ) = bookModel(
        key = key,
        title = title,
        author_name = authors,
        subject = subjects,
        language = languages,
        has_fulltext = true,
        ia = listOf("archive_id"),
        cover_i = 123
    )
}
