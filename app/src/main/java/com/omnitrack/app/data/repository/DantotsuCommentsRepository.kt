package com.omnitrack.app.data.repository

import android.content.Context
import com.omnitrack.app.OmniTrackApplication
import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.ui.screens.detail.components.DiscussionComment
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 3: Dantotsu Comment DB Integration.
 * Endpoints: https://api.dantotsu.app
 * Handshake key: 6*45Qp%W2RS@t38jkXoSKY588Ynj%n
 *
 * 1:1 Dartotsu parity:
 * - 6-day session persistence in SharedPreferences.
 * - Single-flight Mutex lock on /authenticate prevents 429 concurrent storms.
 * - 1 single HTTP request for comments page (no recursive N+1 replies loop).
 * - Automatic 401 re-authentication and retry.
 * - On-demand reply loading.
 */
class DantotsuCommentsRepository(
    private val client: OkHttpClient = NetworkClient.sharedClient
) {
    companion object {
        private const val TAG = "DantotsuComments"
        private const val BASE_URL = "https://api.dantotsu.app"
        private const val APP_AUTH_KEY = "6*45Qp%W2RS@t38jkXoSKY588Ynj%n"
        private const val PREFS_NAME = "dantotsu_comments_session"
        private const val SESSION_LIFETIME_MS = 6L * 24 * 60 * 60 * 1000L // 6 days

        private val authMutex = Mutex()

        @Volatile
        private var cachedAuthToken: String? = null

        @Volatile
        private var cachedUserId: String? = null

        @Volatile
        private var cachedUsername: String? = null

        @Volatile
        private var cachedUserAvatar: String? = null

        @Volatile
        private var cachedForToken: String? = null

        @Volatile
        private var cachedIssuedAt: Long = 0L

        private val commentCache = ConcurrentHashMap<String, List<DiscussionComment>>()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun getCurrentUsername(): String? = cachedUsername
    fun getCurrentUserAvatar(): String? = cachedUserAvatar
    fun getCurrentUserId(): String? = cachedUserId

    fun clearCache() {
        commentCache.clear()
    }

    fun signOut() {
        cachedAuthToken = null
        cachedUserId = null
        cachedUsername = null
        cachedUserAvatar = null
        cachedForToken = null
        cachedIssuedAt = 0L
        commentCache.clear()
        try {
            val prefs = OmniTrackApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        } catch (_: Exception) {}
    }

    /**
     * Authenticates with Dantotsu API using an AniList OAuth access token.
     * Uses SharedPreferences cache (6 days TTL) and a Mutex lock to prevent duplicate /authenticate calls.
     */
    suspend fun authenticate(anilistToken: String?, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (anilistToken.isNullOrBlank()) return@withContext false

        val now = System.currentTimeMillis()
        if (!force && cachedAuthToken != null && cachedForToken == anilistToken && (now - cachedIssuedAt) < SESSION_LIFETIME_MS) {
            return@withContext true
        }

        authMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (!force && cachedAuthToken != null && cachedForToken == anilistToken && (lockedNow - cachedIssuedAt) < SESSION_LIFETIME_MS) {
                return@withLock true
            }

            val prefs = try {
                OmniTrackApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            } catch (_: Exception) {
                null
            }

            if (!force && prefs != null) {
                val savedToken = prefs.getString("auth_token", null)
                val savedForToken = prefs.getString("for_token", null)
                val savedIssuedAt = prefs.getLong("issued_at", 0L)
                if (!savedToken.isNullOrBlank() && savedForToken == anilistToken && (lockedNow - savedIssuedAt) < SESSION_LIFETIME_MS) {
                    cachedAuthToken = savedToken
                    cachedForToken = savedForToken
                    cachedIssuedAt = savedIssuedAt
                    cachedUserId = prefs.getString("user_id", null)
                    cachedUsername = prefs.getString("username", null)
                    cachedUserAvatar = prefs.getString("avatar", null)
                    AppLogger.d(TAG, "Restored active Dantotsu session from persistent storage for user $cachedUsername")
                    return@withLock true
                }
            }

            try {
                val body = FormBody.Builder()
                    .add("token", anilistToken)
                    .build()

                val request = Request.Builder()
                    .url("$BASE_URL/authenticate")
                    .header("appauth", APP_AUTH_KEY)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        AppLogger.w(TAG, "Dantotsu authenticate HTTP ${response.code}")
                        return@withLock false
                    }

                    val responseBody = response.body?.string() ?: return@withLock false
                    val parsed = json.parseToJsonElement(responseBody).jsonObject

                    val token = parsed["authToken"]?.jsonPrimitive?.contentOrNull
                    val user = parsed["user"]?.jsonObject

                    if (token != null) {
                        val issuedTime = System.currentTimeMillis()
                        cachedAuthToken = token
                        cachedForToken = anilistToken
                        cachedIssuedAt = issuedTime
                        cachedUserId = user?.get("user_id")?.jsonPrimitive?.contentOrNull ?: user?.get("id")?.jsonPrimitive?.contentOrNull
                        cachedUsername = user?.get("username")?.jsonPrimitive?.contentOrNull
                        cachedUserAvatar = user?.get("profile_picture_url")?.jsonPrimitive?.contentOrNull

                        prefs?.edit()
                            ?.putString("auth_token", token)
                            ?.putString("for_token", anilistToken)
                            ?.putLong("issued_at", issuedTime)
                            ?.putString("user_id", cachedUserId)
                            ?.putString("username", cachedUsername)
                            ?.putString("avatar", cachedUserAvatar)
                            ?.apply()

                        AppLogger.i(TAG, "Dantotsu authenticated successfully for $cachedUsername")
                        true
                    } else {
                        false
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error during Dantotsu authenticate", e)
                false
            }
        }
    }

    /**
     * Fetches top-level comments for an AniList media item (1 single HTTP request).
     */
    suspend fun getComments(
        anilistId: Long,
        page: Int = 1,
        tag: Int? = null,
        tagPrefix: String = "Ep",
        sort: String = "newest",
        anilistToken: String? = null,
        retried: Boolean = false
    ): List<DiscussionComment> = withContext(Dispatchers.IO) {
        if (anilistId <= 0L) return@withContext emptyList()

        val cacheKey = "${anilistId}_${page}_${tag ?: -1}_$sort"
        if (page == 1 && commentCache.containsKey(cacheKey)) {
            return@withContext commentCache[cacheKey] ?: emptyList()
        }

        if (cachedAuthToken == null && !anilistToken.isNullOrBlank()) {
            authenticate(anilistToken)
        }

        var url = "$BASE_URL/comments/$anilistId/$page"
        val queryParams = mutableListOf<String>()
        if (tag != null && tag > 0) queryParams.add("tag=$tag")
        val apiSort = when (sort.lowercase()) {
            "top", "highest_rated" -> "highest_rated"
            "bottom", "lowest_rated" -> "lowest_rated"
            "oldest" -> "oldest"
            else -> null // On page 1, omit sort for "newest" matching Dantotsu CommentsFragment.kt:789
        }
        if (page > 1 && sort.isNotBlank()) {
            queryParams.add("sort=$sort")
        } else if (apiSort != null) {
            queryParams.add("sort=$apiSort")
        }
        if (queryParams.isNotEmpty()) url += "?" + queryParams.joinToString("&")

        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("appauth", APP_AUTH_KEY)

            cachedAuthToken?.let { reqBuilder.header("Authorization", it) }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.code == 401 && !retried && !anilistToken.isNullOrBlank()) {
                    AppLogger.w(TAG, "Dantotsu 401 Unauthorized - re-authenticating with force=true")
                    cachedAuthToken = null
                    val authSuccess = authenticate(anilistToken, force = true)
                    if (authSuccess) {
                        return@withContext getComments(
                            anilistId = anilistId,
                            page = page,
                            tag = tag,
                            tagPrefix = tagPrefix,
                            sort = sort,
                            anilistToken = anilistToken,
                            retried = true
                        )
                    }
                }

                if (response.code == 404) {
                    AppLogger.d(TAG, "getComments HTTP 404 (no comments yet) for anilistId=$anilistId, tag=$tag")
                    return@withContext emptyList()
                }

                if (!response.isSuccessful) {
                    AppLogger.w(TAG, "getComments failed with HTTP ${response.code} for anilistId=$anilistId, tag=$tag")
                    return@withContext emptyList()
                }

                val body = response.body?.string() ?: return@withContext emptyList()
                if (!body.startsWith("{")) return@withContext emptyList()

                val root = json.parseToJsonElement(body).jsonObject
                val commentsArray = root["comments"]?.jsonArray ?: return@withContext emptyList()
                AppLogger.d(TAG, "getComments HTTP 200: ${commentsArray.size} comments for anilistId=$anilistId, tag=$tag")

                val commentsList = ArrayList<DiscussionComment>(commentsArray.size)
                for (elem in commentsArray) {
                    val c = elem.jsonObject
                    val commentId = c["comment_id"]?.jsonPrimitive?.intOrNull
                        ?: c["id"]?.jsonPrimitive?.intOrNull
                        ?: continue
                    val isDeleted = (c["deleted"]?.jsonPrimitive?.intOrNull ?: 0) == 1
                    val rawContent = c["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    val content = if (isDeleted) "[deleted]" else rawContent
                    val tagNum = c["tag"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        ?: (try { c["tag"]?.jsonPrimitive?.intOrNull } catch (_: Exception) { null })
                        ?: tag
                    val tagStr = when {
                        tagNum != null && tagNum > 0 -> "$tagPrefix $tagNum"
                        tagNum == 0 -> "General"
                        tag != null && tag > 0 -> "$tagPrefix $tag"
                        else -> null
                    }
                    val username = c["username"]?.jsonPrimitive?.contentOrNull ?: "User"
                    val avatar = c["profile_picture_url"]?.jsonPrimitive?.contentOrNull
                    val anilistUserId = c["user_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val timestamp = c["timestamp"]?.jsonPrimitive?.contentOrNull ?: ""
                    val upvotes = c["upvotes"]?.jsonPrimitive?.intOrNull ?: 0
                    val downvotes = c["downvotes"]?.jsonPrimitive?.intOrNull ?: 0
                    val replyCount = c["reply_count"]?.jsonPrimitive?.intOrNull ?: 0

                    commentsList.add(
                        DiscussionComment(
                            id = commentId.toString(),
                            author = username,
                            authorAvatarUrl = avatar,
                            anilistUserId = anilistUserId,
                            tag = tagStr,
                            content = content,
                            timeAgo = formatRelativeTime(timestamp),
                            upvotes = upvotes,
                            downvotes = downvotes,
                            parentId = null,
                            replies = emptyList(),
                            replyCount = replyCount,
                            depth = 0
                        )
                    )
                }

                val sortedList = when (sort.lowercase()) {
                    "oldest" -> commentsList.reversed()
                    "top", "highest_rated" -> commentsList.sortedByDescending { it.upvotes - it.downvotes }
                    "bottom", "lowest_rated" -> commentsList.sortedBy { it.upvotes - it.downvotes }
                    else -> commentsList
                }

                if (page == 1 && sortedList.isNotEmpty()) {
                    commentCache[cacheKey] = sortedList
                }
                sortedList
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Exception during getComments for anilistId=$anilistId, tag=$tag", e)
            emptyList()
        }
    }

    /**
     * Fetches replies for a specific parent comment on demand.
     */
    suspend fun getReplies(
        parentCommentId: Long,
        page: Int = 1,
        tagPrefix: String = "Ep",
        depth: Int = 1,
        anilistToken: String? = null,
        retried: Boolean = false
    ): List<DiscussionComment> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/comments/parent/$parentCommentId/$page"
        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("appauth", APP_AUTH_KEY)

            cachedAuthToken?.let { reqBuilder.header("Authorization", it) }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.code == 401 && !retried && !anilistToken.isNullOrBlank()) {
                    cachedAuthToken = null
                    val authSuccess = authenticate(anilistToken, force = true)
                    if (authSuccess) {
                        return@withContext getReplies(
                            parentCommentId = parentCommentId,
                            page = page,
                            tagPrefix = tagPrefix,
                            depth = depth,
                            anilistToken = anilistToken,
                            retried = true
                        )
                    }
                }

                if (!response.isSuccessful) return@withContext emptyList()

                val body = response.body?.string() ?: return@withContext emptyList()
                if (!body.startsWith("{")) return@withContext emptyList()

                val root = json.parseToJsonElement(body).jsonObject
                val commentsArray = root["comments"]?.jsonArray ?: return@withContext emptyList()

                val repliesList = ArrayList<DiscussionComment>(commentsArray.size)
                for (elem in commentsArray) {
                    val c = elem.jsonObject
                    val commentId = c["comment_id"]?.jsonPrimitive?.intOrNull
                        ?: c["id"]?.jsonPrimitive?.intOrNull
                        ?: continue
                    val isDeleted = (c["deleted"]?.jsonPrimitive?.intOrNull ?: 0) == 1
                    val rawContent = c["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    val content = if (isDeleted) "[deleted]" else rawContent
                    val tagNum = c["tag"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        ?: (try { c["tag"]?.jsonPrimitive?.intOrNull } catch (_: Exception) { null })
                    val tagStr = when {
                        tagNum != null && tagNum > 0 -> "$tagPrefix $tagNum"
                        tagNum == 0 -> "General"
                        else -> null
                    }
                    val username = c["username"]?.jsonPrimitive?.contentOrNull ?: "User"
                    val avatar = c["profile_picture_url"]?.jsonPrimitive?.contentOrNull
                    val anilistUserId = c["user_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val timestamp = c["timestamp"]?.jsonPrimitive?.contentOrNull ?: ""
                    val upvotes = c["upvotes"]?.jsonPrimitive?.intOrNull ?: 0
                    val downvotes = c["downvotes"]?.jsonPrimitive?.intOrNull ?: 0
                    val childReplyCount = c["reply_count"]?.jsonPrimitive?.intOrNull ?: 0

                    repliesList.add(
                        DiscussionComment(
                            id = commentId.toString(),
                            author = username,
                            authorAvatarUrl = avatar,
                            anilistUserId = anilistUserId,
                            tag = tagStr,
                            content = content,
                            timeAgo = formatRelativeTime(timestamp),
                            upvotes = upvotes,
                            downvotes = downvotes,
                            parentId = parentCommentId.toString(),
                            replies = emptyList(),
                            replyCount = childReplyCount,
                            depth = depth
                        )
                    )
                }
                repliesList
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error fetching replies for parentCommentId=$parentCommentId", e)
            emptyList()
        }
    }

    /**
     * Submits a new comment or threaded reply.
     */
    suspend fun postComment(
        anilistId: Long,
        content: String,
        tag: Int? = null,
        parentCommentId: Long? = null
    ): DiscussionComment? = withContext(Dispatchers.IO) {
        val token = cachedAuthToken ?: return@withContext null
        val uid = cachedUserId ?: return@withContext null

        try {
            val formBuilder = FormBody.Builder()
                .add("user_id", uid)
                .add("media_id", anilistId.toString())
                .add("content", content)

            if (tag != null) {
                formBuilder.add("tag", tag.toString())
            }
            if (parentCommentId != null) {
                formBuilder.add("parent_comment_id", parentCommentId.toString())
            }

            val request = Request.Builder()
                .url("$BASE_URL/comments")
                .header("appauth", APP_AUTH_KEY)
                .header("Authorization", token)
                .post(formBuilder.build())
                .build()

            val parsed = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                json.parseToJsonElement(body).jsonObject
            }

            val commentId = parsed["comment_id"]?.jsonPrimitive?.intOrNull
                ?: parsed["id"]?.jsonPrimitive?.intOrNull
                ?: System.currentTimeMillis().toInt()

            commentCache.clear()

            DiscussionComment(
                id = commentId.toString(),
                author = cachedUsername ?: "You",
                authorAvatarUrl = cachedUserAvatar,
                anilistUserId = uid.toIntOrNull(),
                tag = if (tag != null && tag > 0) "Ep $tag" else null,
                content = content,
                timeAgo = "Just now",
                upvotes = 1,
                downvotes = 0,
                parentId = parentCommentId?.toString(),
                replies = emptyList(),
                replyCount = 0,
                depth = if (parentCommentId != null) 1 else 0
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error posting comment", e)
            null
        }
    }

    /**
     * Votes on a comment: 1 = upvote, -1 = downvote, 0 = neutral.
     */
    suspend fun vote(commentId: Long, voteType: Int): Boolean = withContext(Dispatchers.IO) {
        val token = cachedAuthToken ?: return@withContext false
        try {
            val request = Request.Builder()
                .url("$BASE_URL/comments/vote/$commentId/$voteType")
                .header("appauth", APP_AUTH_KEY)
                .header("Authorization", token)
                .post(FormBody.Builder().build())
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error voting on comment $commentId", e)
            false
        }
    }

    suspend fun editComment(commentId: Long, newContent: String): Boolean = withContext(Dispatchers.IO) {
        val token = cachedAuthToken ?: return@withContext false
        try {
            val body = FormBody.Builder()
                .add("content", newContent)
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/comments/edit/$commentId")
                .header("appauth", APP_AUTH_KEY)
                .header("Authorization", token)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error editing comment $commentId", e)
            false
        }
    }

    suspend fun deleteComment(commentId: Long): Boolean = withContext(Dispatchers.IO) {
        val token = cachedAuthToken ?: return@withContext false
        try {
            val request = Request.Builder()
                .url("$BASE_URL/comments/$commentId")
                .header("appauth", APP_AUTH_KEY)
                .header("Authorization", token)
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting comment $commentId", e)
            false
        }
    }

    private fun formatRelativeTime(isoString: String): String {
        return try {
            val instant = Instant.parse(isoString)
            val diffSec = (System.currentTimeMillis() - instant.toEpochMilli()) / 1000
            when {
                diffSec < 60 -> "Just now"
                diffSec < 3600 -> "${diffSec / 60}m ago"
                diffSec < 86400 -> "${diffSec / 3600}h ago"
                diffSec < 604800 -> "${diffSec / 86400}d ago"
                else -> "${diffSec / 604800}w ago"
            }
        } catch (e: Exception) {
            "Recently"
        }
    }
}
