package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface MangaDexApi {

    @GET("manga")
    suspend fun searchManga(
        @Query("limit") limit: Int = 24,
        @Query("offset") offset: Int = 0,
        @Query("title") title: String? = null,
        @Query("availableTranslatedLanguage[]") languages: List<String>? = null,
        @Query("includedTags[]") includedTags: List<String>? = null,
        @Query("contentRating[]") contentRating: List<String> = listOf("safe", "suggestive"),
        @Query("includes[]") includes: List<String> = listOf("cover_art", "author"),
        @Query("order[followedCount]") orderFollowedCount: String = "desc",
        @Query("hasAvailableChapters") hasAvailableChapters: String = "true"
    ): MangaListResponse

    @GET("manga/tag")
    suspend fun getTags(): TagListResponse

    @GET("manga/{id}")
    suspend fun getMangaDetail(
        @Path("id") id: String,
        @Query("includes[]") includes: List<String> = listOf("cover_art", "author")
    ): MangaSingleResponse

    @GET("manga/{id}/feed")
    suspend fun getMangaFeed(
        @Path("id") id: String,
        @Query("translatedLanguage[]") languages: List<String>? = null,
        @Query("contentRating[]") contentRating: List<String> = listOf("safe", "suggestive"),
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("order[volume]") orderVolume: String = "asc",
        @Query("order[chapter]") orderChapter: String = "asc",
        @Query("includeExternalUrl") includeExternalUrl: Int = 0
    ): ChapterFeedResponse

    @GET("at-home/server/{chapterId}")
    suspend fun getChapterServer(
        @Path("chapterId") chapterId: String,
        @Query("forcePort443") forcePort443: Boolean = true
    ): AtHomeResponse

    companion object {
        private const val BASE_URL = "https://api.mangadex.org/"

        fun create(): MangaDexApi {
            val userAgentInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "MangaLingo/1.0 (Android Compose Reader)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(userAgentInterceptor)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(MangaDexApi::class.java)
        }
    }
}
