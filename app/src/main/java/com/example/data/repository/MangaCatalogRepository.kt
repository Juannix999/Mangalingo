package com.example.data.repository

import com.example.R
import com.example.data.model.DetectedBubble
import com.example.data.model.InteractiveMangaPanel
import com.example.data.model.MangaChapter
import com.example.data.model.MangaSummary
import com.example.data.model.MangaTag
import com.example.data.model.ReaderPage
import com.example.data.remote.MangaDexApi
import com.example.data.remote.MangaDto
import kotlinx.coroutines.CancellationException

class MangaCatalogRepository(
    private val api: MangaDexApi = MangaDexApi.create()
) {
    suspend fun fetchMangaCatalog(
        query: String = "",
        languageFilter: String? = null,
        tagIds: List<String> = emptyList(),
        offset: Int = 0,
        limit: Int = 24
    ): Result<List<MangaSummary>> {
        return try {
            val cleanQuery = query.trim().takeIf { it.isNotBlank() }
            val langs = languageFilter?.takeIf { it.isNotBlank() }?.let { code ->
                when (code) {
                    "es" -> listOf("es", "es-la")
                    "pt-br" -> listOf("pt-br", "pt")
                    else -> listOf(code)
                }
            }
            val tags = tagIds.takeIf { it.isNotEmpty() }
            val response = api.searchManga(
                limit = limit,
                offset = offset,
                title = cleanQuery,
                languages = langs,
                includedTags = tags
            )
            val mapped = response.data.map { it.toSummary() }
            Result.success(mapped)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchGenreTags(): Result<List<MangaTag>> {
        return try {
            val response = api.getTags()
            val genres = response.data
                .filter { it.attributes?.group == "genre" }
                .mapNotNull { dto ->
                    val name = dto.attributes?.name?.get("es")
                        ?: dto.attributes?.name?.get("en")
                        ?: dto.attributes?.name?.values?.firstOrNull()
                    if (name != null) MangaTag(id = dto.id, name = name) else null
                }
                .sortedBy { it.name }
            Result.success(genres)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMangaDetail(mangaId: String): Result<MangaSummary> {
        val interactive = InteractiveMangaSamples.findById(mangaId)
        if (interactive != null) {
            return Result.success(interactive)
        }
        return try {
            val response = api.getMangaDetail(mangaId)
            val dto = response.data ?: error("Manga no encontrado")
            Result.success(dto.toSummary())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMangaChapters(
        mangaId: String,
        languageFilter: String? = null
    ): Result<List<MangaChapter>> {
        if (InteractiveMangaSamples.isInteractiveId(mangaId)) {
            return Result.success(InteractiveMangaSamples.chaptersFor(mangaId, languageFilter))
        }
        return try {
            val langs = languageFilter?.takeIf { it.isNotBlank() }?.let { code ->
                when (code) {
                    "es" -> listOf("es", "es-la")
                    "pt-br" -> listOf("pt-br", "pt")
                    else -> listOf(code)
                }
            }
            val response = api.getMangaFeed(
                id = mangaId,
                languages = langs,
                limit = 100
            )
            val chapters = response.data
                .filter { (it.attributes?.pages ?: 0) > 0 && it.attributes?.externalUrl.isNullOrBlank() }
                .map { dto ->
                    val attr = dto.attributes
                    MangaChapter(
                        id = dto.id,
                        volume = attr?.volume,
                        chapter = attr?.chapter,
                        title = attr?.title?.takeIf { it.isNotBlank() } ?: "",
                        language = attr?.translatedLanguage ?: "en",
                        pageCount = attr?.pages ?: 0
                    )
                }
            Result.success(chapters)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchChapterPages(chapterId: String): Result<List<ReaderPage>> {
        val interactivePages = InteractiveMangaSamples.pagesForChapter(chapterId)
        if (interactivePages != null) {
            return Result.success(interactivePages)
        }
        return try {
            val response = api.getChapterServer(chapterId)
            val baseUrl = response.baseUrl.trimEnd('/')
            val chapter = response.chapter ?: error("Páginas del capítulo no disponibles")
            val useSaver = chapter.dataSaver.isNotEmpty()
            val files = if (useSaver) chapter.dataSaver else chapter.data
            val qualitySegment = if (useSaver) "data-saver" else "data"
            val altFiles = if (useSaver) chapter.data else chapter.dataSaver
            val altSegment = if (useSaver) "data" else "data-saver"
            val pages = files.mapIndexed { idx, filename ->
                val altName = altFiles.getOrNull(idx)
                ReaderPage(
                    index = idx,
                    imageUrl = "$baseUrl/$qualitySegment/${chapter.hash}/$filename",
                    fallbackImageUrl = altName?.let { "$baseUrl/$altSegment/${chapter.hash}/$it" }
                )
            }
            Result.success(pages)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getInteractiveShowcase(): List<MangaSummary> = InteractiveMangaSamples.allMangas

    private fun MangaDto.toSummary(): MangaSummary {
        val attr = attributes
        val title = attr?.title?.get("es")
            ?: attr?.title?.get("en")
            ?: attr?.title?.get("ja-ro")
            ?: attr?.title?.values?.firstOrNull()
            ?: "Sin título"
        val description = attr?.description?.get("es")
            ?: attr?.description?.get("es-la")
            ?: attr?.description?.get("en")
            ?: attr?.description?.values?.firstOrNull()
            ?: "Sin descripción disponible."

        val coverFile = relationships
            .firstOrNull { it.type == "cover_art" }
            ?.attributes?.get("fileName") as? String
        val coverUrl = if (!coverFile.isNullOrBlank()) {
            "https://uploads.mangadex.org/covers/$id/$coverFile.512.jpg"
        } else {
            ""
        }

        val author = relationships
            .firstOrNull { it.type == "author" }
            ?.attributes?.get("name") as? String
            ?: "Autor desconocido"

        val tagNames = attr?.tags?.mapNotNull {
            it.attributes?.name?.get("es") ?: it.attributes?.name?.get("en")
        }?.take(5) ?: emptyList()

        return MangaSummary(
            id = id,
            title = title,
            description = description,
            coverUrl = coverUrl,
            author = author,
            originalLanguage = attr?.originalLanguage ?: "ja",
            availableLanguages = attr?.availableTranslatedLanguages?.distinct() ?: emptyList(),
            tags = tagNames,
            status = attr?.status ?: "ongoing",
            year = attr?.year
        )
    }
}

object InteractiveMangaSamples {
    const val NEO_SHIBUYA_ID = "interactive-neo-shibuya"
    const val KYOTO_CAFE_ID = "interactive-kyoto-cafe"

    val allMangas = listOf(
        MangaSummary(
            id = NEO_SHIBUYA_ID,
            title = "Crónicas de Neo-Shibuya",
            description = "Manga interactivo multilingüe ideal para practicar lectura y traducción. Cambia entre japonés, inglés, español y francés y mantén pulsada cualquier palabra o desliza para traducir frases enteras.",
            coverUrl = "",
            author = "Estudio MangaLingo",
            originalLanguage = "ja",
            availableLanguages = listOf("ja", "en", "es", "fr"),
            tags = listOf("Acción", "Sci-Fi", "Aventura"),
            status = "ongoing",
            year = 2026,
            isInteractiveOriginal = true,
            coverDrawableRes = R.drawable.img_scene_shibuya
        ),
        MangaSummary(
            id = KYOTO_CAFE_ID,
            title = "Medianoche en Café Kioto",
            description = "Un tranquilo café nocturno en Kioto donde viajeros de todo el mundo comparten historias. Diseñado para aprender vocabulario cotidiano en varios idiomas.",
            coverUrl = "",
            author = "Estudio MangaLingo",
            originalLanguage = "ja",
            availableLanguages = listOf("ja", "en", "es", "fr"),
            tags = listOf("Slice of Life", "Misterio", "Drama"),
            status = "ongoing",
            year = 2026,
            isInteractiveOriginal = true,
            coverDrawableRes = R.drawable.img_scene_kyoto
        )
    )

    fun isInteractiveId(id: String) = id == NEO_SHIBUYA_ID || id == KYOTO_CAFE_ID

    fun findById(id: String): MangaSummary? = allMangas.firstOrNull { it.id == id }

    fun chaptersFor(mangaId: String, languageFilter: String?): List<MangaChapter> {
        val langs = listOf("ja", "en", "es", "fr").let { list ->
            if (languageFilter.isNullOrBlank()) list else list.filter { it == languageFilter }
        }
        val titlePrefix = if (mangaId == NEO_SHIBUYA_ID) {
            mapOf(
                "ja" to "ネオ渋谷の信号",
                "en" to "Signal in Neo-Shibuya",
                "es" to "Señal en Neo-Shibuya",
                "fr" to "Signal à Néo-Shibuya"
            )
        } else {
            mapOf(
                "ja" to "雨の夜の約束",
                "en" to "A Rainy Night Promise",
                "es" to "Promesa en la noche lluviosa",
                "fr" to "Promesse d'une nuit pluvieuse"
            )
        }
        return langs.map { lang ->
            MangaChapter(
                id = "${mangaId}__ch1__$lang",
                volume = "1",
                chapter = "1",
                title = titlePrefix[lang] ?: "Capítulo 1",
                language = lang,
                pageCount = 2,
                isInteractiveOriginal = true
            )
        }
    }

    fun pagesForChapter(chapterId: String): List<ReaderPage>? {
        if (!chapterId.startsWith("interactive-")) return null
        val parts = chapterId.split("__")
        if (parts.size < 3) return null
        val mangaId = parts[0]
        val lang = parts[2]
        return if (mangaId == NEO_SHIBUYA_ID) {
            neoShibuyaPages(lang)
        } else {
            kyotoCafePages(lang)
        }
    }

    private fun neoShibuyaPages(lang: String): List<ReaderPage> {
        return when (lang) {
            "ja" -> listOf(
                ReaderPage(
                    index = 0,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "ネオ渋谷 · 屋上 (Azotea de Neo-Shibuya)",
                        sceneMood = "NOCHE LLUVIOSA · NEÓN",
                        languageCode = "ja",
                        artworkResId = R.drawable.img_scene_shibuya,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "b1",
                                rawText = "ちょっと 待って ください！",
                                tokens = listOf("ちょっと", "待って", "ください！"),
                                yMin = 120, xMin = 100, yMax = 300, xMax = 650,
                                speaker = "Akira"
                            ),
                            DetectedBubble(
                                id = "b2",
                                rawText = "この 暗号 を 解読 しなければ ならない。",
                                tokens = listOf("この", "暗号", "を", "解読", "しなければ", "ならない。"),
                                yMin = 390, xMin = 320, yMax = 600, xMax = 900,
                                speaker = "Ren"
                            ),
                            DetectedBubble(
                                id = "b3",
                                rawText = "時間 が もう ない よ！",
                                tokens = listOf("時間", "が", "もう", "ない", "よ！"),
                                yMin = 680, xMin = 120, yMax = 880, xMax = 680,
                                speaker = "Akira"
                            )
                        )
                    )
                ),
                ReaderPage(
                    index = 1,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "地下トンネル (Túnel Subterráneo)",
                        sceneMood = "TENSIÓN · PERSECUCIÓN",
                        languageCode = "ja",
                        artworkResId = R.drawable.img_hero_banner,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "b4",
                                rawText = "大丈夫 ですか？ 怪我 は ありませんか？",
                                tokens = listOf("大丈夫", "ですか？", "怪我", "は", "ありませんか？"),
                                yMin = 140, xMin = 150, yMax = 360, xMax = 850,
                                speaker = "Ren"
                            ),
                            DetectedBubble(
                                id = "b5",
                                rawText = "うん、 一緒 に 逃げよう！",
                                tokens = listOf("うん、", "一緒", "に", "逃げよう！"),
                                yMin = 500, xMin = 180, yMax = 740, xMax = 780,
                                speaker = "Akira"
                            )
                        )
                    )
                )
            )
            "en" -> listOf(
                ReaderPage(
                    index = 0,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "Neo-Shibuya Rooftop",
                        sceneMood = "RAINY NIGHT · NEON",
                        languageCode = "en",
                        artworkResId = R.drawable.img_scene_shibuya,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "b1",
                                rawText = "Hold on a second!",
                                tokens = listOf("Hold", "on", "a", "second!"),
                                yMin = 120, xMin = 100, yMax = 300, xMax = 650,
                                speaker = "Akira"
                            ),
                            DetectedBubble(
                                id = "b2",
                                rawText = "We have to figure out this secret code.",
                                tokens = listOf("We", "have", "to", "figure", "out", "this", "secret", "code."),
                                yMin = 390, xMin = 320, yMax = 600, xMax = 900,
                                speaker = "Ren"
                            ),
                            DetectedBubble(
                                id = "b3",
                                rawText = "We are running out of time!",
                                tokens = listOf("We", "are", "running", "out", "of", "time!"),
                                yMin = 680, xMin = 120, yMax = 880, xMax = 680,
                                speaker = "Akira"
                            )
                        )
                    )
                ),
                ReaderPage(
                    index = 1,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "Subway Tunnel",
                        sceneMood = "CHASE · HIGH TENSION",
                        languageCode = "en",
                        artworkResId = R.drawable.img_hero_banner,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "b4",
                                rawText = "Are you alright? Did you get hurt?",
                                tokens = listOf("Are", "you", "alright?", "Did", "you", "get", "hurt?"),
                                yMin = 140, xMin = 150, yMax = 360, xMax = 850,
                                speaker = "Ren"
                            ),
                            DetectedBubble(
                                id = "b5",
                                rawText = "Yeah, let's stick together and run!",
                                tokens = listOf("Yeah,", "let's", "stick", "together", "and", "run!"),
                                yMin = 500, xMin = 180, yMax = 740, xMax = 780,
                                speaker = "Akira"
                            )
                        )
                    )
                )
            )
            "fr" -> listOf(
                ReaderPage(
                    index = 0,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "Toit de Néo-Shibuya",
                        sceneMood = "NUIT PLUVIEUSE · NÉON",
                        languageCode = "fr",
                        artworkResId = R.drawable.img_scene_shibuya,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "b1",
                                rawText = "Attends une seconde, s'il te plaît !",
                                tokens = listOf("Attends", "une", "seconde,", "s'il", "te", "plaît", "!"),
                                yMin = 120, xMin = 100, yMax = 300, xMax = 650,
                                speaker = "Akira"
                            ),
                            DetectedBubble(
                                id = "b2",
                                rawText = "Il faut déchiffrer ce message tout de suite.",
                                tokens = listOf("Il", "faut", "déchiffrer", "ce", "message", "tout", "de", "suite."),
                                yMin = 390, xMin = 320, yMax = 600, xMax = 900,
                                speaker = "Ren"
                            )
                        )
                    )
                ),
                ReaderPage(
                    index = 1,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "Tunnel Souterrain",
                        sceneMood = "POURSUITE · TENSION",
                        languageCode = "fr",
                        artworkResId = R.drawable.img_hero_banner,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "b4",
                                rawText = "Tu vas bien ? Tu n'es pas blessé ?",
                                tokens = listOf("Tu", "vas", "bien", "?", "Tu", "n'es", "pas", "blessé", "?"),
                                yMin = 140, xMin = 150, yMax = 360, xMax = 850,
                                speaker = "Ren"
                            ),
                            DetectedBubble(
                                id = "b5",
                                rawText = "Oui, restons ensemble et courons !",
                                tokens = listOf("Oui,", "restons", "ensemble", "et", "courons", "!"),
                                yMin = 500, xMin = 180, yMax = 740, xMax = 780,
                                speaker = "Akira"
                            )
                        )
                    )
                )
            )
            else -> listOf(
                ReaderPage(
                    index = 0,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "Azotea de Neo-Shibuya",
                        sceneMood = "NOCHE LLUVIOSA · NEÓN",
                        languageCode = "es",
                        artworkResId = R.drawable.img_scene_shibuya,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "b1",
                                rawText = "¡Espera un momento, por favor!",
                                tokens = listOf("¡Espera", "un", "momento,", "por", "favor!"),
                                yMin = 120, xMin = 100, yMax = 300, xMax = 650,
                                speaker = "Akira"
                            ),
                            DetectedBubble(
                                id = "b2",
                                rawText = "Tenemos que descifrar este código cuanto antes.",
                                tokens = listOf("Tenemos", "que", "descifrar", "este", "código", "cuanto", "antes."),
                                yMin = 390, xMin = 320, yMax = 600, xMax = 900,
                                speaker = "Ren"
                            )
                        )
                    )
                ),
                ReaderPage(
                    index = 1,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "Túnel Subterráneo",
                        sceneMood = "PERSECUCIÓN · ALTA TENSIÓN",
                        languageCode = "es",
                        artworkResId = R.drawable.img_hero_banner,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "b4",
                                rawText = "¿Estás bien? ¿No te hiciste daño?",
                                tokens = listOf("¿Estás", "bien?", "¿No", "te", "hiciste", "daño?"),
                                yMin = 140, xMin = 150, yMax = 360, xMax = 850,
                                speaker = "Ren"
                            ),
                            DetectedBubble(
                                id = "b5",
                                rawText = "¡Sí, sigamos juntos y escapemos de aquí!",
                                tokens = listOf("¡Sí,", "sigamos", "juntos", "y", "escapemos", "de", "aquí!"),
                                yMin = 500, xMin = 180, yMax = 740, xMax = 780,
                                speaker = "Akira"
                            )
                        )
                    )
                )
            )
        }
    }

    private fun kyotoCafePages(lang: String): List<ReaderPage> {
        return when (lang) {
            "ja" -> listOf(
                ReaderPage(
                    index = 0,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "京都の喫茶店 (Café de Kioto)",
                        sceneMood = "LLUVIA SUAVE · CALMA",
                        languageCode = "ja",
                        artworkResId = R.drawable.img_scene_kyoto,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "k1",
                                rawText = "いらっしゃいませ！ 温かい 珈琲 は いかがですか？",
                                tokens = listOf("いらっしゃいませ！", "温かい", "珈琲", "は", "いかがですか？"),
                                yMin = 140, xMin = 110, yMax = 350, xMax = 840,
                                speaker = "Maestra Haru"
                            ),
                            DetectedBubble(
                                id = "k2",
                                rawText = "外 は とても 寒い です ね。",
                                tokens = listOf("外", "は", "とても", "寒い", "です", "ね。"),
                                yMin = 480, xMin = 200, yMax = 680, xMax = 820,
                                speaker = "Visitante"
                            )
                        )
                    )
                ),
                ReaderPage(
                    index = 1,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "雨の夜 (Noche de lluvia)",
                        sceneMood = "CONVERSACIÓN · CALIDEZ",
                        languageCode = "ja",
                        artworkResId = R.drawable.img_hero_banner,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "k3",
                                rawText = "ゆっくり 休んで ください。",
                                tokens = listOf("ゆっくり", "休んで", "ください。"),
                                yMin = 160, xMin = 140, yMax = 360, xMax = 800,
                                speaker = "Maestra Haru"
                            )
                        )
                    )
                )
            )
            "en" -> listOf(
                ReaderPage(
                    index = 0,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "Midnight Kyoto Café",
                        sceneMood = "SOFT RAIN · WARM LIGHT",
                        languageCode = "en",
                        artworkResId = R.drawable.img_scene_kyoto,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "k1",
                                rawText = "Welcome in! Would you care for a cup of hot coffee?",
                                tokens = listOf("Welcome", "in!", "Would", "you", "care", "for", "a", "cup", "of", "hot", "coffee?"),
                                yMin = 140, xMin = 110, yMax = 350, xMax = 840,
                                speaker = "Haru"
                            ),
                            DetectedBubble(
                                id = "k2",
                                rawText = "It's freezing outside tonight.",
                                tokens = listOf("It's", "freezing", "outside", "tonight."),
                                yMin = 480, xMin = 200, yMax = 680, xMax = 820,
                                speaker = "Traveler"
                            )
                        )
                    )
                ),
                ReaderPage(
                    index = 1,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "Rainy Midnight Counter",
                        sceneMood = "COZY · QUIET TALK",
                        languageCode = "en",
                        artworkResId = R.drawable.img_hero_banner,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "k3",
                                rawText = "Take your time and warm up by the lantern.",
                                tokens = listOf("Take", "your", "time", "and", "warm", "up", "by", "the", "lantern."),
                                yMin = 160, xMin = 140, yMax = 360, xMax = 800,
                                speaker = "Haru"
                            )
                        )
                    )
                )
            )
            "fr" -> listOf(
                ReaderPage(
                    index = 0,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "Café Minuit à Kyoto",
                        sceneMood = "PLUIE DOUCE · CHALEUR",
                        languageCode = "fr",
                        artworkResId = R.drawable.img_scene_kyoto,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "k1",
                                rawText = "Bienvenue ! Voulez-vous un café bien chaud ?",
                                tokens = listOf("Bienvenue", "!", "Voulez-vous", "un", "café", "bien", "chaud", "?"),
                                yMin = 140, xMin = 110, yMax = 350, xMax = 840,
                                speaker = "Haru"
                            )
                        )
                    )
                ),
                ReaderPage(
                    index = 1,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "Comptoir sous la Pluie",
                        sceneMood = "CALME · HISTOIRE",
                        languageCode = "fr",
                        artworkResId = R.drawable.img_hero_banner,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "k3",
                                rawText = "Il fait si froid dehors ce soir.",
                                tokens = listOf("Il", "fait", "si", "froid", "dehors", "ce", "soir."),
                                yMin = 160, xMin = 140, yMax = 360, xMax = 800,
                                speaker = "Voyageur"
                            )
                        )
                    )
                )
            )
            else -> listOf(
                ReaderPage(
                    index = 0,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "Café Kioto de Medianoche",
                        sceneMood = "LLUVIA SUAVE · CALMA",
                        languageCode = "es",
                        artworkResId = R.drawable.img_scene_kyoto,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "k1",
                                rawText = "¡Bienvenido! ¿Te gustaría una taza de café caliente?",
                                tokens = listOf("¡Bienvenido!", "¿Te", "gustaría", "una", "taza", "de", "café", "caliente?"),
                                yMin = 140, xMin = 110, yMax = 350, xMax = 840,
                                speaker = "Haru"
                            )
                        )
                    )
                ),
                ReaderPage(
                    index = 1,
                    interactivePanel = InteractiveMangaPanel(
                        sceneTitle = "Barra bajo la Lluvia",
                        sceneMood = "CHARLA · CALIDEZ",
                        languageCode = "es",
                        artworkResId = R.drawable.img_hero_banner,
                        bubbles = listOf(
                            DetectedBubble(
                                id = "k3",
                                rawText = "Hace mucho frío afuera esta noche.",
                                tokens = listOf("Hace", "mucho", "frío", "afuera", "esta", "noche."),
                                yMin = 160, xMin = 140, yMax = 360, xMax = 800,
                                speaker = "Visitante"
                            )
                        )
                    )
                )
            )
        }
    }
}
