package com.omnitrack.app.data.model

data class AnimeCharacterItem(
    val characterId: Long,
    val characterName: String,
    val characterRole: String, // "MAIN", "SUPPORT"
    val characterImageUrl: String?,
    val voiceActorId: Long? = null,
    val voiceActorName: String? = null,
    val voiceActorImageUrl: String? = null,
    val voiceActorLanguage: String = "JP"
)

data class AnimeRelationItem(
    val id: Long,
    val title: String,
    val relationType: String, // "SEQUEL", "PREQUEL", "SIDE_STORY", "SPIN_OFF", "MANGA", "NOVEL", "ALTERNATIVE", "OTHER"
    val format: String, // "TV", "MOVIE", "MANGA", "NOVEL", "ONA", "OVA", "SPECIAL"
    val coverUrl: String?,
    val year: Int? = null,
    val mediaType: MediaType = MediaType.ANIME
)

data class AnimeAiringSchedule(
    val episodeNumber: Int,
    val airingAtEpochSeconds: Long,
    val broadcastNetwork: String? = null
)

data class VoiceActorRoleItem(
    val characterId: Long,
    val characterName: String,
    val characterImageUrl: String?,
    val characterRole: String, // "MAIN", "SUPPORT"
    val mediaId: Long,
    val mediaTitle: String,
    val mediaCoverUrl: String?,
    val mediaFormat: String?,
    val mediaYear: Int?,
    val mediaType: MediaType = MediaType.ANIME
)

data class AnimeVoiceActorDetail(
    val staffId: Long,
    val name: String,
    val nativeName: String?,
    val imageUrl: String?,
    val biography: String?,
    val age: Int?,
    val dateOfBirth: String?,
    val homeTown: String?,
    val primaryOccupations: List<String> = emptyList(),
    val roles: List<VoiceActorRoleItem> = emptyList()
)

data class AnimeEnrichedDetails(
    val anilistId: Long?,
    val malId: Long?,
    val characters: List<AnimeCharacterItem> = emptyList(),
    val relations: List<AnimeRelationItem> = emptyList(),
    val nextAiring: AnimeAiringSchedule? = null,
    val animationStudios: List<String> = emptyList(),
    val synopsis: String? = null,
    val bannerUrl: String? = null,
    val coverUrl: String? = null,
    val averageScore: Float? = null,
    val totalEpisodes: Int? = null,
    val totalChapters: Int? = null,
    val genres: List<String> = emptyList()
)
