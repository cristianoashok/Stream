package com.victor.stream.ui

import android.content.Context
import com.victor.stream.BuildConfig
import com.victor.stream.domain.MediaItemModel
import com.victor.stream.domain.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Small UI-side extras that need no changes to the data/streaming engines:
 * recent-search storage and lightweight TMDB metadata lookups
 * (recommendations, trailers).
 */
object RecentSearchStore {
    private const val PREFS = "stream_ui_extras"
    private const val KEY = "recent_searches"
    private const val MAX = 8

    fun get(context: Context): List<String> = try {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Throwable) {
        emptyList()
    }

    fun add(context: Context, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return get(context)
        val updated = (listOf(q) + get(context).filter { !it.equals(q, ignoreCase = true) }).take(MAX)
        save(context, updated)
        return updated
    }

    fun clear(context: Context): List<String> {
        save(context, emptyList())
        return emptyList()
    }

    private fun save(context: Context, list: List<String>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, arr.toString())
                .apply()
        } catch (_: Throwable) {
        }
    }
}

object TmdbExtras {

    private fun httpGetJson(url: String): JSONObject? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Accept", "application/json")
        }
        if (conn.responseCode in 200..299) {
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } else null
    } catch (_: Throwable) {
        null
    }

    private fun tmdbPathFor(media: MediaItemModel): Pair<String, Int>? {
        val id = media.tmdbId ?: if (!media.isAnime) media.id else return null
        return when {
            media.isSeries -> "tv" to id
            else -> "movie" to id
        }
    }

    /** "Because you watched X" — TMDB's recommendations for one title. */
    suspend fun fetchRecommendations(media: MediaItemModel): List<MediaItemModel> =
        withContext(Dispatchers.IO) {
            val (path, id) = tmdbPathFor(media) ?: return@withContext emptyList()
            val key = URLEncoder.encode(BuildConfig.TMDB_API_KEY, "UTF-8")
            val json = httpGetJson("https://api.themoviedb.org/3/$path/$id/recommendations?api_key=$key")
                ?: return@withContext emptyList()
            val results = json.optJSONArray("results") ?: return@withContext emptyList()
            val out = mutableListOf<MediaItemModel>()
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                val poster = o.optString("poster_path", "")
                if (poster.isBlank() || poster == "null") continue
                val mediaType = o.optString("media_type", path)
                val isTvItem = mediaType == "tv"
                val title = if (isTvItem) o.optString("name") else o.optString("title")
                if (title.isBlank()) continue
                val date = if (isTvItem) o.optString("first_air_date", "") else o.optString("release_date", "")
                out.add(
                    MediaItemModel(
                        id = o.optInt("id"),
                        kind = if (isTvItem) MediaKind.TV else MediaKind.MOVIE,
                        title = title,
                        overview = o.optString("overview", ""),
                        posterUrl = "https://image.tmdb.org/t/p/w500$poster",
                        rating = o.optDouble("vote_average", 0.0),
                        releaseYear = if (date.length >= 4) date.take(4) else "",
                        backdropUrl = o.optString("backdrop_path", "").let {
                            if (it.isBlank() || it == "null") "" else "https://image.tmdb.org/t/p/w780$it"
                        },
                        tmdbId = o.optInt("id")
                    )
                )
                if (out.size >= 15) break
            }
            out
        }

    /** YouTube key of the best trailer for a title, or null. */
    suspend fun fetchTrailerKey(media: MediaItemModel): String? = withContext(Dispatchers.IO) {
        val (path, id) = tmdbPathFor(media) ?: return@withContext null
        val key = URLEncoder.encode(BuildConfig.TMDB_API_KEY, "UTF-8")
        val json = httpGetJson("https://api.themoviedb.org/3/$path/$id/videos?api_key=$key")
            ?: return@withContext null
        val results = json.optJSONArray("results") ?: return@withContext null
        var fallback: String? = null
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            if (!o.optString("site").equals("YouTube", ignoreCase = true)) continue
            val k = o.optString("key", "")
            if (k.isBlank()) continue
            if (o.optString("type").equals("Trailer", ignoreCase = true)) {
                if (o.optBoolean("official", false)) return@withContext k
                if (fallback == null) fallback = k
            } else if (fallback == null && o.optString("type").equals("Teaser", ignoreCase = true)) {
                fallback = k
            }
        }
        fallback
    }
}
