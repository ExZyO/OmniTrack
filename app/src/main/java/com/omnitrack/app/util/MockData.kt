package com.omnitrack.app.util

import com.omnitrack.app.data.db.entity.CategoryEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus

object MockData {

    val categories = listOf(
        CategoryEntity(name = "Favorites", orderIndex = 0),
        CategoryEntity(name = "Seasonal Picks", orderIndex = 1),
        CategoryEntity(name = "Cultivation", orderIndex = 2),
        CategoryEntity(name = "JRPGs", orderIndex = 3),
    )

    val sampleMedia = listOf(
        // ── Anime ──
        MediaItemEntity(
            title = "Solo Leveling",
            nativeTitle = "俺だけレベルアップな件",
            synopsis = "In a world where hunters must battle deadly monsters, Sung Jinwoo finds himself as the weakest of all the hunters.",
            coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx151807-m1gX3iwfIsLu.png",
            bannerUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/151807-37SXRV0Wcsiq.jpg",
            mediaType = MediaType.ANIME,
            status = TrackingStatus.IN_PROGRESS,
            currentProgress = 8.0,
            totalProgress = 12.0,
            userRating = 9.0f,
            anilistId = 151807
        ),
        MediaItemEntity(
            title = "Frieren: Beyond Journey's End",
            nativeTitle = "葬送のフリーレン",
            synopsis = "After the party of heroes defeated the Demon King, the elf mage Frieren begins a new journey.",
            coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx154587-n1fmjRv4JQUd.jpg",
            bannerUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/154587-ivXNJ23SM1xB.jpg",
            mediaType = MediaType.ANIME,
            status = TrackingStatus.COMPLETED,
            currentProgress = 28.0,
            totalProgress = 28.0,
            userRating = 9.5f,
            anilistId = 154587
        ),
        MediaItemEntity(
            title = "Dandadan",
            nativeTitle = "ダンダダン",
            synopsis = "Momo Ayase and Ken Takakura bond over their shared love of the supernatural and sci-fi.",
            coverUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx171018-2ldCj6QywuOa.jpg",
            mediaType = MediaType.ANIME,
            status = TrackingStatus.IN_PROGRESS,
            currentProgress = 5.0,
            totalProgress = 12.0,
            anilistId = 171018
        ),

        // ── Manga ──
        MediaItemEntity(
            title = "One Piece",
            nativeTitle = "ONE PIECE",
            synopsis = "Follows the adventures of Monkey D. Luffy and his pirate crew.",
            coverUrl = "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/bx30013-oT7YguhEK1TE.jpg",
            mediaType = MediaType.MANGA,
            status = TrackingStatus.IN_PROGRESS,
            currentProgress = 1108.0,
            totalProgress = null,
            userRating = 10.0f,
            anilistId = 30013
        ),
        MediaItemEntity(
            title = "Chainsaw Man",
            nativeTitle = "チェンソーマン",
            synopsis = "Denji has a simple dream — to live a happy and peaceful life.",
            coverUrl = "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/bx105778-oSJNBjiHFKbh.jpg",
            mediaType = MediaType.MANGA,
            status = TrackingStatus.IN_PROGRESS,
            currentProgress = 175.0,
            totalProgress = null,
            anilistId = 105778
        ),

        // ── Novels ──
        MediaItemEntity(
            title = "Reverend Insanity",
            nativeTitle = "蛊真人",
            synopsis = "A story about a demonic cultivator who is reborn 500 years into the past.",
            coverUrl = "https://cdn.novelupdates.com/imgmid/reverend-insanity.jpg",
            mediaType = MediaType.NOVEL,
            status = TrackingStatus.IN_PROGRESS,
            currentProgress = 845.0,
            totalProgress = 2334.0,
            userRating = 9.8f,
            categoryNames = "Cultivation"
        ),
        MediaItemEntity(
            title = "Lord of the Mysteries",
            nativeTitle = "诡秘之主",
            synopsis = "Waking up in a mysterious world, Klein Moretti finds himself in the body of a young man.",
            coverUrl = "https://cdn.novelupdates.com/imgmid/lord-of-the-mysteries.jpg",
            mediaType = MediaType.NOVEL,
            status = TrackingStatus.COMPLETED,
            currentProgress = 1432.0,
            totalProgress = 1432.0,
            userRating = 9.7f,
            categoryNames = "Favorites"
        ),

        // ── Shows ──
        MediaItemEntity(
            title = "Arcane",
            synopsis = "Set in the utopian Piltover and the oppressed underground of Zaun.",
            coverUrl = "https://image.tmdb.org/t/p/original/fqldf2t8ztc9aiwn3k6mlX3tvRT.jpg",
            bannerUrl = "https://image.tmdb.org/t/p/original/rkB4LyZHo1NHXFEDHl9vSD9r1lI.jpg",
            mediaType = MediaType.SHOW,
            status = TrackingStatus.COMPLETED,
            currentProgress = 18.0,
            totalProgress = 18.0,
            userRating = 9.6f,
            tmdbId = 94605
        ),
        MediaItemEntity(
            title = "Severance",
            synopsis = "Mark leads a team of office workers whose memories have been surgically divided.",
            coverUrl = "https://image.tmdb.org/t/p/original/pBJ4A3ByBCHMlhBSYSyCoj4oTy6.jpg",
            mediaType = MediaType.SHOW,
            status = TrackingStatus.IN_PROGRESS,
            currentProgress = 6.0,
            totalProgress = 19.0,
            tmdbId = 95396
        ),

        // ── Movies ──
        MediaItemEntity(
            title = "Dune: Part Two",
            synopsis = "Paul Atreides unites with Chani and the Fremen against House Harkonnen.",
            coverUrl = "https://image.tmdb.org/t/p/original/8b8R8l88Qje9dn9OE8PY05Nez7H.jpg",
            bannerUrl = "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg",
            mediaType = MediaType.MOVIE,
            status = TrackingStatus.COMPLETED,
            currentProgress = 1.0,
            totalProgress = 1.0,
            userRating = 8.8f,
            tmdbId = 693134
        ),
        MediaItemEntity(
            title = "Your Name",
            nativeTitle = "君の名は。",
            synopsis = "Two strangers find themselves linked in a bizarre way.",
            coverUrl = "https://image.tmdb.org/t/p/original/q719jXXEzOoYaps6babgKnONONX.jpg",
            mediaType = MediaType.MOVIE,
            status = TrackingStatus.COMPLETED,
            currentProgress = 1.0,
            totalProgress = 1.0,
            userRating = 9.2f,
            tmdbId = 372058
        ),

        // ── Visual Novels ──
        MediaItemEntity(
            title = "Steins;Gate",
            nativeTitle = "シュタインズ・ゲート",
            synopsis = "Self-proclaimed mad scientist Okabe Rintaro discovers a way to send messages to the past.",
            coverUrl = "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/bx35853-3En46OACCaOo.jpg",
            mediaType = MediaType.VISUAL_NOVEL,
            status = TrackingStatus.COMPLETED,
            currentProgress = 30.0,
            totalProgress = 30.0,
            userRating = 9.4f,
            vndbId = "v2002"
        ),
        MediaItemEntity(
            title = "Umineko When They Cry",
            nativeTitle = "うみねこのなく頃に",
            synopsis = "A mystery visual novel about the Ushiromiya family gathering on the island of Rokkenjima.",
            coverUrl = "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/bx37578-JnDBXQjvwhPx.jpg",
            mediaType = MediaType.VISUAL_NOVEL,
            status = TrackingStatus.IN_PROGRESS,
            currentProgress = 45.0,
            totalProgress = 80.0,
            vndbId = "v24"
        ),

        // ── Games ──
        MediaItemEntity(
            title = "Elden Ring",
            synopsis = "An action RPG set in the Lands Between, created by FromSoftware and George R.R. Martin.",
            coverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co4jni.jpg",
            mediaType = MediaType.GAME,
            status = TrackingStatus.IN_PROGRESS,
            currentProgress = 87.5,
            totalProgress = null,
            userRating = 9.5f,
            platform = "PC",
            igdbId = 119171
        ),
        MediaItemEntity(
            title = "Persona 5 Royal",
            synopsis = "A JRPG where you lead a double life as a high school student and phantom thief.",
            coverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co1nic.jpg",
            mediaType = MediaType.GAME,
            status = TrackingStatus.COMPLETED,
            currentProgress = 142.0,
            totalProgress = null,
            userRating = 9.8f,
            platform = "PS5",
            categoryNames = "JRPGs",
            igdbId = 24302
        ),
        MediaItemEntity(
            title = "Baldur's Gate 3",
            synopsis = "Gather your party and return to the Forgotten Realms.",
            coverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co670h.jpg",
            mediaType = MediaType.GAME,
            status = TrackingStatus.IN_PROGRESS,
            currentProgress = 62.0,
            totalProgress = null,
            userRating = 9.3f,
            platform = "PC",
            igdbId = 119171
        ),
    )
}
