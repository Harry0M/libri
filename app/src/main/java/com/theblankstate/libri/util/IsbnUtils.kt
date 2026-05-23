package com.theblankstate.libri.util

object IsbnUtils {
    fun normalize(raw: String?): String? {
        val input = raw.orEmpty().uppercase()
        val isbn13Candidate = input.filter { it.isDigit() }
            .windowed(13, 1)
            .firstOrNull { isValidIsbn13(it) }
        if (isbn13Candidate != null) return isbn13Candidate

        val cleaned = input.filter { it.isDigit() || it == 'X' }
        val isbn10Candidate = cleaned.windowed(10, 1)
            .firstOrNull { isValidIsbn10(it) }
        return isbn10Candidate
    }

    fun isValid(raw: String?): Boolean = normalize(raw) != null

    fun isValidIsbn13(value: String): Boolean {
        if (value.length != 13 || !value.all { it.isDigit() }) return false
        if (!value.startsWith("978") && !value.startsWith("979")) return false
        val sum = value.take(12).mapIndexed { index, char ->
            val digit = char.digitToInt()
            if (index % 2 == 0) digit else digit * 3
        }.sum()
        val check = (10 - (sum % 10)) % 10
        return check == value.last().digitToInt()
    }

    fun isValidIsbn10(value: String): Boolean {
        if (value.length != 10) return false
        if (!value.take(9).all { it.isDigit() }) return false
        if (!value.last().isDigit() && value.last() != 'X') return false
        val sum = value.mapIndexed { index, char ->
            val digit = if (char == 'X') 10 else char.digitToInt()
            digit * (10 - index)
        }.sum()
        return sum % 11 == 0
    }
}
