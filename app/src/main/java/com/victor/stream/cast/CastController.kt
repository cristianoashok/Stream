package com.victor.stream.cast

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.TextTrackStyle
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.victor.stream.domain.PlaybackSession
import com.victor.stream.domain.StreamMimeType

/**
 * Everything about casting, in one place, as Compose state the player screen can read:
 * finding TVs, connecting, sending the video (through [CastRelay]) and remote controls.
 * All functions must be called on the main thread.
 */
object CastController {

    sealed interface State {
        data object Idle : State
        data class Connecting(val deviceName: String) : State
        data class Connected(val deviceName: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set
    var routes by mutableStateOf<List<MediaRouter.RouteInfo>>(emptyList())
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)

    /** Id of the PlaybackSession currently playing on the TV (null = nothing loaded yet). */
    var loadedSessionId by mutableStateOf<String?>(null)
        private set

    private var castContext: CastContext? = null
    private var router: MediaRouter? = null
    private var attachedClient: RemoteMediaClient? = null

    private val selector: MediaRouteSelector by lazy {
        MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            )
            .build()
    }

    /** False when Google Play services / Cast isn't available on this phone. */
    fun isAvailable(context: Context): Boolean = ensure(context) != null

    private fun ensure(context: Context): CastContext? {
        val existing = castContext
        if (existing != null) return existing
        val ctx: CastContext
        try {
            @Suppress("DEPRECATION")
            ctx = CastContext.getSharedInstance(context.applicationContext)
        } catch (_: Throwable) {
            return null
        }
        castContext = ctx
        router = MediaRouter.getInstance(context.applicationContext)
        ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        ctx.sessionManager.currentCastSession?.let { if (it.isConnected) onConnected(it) }
        return ctx
    }

