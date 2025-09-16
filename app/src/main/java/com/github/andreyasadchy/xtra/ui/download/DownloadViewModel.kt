package com.github.andreyasadchy.xtra.ui.download

import android.content.Context
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.util.KickApiHelper
import com.github.andreyasadchy.xtra.util.toast
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    private val _qualities = MutableStateFlow<Map<String, Pair<String, String>>?>(null)
    val qualities: StateFlow<Map<String, Pair<String, String>>?> = _qualities
    val dismiss = MutableStateFlow(false)
    var backupQualities: List<String>? = null

    fun setStream(networkLibrary: String?, gqlHeaders: Map<String, String>, channelLogin: String?, qualities: Map<String, Pair<String, String>>?, randomDeviceId: Boolean?, xDeviceId: String?, playerType: String?, supportedCodecs: String?, enableIntegrity: Boolean) {
        if (_qualities.value == null) {
            if (!qualities.isNullOrEmpty()) {
                _qualities.value = qualities
            } else {
                viewModelScope.launch {
                    val default = mutableMapOf("source" to "", "1080p60" to "", "1080p30" to "", "720p60" to "", "720p30" to "", "480p30" to "", "360p30" to "", "160p30" to "", "audio_only" to "")
                    try {
                        val urls = if (!channelLogin.isNullOrBlank()) {
                            val playlist = playerRepository.loadStreamPlaylist(networkLibrary, gqlHeaders, channelLogin, randomDeviceId, xDeviceId, playerType, supportedCodecs, enableIntegrity)
                            if (!playlist.isNullOrBlank()) {
                                val names = "NAME=\"(.*)\"".toRegex().findAll(playlist).map { it.groupValues[1] }.toMutableList()
                                val urls = "https://.*\\.m3u8".toRegex().findAll(playlist).map(MatchResult::value).toMutableList()
                                names.zip(urls).toMap(mutableMapOf()).takeIf { it.isNotEmpty() } ?: default
                            } else default
                        } else default
                        val map = mutableMapOf<String, Pair<String, String>>()
                        urls.entries.forEach {
                            when (it.key) {
                                "source" -> map[it.key] = Pair(ContextCompat.getString(applicationContext, R.string.source), it.value)
                                "audio_only" -> map[it.key] = Pair(ContextCompat.getString(applicationContext, R.string.audio_only), it.value)
                                else -> map[it.key] = Pair(it.key, it.value)
                            }
                        }
                        _qualities.value = map.toList()
                            .sortedByDescending {
                                it.first.substringAfter("p", "").takeWhile { it.isDigit() }.toIntOrNull()
                            }
                            .sortedByDescending {
                                it.first.substringBefore("p", "").takeWhile { it.isDigit() }.toIntOrNull()
                            }
                            .sortedByDescending {
                                it.first == "source"
                            }
                            .toMap()
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check") {
                            if (integrity.value == null) {
                                integrity.value = "refresh"
                            }
                        } else {
                            val map = mutableMapOf<String, Pair<String, String>>()
                            default.forEach {
                                when (it.key) {
                                    "source" -> map[it.key] = Pair(ContextCompat.getString(applicationContext, R.string.source), it.value)
                                    "audio_only" -> map[it.key] = Pair(ContextCompat.getString(applicationContext, R.string.audio_only), it.value)
                                    else -> map[it.key] = Pair(it.key, it.value)
                                }
                            }
                            _qualities.value = map
                        }
                    }
                }
            }
        }
    }

    fun setVideo(networkLibrary: String?, gqlHeaders: Map<String, String>, videoId: String?, animatedPreviewUrl: String?, videoType: String?, qualities: Map<String, Pair<String, String>>?, playerType: String?, supportedCodecs: String?, skipAccessToken: Int, enableIntegrity: Boolean) {
        if (_qualities.value == null) {
            if (!qualities.isNullOrEmpty()) {
                _qualities.value = qualities
            } else {
                viewModelScope.launch {
                    try {
                        if (skipAccessToken <= 1 && !animatedPreviewUrl.isNullOrBlank()) {
                            val urls = KickApiHelper.getVideoUrlMapFromPreview(animatedPreviewUrl, videoType, backupQualities)
                            val map = mutableMapOf<String, Pair<String, String>>()
                            urls.entries.forEach {
                                when (it.key) {
                                    "source" -> map[it.key] = Pair(ContextCompat.getString(applicationContext, R.string.source), it.value)
                                    "audio_only" -> map[it.key] = Pair(ContextCompat.getString(applicationContext, R.string.audio_only), it.value)
                                    else -> map[it.key] = Pair(it.key, it.value)
                                }
                            }
                            map.remove("audio_only")?.let { map.put("audio_only", it) }
                            _qualities.value = map.toList()
                                .sortedByDescending {
                                    it.first.substringAfter("p", "").takeWhile { it.isDigit() }.toIntOrNull()
                                }
                                .sortedByDescending {
                                    it.first.substringBefore("p", "").takeWhile { it.isDigit() }.toIntOrNull()
                                }
                                .sortedByDescending {
                                    it.first == "source"
                                }
                                .toMap()
                        } else {
                            val result = playerRepository.loadVideoPlaylist(networkLibrary, gqlHeaders, videoId, playerType, supportedCodecs, enableIntegrity)
                            val playlist = result.first
                            backupQualities = result.second
                            if (!playlist.isNullOrBlank()) {
                                val names = Regex("NAME=\"(.+?)\"").findAll(playlist).mapNotNull { it.groups[1]?.value }.toMutableList()
                                val codecs = Regex("CODECS=\"(.+?)\"").findAll(playlist).mapNotNull { it.groups[1]?.value }.toMutableList()
                                val urls = Regex("https://.*\\.m3u8").findAll(playlist).map(MatchResult::value).toMutableList()
                                playlist.lines().filter { it.startsWith("#EXT-X-SESSION-DATA") }.let { list ->
                                    if (list.isNotEmpty()) {
                                        val url = urls.firstOrNull()?.takeIf { it.contains("/index-") }
                                        val groupId = Regex("GROUP-ID=\"(.+?)\"").find(playlist)?.groups?.get(1)?.value
                                        if (url != null && groupId != null) {
                                            list.forEach { line ->
                                                val id = Regex("DATA-ID=\"(.+?)\"").find(line)?.groups?.get(1)?.value
                                                if (id == "com.amazon.ivs.unavailable-media") {
                                                    val value = Regex("VALUE=\"(.+?)\"").find(line)?.groups?.get(1)?.value
                                                    if (value != null) {
                                                        val bytes = try {
                                                            Base64.decode(value, Base64.DEFAULT)
                                                        } catch (e: IllegalArgumentException) {
                                                            null
                                                        }
                                                        if (bytes != null) {
                                                            val string = String(bytes)
                                                            val array = try {
                                                                JSONArray(string)
                                                            } catch (e: JSONException) {
                                                                null
                                                            }
                                                            if (array != null) {
                                                                for (i in 0 until array.length()) {
                                                                    val obj = array.optJSONObject(i)
                                                                    if (obj != null) {
                                                                        var skip = false
                                                                        val filterReasons = obj.optJSONArray("FILTER_REASONS")
                                                                        if (filterReasons != null) {
                                                                            for (filterIndex in 0 until filterReasons.length()) {
                                                                                val filter = filterReasons.optString(filterIndex)
                                                                                if (filter == "FR_CODEC_NOT_REQUESTED") {
                                                                                    skip = true
                                                                                    break
                                                                                }
                                                                            }
                                                                        }
                                                                        if (!skip) {
                                                                            val name = obj.optString("NAME")
                                                                            val codec = obj.optString("CODECS")
                                                                            val newGroupId = obj.optString("GROUP-ID")
                                                                            if (!name.isNullOrBlank() && !newGroupId.isNullOrBlank()) {
                                                                                names.add(name)
                                                                                if (!codec.isNullOrBlank()) {
                                                                                    codecs.add(codec)
                                                                                }
                                                                                urls.add(url.replace("$groupId/index-", "$newGroupId/index-"))
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                val codecList = codecs.map { codec ->
                                    codec.substringBefore('.').let {
                                        when (it) {
                                            "av01" -> "AV1"
                                            "hev1" -> "H.265"
                                            "avc1" -> "H.264"
                                            else -> it
                                        }
                                    }
                                }.takeUnless { it.all { it == "H.264" || it == "mp4a" } }
                                val map = mutableMapOf<String, Pair<String, String>>()
                                names.forEachIndexed { index, quality ->
                                    urls.getOrNull(index)?.let { url ->
                                        when {
                                            quality.equals("source", true) -> {
                                                map["source"] = Pair(ContextCompat.getString(applicationContext, R.string.source), url)
                                            }
                                            quality.startsWith("audio", true) -> {
                                                map["audio_only"] = Pair(ContextCompat.getString(applicationContext, R.string.audio_only), url)
                                            }
                                            else -> {
                                                map[quality] = Pair(codecList?.getOrNull(index)?.let { "$quality $it" } ?: quality, url)
                                            }
                                        }
                                    }
                                }
                                _qualities.value = map.toList()
                                    .sortedByDescending {
                                        it.first.substringAfter("p", "").takeWhile { it.isDigit() }.toIntOrNull()
                                    }
                                    .sortedByDescending {
                                        it.first.substringBefore("p", "").takeWhile { it.isDigit() }.toIntOrNull()
                                    }
                                    .sortedByDescending {
                                        it.first == "source"
                                    }
                                    .toMap()
                            } else {
                                if (skipAccessToken == 2 && !animatedPreviewUrl.isNullOrBlank()) {
                                    val urls = KickApiHelper.getVideoUrlMapFromPreview(animatedPreviewUrl, videoType, backupQualities)
                                    val map = mutableMapOf<String, Pair<String, String>>()
                                    urls.entries.forEach {
                                        when (it.key) {
                                            "source" -> map[it.key] = Pair(ContextCompat.getString(applicationContext, R.string.source), it.value)
                                            "audio_only" -> map[it.key] = Pair(ContextCompat.getString(applicationContext, R.string.audio_only), it.value)
                                            else -> map[it.key] = Pair(it.key, it.value)
                                        }
                                    }
                                    map.remove("audio_only")?.let { map.put("audio_only", it) }
                                    _qualities.value = map.toList()
                                        .sortedByDescending {
                                            it.first.substringAfter("p", "").takeWhile { it.isDigit() }.toIntOrNull()
                                        }
                                        .sortedByDescending {
                                            it.first.substringBefore("p", "").takeWhile { it.isDigit() }.toIntOrNull()
                                        }
                                        .sortedByDescending {
                                            it.first == "source"
                                        }
                                        .toMap()
                                } else {
                                    throw IllegalAccessException()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check" && integrity.value == null) {
                            integrity.value = "refresh"
                        }
                        if (e is IllegalAccessException) {
                            applicationContext.toast(ContextCompat.getString(applicationContext, R.string.video_subscribers_only))
                            dismiss.value = true
                        }
                    }
                }
            }
        }
    }

    fun setClip(networkLibrary: String?, gqlHeaders: Map<String, String>, clipId: String?, thumbnailUrl: String?, qualities: Map<String, Pair<String, String>>?, skipAccessToken: Int, enableIntegrity: Boolean) {
        if (_qualities.value == null) {
            if (!qualities.isNullOrEmpty()) {
                _qualities.value = qualities
            } else {
                viewModelScope.launch {
                    try {
                        val urls = if (skipAccessToken <= 1 && !thumbnailUrl.isNullOrBlank()) {
                            KickApiHelper.getClipUrlMapFromPreview(thumbnailUrl)
                        } else {
                            playerRepository.loadClipUrls(networkLibrary, gqlHeaders, clipId, enableIntegrity) ?:
                            if (skipAccessToken == 2 && !thumbnailUrl.isNullOrBlank()) {
                                KickApiHelper.getClipUrlMapFromPreview(thumbnailUrl)
                            } else null
                        }
                        val map = mutableMapOf<String, Pair<String, String>>()
                        urls?.entries?.forEach {
                            when (it.key) {
                                "source" -> map[it.key] = Pair(ContextCompat.getString(applicationContext, R.string.source), it.value)
                                "audio_only" -> map[it.key] = Pair(ContextCompat.getString(applicationContext, R.string.audio_only), it.value)
                                else -> map[it.key] = Pair(it.key, it.value)
                            }
                        }
                        _qualities.value = map.toList()
                            .sortedByDescending {
                                it.first.substringAfter("p", "").takeWhile { it.isDigit() }.toIntOrNull()
                            }
                            .sortedByDescending {
                                it.first.substringBefore("p", "").takeWhile { it.isDigit() }.toIntOrNull()
                            }
                            .sortedByDescending {
                                it.first == "source"
                            }
                            .toMap()
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check" && integrity.value == null) {
                            integrity.value = "refresh"
                        }
                    }
                }
            }
        }
    }
}