package com.theblankstate.libri.util

import com.theblankstate.libri.datamodel.ReadingStatus

data class ReadingProgressUpdate(
    val currentPage: Int,
    val totalPages: Int,
    val status: ReadingStatus? = null
)

object ReadingProgressUtils {
    fun normalize(
        currentPage: Int,
        totalPages: Int,
        currentStatus: ReadingStatus?
    ): ReadingProgressUpdate {
        val safeTotal = totalPages.coerceAtLeast(0)
        val safeCurrent = if (safeTotal > 0) {
            currentPage.coerceIn(0, safeTotal)
        } else {
            currentPage.coerceAtLeast(0)
        }
        val nextStatus = when {
            safeTotal > 0 && safeCurrent >= safeTotal -> ReadingStatus.FINISHED
            safeCurrent > 0 && currentStatus == ReadingStatus.WANT_TO_READ -> ReadingStatus.IN_PROGRESS
            safeCurrent > 0 && currentStatus == null -> ReadingStatus.IN_PROGRESS
            else -> null
        }
        return ReadingProgressUpdate(
            currentPage = safeCurrent,
            totalPages = safeTotal,
            status = nextStatus
        )
    }
}
