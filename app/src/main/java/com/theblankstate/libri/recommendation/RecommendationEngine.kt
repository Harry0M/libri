package com.theblankstate.libri.recommendation

import com.theblankstate.libri.data.RecommendationSeeds
import com.theblankstate.libri.datamodel.bookModel
import kotlin.math.ln

class RecommendationEngine {
    fun rankHomeSections(
        sourceSections: List<Pair<String, List<bookModel>>>,
        signals: RecommendationSignals
    ): List<Pair<String, List<bookModel>>> {
        val cleanedSections = sourceSections
            .map { (title, books) -> title to books.filterVisible(signals).distinctBy(::canonicalId) }
            .filter { it.second.isNotEmpty() }

        val continueReading = cleanedSections.firstOrNull {
            it.first.equals("Continue Reading", ignoreCase = true)
        }?.let { (title, books) ->
            title to books.sortedByRecent(signals).take(10)
        }

        val candidates = cleanedSections
            .filterNot { it.first.equals("Continue Reading", ignoreCase = true) }
            .flatMap { (title, books) ->
                books.map { book ->
                    RecommendationCandidate(
                        book = book,
                        generator = title,
                        score = score(book, title, signals),
                        reason = reasonFor(book, title)
                    )
                }
            }
            .filter { it.score > -20.0 }

        val forYou = diversify(
            candidates
                .dedupeByBook()
                .sortedByDescending { it.score },
            maxItems = 14
        ).map { it.book }

        val rankedOriginalSections = cleanedSections
            .filterNot { it.first.equals("Continue Reading", ignoreCase = true) }
            .mapNotNull { (title, books) ->
                val ranked = books
                    .map { book ->
                        RecommendationCandidate(
                            book = book,
                            generator = title,
                            score = score(book, title, signals),
                            reason = reasonFor(book, title)
                        )
                    }
                    .filter { it.score > -20.0 }
                    .dedupeByBook()
                    .sortedByDescending { it.score }
                    .let { diversify(it, maxItems = 14) }
                    .map { it.book }
                if (ranked.size >= 3) title to ranked else null
            }

        val finalSections = mutableListOf<Pair<String, List<bookModel>>>()
        continueReading?.takeIf { it.second.isNotEmpty() }?.let(finalSections::add)
        if (forYou.size >= 4) {
            finalSections.add("For You" to forYou)
        }
        rankedOriginalSections
            .filterNot { section ->
                section.first.equals("For You", ignoreCase = true) ||
                    finalSections.any { existing -> sameSection(existing.first, section.first) }
            }
            .take(8)
            .forEach(finalSections::add)

        return finalSections.ifEmpty {
            cleanedSections.take(4)
        }
    }

    fun score(
        book: bookModel,
        generator: String,
        signals: RecommendationSignals
    ): Double {
        val itemId = canonicalId(book)
        if (signals.hiddenItemIds.contains(itemId)) return -100.0
        if (signals.finishedItemIds.contains(itemId)) return -25.0

        var score = generatorBoost(generator)

        val authors = book.author_name.orEmpty()
        authors.forEach { author ->
            val normalized = author.normalizedToken()
            score += signals.authorWeights[normalized] ?: 0.0
            if (signals.selectedAuthors.any { selected -> author.contains(selected, ignoreCase = true) }) {
                score += 4.0
            }
        }

        val subjects = book.subject.orEmpty()
        val normalizedGenres = signals.selectedGenres.map { RecommendationSeeds.normalizeTopic(it).normalizedToken() }
        subjects.take(20).forEach { subject ->
            val normalized = subject.normalizedToken()
            score += (signals.subjectWeights[normalized] ?: 0.0) * 0.9
            if (normalizedGenres.any { genre -> normalized.contains(genre) || genre.contains(normalized) }) {
                score += 2.5
            }
        }

        val languages = book.language.orEmpty()
        languages.forEach { language ->
            val normalized = language.normalizedToken()
            score += signals.languageWeights[normalized] ?: 0.0
            if (signals.selectedLanguages.any { selected -> selected.contains(language, ignoreCase = true) }) {
                score += 1.5
            }
        }

        if (book.has_fulltext == true && !book.ia.isNullOrEmpty()) score += 2.0
        if (!book.id_project_gutenberg.isNullOrEmpty()) score += 2.0
        if (book.cover_i != null || book.coverUrl != null) score += 0.6
        book.ratings_average?.let { score += it.coerceIn(0.0, 5.0) * 0.35 }
        book.edition_count?.let { score += ln(it.coerceAtLeast(1).toDouble()) * 0.2 }

        if (signals.recentBookIds.any { normalizeItemId(it) == itemId }) {
            score += 8.0
        }

        return score
    }

    private fun List<bookModel>.filterVisible(signals: RecommendationSignals): List<bookModel> {
        return filter { book ->
            val id = canonicalId(book)
            id !in signals.hiddenItemIds && id !in signals.finishedItemIds
        }
    }

