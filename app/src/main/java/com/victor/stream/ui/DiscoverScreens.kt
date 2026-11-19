package com.victor.stream.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.victor.stream.data.GENRE_MAP
import com.victor.stream.domain.MediaItemModel
import com.victor.stream.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// ==========================================
// HOME — "Studios & Streaming" marquee rows
// ==========================================

/** Two gently auto-scrolling rows of picture tiles: studios, then streaming services. */
@Composable
fun BrandRows(onOpenBrand: (Brand) -> Unit) {
    val isTv = rememberIsTv()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Studios & Streaming",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
        // On TV the remote moves through the tiles, so no auto-scroll there
        BrandMarqueeRow(brands = Brands.studios, driftRight = true, autoScroll = !isTv, onOpenBrand = onOpenBrand)
        BrandMarqueeRow(brands = Brands.providers, driftRight = false, autoScroll = !isTv, onOpenBrand = onOpenBrand)
    }
}

@Composable
private fun BrandMarqueeRow(
    brands: List<Brand>,
    driftRight: Boolean,
    autoScroll: Boolean,
    onOpenBrand: (Brand) -> Unit
) {
    // "Endless" row: many repeats, starting in the middle so it can drift either way
    val repeats = if (autoScroll) 400 else 1
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (autoScroll) brands.size * (repeats / 2) else 0
    )
    if (autoScroll) {
        LaunchedEffect(Unit) {
            while (isActive) {
                try {
                    listState.animateScrollBy(
                        value = if (driftRight) -1200f else 1200f,
                        animationSpec = tween(durationMillis = 30000, easing = LinearEasing)
                    )
                } catch (e: CancellationException) {
                    if (!isActive) throw e
                    // The user grabbed the row: let them browse, then resume drifting
                    delay(3000)
                }
            }
        }
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(brands.size * repeats) { index ->
            BrandTile(brand = brands[index % brands.size], onClick = onOpenBrand)
        }
    }
}

