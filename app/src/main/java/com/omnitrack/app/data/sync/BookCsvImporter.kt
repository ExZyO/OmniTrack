package com.omnitrack.app.data.sync

import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BookImportSummary(
    val imported: Int,
    val updated: Int,
    val error: String? = null
)

object BookCsvImporter {

    suspend fun importCsv(
        content: String,
        mediaRepository: MediaRepository
    ): BookImportSummary = withContext(Dispatchers.IO) {
        val cleaned = content.removePrefix("\uFEFF")
        val rows = parseRfc4180Csv(cleaned)
        if (rows.size < 2) {
            return@withContext BookImportSummary(0, 0, "CSV file is empty or missing header row")
        }

        val headerIndex = LinkedHashMap<String, Int>()
        rows.first().forEachIndexed { idx, col ->
            headerIndex[col.trim().lowercase()] = idx
        }

        val titleIdx = headerIndex["title"]
            ?: return@withContext BookImportSummary(0, 0, "CSV missing required 'Title' column")

        val isStoryGraph = "authors" in headerIndex || "read status" in headerIndex || "star rating" in headerIndex
        val authorIdx = if (isStoryGraph) headerIndex["authors"] ?: headerIndex["author"] else headerIndex["author"] ?: headerIndex["authors"]
        val isbnIdx = headerIndex["isbn13"] ?: headerIndex["isbn/uid"] ?: headerIndex["isbn"]
        val ratingIdx = headerIndex["my rating"] ?: headerIndex["star rating"] ?: headerIndex["rating"]
        val pagesIdx = headerIndex["number of pages"] ?: headerIndex["pages"] ?: headerIndex["page count"]
        val yearIdx = headerIndex["year published"] ?: headerIndex["original publication year"] ?: headerIndex["publication year"]
        val shelfIdx = headerIndex["exclusive shelf"] ?: headerIndex["read status"] ?: headerIndex["status"]
        val shelvesExtraIdx = headerIndex["bookshelves"]
        val formatIdx = headerIndex["format"] ?: headerIndex["binding"]
        val reviewIdx = headerIndex["my review"] ?: headerIndex["review"]
        val moodsIdx = headerIndex["moods"]

        var imported = 0
        var updated = 0

        for (rowIdx in 1 until rows.size) {
            val row = rows[rowIdx]
            val title = row.getOrNull(titleIdx)?.trim()?.takeIf { it.isNotBlank() } ?: continue
            val author = authorIdx?.let { row.getOrNull(it)?.trim()?.takeIf { s -> s.isNotBlank() } }
            val rawIsbn = isbnIdx?.let { row.getOrNull(it) }
            val isbn = cleanIsbn(rawIsbn)
            val rating5 = ratingIdx?.let { row.getOrNull(it)?.trim()?.toFloatOrNull() }?.takeIf { it > 0f }
            val rating10 = rating5?.let { (it * 2f).coerceIn(1f, 10f) }
            val pages = pagesIdx?.let { row.getOrNull(it)?.trim()?.toIntOrNull() }?.takeIf { it > 0 }
            val year = yearIdx?.let { row.getOrNull(it)?.trim()?.take(4)?.takeIf { y -> y.length == 4 && y.all { c -> c.isDigit() } } }
            val rawStatus = shelfIdx?.let { row.getOrNull(it)?.trim()?.lowercase() }.orEmpty()
            val status = mapCsvStatus(rawStatus)
            val rawFormat = formatIdx?.let { row.getOrNull(it)?.trim() }
            val bookFormat = mapCsvFormat(rawFormat)
            val review = reviewIdx?.let { row.getOrNull(it)?.trim()?.takeIf { r -> r.isNotBlank() } }
            val extraTags = listOfNotNull(
                shelvesExtraIdx?.let { row.getOrNull(it)?.trim()?.takeIf { s -> s.isNotBlank() } },
                moodsIdx?.let { row.getOrNull(it)?.trim()?.takeIf { s -> s.isNotBlank() } }
            ).joinToString(", ").takeIf { it.isNotBlank() }

            val existing = mediaRepository.findExistingMedia(
                isbn = isbn,
                title = title,
                mediaType = MediaType.BOOK
            )

            if (existing != null) {
                val mergedNotes = when {
                    !existing.notes.isNullOrBlank() || review.isNullOrBlank() -> existing.notes
                    else -> review
                }
                val mergedStatus = if (existing.status == TrackingStatus.PLAN_TO_TRACK && status != TrackingStatus.PLAN_TO_TRACK) {
                    status
                } else {
                    existing.status
                }
                val nextProgress = if (mergedStatus == TrackingStatus.COMPLETED && existing.currentProgress <= 0.0 && pages != null) {
                    pages.toDouble()
                } else {
                    existing.currentProgress
                }
                val next = existing.copy(
                    isbn = existing.isbn ?: isbn,
                    bookAuthor = existing.bookAuthor ?: author,
                    bookFormat = existing.bookFormat ?: bookFormat,
                    releaseYear = existing.releaseYear ?: year,
                    totalProgress = existing.totalProgress ?: pages?.toDouble(),
                    currentProgress = nextProgress,
                    status = mergedStatus,
                    userRating = existing.userRating ?: rating10,
                    notes = mergedNotes,
                    categoryNames = existing.categoryNames ?: extraTags
                )
                if (next != existing) {
                    mediaRepository.updateMedia(next)
                    updated++
                }
            } else {
                val initProgress = if (status == TrackingStatus.COMPLETED && pages != null) pages.toDouble() else 0.0
                mediaRepository.insertMedia(
                    MediaItemEntity(
                        title = title,
                        mediaType = MediaType.BOOK,
                        status = status,
                        currentProgress = initProgress,
                        totalProgress = pages?.toDouble(),
                        userRating = rating10,
                        releaseYear = year,
                        notes = review,
                        categoryNames = extraTags,
                        isbn = isbn,
                        bookAuthor = author,
                        bookFormat = bookFormat
                    )
                )
                imported++
            }
        }

        BookImportSummary(imported = imported, updated = updated)
    }

