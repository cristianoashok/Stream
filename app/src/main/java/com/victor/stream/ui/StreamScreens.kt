package com.victor.stream.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import coil.compose.AsyncImage
import com.victor.stream.ui.theme.AppTheme
import com.victor.stream.core.DebugCaptureCard
import com.victor.stream.data.GENRE_MAP
import com.victor.stream.data.PrefsStore
import com.victor.stream.di.ServiceLocator
import com.victor.stream.domain.EpisodeRef
import com.victor.stream.domain.MediaItemModel
import com.victor.stream.domain.PlayOrigin
import com.victor.stream.domain.PlaybackSession
import com.victor.stream.domain.ResolverEvent
import com.victor.stream.domain.SeasonInfo
import com.victor.stream.domain.WatchProgressEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// ==========================================
// 1. SHARED UI COMPONENTS (PosterCard, Carousel, ResolvingOverlay)
// ==========================================

/**
 * Clean "finding a server" loader: a ring that fills as servers are tried,
 * the "2 / 12" count in the middle, one short line of text and Cancel.
 * The detailed status text from the resolver is reduced to just that.
 */
@Composable
fun ResolvingOverlay(
    statusText: String,
    onCancel: () -> Unit
) {
    val t = AppTheme.c
    val counter = remember(statusText) { Regex("\\((\\d+)/(\\d+)\\)").find(statusText) }
    val current = counter?.groupValues?.get(1)?.toIntOrNull()
    val total = counter?.groupValues?.get(2)?.toIntOrNull()
    val episode = remember(statusText) {
        Regex("S(\\d+):E(\\d+)").find(statusText)?.let { "Season ${it.groupValues[1]} \u00B7 Episode ${it.groupValues[2]}" }
    }
    val headline = when {
        statusText.startsWith("Switching", ignoreCase = true) -> "Switching server"
        else -> "Finding a server"
    }
    val progress by animateFloatAsState(
        targetValue = if (current != null && total != null && total > 0) current.toFloat() / total else 0f,
        animationSpec = tween(450),
        label = "resolveProgress"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
                // Faint track + spinning arc, plus a ring that fills per server tried
                CircularProgressIndicator(
                    progress = { 1f },
                    color = Color(0x1FFFFFFF),
                    strokeWidth = 4.dp,
                    modifier = Modifier.fillMaxSize()
                )
                if (total != null) {
                    CircularProgressIndicator(
                        progress = { progress },
                        color = t.accent,
                        strokeWidth = 4.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                CircularProgressIndicator(
                    color = Color(0x99FFFFFF),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(72.dp)
                )
                if (current != null && total != null) {
                    Text(
                        text = "$current/$total",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(headline, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (episode != null) {
                Text(episode, color = Color(0xFF9A9A9A), fontSize = 13.sp)
            }
            val cancelFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                delay(100)
                runCatching { cancelFocus.requestFocus() }
            }
            Text(
                text = "Cancel",
                color = Color(0xFFD0D0D0),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .focusRequester(cancelFocus)
                    .tvFocusHighlight(RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 22.dp, vertical = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingRow(
    continueList: List<WatchProgressEntry>,
    onContinueClick: (WatchProgressEntry) -> Unit,
    onRemoveEntry: (Int) -> Unit,
    rowKey: String = "continue",
    isFirstRow: Boolean = false,
    focusMemory: TvFocusMemory? = null
) {
    var showPopupFor by remember { mutableStateOf<WatchProgressEntry?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Continue Watching",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            itemsIndexed(continueList) { index, entry ->
                val itemKey = "$rowKey#${entry.media.id}"
                val t = AppTheme.c
                Box(
                    modifier = Modifier
                        .width(128.dp)
                        .tvRememberedFocus(itemKey, index, isFirstRow, focusMemory)
                        .tvFocusHighlight(RoundedCornerShape(8.dp), focusedScale = 1.08f)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = {
                                focusMemory?.pendingKey = itemKey
                                onContinueClick(entry)
                            },
                            onLongClick = { showPopupFor = entry }
                        )
                ) {
                    AsyncImage(
                        model = entry.media.posterUrl,
                        contentDescription = entry.media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .background(Color(0xFF181818))
                    )
                    if (entry.seasonNumber != null && entry.episodeNumber != null) {
                        Text(
                            text = "S${entry.seasonNumber}:E${entry.episodeNumber}",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(5.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xB3000000))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    val minsLeft = if (entry.durationMs > 0L) {
                        ((entry.durationMs - entry.positionMs).coerceAtLeast(0L) / 60000L).toInt()
                    } else -1
                    if (minsLeft >= 0) {
                        Text(
                            text = if (minsLeft == 0) "Almost done" else "$minsLeft min",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 4.dp, bottom = 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xB3000000))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    if (entry.durationMs > 0L) {
                        LinearProgressIndicator(
                            progress = {
                                (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
                            },
                            color = t.accent,
                            trackColor = Color(0x80333333),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

    if (showPopupFor != null) {
        AlertDialog(
            onDismissRequest = { showPopupFor = null },
            title = { Text("Options") },
            text = { Text("Remove \"${showPopupFor?.media?.title}\" from Continue Watching?") },
            confirmButton = {
                Button(
                    onClick = {
                        showPopupFor?.let { onRemoveEntry(it.media.id) }
                        showPopupFor = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.c.accent),
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp))
                ) { Text("Remove", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPopupFor = null }, modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp))) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun MediaCarouselRow(
    title: String,
    items: List<MediaItemModel>,
    showRankBadge: Boolean = false,
    onMediaClick: (MediaItemModel) -> Unit,
    onWatchLaterChanged: () -> Unit,
    isFirstRow: Boolean = false,
    focusMemory: TvFocusMemory? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            itemsIndexed(items) { index, item ->
                PosterCard(
                    item = item,
                    rankNumber = if (showRankBadge) index + 1 else null,
                    isWatchLaterScreen = false,
                    onClick = { onMediaClick(item) },
                    onWatchLaterChanged = onWatchLaterChanged,
                    modifier = Modifier.width(128.dp),
                    focusKey = "$title#${item.id}",
                    focusIndex = index,
                    isFirstRow = isFirstRow,
                    focusMemory = focusMemory
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    item: MediaItemModel,
    rankNumber: Int? = null,
    isWatchLaterScreen: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
    onWatchLaterChanged: () -> Unit,
    focusKey: String? = null,
    focusIndex: Int = -1,
    isFirstRow: Boolean = false,
    focusMemory: TvFocusMemory? = null
) {
    val scope = rememberCoroutineScope()
    var showWatchLaterPopup by remember { mutableStateOf(false) }

    // NOTE: no separate .focusable() here. combinedClickable is already focusable;
    // an extra focusable() created two focus stops, so OK had to be pressed twice.
    val t = AppTheme.c
    val isTv = rememberIsTv()
    var isFocusedNow by remember { mutableStateOf(false) }
    val pressInteraction = remember { MutableInteractionSource() }
    val isPressed by pressInteraction.collectIsPressedAsState()
    // Little "physical" bounce while a finger is down (phones only)
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && !isTv) 0.94f else 1f,
        animationSpec = tween(110),
        label = "posterPress"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(
                if (focusKey != null) Modifier.tvRememberedFocus(focusKey, focusIndex, isFirstRow, focusMemory)
                else Modifier
            )
            .onFocusChanged { isFocusedNow = it.isFocused }
            .tvFocusHighlight(RoundedCornerShape(8.dp), focusedScale = 1.08f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                interactionSource = pressInteraction,
                indication = LocalIndication.current,
                onClick = {
                    if (focusKey != null) focusMemory?.pendingKey = focusKey
                    onClick()
                },
                onLongClick = { showWatchLaterPopup = true }
            )
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(Color(0xFF181818))
        )
        if (rankNumber != null) {
            Text(
                text = "#$rankNumber",
                color = t.onAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(t.accentGradient)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(
            text = item.badgeLabel,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xB3000000))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        )
        // On TV, show the title over the focused poster (posters carry no text otherwise)
        if (isTv && isFocusedNow) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xE6000000))
                        )
                    )
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 6.dp)
                )
            }
        }
    }

    if (showWatchLaterPopup) {
        // Long-press quick preview: peek at a title without leaving the row
        val t2 = AppTheme.c
        Dialog(onDismissRequest = { showWatchLaterPopup = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(t2.surface)
                    .border(1.dp, t2.glassBorder, RoundedCornerShape(18.dp))
            ) {
                Box {
                    AsyncImage(
                        model = item.backdropUrl.ifBlank { item.posterUrl },
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(Color(0xFF181818))
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, t2.surface)
                                )
                            )
                    )
                    Text(
                        text = item.badgeLabel,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xB3000000))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = t2.accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = String.format("%.1f", item.rating) + "   •   " + item.releaseYear.take(4),
                            color = t2.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (item.overview.isNotBlank()) {
                        Text(
                            text = item.overview,
                            color = t2.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .tvFocusHighlight(RoundedCornerShape(50), focusedScale = 1.06f)
                                .clip(RoundedCornerShape(50))
                                .background(t2.accentGradient)
                                .clickable {
                                    showWatchLaterPopup = false
                                    if (focusKey != null) focusMemory?.pendingKey = focusKey
                                    onClick()
                                }
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = t2.onAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Details", color = t2.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .tvFocusHighlight(RoundedCornerShape(50), focusedScale = 1.06f)
                                .clip(RoundedCornerShape(50))
                                .background(t2.glass)
                                .border(1.dp, t2.glassBorder, RoundedCornerShape(50))
                                .clickable {
                                    if (isWatchLaterScreen) {
                                        ServiceLocator.prefsStore.removeFromWatchLater(item.id)
                                        scope.launch {
                                            ServiceLocator.catalogRepository.syncProgressToCloud(item, inWatchlist = false)
                                        }
                                    } else {
                                        ServiceLocator.prefsStore.addToWatchLater(item)
                                        scope.launch {
                                            ServiceLocator.catalogRepository.syncProgressToCloud(item, inWatchlist = true)
                                        }
                                    }
                                    showWatchLaterPopup = false
                                    onWatchLaterChanged()
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isWatchLaterScreen) "− Remove" else "+ Watch Later",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Gives a poster remote focus when [TvFocusMemory] says it should have it:
 * the very first poster of the first row on launch, or the poster you opened
 * when you come back from the details screen. Also records the last focused poster.
 */
fun Modifier.tvRememberedFocus(
    key: String,
    index: Int,
    isFirstRow: Boolean,
    memory: TvFocusMemory?
): Modifier = composed {
    val isTv = rememberIsTv()
    // Phones are driven by touch, so only steer focus automatically on TV
    if (memory == null || !isTv) return@composed this
    val requester = remember { FocusRequester() }
    var hasFocusNow by remember { mutableStateOf(false) }
    val wantsFocus = memory.pendingKey == key ||
            (memory.pendingKey == TvFocusMemory.FIRST_ITEM && isFirstRow && index == 0)
    LaunchedEffect(wantsFocus) {
        if (wantsFocus) {
            // On return from a movie, the top menu button grabs focus first. Keep re-requesting
            // (up to ~2.5s) until THIS poster actually holds focus — one request can "succeed"
            // yet lose focus again a frame later, which is why focus kept ending on the menu bar.
            var attempts = 0
            while (attempts < 25 && !hasFocusNow &&
                (memory.pendingKey == key || memory.pendingKey == TvFocusMemory.FIRST_ITEM)
            ) {
                runCatching { requester.requestFocus() }
                delay(100)
                attempts++
            }
        }
    }
    this
        .focusRequester(requester)
        .onFocusChanged {
            hasFocusNow = it.hasFocus
            if (it.hasFocus) {
                memory.lastFocusedKey = key
                // This poster got focus (the one we wanted) — stop any pending request.
                if (memory.pendingKey == key || memory.pendingKey == TvFocusMemory.FIRST_ITEM) {
                    memory.pendingKey = null
                }
            }
        }
}

// ==========================================
// SHIMMER (loading placeholders) + FEATURED HERO
// ==========================================

/** An animated left-to-right shimmer, used for skeleton loading placeholders. */
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(8.dp)): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -600f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    val brush = Brush.linearGradient(
        colors = listOf(Color(0xFF1C1C1C), Color(0xFF2E2E2E), Color(0xFF1C1C1C)),
        start = Offset(x, 0f),
        end = Offset(x + 500f, 0f)
    )
    this.clip(shape).background(brush)
}

@Composable
private fun ShimmerRow(posterCount: Int = 6) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .width(160.dp)
                .height(16.dp)
                .shimmer(RoundedCornerShape(4.dp))
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false
        ) {
            items(posterCount) {
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .height(165.dp)
                        .shimmer(RoundedCornerShape(5.dp))
                )
            }
        }
    }
}

@Composable
fun HomeLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // A big hero placeholder, then a couple of poster rows
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .shimmer(RoundedCornerShape(0.dp))
        )
        ShimmerRow()
        ShimmerRow()
    }
}

