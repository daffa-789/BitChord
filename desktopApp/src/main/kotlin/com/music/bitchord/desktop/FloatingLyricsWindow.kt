package com.music.bitchord.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The island's playback state, fed from the app and read by three surfaces: the capsule floating
 * over the page, the slim pill in the top bar, and the live preview in Settings.
 */
internal object DesktopFloatingLyricsManager {

    /** Delegated so the island, its menu and Settings all write through one state. */
    val isEnabled: MutableStateFlow<Boolean> get() = DesktopDynamicIslandSettings.enabled
    val isLocked: MutableStateFlow<Boolean> get() = DesktopDynamicIslandSettings.locked

    val currentSong = MutableStateFlow<Song?>(null)
    val isPlaying = MutableStateFlow(false)
    val lyricsLines = MutableStateFlow<List<LyricLine>?>(null)

    /**
     * The playhead, as Compose state rather than a flow.
     *
     * The engine publishes a position four times a second. Collected as a flow, every one of those
     * ticks recomposed the whole island — the artwork, the transport row, the animated lyric block —
     * to move a two-pixel hairline. As snapshot state it can be read inside `derivedStateOf` (so a
     * recomposition happens only when the *line* changes) and inside `drawBehind` (so the hairline
     * redraws without any composition at all).
     */
    val currentPositionMs = mutableStateOf(0L)
    val durationMs = mutableStateOf(0L)

    val repeatMode = MutableStateFlow(DesktopRepeatMode.OFF)

    // Player action callbacks, wired once by the app composable.
    var onPlayPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
    var onToggleRepeat: (() -> Unit)? = null
    var onOpenNowPlaying: (() -> Unit)? = null

    /** Opens Settings, where the island is configured. */
    var onOpenSettings: (() -> Unit)? = null

    /** Opens the lyrics column beside the page. */
    var onOpenLyrics: (() -> Unit)? = null

    fun toggle() = DesktopDynamicIslandSettings.toggle()

    fun setEnabled(enabled: Boolean) = DesktopDynamicIslandSettings.setEnabled(enabled)
}

/** Stub kept for compatibility: the island lives inside the main window, not in a second one. */
@Composable
internal fun FloatingLyricsWindow() {
    // No-op: the Dynamic Island is unified directly inside BitChord's main window.
}

/**
 * The lines around the one being sung, and the same three lines for any other index.
 *
 * [at] exists for the roll: while a line change is animating, the outgoing block has to be drawn
 * from the lines as they stood one index ago, or the two halves of the animation would show the
 * same words in the same place.
 */
private class IslandLines(
    val activeIndex: Int,
    private val raw: List<LyricLine>,
    private val fallback: String,
) {
    val previous: String
        get() = raw.getOrNull(activeIndex - 1)?.text.orEmpty()

    val current: String
        get() = raw.getOrNull(activeIndex)?.text?.takeIf { it.isNotBlank() } ?: fallback

    val next: String
        get() = raw.getOrNull(activeIndex + 1)?.text.orEmpty()

    fun at(index: Int): IslandLines = IslandLines(index, raw, fallback)
}

/**
 * The index of the line being sung at [positionMs], or -1 before the first one.
 *
 * A binary search rather than a walk: this runs on every playhead tick for a song that can carry
 * several hundred lines, and the lines are sorted by time by construction. The old linear scan
 * restarted from the top four times a second.
 */
internal fun activeLyricIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.size - 1
    var found = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lines[mid].timeMs <= positionMs) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}

private fun islandLines(lines: List<LyricLine>?, song: Song?, activeIndex: Int): IslandLines =
    IslandLines(
        activeIndex = activeIndex,
        raw = lines.orEmpty(),
        fallback = song?.let { "${it.title} — ${it.artist}" }.orEmpty(),
    )

/** The island's own measurements, before the user's size multiplier. */
internal val IslandLayout.baseWidth: Dp
    get() = when (this) {
        IslandLayout.FULL -> 660.dp
        IslandLayout.COMPACT -> 540.dp
        IslandLayout.MINI -> 360.dp
    }

