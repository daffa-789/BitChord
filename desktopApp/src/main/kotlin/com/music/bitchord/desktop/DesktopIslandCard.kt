package com.music.bitchord.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.icons.BitChordIcons

/**
 * The capsule's measurements, in dp, before the user's size multiplier.
 *
 * Two sizes, not three: the pill the island rests at, and the face it grows into. The island used
 * to carry a Full / Compact / Mini ladder and a switch saying which of them to shrink to — four
 * ways of asking "how much of the song is showing". A Dynamic Island has one shape that changes
 * with what is happening to it, and so does this one.
 */
internal const val ISLAND_COLLAPSED_WIDTH = 380f
internal const val ISLAND_COLLAPSED_HEIGHT = 44f
internal const val ISLAND_EXPANDED_WIDTH = 680f
internal const val ISLAND_EXPANDED_HEIGHT = 116f

/**
 * The corners of the capsule.
 *
 * A rectangle with its corners taken off, not a pill: the island is a panel of glass sitting on
 * the screen, and rounding it off all the way round turns it into a lozenge that reads as a
 * sticker rather than as a part of the display.
 */
internal val ISLAND_CORNER = 16.dp

/**
 * The lines around the one being sung, and the same three lines for any other index.
 *
 * [at] exists for the roll: while a line change is animating, the outgoing block has to be drawn
 * from the lines as they stood one index ago, or the two halves of the animation would show the
 * same words in the same place.
 */
internal class IslandLines(
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

/**
 * How far through the current song the playhead is.
 *
 * The player reports its duration alongside the position, so the hairline is the song's real
 * progress; the lyric's own last timestamp is only a fallback for a stream that never told us.
 */
internal fun islandProgress(positionMs: Long, durationMs: Long): Float {
    if (durationMs > 0L) return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val line = DesktopIslandState.lyricsLines.value.orEmpty()
    val last = line.lastOrNull()?.timeMs ?: return 0f
    if (last <= 0L) return 0f
    return (positionMs.toFloat() / last).coerceIn(0f, 1f)
}

/* ------------------------------------------------------------------------------------------------
 * The capsule itself
 * --------------------------------------------------------------------------------------------- */

/**
 * One capsule, drawn at whichever size the island's window gives it.
 *
 * The shell fills whatever it is handed, so the glass is always exactly as wide as the window that
 * holds it — there is no state in which the capsule is bigger than its own frame, which is what
 * used to happen while the window resized ahead of the drawing.
 *
 * Inside the shell sit two faces, drawn on top of each other: the pill the island rests at, and the
 * full face it grows into. How far along that growth is, 0 for the pill and 1 for the full face, is
 * measured from the capsule's real width rather than taken from the animation that resized the
 * window — so the drawing and the frame that holds it can never disagree.
 *
 * [positionMs] and [progress] are lambdas, not values, and that is the whole performance story of
 * this file. The engine publishes a playhead four times a second; passed as a value it recomposed
 * the entire capsule — artwork, transport row, animated lyric block — on every tick. Read inside
 * `derivedStateOf` and `drawBehind` instead, a tick costs one hairline redraw, and a full
 * recomposition happens only when the *line being sung* changes.
 */
@Composable
internal fun DesktopIslandCard(
    song: Song?,
    lines: List<LyricLine>?,
    positionMs: () -> Long,
    durationMs: () -> Long,
    isPlaying: Boolean,
    repeatMode: DesktopRepeatMode,
    scale: Float,
    hovered: Boolean,
    interactive: Boolean,
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onOpenNowPlaying: () -> Unit = {},
    /** Null leaves the expanded face without a scrub row, as a click-through capsule wants. */
    onSeek: ((Float) -> Unit)? = null,
    onSeekStarted: () -> Unit = {},
    onSeekFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    menu: (@Composable BoxScope.() -> Unit)? = null,
) {
    // Re-read only when the sung line changes, not on every playhead tick.
    val activeIndex by remember(lines, song) {
        derivedStateOf { activeLyricIndex(lines.orEmpty(), positionMs()) }
    }
    val now = remember(lines, song, activeIndex) { islandLines(lines, song, activeIndex) }

    val palette = rememberDesktopArtworkPaletteOrNull(song?.thumbnailUrl.artworkAt(ISLAND_COVER_PX))
    // The glass is a shade deeper while the pointer is on it, the way a lifted surface reads, and
    // it carries a wash of the cover's own colour so the capsule belongs to the song rather than
    // sitting on top of it. 96 px is the thumbnail the cover already decodes, so the tint costs no
    // extra download.
    val baseGlass = if (hovered) Color(0xFF1B1B20) else Color(0xFF15151A)
    val topGlass = palette
        ?.let { it.primary.copy(alpha = ISLAND_TINT_ALPHA).compositeOver(baseGlass) }
        ?: baseGlass
    val bottomGlass = Color(0xFF08080B)

    // The face is worked out from the width the capsule was actually given, so the drawing and the
    // window that holds it are always the same size, to the pixel.
    val density = LocalDensity.current
    val measured = remember { mutableFloatStateOf(0f) }
    val collapsed = ISLAND_COLLAPSED_WIDTH * scale
    val span = (ISLAND_EXPANDED_WIDTH - ISLAND_COLLAPSED_WIDTH) * scale

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                val width = with(density) { size.width.toDp() }.value
                measured.floatValue = if (span <= 0f) {
                    0f
                } else {
                    ((width - collapsed) / span).coerceIn(0f, 1f)
                }
                DesktopTrackLog.log("island card: size=${size.width}x${size.height} measured=${measured.floatValue}")
            }
            .shadow(
                elevation = if (interactive) 24.dp else 0.dp,
                shape = RoundedCornerShape(ISLAND_CORNER),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.65f),
            )
            .clip(RoundedCornerShape(ISLAND_CORNER))
            .background(
                Brush.verticalGradient(
                    listOf(topGlass, bottomGlass),
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
                shape = RoundedCornerShape(ISLAND_CORNER),
            )
            .then(
                if (interactive) Modifier.clickable(onClick = onOpenNowPlaying) else Modifier,
            ),
    ) {
        // The full face is a slab of its own, wider and taller than the shell that holds it, so the
        // growing window uncovers it rather than reflowing it: the cover and the title arrive
        // first, the transport next, and the lyric column slides in from the right as the capsule
        // reaches its full width.
        Box(
            Modifier
                .width(ISLAND_EXPANDED_WIDTH.dp)
                .height(ISLAND_EXPANDED_HEIGHT.dp)
                .graphicsLayer { alpha = measured.floatValue },
        ) {
            ExpandedIslandFace(
                song = song,
                now = now,
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                repeatMode = repeatMode,
                face = measured,
                interactive = interactive,
                onSeek = onSeek,
                onSeekStarted = onSeekStarted,
                onSeekFinished = onSeekFinished,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleRepeat = onToggleRepeat,
            )
        }

        // The pill the island rests at.
        Box(
            Modifier
                .width(ISLAND_COLLAPSED_WIDTH.dp)
                .height(ISLAND_COLLAPSED_HEIGHT.dp)
                .graphicsLayer { alpha = 1f - measured.floatValue },
        ) {
            CollapsedIslandFace(song = song, now = now)
        }

        // The hairline along the capsule's bottom edge. It hands over to the scrub row as the
        // capsule grows, so the two progress readings never show at once.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = (1f - measured.floatValue / 0.55f).coerceIn(0f, 1f) }
                .padding(horizontal = 22.dp)
                .padding(bottom = 5.dp)
                .fillMaxWidth()
                .height(2.dp),
        ) {
            IslandProgress(progress = { islandProgress(positionMs(), durationMs()) })
        }

        if (menu != null) {
            Box(Modifier.align(Alignment.TopEnd)) {
                menu(this)
            }
        }
    }
}

