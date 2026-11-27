package com.omnitrack.app.data.enrichment

import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.AnimeCharacterItem
import com.omnitrack.app.data.model.AnimeRelationItem
import com.omnitrack.app.data.model.AnimeStaffItem
import com.omnitrack.app.data.model.AnimeTagItem
import com.omnitrack.app.data.model.ExternalLinkItem
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.VnCharacterProfile
import com.omnitrack.app.data.model.VnDetailMeta
import com.omnitrack.app.data.model.VnReleaseItem
import com.omnitrack.app.data.model.VnScreenshotItem
import com.omnitrack.app.data.model.VnTraitItem
import com.omnitrack.app.data.model.formatVndbLengthTier
import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.ui.screens.detail.components.DiscussionComment
import com.omnitrack.app.ui.screens.detail.components.MediaTrailer
import com.omnitrack.app.ui.screens.detail.components.NetworkProductionCompany
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class VnFullEnrichmentResult(
    val resolvedVndbId: String?,
    val englishTitle: String? = null,
    val romajiTitle: String? = null,
    val nativeTitle: String? = null,
    val synonyms: List<String> = emptyList(),
    val synopsis: String? = null,
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val clearLogoUrl: String? = null,
    val originCountry: String? = null,
    val releaseYear: String? = null,
    val startDate: String? = null,
    val airStatus: String? = null,
    val contentRating: String? = null,
    val runtimeMinutes: Int? = null,
    val platformsSummary: String? = null,
    val studioOrNetwork: String? = null,
    val communityScore: Float? = null,
    val bangumiScore: Float? = null,
    val metacriticScore: Int? = null,
    val vnDetailMeta: VnDetailMeta = VnDetailMeta(),
    val characters: List<AnimeCharacterItem> = emptyList(),
    val staff: List<AnimeStaffItem> = emptyList(),
    val relations: List<AnimeRelationItem> = emptyList(),
    val companies: List<NetworkProductionCompany> = emptyList(),
    val tags: List<AnimeTagItem> = emptyList(),
    val trailers: List<MediaTrailer> = emptyList(),
    val externalLinks: List<ExternalLinkItem> = emptyList(),
    val discussionComments: List<DiscussionComment> = emptyList(),
    val resolvedBangumiId: Long? = null
)

