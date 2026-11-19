package com.victor.stream.cast

import android.util.Base64
import com.victor.stream.domain.SubtitleTrack
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * The "phone relay" for casting.
 *
 * A Chromecast can't send the special request headers (Referer, Origin, User-Agent…)
 * that many stream servers require, and it needs CORS headers on every response.
 * So while casting, the phone runs this tiny HTTP server on the Wi-Fi network:
 * the TV asks the phone, the phone fetches from the real server with the right
 * headers and passes the bytes back with CORS added.
 *
 *  - Every URL carries a random token, so other devices can't use the relay.
 *  - HLS playlists (.m3u8) are rewritten so every segment/key/sub-playlist also
 *    goes through the relay.
 *  - Byte ranges are passed through (needed for MP4 seeking).
 *  - Subtitles are served as WebVTT (SRT is converted), which is what Cast needs.
 *
 * Only runs while a cast session is active; [stop] shuts it down.
 */
object CastRelay {
    private const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private const val MAX_PLAYLIST_BYTES = 8 * 1024 * 1024
    private val B64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    private val URI_ATTR = Regex("URI=\"([^\"]+)\"")
    private val SRT_TIME = Regex("(\\d{1,2}:\\d{2}:\\d{2}),(\\d{3})")

    @Volatile private var server: ServerSocket? = null
    @Volatile private var pool: ExecutorService? = null
    @Volatile private var token: String = ""
    @Volatile private var upstreamHeaders: Map<String, String> = emptyMap()
    @Volatile private var subtitleSources: List<SubtitleTrack> = emptyList()

    /** e.g. http://192.168.1.20:41233/t/ab12cd34…  (null when not running) */
    @Volatile var baseUrl: String? = null
        private set

