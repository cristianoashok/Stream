package com.victor.stream.player

import android.content.Context
import android.net.Uri
import com.victor.stream.BuildConfig
import com.victor.stream.core.Logger
import com.victor.stream.domain.EpisodeRef
import com.victor.stream.domain.MediaItemModel
import com.victor.stream.domain.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * Backup subtitle source: SubDL (free API key, Subscene's old library).
 *
 * SubDL hands out .zip files, so we download a few English ones, pull out the
 * right .srt/.vtt (the matching episode for season packs), save it in the app's
 * cache folder and give the player a local file link. Each file's text is checked
 * by [SubtitleVerifier] so only real subtitles in the right language are used.
 *
 * Key: SUBDL_API_KEY in local.properties (never committed).
 */
object SubDlSubtitles {

    private const val UA = "Mozilla/5.0 (Linux; Android 14) Chrome/131.0.0.0 Mobile Safari/537.36"
    private val results = ConcurrentHashMap<String, List<SubtitleTrack>>()

    val isConfigured: Boolean
        get() = BuildConfig.SUBDL_API_KEY.isNotBlank()

    /** English subtitles for this title/episode from SubDL (empty if none / no key). */
    suspend fun fetch(
        context: Context,
        media: MediaItemModel,
        episode: EpisodeRef?,
        maxFiles: Int = 3
    ): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        val key = BuildConfig.SUBDL_API_KEY
        if (key.isBlank()) return@withContext emptyList()
        val tmdbId = media.tmdbId ?: (if (media.isAnime) null else media.id) ?: return@withContext emptyList()
        val season = episode?.seasonNumber
        val ep = episode?.episodeNumber
        val cacheKey = "${if (media.isSeries) "tv" else "mv"}-$tmdbId-$season-$ep-$maxFiles"
        results[cacheKey]?.let { return@withContext it }

        val query = buildString {
            append("https://api.subdl.com/api/v1/subtitles?api_key=")
            append(URLEncoder.encode(key, "UTF-8"))
            append("&tmdb_id=").append(tmdbId)
            append("&type=").append(if (media.isSeries) "tv" else "movie")
            append("&languages=EN&subs_per_page=10")
            if (media.isSeries && season != null && ep != null) {
                append("&season_number=").append(season).append("&episode_number=").append(ep)
            }
        }
        val json = getJson(query)
        if (json == null) {
            Logger.w("Subtitles", "SubDL: no response")
            return@withContext emptyList()
        }
        if (!json.optBoolean("status", false)) {
            Logger.w("Subtitles", "SubDL: ${json.optString("error", "request refused").take(120)}")
            return@withContext emptyList()
        }
        val list = json.optJSONArray("subtitles")
        if (list == null || list.length() == 0) {
            Logger.i("Subtitles", "SubDL found 0 subtitle(s) for ${media.title}")
            return@withContext emptyList()
        }

        val dir = File(context.cacheDir, "subtitles").apply { mkdirs() }
        val out = mutableListOf<SubtitleTrack>()
        for (i in 0 until list.length()) {
            if (out.size >= maxFiles) break
            val o = list.optJSONObject(i) ?: continue
            val path = o.optString("url", "")
            if (path.isBlank()) continue
            val zipUrl = if (path.startsWith("http")) path else "https://dl.subdl.com$path"
            val picked = runCatching {
                downloadAndPick(zipUrl, if (media.isSeries) season else null, if (media.isSeries) ep else null)
            }.getOrNull() ?: continue
            val (entryName, text) = picked
            val ext = if (entryName.endsWith(".vtt", ignoreCase = true)) "vtt" else "srt"
            val file = File(dir, "subdl-$tmdbId-${season ?: 0}-${ep ?: 0}-$i.$ext")
            runCatching { file.writeText(text, Charsets.UTF_8) }.getOrNull() ?: continue
            val url = Uri.fromFile(file).toString()
            val verdict = SubtitleVerifier.recordContent(url, text)
            if (!verdict.valid) continue
            val lang = verdict.language ?: "en"
            val base = if (lang == "en") "English" else (verdict.label ?: lang.uppercase())
            val label = "$base · SubDL" + if (out.isNotEmpty()) " #${out.size + 1}" else ""
            out.add(SubtitleTrack(url, label, lang, ext))
        }
        Logger.i("Subtitles", "SubDL found ${out.size} usable subtitle(s) for ${media.title}")
        if (out.isNotEmpty()) results[cacheKey] = out
        out
    }

    private fun getJson(url: String): JSONObject? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", UA)
        val code = conn.responseCode
        val body = (if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.use { it.readText() }
        conn.disconnect()
        if (code >= 400) Logger.w("Subtitles", "SubDL HTTP $code")
        body?.let { JSONObject(it) }
    } catch (_: Exception) {
        null
    }

    /** Downloads a SubDL zip and returns (file name, text) of the best subtitle inside. */
    private fun downloadAndPick(zipUrl: String, season: Int?, episode: Int?): Pair<String, String>? {
        val conn = URL(zipUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", UA)
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            return null
        }
        val entries = conn.inputStream.use { readZipSubtitles(it) }
        conn.disconnect()
        return pickEntry(entries, season, episode)
    }

    /** All .srt/.vtt files inside a zip, as (name, text). */
    internal fun readZipSubtitles(input: java.io.InputStream): List<Pair<String, String>> {
        val found = mutableListOf<Pair<String, String>>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                val lower = name.lowercase()
                if (!entry.isDirectory && !lower.contains("__macosx") &&
                    (lower.endsWith(".srt") || lower.endsWith(".vtt"))
                ) {
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (buf.size() < 5 * 1024 * 1024) {
                        val n = zip.read(chunk)
                        if (n <= 0) break
                        buf.write(chunk, 0, n)
                    }
                    found.add(name.substringAfterLast('/') to decode(buf.toByteArray()))
                }
                zip.closeEntry()
            }
        }
        return found
    }

    /** For season packs pick the matching episode; otherwise the biggest file (the full subtitle). */
    internal fun pickEntry(entries: List<Pair<String, String>>, season: Int?, episode: Int?): Pair<String, String>? {
        if (entries.isEmpty()) return null
        if (season != null && episode != null) {
            val patterns = listOf(
                Regex("(?i)s0*${season}[ ._-]*e0*${episode}(?!\\d)"),
                Regex("(?i)(?<!\\d)0*${season}x0*${episode}(?!\\d)"),
                Regex("(?i)(?:ep?|episode)[ ._-]*0*${episode}(?!\\d)")
            )
            for (p in patterns) {
                entries.firstOrNull { p.containsMatchIn(it.first) }?.let { return it }
            }
            // A single file in the zip is the episode we asked for
            if (entries.size == 1) return entries.first()
            return null
        }
        return entries.maxByOrNull { it.second.length }
    }

    /** Subtitle files are often not UTF-8 (older ones are Windows-1252). */
    private fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        val broken = utf8.count { it == '�' }
        return if (broken > 5) String(bytes, charset("windows-1252")) else utf8
    }
}