@Composable
fun BrandTile(brand: Brand, onClick: (Brand) -> Unit) {
    var cover by remember(brand.key) { mutableStateOf(TmdbExtras.cachedBrandCover(brand)) }
    LaunchedEffect(brand.key) {
        if (cover == null) cover = runCatching { TmdbExtras.brandCover(brand) }.getOrNull()
    }
    val brandColor = Color(brand.color)
    Box(
        modifier = Modifier
            .width(210.dp)
            .height(118.dp)
            .tvFocusHighlight(RoundedCornerShape(16.dp), focusedScale = 1.06f)
            .clip(RoundedCornerShape(16.dp))
            .background(brandColor.copy(alpha = 0.35f))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(16.dp))
            .clickable { onClick(brand) }
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = brand.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Brand colour washes in from the left so the name always reads clearly
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to brandColor.copy(alpha = 0.92f),
                        0.55f to brandColor.copy(alpha = 0.35f),
                        1f to Color.Transparent
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0x99000000)))
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = if (brand.kind == BrandKind.STUDIO) "STUDIO" else "STREAMING",
                color = Color(0xCCFFFFFF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = brand.name,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==========================================
// GENRES — flat icon + name pills
// ==========================================

fun genreIcon(genreId: Int): ImageVector = when (genreId) {
    28 -> Icons.Outlined.Bolt
    12 -> Icons.Outlined.Explore
    16 -> Icons.Outlined.Animation
    35 -> Icons.Outlined.SentimentVerySatisfied
    80 -> Icons.Outlined.Gavel
    99 -> Icons.Outlined.Videocam
    18 -> Icons.Outlined.TheaterComedy
    10751 -> Icons.Outlined.FamilyRestroom
    14 -> Icons.Outlined.AutoFixHigh
    36 -> Icons.Outlined.HistoryEdu
    27 -> Icons.Outlined.NightsStay
    10402 -> Icons.Outlined.MusicNote
    9648 -> Icons.Outlined.Visibility
    10749 -> Icons.Filled.FavoriteBorder
    878 -> Icons.Outlined.Science
    10770 -> Icons.Outlined.LiveTv
    53 -> Icons.Outlined.Whatshot
    10752 -> Icons.Outlined.Shield
    37 -> Icons.Outlined.Landscape
    else -> Icons.Outlined.Explore
}

/** Genres in a friendly order (popular ones first). */
val BROWSE_GENRES: List<Pair<Int, String>> = listOf(
    28, 35, 10749, 14, 27, 878, 53, 12, 16, 18, 80, 9648, 10751, 99, 36, 10402, 10752, 37, 10770
).mapNotNull { id -> GENRE_MAP[id]?.let { id to it } }

/** Two-column grid of flat pill-shaped genre buttons (icon + name). */
@Composable
fun GenrePillGrid(onGenreClick: (Int, String) -> Unit) {
    val t = AppTheme.c
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BROWSE_GENRES.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (id, name) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .tvFocusHighlight(RoundedCornerShape(50), focusedScale = 1.04f)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF161616))
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(50))
                            .clickable { onGenreClick(id, name) }
                            .padding(horizontal = 16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(t.accent.copy(alpha = 0.16f))
                        ) {
                            Icon(
                                imageVector = genreIcon(id),
                                contentDescription = null,
                                tint = t.accent,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Text(
                            text = name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// ==========================================
// BROWSE PAGES — brand collection & genre
// ==========================================

@Composable
fun BrandCollectionScreen(
    brandKey: String,
    onBack: () -> Unit,
    onMediaClick: (MediaItemModel) -> Unit
) {
    val brand = Brands.byKey(brandKey) ?: run {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var cover by remember(brand.key) { mutableStateOf(TmdbExtras.cachedBrandCover(brand)) }
    LaunchedEffect(brand.key) {
        if (cover == null) cover = runCatching { TmdbExtras.brandCover(brand) }.getOrNull()
    }
    DiscoverGridPage(
        title = brand.name,
        kicker = if (brand.kind == BrandKind.STUDIO) "STUDIO" else "STREAMING",
        accent = Color(brand.color),
        coverUrl = cover,
        icon = null,
        hasSeries = true,
        startOnSeries = brand.kind == BrandKind.PROVIDER,
        onBack = onBack,
        onMediaClick = onMediaClick,
        load = { isTv, page, year, rating -> TmdbExtras.discoverBrand(brand, isTv, page, year, rating) }
    )
}

@Composable
fun GenreBrowsePage(
    genreId: Int,
    genreName: String,
    onBack: () -> Unit,
    onMediaClick: (MediaItemModel) -> Unit
) {
    DiscoverGridPage(
        title = genreName,
        kicker = "GENRE",
        accent = AppTheme.c.accent,
        coverUrl = null,
        icon = genreIcon(genreId),
        hasSeries = TmdbExtras.tvGenreFor(genreId) != null,
        startOnSeries = false,
        onBack = onBack,
        onMediaClick = onMediaClick,
        load = { isTv, page, year, rating -> TmdbExtras.discoverGenre(genreId, isTv, page, year, rating) }
    )
}

private val RATING_OPTIONS: List<Pair<String, Double?>> =
    listOf("Any" to null, "6+" to 6.0, "7+" to 7.0, "8+" to 8.0)

/**
 * Shared browse page: header, Movies/Series switch, year + rating filters on
 * top, and an endless 3-column poster grid.
 */
@Composable
fun DiscoverGridPage(
    title: String,
    kicker: String,
    accent: Color,
    coverUrl: String?,
    icon: ImageVector?,
    hasSeries: Boolean,
    startOnSeries: Boolean,
    onBack: () -> Unit,
    onMediaClick: (MediaItemModel) -> Unit,
    load: suspend (isTv: Boolean, page: Int, year: Int?, minRating: Double?) -> List<MediaItemModel>
) {
    val t = AppTheme.c
    var seriesTab by rememberSaveable { mutableStateOf(startOnSeries && hasSeries) }
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var ratingIndex by rememberSaveable { mutableIntStateOf(0) }
    var yearMenuOpen by remember { mutableStateOf(false) }

    var items by remember { mutableStateOf<List<MediaItemModel>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var endReached by remember { mutableStateOf(false) }
    val minRating = RATING_OPTIONS[ratingIndex].second

    LaunchedEffect(seriesTab, year, ratingIndex) {
        loading = true
        items = emptyList()
        page = 1
        endReached = false
        val first = runCatching { load(seriesTab, 1, year, minRating) }.getOrDefault(emptyList())
        items = first
        endReached = first.isEmpty()
        loading = false
    }

    BackHandler(onBack = onBack)
    val gridState = rememberLazyGridState()
    val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 105.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(t.background)
            .background(Brush.verticalGradient(0f to accent.copy(alpha = 0.55f), 0.35f to t.background))
    ) {
        // ---- Header ----
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.statusBarsPadding()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(40.dp)
                        .tvFocusHighlight(CircleShape, focusedScale = 1.12f)
                        .clip(CircleShape)
                        .background(Color(0x59000000))
                        .clickable(onClick = onBack)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF181818))
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (icon != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.2f))
                        ) {
                            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
                        }
                    }
                    Column {
                        Text(kicker, color = Color(0xB3FFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // ---- Filters on top: Movies/Series, year, rating ----
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                if (hasSeries) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x8C000000))
                            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(50))
                            .padding(4.dp)
                    ) {
                        SegmentPill("Movies", selected = !seriesTab) { seriesTab = false }
                        SegmentPill("Series", selected = seriesTab) { seriesTab = true }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Box {
                        FilterChipPill(
                            text = (year?.toString() ?: "Any year") + "  ▾",
                            selected = year != null,
                            onClick = { yearMenuOpen = true }
                        )
                        DropdownMenu(
                            expanded = yearMenuOpen,
                            onDismissRequest = { yearMenuOpen = false },
                            modifier = Modifier
                                .background(Color(0xFF1C1C1E))
                                .heightIn(max = 320.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Any year", color = Color.White) },
                                onClick = { year = null; yearMenuOpen = false }
                            )
                            (currentYear downTo 1980).forEach { y ->
                                DropdownMenuItem(
                                    text = { Text(y.toString(), color = if (y == year) accent else Color.White) },
                                    onClick = { year = y; yearMenuOpen = false }
                                )
                            }
                        }
                    }
                    Text("★", color = Color(0xFFF5C518), fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                    RATING_OPTIONS.forEachIndexed { i, (label, _) ->
                        FilterChipPill(text = label, selected = ratingIndex == i, onClick = { ratingIndex = i })
                    }
                }
            }
        }

        // ---- Results ----
        if (loading && items.isEmpty()) {
            items(12) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .shimmer(RoundedCornerShape(8.dp))
                )
            }
        } else if (items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Nothing found for these filters.",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 40.dp)
                )
            }
        } else {
            itemsIndexed(items, key = { _, m -> "${m.kind}-${m.id}" }) { index, media ->
                // Endless scroll: fetch the next page as the user nears the end
                if (index >= items.size - 6 && !loading && !endReached) {
                    LaunchedEffect(items.size) {
                        loading = true
                        val tabAtStart = seriesTab
                        val next = runCatching { load(seriesTab, page + 1, year, minRating) }.getOrDefault(emptyList())
                        if (tabAtStart == seriesTab) {
                            if (next.isEmpty()) {
                                endReached = true
                            } else {
                                page += 1
                                items = (items + next).distinctBy { "${it.kind}-${it.id}" }
                            }
                        }
                        loading = false
                    }
                }
                PosterCard(
                    item = media,
                    isWatchLaterScreen = false,
                    onClick = { onMediaClick(media) },
                    onWatchLaterChanged = {}
                )
            }
        }
    }
}

@Composable
private fun SegmentPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color.White else Color.Transparent, tween(200), label = "segBg")
    val fg by animateColorAsState(if (selected) Color.Black else Color(0xFFE0E0E0), tween(200), label = "segFg")
    Text(
        text = text,
        color = fg,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .tvFocusHighlight(RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp)
    )
}

/** Small flat pill used for filters across Search and browse pages. */
@Composable
fun FilterChipPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color.White else Color(0xFF1A1A1A), tween(200), label = "chipBg")
    val fg by animateColorAsState(if (selected) Color.Black else Color.White, tween(200), label = "chipFg")
    Text(
        text = text,
        color = fg,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .tvFocusHighlight(RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else Color(0x1FFFFFFF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