    /** Starts (or restarts) the relay for one stream. Returns the base URL, or null if not on Wi-Fi. */
    @Synchronized
    fun start(headers: Map<String, String>, subtitles: List<SubtitleTrack>): String? {
        stop()
        val ip = lanIpv4() ?: return null
        return try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(0)) // all interfaces, any free port
            val executor = Executors.newCachedThreadPool()
            server = ss
            pool = executor
            token = UUID.randomUUID().toString().replace("-", "").take(20)
            upstreamHeaders = headers.filterKeys {
                it.lowercase() !in setOf("host", "range", "accept-encoding", "content-length", "connection")
            }
            subtitleSources = subtitles
            baseUrl = "http://$ip:${ss.localPort}/t/$token"
            thread(name = "cast-relay", isDaemon = true) {
                while (!ss.isClosed) {
                    val client = try {
                        ss.accept()
                    } catch (_: Exception) {
                        break
                    }
                    try {
                        executor.execute { handle(client) }
                    } catch (_: Exception) {
                        runCatching { client.close() }
                    }
                }
            }
            baseUrl
        } catch (_: Exception) {
            stop()
            null
        }
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
        runCatching { pool?.shutdownNow() }
        pool = null
        baseUrl = null
        token = ""
    }

    /** Relay URL for an upstream URL; the path is kept so relative links still resolve. */
    fun proxied(upstream: String): String? {
        val base = baseUrl ?: return null
        val u = runCatching { URL(upstream) }.getOrNull() ?: return null
        if (u.protocol != "http" && u.protocol != "https") return null
        val origin = "${u.protocol}://${u.authority}"
        val enc = Base64.encodeToString(origin.toByteArray(Charsets.UTF_8), B64_FLAGS)
        val path = u.path.ifEmpty { "/" }
        val query = u.query?.let { "?$it" } ?: ""
        return "$base/$enc$path$query"
    }

    fun subtitleUrl(index: Int): String? = baseUrl?.let { "$it/sub/$index.vtt" }

    // ---------------------------------------------------------------- server

    private fun handle(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 30_000
                val input = BufferedInputStream(s.getInputStream())
                val out = BufferedOutputStream(s.getOutputStream(), 64 * 1024)
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                val target = parts[1]
                val reqHeaders = HashMap<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val i = line.indexOf(':')
                    if (i > 0) reqHeaders[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
                }
                if (method == "OPTIONS") {
                    writeHead(out, 204, "No Content", emptyMap(), 0L)
                    out.flush()
                    return
                }
                val currentToken = token
                val prefix = "/t/$currentToken/"
                if (currentToken.isEmpty() || !target.startsWith(prefix)) {
                    writeText(out, 403, "Forbidden", "forbidden")
                    return
                }
                val rest = target.substring(prefix.length)
                try {
                    if (rest.startsWith("sub/")) {
                        serveSubtitle(rest, out)
                    } else {
                        serveProxy(rest, method, reqHeaders["range"], out)
                    }
                } catch (_: Exception) {
                    runCatching { writeText(out, 502, "Bad Gateway", "relay error") }
                }
            }
        } catch (_: Exception) {
            // client went away - nothing to do
        }
    }

    private fun serveProxy(rest: String, method: String, range: String?, out: OutputStream) {
        val slash = rest.indexOf('/')
        val encOrigin = if (slash < 0) rest.substringBefore('?') else rest.substring(0, slash)
        val pathAndQuery = if (slash < 0) "/" else rest.substring(slash)
        val origin = String(Base64.decode(encOrigin, B64_FLAGS), Charsets.UTF_8)
        var url = origin + pathAndQuery

        var conn = open(url, method, range)
        var redirects = 0
        while (conn.responseCode in 300..399 && redirects < 6) {
            val location = conn.getHeaderField("Location") ?: break
            conn.disconnect()
            url = URL(URL(url), location).toString()
            conn = open(url, method, range)
            redirects++
        }

        val code = conn.responseCode
        val contentType = conn.contentType ?: ""
        val raw: InputStream = (if (code >= 400) conn.errorStream else conn.inputStream)
            ?: ByteArrayInputStream(ByteArray(0))
        val body = BufferedInputStream(raw, 64 * 1024)

        val isPlaylist = code < 400 && method != "HEAD" && (
            contentType.contains("mpegurl", ignoreCase = true) ||
                URL(url).path.endsWith(".m3u8", ignoreCase = true) ||
                startsWith(body, "#EXTM3U")
            )
        if (isPlaylist) {
            val text = readLimited(body, MAX_PLAYLIST_BYTES).toString(Charsets.UTF_8)
            val rewritten = rewritePlaylist(text, url).toByteArray(Charsets.UTF_8)
            writeHead(
                out, 200, "OK",
                mapOf("Content-Type" to "application/vnd.apple.mpegurl", "Cache-Control" to "no-cache"),
                rewritten.size.toLong()
            )
            out.write(rewritten)
            out.flush()
            conn.disconnect()
            return
        }

        val headers = LinkedHashMap<String, String>()
        if (contentType.isNotEmpty()) headers["Content-Type"] = contentType
        conn.getHeaderField("Content-Range")?.let { headers["Content-Range"] = it }
        conn.getHeaderField("Accept-Ranges")?.let { headers["Accept-Ranges"] = it }
        val length = conn.getHeaderField("Content-Length")?.toLongOrNull()
        writeHead(out, code, conn.responseMessage ?: "OK", headers, length)
        if (method != "HEAD") body.copyTo(out, 64 * 1024)
        out.flush()
        conn.disconnect()
    }

    private fun open(url: String, method: String, range: String?): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.requestMethod = if (method == "HEAD") "HEAD" else "GET"
        upstreamHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (upstreamHeaders.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            conn.setRequestProperty("User-Agent", DEFAULT_UA)
        }
        conn.setRequestProperty("Accept-Encoding", "identity")
        if (range != null) conn.setRequestProperty("Range", range)
        return conn
    }

    /** Points every URI in an HLS playlist (segments, keys, maps, variants) back at the relay. */
    private fun rewritePlaylist(text: String, playlistUrl: String): String {
        val base = URL(playlistUrl)
        return text.lines().joinToString("\n") { rawLine ->
            val line = rawLine.trimEnd('\r')
            when {
                line.isBlank() -> line
                line.startsWith("#") -> URI_ATTR.replace(line) { m ->
                    val original = m.groupValues[1]
                    val abs = runCatching { URL(base, original).toString() }.getOrNull()
                    "URI=\"" + (abs?.let { proxied(it) } ?: original) + "\""
                }
                else -> {
                    val abs = runCatching { URL(base, line.trim()).toString() }.getOrNull()
                    abs?.let { proxied(it) } ?: line
                }
            }
        }
    }

    private fun serveSubtitle(rest: String, out: OutputStream) {
        val index = rest.removePrefix("sub/").substringBefore('.').toIntOrNull()
        val track = index?.let { subtitleSources.getOrNull(it) }
        if (track == null) {
            writeText(out, 404, "Not Found", "no such subtitle")
            return
        }
        val text = if (track.url.startsWith("file:")) {
            // Backup subtitles (SubDL) are unzipped into the app's cache folder
            java.io.File(java.net.URI(track.url)).readText(Charsets.UTF_8)
        } else {
            val conn = URL(track.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", DEFAULT_UA)
            val body = conn.inputStream.use { readLimited(it, 4 * 1024 * 1024) }.toString(Charsets.UTF_8)
            conn.disconnect()
            body
        }
        val vtt = toWebVtt(text).toByteArray(Charsets.UTF_8)
        writeHead(out, 200, "OK", mapOf("Content-Type" to "text/vtt; charset=utf-8"), vtt.size.toLong())
        out.write(vtt)
        out.flush()
    }

    private fun toWebVtt(input: String): String {
        val text = input.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        if (text.trimStart().startsWith("WEBVTT")) return text
        val body = text.lines().joinToString("\n") { line ->
            if (line.contains("-->")) SRT_TIME.replace(line) { m -> "${m.groupValues[1]}.${m.groupValues[2]}" } else line
        }
        return "WEBVTT\n\n$body"
    }

    // ---------------------------------------------------------------- helpers

    private fun writeHead(out: OutputStream, code: Int, message: String, headers: Map<String, String>, length: Long?) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(code).append(' ').append(message.ifBlank { "OK" }).append("\r\n")
        headers.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("Access-Control-Allow-Origin: *\r\n")
        sb.append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
        sb.append("Access-Control-Allow-Headers: *, Range, Content-Type\r\n")
        sb.append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n")
        if (length != null && length >= 0) sb.append("Content-Length: ").append(length).append("\r\n")
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun writeText(out: OutputStream, code: Int, message: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writeHead(out, code, message, mapOf("Content-Type" to "text/plain"), bytes.size.toLong())
        out.write(bytes)
        out.flush()
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString("ISO-8859-1")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 16 * 1024) break
        }
        return buf.toString("ISO-8859-1")
    }

    private fun startsWith(input: BufferedInputStream, prefix: String): Boolean {
        val want = prefix.toByteArray(Charsets.US_ASCII)
        input.mark(want.size + 8)
        val got = ByteArray(want.size)
        var read = 0
        while (read < want.size) {
            val n = input.read(got, read, want.size - read)
            if (n <= 0) break
            read += n
        }
        input.reset()
        return read == want.size && got.contentEquals(want)
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < max) {
            val n = input.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** The phone's Wi-Fi address on the local network (prefers wlan interfaces). */
    private fun lanIpv4(): String? {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull().orEmpty()
            .filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual }.getOrDefault(false) }
            .sortedBy {
                when {
                    it.name.startsWith("wlan") -> 0
                    it.name.startsWith("eth") -> 1
                    else -> 2
                }
            }
        for (ni in interfaces) {
            for (addr in ni.inetAddresses.toList()) {
                if (addr is Inet4Address && addr.isSiteLocalAddress) return addr.hostAddress
            }
        }
        return null
    }
}