/**
 * The full face: the cover and the title on the left, the transport under them, and the lyric
 * being sung on the right.
 */
@Composable
private fun ExpandedIslandFace(
    song: Song?,
    now: IslandLines,
    positionMs: () -> Long,
    durationMs: () -> Long,
    isPlaying: Boolean,
    repeatMode: DesktopRepeatMode,
    face: FloatState,
    interactive: Boolean,
    onSeek: ((Float) -> Unit)?,
    onSeekStarted: () -> Unit,
    onSeekFinished: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleRepeat: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = 9.dp,
                    bottom = if (onSeek != null) 1.dp else 9.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.width(236.dp).padding(end = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DesktopArtwork(
                        url = song?.thumbnailUrl,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF222226)),
                        contentDescription = "Cover",
                        // A 38 dp thumbnail does not need a 544 px decode: the island asks for the
                        // size it draws, so the cache holds a few kilobytes instead of a megabyte.
                        px = ISLAND_COVER_PX,
                    )

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = song?.title ?: DesktopStrings["d_island_nothing_playing", "Nothing playing"],
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = song?.artist ?: "BitChord",
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 11.sp,
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
                    IslandControlButton(
                        icon = Icons.Rounded.FastRewind,
                        tooltip = "Previous",
                        size = 27.dp,
                        iconSize = 15.dp,
                        enabled = interactive,
                        onClick = onPrevious,
                    )
                    IslandControlButton(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        tooltip = if (isPlaying) "Pause" else "Play",
                        size = 33.dp,
                        iconSize = 20.dp,
                        isPrimary = true,
                        enabled = interactive,
                        onClick = onPlayPause,
                    )
                    IslandControlButton(
                        icon = Icons.Rounded.FastForward,
                        tooltip = "Next",
                        size = 27.dp,
                        iconSize = 15.dp,
                        enabled = interactive,
                        onClick = onNext,
                    )
                    val repeatOn = repeatMode != DesktopRepeatMode.OFF
                    IslandControlButton(
                        icon = BitChordIcons.Repeat,
                        tooltip = "Repeat: ${repeatMode.label()}",
                        size = 27.dp,
                        iconSize = 15.dp,
                        tint = if (repeatOn) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.62f),
                        badgeText = if (repeatMode == DesktopRepeatMode.ONE) "1" else null,
                        enabled = interactive,
                        onClick = onToggleRepeat,
                    )
                }
            }

            Box(
                Modifier
                    .width(1.dp)
                    .height(56.dp)
                    .background(Color.White.copy(alpha = 0.12f)),
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 6.dp),
            ) {
                // The whole three-line block rolls up by one line when the song moves on, the way the
                // player's own lyric sheet does: the line being sung turns white where it stands,
                // and nothing sweeps across it. Sliding a third of the block is one line's worth.
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
                        IslandLine(text = at.previous, fontSize = 11.sp, active = false)
                        IslandLine(text = at.current, fontSize = 15.sp, active = true)
                        IslandLine(text = at.next, fontSize = 11.sp, active = false)
                    }
                }
            }
        }

        if (onSeek != null) {
            Box(Modifier.graphicsLayer { alpha = ((face.floatValue - 0.5f) / 0.5f).coerceIn(0f, 1f) }) {
                IslandSeekRow(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onSeek = onSeek,
                    onSeekStarted = onSeekStarted,
                    onSeekFinished = onSeekFinished,
                )
            }
        }
    }
}