/** A row of genre pills; tapping one opens Search filtered to that type + genre. */
@Composable
fun GenreChipsRow(type: String, onPick: (String, String) -> Unit) {
    val genres = listOf(
        "Action", "Comedy", "Drama", "Horror", "Romance",
        "Thriller", "Animation", "Fantasy", "Mystery", "Adventure"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Browse by Genre",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(genres) { g ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .tvFocusHighlight(RoundedCornerShape(50), focusedScale = 1.05f)
                        .background(AppTheme.c.surfaceHigh)
                        .border(1.dp, AppTheme.c.glassBorder, RoundedCornerShape(50))
                        .clickable { onPick(type, g) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = g, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Edge-to-edge featured hero. Phones swipe; TV pages with the remote. */
@Composable
fun HeroCarousel(
    items: List<MediaItemModel>,
    onPlay: (MediaItemModel) -> Unit,
    focusMemory: TvFocusMemory? = null
) {
    if (items.isEmpty()) return
    if (rememberIsTv()) {
        TvHeroPager(items = items, onPlay = onPlay, focusMemory = focusMemory)
    } else {
        PhoneHeroCarousel(items = items, onPlay = onPlay)
    }
}

/**
 * TV hero = the phone hero: full-bleed picture, centered page dots, title,
 * "year · type · rating" and My List · white Play · Info underneath.
 * (TV pictures are wide scene shots without the title printed on them, so the
 * title is shown as text.)
 * Remote: LEFT on My List = previous title (or the menu on the first),
 * RIGHT on Info = next title. Moves on by itself while you browse the rows.
 */
@Composable
private fun TvHeroPager(
    items: List<MediaItemModel>,
    onPlay: (MediaItemModel) -> Unit,
    focusMemory: TvFocusMemory?
) {
    val t = AppTheme.c
    val scope = rememberCoroutineScope()
    var idx by rememberSaveable { mutableIntStateOf(0) }
    var heroDir by remember { mutableIntStateOf(1) }
    if (idx >= items.size) idx = 0
    val current = items[idx]
    val heroResume = remember(current.id) {
        ServiceLocator.prefsStore.getContinueWatching().firstOrNull { it.media.id == current.id }
    }
    var inList by remember(current.id) {
        mutableStateOf(ServiceLocator.prefsStore.getWatchLater().any { it.id == current.id })
    }
    var heroFocused by remember { mutableStateOf(false) }
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.74f).dp.coerceAtLeast(340.dp)

    // The buttons stay the same focusable views (focus never breaks) but ride along with each page
    val buttonSlide = remember { Animatable(0f) }
    var firstHeroShow by remember { mutableStateOf(true) }
    LaunchedEffect(idx) {
        if (firstHeroShow) {
            firstHeroShow = false
        } else {
            buttonSlide.snapTo(heroDir * 180f)
            buttonSlide.animateTo(0f, animationSpec = tween(420))
        }
    }
    // Auto-advance (like the phone) while the viewer is somewhere else on the page
    LaunchedEffect(items.size, heroFocused) {
        if (heroFocused || items.size < 2) return@LaunchedEffect
        while (true) {
            delay(7000L)
            heroDir = 1
            idx = (idx + 1) % items.size
        }
    }
    fun goNext() {
        heroDir = 1
        idx = (idx + 1) % items.size
    }
    fun goPrev(): Boolean {
        if (idx == 0) return false
        heroDir = -1
        idx -= 1
        return true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        AnimatedContent(
            targetState = idx,
            transitionSpec = {
                val d = heroDir
                (slideInHorizontally(animationSpec = tween(420)) { full -> full / 2 * d } + fadeIn(tween(420))) togetherWith
                    (slideOutHorizontally(animationSpec = tween(380)) { full -> -full / 2 * d } + fadeOut(tween(260)))
            },
            label = "tvHeroPage"
        ) { i ->
            val media = items[i.coerceIn(0, items.size - 1)]
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = media.backdropUrl.ifBlank { media.posterUrl },
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF181818))
                )
                // Same scrims as the phone hero: dark under the header, long fade at the bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color(0x99000000),
                                0.22f to Color.Transparent,
                                0.5f to Color.Transparent,
                                0.8f to t.background.copy(alpha = 0.85f),
                                1f to t.background
                            )
                        )
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 92.dp)
                ) {
                    Text(
                        text = media.title,
                        color = Color.White,
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    val kind = when {
                        media.isAnime -> "Anime"
                        media.isSeries -> "Series"
                        else -> "Movie"
                    }
                    Text(
                        text = listOf(media.releaseYear.take(4), kind, "\u2605 " + String.format("%.1f", media.rating))
                            .filter { it.isNotBlank() }
                            .joinToString("   \u2022   "),
                        color = Color(0xE6FFFFFF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Page dots: small, centered, just above the title (white, like the phone)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 168.dp)
        ) {
            repeat(items.size) { index ->
                val w by animateDpAsState(
                    targetValue = if (index == idx) 14.dp else 4.dp,
                    animationSpec = tween(300),
                    label = "tvDot"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.5.dp)
                        .height(4.dp)
                        .width(w)
                        .clip(RoundedCornerShape(50))
                        .background(if (index == idx) Color.White else Color(0x73FFFFFF))
                )
            }
        }

        // My List · white Play · Info - centered, like the phone
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .graphicsLayer { translationX = buttonSlide.value }
                .onFocusChanged { heroFocused = it.hasFocus }
        ) {
            // My List: LEFT here = previous title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .onPreviewKeyEvent { ev ->
                        ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft && goPrev()
                    }
                    .tvFocusHighlight(RoundedCornerShape(12.dp), focusedScale = 1.1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (inList) {
                            ServiceLocator.prefsStore.removeFromWatchLater(current.id)
                            scope.launch { ServiceLocator.catalogRepository.syncProgressToCloud(current, inWatchlist = false) }
                        } else {
                            ServiceLocator.prefsStore.addToWatchLater(current)
                            scope.launch { ServiceLocator.catalogRepository.syncProgressToCloud(current, inWatchlist = true) }
                        }
                        inList = !inList
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Crossfade(targetState = inList, animationSpec = tween(200), label = "tvHeroList") { added ->
                    Icon(
                        imageVector = if (added) Icons.Filled.Check else Icons.Filled.Add,
                        contentDescription = "My List",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text("My List", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
            // Play (or Resume): starting focus
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .then(
                        if (focusMemory != null) Modifier.tvRememberedFocus("hero", 0, true, focusMemory)
                        else Modifier
                    )
                    .tvFocusHighlight(RoundedCornerShape(50), focusedScale = 1.08f)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable {
                        if (focusMemory != null) focusMemory.pendingKey = "hero"
                        onPlay(current)
                    }
                    .padding(horizontal = 44.dp, vertical = 11.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                Text(
                    text = if (heroResume != null) "Resume" else "Play",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // Info: RIGHT here = next title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionRight) {
                            goNext()
                            true
                        } else {
                            false
                        }
                    }
                    .tvFocusHighlight(RoundedCornerShape(12.dp), focusedScale = 1.1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPlay(current) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Outlined.Info, contentDescription = "Info", tint = Color.White, modifier = Modifier.size(24.dp))
                Text("Info", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Phone hero: tall swipeable poster pages with auto-advance. */
@Composable
private fun PhoneHeroCarousel(items: List<MediaItemModel>, onPlay: (MediaItemModel) -> Unit) {
    val listState = rememberLazyListState()
    val fling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = listState)
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.74f).dp

    if (items.size > 1) {
        LaunchedEffect(items.size) {
            while (true) {
                delay(7000)
                if (!listState.isScrollInProgress) {
                    val next = (listState.firstVisibleItemIndex + 1) % items.size
                    runCatching { listState.animateScrollToItem(next) }
                }
            }
        }
    }

    Box(modifier = Modifier.height(heroHeight)) {
        LazyRow(state = listState, flingBehavior = fling) {
            items(items) { item ->
                HeroSlide(
                    item = item,
                    onPlay = onPlay,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .fillMaxHeight()
                )
            }
        }
        // Small centered page dots just above the meta line
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 116.dp)
        ) {
            val current = listState.firstVisibleItemIndex
            repeat(items.size) { index ->
                val w by animateDpAsState(
                    targetValue = if (index == current) 14.dp else 4.dp,
                    animationSpec = tween(300),
                    label = "phoneDot"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.5.dp)
                        .height(4.dp)
                        .width(w)
                        .clip(RoundedCornerShape(50))
                        .background(if (index == current) Color.White else Color(0x73FFFFFF))
                )
            }
        }
    }
}

/** One tall hero page (phone): poster art, centered meta and My List · Play · Info. */
@Composable
private fun HeroSlide(
    item: MediaItemModel,
    onPlay: (MediaItemModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val t = AppTheme.c
    val scope = rememberCoroutineScope()
    val slideResume = remember(item.id) {
        ServiceLocator.prefsStore.getContinueWatching().firstOrNull { it.media.id == item.id }
    }
    var inList by remember(item.id) {
        mutableStateOf(ServiceLocator.prefsStore.getWatchLater().any { it.id == item.id })
    }
    Box(modifier = modifier) {
        AsyncImage(
            model = item.posterUrl.ifBlank { item.backdropUrl },
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF181818))
        )
        // Dark top (under the header) and a long fade into the page at the bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x99000000),
                        0.22f to Color.Transparent,
                        0.55f to Color.Transparent,
                        0.82f to t.background.copy(alpha = 0.85f),
                        1f to t.background
                    )
                )
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            val kind = when {
                item.isAnime -> "Anime"
                item.isSeries -> "Series"
                else -> "Movie"
            }
            Text(
                text = listOf(item.releaseYear.take(4), kind, "\u2605 " + String.format("%.1f", item.rating))
                    .filter { it.isNotBlank() }
                    .joinToString("   \u2022   "),
                color = Color(0xE6FFFFFF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                HeroSideAction(
                    icon = if (inList) Icons.Filled.Check else Icons.Filled.Add,
                    label = "My List",
                    onClick = {
                        if (inList) {
                            ServiceLocator.prefsStore.removeFromWatchLater(item.id)
                            scope.launch { ServiceLocator.catalogRepository.syncProgressToCloud(item, inWatchlist = false) }
                        } else {
                            ServiceLocator.prefsStore.addToWatchLater(item)
                            scope.launch { ServiceLocator.catalogRepository.syncProgressToCloud(item, inWatchlist = true) }
                        }
                        inList = !inList
                    }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable { onPlay(item) }
                        .padding(horizontal = 44.dp, vertical = 11.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (slideResume != null) "Resume" else "Play",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                HeroSideAction(
                    icon = Icons.Outlined.Info,
                    label = "Info",
                    onClick = { onPlay(item) }
                )
            }
        }
    }
}

@Composable
private fun HeroSideAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Crossfade(targetState = icon, animationSpec = tween(200), label = "heroActionIcon") { ic ->
            Icon(imageVector = ic, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(text = label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Left darkening + bottom fade shared by all hero variants. */
@Composable
private fun HeroScrims() {
    val t = AppTheme.c
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(t.background, t.background.copy(alpha = 0.55f), Color.Transparent),
                    endX = 1100f
                )
            )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Transparent, t.background)
                )
            )
    )
}

@Composable
private fun HeroKicker(item: MediaItemModel) {
    val t = AppTheme.c
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(t.accentGradient)
        )
        Text(
            text = item.badgeLabel.uppercase(),
            color = Color(0xFFDADADA),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
    }
}

@Composable
private fun HeroMetaRow(item: MediaItemModel) {
    val t = AppTheme.c
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "TMDB",
            color = Color.Black,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFF5C518))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
        Text(
            text = String.format("%.1f", item.rating) + "/10",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = item.releaseYear.take(4),
            color = t.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Animated page dots used by both hero variants. */
@Composable
private fun HeroDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(end = 18.dp, bottom = 12.dp)
    ) {
        repeat(count) { index ->
            val w by animateDpAsState(
                targetValue = if (index == current) 16.dp else 6.dp,
                animationSpec = tween(300),
                label = "dotW"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width(w)
                    .clip(RoundedCornerShape(50))
                    .background(if (index == current) AppTheme.c.accent else Color(0x66FFFFFF))
            )
        }
    }
}