internal val IslandLayout.baseHeight: Dp
    get() = when (this) {
        IslandLayout.FULL -> 98.dp
        IslandLayout.COMPACT -> 62.dp
        IslandLayout.MINI -> 44.dp
    }

/* ------------------------------------------------------------------------------------------------
 * The capsule itself
 * --------------------------------------------------------------------------------------------- */

/**
 * One capsule, drawn at whichever density the settings ask for.
 *
 * Everything the three layouts share — glass, edge light, the progress hairline —
 * is here rather than in each rung, so a change to the island's material shows up in all of them.
 * [interactive] is false for the Settings preview, where a drag or a click would be a surprise.
 *
 * [positionMs] and [progress] are lambdas, not values, and that is the whole performance story of
 * this file. The engine publishes a playhead four times a second; passed as a value it recomposed
 * the entire capsule — artwork, transport row, animated lyric block — on every tick. Read inside
 * `derivedStateOf` and `drawBehind` instead, a tick costs one hairline redraw, and a full
 * recomposition happens only when the *line being sung* changes.
 */
@Composable
internal fun DynamicIslandCard(
    song: Song?,
    lines: List<LyricLine>?,
    positionMs: () -> Long,
    progress: () -> Float,
    isPlaying: Boolean,
    repeatMode: DesktopRepeatMode,
    layout: IslandLayout,
    scale: Float,
    opacity: Float,
    hovered: Boolean,
    interactive: Boolean,
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onOpenNowPlaying: () -> Unit = {},
    modifier: Modifier = Modifier,
    menu: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
) {
    val showCover by DesktopDynamicIslandSettings.showCover.collectAsState()
    val showControls by DesktopDynamicIslandSettings.showControls.collectAsState()
    val showProgress by DesktopDynamicIslandSettings.showProgress.collectAsState()

    // Re-read only when the sung line changes, not on every playhead tick.
    val activeIndex by remember(lines, song) {
        derivedStateOf { activeLyricIndex(lines.orEmpty(), positionMs()) }
    }
    val now = remember(lines, song, activeIndex) { islandLines(lines, song, activeIndex) }
    val width by animateDpAsState(
        targetValue = layout.baseWidth * scale,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
        label = "islandWidth",
    )
    val height by animateDpAsState(
        targetValue = layout.baseHeight * scale,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
        label = "islandHeight",
    )
    val shape = RoundedCornerShape(height / 2)
    // The glass is a shade deeper while the pointer is on it, the way a lifted surface reads.
    val topGlass = if (hovered) Color(0xFF1B1B20) else Color(0xFF15151A)
    val bottomGlass = Color(0xFF08080B)

    Box(
        modifier
            .width(width)
            .height(height)
            .shadow(
                elevation = if (interactive) 24.dp else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.65f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        topGlass.copy(alpha = opacity),
                        bottomGlass.copy(alpha = opacity),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (hovered) 0.38f else 0.20f),
                        Color.White.copy(alpha = 0.04f),
                    ),
                ),
                shape = shape,
            )
            .then(
                if (interactive) Modifier.clickable(onClick = onOpenNowPlaying) else Modifier,
            ),
    ) {
        AnimatedContent(
            targetState = layout,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "islandLayout",
        ) { density ->
            when (density) {
                IslandLayout.FULL -> FullIsland(
                    song = song,
                    now = now,
                    positionMs = positionMs,
                    isPlaying = isPlaying,
                    repeatMode = repeatMode,
                    scale = scale,
                    hovered = hovered,
                    interactive = interactive,
                    showCover = showCover,
                    showControls = showControls,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onToggleRepeat = onToggleRepeat,
                )
                IslandLayout.COMPACT -> CompactIsland(
                    song = song,
                    now = now,
                    isPlaying = isPlaying,
                    scale = scale,
                    interactive = interactive,
                    showCover = showCover,
                    showControls = showControls,
                    onPlayPause = onPlayPause,
                )
                IslandLayout.MINI -> MiniIsland(
                    song = song,
                    now = now,
                    scale = scale,
                    showCover = showCover,
                )
            }
        }

        if (showProgress) {
            IslandProgress(
                progress = progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = height / 2)
                    .padding(bottom = 5.dp)
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }

        menu?.invoke(this)
    }
}