/**
 * The paused island's whole content: a button, not a player.
 *
 * A stopped player is not something you look at, and a stopped player is not something you play
 * from here — so the island keeps nothing but a door, and the door is the whole of it. Tapping
 * hands the screen back to the music, which is what the island was put there to do.
 */
@Composable
internal fun DesktopIslandDoorbell(
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFF222226))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            DesktopStrings["d_island_open_player", "Open player"],
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The pill the island rests at: the cover and the line being sung, nothing else. */
@Composable
private fun CollapsedIslandFace(
    song: Song?,
    now: IslandLines,
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
                    .size(27.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    BitChordIcons.MusicNote,
                    null,
                    tint = Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.size(14.dp),
                )
            }
            else -> DesktopArtwork(
                url = song.thumbnailUrl,
                modifier = Modifier
                    .size(27.dp)
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
            label = "islandLineCollapsed",
        ) { target ->
            IslandLine(
                text = target.first.ifBlank {
                    if (song == null) DesktopStrings["d_island_nothing_playing", "Nothing playing"] else "BitChord"
                },
                fontSize = 12.sp,
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

/**
 * The scrub row under the expanded face: what has been played, a bar, what is left.
 *
 * The bar only reports on release. While the pointer is down it previews the position locally, so
 * the lyrics keep reading from where the song actually is until the listener has chosen; asking
 * the engine to jump on every pixel of a drag would be dozens of decoder seeks per gesture.
 */
@Composable
private fun IslandSeekRow(
    positionMs: () -> Long,
    durationMs: () -> Long,
    onSeek: (Float) -> Unit,
    onSeekStarted: () -> Unit,
    onSeekFinished: () -> Unit,
) {
    var preview by remember { mutableStateOf<Float?>(null) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        IslandSeekTime(
            positionMs = positionMs,
            durationMs = durationMs,
            preview = preview,
            leading = true,
        )
        IslandSeekBar(
            positionMs = positionMs,
            durationMs = durationMs,
            preview = preview,
            onPreview = { preview = it },
            onSeekStarted = onSeekStarted,
            onSeekFinished = {
                val target = preview
                if (target != null) onSeek(target)
                preview = null
                onSeekFinished()
            },
            modifier = Modifier.weight(1f),
        )
        IslandSeekTime(
            positionMs = positionMs,
            durationMs = durationMs,
            preview = preview,
            leading = false,
        )
    }
}

@Composable
private fun IslandSeekBar(
    positionMs: () -> Long,
    durationMs: () -> Long,
    preview: Float?,
    onPreview: (Float) -> Unit,
    onSeekStarted: () -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read through a derived state so the four playhead ticks a second recompose the bar, not the
    // capsule around it.
    val live by remember {
        derivedStateOf {
            val duration = durationMs()
            if (duration > 0L) (positionMs().toFloat() / duration).coerceIn(0f, 1f) else 0f
        }
    }
    DesktopThinSlider(
        value = preview ?: live,
        onValueChange = onPreview,
        modifier = modifier,
        onValueChangeStarted = onSeekStarted,
        onValueChangeFinished = onSeekFinished,
        idleHeight = 3.dp,
        activeHeight = 5.dp,
        activeColor = Color.White.copy(alpha = 0.92f),
        inactiveColor = Color.White.copy(alpha = 0.20f),
    )
}

/** Elapsed time on the left of the seek row, time left on the right. */
@Composable
private fun IslandSeekTime(
    positionMs: () -> Long,
    durationMs: () -> Long,
    preview: Float?,
    leading: Boolean,
) {
    val text by remember(preview) {
        derivedStateOf {
            val duration = durationMs()
            val position = when {
                preview != null && duration > 0L -> (preview * duration).toLong()
                else -> positionMs()
            }
            if (leading) {
                formatIslandTime(position)
            } else {
                "-" + formatIslandTime((duration - position).coerceAtLeast(0L))
            }
        }
    }
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.52f),
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
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

/** How much of the cover's colour is washed into the capsule's glass. */
private const val ISLAND_TINT_ALPHA = 0.22f

private fun formatIslandTime(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
