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
    val favoriteAnime: List<UserFavoriteMedia> = emptyList(),
    val favoriteManga: List<UserFavoriteMedia> = emptyList(),
    val favoriteCharacters: List<UserFavoriteCharacter> = emptyList(),
    val favoriteStaff: List<UserFavoriteStaff> = emptyList()
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
