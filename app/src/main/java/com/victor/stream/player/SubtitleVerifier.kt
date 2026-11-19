package com.victor.stream.player

import com.victor.stream.core.Logger
import com.victor.stream.domain.SubtitleTrack
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Looks inside subtitle files before the player trusts them.
 *
 * Video pages often expose many subtitle files (every language, plus thumbnail
 * "storyboard" VTTs used for seek previews), and links rarely say which language
 * they are. Guessing from the link led to wrong subtitles being switched on.
 * This downloads the start of each file once and decides:
 *  - is it a real subtitle at all (timed cues with text, not image sprites)?
 *  - what language is the text actually in?
 * Results are cached by URL, and [apply] relabels / drops tracks accordingly.
 */
object SubtitleVerifier {

    data class Verdict(
        val valid: Boolean,
        val language: String?,
        val label: String?,
        /** True when the file couldn't be downloaded to check (blocked, timed out...). */
        val unreadable: Boolean = false
    )

    private val verdicts = ConcurrentHashMap<String, Verdict>()

    fun isVerified(url: String): Boolean = verdicts.containsKey(url)

    /** True only when the file was downloaded and its text is actually English. */
    fun isConfirmedEnglish(url: String): Boolean = verdicts[url]?.let { it.valid && it.language == "en" } == true

    /**
     * Fallback: the file couldn't be downloaded to check, but its link clearly says
     * English (e.g. ".../eng.vtt"). Better to show it than to show nothing.
     */
    fun isUnreadableButNamedEnglish(track: SubtitleTrack): Boolean =
        verdicts[track.url]?.unreadable == true && track.language.equals("en", ignoreCase = true)

    /** Relabels tracks we've checked and removes ones that aren't real subtitles. */
    fun apply(tracks: List<SubtitleTrack>): List<SubtitleTrack> = tracks.mapNotNull { track ->
        val v = verdicts[track.url] ?: return@mapNotNull track
        when {
            !v.valid -> null
            v.language != null && v.label != null -> track.copy(language = v.language, label = v.label)
            else -> track
        }
    }