/** "Top 10" row: big outlined rank numbers with the poster tucked over them. */
@Composable
fun Top10Row(
    title: String,
    items: List<MediaItemModel>,
    onMediaClick: (MediaItemModel) -> Unit,
    onWatchLaterChanged: () -> Unit,
    focusMemory: TvFocusMemory? = null,
    rowKey: String = "top10",
    isFirstRow: Boolean = false
) {
    val top = items.take(10)
    if (top.isEmpty()) return
    val isTv = rememberIsTv()
    val posterW = 118.dp
    val posterH = posterW * 1.5f
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(top) { index, item ->
                val num = "${index + 1}"
                Box(
                    modifier = Modifier
                        .width(posterW + if (index == 9) 92.dp else 66.dp)
                        .height(posterH + 6.dp)
                ) {
                    // Outlined number (stroke only), like the reference design
                    Text(
                        text = num,
                        style = TextStyle(
                            color = Color(0xFF8C8C8C),
                            fontSize = 150.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-10).sp,
                            drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 5f,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        ),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(y = 6.dp)
                    )
                    PosterCard(
                        item = item,
                        isWatchLaterScreen = false,
                        onClick = { onMediaClick(item) },
                        onWatchLaterChanged = onWatchLaterChanged,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(posterW),
                        focusKey = "$rowKey#${item.id}",
                        focusIndex = index,
                        isFirstRow = isFirstRow,
                        focusMemory = focusMemory
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. HOME SCREEN & SIDEBAR NAVIGATION
// ==========================================

@Composable
fun HomeScreenWithSidebar(
    selectedSection: String,
    onSelectSection: (String) -> Unit,
    isLoading: Boolean,
    trendingAll: List<MediaItemModel>,
    trendingMovies: List<MediaItemModel>,
    popularMovies: List<MediaItemModel>,
    topRatedMovies: List<MediaItemModel>,
    trendingSeries: List<MediaItemModel>,
    popularSeries: List<MediaItemModel>,
    topRatedSeries: List<MediaItemModel>,
    trendingAnime: List<MediaItemModel>,
    popularAnime: List<MediaItemModel>,
    anilistWatchingList: List<MediaItemModel>,
    anilistPlanningList: List<MediaItemModel>,
    tmdbWatchlistMovies: List<MediaItemModel>,
    tmdbWatchlistSeries: List<MediaItemModel>,
    onOpenSearchScreen: () -> Unit,
    onOpenSettingsScreen: () -> Unit,
    onMediaClick: (MediaItemModel) -> Unit,
    onContinueClick: (WatchProgressEntry) -> Unit,
    onOpenBrand: (String) -> Unit = {},
    onOpenGenre: (String, String) -> Unit = { _, _ -> },
    focusMemory: TvFocusMemory = remember { TvFocusMemory() }
) {
    val isTv = rememberIsTv()
    val t = AppTheme.c

    var continueList by remember { mutableStateOf(ServiceLocator.prefsStore.getContinueWatching()) }
    var watchLaterList by remember { mutableStateOf(ServiceLocator.prefsStore.getWatchLater()) }
    var tmdbAuth by remember { mutableStateOf(ServiceLocator.prefsStore.getTmdbAuth()) }
    var aniAuth by remember { mutableStateOf(ServiceLocator.prefsStore.getAniListAuth()) }

    LaunchedEffect(selectedSection) {
        continueList = ServiceLocator.prefsStore.getContinueWatching()
        watchLaterList = ServiceLocator.prefsStore.getWatchLater()
        tmdbAuth = ServiceLocator.prefsStore.getTmdbAuth()
        aniAuth = ServiceLocator.prefsStore.getAniListAuth()
    }

    // "Because you watched X" — recommendations seeded from the newest
    // Continue Watching title (pure TMDB metadata, fetched once per source).
    var homeRecsFor by remember { mutableStateOf<MediaItemModel?>(null) }
    var homeRecs by remember { mutableStateOf<List<MediaItemModel>>(emptyList()) }
    LaunchedEffect(continueList.firstOrNull()?.media?.id) {
        val seed = continueList.firstOrNull()?.media
        if (seed != null && seed.id != homeRecsFor?.id) {
            homeRecsFor = seed
            homeRecs = runCatching { TmdbExtras.fetchRecommendations(seed) }.getOrDefault(emptyList())
        }
    }

    // If the remembered poster isn't on screen any more, fall back to the first poster
    LaunchedEffect(focusMemory.pendingKey, isLoading, selectedSection) {
        val pending = focusMemory.pendingKey
        if (isTv && !isLoading && pending != null && pending != TvFocusMemory.FIRST_ITEM) {
            delay(1500)
            if (focusMemory.pendingKey == pending) focusMemory.pendingKey = TvFocusMemory.FIRST_ITEM
        }
    }

    val goSection: (String) -> Unit = { key ->
        if (key != selectedSection) focusMemory.pendingKey = TvFocusMemory.FIRST_ITEM
        onSelectSection(key)
    }
    val goSearch: () -> Unit = {
        focusMemory.pendingKey = focusMemory.lastFocusedKey ?: TvFocusMemory.FIRST_ITEM
        onOpenSearchScreen()
    }
    val goSettings: () -> Unit = {
        focusMemory.pendingKey = focusMemory.lastFocusedKey ?: TvFocusMemory.FIRST_ITEM
        onOpenSettingsScreen()
    }
    // Phone: collapsing header (logo + section tabs) on top, floating nav bar at the
    // bottom. TV: permanent side rail. The header hides its logo row while you scroll
    // down and brings it back when you scroll up.
    var headerCollapsed by remember { mutableStateOf(false) }
    var myListFilter by rememberSaveable { mutableStateOf("All") }
    var myListHidden by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(selectedSection) { headerCollapsed = false }
    val headerScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -6f) headerCollapsed = true
                else if (available.y > 6f) headerCollapsed = false
                return Offset.Zero
            }
        }
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Space the scrolling content leaves for the phone header / bottom bar
    val phoneTopInset: Dp = statusTop + 104.dp
    val phoneBottomInset: Dp = if (isTv) 16.dp else 100.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(t.background)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (isTv) {
                // Room for the collapsed rail; the rail itself is drawn on top (below)
                Spacer(modifier = Modifier.width(RAIL_COLLAPSED_WIDTH))
            }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (!isTv) Modifier.nestedScroll(headerScroll) else Modifier)
                ) {
            if (isLoading) {
                HomeLoadingSkeleton()
            } else {
                Crossfade(
                    targetState = selectedSection,
                    animationSpec = tween(260),
                    label = "sectionFade"
                ) { section ->
                when (section) {
                    "WatchLater" -> {
                        // Merged list: Watch Later saved in the app + TMDB watchlist (movies & series)
                        val keyOf: (MediaItemModel) -> String = { m ->
                            (if (m.isSeries) "tv-" else "mv-") + (m.tmdbId ?: m.id)
                        }
                        val myListAll = (watchLaterList + tmdbWatchlistMovies + tmdbWatchlistSeries)
                            .distinctBy(keyOf)
                            .filter { keyOf(it) !in myListHidden }
                        val myListShown = when (myListFilter) {
                            "Movies" -> myListAll.filter { !it.isSeries }
                            "Series" -> myListAll.filter { it.isSeries }
                            else -> myListAll
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                        if (myListShown.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = when (myListFilter) {
                                        "Movies" -> "No movies in your list yet."
                                        "Series" -> "No series in your list yet."
                                        else -> "Your list is empty.\nLong press any poster to add it here!"
                                    },
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 105.dp),
                                contentPadding = PaddingValues(
                                    start = 14.dp,
                                    end = 14.dp,
                                    top = 10.dp + phoneTopInset,
                                    bottom = phoneBottomInset
                                ),
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                gridItemsIndexed(myListShown, key = { _, m -> keyOf(m) }) { index, item ->
                                    PosterCard(
                                        item = item,
                                        isWatchLaterScreen = true,
                                        onClick = { onMediaClick(item) },
                                        onWatchLaterChanged = {
                                            // Removed (also from TMDB/AniList): hide it right away
                                            watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                            myListHidden = myListHidden + keyOf(item)
                                        },
                                        focusKey = "watchLaterGrid#${item.id}",
                                        focusIndex = index,
                                        isFirstRow = true,
                                        focusMemory = focusMemory
                                    )
                                }
                            }
                        }
                        }
                    }

                    "Anime" -> {
                        AnimeCatalogSection(
                            topInset = phoneTopInset,
                            bottomInset = phoneBottomInset,
                            focusMemory = focusMemory,
                            trendingAnime = trendingAnime,
                            popularAnime = popularAnime,
                            anilistWatchingList = anilistWatchingList,
                            anilistPlanningList = anilistPlanningList,
                            onAnimeClick = onMediaClick,
                            onWatchLaterChanged = {
                                watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                            }
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = if (section == "Home") 0.dp else 12.dp + phoneTopInset,
                                bottom = phoneBottomInset
                            ),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            when (section) {
                                "Home" -> {
                                    val heroItems = trendingAll
                                        .filter { it.backdropUrl.isNotBlank() }
                                        .ifEmpty { trendingAll }
                                        .take(5)
                                    if (heroItems.isNotEmpty()) {
                                        item {
                                            HeroCarousel(
                                                items = heroItems,
                                                onPlay = onMediaClick,
                                                focusMemory = focusMemory
                                            )
                                        }
                                    }
                                    // On TV the hero's Details button is the first focus target,
                                    // so the app opens with the hero fully visible.
                                    val homeFirst = when {
                                        heroItems.isNotEmpty() -> "hero"
                                        continueList.isNotEmpty() -> "continue"
                                        watchLaterList.isNotEmpty() -> "watchLater"
                                        else -> "trending"
                                    }
                                    if (continueList.isNotEmpty()) {
                                        item {
                                            ContinueWatchingRow(
                                                rowKey = "home-continue",
                                                isFirstRow = homeFirst == "continue",
                                                focusMemory = focusMemory,
                                                continueList = continueList,
                                                onContinueClick = onContinueClick,
                                                onRemoveEntry = { id ->
                                                    continueList = ServiceLocator.prefsStore.removeFromContinueWatching(id)
                                                }
                                            )
                                        }
                                    }
                                    if (watchLaterList.isNotEmpty()) {
                                        item {
                                            MediaCarouselRow(
                                                focusMemory = focusMemory,
                                                title = "Watch Later / Watchlist",
                                                isFirstRow = homeFirst == "watchLater",
                                                items = watchLaterList,
                                                showRankBadge = false,
                                                onMediaClick = onMediaClick,
                                                onWatchLaterChanged = {
                                                    watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                                }
                                            )
                                        }
                                    }
                                    if (homeRecs.isNotEmpty()) {
                                        item {
                                            MediaCarouselRow(
                                                focusMemory = focusMemory,
                                                title = "Because you watched ${homeRecsFor?.title ?: ""}",
                                                items = homeRecs,
                                                showRankBadge = false,
                                                onMediaClick = onMediaClick,
                                                onWatchLaterChanged = {
                                                    watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                                }
                                            )
                                        }
                                    }
                                    item {
                                        MediaCarouselRow(
                                            focusMemory = focusMemory,
                                            title = "Trending Movies & TV Shows",
                                            isFirstRow = homeFirst == "trending",
                                            items = trendingAll,
                                            showRankBadge = false,
                                            onMediaClick = onMediaClick,
                                            onWatchLaterChanged = {
                                                watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                            }
                                        )
                                    }
                                    if (trendingAll.size >= 4) {
                                        item {
                                            Top10Row(
                                                title = "Top 10 Today",
                                                items = trendingAll,
                                                onMediaClick = onMediaClick,
                                                onWatchLaterChanged = {
                                                    watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                                },
                                                focusMemory = focusMemory,
                                                rowKey = "home-top10"
                                            )
                                        }
                                    }
                                    item {
                                        BrandRows(onOpenBrand = { brand -> onOpenBrand(brand.key) })
                                    }
                                    if (trendingAnime.isNotEmpty()) {
                                        item {
                                            MediaCarouselRow(
                                                focusMemory = focusMemory,
                                                title = "Trending Anime (Sub)",
                                                items = trendingAnime,
                                                showRankBadge = false,
                                                onMediaClick = onMediaClick,
                                                onWatchLaterChanged = {
                                                    watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                                }
                                            )
                                        }
                                    }
                                    item {
                                        MediaCarouselRow(
                                            focusMemory = focusMemory,
                                            title = "Trending Movies Today",
                                            items = trendingMovies,
                                            showRankBadge = false,
                                            onMediaClick = onMediaClick,
                                            onWatchLaterChanged = {
                                                watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                            }
                                        )
                                    }
                                    item {
                                        MediaCarouselRow(
                                            focusMemory = focusMemory,
                                            title = "Trending TV Series Today",
                                            items = trendingSeries,
                                            showRankBadge = false,
                                            onMediaClick = onMediaClick,
                                            onWatchLaterChanged = {
                                                watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                            }
                                        )
                                    }
                                }

                                "Movies" -> {
                                    item { GenreChipsRow(type = "Movies", onPick = onOpenGenre) }
                                    val movieContinue = continueList.filter { !it.media.isSeries && !it.media.isAnime }
                                    val movieWatchLaterPre = (tmdbWatchlistMovies + watchLaterList.filter { !it.isSeries && !it.isAnime })
                                    val moviesFirst = when {
                                        movieContinue.isNotEmpty() -> "continue"
                                        movieWatchLaterPre.isNotEmpty() -> "watchLater"
                                        else -> "top10"
                                    }
                                    if (movieContinue.isNotEmpty()) {
                                        item {
                                            ContinueWatchingRow(
                                                rowKey = "movies-continue",
                                                isFirstRow = moviesFirst == "continue",
                                                focusMemory = focusMemory,
                                                continueList = movieContinue,
                                                onContinueClick = onContinueClick,
                                                onRemoveEntry = { id ->
                                                    continueList = ServiceLocator.prefsStore.removeFromContinueWatching(id)
                                                }
                                            )
                                        }
                                    }
                                    val movieWatchLater = (tmdbWatchlistMovies + watchLaterList.filter { !it.isSeries && !it.isAnime }).distinctBy { it.id }
                                    if (movieWatchLater.isNotEmpty()) {
                                        item {
                                            MediaCarouselRow(
                                                focusMemory = focusMemory,
                                                title = "Movies Watchlist (TMDB Synced)",
                                                isFirstRow = moviesFirst == "watchLater",
                                                items = movieWatchLater,
                                                showRankBadge = false,
                                                onMediaClick = onMediaClick,
                                                onWatchLaterChanged = {
                                                    watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                                }
                                            )
                                        }
                                    }
                                    item {
                                        Top10Row(
                                            title = "Top 10 Movies Today",
                                            items = trendingMovies,
                                            onMediaClick = onMediaClick,
                                            onWatchLaterChanged = {
                                                watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                            },
                                            focusMemory = focusMemory,
                                            rowKey = "movies-top10",
                                            isFirstRow = moviesFirst == "top10"
                                        )
                                    }
                                    item {
                                        MediaCarouselRow(
                                            focusMemory = focusMemory,
                                            title = "Current Trending Movies",
                                            items = trendingMovies,
                                            showRankBadge = false,
                                            onMediaClick = onMediaClick,
                                            onWatchLaterChanged = {
                                                watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                            }
                                        )
                                    }
                                    item {
                                        MediaCarouselRow(
                                            focusMemory = focusMemory,
                                            title = "Top Rated Movies",
                                            items = topRatedMovies,
                                            showRankBadge = false,
                                            onMediaClick = onMediaClick,
                                            onWatchLaterChanged = {
                                                watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                            }
                                        )
                                    }
                                    item {
                                        MediaCarouselRow(
                                            focusMemory = focusMemory,
                                            title = "Popular Movies",
                                            items = popularMovies,
                                            showRankBadge = false,
                                            onMediaClick = onMediaClick,
                                            onWatchLaterChanged = {
                                                watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                            }
                                        )
                                    }
                                }

                                "Series" -> {
                                    item { GenreChipsRow(type = "Series", onPick = onOpenGenre) }
                                    val seriesContinue = continueList.filter { it.media.isSeries && !it.media.isAnime }
                                    val seriesWatchLaterPre = (tmdbWatchlistSeries + watchLaterList.filter { it.isSeries && !it.isAnime })
                                    val seriesFirst = when {
                                        seriesContinue.isNotEmpty() -> "continue"
                                        seriesWatchLaterPre.isNotEmpty() -> "watchLater"
                                        else -> "top10"
                                    }
                                    if (seriesContinue.isNotEmpty()) {
                                        item {
                                            ContinueWatchingRow(
                                                rowKey = "series-continue",
                                                isFirstRow = seriesFirst == "continue",
                                                focusMemory = focusMemory,
                                                continueList = seriesContinue,
                                                onContinueClick = onContinueClick,
                                                onRemoveEntry = { id ->
                                                    continueList = ServiceLocator.prefsStore.removeFromContinueWatching(id)
                                                }
                                            )
                                        }
                                    }
                                    val seriesWatchLater = (tmdbWatchlistSeries + watchLaterList.filter { it.isSeries && !it.isAnime }).distinctBy { it.id }
                                    if (seriesWatchLater.isNotEmpty()) {
                                        item {
                                            MediaCarouselRow(
                                                focusMemory = focusMemory,
                                                title = "Series Watchlist (TMDB Synced)",
                                                isFirstRow = seriesFirst == "watchLater",
                                                items = seriesWatchLater,
                                                showRankBadge = false,
                                                onMediaClick = onMediaClick,
                                                onWatchLaterChanged = {
                                                    watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                                }
                                            )
                                        }
                                    }
                                    item {
                                        Top10Row(
                                            title = "Top 10 TV Series Today",
                                            items = trendingSeries,
                                            onMediaClick = onMediaClick,
                                            onWatchLaterChanged = {
                                                watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                            },
                                            focusMemory = focusMemory,
                                            rowKey = "series-top10",
                                            isFirstRow = seriesFirst == "top10"
                                        )
                                    }
                                    item {
                                        MediaCarouselRow(
                                            focusMemory = focusMemory,
                                            title = "Current Trending TV Series",
                                            items = trendingSeries,
                                            showRankBadge = false,
                                            onMediaClick = onMediaClick,
                                            onWatchLaterChanged = {
                                                watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                            }
                                        )
                                    }
                                    item {
                                        MediaCarouselRow(
                                            focusMemory = focusMemory,
                                            title = "Top Rated TV Series",
                                            items = topRatedSeries,
                                            showRankBadge = false,
                                            onMediaClick = onMediaClick,
                                            onWatchLaterChanged = {
                                                watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                            }
                                        )
                                    }
                                    item {
                                        MediaCarouselRow(
                                            focusMemory = focusMemory,
                                            title = "Popular TV Series",
                                            items = popularSeries,
                                            showRankBadge = false,
                                            onMediaClick = onMediaClick,
                                            onWatchLaterChanged = {
                                                watchLaterList = ServiceLocator.prefsStore.getWatchLater()
                                            }
                                        )
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

        run {
            val onMyList = selectedSection == "WatchLater"
            PhoneTopHeader(
                collapsed = if (isTv) false else headerCollapsed,
                title = if (onMyList) "My List" else "Stream",
                tabs = if (onMyList) MY_LIST_TABS else listOf("Home", "Movies", "Series", "Anime"),
                selectedTab = if (onMyList) myListFilter else selectedSection,
                onSelectTab = { tab -> if (onMyList) myListFilter = tab else goSection(tab) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = if (isTv) RAIL_COLLAPSED_WIDTH else 0.dp)
            )
            if (!isTv) {
                PhoneBottomNav(
                    selectedSection = selectedSection,
                    onHome = { goSection("Home") },
                    onSearch = goSearch,
                    onMyList = { goSection("WatchLater") },
                    onSettings = goSettings,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
        if (isTv) {
            // Floats over the page so opening it doesn't squash the rows
            SideRail(
                selectedSection = selectedSection,
                onSelectSection = goSection,
                onOpenSearch = goSearch,
                onOpenSettings = goSettings,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
    }
}

// ==========================================
// PHONE SHELL — collapsing header + floating bottom nav
// ==========================================

val MY_LIST_TABS = listOf("All", "Movies", "Series")

@Composable
fun PhoneTopHeader(
    collapsed: Boolean,
    title: String,
    tabs: List<String>,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val t = AppTheme.c
    val headerOnTv = rememberIsTv()
    // Fully solid once collapsed, a soft fade over the hero otherwise
    val solid by animateFloatAsState(
        targetValue = if (collapsed) 0.94f else 0f,
        animationSpec = tween(250),
        label = "headerSolid"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xCC000000), Color(0x80000000), Color.Transparent)
                )
            )
            .background(t.background.copy(alpha = solid))
            .statusBarsPadding()
    ) {
        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(220)) + fadeOut(tween(160))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 4.dp)
            ) {
                // Wordmark: small red play mark + "Stream"
                Box(
                    modifier = Modifier
                        .size(width = 9.dp, height = 22.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(t.accentGradient)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                )
            }
        }
        // Tabs sit in a dark frosted capsule so they stay readable over any poster.
        // Equal-width tabs that always fit the screen (no sideways scrolling).
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp)
                // Phone: tabs share the full width. TV: a compact capsule (a full-width one
                // would stretch across the whole television)
                .then(if (headerOnTv) Modifier else Modifier.fillMaxWidth())
                .clip(RoundedCornerShape(50))
                .background(Color(0x8C000000))
                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(50))
                .padding(3.dp)
        ) {
            tabs
                .forEach { key ->
                    val label = key
                    val selected = key == selectedTab
                    val pill by animateColorAsState(
                        targetValue = if (selected) Color.White else Color.Transparent,
                        animationSpec = tween(220),
                        label = "tabPill"
                    )
                    val txt by animateColorAsState(
                        targetValue = if (selected) Color.Black else Color(0xFFE0E0E0),
                        animationSpec = tween(220),
                        label = "tabText"
                    )
                    Text(
                        text = label,
                        color = txt,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .then(if (headerOnTv) Modifier else Modifier.weight(1f))
                            .tvFocusHighlight(RoundedCornerShape(50), focusedScale = 1.06f)
                            .clip(RoundedCornerShape(50))
                            .background(pill)
                            .clickable { onSelectTab(key) }
                            .padding(horizontal = if (headerOnTv) 22.dp else 0.dp, vertical = 7.dp)
                    )
                }
        }
    }
}

