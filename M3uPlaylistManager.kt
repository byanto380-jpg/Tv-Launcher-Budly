package com.custom.tvlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

data class IptvChannel(
    val name: String,
    val url: String,
    val group: String = "Umum"
)

class M3uPlaylistManager(private val context: Context) {
    private val playlistKey = stringPreferencesKey("m3u_playlist_source")

    val playlistUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[playlistKey] ?: ""
    }

    suspend fun savePlaylistSource(source: String) {
        context.dataStore.edit { prefs ->
            prefs[playlistKey] = source.trim()
        }
    }

    suspend fun loadChannels(source: String): List<IptvChannel> = withContext(Dispatchers.IO) {
        if (source.isBlank()) return@withContext emptyList()

        val rawContent = try {
            if (source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)) {
                URL(source).openStream().bufferedReader().use { it.readText() }
            } else {
                val file = File(source)
                if (file.exists()) file.readText() else ""
            }
        } catch (_: Exception) {
            ""
        }

        parseM3uContent(rawContent)
    }

    private fun parseM3uContent(content: String): List<IptvChannel> {
        if (!content.contains("#EXTM3U", ignoreCase = true) && !content.contains("#EXTINF", ignoreCase = true)) {
            return emptyList()
        }

        val channels = mutableListOf<IptvChannel>()
        var pendingName: String? = null
        var pendingGroup = "Umum"

        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                if (line.startsWith("#EXTINF", ignoreCase = true)) {
                    val groupMatch = Regex("""group-title="([^"]*)"""").find(line)
                    pendingGroup = groupMatch?.groupValues?.getOrNull(1)?.ifBlank { "Umum" } ?: "Umum"

                    val commaIndex = line.lastIndexOf(',')
                    pendingName = if (commaIndex != -1 && commaIndex + 1 < line.length) {
                        line.substring(commaIndex + 1).trim()
                    } else {
                        "Saluran TV"
                    }
                } else if (!line.startsWith("#")) {
                    val name = pendingName ?: "Saluran ${channels.size + 1}"
                    channels.add(IptvChannel(name = name, url = line, group = pendingGroup))
                    pendingName = null
                    pendingGroup = "Umum"
                }
            }

        return channels
    }
}

fun launchIptvStream(context: Context, channel: IptvChannel): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(channel.url), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
