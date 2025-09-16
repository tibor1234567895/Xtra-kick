package com.github.andreyasadchy.xtra.kick.api

import com.github.andreyasadchy.xtra.kick.api.model.KickCategoriesResponse
import com.github.andreyasadchy.xtra.kick.api.model.KickCategory
import com.github.andreyasadchy.xtra.kick.api.model.KickCategoryResponse
import com.github.andreyasadchy.xtra.kick.api.model.KickChannelsResponse
import com.github.andreyasadchy.xtra.kick.api.model.KickLivestreamsResponse
import com.github.andreyasadchy.xtra.kick.api.model.KickUsersResponse
import com.github.andreyasadchy.xtra.kick.config.KickEnvironment
import com.github.andreyasadchy.xtra.kick.storage.KickTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KickApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val environment: KickEnvironment,
    private val tokenProvider: KickTokenProvider
) {
    private val apiBaseUrl = environment.normalizedApiBaseUrl.toHttpUrl()

    suspend fun getLivestreams(
        broadcasterUserIds: List<Long>? = null,
        categoryId: Long? = null,
        language: String? = null,
        limit: Int? = null,
        page: Int? = null,
        sort: String? = null
    ): KickLivestreamsResponse {
        val urlBuilder = apiBaseUrl.newBuilder().addPathSegment("livestreams")
        broadcasterUserIds?.forEach { urlBuilder.addQueryParameter("broadcaster_user_id", it.toString()) }
        categoryId?.let { urlBuilder.addQueryParameter("category_id", it.toString()) }
        language?.let { urlBuilder.addQueryParameter("language", it) }
        limit?.let { urlBuilder.addQueryParameter("limit", it.toString()) }
        page?.let { urlBuilder.addQueryParameter("page", it.toString()) }
        sort?.let { urlBuilder.addQueryParameter("sort", it) }
        return execute(urlBuilder.build()) { json.decodeFromString<KickLivestreamsResponse>(it) }
    }

    suspend fun searchCategories(query: String, page: Int? = null): KickCategoriesResponse {
        val urlBuilder = apiBaseUrl.newBuilder().addPathSegment("categories")
            .addQueryParameter("q", query)
        page?.let { urlBuilder.addQueryParameter("page", it.toString()) }
        return execute(urlBuilder.build()) { json.decodeFromString<KickCategoriesResponse>(it) }
    }

    suspend fun getCategory(categoryId: Long): KickCategory? {
        val url = apiBaseUrl.newBuilder()
            .addPathSegment("categories")
            .addPathSegment(categoryId.toString())
            .build()
        return execute(url) { json.decodeFromString<KickCategoryResponse>(it).data }
    }

    suspend fun getChannelsBySlugs(slugs: List<String>): KickChannelsResponse {
        require(slugs.isNotEmpty()) { "slugs must not be empty" }
        val urlBuilder = apiBaseUrl.newBuilder().addPathSegment("channels")
        slugs.forEach { slug -> urlBuilder.addQueryParameter("slug", slug) }
        return execute(urlBuilder.build()) { json.decodeFromString<KickChannelsResponse>(it) }
    }

    suspend fun getChannelsByBroadcasterIds(broadcasterUserIds: List<Long>): KickChannelsResponse {
        require(broadcasterUserIds.isNotEmpty()) { "broadcasterUserIds must not be empty" }
        val urlBuilder = apiBaseUrl.newBuilder().addPathSegment("channels")
        broadcasterUserIds.forEach { id -> urlBuilder.addQueryParameter("broadcaster_user_id", id.toString()) }
        return execute(urlBuilder.build()) { json.decodeFromString<KickChannelsResponse>(it) }
    }

    suspend fun getUsers(userIds: List<Long>? = null): KickUsersResponse {
        val urlBuilder = apiBaseUrl.newBuilder().addPathSegment("users")
        userIds?.forEach { id -> urlBuilder.addQueryParameter("user_id", id.toString()) }
        return execute(urlBuilder.build()) { json.decodeFromString<KickUsersResponse>(it) }
    }

    private suspend fun <T> execute(url: HttpUrl, parser: (String) -> T): T {
        return withContext(Dispatchers.IO) {
            val token = tokenProvider.accessToken
                ?: throw KickApiException("Kick access token is missing", statusCode = 401)
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
            val clientId = environment.clientId
            if (clientId.isNotBlank()) {
                requestBuilder.header("Client-Id", clientId)
            }
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string()
                    throw KickApiException(
                        message = body ?: "Request failed with HTTP ${response.code}",
                        statusCode = response.code
                    )
                }
                val body = response.body?.string() ?: throw IOException("Missing response body")
                parser(body)
            }
        }
    }
}