@Composable
fun PhoneBottomNav(
    selectedSection: String,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onMyList: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onHomeTabs = selectedSection in listOf("Home", "Movies", "Series", "Anime")
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 10.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xEB1C1C1E))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 6.dp)
    ) {
        BottomNavItem(Icons.Outlined.Home, "Home", onHomeTabs, onHome, Modifier.weight(1f))
        BottomNavItem(Icons.Outlined.Search, "Search", false, onSearch, Modifier.weight(1f))
        BottomNavItem(Icons.Outlined.BookmarkBorder, "My List", selectedSection == "WatchLater", onMyList, Modifier.weight(1f))
        BottomNavItem(Icons.Outlined.Settings, "Settings", false, onSettings, Modifier.weight(1f))
    }
}

@Composable
private fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (selected) Color(0x2EFFFFFF) else Color.Transparent,
        animationSpec = tween(220),
        label = "navBg"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) Color.White else Color(0xFF9A9A9A),
        animationSpec = tween(220),
        label = "navTint"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .padding(vertical = 6.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

// ==========================================
// SIDE RAIL (TV) — compact icons that open into a labelled menu when focused
// ==========================================

val RAIL_COLLAPSED_WIDTH = 64.dp

@Composable
fun SideRail(
    selectedSection: String,
    onSelectSection: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = AppTheme.c
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (expanded) 220.dp else RAIL_COLLAPSED_WIDTH,
        animationSpec = tween(220),
        label = "railWidth"
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .onFocusChanged { expanded = it.hasFocus }
            .background(if (expanded) Color(0xF5000000) else t.background)
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Wordmark: red bar, plus "Stream" when open
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(40.dp)
                .padding(start = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(t.accentGradient)
            )
            AnimatedVisibility(visible = expanded, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
                Text(
                    text = "Stream",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Same four places as the phone's bottom bar; Movies/Series/Anime are the tabs at the top
        val onHomeTabs = selectedSection in listOf("Home", "Movies", "Series", "Anime")
        RailItem(Icons.Outlined.Home, "Home", onHomeTabs, expanded) { onSelectSection("Home") }
        RailItem(Icons.Outlined.Search, "Search", false, expanded, onOpenSearch)
        RailItem(Icons.Outlined.BookmarkBorder, "My List", selectedSection == "WatchLater", expanded) { onSelectSection("WatchLater") }
        RailItem(Icons.Outlined.Settings, "Settings", false, expanded, onOpenSettings)
    }
}

@Composable
private fun RailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit
) {
    // Same language as the phone tabs: selected = white pill when open, soft pill when closed
    val bg by animateColorAsState(
        targetValue = when {
            selected && expanded -> Color.White
            selected -> Color(0x2EFFFFFF)
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "railBg"
    )
    val fg by animateColorAsState(
        targetValue = when {
            selected && expanded -> Color.Black
            selected -> Color.White
            else -> Color(0xFF9A9A9A)
        },
        animationSpec = tween(200),
        label = "railFg"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .tvFocusHighlight(RoundedCornerShape(50), focusedScale = 1.05f)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(start = 11.dp, end = 12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = fg, modifier = Modifier.size(22.dp))
        AnimatedVisibility(visible = expanded, enter = fadeIn(tween(180)), exit = fadeOut(tween(100))) {
            Text(
                text = label,
                color = if (selected) fg else Color(0xFFE6E6E6),
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(start = 14.dp)
            )
        }
    }
}

@Composable
fun AnimeCatalogSection(
    topInset: Dp = 0.dp,
    bottomInset: Dp = 16.dp,
    focusMemory: TvFocusMemory? = null,
    trendingAnime: List<MediaItemModel>,
    popularAnime: List<MediaItemModel>,
    anilistWatchingList: List<MediaItemModel>,
    anilistPlanningList: List<MediaItemModel>,
    onAnimeClick: (MediaItemModel) -> Unit,
    onWatchLaterChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var localTrending by remember(trendingAnime) { mutableStateOf(trendingAnime) }
    var localPopular by remember(popularAnime) { mutableStateOf(popularAnime) }
    var isFetchingNow by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (localTrending.isEmpty() || localPopular.isEmpty()) {
            isFetchingNow = true
            val tDef = async(Dispatchers.IO) { ServiceLocator.catalogRepository.fetchTrendingAnime() }
            val pDef = async(Dispatchers.IO) { ServiceLocator.catalogRepository.fetchPopularAnime() }
            val tRes = tDef.await()
            val pRes = pDef.await()
            if (tRes.isNotEmpty()) localTrending = tRes
            if (pRes.isNotEmpty()) localPopular = pRes
            isFetchingNow = false
        }
    }

    if (isFetchingNow && localTrending.isEmpty() && localPopular.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = AppTheme.c.accent)
                Text("Loading Anime Catalog (SUB)...", color = Color.LightGray, fontSize = 14.sp)
            }
        }
    } else if (localTrending.isEmpty() && localPopular.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Could not load Anime Catalog.", color = Color.LightGray, fontSize = 14.sp)
                Button(
                    onClick = {
                        isFetchingNow = true
                        scope.launch {
                            localTrending = ServiceLocator.catalogRepository.fetchTrendingAnime()
                            localPopular = ServiceLocator.catalogRepository.fetchPopularAnime()
                            isFetchingNow = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.c.accent),
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp))
                ) {
                    Text("Retry Loading Anime", color = Color.White)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp + topInset, bottom = bottomInset),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            val animeFirst = when {
                anilistWatchingList.isNotEmpty() -> "watching"
                anilistPlanningList.isNotEmpty() -> "planning"
                localTrending.isNotEmpty() -> "top10"
                else -> "popular"
            }
            if (anilistWatchingList.isNotEmpty()) {
                item {
                    MediaCarouselRow(
                        title = "Currently Watching (AniList Synced)",
                        isFirstRow = animeFirst == "watching",
                        items = anilistWatchingList,
                        showRankBadge = false,
                        onMediaClick = onAnimeClick,
                        onWatchLaterChanged = onWatchLaterChanged,
                        focusMemory = focusMemory
                    )
                }
            }
            if (anilistPlanningList.isNotEmpty()) {
                item {
                    MediaCarouselRow(
                        title = "Planning / Watchlist (AniList Synced)",
                        isFirstRow = animeFirst == "planning",
                        items = anilistPlanningList,
                        showRankBadge = false,
                        onMediaClick = onAnimeClick,
                        onWatchLaterChanged = onWatchLaterChanged,
                        focusMemory = focusMemory
                    )
                }
            }
            if (localTrending.isNotEmpty()) {
                item {
                    MediaCarouselRow(
                        title = "Top 10 Trending Anime (Sub)",
                        isFirstRow = animeFirst == "top10",
                        items = localTrending.take(10),
                        showRankBadge = true,
                        onMediaClick = onAnimeClick,
                        onWatchLaterChanged = onWatchLaterChanged,
                        focusMemory = focusMemory
                    )
                }
                item {
                    MediaCarouselRow(
                        title = "Trending Anime (Sub)",
                        items = localTrending,
                        showRankBadge = false,
                        onMediaClick = onAnimeClick,
                        onWatchLaterChanged = onWatchLaterChanged,
                        focusMemory = focusMemory
                    )
                }
            }
            if (localPopular.isNotEmpty()) {
                item {
                    MediaCarouselRow(
                        title = "All-Time Popular Anime (Sub)",
                        isFirstRow = animeFirst == "popular",
                        items = localPopular,
                        showRankBadge = false,
                        onMediaClick = onAnimeClick,
                        onWatchLaterChanged = onWatchLaterChanged,
                        focusMemory = focusMemory
                    )
                }
            }
        }
    }
}