    // ------------------------------------------------------------ discovery

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes()
    }

    fun startDiscovery(context: Context) {
        ensure(context) ?: return
        router?.addCallback(
            selector,
            routerCallback,
            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
        )
        refreshRoutes()
    }

    fun stopDiscovery() {
        router?.removeCallback(routerCallback)
    }

    private fun refreshRoutes() {
        val r = router ?: return
        routes = r.routes.filter { !it.isDefault && it.isEnabled && it.matchesSelector(selector) }
    }

    // ------------------------------------------------------------ session

    fun connect(route: MediaRouter.RouteInfo) {
        lastError = null
        state = State.Connecting(route.name)
        route.select()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStarted(session: CastSession, sessionId: String) = onConnected(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            lastError = "Couldn't connect to the TV"
            onDisconnected()
        }
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionEnded(session: CastSession, error: Int) = onDisconnected()
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onConnected(session)
        override fun onSessionResumeFailed(session: CastSession, error: Int) = onDisconnected()
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    private val progressListener = RemoteMediaClient.ProgressListener { progress, duration ->
        positionMs = progress
        if (duration > 0) durationMs = duration
    }

    private val remoteCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val c = attachedClient ?: return
            isPlaying = c.isPlaying
            isBuffering = c.isBuffering
            val status = c.mediaStatus
            if (status != null &&
                status.playerState == MediaStatus.PLAYER_STATE_IDLE &&
                status.idleReason == MediaStatus.IDLE_REASON_ERROR
            ) {
                lastError = "The TV couldn't play this stream. Try another server."
            }
        }
    }

    private fun onConnected(session: CastSession) {
        state = State.Connected(session.castDevice?.friendlyName ?: "TV")
        val client = session.remoteMediaClient ?: return
        if (attachedClient !== client) {
            detachClient()
            client.registerCallback(remoteCallback)
            client.addProgressListener(progressListener, 1000L)
            attachedClient = client
        }
    }

    private fun onDisconnected() {
        detachClient()
        CastRelay.stop()
        loadedSessionId = null
        isPlaying = false
        isBuffering = false
        state = State.Idle
    }

    private fun detachClient() {
        attachedClient?.let {
            runCatching { it.unregisterCallback(remoteCallback) }
            runCatching { it.removeProgressListener(progressListener) }
        }
        attachedClient = null
    }

    private fun client(): RemoteMediaClient? = castContext?.sessionManager?.currentCastSession?.remoteMediaClient

    // ------------------------------------------------------------ playback

    /** Sends the current video to the TV, starting at [startMs]. False if it couldn't start. */
    fun load(session: PlaybackSession, startMs: Long): Boolean {
        val client = client() ?: return false
        val stream = session.stream
        CastRelay.start(stream.headers, stream.subtitles) ?: run {
            lastError = "Couldn't start casting. Make sure your phone is on Wi-Fi."
            return false
        }
        val url = CastRelay.proxied(stream.streamUrl) ?: run {
            lastError = "This stream can't be cast."
            return false
        }
        val lower = stream.streamUrl.lowercase()
        val contentType = when {
            stream.mimeType == StreamMimeType.HLS || lower.contains(".m3u8") -> "application/x-mpegURL"
            stream.mimeType == StreamMimeType.DASH || lower.contains(".mpd") -> "application/dash+xml"
            else -> "video/mp4"
        }

        val media = session.media
        val metadata = MediaMetadata(
            if (media.isSeries) MediaMetadata.MEDIA_TYPE_TV_SHOW else MediaMetadata.MEDIA_TYPE_MOVIE
        ).apply {
            putString(MediaMetadata.KEY_TITLE, media.title)
            session.episode?.let { ep ->
                putString(MediaMetadata.KEY_SUBTITLE, "S${ep.seasonNumber} E${ep.episodeNumber} · ${ep.title}")
            }
            val image = media.backdropUrl.ifBlank { media.posterUrl }
            if (image.isNotBlank()) addImage(WebImage(Uri.parse(image)))
        }

        // Only real subtitle files, with their checked language (same as the phone player)
        val subs = com.victor.stream.player.SubtitleVerifier.apply(stream.subtitles)
        val tracks = subs.mapIndexedNotNull { i, sub ->
            val subIndex = stream.subtitles.indexOfFirst { it.url == sub.url }
            val subUrl = CastRelay.subtitleUrl(subIndex) ?: return@mapIndexedNotNull null
            MediaTrack.Builder((subIndex + 1).toLong(), MediaTrack.TYPE_TEXT)
                .setName(sub.label)
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(subUrl)
                .setContentType("text/vtt")
                .setLanguage(sub.language)
                .build()
        }

        // Same subtitle look as the phone: white text, black outline, no box
        val textStyle = TextTrackStyle().apply {
            foregroundColor = 0xFFFFFFFF.toInt()
            backgroundColor = 0x00000000
            windowType = TextTrackStyle.WINDOW_TYPE_NONE
            edgeType = TextTrackStyle.EDGE_TYPE_OUTLINE
            edgeColor = 0xFF000000.toInt()
        }
        val info = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(metadata)
            .setMediaTracks(tracks)
            .setTextTrackStyle(textStyle)
            .build()

        // Same default as the phone player: confirmed-English subtitles on when available
        val englishSub = subs.firstOrNull {
            com.victor.stream.player.SubtitleVerifier.isConfirmedEnglish(it.url)
        }
        val english = if (englishSub == null) -1 else stream.subtitles.indexOfFirst { it.url == englishSub.url }
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setAutoplay(true)
            .setCurrentTime(startMs.coerceAtLeast(0L))
            .apply { if (english >= 0) setActiveTrackIds(longArrayOf((english + 1).toLong())) }
            .build()

        lastError = null
        client.load(request)
        loadedSessionId = session.id
        positionMs = startMs
        return true
    }

    fun togglePlay() {
        client()?.togglePlayback()
    }

    fun seekBy(deltaMs: Long) {
        val c = client() ?: return
        var target = (c.approximateStreamPosition + deltaMs).coerceAtLeast(0L)
        if (durationMs > 0) target = target.coerceAtMost(durationMs)
        seekTo(target)
    }

    fun seekTo(positionMs: Long) {
        val c = client() ?: return
        c.seek(MediaSeekOptions.Builder().setPosition(positionMs.coerceAtLeast(0L)).build())
        this.positionMs = positionMs
    }

    /** Stops casting and returns where the TV was, so the phone can carry on from there. */
    fun stop(): Long {
        val pos = client()?.approximateStreamPosition?.takeIf { it > 0 } ?: positionMs
        runCatching { castContext?.sessionManager?.endCurrentSession(true) }
        onDisconnected()
        return pos
    }
}