class VisualNovelEnrichmentSource(
    private val client: OkHttpClient = NetworkClient.getClient()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    private fun JsonElement?.str(): String? {
        if (this == null || this is JsonNull) return null
        val s = try { jsonPrimitive.contentOrNull?.trim() } catch (_: Exception) { null }
        return if (s.isNullOrEmpty() || s.equals("null", ignoreCase = true)) null else s
    }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray

    private fun String.vndbNumericId(): Long =
        dropWhile { !it.isDigit() }.toLongOrNull() ?: hashCode().toLong()

    private fun mapOlangToCountry(olang: String?): String? = when (olang?.lowercase()) {
        "ja" -> "JP"
        "en" -> "EN"
        "zh", "zh-hans", "zh-hant" -> "CN"
        "ko" -> "KR"
        else -> olang?.uppercase()?.take(2)
    }

    private fun mapDevStatus(status: Int?): String? = when (status) {
        0 -> "Finished"
        1 -> "In Development"
        2 -> "Cancelled"
        else -> null
    }

    private fun formatBirthday(arr: JsonArray?): String? {
        if (arr == null || arr.size < 2) return null
        val month = (arr[0] as? JsonPrimitive)?.intOrNull ?: return null
        val day = (arr[1] as? JsonPrimitive)?.intOrNull ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        val names = arrayOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        return "${names[month]} $day"
    }

    private suspend fun postVndb(endpoint: String, payloadJson: String): JsonObject? =
        NetworkPacer.withVndbPacing {
            try {
                val request = Request.Builder()
                    .url("https://api.vndb.org/kana/$endpoint")
                    .post(payloadJson.toRequestBody(mediaTypeJson))
                    .header("User-Agent", "OmniTrack/1.0 (Android)")
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withVndbPacing null
                    val body = resp.body?.string() ?: return@withVndbPacing null
                    json.parseToJsonElement(body).obj()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.w("VNEnrich", "VNDB POST $endpoint failed: ${e.message}")
                null
            }
        }

    private suspend fun resolveVndbId(title: String): String? {
        val clean = title.replace("\"", "").trim()
        if (clean.isBlank()) return null
        val payload = buildJsonObject {
            put("filters", json.parseToJsonElement("[\"search\", \"=\", ${JsonPrimitive(clean)}]"))
            put("fields", "id,title")
            put("sort", "votecount")
            put("reverse", true)
            put("results", 1)
        }.toString()
        val root = postVndb("vn", payload) ?: return null
        return root["results"].arr()?.firstOrNull().obj()?.get("id").str()
    }

    suspend fun fetchFullVisualNovelData(
        item: MediaItemEntity,
        steamGridDbApiKey: String? = null,
        igdbClientId: String? = null,
        igdbClientSecret: String? = null
    ): VnFullEnrichmentResult? =
        withContext(Dispatchers.IO) {
            try {
                val vndbId = item.vndbId?.takeIf { it.isNotBlank() } ?: resolveVndbId(item.title)
                if (vndbId.isNullOrBlank()) return@withContext null

                val filterJson = "[\"id\", \"=\", \"$vndbId\"]"
                val relFilterJson = "[\"vn\", \"=\", [\"id\", \"=\", \"$vndbId\"]]"
                val steamRelFilterJson = "[\"and\", [\"vn\", \"=\", [\"id\", \"=\", \"$vndbId\"]], [\"extlink\", \"=\", \"steam\"]]"

                val vnFields = "id,title,alttitle,titles{lang,title,latin,official,main},aliases,olang,devstatus,released,languages,platforms,image{url,dims,sexual,violence},length,length_minutes,length_votes,description,average,rating,votecount,screenshots{url,thumbnail,dims,sexual,violence,release.title},relations{id,title,relation,relation_official,image.url,rating,released},tags{id,name,category,rating,spoiler,lie},developers{id,name,original},staff{eid,role,note,id,name,original},va{note,staff{id,name,original},character{id,name,original,image.url}},extlinks{url,label,name,id}"
                val charFields = "id,name,original,description,image{url,sexual,violence},sex,blood_type,height,weight,age,birthday,vns{id,role,spoiler},traits{id,name,group_name,spoiler}"
                val relFields = "id,title,released,languages{lang,mtl},platforms,producers{name,developer,publisher},official,patch,voiced,minage,engine,resolution,extlinks{name,id,url}"

                val (vnRoot, charRoot, relPair) = coroutineScope {
                    val vnDeferred = async {
                        postVndb("vn", buildJsonObject {
                            put("filters", json.parseToJsonElement(filterJson))
                            put("fields", vnFields)
                            put("results", 1)
                        }.toString())
                    }
                    val charDeferred = async {
                        postVndb("character", buildJsonObject {
                            put("filters", json.parseToJsonElement(relFilterJson))
                            put("fields", charFields)
                            put("results", 30)
                        }.toString())
                    }
                    val relDeferred = async {
                        postVndb("release", buildJsonObject {
                            put("filters", json.parseToJsonElement(relFilterJson))
                            put("fields", relFields)
                            put("results", 45)
                            put("sort", "released")
                        }.toString())
                    }
                    val steamRelDeferred = async {
                        postVndb("release", buildJsonObject {
                            put("filters", json.parseToJsonElement(steamRelFilterJson))
                            put("fields", relFields)
                            put("results", 15)
                            put("sort", "released")
                        }.toString())
                    }
                    Triple(vnDeferred.await(), charDeferred.await(), relDeferred.await() to steamRelDeferred.await())
                }
                val (relRoot, steamRelRoot) = relPair

                val vnObj = vnRoot?.get("results").arr()?.firstOrNull().obj() ?: return@withContext null

                val romajiTitle = vnObj["title"].str() ?: item.title
                val altTitle = vnObj["alttitle"].str()
                val titlesArray = vnObj["titles"].arr()
                val englishFromTitles = titlesArray?.firstOrNull { el ->
                    val o = el.obj() ?: return@firstOrNull false
                    o["lang"].str() == "en" && (o["official"]?.jsonPrimitive?.booleanOrNull == true || o["main"]?.jsonPrimitive?.booleanOrNull == true)
                }.obj()?.get("title").str()
                    ?: titlesArray?.firstOrNull { it.obj()?.get("lang").str() == "en" }.obj()?.get("title").str()
                val englishTitle = englishFromTitles ?: romajiTitle
                val nativeFromTitles = titlesArray?.firstOrNull { el ->
                    val o = el.obj() ?: return@firstOrNull false
                    o["main"]?.jsonPrimitive?.booleanOrNull == true || o["lang"].str() == vnObj["olang"].str()
                }.obj()?.get("title").str()
                val nativeTitle = altTitle ?: nativeFromTitles

                val synonyms = buildList {
                    vnObj["aliases"].arr()?.forEach { it.str()?.let(::add) }
                    titlesArray?.forEach { el ->
                        val o = el.obj() ?: return@forEach
                        o["title"].str()?.let(::add)
                        o["latin"].str()?.let(::add)
                    }
                }.distinct().filter {
                    !it.equals(englishTitle, ignoreCase = true) && !it.equals(romajiTitle, ignoreCase = true)
                }.take(10)

                val olang = vnObj["olang"].str()
                val originCountry = mapOlangToCountry(olang)
                val released = vnObj["released"].str()
                val releaseYear = released?.take(4)?.takeIf { y -> y.length == 4 && y.all { it.isDigit() } }
                val devStatus = mapDevStatus(vnObj["devstatus"]?.jsonPrimitive?.intOrNull)
                val lengthEnum = vnObj["length"]?.jsonPrimitive?.intOrNull
                val lengthMinutes = vnObj["length_minutes"]?.jsonPrimitive?.intOrNull
                val lengthVotes = vnObj["length_votes"]?.jsonPrimitive?.intOrNull
                val lengthTier = formatVndbLengthTier(lengthEnum, lengthMinutes)
                val ratingRaw = vnObj["rating"]?.jsonPrimitive?.floatOrNull
                    ?: vnObj["average"]?.jsonPrimitive?.floatOrNull
                val vndbScore = ratingRaw?.let { it / 10f }
                val vndbVotes = vnObj["votecount"]?.jsonPrimitive?.intOrNull
                val rawSynopsis = vnObj["description"].str()

                val imgObj = vnObj["image"].obj()
                val coverUrl = imgObj?.get("url").str()?.let { LosslessCoverMapper.toLosslessUrl(it) }
                val coverSexual = imgObj?.get("sexual")?.jsonPrimitive?.floatOrNull ?: 0f
                val coverViolence = imgObj?.get("violence")?.jsonPrimitive?.floatOrNull ?: 0f

                val screenshots = vnObj["screenshots"].arr()?.mapNotNull { el ->
                    val s = el.obj() ?: return@mapNotNull null
                    val url = s["url"].str() ?: return@mapNotNull null
                    val dims = s["dims"].arr()
                    VnScreenshotItem(
                        url = LosslessCoverMapper.toLosslessUrl(url),
                        thumbnailUrl = s["thumbnail"].str(),
                        width = dims?.getOrNull(0)?.jsonPrimitive?.intOrNull,
                        height = dims?.getOrNull(1)?.jsonPrimitive?.intOrNull,
                        sexual = s["sexual"]?.jsonPrimitive?.floatOrNull ?: 0f,
                        violence = s["violence"]?.jsonPrimitive?.floatOrNull ?: 0f,
                        releaseTitle = s["release"].obj()?.get("title").str()
                    )
                }.orEmpty()

                val combinedRelJson = relRoot?.get("results").arr().orEmpty() +
                    steamRelRoot?.get("results").arr().orEmpty()
                val releases = combinedRelJson.mapNotNull { el ->
                    val r = el.obj() ?: return@mapNotNull null
                    val rId = r["id"].str() ?: return@mapNotNull null
                    val rTitle = r["title"].str() ?: return@mapNotNull null
                    val langs = r["languages"].arr()?.mapNotNull { le ->
                        le.obj()?.get("lang").str()
                    }.orEmpty().distinct()
                    val plats = r["platforms"].arr()?.mapNotNull { it.str() }.orEmpty().distinct()
                    val prods = r["producers"].arr()?.mapNotNull { it.obj()?.get("name").str() }.orEmpty().distinct()
                    val relExtLinks = r["extlinks"].arr()
                    val steamExtId = relExtLinks?.firstOrNull { it.obj()?.get("name").str() == "steam" }
                        .obj()?.get("id").str()?.toLongOrNull()
                    val firstUrl = relExtLinks?.firstOrNull().obj()?.get("url").str()
                    VnReleaseItem(
                        id = rId,
                        title = rTitle,
                        released = r["released"].str(),
                        isOfficial = r["official"]?.jsonPrimitive?.booleanOrNull ?: true,
                        isPatch = r["patch"]?.jsonPrimitive?.booleanOrNull ?: false,
                        languages = langs,
                        platforms = plats,
                        producers = prods,
                        voicedLevel = r["voiced"]?.jsonPrimitive?.intOrNull,
                        minAge = r["minage"]?.jsonPrimitive?.intOrNull,
                        engine = r["engine"].str(),
                        resolution = r["resolution"].str(),
                        primaryUrl = firstUrl,
                        steamAppId = steamExtId
                    )
                }.distinctBy { it.id }

                // Summarize release metadata (voiced level, minAge, engine, platforms)
                val canonicalVoiced = releases.filter { it.isOfficial && !it.isPatch }.mapNotNull { it.voicedLevel }.maxOrNull()
                    ?: releases.mapNotNull { it.voicedLevel }.maxOrNull()
                val bestMinAge = releases.filter { it.isOfficial && (it.minAge ?: -1) > 0 }.mapNotNull { it.minAge }.minOrNull()
                    ?: releases.mapNotNull { it.minAge }.firstOrNull { it >= 0 }
                val bestEngine = releases.firstNotNullOfOrNull { it.engine?.takeIf(String::isNotBlank) }
                val allPlatforms = (
                    vnObj["platforms"].arr()?.mapNotNull { formatPlatformCode(it.str()) }.orEmpty() +
                        releases.flatMap { r -> r.platforms.mapNotNull(::formatPlatformCode) }
                    ).distinct().take(6).joinToString(", ").ifBlank { null }

                val contentRating = bestMinAge?.let { age -> if (age == 0) "All Ages" else "${age}+" }

                // Developers -> Companies
                val companies = vnObj["developers"].arr()?.mapNotNull { el ->
                    val d = el.obj() ?: return@mapNotNull null
                    val dId = d["id"].str() ?: return@mapNotNull null
                    val dName = d["name"].str() ?: return@mapNotNull null
                    NetworkProductionCompany(
                        id = dId.vndbNumericId(),
                        name = dName,
                        logoUrl = null,
                        kind = "company"
                    )
                }.orEmpty()
                val studioOrNetwork = companies.firstOrNull()?.name

                // Staff
                val seenStaff = HashSet<String>()
                val staff = vnObj["staff"].arr()?.mapNotNull { el ->
                    val s = el.obj() ?: return@mapNotNull null
                    val sId = s["id"].str() ?: return@mapNotNull null
                    if (!seenStaff.add(sId)) return@mapNotNull null
                    val sName = s["name"].str() ?: return@mapNotNull null
                    val rawRole = s["role"].str() ?: "Staff"
                    val niceRole = formatVnStaffRole(rawRole)
                    AnimeStaffItem(
                        staffId = sId.vndbNumericId(),
                        name = sName,
                        nativeName = s["original"].str(),
                        role = niceRole,
                        imageUrl = null
                    )
                }.orEmpty().take(24)

                // Voice Actors per character from VN va[]
                val vaMap = HashMap<String, Pair<String, Long?>>()
                vnObj["va"].arr()?.forEach { el ->
                    val vo = el.obj() ?: return@forEach
                    val charId = vo["character"].obj()?.get("id").str() ?: return@forEach
                    val st = vo["staff"].obj() ?: return@forEach
                    val stName = st["name"].str() ?: return@forEach
                    val stId = st["id"].str()?.vndbNumericId()
                    if (!vaMap.containsKey(charId)) {
                        vaMap[charId] = stName to stId
                    }
                }

                // Characters & Deep Profiles
                val charProfiles = LinkedHashMap<String, VnCharacterProfile>()
                val characterItems = charRoot?.get("results").arr()?.mapNotNull { el ->
                    val c = el.obj() ?: return@mapNotNull null
                    val cId = c["id"].str() ?: return@mapNotNull null
                    val cName = c["name"].str() ?: return@mapNotNull null
                    val cImg = c["image"].obj()
                    val cUrl = cImg?.get("url").str()?.let { LosslessCoverMapper.toLosslessUrl(it) }
                    val vnRoleEntry = c["vns"].arr()?.firstOrNull {
                        it.obj()?.get("id").str() == vndbId
                    }.obj() ?: c["vns"].arr()?.firstOrNull().obj()
                    val rawRole = vnRoleEntry?.get("role").str() ?: "main"
                    val charSpoiler = vnRoleEntry?.get("spoiler")?.jsonPrimitive?.intOrNull ?: 0
                    val mappedRole = when (rawRole) {
                        "main" -> "PROTAGONIST"
                        "primary" -> "MAIN"
                        else -> "SUPPORT"
                    }
                    val vaInfo = vaMap[cId]
                    val traits = c["traits"].arr()?.mapNotNull { te ->
                        val t = te.obj() ?: return@mapNotNull null
                        val tId = t["id"].str() ?: return@mapNotNull null
                        val tName = t["name"].str() ?: return@mapNotNull null
                        VnTraitItem(
                            id = tId,
                            name = tName,
                            groupName = t["group_name"].str(),
                            spoilerLevel = t["spoiler"]?.jsonPrimitive?.intOrNull ?: 0
                        )
                    }.orEmpty()

                    charProfiles[cId] = VnCharacterProfile(
                        vndbCharId = cId,
                        name = cName,
                        nativeName = c["original"].str(),
                        imageUrl = cUrl,
                        sexual = cImg?.get("sexual")?.jsonPrimitive?.floatOrNull ?: 0f,
                        violence = cImg?.get("violence")?.jsonPrimitive?.floatOrNull ?: 0f,
                        role = rawRole,
                        spoilerLevel = charSpoiler,
                        description = c["description"].str(),
                        sex = c["sex"].arr()?.firstOrNull()?.str() ?: c["sex"].str(),
                        age = c["age"]?.jsonPrimitive?.intOrNull,
                        birthday = formatBirthday(c["birthday"].arr()),
                        bloodType = c["blood_type"].str()?.uppercase(),
                        heightCm = c["height"]?.jsonPrimitive?.intOrNull,
                        weightKg = c["weight"]?.jsonPrimitive?.intOrNull,
                        voiceActorName = vaInfo?.first,
                        voiceActorId = vaInfo?.second,
                        traits = traits
                    )

                    AnimeCharacterItem(
                        characterId = cId.vndbNumericId(),
                        characterName = cName,
                        characterRole = mappedRole,
                        characterImageUrl = cUrl,
                        voiceActorId = vaInfo?.second,
                        voiceActorName = vaInfo?.first,
                        voiceActorLanguage = if (olang == "en") "EN" else "JP",
                        vndbCharacterId = cId,
                        spoilerLevel = charSpoiler
                    )
                }.orEmpty().sortedBy {
                    when (it.characterRole) {
                        "MAIN" -> 0
                        "PROTAGONIST" -> 1
                        else -> 2
                    }
                }

                // VNDB Relations
                val vndbRelations = vnObj["relations"].arr()?.mapNotNull { el ->
                    val r = el.obj() ?: return@mapNotNull null
                    val rId = r["id"].str() ?: return@mapNotNull null
                    val rTitle = r["title"].str() ?: return@mapNotNull null
                    val rawRel = r["relation"].str() ?: "other"
                    val mappedRel = when (rawRel) {
                        "seq" -> "SEQUEL"
                        "preq" -> "PREQUEL"
                        "fan" -> "FANDISC"
                        "side" -> "SIDE_STORY"
                        "alt" -> "ALTERNATIVE"
                        "set" -> "SAME_SETTING"
                        "ser" -> "SAME_SERIES"
                        else -> rawRel.uppercase()
                    }
                    AnimeRelationItem(
                        id = rId.vndbNumericId(),
                        title = rTitle,
                        relationType = mappedRel,
                        format = "VN",
                        coverUrl = r["image"].obj()?.get("url").str()?.let { LosslessCoverMapper.toLosslessUrl(it) },
                        year = r["released"].str()?.take(4)?.toIntOrNull(),
                        mediaType = MediaType.VISUAL_NOVEL,
                        vndbId = rId
                    )
                }.orEmpty()

                // Categorized VNDB Tags
                val tags = vnObj["tags"].arr()?.mapNotNull { el ->
                    val t = el.obj() ?: return@mapNotNull null
                    if (t["lie"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
                    val tId = t["id"].str() ?: return@mapNotNull null
                    val tName = t["name"].str() ?: return@mapNotNull null
                    val score = t["rating"]?.jsonPrimitive?.floatOrNull ?: 0f
                    if (score < 0.5f) return@mapNotNull null
                    AnimeTagItem(
                        id = tId.vndbNumericId(),
                        name = tName,
                        rank = ((score / 3.0f) * 100f).roundToInt().coerceIn(1, 100),
                        isMediaSpoiler = (t["spoiler"]?.jsonPrimitive?.intOrNull ?: 0) > 0,
                        category = t["category"].str() ?: "cont",
                        spoilerLevel = t["spoiler"]?.jsonPrimitive?.intOrNull ?: 0,
                        rating = score
                    )
                }.orEmpty().sortedByDescending { it.rating ?: 0f }.take(45)

                // External Links (VNDB + VN extlinks + HLTB + official store links)
                val extLinks = buildList {
                    add(ExternalLinkItem(id = 0L, url = "https://vndb.org/$vndbId", site = "VNDB", type = "INFO"))
                    vnObj["extlinks"].arr()?.forEachIndexed { idx, el ->
                        val xo = el.obj() ?: return@forEachIndexed
                        val url = xo["url"].str() ?: return@forEachIndexed
                        val label = xo["label"].str() ?: xo["name"].str() ?: return@forEachIndexed
                        add(ExternalLinkItem(id = (idx + 1).toLong(), url = url, site = label, type = "INFO"))
                    }
                }.distinctBy { it.url }.take(14)

                // Parallel Secondary Enrichment: Steam Bridge + Bangumi + AniList Adaptations + IGDB + SteamGridDB + r/visualnovels
                val selectedSteamAppId = selectBestReleasedSteamAppId(releases)
                val igdbSlug = vnObj["extlinks"].arr()?.firstOrNull {
                    it.obj()?.get("name").str() == "igdb_game"
                }.obj()?.get("id").str()

                coroutineScope {
                    val steamDeferred = async {
                        selectedSteamAppId?.let { fetchSteamBridge(it) }
                    }
                    val bgmDeferred = async {
                        val directBgmId = vnObj["extlinks"].arr()?.firstOrNull {
                            it.obj()?.get("name").str()?.contains("bangumi", ignoreCase = true) == true
                        }.obj()?.get("id").str()?.toLongOrNull()
                        fetchBangumiScoreForVn(englishTitle, romajiTitle, nativeTitle, directBgmId)
                    }
                    val aniDeferred = async {
                        fetchAniListAdaptationBridge(romajiTitle, englishTitle)
                    }
                    val igdbDeferred = async {
                        if (!igdbClientId.isNullOrBlank() && !igdbClientSecret.isNullOrBlank()) {
                            IgdbApiClient(client).fetchGameArtwork(igdbClientId, igdbClientSecret, englishTitle, igdbSlug)
                        } else null
                    }
                    val redditDeferred = async {
                        fetchVisualNovelRedditDiscussions(englishTitle, romajiTitle)
                    }

                    val steamData = steamDeferred.await()
                    val bgmData = bgmDeferred.await()
                    val aniData = aniDeferred.await()
                    val igdbData = igdbDeferred.await()
                    val redditComments = redditDeferred.await()

                    val needsSgdbHero = steamData?.libraryHeroUrl == null && igdbData?.artworks.isNullOrEmpty()
                    val needsSgdbLogo = steamData?.logoUrl == null
                    val sgdbPair = if (!steamGridDbApiKey.isNullOrBlank() && (needsSgdbHero || needsSgdbLogo)) {
                        fetchSteamGridDbHeroAndLogo(selectedSteamAppId, englishTitle, steamGridDbApiKey)
                    } else null

                    val aniZipLogo = if (steamData?.logoUrl == null && sgdbPair?.second == null && aniData?.aniListId != null) {
                        fetchAniZipLogoUrl(aniData.aniListId)
                    } else null

                    val existingNonShotBanner = item.bannerUrl?.takeIf {
                        it.isNotBlank() && !com.omnitrack.app.data.artwork.ArtworkModelResolver.isAutoScreenshotUrl(it)
                    }
                    val finalBanner = steamData?.libraryHeroUrl
                        ?: sgdbPair?.first
                        ?: igdbData?.artworks?.firstOrNull()
                        ?: existingNonShotBanner
                    val finalClearLogo = steamData?.logoUrl ?: sgdbPair?.second ?: aniZipLogo
                    val combinedRelations = (vndbRelations + aniData?.relations.orEmpty())
                        .distinctBy { it.vndbId ?: "${it.mediaType}_${it.title.lowercase()}" }

                    val finalLinks = buildList {
                        addAll(extLinks)
                        if (selectedSteamAppId != null) {
                            add(
                                ExternalLinkItem(
                                    id = 9001L,
                                    url = "https://store.steampowered.com/app/$selectedSteamAppId/",
                                    site = "Steam",
                                    type = "STORE"
                                )
                            )
                        }
                    }.distinctBy { it.url }

                    val meta = VnDetailMeta(
                        vndbScore = vndbScore,
                        vndbVotes = vndbVotes,
                        lengthMinutes = lengthMinutes,
                        lengthVotes = lengthVotes,
                        lengthTier = lengthTier,
                        devStatus = devStatus,
                        steamAppId = selectedSteamAppId,
                        steamPositivePct = steamData?.positivePct,
                        steamReviewDesc = steamData?.reviewDesc,
                        voicedLevel = canonicalVoiced,
                        minAge = bestMinAge,
                        engine = bestEngine,
                        screenshots = screenshots,
                        releases = releases,
                        characterProfiles = charProfiles,
                        coverSexual = coverSexual,
                        coverViolence = coverViolence
                    )

                    VnFullEnrichmentResult(
                        resolvedVndbId = vndbId,
                        englishTitle = englishTitle,
                        romajiTitle = romajiTitle,
                        nativeTitle = nativeTitle,
                        synonyms = synonyms,
                        synopsis = rawSynopsis,
                        coverUrl = coverUrl,
                        bannerUrl = finalBanner,
                        clearLogoUrl = finalClearLogo,
                        originCountry = originCountry,
                        releaseYear = releaseYear,
                        startDate = released,
                        airStatus = devStatus,
                        contentRating = contentRating,
                        runtimeMinutes = lengthMinutes,
                        platformsSummary = allPlatforms,
                        studioOrNetwork = studioOrNetwork,
                        communityScore = vndbScore,
                        bangumiScore = bgmData?.first,
                        metacriticScore = steamData?.metacriticScore,
                        vnDetailMeta = meta,
                        characters = characterItems,
                        staff = staff,
                        relations = combinedRelations,
                        companies = companies,
                        tags = tags,
                        trailers = aniData?.trailers.orEmpty(),
                        externalLinks = finalLinks,
                        discussionComments = redditComments,
                        resolvedBangumiId = bgmData?.second
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e("VNEnrich", "Error enriching VN ${item.title}: ${e.message}")
                null
            }
        }

    /**
     * Selects the canonical released official Steam AppID from VNDB releases.
     * Filters out TBA / future / reboot announcements so e.g. STEINS;GATE RE:BOOT (4012810)
     * never overrides released edition STEINS;GATE (412830).
     */
    private fun selectBestReleasedSteamAppId(releases: List<VnReleaseItem>): Long? {
        val steamReleases = releases.filter { it.steamAppId != null }
        if (steamReleases.isEmpty()) return null
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val releasedOfficial = steamReleases.filter { rel ->
            val d = rel.released?.trim()
            rel.isOfficial &&
                !rel.isPatch &&
                !d.isNullOrBlank() &&
                !d.equals("TBA", ignoreCase = true) &&
                d <= todayIso
        }.sortedBy { it.released ?: "9999-99-99" }

        return releasedOfficial.firstOrNull()?.steamAppId
            ?: steamReleases.firstOrNull { !it.title.contains("reboot", ignoreCase = true) }?.steamAppId
            ?: steamReleases.firstOrNull()?.steamAppId
    }

    private data class SteamBridgeData(
        val logoUrl: String? = null,
        val libraryHeroUrl: String? = null,
        val metacriticScore: Int? = null,
        val positivePct: Int? = null,
        val reviewDesc: String? = null
    )

    private suspend fun checkUrlOk(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).head().build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
    }

    private suspend fun fetchSteamBridge(appId: Long): SteamBridgeData? = NetworkPacer.withSteamPacing {
        try {
            val logoCandidate = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/logo.png"
            val heroCandidate = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/library_hero.jpg"
            val headerFallback = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg"

            var metacritic: Int? = null
            var positivePct: Int? = null
            var reviewDesc: String? = null
            var steamBgRaw: String? = null

            val logoOk = checkUrlOk(logoCandidate)
            val heroOk = checkUrlOk(heroCandidate)
            val headerOk = if (!heroOk) checkUrlOk(headerFallback) else false

            val detailsReq = Request.Builder()
                .url("https://store.steampowered.com/api/appdetails?appids=$appId")
                .get()
                .build()
            client.newCall(detailsReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val root = json.parseToJsonElement(body).obj()
                        val appNode = root?.get(appId.toString()).obj()
                        if (appNode?.get("success")?.jsonPrimitive?.booleanOrNull == true) {
                            val dataObj = appNode["data"].obj()
                            metacritic = dataObj?.get("metacritic").obj()
                                ?.get("score")?.jsonPrimitive?.intOrNull
                            steamBgRaw = dataObj?.get("background_raw").str()
                                ?: dataObj?.get("background").str()
                        }
                    }
                }
            }

            val revReq = Request.Builder()
                .url("https://store.steampowered.com/appreviews/$appId?json=1&language=all&purchase_type=all&num_per_page=0")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36")
                .get()
                .build()
            client.newCall(revReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val summary = json.parseToJsonElement(body).obj()?.get("query_summary").obj()
                        val pos = summary?.get("total_positive")?.jsonPrimitive?.intOrNull ?: 0
                        val total = summary?.get("total_reviews")?.jsonPrimitive?.intOrNull ?: 0
                        reviewDesc = summary?.get("review_score_desc").str()
                        if (total > 0) {
                            positivePct = ((pos * 100.0) / total).roundToInt().coerceIn(0, 100)
                        }
                    }
                }
            }

            SteamBridgeData(
                logoUrl = if (logoOk) logoCandidate else null,
                libraryHeroUrl = when {
                    heroOk -> heroCandidate
                    !steamBgRaw.isNullOrBlank() -> steamBgRaw
                    headerOk -> headerFallback
                    else -> null
                },
                metacriticScore = metacritic,
                positivePct = positivePct,
                reviewDesc = reviewDesc
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun fetchSteamGridDbHeroAndLogo(
        steamAppId: Long?,
        englishTitle: String,
        apiKey: String
    ): Pair<String?, String?>? = withContext(Dispatchers.IO) {
        try {
            val cleanKey = apiKey.trim()
            if (cleanKey.isBlank()) return@withContext null

            var sgdbGameId: Long? = null
            if (steamAppId != null && steamAppId > 0L) {
                val gameReq = Request.Builder()
                    .url("https://www.steamgriddb.com/api/v2/games/steam/$steamAppId")
                    .header("Authorization", "Bearer $cleanKey")
                    .get()
                    .build()
                client.newCall(gameReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val obj = json.parseToJsonElement(body).obj()
                        if (obj?.get("success")?.jsonPrimitive?.booleanOrNull == true) {
                            sgdbGameId = obj["data"].obj()?.get("id")?.jsonPrimitive?.longOrNull
                        }
                    }
                }
            }
            if (sgdbGameId == null && englishTitle.isNotBlank()) {
                val encoded = URLEncoder.encode(englishTitle.trim(), "UTF-8")
                val searchReq = Request.Builder()
                    .url("https://www.steamgriddb.com/api/v2/search/autocomplete/$encoded")
                    .header("Authorization", "Bearer $cleanKey")
                    .get()
                    .build()
                client.newCall(searchReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val obj = json.parseToJsonElement(body).obj()
                        if (obj?.get("success")?.jsonPrimitive?.booleanOrNull == true) {
                            sgdbGameId = obj["data"].arr()?.firstOrNull().obj()?.get("id")?.jsonPrimitive?.longOrNull
                        }
                    }
                }
            }
            val targetId = sgdbGameId ?: return@withContext null
            val hideNsfw = com.omnitrack.app.data.preferences.PreferenceManager.cachedVnNsfwFilterMode ==
                com.omnitrack.app.data.model.VnNsfwFilterMode.SAFE_HIDE

            fun fetchBestAsset(endpoint: String): String? {
                val assetReq = Request.Builder()
                    .url("https://www.steamgriddb.com/api/v2/$endpoint/game/$targetId")
                    .header("Authorization", "Bearer $cleanKey")
                    .get()
                    .build()
                client.newCall(assetReq).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val body = resp.body?.string() ?: return null
                    val obj = json.parseToJsonElement(body).obj() ?: return null
                    if (obj["success"]?.jsonPrimitive?.booleanOrNull != true) return null
                    val list = obj["data"].arr() ?: return null
                    val valid = list.mapNotNull { it.obj() }.filter { item ->
                        !hideNsfw || (item["nsfw"]?.jsonPrimitive?.booleanOrNull != true)
                    }
                    return valid.firstOrNull { it["humor"]?.jsonPrimitive?.booleanOrNull != true }?.get("url").str()
                        ?: valid.firstOrNull()?.get("url").str()
                }
            }

            val heroUrl = fetchBestAsset("heroes")
            val logoUrl = fetchBestAsset("logos")
            heroUrl to logoUrl
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun fetchBangumiScoreForVn(
        englishTitle: String,
        romajiTitle: String,
        nativeTitle: String?,
        directBangumiId: Long?
    ): Pair<Float, Long>? = NetworkPacer.withBangumiPacing {
        try {
            if (directBangumiId != null && directBangumiId > 0L) {
                val req = Request.Builder()
                    .url("https://api.bgm.tv/v0/subjects/$directBangumiId")
                    .header("User-Agent", "OmniTrack/1.0")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val score = json.parseToJsonElement(body).obj()?.get("rating").obj()?.get("score")?.jsonPrimitive?.floatOrNull
                            if (score != null && score > 0f) return@withBangumiPacing score to directBangumiId
                        }
                    }
                }
            }

            val query = nativeTitle?.takeIf { it.isNotBlank() } ?: romajiTitle.ifBlank { englishTitle }
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchReq = Request.Builder()
                .url("https://api.bgm.tv/search/subject/$encoded?type=4&responseGroup=medium&max_results=5")
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            client.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withBangumiPacing null
                val body = resp.body?.string() ?: return@withBangumiPacing null
                val list = json.parseToJsonElement(body).obj()?.get("list").arr() ?: return@withBangumiPacing null
                for (el in list) {
                    val obj = el.obj() ?: continue
                    val bId = obj["id"]?.jsonPrimitive?.longOrNull ?: continue
                    val score = obj["rating"].obj()?.get("score")?.jsonPrimitive?.floatOrNull
                    if (score != null && score > 0f) return@withBangumiPacing score to bId
                }
            }
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private data class AniBridgeResult(
        val aniListId: Long? = null,
        val relations: List<AnimeRelationItem> = emptyList(),
        val trailers: List<MediaTrailer> = emptyList()
    )

    private suspend fun fetchAniListAdaptationBridge(
        romajiTitle: String,
        englishTitle: String
    ): AniBridgeResult? = NetworkPacer.withAniListPacing {
        try {
            val cleanQuery = romajiTitle.ifBlank { englishTitle }.trim()
            if (cleanQuery.isBlank()) return@withAniListPacing null
            val gql = """
                query (${"$"}search: String) {
                  Page(perPage: 5) {
                    media(search: ${"$"}search, type: ANIME, sort: POPULARITY_DESC) {
                      id
                      source
                      format
                      seasonYear
                      title { romaji english }
                      coverImage { extraLarge large }
                      bannerImage
                      trailer { id site }
                    }
                  }
                }
            """.trimIndent()
            val payload = buildJsonObject {
                put("query", gql)
                put("variables", buildJsonObject { put("search", cleanQuery) })
            }.toString()
            val req = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(payload.toRequestBody(mediaTypeJson))
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withAniListPacing null
                val body = resp.body?.string() ?: return@withAniListPacing null
                val list = json.parseToJsonElement(body).obj()?.get("data").obj()
                    ?.get("Page").obj()?.get("media").arr() ?: return@withAniListPacing null

                val targetNorm = cleanQuery.lowercase().replace(Regex("[^a-z0-9]"), "")
                val matched = list.mapNotNull { el ->
                    val m = el.obj() ?: return@mapNotNull null
                    val tObj = m["title"].obj()
                    val rTitle = tObj?.get("romaji").str().orEmpty()
                    val eTitle = tObj?.get("english").str().orEmpty()
                    val src = m["source"].str().orEmpty()
                    val rNorm = rTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
                    val eNorm = eTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
                    val exactOrPrefix = (rNorm.isNotEmpty() && (rNorm == targetNorm || rNorm.startsWith(targetNorm))) ||
                        (eNorm.isNotEmpty() && (eNorm == targetNorm || eNorm.startsWith(targetNorm)))
                    if (exactOrPrefix || src.equals("VISUAL_NOVEL", ignoreCase = true)) m else null
                }
                if (matched.isEmpty()) return@withAniListPacing null

                val relations = matched.take(4).mapNotNull { m ->
                    val id = m["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    val tObj = m["title"].obj()
                    val title = tObj?.get("english").str() ?: tObj?.get("romaji").str() ?: return@mapNotNull null
                    val img = m["coverImage"].obj()?.get("extraLarge").str()
                        ?: m["coverImage"].obj()?.get("large").str()
                    AnimeRelationItem(
                        id = id,
                        title = title,
                        relationType = "ADAPTATION",
                        format = m["format"].str() ?: "TV",
                        coverUrl = img,
                        bannerUrl = m["bannerImage"].str(),
                        year = m["seasonYear"]?.jsonPrimitive?.intOrNull,
                        mediaType = MediaType.ANIME
                    )
                }

                val trailers = matched.mapNotNull { m ->
                    val tr = m["trailer"].obj() ?: return@mapNotNull null
                    val site = tr["site"].str() ?: return@mapNotNull null
                    val ytId = tr["id"].str() ?: return@mapNotNull null
                    if (site.equals("youtube", ignoreCase = true)) {
                        val tObj = m["title"].obj()
                        val name = tObj?.get("english").str() ?: tObj?.get("romaji").str() ?: cleanQuery
                        MediaTrailer(title = "$name (Anime Trailer)", youtubeKey = ytId, type = "Trailer")
                    } else null
                }

                AniBridgeResult(
                    aniListId = matched.firstOrNull()?.get("id")?.jsonPrimitive?.longOrNull,
                    relations = relations,
                    trailers = trailers
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun fetchAniZipLogoUrl(aniListId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.ani.zip/mappings?anilist_id=$aniListId").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val images = json.parseToJsonElement(body).obj()?.get("images").arr() ?: return@withContext null
                images.firstOrNull {
                    it.obj()?.get("coverType").str().equals("clearlogo", ignoreCase = true)
                }.obj()?.get("url").str()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun fetchVisualNovelRedditDiscussions(
        englishTitle: String,
        romajiTitle: String
    ): List<DiscussionComment> = NetworkPacer.withGeneralPacing {
        try {
            val q = englishTitle.ifBlank { romajiTitle }.trim()
            if (q.isBlank()) return@withGeneralPacing emptyList()
            val encoded = URLEncoder.encode(q, "UTF-8")
            val req = Request.Builder()
                .url("https://www.reddit.com/r/visualnovels/search.json?q=$encoded&restrict_sr=1&sort=top&limit=8")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                val body = resp.body?.string() ?: return@withGeneralPacing emptyList()
                val children = json.parseToJsonElement(body).obj()?.get("data").obj()
                    ?.get("children").arr() ?: return@withGeneralPacing emptyList()
                children.mapNotNull { el ->
                    val d = el.obj()?.get("data").obj() ?: return@mapNotNull null
                    val id = d["id"].str() ?: return@mapNotNull null
                    val title = d["title"].str() ?: return@mapNotNull null
                    val self = d["selftext"].str()?.take(400).orEmpty()
                    val combined = if (self.isNotBlank()) "$title\n\n$self" else title
                    val author = d["author"].str() ?: "redditor"
                    val score = d["score"]?.jsonPrimitive?.intOrNull ?: 0
                    val flair = d["link_flair_text"].str() ?: "r/visualnovels"
                    DiscussionComment(
                        id = "reddit_$id",
                        author = "u/$author",
                        tag = flair,
                        content = combined,
                        timeAgo = "Reddit",
                        upvotes = score
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private fun formatVnStaffRole(raw: String): String = when (raw.lowercase()) {
        "scenario" -> "Scenario"
        "chardesign" -> "Character Design"
        "art" -> "Art"
        "music" -> "Music"
        "director" -> "Director"
        "songs" -> "Vocals"
        "translator" -> "Translator"
        "editor" -> "Editor"
        "qa" -> "QA"
        "staff" -> "Staff"
        else -> raw.replaceFirstChar { it.uppercase() }
    }

    private fun formatPlatformCode(code: String?): String? = when (code?.lowercase()) {
        "win" -> "PC (Windows)"
        "lin" -> "Linux"
        "mac" -> "macOS"
        "swi" -> "Nintendo Switch"
        "psv" -> "PS Vita"
        "psp" -> "PSP"
        "ps3" -> "PS3"
        "ps4" -> "PS4"
        "ps5" -> "PS5"
        "and" -> "Android"
        "ios" -> "iOS"
        "web" -> "Browser"
        "xb3" -> "Xbox 360"
        "xbo" -> "Xbox One"
        "n3d" -> "3DS"
        "nds" -> "DS"
        null -> null
        else -> code.uppercase()
    }
}