    private fun List<bookModel>.sortedByRecent(signals: RecommendationSignals): List<bookModel> {
        val recentIndex = signals.recentBookIds
            .mapIndexed { index, id -> normalizeItemId(id) to index }
            .toMap()
        return sortedWith(compareBy<bookModel> { recentIndex[canonicalId(it)] ?: Int.MAX_VALUE }
            .thenBy { it.title })
    }

    private fun List<RecommendationCandidate>.dedupeByBook(): List<RecommendationCandidate> {
        return groupBy { canonicalId(it.book) }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.score } }
    }

    private fun diversify(
        candidates: List<RecommendationCandidate>,
        maxItems: Int
    ): List<RecommendationCandidate> {
        val selected = mutableListOf<RecommendationCandidate>()
        val authorCounts = mutableMapOf<String, Int>()
        val subjectCounts = mutableMapOf<String, Int>()

        candidates.forEach { candidate ->
            if (selected.size >= maxItems) return@forEach
            val primaryAuthor = candidate.book.author_name.orEmpty().firstOrNull()?.normalizedToken()
            val primarySubject = candidate.book.subject.orEmpty().firstOrNull()?.normalizedToken()
            val authorCount = primaryAuthor?.let { authorCounts[it] ?: 0 } ?: 0
            val subjectCount = primarySubject?.let { subjectCounts[it] ?: 0 } ?: 0

            if (authorCount >= 3 || subjectCount >= 4) return@forEach

            selected.add(candidate)
            primaryAuthor?.let { authorCounts[it] = authorCount + 1 }
            primarySubject?.let { subjectCounts[it] = subjectCount + 1 }
        }

        if (selected.size < maxItems) {
            val existing = selected.map { canonicalId(it.book) }.toSet()
            candidates
                .filterNot { canonicalId(it.book) in existing }
                .take(maxItems - selected.size)
                .forEach(selected::add)
        }

        return selected
    }

    private fun generatorBoost(generator: String): Double {
        return when {
            generator.equals("Continue Reading", ignoreCase = true) -> 50.0
            generator.startsWith("Because", ignoreCase = true) -> 5.0
            generator.startsWith("More from", ignoreCase = true) -> 4.5
            generator.contains("classic", ignoreCase = true) -> 2.5
            generator.contains("gutenberg", ignoreCase = true) -> 2.2
            generator.contains("free", ignoreCase = true) -> 2.0
            generator.contains("trending", ignoreCase = true) -> 1.5
            else -> 1.0
        }
    }

    private fun sameSection(left: String, right: String): Boolean {
        return left.equals(right, ignoreCase = true)
    }

    private fun String.normalizedToken(): String {
        return trim().lowercase()
    }

    companion object {
        fun canonicalId(book: bookModel): String {
            val key = book.key?.takeIf { it.isNotBlank() }
            val gutenberg = book.id_project_gutenberg?.firstOrNull()?.takeIf { it.isNotBlank() }
            val titleAuthor = listOfNotNull(
                book.title.takeIf { it.isNotBlank() },
                book.author_name?.firstOrNull()
            ).joinToString("|").lowercase()
            return when {
                key != null -> normalizeItemId(key)
                gutenberg != null -> "gutenberg:$gutenberg"
                titleAuthor.isNotBlank() -> "title:$titleAuthor"
                else -> "unknown:${book.hashCode()}"
            }
        }

        fun normalizeItemId(raw: String): String {
            val trimmed = raw.trim()
            return when {
                trimmed.startsWith("/works/") -> trimmed.removePrefix("/").lowercase()
                trimmed.startsWith("/books/") -> trimmed.removePrefix("/").lowercase()
                trimmed.startsWith("works/") || trimmed.startsWith("books/") -> trimmed.lowercase()
                trimmed.startsWith("gutenberg:", ignoreCase = true) -> trimmed.lowercase()
                trimmed.startsWith("OL", ignoreCase = true) && trimmed.endsWith("M", ignoreCase = true) ->
                    "books/${trimmed.lowercase()}"
                trimmed.startsWith("OL", ignoreCase = true) && trimmed.endsWith("W", ignoreCase = true) ->
                    "works/${trimmed.lowercase()}"
                else -> trimmed.lowercase()
            }
        }

        fun sourceFor(book: bookModel): RecommendationItemSource {
            return when {
                !book.id_project_gutenberg.isNullOrEmpty() -> RecommendationItemSource.GUTENBERG
                !book.key.isNullOrBlank() -> RecommendationItemSource.OPEN_LIBRARY
                else -> RecommendationItemSource.UNKNOWN
            }
        }

        fun reasonFor(book: bookModel, generator: String): String {
            return when {
                generator.equals("Continue Reading", ignoreCase = true) -> "Continue reading"
                generator.startsWith("Because", ignoreCase = true) -> generator
                generator.startsWith("More from", ignoreCase = true) -> generator
                !book.id_project_gutenberg.isNullOrEmpty() -> "Free Project Gutenberg book"
                book.has_fulltext == true -> "Readable on Open Library"
                generator.contains("trending", ignoreCase = true) -> "Trending on Open Library"
                else -> "Personalized pick"
            }
        }
    }
}