    /** Downloads (the start of) a subtitle file and records what it really is. Blocking: call off the main thread. */
    fun verify(track: SubtitleTrack, headers: Map<String, String>): Verdict {
        verdicts[track.url]?.let { return it }
        val verdict = try {
            val conn = URL(track.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            headers.forEach { (k, v) ->
                if (!k.equals("Range", true) && !k.equals("Host", true) && !k.equals("Accept-Encoding", true)) {
                    conn.setRequestProperty(k, v)
                }
            }
            if (headers.keys.none { it.equals("User-Agent", true) }) {
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/131.0.0.0 Mobile Safari/537.36")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                // Couldn't read it: keep the track as-is rather than hide it
                conn.disconnect()
                Logger.w("Subtitles", "Couldn't check ${shortName(track.url)} (HTTP $code)")
                val v = Verdict(valid = true, language = null, label = null, unreadable = true)
                verdicts[track.url] = v
                return v
            }
            val bytes = conn.inputStream.use { input ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (buf.size() < 256 * 1024) {
                    val n = input.read(chunk)
                    if (n <= 0) break
                    buf.write(chunk, 0, n)
                }
                buf.toByteArray()
            }
            conn.disconnect()
            analyze(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            Logger.w("Subtitles", "Couldn't check ${shortName(track.url)} (${e.javaClass.simpleName})")
            Verdict(valid = true, language = null, label = null, unreadable = true)
        }
        verdicts[track.url] = verdict
        if (!verdict.unreadable) {
            Logger.i(
                "Subtitles",
                "Checked ${shortName(track.url)}: " +
                    (if (verdict.valid) "subtitle, language=${verdict.language ?: "unknown"}" else "NOT a subtitle (skipped)")
            )
        }
        return verdict
    }

    private fun shortName(url: String): String {
        val noQuery = url.substringBefore('?')
        val host = runCatching { URL(url).host }.getOrDefault("")
        return host + "/…/" + noQuery.substringAfterLast('/').take(40)
    }

    // ------------------------------------------------------------ analysis

    private val TIMESTAMP = Regex("""\d{1,2}:\d{2}(:\d{2})?[.,]\d{1,3}\s*-->""")
    private val TAGS = Regex("""<[^>]+>|\{[^}]+\}""")

    private val STOPWORDS: Map<String, Set<String>> = mapOf(
        "en" to setOf("the", "you", "and", "to", "a", "of", "i", "is", "it", "that", "what", "in", "me", "this",
            "we", "be", "your", "for", "don't", "i'm", "are", "have", "do", "not", "was", "my", "he", "she", "with"),
        "es" to setOf("que", "de", "no", "la", "el", "y", "es", "en", "lo", "por", "un", "una", "se", "los",
            "qué", "con", "para", "está", "pero", "las", "mi", "eso", "muy", "como", "yo"),
        "fr" to setOf("je", "de", "le", "la", "et", "vous", "pas", "est", "que", "les", "un", "une", "tu",
            "il", "ce", "qui", "c'est", "en", "ne", "on", "des", "pour", "mais", "j'ai"),
        "pt" to setOf("não", "que", "de", "o", "você", "é", "eu", "um", "uma", "se", "com", "para", "isso",
            "por", "mas", "do", "da", "está", "os", "ele", "ela", "muito"),
        "de" to setOf("ich", "und", "die", "der", "nicht", "sie", "das", "ist", "du", "ein", "es", "zu",
            "was", "wir", "mit", "den", "auf", "mir", "ja", "haben", "dich", "aber"),
        "it" to setOf("che", "non", "di", "il", "è", "la", "un", "per", "sono", "mi", "ho", "ti", "una",
            "questo", "come", "cosa", "ma", "lo", "bene", "se", "gli", "sei"),
        "tr" to setOf("bir", "ve", "bu", "ne", "için", "ben", "sen", "çok", "mi", "da", "de", "değil",
            "var", "o", "ama", "evet", "hayır", "gibi")
    )

    private val LANGUAGE_NAMES = mapOf(
        "en" to "English", "es" to "Spanish", "fr" to "French", "pt" to "Portuguese", "de" to "German",
        "it" to "Italian", "tr" to "Turkish", "ar" to "Arabic", "ru" to "Russian", "zh" to "Chinese",
        "ja" to "Japanese", "ko" to "Korean", "hi" to "Hindi", "th" to "Thai", "el" to "Greek", "he" to "Hebrew"
    )

    internal fun analyze(raw: String): Verdict {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n")
        val cues = TIMESTAMP.findAll(text).count()
        if (cues < 3) return Verdict(valid = false, language = null, label = null)

        // Keep only the spoken text lines
        val lines = text.lines().map { it.trim() }.filter { line ->
            line.isNotEmpty() &&
                !line.contains("-->") &&
                !line.startsWith("WEBVTT") &&
                !line.startsWith("NOTE") &&
                !line.startsWith("STYLE") &&
                line.toIntOrNull() == null
        }
        if (lines.isEmpty()) return Verdict(valid = false, language = null, label = null)

        // Thumbnail / storyboard VTTs: cue text is an image reference
        val imageLines = lines.count {
            it.contains("#xywh=") || Regex("""\.(jpe?g|png|webp)(\?|#|$)""", RegexOption.IGNORE_CASE).containsMatchIn(it)
        }
        if (imageLines * 2 >= lines.size) return Verdict(valid = false, language = null, label = null)

        val sample = lines.take(400).joinToString(" ") { TAGS.replace(it, " ") }
        val lang = detectLanguage(sample)
        return Verdict(valid = true, language = lang, label = lang?.let { LANGUAGE_NAMES[it] ?: it.uppercase() })
    }

    private fun detectLanguage(sample: String): String? {
        // Non-Latin scripts are easy to tell apart by their characters
        var letters = 0
        val scripts = HashMap<String, Int>()
        for (ch in sample) {
            if (!ch.isLetter()) continue
            letters++
            val key = when (Character.UnicodeScript.of(ch.code)) {
                Character.UnicodeScript.ARABIC -> "ar"
                Character.UnicodeScript.CYRILLIC -> "ru"
                Character.UnicodeScript.HAN -> "zh"
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> "ja"
                Character.UnicodeScript.HANGUL -> "ko"
                Character.UnicodeScript.DEVANAGARI -> "hi"
                Character.UnicodeScript.THAI -> "th"
                Character.UnicodeScript.GREEK -> "el"
                Character.UnicodeScript.HEBREW -> "he"
                else -> null
            } ?: continue
            scripts[key] = (scripts[key] ?: 0) + 1
        }
        if (letters > 0) {
            // Japanese text mixes kana with Han characters
            val ja = scripts["ja"] ?: 0
            if (ja * 10 > letters) return "ja"
            val top = scripts.maxByOrNull { it.value }
            if (top != null && top.value * 2 > letters) return top.key
        }

        // Latin-script languages: count very common words
        val words = sample.lowercase()
            .split(Regex("""[^\p{L}']+"""))
            .filter { it.isNotBlank() }
        if (words.size < 20) return null
        var best: String? = null
        var bestScore = 0.0
        for ((code, set) in STOPWORDS) {
            val hits = words.count { it in set }
            val score = hits.toDouble() / words.size
            if (score > bestScore) {
                bestScore = score
                best = code
            }
        }
        return if (bestScore >= 0.08) best else null
    }
}
