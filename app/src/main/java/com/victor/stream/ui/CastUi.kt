package com.victor.stream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.victor.stream.cast.CastController
import com.victor.stream.domain.EpisodeRef
import com.victor.stream.domain.MediaItemModel
import com.victor.stream.ui.theme.AppTheme

/** Round cast button for the player's top bar. */
@Composable
fun CastButton(connected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = if (connected) Icons.Filled.CastConnected else Icons.Outlined.Cast,
            contentDescription = "Cast",
            tint = if (connected) AppTheme.c.accent else Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** "Cast to" sheet: lists Chromecast / Google TV devices found on the Wi-Fi. */
@Composable
fun CastDevicePickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        CastController.startDiscovery(context)
        onDispose { CastController.stopDiscovery() }
    }
    val routes = CastController.routes
    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF141414))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text("Cast to", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (routes.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = AppTheme.c.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Text("Looking for TVs on your Wi-Fi…", color = Color(0xFFD0D0D0), fontSize = 14.sp)
                }
                Text(
                    "Make sure your phone and the Chromecast / Google TV are on the same Wi-Fi.",
                    color = Color(0xFF8C8C8C),
                    fontSize = 12.sp
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 320.dp)) {
                    items(routes, key = { it.id }) { route ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1C1C1E))
                                .clickable {
                                    CastController.connect(route)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Outlined.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(route.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val desc = route.description
                                if (!desc.isNullOrBlank()) {
                                    Text(desc, color = Color(0xFF8C8C8C), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            Text(
                text = "Cancel",
                color = Color(0xFFB0B0B0),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

private fun castTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Full-screen remote shown on the phone while the video plays on the TV. */
@Composable
fun CastingOverlay(
    media: MediaItemModel,
    episode: EpisodeRef?,
    onStopCasting: () -> Unit
) {
    val t = AppTheme.c
    val state = CastController.state
    val deviceName = when (state) {
        is CastController.State.Connected -> state.deviceName
        is CastController.State.Connecting -> state.deviceName
        else -> "TV"
    }
    val connecting = state is CastController.State.Connecting
    val duration = CastController.durationMs
    val position = CastController.positionMs
    var dragValue by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = false) {}
    ) {
        AsyncImage(
            model = media.backdropUrl.ifBlank { media.posterUrl },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.35f,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0xE6000000))))
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.82f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CastConnected, contentDescription = null, tint = t.accent, modifier = Modifier.size(20.dp))
                Text(
                    text = if (connecting) "Connecting to $deviceName…" else "Casting to $deviceName",
                    color = Color(0xFFE0E0E0),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(media.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (episode != null) {
                Text(
                    "S${episode.seasonNumber} E${episode.episodeNumber} · ${episode.title}",
                    color = Color(0xFFB3B3B3),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!connecting) {
                Spacer(modifier = Modifier.height(6.dp))
                Slider(
                    value = dragValue ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { dragValue = it },
                    onValueChangeFinished = {
                        dragValue?.let { v -> if (duration > 0) CastController.seekTo((v * duration).toLong()) }
                        dragValue = null
                    },
                    enabled = duration > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = t.accent,
                        inactiveTrackColor = Color(0x40FFFFFF)
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val shown = dragValue?.let { (it * duration).toLong() } ?: position
                    Text(castTime(shown), color = Color(0xFFB3B3B3), fontSize = 12.sp)
                    Text(if (duration > 0) castTime(duration) else "--:--", color = Color(0xFFB3B3B3), fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    CastRoundButton(size = 52) {
                        CastController.seekBy(-10_000L)
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { CastController.togglePlay() }
                    ) {
                        if (CastController.isBuffering) {
                            CircularProgressIndicator(color = Color.Black, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                        } else {
                            Icon(
                                imageVector = if (CastController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play / pause",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    CastRoundButton(size = 52, forward = true) {
                        CastController.seekBy(10_000L)
                    }
                }
            } else {
                CircularProgressIndicator(color = t.accent, modifier = Modifier.padding(top = 10.dp))
            }

            CastController.lastError?.let { err ->
                Text(err, color = Color(0xFFFF6B6B), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Stop casting",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x33FFFFFF))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                    .clickable(onClick = onStopCasting)
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            )
            Text(
                text = "Keep this app open on the same Wi-Fi while casting.",
                color = Color(0xFF8C8C8C),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CastRoundButton(size: Int, forward: Boolean = false, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0x26FFFFFF))
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = if (forward) Icons.Filled.Forward10 else Icons.Filled.Replay10,
            contentDescription = if (forward) "Forward 10 seconds" else "Back 10 seconds",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}