@Composable
private fun FullIsland(
    song: Song?,
    now: IslandLines,
    positionMs: () -> Long,
    isPlaying: Boolean,
    repeatMode: DesktopRepeatMode,
    scale: Float,
    hovered: Boolean,
    interactive: Boolean,
    showCover: Boolean,
    showControls: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleRepeat: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .width(if (showCover) 236.dp * scale else 210.dp * scale)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showCover) {
                    DesktopArtwork(
                        url = song?.thumbnailUrl,
                        modifier = Modifier
                            .size(38.dp * scale)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF222226)),
                        contentDescription = "Cover",
                        // A 38 dp thumbnail does not need a 544 px decode: the island asks for the
                        // size it draws, so the cache holds a few kilobytes instead of a megabyte.
                        px = ISLAND_COVER_PX,
                    )
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        text = song?.title ?: DesktopStrings["d_island_nothing_playing", "Nothing playing"],
                        color = Color.White,
                        fontSize = 12.5.sp * scale,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = song?.artist ?: "BitChord",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 10.5.sp * scale,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showControls) {
                    IslandControlButton(
                        icon = Icons.Rounded.FastRewind,
                        tooltip = "Previous",
                        size = 27.dp * scale,
                        iconSize = 15.dp * scale,
                        enabled = interactive,
                        onClick = onPrevious,
                    )
                    IslandControlButton(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        tooltip = if (isPlaying) "Pause" else "Play",
                        size = 33.dp * scale,
                        iconSize = 20.dp * scale,
                        isPrimary = true,
                        enabled = interactive,
                        onClick = onPlayPause,
                    )
                    IslandControlButton(
                        icon = Icons.Rounded.FastForward,
                        tooltip = "Next",
                        size = 27.dp * scale,
                        iconSize = 15.dp * scale,
                        enabled = interactive,
                        onClick = onNext,
                    )
                    val repeatOn = repeatMode != DesktopRepeatMode.OFF
                    IslandControlButton(
                        icon = BitChordIcons.Repeat,
                        tooltip = "Repeat: ${repeatMode.label()}",
                        size = 27.dp * scale,
                        iconSize = 15.dp * scale,
                        tint = if (repeatOn) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.62f),
                        badgeText = if (repeatMode == DesktopRepeatMode.ONE) "1" else null,
                        enabled = interactive,
                        onClick = onToggleRepeat,
                    )
                } else {
                    // The clock reads the playhead through a derived state, so the text is rewritten
                    // when the second changes rather than on all four ticks inside it.
                    val clock by remember {
                        derivedStateOf { formatIslandTime(positionMs()) }
                    }
                    Text(
                        text = clock,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 10.sp * scale,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }

        Box(
            Modifier
                .width(1.dp)
                .height(64.dp * scale)
                .background(Color.White.copy(alpha = 0.12f)),
        )

        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp, end = if (hovered) 74.dp else 6.dp),
        ) {
            // The whole three-line block rolls up by one line when the song moves on, the way the
            // player's own lyric sheet does: the line being sung turns white where it stands, and
            // nothing sweeps across it. Sliding a third of the block is one line's worth.
            AnimatedContent(
                targetState = now.activeIndex,
                transitionSpec = {
                    (slideInVertically(animationSpec = tween(ACTIVE_LINE_ROLL_MS)) { it / 3 } + fadeIn(tween(ACTIVE_LINE_ROLL_MS)))
                        .togetherWith(
                            slideOutVertically(animationSpec = tween(ACTIVE_LINE_ROLL_MS)) { -it / 3 } +
                                fadeOut(tween(ACTIVE_LINE_ROLL_MS)),
                        )
                },
                modifier = Modifier.fillMaxSize(),
                label = "islandLines",
            ) { index ->
                val at = now.at(index)
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IslandLine(text = at.previous, fontSize = 11.sp * scale, active = false)
                    IslandLine(text = at.current, fontSize = 14.sp * scale, active = true)
                    IslandLine(text = at.next, fontSize = 11.sp * scale, active = false)
                }
            }
        }
    }
}

