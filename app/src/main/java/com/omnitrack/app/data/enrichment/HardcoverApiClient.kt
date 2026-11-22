package com.omnitrack.app.data.enrichment

import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class HardcoverBookDetails(
    val hardcoverId: String,
    val title: String,
    val slug: String? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val releaseYear: String? = null,
    val pages: Int? = null,
    val audioSeconds: Int? = null,
    val rating5: Float? = null,
    val ratingsCount: Int? = null,
    val authorName: String? = null,
    val seriesName: String? = null,
    val seriesPosition: String? = null,
    val seriesBooksCount: Int? = null,
    val moods: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val contentWarnings: List<String> = emptyList(),
    val isbn13: String? = null
)

data class HardcoverUserBookRecord(
    val hardcoverId: String,
    val title: String,
    val author: String? = null,
    val coverUrl: String? = null,
    val releaseYear: String? = null,
    val pages: Int? = null,
    val isbn13: String? = null,
    val status: TrackingStatus = TrackingStatus.PLAN_TO_TRACK,
    val userRating10: Float? = null,
    val currentPage: Double = 0.0
)

class HardcoverApiClient(
    private val client: OkHttpClient = NetworkClient.getClient()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val endpoint = "https://api.hardcover.app/v1/graphql"

    @Volatile
    var lastError: String? = null

    @Volatile
    var lastHttpCode: Int? = null

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.str(): String? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) }
    private fun JsonElement?.intVal(): Int? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
    private fun JsonElement?.floatVal(): Float? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull

    private fun cleanAuth(rawToken: String): String {
        val t = rawToken.trim().removePrefix("Bearer ").removePrefix("bearer ").trim()
        return "Bearer $t"
    }

    private suspend fun executeGraphQl(token: String, query: String, variables: JsonObject? = null): JsonObject? =
        NetworkPacer.withHardcoverPacing {
            if (token.isBlank()) return@withHardcoverPacing null
            lastError = null
            lastHttpCode = null
            try {
                val payload = buildJsonObject {
                    put("query", query)
                    if (variables != null) put("variables", variables)
                }
                val req = Request.Builder()
                    .url(endpoint)
                    .header("Authorization", cleanAuth(token))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "OmniTrack/1.0 (Android)")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    lastHttpCode = resp.code
                    if (!resp.isSuccessful) {
                        lastError = "Hardcover HTTP ${resp.code}"
                        AppLogger.w("TrackerSync", "service=HARDCOVER op=graphql http=${resp.code}")
                        return@withHardcoverPacing null
                    }
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) return@withHardcoverPacing null
                    val root = json.parseToJsonElement(body).obj() ?: return@withHardcoverPacing null
                    val errMsg = root["errors"].arr()?.firstOrNull().obj()?.get("message").str()
                    if (!errMsg.isNullOrBlank()) {
                        lastError = "Hardcover GraphQL: $errMsg"
                        AppLogger.w("TrackerSync", "service=HARDCOVER op=graphql graphqlError=true")
                    }
                    root["data"].obj()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastError = "Hardcover network error (${e.javaClass.simpleName})"
                AppLogger.w("TrackerSync", "service=HARDCOVER op=graphql ex=${e.javaClass.simpleName}")
                null
            }
        }

    suspend fun verifyToken(token: String): String? = withContext(Dispatchers.IO) {
        val data = executeGraphQl(token, "query { me { id username name } }") ?: return@withContext null
        val meObj = data["me"].arr()?.firstOrNull().obj() ?: data["me"].obj() ?: return@withContext null
        meObj["username"].str() ?: meObj["name"].str() ?: meObj["id"].str()
    }

    suspend fun searchBooks(token: String, query: String, limit: Int = 10): List<SearchResultItem> = withContext(Dispatchers.IO) {
        if (token.isBlank() || query.isBlank()) return@withContext emptyList()
        val gql = """
            query SearchBooks(${'$'}q: String!, ${'$'}limit: Int!) {
              books(where: {title: {_ilike: ${'$'}q}}, limit: ${'$'}limit, order_by: {users_read_count: desc}) {
                id
                title
                slug
                pages
                release_year
                rating
                description
                image { url }
                contributions(limit: 2) { author { name } }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("q", "%${query.trim()}%")
            put("limit", limit)
        }
        val data = executeGraphQl(token, gql, vars) ?: return@withContext emptyList()
        val arr = data["books"].arr() ?: return@withContext emptyList()
        arr.mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val id = o["id"].str() ?: return@mapNotNull null
            val title = o["title"].str() ?: return@mapNotNull null
            val cover = o["image"].obj()?.get("url").str().orEmpty()
            val author = o["contributions"].arr()?.mapNotNull {
                it.obj()?.get("author").obj()?.get("name").str()
            }?.distinct()?.joinToString(", ")
            val rating5 = o["rating"].floatVal()
            SearchResultItem(
                title = title,
                synopsis = o["description"].str(),
                coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                mediaType = MediaType.BOOK,
                releaseYear = o["release_year"].str(),
                communityScore = rating5?.let { (it * 2f).coerceIn(0f, 10f) },
                sourceProvider = "Hardcover",
                hardcoverId = id,
                bookAuthor = author,
                totalProgress = o["pages"].intVal()?.toDouble()
            )
        }
    }

    suspend fun fetchBookDetails(
        token: String,
        hardcoverId: String?,
        title: String,
        author: String? = null
    ): HardcoverBookDetails? = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext null
        val idNum = hardcoverId?.trim()?.toIntOrNull()
        val gql = if (idNum != null) {
            """
            query BookById(${'$'}id: Int!) {
              books(where: {id: {_eq: ${'$'}id}}, limit: 1) {
                id title slug pages audio_seconds release_year rating ratings_count description
                image { url }
                contributions(limit: 3) { author { name } }
                book_series(limit: 1) { position series { name books_count } }
                cached_tags
              }
            }
            """.trimIndent()
        } else {
            """
            query BookByTitle(${'$'}q: String!) {
              books(where: {title: {_ilike: ${'$'}q}}, limit: 4, order_by: {users_read_count: desc}) {
                id title slug pages audio_seconds release_year rating ratings_count description
                image { url }
                contributions(limit: 3) { author { name } }
                book_series(limit: 1) { position series { name books_count } }
                cached_tags
              }
            }
            """.trimIndent()
        }
        val vars = buildJsonObject {
            if (idNum != null) put("id", idNum) else put("q", title.trim())
        }
        val data = executeGraphQl(token, gql, vars) ?: return@withContext null
        val arr = data["books"].arr() ?: return@withContext null
        val match = if (idNum != null || author.isNullOrBlank()) {
            arr.firstOrNull().obj()
        } else {
            arr.firstOrNull { el ->
                val contribs = el.obj()?.get("contributions").arr().orEmpty()
                contribs.any { c ->
                    val name = c.obj()?.get("author").obj()?.get("name").str().orEmpty()
                    name.contains(author.split(",").first().trim(), ignoreCase = true)
                }
            }.obj() ?: arr.firstOrNull().obj()
        } ?: return@withContext null

        val resolvedId = match["id"].str() ?: return@withContext null
        val resolvedTitle = match["title"].str() ?: title
        val authors = match["contributions"].arr()?.mapNotNull {
            it.obj()?.get("author").obj()?.get("name").str()
        }?.distinct()?.joinToString(", ")?.takeIf { it.isNotBlank() }

        val seriesEntry = match["book_series"].arr()?.firstOrNull().obj()
        val seriesObj = seriesEntry?.get("series").obj()

        val moods = mutableListOf<String>()
        val genres = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val cachedTags = match["cached_tags"].obj()
        cachedTags?.get("Mood")?.arr()?.forEach { t ->
            t.obj()?.get("tag").str()?.let { moods.add(it) }
        }
        cachedTags?.get("Genre")?.arr()?.forEach { t ->
            t.obj()?.get("tag").str()?.let { genres.add(it) }
        }
        cachedTags?.get("Content Warning")?.arr()?.forEach { t ->
            t.obj()?.get("tag").str()?.let { warnings.add(it) }
        }

        HardcoverBookDetails(
            hardcoverId = resolvedId,
            title = resolvedTitle,
            slug = match["slug"].str(),
            description = match["description"].str(),
            coverUrl = match["image"].obj()?.get("url").str(),
            releaseYear = match["release_year"].str(),
            pages = match["pages"].intVal(),
            audioSeconds = match["audio_seconds"].intVal(),
            rating5 = match["rating"].floatVal(),
            ratingsCount = match["ratings_count"].intVal(),
            authorName = authors,
            seriesName = seriesObj?.get("name").str(),
            seriesPosition = seriesEntry?.get("position").str(),
            seriesBooksCount = seriesObj?.get("books_count").intVal(),
            moods = moods.distinct().take(10),
            genres = genres.distinct().take(10),
            contentWarnings = warnings.distinct().take(6)
        )
    }

    suspend fun pullUserBooks(token: String): List<HardcoverUserBookRecord> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext emptyList()
        val gql = """
            query UserLibrary {
              me {
                user_books(limit: 200) {
                  status_id
                  rating
                  book {
                    id
                    title
                    pages
                    release_year
                    image { url }
                    contributions(limit: 2) { author { name } }
                  }
                  user_book_reads(limit: 1, order_by: {started_at: desc}) {
                    progress_pages
                  }
                }
              }
            }
        """.trimIndent()
        val data = executeGraphQl(token, gql) ?: return@withContext emptyList()
        val meObj = data["me"].arr()?.firstOrNull().obj() ?: data["me"].obj() ?: return@withContext emptyList()
        val uBooks = meObj["user_books"].arr() ?: return@withContext emptyList()
        uBooks.mapNotNull { el ->
            val ub = el.obj() ?: return@mapNotNull null
            val b = ub["book"].obj() ?: return@mapNotNull null
            val hcId = b["id"].str() ?: return@mapNotNull null
            val title = b["title"].str() ?: return@mapNotNull null
            val author = b["contributions"].arr()?.mapNotNull {
                it.obj()?.get("author").obj()?.get("name").str()
            }?.distinct()?.joinToString(", ")
            val statusId = ub["status_id"].intVal() ?: 1
            val status = when (statusId) {
                2 -> TrackingStatus.IN_PROGRESS
                3 -> TrackingStatus.COMPLETED
                4 -> TrackingStatus.ON_HOLD
                5 -> TrackingStatus.DROPPED
                else -> TrackingStatus.PLAN_TO_TRACK
            }
            val curPage = ub["user_book_reads"].arr()?.firstOrNull().obj()?.get("progress_pages")?.intVal()?.toDouble() ?: 0.0
            val rating5 = ub["rating"].floatVal()
            HardcoverUserBookRecord(
                hardcoverId = hcId,
                title = title,
                author = author,
                coverUrl = b["image"].obj()?.get("url").str(),
                releaseYear = b["release_year"].str(),
                pages = b["pages"].intVal(),
                status = status,
                userRating10 = rating5?.let { (it * 2f).coerceIn(1f, 10f) },
                currentPage = curPage
            )
        }
    }

    suspend fun syncBookStatus(
        token: String,
        hardcoverId: String,
        status: TrackingStatus,
        rating10: Float?,
        currentPage: Int? = null,
        reviewNote: String? = null,
        isPrivate: Boolean = false
    ): Boolean = syncBookStatusDetailed(
        token = token,
        hardcoverId = hardcoverId,
        status = status,
        rating10 = rating10,
        currentPage = currentPage,
        reviewNote = reviewNote,
        isPrivate = isPrivate
    ).first

    suspend fun syncBookStatusDetailed(
        token: String,
        hardcoverId: String,
        status: TrackingStatus,
        rating10: Float?,
        currentPage: Int? = null,
        reviewNote: String? = null,
        isPrivate: Boolean = false
    ): Pair<Boolean, Int?> = withContext(Dispatchers.IO) {
        val bookIdInt = hardcoverId.trim().toIntOrNull() ?: return@withContext false to null
        val statusId = when (status) {
            TrackingStatus.PLAN_TO_TRACK -> 1
            TrackingStatus.IN_PROGRESS -> 2
            TrackingStatus.COMPLETED -> 3
            TrackingStatus.ON_HOLD -> 4
            TrackingStatus.DROPPED -> 5
            else -> 2
        }
        val cleanNote = reviewNote?.trim()?.takeIf { it.isNotBlank() }
        val gql = """
            mutation UpdateBook(${'$'}bookId: Int!, ${'$'}statusId: Int!, ${'$'}rating: numeric, ${'$'}review: String, ${'$'}priv: Boolean) {
              insert_user_book(object: {book_id: ${'$'}bookId, status_id: ${'$'}statusId, rating: ${'$'}rating, review_raw: ${'$'}review, privacy_setting_id: ${if (isPrivate) 3 else 1}}) {
                id
                user_book {
                  id
                }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("bookId", bookIdInt)
            put("statusId", statusId)
            if (rating10 != null && rating10 > 0f) {
                put("rating", (rating10 / 2f).coerceIn(0.5f, 5.0f))
            }
            if (cleanNote != null) {
                put("review", cleanNote)
            }
            put("priv", isPrivate)
        }
        val data = executeGraphQl(token, gql, vars)
        if (data == null || !lastError.isNullOrBlank()) {
            // Retry simpler insert_user_book signature if review_raw/privacy fields differ in schema
            val fallbackGql = """
                mutation UpdateBookBasic(${'$'}bookId: Int!, ${'$'}statusId: Int!, ${'$'}rating: numeric) {
                  insert_user_book(object: {book_id: ${'$'}bookId, status_id: ${'$'}statusId, rating: ${'$'}rating}) {
                    id
                  }
                }
            """.trimIndent()
            val fallbackVars = buildJsonObject {
                put("bookId", bookIdInt)
                put("statusId", statusId)
                if (rating10 != null && rating10 > 0f) {
                    put("rating", (rating10 / 2f).coerceIn(0.5f, 5.0f))
                }
            }
            val retryData = executeGraphQl(token, fallbackGql, fallbackVars)
            val userBookId = retryData?.get("insert_user_book").obj()?.get("id").intVal()
            if (userBookId != null && currentPage != null && currentPage > 0) {
                syncReadProgress(token, userBookId, currentPage)
            }
            return@withContext (retryData != null && lastError.isNullOrBlank()) to lastHttpCode
        }
        val inserted = data["insert_user_book"].obj()
        val userBookId = inserted?.get("user_book").obj()?.get("id").intVal()
            ?: inserted?.get("id").intVal()
        if (userBookId != null && currentPage != null && currentPage > 0) {
            syncReadProgress(token, userBookId, currentPage)
        }
        true to lastHttpCode
    }

    private suspend fun syncReadProgress(token: String, userBookId: Int, pagesRead: Int) {
        val gql = """
            mutation UpsertRead(${'$'}ubId: Int!, ${'$'}pages: Int!) {
              insert_user_book_read(user_book_id: ${'$'}ubId, user_book_read: {progress_pages: ${'$'}pages}) {
                id
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("ubId", userBookId)
            put("pages", pagesRead.coerceAtLeast(0))
        }
        executeGraphQl(token, gql, vars)
    }

    suspend fun fetchUserBookEntry(token: String, hardcoverId: String): HardcoverUserBookRecord? = withContext(Dispatchers.IO) {
        val bookIdInt = hardcoverId.trim().toIntOrNull() ?: return@withContext null
        val gql = """
            query SingleUserBook(${'$'}bookId: Int!) {
              me {
                user_books(where: {book_id: {_eq: ${'$'}bookId}}, limit: 1) {
                  status_id
                  rating
                  review_raw
                  book {
                    id
                    title
                    pages
                    release_year
                    image { url }
                    contributions(limit: 2) { author { name } }
                  }
                  user_book_reads(limit: 1, order_by: {started_at: desc}) {
                    progress_pages
                  }
                }
              }
            }
        """.trimIndent()
        val data = executeGraphQl(token, gql, buildJsonObject { put("bookId", bookIdInt) }) ?: return@withContext null
        val meObj = data["me"].arr()?.firstOrNull().obj() ?: data["me"].obj() ?: return@withContext null
        val ub = meObj["user_books"].arr()?.firstOrNull().obj() ?: return@withContext null
        val b = ub["book"].obj() ?: return@withContext null
        val hcId = b["id"].str() ?: hardcoverId
        val title = b["title"].str() ?: return@withContext null
        val author = b["contributions"].arr()?.mapNotNull {
            it.obj()?.get("author").obj()?.get("name").str()
        }?.distinct()?.joinToString(", ")
        val statusId = ub["status_id"].intVal() ?: 1
        val status = when (statusId) {
            2 -> TrackingStatus.IN_PROGRESS
            3 -> TrackingStatus.COMPLETED
            4 -> TrackingStatus.ON_HOLD
            5 -> TrackingStatus.DROPPED
            else -> TrackingStatus.PLAN_TO_TRACK
        }
        val curPage = ub["user_book_reads"].arr()?.firstOrNull().obj()?.get("progress_pages")?.intVal()?.toDouble() ?: 0.0
        val rating5 = ub["rating"].floatVal()
        HardcoverUserBookRecord(
            hardcoverId = hcId,
            title = title,
            author = author,
            coverUrl = b["image"].obj()?.get("url").str(),
            releaseYear = b["release_year"].str(),
            pages = b["pages"].intVal(),
            status = status,
            userRating10 = rating5?.let { (it * 2f).coerceIn(1f, 10f) },
            currentPage = curPage
        )
    }

    suspend fun deleteUserBook(token: String, hardcoverId: String): Pair<Boolean, Int?> = withContext(Dispatchers.IO) {
        val bookIdInt = hardcoverId.trim().toIntOrNull() ?: return@withContext false to null
        val findGql = """
            query FindUb(${'$'}bookId: Int!) {
              me {
                user_books(where: {book_id: {_eq: ${'$'}bookId}}, limit: 1) { id }
              }
            }
        """.trimIndent()
        val findData = executeGraphQl(token, findGql, buildJsonObject { put("bookId", bookIdInt) })
            ?: return@withContext false to lastHttpCode
        val meObj = findData["me"].arr()?.firstOrNull().obj() ?: findData["me"].obj()
        val ubId = meObj?.get("user_books").arr()?.firstOrNull().obj()?.get("id").intVal()
            ?: return@withContext true to lastHttpCode
        val delGql = """
            mutation DeleteUserBook(${'$'}id: Int!) {
              delete_user_book(id: ${'$'}id) { id }
            }
        """.trimIndent()
        val delRes = executeGraphQl(token, delGql, buildJsonObject { put("id", ubId) })
        (delRes != null && lastError.isNullOrBlank()) to lastHttpCode
    }

    suspend fun fetchUpcomingBooks(token: String, startDateIso: String, endDateIso: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext emptyList()
        val gql = """
            query UpcomingBooks(${'$'}start: date!, ${'$'}end: date!) {
              books(where: {release_date: {_gte: ${'$'}start, _lte: ${'$'}end}}, limit: 20, order_by: {users_read_count: desc}) {
                id
                title
                pages
                release_date
                rating
                image { url }
                contributions(limit: 2) { author { name } }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("start", startDateIso)
            put("end", endDateIso)
        }
        val data = executeGraphQl(token, gql, vars) ?: return@withContext emptyList()
        val arr = data["books"].arr() ?: return@withContext emptyList()
        arr.mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val id = o["id"].str() ?: return@mapNotNull null
            val title = o["title"].str() ?: return@mapNotNull null
            val cover = o["image"].obj()?.get("url").str().orEmpty()
            val author = o["contributions"].arr()?.mapNotNull {
                it.obj()?.get("author").obj()?.get("name").str()
            }?.distinct()?.joinToString(", ")
            SearchResultItem(
                title = title,
                coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                mediaType = MediaType.BOOK,
                releaseYear = o["release_date"].str(),
                communityScore = o["rating"].floatVal()?.let { (it * 2f).coerceIn(0f, 10f) },
                sourceProvider = "Hardcover",
                hardcoverId = id,
                bookAuthor = author,
                totalProgress = o["pages"].intVal()?.toDouble()
            )
        }
    }
}