// ==========================================
// 3. UNIFIED MEDIA & ANIME DETAILS SCREEN
//    (Uses StreamResolver Flow -> Creates PlaybackSession -> Navigates by sessionId)
// ==========================================

@Composable
fun MediaDetailsScreen(
    initialMedia: MediaItemModel,
    onBack: () -> Unit,
    onNavigateToSession: (String) -> Unit,
    onOpenPerson: (Int, String) -> Unit = { _, _ -> },
    onOpenCompany: (Int, String) -> Unit = { _, _ -> },
    onOpenMedia: (MediaItemModel) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var media by remember(initialMedia.id) { mutableStateOf(initialMedia) }
    var castList by remember(initialMedia.id) { mutableStateOf<List<com.victor.stream.domain.CastMember>>(emptyList()) }
    var companyList by remember(initialMedia.id) { mutableStateOf<List<com.victor.stream.domain.ProductionCompanyRef>>(emptyList()) }

    LaunchedEffect(initialMedia.id) {
        castList = ServiceLocator.catalogRepository.fetchCast(initialMedia)
        companyList = ServiceLocator.catalogRepository.fetchProductionCompanies(initialMedia)
    }
    var moreLikeThis by remember(initialMedia.id) { mutableStateOf<List<MediaItemModel>>(emptyList()) }
    LaunchedEffect(initialMedia.id) {
        moreLikeThis = runCatching { TmdbExtras.fetchRecommendations(initialMedia) }.getOrDefault(emptyList())
    }
    var inWatchLater by remember(media.id) {
        mutableStateOf(ServiceLocator.prefsStore.getWatchLater().any { it.id == media.id })
    }
    val continueEntry = remember(media.id) {
        ServiceLocator.prefsStore.getContinueWatching().firstOrNull { it.media.id == media.id }
    }

    // ---- TV remote focus ----
    val isTv = rememberIsTv()
    val playFocusRequester = remember { FocusRequester() }
    // Survives going into the player and coming back, so focus returns to the episode you played
    var lastPlayedEpisodeNum by rememberSaveable(initialMedia.id) { mutableIntStateOf(-1) }
    var lastPlayedSeasonNum by rememberSaveable(initialMedia.id) { mutableIntStateOf(-1) }
    var seasonFocusTarget by remember { mutableStateOf<Int?>(null) }
    var episodeFocusTarget by remember { mutableStateOf<Int?>(null) }
    var initialFocusDone by remember { mutableStateOf(false) }
    var detailsLoaded by remember { mutableStateOf(!initialMedia.isSeries) }
    val seasonsRowState = rememberLazyListState()
    val episodesRowState = rememberLazyListState()

    var seasonsList by remember { mutableStateOf<List<SeasonInfo>>(initialMedia.seasons) }
    var selectedSeasonNum by remember {
        mutableIntStateOf(if (lastPlayedSeasonNum > 0) lastPlayedSeasonNum else continueEntry?.seasonNumber ?: 1)
    }
    var episodesList by remember { mutableStateOf<List<EpisodeRef>>(emptyList()) }
    var isEpisodesLoading by remember { mutableStateOf(false) }

    var selectedChunkIndex by remember { mutableIntStateOf(0) }
    var seasonSortAscending by remember { mutableStateOf(true) }
    var episodeSortAscending by remember { mutableStateOf(true) }

    var isResolving by remember { mutableStateOf(false) }
    var resolveStatusText by remember { mutableStateOf("Resolving stream...") }
    var activeResolveJob by remember { mutableStateOf<Job?>(null) }
    var errorDialogMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialMedia.id) {
        if (media.isSeries) {
            val enriched = ServiceLocator.catalogRepository.fetchTvDetailsAndSeasons(media)
            media = enriched
            seasonsList = enriched.seasons
            if (seasonsList.isNotEmpty() && seasonsList.none { it.seasonNumber == selectedSeasonNum }) {
                selectedSeasonNum = seasonsList.first().seasonNumber
            }
        }
        detailsLoaded = true
    }

    LaunchedEffect(media.id, selectedSeasonNum) {
        if (media.isSeries) {
            isEpisodesLoading = true
            val loaded = ServiceLocator.catalogRepository.fetchSeasonEpisodes(media, selectedSeasonNum)
            episodesList = loaded
            selectedChunkIndex = 0
            ServiceLocator.sessionStore.putSeasonEpisodes(media.id, selectedSeasonNum, loaded)
            isEpisodesLoading = false
        }
    }

    val episodeChunks = remember(episodesList) { episodesList.chunked(50) }

    // TV: decide where the remote focus starts on this screen.
    //  - Movies: the Play (or Resume) button
    //  - Series: the episode you just watched, otherwise the selected season poster,
    //    otherwise the first episode poster
    LaunchedEffect(isTv, detailsLoaded, isEpisodesLoading, episodesList.size, seasonsList.size) {
        if (!isTv || initialFocusDone || !detailsLoaded) return@LaunchedEffect
        if (!media.isSeries) {
            delay(150)
            runCatching { playFocusRequester.requestFocus() }
            initialFocusDone = true
            return@LaunchedEffect
        }
        val episodesReady = !isEpisodesLoading && episodesList.isNotEmpty()
        when {
            lastPlayedEpisodeNum > 0 && lastPlayedSeasonNum == selectedSeasonNum && episodesReady -> {
                val chunkIdx = episodeChunks.indexOfFirst { chunk -> chunk.any { it.episodeNumber == lastPlayedEpisodeNum } }
                if (chunkIdx >= 0) selectedChunkIndex = chunkIdx
                episodeFocusTarget = lastPlayedEpisodeNum
                initialFocusDone = true
            }
            lastPlayedEpisodeNum <= 0 && seasonsList.size > 1 -> {
                seasonFocusTarget = selectedSeasonNum
                initialFocusDone = true
            }
            episodesReady -> {
                val firstEp = if (episodeSortAscending) {
                    episodeChunks.firstOrNull()?.minByOrNull { it.episodeNumber }
                } else {
                    episodeChunks.firstOrNull()?.maxByOrNull { it.episodeNumber }
                }
                episodeFocusTarget = firstEp?.episodeNumber
                initialFocusDone = true
            }
        }
    }

    fun startStreamResolution(episode: EpisodeRef?, startFromZero: Boolean, origin: PlayOrigin) {
        activeResolveJob?.cancel()
        ServiceLocator.streamResolver.cancelActive()
        isResolving = true
        resolveStatusText = if (episode != null) {
            "Connecting servers for ${media.title} S${episode.seasonNumber}:E${episode.episodeNumber}..."
        } else {
            "Connecting servers for ${media.title}..."
        }

        activeResolveJob = scope.launch {
            val plan = ServiceLocator.resolveStreamUseCase.buildPlan(media = media, episode = episode)
            if (plan.candidates.isEmpty()) {
                isResolving = false
                errorDialogMessage = "No compatible streaming servers found for \"${media.title}\"."
                return@launch
            }

            ServiceLocator.resolveStreamUseCase.resolve(plan, harvestAll = false).collect { event ->
                when (event) {
                    is ResolverEvent.Progress -> {
                        resolveStatusText = "Loading ${event.label} (${event.index}/${event.total})…"
                    }
                    is ResolverEvent.Captured -> {
                        isResolving = false
                        val savedPos = if (startFromZero) {
                            0L
                        } else {
                            ServiceLocator.prefsStore.getEpisodePositionMs(
                                media.id,
                                episode?.seasonNumber,
                                episode?.episodeNumber
                            )
                        }
                        val session = PlaybackSession(
                            id = UUID.randomUUID().toString(),
                            media = media,
                            episode = episode,
                            plan = plan,
                            stream = event.stream,
                            startAtMs = savedPos,
                            origin = origin
                        )
                        ServiceLocator.sessionStore.put(session)
                        if (media.isAnime) {
                            scope.launch(Dispatchers.IO) {
                                ServiceLocator.catalogRepository.syncProgressToCloud(
                                    media = media,
                                    inWatchlist = true,
                                    watchedEpisode = episode?.episodeNumber ?: 1
                                )
                            }
                        }
                        onNavigateToSession(session.id)
                    }
                    is ResolverEvent.CandidateFailed -> {
                        // Resolver automatically advances to the next candidate
                    }
                    is ResolverEvent.AllExhausted -> {
                        isResolving = false
                        errorDialogMessage = "Could not find an active stream across servers for \"${media.title}\". Please try again."
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeResolveJob?.cancel()
        }
    }

    // Remote Back while "Connecting servers..." cancels instead of leaving the page
    BackHandler(enabled = isResolving) {
        activeResolveJob?.cancel()
        ServiceLocator.streamResolver.cancelActive()
        isResolving = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.c.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val t = AppTheme.c
            val detailsContext = LocalContext.current

            // ---- Full-bleed backdrop with a floating back button ----
            Box(
                modifier = Modifier
                    .horizontalBleed(16.dp)
                    .fillMaxWidth()
                    .height(if (isTv) 340.dp else 260.dp)
            ) {
                AsyncImage(
                    model = media.backdropUrl.ifBlank { media.posterUrl },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF181818))
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color(0x99000000),
                                0.3f to Color.Transparent,
                                0.7f to Color.Transparent,
                                1f to t.background
                            )
                        )
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(12.dp)
                        .size(40.dp)
                        .tvFocusHighlight(CircleShape, focusedScale = 1.12f)
                        .clip(CircleShape)
                        .background(Color(0x66000000))
                        .clickable(onClick = onBack)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ---- Title block ----
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = media.title,
                    color = Color.White,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (!media.romajiTitle.isNullOrBlank() && media.romajiTitle != media.title) {
                    Text(
                        text = media.romajiTitle!!,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Meta line: type · year · [SUB] · seasons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val kindLabel = when {
                        media.isAnime && media.isSeries -> "Anime"
                        media.isAnime -> "Anime Movie"
                        media.isSeries -> "Series"
                        else -> "Movie"
                    }
                    Text(kindLabel, color = Color(0xFFB3B3B3), fontSize = 14.sp)
                    if (media.releaseYear.isNotBlank()) {
                        Text(media.releaseYear.take(4), color = Color(0xFFB3B3B3), fontSize = 14.sp)
                    }
                    if (media.isAnime) {
                        Text(
                            text = "SUB",
                            color = Color(0xFFD0D0D0),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .border(1.dp, Color(0xFF8A8A8A), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    if (media.isSeries && seasonsList.isNotEmpty()) {
                        Text(
                            text = if (seasonsList.size == 1) "1 Season" else "${seasonsList.size} Seasons",
                            color = Color(0xFFB3B3B3),
                            fontSize = 14.sp
                        )
                    }
                }
                Text(
                    text = "\u2605 " + String.format("%.1f", media.rating),
                    color = Color(0xFFB3B3B3),
                    fontSize = 14.sp
                )
                val langName = remember(media.originalLanguage) {
                    runCatching { java.util.Locale(media.originalLanguage).displayLanguage }.getOrDefault("")
                }
                if (langName.isNotBlank()) {
                    Text(
                        text = if (media.isAnime) "$langName \u00B7 English subtitles" else langName,
                        color = Color(0xFF8C8C8C),
                        fontSize = 13.sp
                    )
                }
            }

            // ---- Primary action: full-width white Play / Resume ----
            if (continueEntry != null) {
                DetailsWideButton(
                    text = if (continueEntry.seasonNumber != null && continueEntry.episodeNumber != null) {
                        "Resume S${continueEntry.seasonNumber}:E${continueEntry.episodeNumber}"
                    } else {
                        "Resume"
                    },
                    filled = true,
                    modifier = if (!media.isSeries) Modifier.focusRequester(playFocusRequester) else Modifier,
                    onClick = {
                        val epRef = if (media.isSeries) {
                            EpisodeRef(
                                seasonNumber = continueEntry.seasonNumber ?: 1,
                                episodeNumber = continueEntry.episodeNumber ?: 1,
                                title = continueEntry.episodeTitle ?: "Episode ${continueEntry.episodeNumber ?: 1}"
                            )
                        } else null
                        if (epRef != null) {
                            lastPlayedEpisodeNum = epRef.episodeNumber
                            lastPlayedSeasonNum = epRef.seasonNumber
                        }
                        startStreamResolution(epRef, startFromZero = false, origin = PlayOrigin.RESUME)
                    }
                )
            }
            if (!media.isSeries) {
                DetailsWideButton(
                    text = when {
                        continueEntry != null -> "Play from beginning"
                        media.isAnime -> "Play (SUB)"
                        else -> "Play"
                    },
                    filled = continueEntry == null,
                    modifier = if (continueEntry == null) Modifier.focusRequester(playFocusRequester) else Modifier,
                    onClick = {
                        startStreamResolution(
                            episode = null,
                            startFromZero = true,
                            origin = PlayOrigin.USER_PICK
                        )
                    }
                )
            }

            // ---- Overview (tap to expand) ----
            var overviewExpanded by remember(media.id) { mutableStateOf(false) }
            if (media.overview.isNotBlank()) {
                Text(
                    text = media.overview,
                    color = Color(0xFFE0E0E0),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    maxLines = if (overviewExpanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth(if (isTv) 0.65f else 1f)
                        .animateContentSize(tween(220))
                        .clickable { overviewExpanded = !overviewExpanded }
                )
            }

            // ---- Action row: My List · Trailer · Share ----
            Row(
                horizontalArrangement = Arrangement.spacedBy(30.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                DetailsActionItem(
                    icon = if (inWatchLater) Icons.Filled.Check else Icons.Filled.Add,
                    label = "My List",
                    onClick = {
                        if (inWatchLater) {
                            ServiceLocator.prefsStore.removeFromWatchLater(media.id)
                            inWatchLater = false
                            scope.launch {
                                ServiceLocator.catalogRepository.syncProgressToCloud(media, inWatchlist = false)
                            }
                        } else {
                            ServiceLocator.prefsStore.addToWatchLater(media)
                            inWatchLater = true
                            scope.launch {
                                ServiceLocator.catalogRepository.syncProgressToCloud(media, inWatchlist = true)
                            }
                        }
                    }
                )
                DetailsActionItem(
                    icon = Icons.Outlined.Theaters,
                    label = "Trailer",
                    onClick = {
                        scope.launch {
                            val key = runCatching { TmdbExtras.fetchTrailerKey(media) }.getOrNull()
                            if (key != null) {
                                runCatching {
                                    detailsContext.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key"))
                                    )
                                }
                            } else {
                                android.widget.Toast
                                    .makeText(detailsContext, "No trailer found", android.widget.Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                )
                DetailsActionItem(
                    icon = Icons.Outlined.Share,
                    label = "Share",
                    onClick = {
                        val link = when {
                            media.isAnime && media.anilistId != null -> "https://anilist.co/anime/${media.anilistId}"
                            media.isSeries -> "https://www.themoviedb.org/tv/${media.tmdbId ?: media.id}"
                            else -> "https://www.themoviedb.org/movie/${media.tmdbId ?: media.id}"
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${media.title} (${media.releaseYear.take(4)})\n$link")
                        }
                        runCatching { detailsContext.startActivity(Intent.createChooser(send, "Share")) }
                    }
                )
            }

            HorizontalDivider(color = Color(0x1FFFFFFF))

            if (castList.isNotEmpty()) {
                HorizontalDivider(color = Color.DarkGray)
                CastRow(cast = castList, onCastClick = { c -> onOpenPerson(c.id, c.name) })
            }

            if (companyList.isNotEmpty()) {
                ProductionRow(companies = companyList, onCompanyClick = { co -> onOpenCompany(co.id, co.name) })
            }

            if (media.isSeries) {
                HorizontalDivider(color = Color.DarkGray)

                if (seasonsList.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Seasons", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = { seasonSortAscending = !seasonSortAscending },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier
                                .tvFocusHighlight(RoundedCornerShape(50))
                                .height(30.dp)
                        ) {
                            Text(
                                text = if (seasonSortAscending) "1 → ${seasonsList.size}" else "${seasonsList.size} → 1",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }

                    val sortedSeasons = if (seasonSortAscending) {
                        seasonsList.sortedBy { it.seasonNumber }
                    } else {
                        seasonsList.sortedByDescending { it.seasonNumber }
                    }

                    LaunchedEffect(seasonFocusTarget, sortedSeasons) {
                        val target = seasonFocusTarget ?: return@LaunchedEffect
                        val idx = sortedSeasons.indexOfFirst { it.seasonNumber == target }
                        if (idx >= 0) runCatching { seasonsRowState.scrollToItem(idx) }
                    }

                    LazyRow(
                        state = seasonsRowState,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        items(sortedSeasons) { season ->
                            val isSelected = selectedSeasonNum == season.seasonNumber
                            val seasonRequester = remember { FocusRequester() }
                            LaunchedEffect(seasonFocusTarget) {
                                if (seasonFocusTarget == season.seasonNumber) {
                                    delay(80)
                                    runCatching { seasonRequester.requestFocus() }
                                    seasonFocusTarget = null
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .width(100.dp)
                                    .focusRequester(seasonRequester)
                                    .tvFocusHighlight(RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF2C1A1A) else AppTheme.c.surface)
                                    .then(
                                        if (isSelected) Modifier.border(2.dp, AppTheme.c.accent, RoundedCornerShape(8.dp))
                                        else Modifier
                                    )
                                    .clickable { selectedSeasonNum = season.seasonNumber }
                            ) {
                                AsyncImage(
                                    model = season.posterUrl,
                                    contentDescription = season.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(130.dp)
                                        .background(Color.DarkGray)
                                )
                                Text(
                                    text = season.name,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }
                    }
                }

                if (episodeChunks.size > 1) {
                    Text(text = "Episode Ranges", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        itemsIndexed(episodeChunks) { idx, chunk ->
                            val firstEp = chunk.first().episodeNumber
                            val lastEp = chunk.last().episodeNumber
                            val isSelected = selectedChunkIndex == idx
                            Button(
                                onClick = { selectedChunkIndex = idx },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) AppTheme.c.accent else AppTheme.c.surfaceHigh
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .tvFocusHighlight(RoundedCornerShape(8.dp))
                                    .height(32.dp)
                            ) {
                                Text("EP $firstEp – $lastEp", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                val currentChunk = episodeChunks.getOrElse(selectedChunkIndex) { episodesList }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Episodes (${episodesList.size})",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = { episodeSortAscending = !episodeSortAscending },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .tvFocusHighlight(RoundedCornerShape(50))
                            .height(30.dp)
                    ) {
                        Text(
                            text = if (episodeSortAscending) "Ascending ↑" else "Descending ↓",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }

                if (isEpisodesLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AppTheme.c.accent)
                    }
                } else {
                    val sortedEpisodes = if (episodeSortAscending) {
                        currentChunk.sortedBy { it.episodeNumber }
                    } else {
                        currentChunk.sortedByDescending { it.episodeNumber }
                    }

                    LaunchedEffect(episodeFocusTarget, sortedEpisodes) {
                        val target = episodeFocusTarget ?: return@LaunchedEffect
                        val idx = sortedEpisodes.indexOfFirst { it.episodeNumber == target }
                        if (idx >= 0) runCatching { episodesRowState.scrollToItem(idx) }
                    }

                    LazyRow(
                        state = episodesRowState,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 16.dp)
                    ) {
                        items(sortedEpisodes) { ep ->
                            val epRequester = remember { FocusRequester() }
                            LaunchedEffect(episodeFocusTarget) {
                                if (episodeFocusTarget == ep.episodeNumber) {
                                    delay(80)
                                    runCatching { epRequester.requestFocus() }
                                    episodeFocusTarget = null
                                }
                            }
                            val epSavedPos = remember(media.id, selectedSeasonNum, ep.episodeNumber) {
                                ServiceLocator.prefsStore.getEpisodePositionMs(
                                    media.id,
                                    selectedSeasonNum,
                                    ep.episodeNumber
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .width(145.dp)
                                    .focusRequester(epRequester)
                                    .tvFocusHighlight(RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AppTheme.c.surface)
                                    .clickable {
                                        lastPlayedEpisodeNum = ep.episodeNumber
                                        lastPlayedSeasonNum = selectedSeasonNum
                                        val startFresh = epSavedPos <= 3_000L
                                        startStreamResolution(
                                            episode = ep,
                                            startFromZero = startFresh,
                                            origin = PlayOrigin.USER_PICK
                                        )
                                    }
                            ) {
                                Box {
                                    AsyncImage(
                                        model = ep.stillUrl.ifBlank { media.posterUrl },
                                        contentDescription = ep.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(95.dp)
                                            .background(Color.DarkGray)
                                    )
                                    Text(
                                        text = "EP ${ep.episodeNumber}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(5.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AppTheme.c.accent)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    if (epSavedPos > 3_000L) {
                                        Text(
                                            text = "RESUME",
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xCC4CAF50))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = ep.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = ep.overview,
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (moreLikeThis.isNotEmpty()) {
                HorizontalDivider(color = Color(0x1FFFFFFF))
                Text("More like this", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val moreCols = if (isTv) 4 else 2
                    moreLikeThis.take(12).chunked(moreCols).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { rec ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .tvFocusHighlight(RoundedCornerShape(8.dp), focusedScale = 1.05f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenMedia(rec) }
                                ) {
                                    AsyncImage(
                                        model = rec.backdropUrl.ifBlank { rec.posterUrl },
                                        contentDescription = rec.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF181818))
                                    )
                                    Text(
                                        text = rec.title,
                                        color = Color(0xFFE6E6E6),
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                    )
                                }
                            }
                            repeat(moreCols - pair.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        if (isResolving) {
            ResolvingOverlay(
                statusText = resolveStatusText,
                onCancel = {
                    activeResolveJob?.cancel()
                    ServiceLocator.streamResolver.cancelActive()
                    isResolving = false
                }
            )
        }

        if (errorDialogMessage != null) {
            AlertDialog(
                onDismissRequest = { errorDialogMessage = null },
                title = { Text("Stream Not Found") },
                text = { Text(errorDialogMessage ?: "") },
                confirmButton = {
                    Button(
                        onClick = { errorDialogMessage = null },
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.c.accent),
                        modifier = Modifier.tvFocusHighlight(RoundedCornerShape(50))
                    ) { Text("OK", color = Color.White) }
                }
            )
        }
    }
}


/** Lets a child ignore its parent's horizontal padding (edge-to-edge images). */
fun Modifier.horizontalBleed(bleed: Dp): Modifier = this.layout { measurable, constraints ->
    val extra = bleed.roundToPx() * 2
    val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = maxW, maxWidth = maxW))
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
    layout(width, placeable.height) { placeable.place(-bleed.roundToPx(), 0) }
}

/** Full-width pill button used on the details page (white = primary). */
@Composable
fun DetailsWideButton(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .then(if (rememberIsTv()) Modifier.widthIn(max = 380.dp) else Modifier)
            .fillMaxWidth()
            .tvFocusHighlight(RoundedCornerShape(10.dp), focusedScale = 1.03f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) Color.White else Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = if (filled) Color.Black else Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = if (filled) Color.Black else Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Icon-over-label action used in the details action row. */
@Composable
fun DetailsActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .tvFocusHighlight(RoundedCornerShape(10.dp), focusedScale = 1.1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Crossfade(targetState = icon, animationSpec = tween(200), label = "detailsActionIcon") { ic ->
            Icon(imageVector = ic, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Text(
            text = label,
            color = Color(0xFFA6A6A6),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ==========================================
// 3b. CAST & PRODUCTION (rows + filmography/company screens)
// ==========================================

@Composable
fun CastRow(
    cast: List<com.victor.stream.domain.CastMember>,
    onCastClick: (com.victor.stream.domain.CastMember) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Cast", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
            items(cast) { member ->
                var focused by remember { mutableStateOf(false) }
                val castScale by animateFloatAsState(if (focused) 1.14f else 1f, label = "castScale")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(84.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .clickable { onCastClick(member) }
                        .padding(4.dp)
                        .graphicsLayer { scaleX = castScale; scaleY = castScale }
                ) {
                    // Bright ring + red glow makes it obvious which cast member is selected
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (focused) AppTheme.c.accent else Color.Transparent)
                            .border(
                                if (focused) 3.dp else 0.dp,
                                if (focused) Color.White else Color.Transparent,
                                RoundedCornerShape(50)
                            )
                            .padding(if (focused) 3.dp else 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (member.profileUrl.isNotBlank()) {
                            AsyncImage(
                                model = member.profileUrl,
                                contentDescription = member.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.DarkGray)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF2A2A2A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = member.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = member.name,
                        color = if (focused) Color.White else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                    if (member.character.isNotBlank()) {
                        Text(
                            text = member.character,
                            color = Color.Gray,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProductionRow(
    companies: List<com.victor.stream.domain.ProductionCompanyRef>,
    onCompanyClick: (com.victor.stream.domain.ProductionCompanyRef) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Production", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
            items(companies) { co ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .tvFocusHighlight(RoundedCornerShape(10.dp))
                        .background(AppTheme.c.surfaceHigh)
                        .clickable { onCompanyClick(co) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (co.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = co.logoUrl,
                            contentDescription = co.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(22.dp)
                                .widthIn(max = 60.dp)
                                .background(Color.White, RoundedCornerShape(4.dp))
                                .padding(2.dp)
                        )
                    }
                    Text(
                        text = co.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Shared grid screen used by both the actor-filmography and company-titles pages. */
@Composable
private fun TitleGridScreen(
    title: String,
    subtitle: String,
    isLoading: Boolean,
    titles: List<MediaItemModel>,
    onBack: () -> Unit,
    onMediaClick: (MediaItemModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.c.background)
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .tvFocusHighlight(RoundedCornerShape(50))
                    .height(36.dp)
            ) {
                Text("← Back", color = Color.White, fontSize = 13.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(text = subtitle, color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
        HorizontalDivider(color = AppTheme.c.glassBorder)

        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppTheme.c.accent)
            }
            titles.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing to show here yet.", color = Color.Gray, fontSize = 14.sp)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(titles) { item ->
                    PosterCard(
                        item = item,
                        isWatchLaterScreen = false,
                        onClick = { onMediaClick(item) },
                        onWatchLaterChanged = {}
                    )
                }
            }
        }
    }
}

@Composable
fun PersonFilmographyScreen(
    personId: Int,
    personName: String,
    onBack: () -> Unit,
    onMediaClick: (MediaItemModel) -> Unit
) {
    var items by remember(personId) { mutableStateOf<List<MediaItemModel>>(emptyList()) }
    var isLoading by remember(personId) { mutableStateOf(true) }
    LaunchedEffect(personId) {
        isLoading = true
        items = ServiceLocator.catalogRepository.fetchPersonFilmography(personId)
        isLoading = false
    }
    TitleGridScreen(
        title = personName,
        subtitle = if (!isLoading) "${items.size} titles" else "Loading…",
        isLoading = isLoading,
        titles = items,
        onBack = onBack,
        onMediaClick = onMediaClick
    )
}

@Composable
fun CompanyTitlesScreen(
    companyId: Int,
    companyName: String,
    onBack: () -> Unit,
    onMediaClick: (MediaItemModel) -> Unit
) {
    var items by remember(companyId) { mutableStateOf<List<MediaItemModel>>(emptyList()) }
    var isLoading by remember(companyId) { mutableStateOf(true) }
    LaunchedEffect(companyId) {
        isLoading = true
        items = ServiceLocator.catalogRepository.fetchCompanyTitles(companyId)
        isLoading = false
    }
    TitleGridScreen(
        title = companyName,
        subtitle = if (!isLoading) "${items.size} titles" else "Loading…",
        isLoading = isLoading,
        titles = items,
        onBack = onBack,
        onMediaClick = onMediaClick
    )
}

// ==========================================
// 4. SEARCH SCREEN
// ==========================================

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onMediaClick: (MediaItemModel) -> Unit,
    initialType: String = "All",
    initialGenre: String = "All"
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val searchContext = LocalContext.current
    var queryInput by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MediaItemModel>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var recentSearches by remember { mutableStateOf(RecentSearchStore.get(searchContext)) }

    var selectedType by remember { mutableStateOf(initialType) }
    var selectedGenre by remember { mutableStateOf(initialGenre) }
    var selectedYear by remember { mutableStateOf("All") }

    var typeExpanded by remember { mutableStateOf(false) }
    var genreExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }

    var liveJob by remember { mutableStateOf<Job?>(null) }
    fun runSearch(saveRecent: Boolean) {
        if (saveRecent && queryInput.isNotBlank()) {
            recentSearches = RecentSearchStore.add(searchContext, queryInput)
        }
        isSearching = true
        hasSearched = true
        liveJob?.cancel()
        liveJob = scope.launch {
            val results = ServiceLocator.catalogRepository.searchCatalog(
                query = queryInput,
                selectedType = selectedType,
                selectedGenre = selectedGenre,
                selectedYear = selectedYear
            )
            searchResults = results
            isSearching = false
        }
    }
    // Search as you type: wait for a short pause, then search without closing the keyboard
    LaunchedEffect(queryInput) {
        if (queryInput.trim().length >= 2) {
            delay(450)
            runSearch(saveRecent = false)
        } else if (queryInput.isEmpty() && selectedType == "All" && selectedGenre == "All" && selectedYear == "All") {
            liveJob?.cancel()
            hasSearched = false
            isSearching = false
            searchResults = emptyList()
        }
    }

    fun performSearch() {
        focusManager.clearFocus()
        keyboardController?.hide()
        if (queryInput.isNotBlank()) {
            recentSearches = RecentSearchStore.add(searchContext, queryInput)
        }
        isSearching = true
        hasSearched = true
        scope.launch {
            val results = ServiceLocator.catalogRepository.searchCatalog(
                query = queryInput,
                selectedType = selectedType,
                selectedGenre = selectedGenre,
                selectedYear = selectedYear
            )
            searchResults = results
            isSearching = false
        }
    }

    // Opened from a genre chip: run the filtered browse right away
    LaunchedEffect(Unit) {
        if (initialType != "All" || initialGenre != "All") performSearch()
    }

    // Genre pill tapped -> its own browse page (year + rating filters on top)
    var openGenreId by rememberSaveable { mutableStateOf<Int?>(null) }
    val openGenre = openGenreId
    if (openGenre != null) {
        GenreBrowsePage(
            genreId = openGenre,
            genreName = GENRE_MAP[openGenre] ?: "Genre",
            onBack = { openGenreId = null },
            onMediaClick = onMediaClick
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.c.background)
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .tvFocusHighlight(CircleShape, focusedScale = 1.12f)
                    .clip(CircleShape)
                    .clickable {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onBack()
                    }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            OutlinedTextField(
                value = queryInput,
                onValueChange = { queryInput = it },
                placeholder = {
                    Text(
                        "Search movies, series, anime...",
                        color = Color(0xFF8A8A8A),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                },
                trailingIcon = {
                    if (queryInput.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear",
                            tint = Color(0xFFB0B0B0),
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable { queryInput = "" }
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF111111),
                    unfocusedContainerColor = Color(0xFF161616),
                    focusedBorderColor = Color(0xFFE6E6E6),
                    unfocusedBorderColor = Color(0x1FFFFFFF),
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterDropdownPill(
                label = "Type",
                selectedValue = selectedType,
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it },
                options = listOf("All", "Movies", "Series", "Anime"),
                onSelect = {
                    selectedType = it
                    performSearch()
                }
            )
            FilterDropdownPill(
                label = "Genre",
                selectedValue = selectedGenre,
                expanded = genreExpanded,
                onExpandedChange = { genreExpanded = it },
                options = listOf("All") + GENRE_MAP.values.sorted(),
                onSelect = {
                    selectedGenre = it
                    performSearch()
                }
            )
            FilterDropdownPill(
                label = "Year",
                selectedValue = selectedYear,
                expanded = yearExpanded,
                onExpandedChange = { yearExpanded = it },
                options = listOf("All") + (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) downTo 1980).map { it.toString() },
                onSelect = {
                    selectedYear = it
                    performSearch()
                }
            )
            if (selectedType != "All" || selectedGenre != "All" || selectedYear != "All") {
                FilterChipPill(text = "Reset", selected = false, onClick = {
                    selectedType = "All"
                    selectedGenre = "All"
                    selectedYear = "All"
                    if (queryInput.isBlank()) {
                        hasSearched = false
                        searchResults = emptyList()
                    } else {
                        performSearch()
                    }
                })
            }
        }


        if (isSearching) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 105.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize()
            ) {
                items(12) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .shimmer(RoundedCornerShape(8.dp))
                    )
                }
            }
        } else if (!hasSearched) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                if (recentSearches.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF141414))
                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\u21BA  Recent searches",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Clear all",
                            color = Color(0xFFB0B0B0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .tvFocusHighlight(RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { recentSearches = RecentSearchStore.clear(searchContext) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recentSearches) { recent ->
                            Text(
                                text = recent,
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .tvFocusHighlight(RoundedCornerShape(50))
                                    .clip(RoundedCornerShape(50))
                                    .background(AppTheme.c.surfaceHigh)
                                    .border(1.dp, AppTheme.c.glassBorder, RoundedCornerShape(50))
                                    .clickable {
                                        queryInput = recent
                                        performSearch()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
                }
                Text(
                    text = "Browse by genre",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                GenrePillGrid(onGenreClick = { id, _ ->
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    openGenreId = id
                })
            }
        } else if (searchResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No results found matching your filters.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 105.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(searchResults) { item ->
                    PosterCard(
                        item = item,
                        isWatchLaterScreen = false,
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            onMediaClick(item)
                        },
                        onWatchLaterChanged = {}
                    )
                }
            }
        }
    }
}

@Composable
fun FilterDropdownPill(
    label: String,
    selectedValue: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = selectedValue != "All"
    Column(modifier = modifier) {
        Box {
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .tvFocusHighlight(RoundedCornerShape(50), focusedScale = 1.04f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isActive) Color.White else Color(0xFF1A1A1A))
                    .border(1.dp, if (isActive) Color.Transparent else Color(0x1FFFFFFF), RoundedCornerShape(50))
                    .clickable { onExpandedChange(true) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isActive) selectedValue else label,
                    color = if (isActive) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "  \u25BE",
                    color = if (isActive) Color.Black else Color(0xFF9A9A9A),
                    fontSize = 12.sp
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .background(AppTheme.c.surfaceHigh)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                color = if (option == selectedValue) AppTheme.c.accent else Color.White,
                                fontWeight = if (option == selectedValue) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        onClick = {
                            onSelect(option)
                            onExpandedChange(false)
                        }
                    )
                }
            }
        }
    }
}

// ==========================================
// 5. SETTINGS & ACCOUNTS SCREEN (TMDB + ANILIST)
// ==========================================


// ==========================================
// SETTINGS — APPEARANCE / THEME PICKER
// ==========================================

/** Small rounded icon tile used at the start of each settings card header. */
@Composable
fun SettingsIconTile(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color? = null) {
    val t = AppTheme.c
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(t.surfaceHigh)
            .border(1.dp, t.glassBorder, RoundedCornerShape(12.dp))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: t.accent,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingsScreen(
    onAccountChanged: () -> Unit,
    onBack: () -> Unit
) {
    val t = AppTheme.c
    var accountPage by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = accountPage != null) { accountPage = null }

    Crossfade(targetState = accountPage, animationSpec = tween(240), label = "settingsPage") { page ->
        when (page) {
            "tmdb" -> AccountSubPage(title = "TMDB Account", onBack = { accountPage = null }) {
                TmdbSettingsCard(onAccountChanged = onAccountChanged)
            }
            "anilist" -> AccountSubPage(title = "AniList Account", onBack = { accountPage = null }) {
                AniListSettingsCard(onAccountChanged = onAccountChanged)
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(t.background)
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(text = "Accounts & tools", color = t.textFaint, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.tvFocusHighlight(RoundedCornerShape(50)).height(36.dp),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, t.glassBorder),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text("\u2190 Done", color = Color.White, fontSize = 13.sp)
                    }
                }

                AccountsCard(onOpen = { accountPage = it })

                WatchStatsCard()

                DebugCaptureCard()
            }
        }
    }
}


/** Little "your watching" stats card built from local lists only. */
@Composable
fun WatchStatsCard() {
    val t = AppTheme.c
    val continueEntries = remember { ServiceLocator.prefsStore.getContinueWatching() }
    val watchLaterCount = remember { ServiceLocator.prefsStore.getWatchLater().size }
    val watchedMinutes = remember(continueEntries) {
        (continueEntries.sumOf { it.positionMs } / 60000L).toInt()
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = t.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, t.glassBorder, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Your watching", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = "${continueEntries.size}",
                    label = "In progress",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = "$watchLaterCount",
                    label = "Watch Later",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = if (watchedMinutes >= 60) "${watchedMinutes / 60}h ${watchedMinutes % 60}m" else "${watchedMinutes}m",
                    label = "Watched*",
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "*Time watched across titles currently in progress",
                color = t.textFaint,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    val t = AppTheme.c
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(t.surfaceHigh)
            .padding(vertical = 12.dp)
    ) {
        Text(value, color = t.accent, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = t.textSecondary, fontSize = 11.sp)
    }
}

/** The "Accounts" list — tap an account to open its own login page. */
@Composable
fun AccountsCard(onOpen: (String) -> Unit) {
    val t = AppTheme.c
    // Re-read link status every time this card comes back on screen
    var statusTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { statusTick += 1 }
    val tmdbLinked = remember(statusTick) { ServiceLocator.prefsStore.getTmdbAuth().sessionId != null }
    val aniLinked = remember(statusTick) { ServiceLocator.prefsStore.getAniListAuth().token != null }

    Card(
        colors = CardDefaults.cardColors(containerColor = t.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, t.glassBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                text = "Accounts",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
            )
            AccountRow(
                icon = Icons.Outlined.Theaters,
                iconTint = null,
                title = "TMDB",
                subtitle = "Movies & series watchlist sync",
                linked = tmdbLinked,
                onClick = { onOpen("tmdb") }
            )
            HorizontalDivider(
                color = t.glassBorder,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            AccountRow(
                icon = Icons.Outlined.CloudSync,
                iconTint = Color(0xFF02A9FF),
                title = "AniList",
                subtitle = "Watching & planning anime sync",
                linked = aniLinked,
                onClick = { onOpen("anilist") }
            )
        }
    }
}

@Composable
private fun AccountRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color?,
    title: String,
    subtitle: String,
    linked: Boolean,
    onClick: () -> Unit
) {
    val t = AppTheme.c
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusHighlight(RoundedCornerShape(12.dp), focusedScale = 1.02f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        SettingsIconTile(icon, tint = iconTint)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = t.textFaint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (linked) Color(0x334CAF50) else Color(0x33FF9800)
        ) {
            Text(
                text = if (linked) "CONNECTED" else "NOT LINKED",
                color = if (linked) Color(0xFF4CAF50) else Color(0xFFFF9800),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        Text("\u203A", color = t.textFaint, fontSize = 22.sp)
    }
}

/** Full page wrapper for one account's login/management screen. */
@Composable
fun AccountSubPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val t = AppTheme.c
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .tvFocusHighlight(CircleShape, focusedScale = 1.12f)
                    .clip(CircleShape)
                    .background(t.surfaceHigh)
                    .border(1.dp, t.glassBorder, CircleShape)
                    .clickable(onClick = onBack)
            ) {
                Text("\u2190", color = Color.White, fontSize = 16.sp)
            }
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        content()
    }
}

/** TMDB login / logout card (shown inside its own page). */
@Composable
fun TmdbSettingsCard(onAccountChanged: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var tmdbAuth by remember { mutableStateOf(ServiceLocator.prefsStore.getTmdbAuth()) }
    var tmdbUsernameInput by remember { mutableStateOf("") }
    var tmdbPasswordInput by remember { mutableStateOf("") }
    var isTmdbLoading by remember { mutableStateOf(false) }
    var tmdbStatusMessage by remember { mutableStateOf<String?>(null) }

        Card(
            colors = CardDefaults.cardColors(containerColor = AppTheme.c.surface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AppTheme.c.glassBorder, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsIconTile(Icons.Outlined.Theaters)
                        Column {
                        Text("TMDB Account", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Syncs Movies & Series Watchlists", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (tmdbAuth.sessionId != null) Color(0x334CAF50) else Color(0x33FF9800)
                    ) {
                        Text(
                            text = if (tmdbAuth.sessionId != null) "CONNECTED" else "NOT LINKED",
                            color = if (tmdbAuth.sessionId != null) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (tmdbAuth.sessionId != null) {
                    Text(
                        text = "Signed in as: ${tmdbAuth.username ?: "TMDB User"}",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    OutlinedButton(
                        onClick = {
                            ServiceLocator.prefsStore.saveTmdbAuth(null, 0, null)
                            tmdbAuth = ServiceLocator.prefsStore.getTmdbAuth()
                            tmdbStatusMessage = "Logged out of TMDB."
                            onAccountChanged()
                        },
                        modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp)).height(36.dp)
                    ) {
                        Text("Log Out of TMDB", color = Color(0xFFFF5252), fontSize = 12.sp)
                    }
                } else {
                    OutlinedTextField(
                        value = tmdbUsernameInput,
                        onValueChange = { tmdbUsernameInput = it },
                        placeholder = { Text("TMDB Username", color = Color.Gray, fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tmdbPasswordInput,
                        onValueChange = { tmdbPasswordInput = it },
                        placeholder = { Text("TMDB Password", color = Color.Gray, fontSize = 13.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (tmdbUsernameInput.isBlank() || tmdbPasswordInput.isBlank()) {
                                tmdbStatusMessage = "Please enter both TMDB username and password."
                                return@Button
                            }
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            isTmdbLoading = true
                            tmdbStatusMessage = null
                            scope.launch {
                                val res = ServiceLocator.catalogRepository.loginToTmdb(tmdbUsernameInput, tmdbPasswordInput)
                                isTmdbLoading = false
                                if (res != null) {
                                    ServiceLocator.prefsStore.saveTmdbAuth(res.first, res.second, res.third)
                                    tmdbAuth = ServiceLocator.prefsStore.getTmdbAuth()
                                    tmdbPasswordInput = ""
                                    tmdbStatusMessage = "Successfully linked TMDB account (${res.third})!"
                                    onAccountChanged()
                                } else {
                                    tmdbStatusMessage = "TMDB Login failed: Check your username & password."
                                }
                            }
                        },
                        enabled = !isTmdbLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.c.accent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp))
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        if (isTmdbLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Sign In to TMDB", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (tmdbStatusMessage != null) {
                    Text(text = tmdbStatusMessage!!, color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AniListSettingsCard(onAccountChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var aniState by remember { mutableStateOf(ServiceLocator.prefsStore.getAniListAuth()) }
    var tokenInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showOAuthModal by remember { mutableStateOf(false) }

    fun verifyToken(raw: String) {
        val cleaned = PrefsStore.extractCleanAniListToken(raw)
        if (cleaned.isBlank()) {
            statusMessage = "Invalid token."
            return
        }
        isLoading = true
        statusMessage = "Verifying with AniList..."
        scope.launch {
            val viewer = ServiceLocator.catalogRepository.verifyAniListToken(cleaned)
            isLoading = false
            if (viewer != null) {
                ServiceLocator.prefsStore.saveAniListAuth(cleaned, viewer.first, viewer.second)
                aniState = ServiceLocator.prefsStore.getAniListAuth()
                tokenInput = ""
                statusMessage = "Connected as ${viewer.second}!"
                onAccountChanged()
            } else {
                statusMessage = "Token rejected by AniList."
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = AppTheme.c.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.c.glassBorder, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsIconTile(Icons.Outlined.CloudSync, tint = Color(0xFF02A9FF))
                    Column {
                    Text("AniList Account", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Syncs Watching & Planning Anime Lists", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (aniState.token != null) Color(0x334CAF50) else Color(0x33FF9800)
                ) {
                    Text(
                        text = if (aniState.token != null) "CONNECTED" else "NOT LINKED",
                        color = if (aniState.token != null) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (aniState.token != null) {
                Text(
                    text = "Signed in as: ${aniState.username ?: "AniList User"}",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
                OutlinedButton(
                    onClick = {
                        ServiceLocator.prefsStore.saveAniListAuth(null, 0, null)
                        aniState = ServiceLocator.prefsStore.getAniListAuth()
                        statusMessage = "Logged out of AniList."
                        onAccountChanged()
                    },
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp)).height(36.dp)
                ) {
                    Text("Log Out of AniList", color = Color.LightGray, fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = { showOAuthModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF02A9FF)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp))
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("🔐 Login", color = Color.White, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    placeholder = { Text("Or paste AniList token here", color = Color.Gray, fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { verifyToken(tokenInput) },
                    enabled = !isLoading && tokenInput.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp))
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Text("Verify Pasted Token", color = Color.White)
                }
            }
            if (statusMessage != null) {
                Text(text = statusMessage!!, color = Color(0xFF4CAF50), fontSize = 12.sp)
            }
        }
    }

    if (showOAuthModal) {
        Dialog(
            onDismissRequest = { showOAuthModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                shape = RoundedCornerShape(14.dp),
                color = AppTheme.c.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppTheme.c.surfaceHigh)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AniList OAuth Login", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showOAuthModal = false }, modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp))) {
                            Text("✕ Close", color = Color.LightGray)
                        }
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val reqUrl = request?.url?.toString() ?: return false
                                        if (reqUrl.contains("access_token=")) {
                                            showOAuthModal = false
                                            verifyToken(reqUrl)
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        if (url != null && url.contains("access_token=")) {
                                            showOAuthModal = false
                                            verifyToken(url)
                                        }
                                    }
                                }
                                loadUrl(PrefsStore.ANILIST_OAUTH_URL)
                            }
                        }
                    )
                }
            }
        }
    }
}