@Composable
private fun CompactIsland(
    song: Song?,
    now: IslandLines,
    isPlaying: Boolean,
    scale: Float,
    interactive: Boolean,
    showCover: Boolean,
    showControls: Boolean,
    onPlayPause: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showCover) {
            DesktopArtwork(
                url = song?.thumbnailUrl,
                modifier = Modifier
                    .size(36.dp * scale)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222226)),
                contentDescription = "Cover",
                px = ISLAND_COVER_PX,
            )
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = song?.title ?: DesktopStrings["d_island_nothing_playing", "Nothing playing"],
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 10.5.sp * scale,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedContent(
                targetState = now.current to now.activeIndex,
                transitionSpec = {
                    (slideInVertically(animationSpec = tween(240)) { it } + fadeIn(tween(240)))
                        .togetherWith(slideOutVertically(animationSpec = tween(220)) { -it } + fadeOut(tween(220)))
                },
                label = "islandLineCompact",
            ) { target ->
                IslandLine(text = target.first, fontSize = 13.sp * scale, active = true)
            }
        }

        if (showControls) {
            IslandControlButton(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                tooltip = if (isPlaying) "Pause" else "Play",
                size = 34.dp * scale,
                iconSize = 20.dp * scale,
                isPrimary = true,
                enabled = interactive,
                onClick = onPlayPause,
            )
        }
    }
}

@Composable
private fun MiniIsland(
    song: Song?,
    now: IslandLines,
    scale: Float,
    showCover: Boolean,
) {
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        when {
            song == null -> Box(
                Modifier
                    .size(27.dp * scale)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    BitChordIcons.MusicNote,
                    null,
                    tint = Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.size(14.dp * scale),
                )
            }
            showCover -> DesktopArtwork(
                url = song.thumbnailUrl,
                modifier = Modifier
                    .size(27.dp * scale)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF222226)),
                contentDescription = "Cover",
                px = ISLAND_COVER_PX,
            )
        }
        AnimatedContent(
            targetState = now.current to now.activeIndex,
            transitionSpec = {
                (slideInVertically(animationSpec = tween(220)) { it } + fadeIn(tween(220)))
                    .togetherWith(slideOutVertically(animationSpec = tween(200)) { -it } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f),
            label = "islandLineMini",
        ) { target ->
            IslandLine(
                text = target.first.ifBlank {
                    if (song == null) DesktopStrings["d_island_nothing_playing", "Nothing playing"] else "BitChord"
                },
                fontSize = 12.sp * scale,
                active = true,
            )
        }
    }
}

/**
 * One line of the island's lyric.
 *
 * The line being sung is solid white and bold, the lines around it a dim white — the same reading
 * the player's lyric sheet gives, with no sweep across the words. The change between lines is the
 * only motion: the block rolls up, and the new line simply is white where it arrives.
 */
@Composable
private fun IslandLine(
    text: String,
    fontSize: TextUnit,
    active: Boolean,
) {
    Text(
        text = text.ifBlank { " " },
        color = if (active) Color.White else Color.White.copy(alpha = 0.36f),
        fontSize = fontSize,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Start,
        style = if (active) {
            TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.95f),
                    offset = Offset(0f, 2f),
                    blurRadius = 7f,
                ),
            )
        } else {
            TextStyle.Default
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The hairline under the capsule, with a soft head where the playhead is.
 *
 * [progress] is read inside `drawBehind`, so a playhead tick redraws two rectangles and nothing
 * else. Taking it as a value would have recomposed the capsule four times a second to move it.
 */
@Composable
private fun IslandProgress(progress: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier.drawBehind {
            val base = progress().coerceIn(0f, 1f)
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.12f),
                size = Size(size.width, size.height),
                cornerRadius = radius,
            )
            if (base > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.88f),
                    size = Size(size.width * base, size.height),
                    cornerRadius = radius,
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.28f),
                    radius = size.height * 2.4f,
                    center = Offset(size.width * base, size.height / 2),
                )
                drawCircle(
                    color = Color.White,
                    radius = size.height / 2,
                    center = Offset(size.width * base, size.height / 2),
                )
            }
        },
    )
}

