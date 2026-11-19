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
import java.util.concurrent.ConcurrentHashMap

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

    /** Turns a TMDB "results" array into app models (skips items without posters). */
    private fun parseResults(results: JSONArray, defaultType: String, limit: Int = 40): List<MediaItemModel> {
        val out = mutableListOf<MediaItemModel>()
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val poster = o.optString("poster_path", "")
            if (poster.isBlank() || poster == "null") continue
            val mediaType = o.optString("media_type", defaultType).ifBlank { defaultType }
            val isTvItem = mediaType == "tv"
            val title = if (isTvItem) o.optString("name") else o.optString("title")
            if (title.isBlank()) continue
            val date = if (isTvItem) o.optString("first_air_date", "") else o.optString("release_date", "")
            val genres = o.optJSONArray("genre_ids")
            out.add(
                MediaItemModel(
                    id = o.optInt("id"),
                    kind = if (isTvItem) MediaKind.TV else MediaKind.MOVIE,
                    title = title,
                    overview = o.optString("overview", ""),
                    posterUrl = "https://image.tmdb.org/t/p/w500$poster",
                    rating = o.optDouble("vote_average", 0.0),
                    releaseYear = if (date.length >= 4) date.take(4) else "",
                    genreIds = if (genres == null) emptyList() else (0 until genres.length()).map { genres.optInt(it) },
                    originalLanguage = o.optString("original_language", "en"),
                    backdropUrl = o.optString("backdrop_path", "").let {
                        if (it.isBlank() || it == "null") "" else "https://image.tmdb.org/t/p/w780$it"
                    },
                    tmdbId = o.optInt("id")
                )
            )
            if (out.size >= limit) break
        }
        return out
    }

    private fun apiKey(): String = URLEncoder.encode(BuildConfig.TMDB_API_KEY, "UTF-8")

    /** "Because you watched X" — TMDB's recommendations for one title. */
    suspend fun fetchRecommendations(media: MediaItemModel): List<MediaItemModel> =
        withContext(Dispatchers.IO) {
            val (path, id) = tmdbPathFor(media) ?: return@withContext emptyList()
            val json = httpGetJson("https://api.themoviedb.org/3/$path/$id/recommendations?api_key=${apiKey()}")
                ?: return@withContext emptyList()
            val results = json.optJSONArray("results") ?: return@withContext emptyList()
            parseResults(results, path, limit = 15)
        }

    /**
     * TMDB discover with optional filters. [extra] holds the "what" (company,
     * provider or genre), year/rating are the user's filters.
     */
    suspend fun discover(
        isTv: Boolean,
        extra: String,
        page: Int = 1,
        year: Int? = null,
        minRating: Double? = null
    ): List<MediaItemModel> = withContext(Dispatchers.IO) {
        val type = if (isTv) "tv" else "movie"
        val sb = StringBuilder("https://api.themoviedb.org/3/discover/$type?api_key=${apiKey()}")
        sb.append("&sort_by=popularity.desc&include_adult=false&page=$page")
        sb.append(extra)
        if (year != null) sb.append(if (isTv) "&first_air_date_year=$year" else "&primary_release_year=$year")
        if (minRating != null) sb.append("&vote_average.gte=$minRating&vote_count.gte=150")
        val json = httpGetJson(sb.toString()) ?: return@withContext emptyList()
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        parseResults(results, type)
    }

    suspend fun discoverBrand(brand: Brand, isTv: Boolean, page: Int, year: Int?, minRating: Double?) =
        discover(isTv, brand.discoverParams(), page, year, minRating)

    /** TV uses different ids for a few combined genres; null = no TV equivalent. */
    fun tvGenreFor(movieGenreId: Int): Int? = when (movieGenreId) {
        28, 12 -> 10759 // Action & Adventure
        878, 14 -> 10765 // Sci-Fi & Fantasy
        10752 -> 10768 // War & Politics
        16, 35, 80, 99, 18, 10751, 9648, 37 -> movieGenreId
        else -> null
    }

    suspend fun discoverGenre(movieGenreId: Int, isTv: Boolean, page: Int, year: Int?, minRating: Double?): List<MediaItemModel> {
        val gid = if (isTv) tvGenreFor(movieGenreId) ?: return emptyList() else movieGenreId
        return discover(isTv, "&with_genres=$gid", page, year, minRating)
    }

    private val coverCache = ConcurrentHashMap<String, String>()

    fun cachedBrandCover(brand: Brand): String? = coverCache[brand.key]

    /** A backdrop from one of the brand's most popular titles, used as its tile picture. */
    suspend fun brandCover(brand: Brand): String? {
        coverCache[brand.key]?.let { return it }
        val list = discoverBrand(brand, isTv = brand.kind == BrandKind.PROVIDER, page = 1, year = null, minRating = null)
            .ifEmpty { discoverBrand(brand, isTv = brand.kind != BrandKind.PROVIDER, page = 1, year = null, minRating = null) }
        val pick = list.firstOrNull { it.backdropUrl.isNotBlank() }?.backdropUrl ?: return null
        coverCache[brand.key] = pick
        return pick
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

enum class BrandKind { STUDIO, PROVIDER }

/** A studio (TMDB production companies) or streaming service (TMDB watch providers, US). */
data class Brand(
    val key: String,
    val name: String,
    val kind: BrandKind,
    /** TMDB ids, OR-joined with "|" (some brands have several). */
    val ids: String,
    /** Brand colour, ARGB. */
    val color: Long
) {
    fun discoverParams(): String = when (kind) {
        BrandKind.STUDIO -> "&with_companies=$ids"
        BrandKind.PROVIDER -> "&with_watch_providers=$ids&watch_region=US"
    }
}

object Brands {
    val studios = listOf(
        Brand("marvel", "Marvel", BrandKind.STUDIO, "420", 0xFFE23636),
        Brand("dc", "DC", BrandKind.STUDIO, "9993|128064|429", 0xFF0476F2),
        Brand("pixar", "Pixar", BrandKind.STUDIO, "3", 0xFF2A7FD4),
        Brand("disney", "Disney", BrandKind.STUDIO, "2", 0xFF113CCF),
        Brand("lucasfilm", "Lucasfilm", BrandKind.STUDIO, "1", 0xFF6B6B6B),
        Brand("warner", "Warner Bros.", BrandKind.STUDIO, "174", 0xFF0A64C8),
        Brand("universal", "Universal", BrandKind.STUDIO, "33", 0xFF35506B),
        Brand("ghibli", "Studio Ghibli", BrandKind.STUDIO, "10342", 0xFF3E8E9C),
        Brand("a24", "A24", BrandKind.STUDIO, "41077", 0xFF444444),
        Brand("legendary", "Legendary", BrandKind.STUDIO, "923", 0xFF8C6A2E)
    )
    val providers = listOf(
        Brand("netflix", "Netflix", BrandKind.PROVIDER, "8", 0xFFE50914),
        Brand("prime", "Prime Video", BrandKind.PROVIDER, "9", 0xFF00A8E1),
        Brand("disneyplus", "Disney+", BrandKind.PROVIDER, "337", 0xFF113CCF),
        Brand("max", "Max", BrandKind.PROVIDER, "1899|384", 0xFF3D3DD8),
        Brand("appletv", "Apple TV+", BrandKind.PROVIDER, "350", 0xFF555555),
        Brand("hulu", "Hulu", BrandKind.PROVIDER, "15", 0xFF1CB866),
        Brand("paramount", "Paramount+", BrandKind.PROVIDER, "531", 0xFF0064FF),
        Brand("peacock", "Peacock", BrandKind.PROVIDER, "386|387", 0xFFD4A20A),
        Brand("crunchyroll", "Crunchyroll", BrandKind.PROVIDER, "283", 0xFFF47521)
    )
    val all = studios + providers
    fun byKey(key: String?): Brand? = all.firstOrNull { it.key == key }
}
