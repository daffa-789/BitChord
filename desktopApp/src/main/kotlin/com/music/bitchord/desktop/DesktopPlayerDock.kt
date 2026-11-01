package com.music.bitchord.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.theme.BcColors
import com.music.bitchord.ui.theme.BcMotion
import com.music.bitchord.ui.theme.BcShapes
import kotlinx.coroutines.delay

/**
 * The player that lives in the page rather than in a window: one surface, collapsed
 * to a calm strip and expanded under the pointer into the full transport.
 *
 * It is the player while the main window is in front; the floating island takes
 * over when the window is hidden or another app has the focus. Every control it
 * shows is also reachable elsewhere, so the island can stay off.
 */
@Composable
internal fun DesktopPlayerDock(
    song: Song?,
    isPlaying: Boolean,
    isLoading: Boolean,
    hasPrevious: Boolean,
    position: PlaybackPosition,
    durationMs: () -> Long,
    volume: Float,
    shuffle: Boolean,
    repeatMode: DesktopRepeatMode,
    compact: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatChange: (DesktopRepeatMode) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenAudioOutput: () -> Unit,
) {
    if (song == null) return
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) {
        if (hovered) {
            expanded = true
        } else {
            delay(250)
            expanded = false
        }
    }

    val shape = RoundedCornerShape(BcShapes.dock)
    val palette = rememberDesktopArtworkPalette(song.thumbnailUrl)
    val artworkSize by animateDpAsState(
        targetValue = if (expanded) 64.dp else 44.dp,
        animationSpec = tween(BcMotion.standardMs),
        label = "dockArtwork",
    )
    Column(
        Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .padding(horizontal = if (compact) 14.dp else 24.dp)
            .hoverable(interaction)
            .animateContentSize(animationSpec = BcMotion.dockSpring())
            .shadow(22.dp, shape)
            .clip(shape)
            .desktopBarGlass(shape),
    ) {
        Box(Modifier.fillMaxWidth()) {
            // The artwork's own colour, held well below the glass so the skin changes with the
            // sleeve without the dock ever reading as a coloured card.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                palette.primary.copy(alpha = 0.16f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(if (expanded) 68.dp else 64.dp)
                        .padding(start = 12.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clickable(onClick = onOpenNowPlaying),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DesktopArtwork(
                            song.thumbnailUrl,
                            Modifier.size(artworkSize).clip(RoundedCornerShape(BcShapes.artwork)),
                            px = ROW_ART_PX,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            if (expanded) {
                                Text(
                                    DesktopStrings["d_source_youtube_music", "YouTube Music"],
                                    color = BcColors.TextFaint,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    song.title,
                                    color = BcColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (isPlaying) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(percent = 50)).background(BcColors.Live))
                                }
                            }
                            Text(
                                song.artist,
                                color = BcColors.TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (expanded) {
                        DockIconButton({ onShuffleChange(!shuffle) }) {
                            Icon(
                                BitChordIcons.Shuffle,
                                DesktopStrings["shuffle", "Shuffle"],
                                tint = if (shuffle) BcColors.Action else BcColors.TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DockIconButton({ onRepeatChange(repeatMode.next()) }) {
                            if (repeatMode == DesktopRepeatMode.ONE) {
                                Text(
                                    "1",
                                    color = BcColors.Action,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            } else {
                                Icon(
                                    BitChordIcons.Repeat,
                                    "Repeat ${repeatMode.label()}",
                                    tint = if (repeatMode != DesktopRepeatMode.OFF) BcColors.Action else BcColors.TextMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        DockIconButton(onOpenLyrics) {
                            Icon(
                                BitChordIcons.LyricsQuote,
                                DesktopStrings["lyrics", "Lyrics"],
                                tint = BcColors.TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DockIconButton(onOpenQueue) {
                            Icon(
                                BitChordIcons.Queue,
                                DesktopStrings["queue", "Queue"],
                                tint = BcColors.TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DockIconButton(onOpenAudioOutput) {
                            Icon(
                                Icons.Rounded.Headphones,
                                DesktopStrings["audio_output", "Audio output"],
                                tint = BcColors.TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    DockIconButton(onPrevious, enabled = hasPrevious) {
                        Icon(
                            Icons.Rounded.FastRewind,
                            DesktopStrings["widget_previous", "Previous"],
                            tint = if (hasPrevious) BcColors.TextPrimary else BcColors.TextFaint,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    DockIconButton(onPlayPause, size = 36.dp) {
                        if (isLoading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = BcColors.Action,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (isPlaying) "Pause" else "Play",
                                tint = BcColors.Action,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    DockIconButton(onNext) {
                        Icon(
                            Icons.Rounded.FastForward,
                            DesktopStrings["widget_next", "Next"],
                            tint = BcColors.TextPrimary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    DockIconButton(onOpenNowPlaying) {
                        Icon(
                            Icons.Rounded.KeyboardArrowUp,
                            "Open player",
                            tint = BcColors.TextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(tween(BcMotion.standardMs)) + fadeIn(tween(BcMotion.standardMs)),
                    exit = shrinkVertically(tween(BcMotion.standardMs)) + fadeOut(tween(BcMotion.hoverMs)),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DockSeek(position = position, durationMs = durationMs, onSeekFraction = onSeekFraction)
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.Rounded.VolumeUp,
                            DesktopStrings["d_volume", "Volume"],
                            tint = BcColors.TextMuted,
                            modifier = Modifier.size(16.dp),
                        )
                        DesktopThinSlider(
                            value = volume,
                            onValueChange = onVolumeChange,
                            idleHeight = 3.dp,
                            activeHeight = 5.dp,
                            modifier = Modifier.width(86.dp),
                        )
                    }
                }
            }
        }
        // The collapsed read of the playhead: a hairline along the dock's own bottom edge.
        if (!expanded) {
            DockProgressLine(position = position, durationMs = durationMs)
        }
    }
}

@Composable
private fun RowScope.DockSeek(
    position: PlaybackPosition,
    durationMs: () -> Long,
    onSeekFraction: (Float) -> Unit,
) {
    val duration = durationMs()
    var preview by remember { mutableStateOf<Float?>(null) }
    val played = preview ?: if (duration > 0) {
        (position.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    Text(
        formatTime(if (preview != null) (preview!! * duration).toLong() else position.positionMs),
        color = BcColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
    )
    DesktopThinSlider(
        value = played,
        onValueChange = { preview = it },
        onValueChangeFinished = {
            preview?.let(onSeekFraction)
            preview = null
        },
        idleHeight = 3.dp,
        activeHeight = 5.dp,
        modifier = Modifier.weight(1f),
    )
    Text(
        "-${formatTime((duration - if (preview != null) (preview!! * duration).toLong() else position.positionMs).coerceAtLeast(0L))}",
        color = BcColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun DockProgressLine(position: PlaybackPosition, durationMs: () -> Long) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        drawProgress(position, durationMs)
    }
}

private fun DrawScope.drawProgress(position: PlaybackPosition, durationMs: () -> Long) {
    val duration = durationMs()
    val fraction = if (duration > 0) {
        (position.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    drawRect(BcColors.HairlineStrong)
    if (fraction > 0f) {
        drawRect(
            color = BcColors.Action.copy(alpha = 0.9f),
            size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height),
            topLeft = Offset.Zero,
        )
    }
}

@Composable
private fun DockIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 30.dp,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 50))
            .desktopHoverWash()
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