/* ------------------------------------------------------------------------------------------------
 * The menu
 * --------------------------------------------------------------------------------------------- */

/**
 * Everything the island can be told to do, in one list.
 *
 * The same menu hangs off the island (right-click, or the "…" badge on hover) and off the toolbar
 * button, because a setting you have to hunt for is one nobody uses. Settings itself is the last
 * entry, for the things that need a slider rather than a tick.
 */
@Composable
internal fun DynamicIslandMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    val enabled by DesktopDynamicIslandSettings.enabled.collectAsState()
    val inTopBar by DesktopDynamicIslandSettings.inTopBar.collectAsState()
    val layout by DesktopDynamicIslandSettings.layout.collectAsState()
    val locked by DesktopDynamicIslandSettings.locked.collectAsState()
    val song by DesktopFloatingLyricsManager.currentSong.collectAsState()
    val isPlaying by DesktopFloatingLyricsManager.isPlaying.collectAsState()

    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        MenuHeader(
            song?.let { "${it.title} — ${it.artist}" }
                ?: DesktopStrings["d_island_nothing_playing", "Nothing playing"],
        )

        MenuAction(
            label = if (isPlaying) {
                DesktopStrings["pause", "Pause"]
            } else {
                DesktopStrings["play", "Play"]
            },
            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            onClick = {
                DesktopFloatingLyricsManager.onPlayPause?.invoke()
                onDismissRequest()
            },
        )
        MenuAction(DesktopStrings["widget_next", "Next"], Icons.Rounded.FastForward) {
            DesktopFloatingLyricsManager.onNext?.invoke()
            onDismissRequest()
        }
        MenuAction(DesktopStrings["widget_previous", "Previous"], Icons.Rounded.FastRewind) {
            DesktopFloatingLyricsManager.onPrevious?.invoke()
            onDismissRequest()
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        MenuCheck(DesktopStrings["d_island_floating", "Floating island"], enabled) {
            DesktopDynamicIslandSettings.setEnabled(it)
        }
        MenuCheck(DesktopStrings["d_island_top_bar", "Island in the top bar"], inTopBar) {
            DesktopDynamicIslandSettings.setInTopBar(it)
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        MenuCaption(DesktopStrings["d_island_layout", "Layout"])
        IslandLayout.entries.forEach { option ->
            MenuCheck(option.label, layout == option) { DesktopDynamicIslandSettings.setLayout(option) }
        }

        if (enabled) {
            MenuCheck(DesktopStrings["d_island_lock", "Lock position"], locked) {
                DesktopDynamicIslandSettings.setLocked(it)
            }
            MenuAction(DesktopStrings["d_island_reset_position", "Reset position"], Icons.Rounded.Refresh) {
                DesktopDynamicIslandSettings.resetOffset()
                onDismissRequest()
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        MenuAction(DesktopStrings["d_island_hide_app", "Hide BitChord"], Icons.Rounded.VisibilityOff) {
            onDismissRequest()
            // The island is its own window, so this puts the main window away without taking the
            // capsule with it — the app is out of sight, the island is not.
            DesktopWindowVisibility.hide()
        }
        MenuAction(DesktopStrings["d_island_settings", "Island settings…"], Icons.Rounded.Settings) {
            onDismissRequest()
            DesktopFloatingLyricsManager.onOpenSettings?.invoke()
        }
    }
}

/**
 * The toolbar's way in: the island's menu, hung off a note-shaped button that stays lit while the
 * island is on screen somewhere.
 */
@Composable
internal fun DesktopIslandToolbarButton() {
    var open by remember { mutableStateOf(false) }
    val anyVisible by DesktopDynamicIslandSettings.anyVisible.collectAsState()

    Box {
        DesktopToolbarButton(onClick = { open = true }, size = 32.dp) {
            Icon(
                BitChordIcons.MusicNote,
                "Dynamic Island",
                tint = if (anyVisible) Color.White else DesktopSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        DynamicIslandMenu(expanded = open, onDismissRequest = { open = false })
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text = text,
        color = DesktopSecondary,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).widthIn(max = 320.dp),
    )
}

@Composable
private fun MenuCaption(text: String) {
    Text(
        text = text.uppercase(),
        color = DesktopSecondary.copy(alpha = 0.75f),
        fontSize = 9.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun MenuAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun MenuCheck(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = {
            if (checked) {
                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
        },
        onClick = { onCheckedChange(!checked) },
    )
}

/* ------------------------------------------------------------------------------------------------
 * The floating capsule
 * --------------------------------------------------------------------------------------------- */

/**
 * The capsule itself now lives in a window of its own — see [DesktopIslandWindow] — so that hiding
 * the main window leaves the island on screen. What stays here is everything the capsule is made
 * of: the card, its three layouts, its menu and its badges.
 */

/* ------------------------------------------------------------------------------------------------
 * The top bar's island
 * --------------------------------------------------------------------------------------------- */

/**
 * The slim island that shares the toolbar with the transport controls.
 *
 * It is the same three-line readout as the capsule, in a pill low enough to sit between the
 * window's own buttons, and it disappears from the bar when it is switched off in its menu.
 */
@Composable
internal fun DesktopTopBarDynamicIsland(
    song: Song?,
    currentProgress: () -> Float,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inTopBar by DesktopDynamicIslandSettings.inTopBar.collectAsState()
    if (!inTopBar) return

    val lines by DesktopFloatingLyricsManager.lyricsLines.collectAsState()

    var menuOpen by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    // The menu counts as hover, so the buttons it was opened from do not vanish underneath it.
    val hovered = isHovered || menuOpen

    // Same split as the capsule: the line changes rarely and recomposes, the playhead does not.
    val activeIndex by remember(lines, song) {
        derivedStateOf { activeLyricIndex(lines.orEmpty(), DesktopFloatingLyricsManager.currentPositionMs.value) }
    }
    val now = remember(lines, song, activeIndex) { islandLines(lines, song, activeIndex) }
    val pillShape = RoundedCornerShape(25.dp)

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                // A pill with nothing in it is a short chip, not a bar: stretch only when it has
                // lyrics three lines deep to lay out.
                .then(
                    if (song == null) {
                        Modifier.widthIn(min = 220.dp, max = 300.dp)
                    } else {
                        Modifier.widthIn(max = 660.dp).fillMaxWidth()
                    },
                )
                .height(48.dp)
                .clip(pillShape)
                .hoverable(interactionSource)
                .islandContextMenu { menuOpen = true }
                .clickable(onClick = onOpenNowPlaying),
            shape = pillShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xF715151A),
                                Color(0xFA0B0B0E),
                                Color(0xF715151A),
                            ),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = if (hovered) 0.24f else 0.12f),
                        shape = pillShape,
                    ),
            ) {
                if (song == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                BitChordIcons.MusicNote,
                                DesktopStrings["playback_channel_name", "Now playing"],
                                tint = DesktopSecondary.copy(alpha = 0.65f),
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = DesktopStrings["d_island_ready", "Dynamic Island · ready"],
                                color = DesktopSecondary.copy(alpha = 0.75f),
                                fontSize = 10.5.sp,
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(start = 8.dp, end = if (hovered) 6.dp else 10.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DesktopArtwork(
                            url = song.thumbnailUrl,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF222226)),
                            contentDescription = "Cover",
                            px = ISLAND_COVER_PX,
                        )
                        Spacer(Modifier.width(9.dp))

                        Column(Modifier.widthIn(min = 100.dp, max = 190.dp)) {
                            Text(
                                text = song.title,
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song.artist,
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 9.6.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Box(
                            Modifier
                                .padding(horizontal = 9.dp)
                                .width(1.dp)
                                .height(26.dp)
                                .background(Color.White.copy(alpha = 0.12f)),
                        )

                        Column(
                            Modifier
                                .weight(1f)
                                .padding(end = 4.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            AnimatedContent(
                                targetState = now.activeIndex,
                                transitionSpec = {
                                    (slideInVertically(animationSpec = tween(280)) { it / 3 } + fadeIn(tween(280)))
                                        .togetherWith(
                                            slideOutVertically(animationSpec = tween(280)) { -it / 3 } +
                                                fadeOut(tween(280)),
                                        )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = "topBarLines",
                            ) { index ->
                                val at = now.at(index)
                                Column(verticalArrangement = Arrangement.SpaceEvenly) {
                                    IslandLine(text = at.previous, fontSize = 9.sp, active = false)
                                    IslandLine(text = at.current, fontSize = 11.5.sp, active = true)
                                    IslandLine(text = at.next, fontSize = 9.sp, active = false)
                                }
                            }
                        }

                        if (hovered) {
                            IslandBadge(
                                icon = Icons.Rounded.MoreHoriz,
                                tooltip = "Island menu",
                                onClick = { menuOpen = true },
                            )
                        }
                    }

                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.dp)
                            .drawBehind {
                                val base = currentProgress().coerceIn(0f, 1f)
                                drawRect(
                                    color = Color.White.copy(alpha = 0.55f),
                                    size = Size(size.width * base, size.height),
                                )
                            },
                    )
                }
            }
        }

        DynamicIslandMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false })
    }
}

