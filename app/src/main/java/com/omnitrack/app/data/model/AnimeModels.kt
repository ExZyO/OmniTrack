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

data class AnimeStaffItem(
    val staffId: Long,
    val name: String,
    val nativeName: String? = null,
    val role: String, // e.g. "Director", "Original Creator", "Music"
    val imageUrl: String? = null
)

data class StaffProductionCreditItem(
    val mediaId: Long,
    val mediaTitle: String,
    val mediaCoverUrl: String?,
    val mediaFormat: String?,
    val mediaYear: Int?,
    val staffRole: String, // e.g. "Director", "Music", "Original Creator"
    val mediaType: MediaType = MediaType.ANIME
)

data class AnimeVoiceActorDetail(
    val staffId: Long,
    val name: String,
    val nativeName: String?,
    val alternativeNames: List<String> = emptyList(),
    val imageUrl: String?,
    val biography: String?,
    val age: Int?,
    val dateOfBirth: String?,
    val dateOfDeath: String? = null,
    val homeTown: String?,
    val primaryOccupations: List<String> = emptyList(),
    val roles: List<VoiceActorRoleItem> = emptyList(),
    val gender: String? = null,
    val yearsActive: String? = null,
    val bloodType: String? = null,
    val language: String? = null,
    val favourites: Int? = null,
    val productionCredits: List<StaffProductionCreditItem> = emptyList()
)

data class AnimeCharacterVoiceActor(
    val staffId: Long,
    val name: String,
    val nativeName: String? = null,
    val imageUrl: String? = null,
    val language: String = "Japanese"
)

data class AnimeCharacterAppearance(
    val id: Long,
    val malId: Long? = null,
    val title: String,
    val coverUrl: String,
    val bannerUrl: String? = null,
    val mediaType: MediaType = MediaType.ANIME,
    val format: String = "TV",
    val score: Float? = null,
    val releaseYear: String? = null,
    val characterRole: String = "MAIN"
)

data class AnimeCharacterDetail(
    val characterId: Long,
    val name: String,
    val nativeName: String? = null,
    val alternativeNames: List<String> = emptyList(),
    val imageUrl: String? = null,
    val biography: String? = null,
    val gender: String? = null,
    val age: String? = null,
    val dateOfBirth: String? = null,
    val bloodType: String? = null,
    val favourites: Int? = null,
    val appearances: List<AnimeCharacterAppearance> = emptyList(),
    val voiceActors: List<AnimeCharacterVoiceActor> = emptyList()
)

data class AnimeTagItem(
    val id: Long,
    val name: String,
    val rank: Int? = null,
    val isMediaSpoiler: Boolean = false,
    val isGeneralSpoiler: Boolean = false
)

data class AnimeEnrichedDetails(
    val anilistId: Long?,
    val malId: Long?,
    val englishTitle: String? = null,
    val romajiTitle: String? = null,
    val nativeTitle: String? = null,
    val synonyms: List<String> = emptyList(),
    val characters: List<AnimeCharacterItem> = emptyList(),
    val staff: List<AnimeStaffItem> = emptyList(),
    val relations: List<AnimeRelationItem> = emptyList(),
    val nextAiring: AnimeAiringSchedule? = null,
    val animationStudios: List<String> = emptyList(),
    val synopsis: String? = null,
    val bannerUrl: String? = null,
    val coverUrl: String? = null,
    val averageScore: Float? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val durationMinutes: Int? = null,
    val totalEpisodes: Int? = null,
    val totalChapters: Int? = null,
    val genres: List<String> = emptyList(),
    val tags: List<AnimeTagItem> = emptyList()
)