    private fun cleanIsbn(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .removePrefix("=\"")
            .removeSuffix("\"")
            .replace("-", "")
            .replace(" ", "")
            .trim()
        return cleaned.takeIf { it.length in 9..14 && it.any { c -> c.isDigit() } }
    }

    private fun mapCsvStatus(raw: String): TrackingStatus = when {
        raw == "read" || raw == "finished" || raw == "completed" -> TrackingStatus.COMPLETED
        "currently" in raw || raw == "reading" || raw == "in_progress" -> TrackingStatus.IN_PROGRESS
        "did-not-finish" in raw || raw == "dnf" || raw == "dropped" -> TrackingStatus.DROPPED
        "paused" in raw || "hold" in raw -> TrackingStatus.ON_HOLD
        else -> TrackingStatus.PLAN_TO_TRACK
    }

    private fun mapCsvFormat(raw: String?): String {
        val lower = raw?.lowercase().orEmpty()
        return when {
            "audio" in lower -> "AUDIOBOOK"
            "kindle" in lower || "ebook" in lower || "e-book" in lower || "digital" in lower -> "EBOOK"
            "paperback" in lower -> "PAPERBACK"
            else -> "HARDCOVER"
        }
    }

    private fun parseRfc4180Csv(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = input.length
        while (i < n) {
            val ch = input[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < n && input[i + 1] == '"') {
                        field.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                    }
                    '\r' -> {
                        if (i + 1 < n && input[i + 1] == '\n') i++
                        currentRow.add(field.toString())
                        field.setLength(0)
                        if (currentRow.any { it.isNotEmpty() }) rows.add(currentRow.toList())
                        currentRow.clear()
                    }
                    '\n' -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                        if (currentRow.any { it.isNotEmpty() }) rows.add(currentRow.toList())
                        currentRow.clear()
                    }
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            if (currentRow.any { it.isNotEmpty() }) rows.add(currentRow.toList())
        }
        return rows
    }
}
