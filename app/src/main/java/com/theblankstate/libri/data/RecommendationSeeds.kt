package com.theblankstate.libri.data

object RecommendationSeeds {
    private val openLibraryLanguages = mapOf(
        "English" to LibraryLanguage(twoLetter = "en", searchCode = "eng"),
        "Hindi" to LibraryLanguage(twoLetter = "hi", searchCode = "hin"),
        "Urdu" to LibraryLanguage(twoLetter = "ur", searchCode = "urd"),
        "Spanish" to LibraryLanguage(twoLetter = "es", searchCode = "spa"),
        "Chinese" to LibraryLanguage(twoLetter = "zh", searchCode = "chi"),
        "French" to LibraryLanguage(twoLetter = "fr", searchCode = "fre"),
        "Arabic" to LibraryLanguage(twoLetter = "ar", searchCode = "ara"),
        "Portuguese" to LibraryLanguage(twoLetter = "pt", searchCode = "por")
    )

    private val gutendexLanguages = mapOf(
        "English" to "en",
        "Hindi" to "hi",
        "Urdu" to "ur",
        "Spanish" to "es",
        "Chinese" to "zh",
        "French" to "fr",
        "Arabic" to "ar",
        "Portuguese" to "pt"
    )

    private val topicAliases = mapOf(
        "Historical Fiction" to "history",
        "Science Fiction" to "science fiction",
        "Children's" to "children",
        "Autobiography" to "biography",
        "Memoir" to "biography",
        "Self-Help" to "self help",
        "Business" to "business",
        "Comics" to "comics",
        "Health" to "health",
        "Cooking" to "cooking"
    )

    val fallbackTopics = listOf(
        "Classic Literature",
        "Mystery",
        "Science Fiction",
        "Romance",
        "History",
        "Philosophy"
    )

    fun preferredOpenLibraryLanguage(languages: Collection<String>): LibraryLanguage? {
        return languages.firstNotNullOfOrNull { openLibraryLanguages[it] }
    }

    fun preferredGutendexLanguage(languages: Collection<String>): String? {
        return languages.firstNotNullOfOrNull { gutendexLanguages[it] }
    }

    fun normalizeTopic(topic: String): String {
        return topicAliases[topic] ?: topic.lowercase().trim()
    }

    fun topicsFromGenres(genres: Collection<String>, limit: Int = 8): List<String> {
        return (genres.map(::normalizeTopic) + fallbackTopics.map(::normalizeTopic))
            .filter { it.isNotBlank() }
            .distinct()
            .take(limit)
    }

    fun displayTopicsFromGenres(genres: Collection<String>, limit: Int = 10): List<String> {
        return (genres + fallbackTopics)
            .filter { it.isNotBlank() }
            .distinct()
            .take(limit)
    }

    fun gutenbergHeadline(genres: Collection<String>, languages: Collection<String>): String {
        val genre = genres.firstOrNull()
        val language = languages.firstOrNull()
        return when {
            genre != null -> "Free $genre classics"
            language != null -> "Popular free reads in $language"
            else -> "Popular free reads"
        }
    }

    fun gutenbergSubtitle(genres: Collection<String>, languages: Collection<String>): String {
        val genre = genres.firstOrNull()
        val language = languages.firstOrNull()
        return when {
            genre != null && language != null -> "Gutenberg picks tuned to $genre and $language"
            genre != null -> "Project Gutenberg books for your $genre mood"
            language != null -> "Project Gutenberg catalog filtered for $language"
            else -> "Project Gutenberg popular public-domain books"
        }
    }
}

data class LibraryLanguage(
    val twoLetter: String,
    val searchCode: String
)