/* ------------------------------------------------------------------------------------------------
 * Small parts
 * --------------------------------------------------------------------------------------------- */

/**
 * A right-click that opens [onOpen] when the button comes back up.
 *
 * Opening on the press put a menu under the pointer before the release arrived, so the release
 * landed on whichever entry happened to be there — and the island's menu begins with Play/Pause.
 * Opening on the release means the gesture that asked for the menu cannot also choose from it.
 */
internal fun Modifier.islandContextMenu(onOpen: () -> Unit): Modifier = pointerInput(Unit) {
    var armed = false
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            when {
                event.type == PointerEventType.Press && event.buttons.isSecondaryPressed -> {
                    armed = true
                    event.changes.forEach { it.consume() }
                }
                armed && event.type == PointerEventType.Release -> {
                    armed = false
                    onOpen()
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
}

@Composable
private fun IslandControlButton(
    icon: ImageVector,
    tooltip: String,
    size: Dp,
    iconSize: Dp,
    isPrimary: Boolean = false,
    tint: Color = Color.White,
    badgeText: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    val background = when {
        isPrimary -> if (isHovered) Color.White else Color(0xEEFFFFFF)
        isHovered -> Color.White.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val iconColor = if (isPrimary) Color.Black else tint

    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (badgeText != null) {
            Text(
                text = badgeText,
                color = iconColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = tooltip,
                tint = iconColor,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
internal fun IslandBadge(
    icon: ImageVector,
    tooltip: String,
    tint: Color = Color.White,
    hoverBg: Color = Color.White.copy(alpha = 0.25f),
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val background = if (isHovered) hoverBg else Color.Black.copy(alpha = 0.55f)

    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(background)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = tooltip, tint = tint, modifier = Modifier.size(11.dp))
    }
}

/**
 * How far through the current song the playhead is.
 *
 * The player reports its duration alongside the position, so the hairline is the song's real
 * progress; the lyric's own last timestamp is only a fallback for a stream that never told us.
 */
internal fun islandProgress(positionMs: Long, durationMs: Long): Float {
    if (durationMs > 0L) return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val line = DesktopFloatingLyricsManager.lyricsLines.value.orEmpty()
    val last = line.lastOrNull()?.timeMs ?: return 0f
    if (last <= 0L) return 0f
    return (positionMs.toFloat() / last).coerceIn(0f, 1f)
}

/** How long the lyric block takes to roll up by one line, matching the player's own sheet. */
private const val ACTIVE_LINE_ROLL_MS = 320

/**
 * The artwork size the island asks the image host for.
 *
 * The capsule's cover is at most 38 dp, which is under 80 physical pixels even at 2× and under 120
 * at 3×. Requesting the default size decoded a full thumbnail per song into the cache for a square
 * the size of a fingernail.
 */
private const val ISLAND_COVER_PX = 96

private fun formatIslandTime(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
