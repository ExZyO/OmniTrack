package com.omnitrack.app.data.model

data class UserProfileData(
    val userId: Int,
    val username: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bio: String? = null,
    val isFollowing: Boolean = false,
    val isFollower: Boolean = false,
    val animeCount: Int = 0,
    val episodesWatched: Int = 0,
    val animeDaysWatched: Double = 0.0,
    val animeMeanScore: Float = 0f,
    val mangaCount: Int = 0,
    val chaptersRead: Int = 0,
    val mangaMeanScore: Float = 0f,
    val topGenres: List<Pair<String, Int>> = emptyList(),
    val scoreDistribution: List<Pair<Int, Int>> = emptyList(),
    val statusBreakdown: List<Pair<String, Int>> = emptyList(),
    val formatBreakdown: List<Pair<String, Int>> = emptyList(),
    val activities: List<UserActivityItem> = emptyList(),
    val activityHistory: List<UserActivityDay> = emptyList(),
    val animeStats: UserStatSet = UserStatSet(),
    val mangaStats: UserStatSet = UserStatSet(),
    val favoriteAnime: List<UserFavoriteMedia> = emptyList(),
    val favoriteManga: List<UserFavoriteMedia> = emptyList(),
    val favoriteCharacters: List<UserFavoriteCharacter> = emptyList(),
    val favoriteStaff: List<UserFavoriteStaff> = emptyList()
)

data class UserActivityItem(
    val id: Int,
    val type: String,
    val mediaTitle: String? = null,
    val coverUrl: String? = null,
    val text: String,
    val timeAgo: String
)

data class UserActivityDay(
    val dateEpochSeconds: Long,
    val amount: Int,
    val level: Int // 0..4
)

data class UserStatSet(
    val count: Int = 0,
    val meanScore: Double = 0.0,
    val standardDeviation: Double = 0.0,
    val minutesWatched: Int = 0,
    val episodesWatched: Int = 0,
    val chaptersRead: Int = 0,
    val volumesRead: Int = 0,
    val categories: Map<String, List<UserStatEntry>> = emptyMap()
)

data class UserStatEntry(
    val id: String? = null,
    val label: String,
    val count: Int = 0,
    val meanScore: Double = 0.0,
    val minutesWatched: Int = 0,
    val chaptersRead: Int = 0
)

data class UserMediaListItem(
    val id: Long,
    val mediaId: Long,
    val title: String,
    val coverUrl: String,
    val status: String, // CURRENT, COMPLETED, PAUSED, DROPPED, PLANNING
    val score: Float = 0f,
    val progress: Int = 0,
    val totalProgress: Int? = null,
    val format: String? = null,
    val mediaType: MediaType = MediaType.ANIME
)

data class UserFavoriteMedia(
    val id: Long,
    val title: String,
    val coverUrl: String,
    val format: String? = null,
    val mediaType: MediaType = MediaType.ANIME
)

data class UserFavoriteCharacter(
    val id: Long,
    val name: String,
    val imageUrl: String? = null
)

data class UserFavoriteStaff(
    val id: Long,
    val name: String,
    val imageUrl: String? = null
)
