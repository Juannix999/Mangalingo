package com.example.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MangaListResponse(
    val result: String = "",
    val data: List<MangaDto> = emptyList(),
    val limit: Int = 20,
    val offset: Int = 0,
    val total: Int = 0
)

@JsonClass(generateAdapter = true)
data class MangaSingleResponse(
    val result: String = "",
    val data: MangaDto? = null
)

@JsonClass(generateAdapter = true)
data class MangaDto(
    val id: String = "",
    val type: String = "",
    val attributes: MangaAttributesDto? = null,
    val relationships: List<RelationshipDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MangaAttributesDto(
    val title: Map<String, String> = emptyMap(),
    val altTitles: List<Map<String, String>> = emptyList(),
    val description: Map<String, String> = emptyMap(),
    val originalLanguage: String? = null,
    val status: String? = null,
    val year: Int? = null,
    val contentRating: String? = null,
    val availableTranslatedLanguages: List<String> = emptyList(),
    val tags: List<TagDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TagListResponse(
    val result: String = "",
    val data: List<TagDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TagDto(
    val id: String = "",
    val attributes: TagAttributesDto? = null
)

@JsonClass(generateAdapter = true)
data class TagAttributesDto(
    val name: Map<String, String> = emptyMap(),
    val group: String? = null
)

@JsonClass(generateAdapter = true)
data class RelationshipDto(
    val id: String = "",
    val type: String = "",
    val attributes: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class ChapterFeedResponse(
    val result: String = "",
    val data: List<ChapterDto> = emptyList(),
    val limit: Int = 100,
    val offset: Int = 0,
    val total: Int = 0
)

@JsonClass(generateAdapter = true)
data class ChapterDto(
    val id: String = "",
    val attributes: ChapterAttributesDto? = null
)

@JsonClass(generateAdapter = true)
data class ChapterAttributesDto(
    val volume: String? = null,
    val chapter: String? = null,
    val title: String? = null,
    val translatedLanguage: String? = null,
    val pages: Int = 0,
    val externalUrl: String? = null,
    val publishAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AtHomeResponse(
    val result: String = "",
    val baseUrl: String = "",
    val chapter: AtHomeChapterDto? = null
)

@JsonClass(generateAdapter = true)
data class AtHomeChapterDto(
    val hash: String = "",
    val data: List<String> = emptyList(),
    val dataSaver: List<String> = emptyList()
)
