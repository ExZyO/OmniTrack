package com.omnitrack.app.data.repository

import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.ui.screens.detail.components.DiscussionComment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 3: Dantotsu Comment DB Integration.
 * Endpoints: https://api.dantotsu.app
 * Auth handshake key: 6*45Qp%W2RS@t38jkXoSKY588Ynj%n
 *
 * Rules:
 * - Strictly requires AniList ID (never pass TMDb or Room IDs).
 * - Threaded comments via /comments/:media_id/:page and /comments/parent/:parent_id/:page.
 * - Dynamic tag support ("Ep 1, Ep 2..." for Anime, "Ch 1, Ch 2..." for Manga/Novels).
 */
class DantotsuCommentsRepository(
    private val client: okhttp3.OkHttpClient = NetworkClient.sharedClient
) {
    companion object {
        private const val BASE_URL = "https://api.dantotsu.app"
        private const val APP_AUTH_KEY = "6*45Qp%W2RS@t38jkXoSKY588Ynj%n"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Cached session token issued by Dantotsu /authenticate
    @Volatile
    private var dantotsuAuthToken: String? = null

    @Volatile
    private var currentUserId: String? = null

    @Volatile
    private var currentUsername: String? = null

    @Volatile
    private var currentUserAvatar: String? = null

    fun getCurrentUsername(): String? = currentUsername
    fun getCurrentUserAvatar(): String? = currentUserAvatar
    fun getCurrentUserId(): String? = currentUserId

    // In-memory cache for fast tab switches
    private val commentCache = ConcurrentHashMap<String, List<DiscussionComment>>()

    /**
     * Authenticates with Dantotsu API using an AniList OAuth access token.
     */
    suspend fun authenticate(anilistToken: String?): Boolean = withContext(Dispatchers.IO) {
        if (anilistToken.isNullOrBlank()) return@withContext false
        if (dantotsuAuthToken != null) return@withContext true

        try {
            val body = FormBody.Builder()
                .add("token", anilistToken)
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/authenticate")
                .header("appauth", APP_AUTH_KEY)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false

            val responseBody = response.body?.string() ?: return@withContext false
            val parsed = json.parseToJsonElement(responseBody).jsonObject

            val token = parsed["authToken"]?.jsonPrimitive?.contentOrNull
            val user = parsed["user"]?.jsonObject

            if (token != null) {
                dantotsuAuthToken = token
                currentUserId = user?.get("user_id")?.jsonPrimitive?.contentOrNull ?: user?.get("id")?.jsonPrimitive?.contentOrNull
                currentUsername = user?.get("username")?.jsonPrimitive?.contentOrNull
                currentUserAvatar = user?.get("profile_picture_url")?.jsonPrimitive?.contentOrNull
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetches paginated top-level comments and nested replies for an AniList media item.
     */
    suspend fun getComments(
        anilistId: Long,
        page: Int = 1,
        tag: Int? = null,
        tagPrefix: String = "Ep",
        sort: String = "newest"
    ): List<DiscussionComment> = withContext(Dispatchers.IO) {
        val cacheKey = "${anilistId}_${page}_${tag ?: -1}_$sort"
        if (page == 1 && commentCache.containsKey(cacheKey)) {
            return@withContext commentCache[cacheKey] ?: emptyList()
        }

        var url = "$BASE_URL/comments/$anilistId/$page"
        val queryParams = mutableListOf<String>()
        if (tag != null && tag > 0) queryParams.add("tag=$tag")
        if (sort.isNotBlank()) queryParams.add("sort=$sort")
        if (queryParams.isNotEmpty()) url += "?" + queryParams.joinToString("&")

        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("appauth", APP_AUTH_KEY)

            dantotsuAuthToken?.let { reqBuilder.header("Authorization", it) }

            val response = client.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            if (!body.startsWith("{")) return@withContext emptyList()

            val root = json.parseToJsonElement(body).jsonObject
            val commentsArray = root["comments"]?.jsonArray ?: return@withContext emptyList()

            val commentsList = mutableListOf<DiscussionComment>()

            for (elem in commentsArray) {
                val c = elem.jsonObject
                val commentId = c["comment_id"]?.jsonPrimitive?.intOrNull ?: continue
                val isDeleted = (c["deleted"]?.jsonPrimitive?.intOrNull ?: 0) == 1
                val rawContent = c["content"]?.jsonPrimitive?.contentOrNull ?: ""
                val content = if (isDeleted) "[deleted]" else rawContent
                val tagNum = c["tag"]?.jsonPrimitive?.intOrNull
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
                val replyCount = c["reply_count"]?.jsonPrimitive?.intOrNull ?: 0

                // Fetch replies if replies exist
                val replies = if (replyCount > 0) {
                    getReplies(commentId.toLong(), tagPrefix = tagPrefix, depth = 1)
                } else {
                    emptyList()
                }

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
                        replies = replies,
                        depth = 0
                    )
                )
            }

            if (page == 1) {
                commentCache[cacheKey] = commentsList
            }
            commentsList
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Fetches threaded replies for a specific parent comment.
     */
    suspend fun getReplies(
        parentCommentId: Long,
        page: Int = 1,
        tagPrefix: String = "Ep",
        depth: Int = 1
    ): List<DiscussionComment> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/comments/parent/$parentCommentId/$page"
        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("appauth", APP_AUTH_KEY)

            dantotsuAuthToken?.let { reqBuilder.header("Authorization", it) }

            val response = client.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            if (!body.startsWith("{")) return@withContext emptyList()

            val root = json.parseToJsonElement(body).jsonObject
            val commentsArray = root["comments"]?.jsonArray ?: return@withContext emptyList()

            val repliesList = mutableListOf<DiscussionComment>()
            for (elem in commentsArray) {
                val c = elem.jsonObject
                val commentId = c["comment_id"]?.jsonPrimitive?.intOrNull ?: continue
                val isDeleted = (c["deleted"]?.jsonPrimitive?.intOrNull ?: 0) == 1
                val rawContent = c["content"]?.jsonPrimitive?.contentOrNull ?: ""
                val content = if (isDeleted) "[deleted]" else rawContent
                val tagNum = c["tag"]?.jsonPrimitive?.intOrNull
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

                // Recurse for deeper nested replies if available (max depth 3)
                val nestedReplies = if (childReplyCount > 0 && depth < 3) {
                    getReplies(commentId.toLong(), tagPrefix = tagPrefix, depth = depth + 1)
                } else {
                    emptyList()
                }

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
                        replies = nestedReplies,
                        depth = depth
                    )
                )
            }
            repliesList
        } catch (e: Exception) {
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
        val token = dantotsuAuthToken ?: return@withContext null
        val uid = currentUserId ?: return@withContext null

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

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val parsed = json.parseToJsonElement(body).jsonObject

            val commentId = parsed["comment_id"]?.jsonPrimitive?.intOrNull ?: parsed["id"]?.jsonPrimitive?.intOrNull ?: System.currentTimeMillis().toInt()

            DiscussionComment(
                id = commentId.toString(),
                author = currentUsername ?: "You",
                authorAvatarUrl = currentUserAvatar,
                anilistUserId = uid.toIntOrNull(),
                tag = if (tag != null && tag > 0) "Ep $tag" else "General",
                content = content,
                timeAgo = "Just now",
                upvotes = 1,
                downvotes = 0,
                parentId = parentCommentId?.toString()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Votes on a comment: 1 = upvote, -1 = downvote, 0 = neutral.
     */
    suspend fun vote(commentId: Long, voteType: Int): Boolean = withContext(Dispatchers.IO) {
        val token = dantotsuAuthToken ?: return@withContext false
        try {
            val request = Request.Builder()
                .url("$BASE_URL/comments/vote/$commentId/$voteType")
                .header("appauth", APP_AUTH_KEY)
                .header("Authorization", token)
                .post(FormBody.Builder().build())
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun editComment(commentId: Long, newContent: String): Boolean = withContext(Dispatchers.IO) {
        val token = dantotsuAuthToken ?: return@withContext false
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

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteComment(commentId: Long): Boolean = withContext(Dispatchers.IO) {
        val token = dantotsuAuthToken ?: return@withContext false
        try {
            val request = Request.Builder()
                .url("$BASE_URL/comments/delete/$commentId")
                .header("appauth", APP_AUTH_KEY)
                .header("Authorization", token)
                .post(FormBody.Builder().build())
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
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
