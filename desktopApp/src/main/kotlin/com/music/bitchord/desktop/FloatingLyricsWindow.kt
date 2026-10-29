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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.icons.BitChordIcons
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow

/** Global Manager to control and feed state into the In-App Dynamic Island Lyrics. */
internal object DesktopFloatingLyricsManager {
    private val persistence get() = DesktopPersistence()
    val isEnabled = MutableStateFlow(DesktopPersistence().boolean("floating_lyrics_enabled", false))
    val isLocked = MutableStateFlow(false)

    // Playback state fed from DesktopApp
    val currentSong = MutableStateFlow<Song?>(null)
    val isPlaying = MutableStateFlow(false)
    val lyricsLines = MutableStateFlow<List<LyricLine>?>(null)
    val currentPositionMs = MutableStateFlow(0L)
    val repeatMode = MutableStateFlow(DesktopRepeatMode.OFF)

    // Player action callbacks
    var onPlayPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
    var onToggleRepeat: (() -> Unit)? = null

    fun toggle() {
        val next = !isEnabled.value
        isEnabled.value = next
        persistence.saveBoolean("floating_lyrics_enabled", next)
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled.value = enabled
        persistence.saveBoolean("floating_lyrics_enabled", enabled)
    }
}

/** Stub function kept for compatibility: secondary OS Window is removed in favor of in-app integration. */
@Composable
internal fun FloatingLyricsWindow() {
    // No-op: The Dynamic Island is now unified directly inside BitChord's main window.
}

/**
 * Integrated Dynamic Island built directly into the Desktop Top Bar.
 * Contains:
 * - Left: Cover Art, Song Title, and Artist
 * - Right: 3-line rolling karaoke lyrics (sebelum, sekarang, sesudah)
 * - Bottom: Progress line
 */
@Composable
internal fun DesktopTopBarDynamicIsland(
    song: Song?,
    currentProgress: Float,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines by DesktopFloatingLyricsManager.lyricsLines.collectAsState()
    val positionMs by DesktopFloatingLyricsManager.currentPositionMs.collectAsState()

    val activeIndex = remember(lines, positionMs) {
        val list = lines.orEmpty()
        var idx = -1
        for (i in list.indices) {
            if (list[i].timeMs <= positionMs) {
                idx = i
            } else {
                break
            }
        }
        idx
    }

    val prevLineText = remember(lines, activeIndex) {
        if (activeIndex > 0) lines?.getOrNull(activeIndex - 1)?.text.orEmpty() else ""
    }
    val currentSong = song
    val currentLineText = remember(lines, activeIndex, currentSong) {
        val active = lines?.getOrNull(activeIndex)?.text
        when {
            !active.isNullOrBlank() -> active
            currentSong != null -> "${currentSong.title} — ${currentSong.artist}"
            else -> "BitChord • Pemutar Musik"
        }
    }
    val nextLineText = remember(lines, activeIndex) {
        if (activeIndex >= 0) lines?.getOrNull(activeIndex + 1)?.text.orEmpty() else ""
    }

    val pillShape = RoundedCornerShape(25.dp)

    Surface(
        modifier = modifier
            .widthIn(min = 340.dp, max = 660.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(pillShape)
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
                    color = Color.White.copy(alpha = 0.12f),
                    shape = pillShape,
                ),
        ) {
            if (song == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        BitChordIcons.MusicNote,
                        DesktopStrings["playback_channel_name", "Now playing"],
                        tint = DesktopSecondary.copy(alpha = 0.65f),
                        modifier = Modifier.size(21.dp),
                    )
                }
            } else {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Sisi Kiri: Artwork + Judul + Artis
                    Row(
                        Modifier
                            .widthIn(min = 130.dp, max = 220.dp)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DesktopArtwork(
                            url = song.thumbnailUrl,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF222226)),
                            contentDescription = "Cover",
                        )

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = song.artist,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 9.8.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Divider Pemisah Vertikal
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(26.dp)
                            .background(Color.White.copy(alpha = 0.12f)),
                    )

                    // Sisi Kanan: 3 Baris Lirik Bergulir
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 10.dp, end = 6.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // Baris 1: Sebelum
                        Text(
                            text = prevLineText.ifBlank { " " },
                            color = Color.White.copy(alpha = 0.38f),
                            fontSize = 9.2.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Baris 2: Sekarang (Menyala Aktif)
                        AnimatedContent(
                            targetState = Pair(currentLineText, activeIndex),
                            transitionSpec = {
                                (slideInVertically(animationSpec = tween(260)) { it / 2 } + fadeIn(tween(260)))
                                    .togetherWith(slideOutVertically(animationSpec = tween(220)) { -it / 2 } + fadeOut(tween(220)))
                            },
                        ) { target ->
                            Text(
                                text = target.first,
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start,
                                style = TextStyle(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.95f),
                                        offset = Offset(0f, 1.5f),
                                        blurRadius = 6f,
                                    ),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        // Baris 3: Sesudah
                        Text(
                            text = nextLineText.ifBlank { " " },
                            color = Color.White.copy(alpha = 0.38f),
                            fontSize = 9.2.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Garis progress berjalan di sepanjang dasar kapsul
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .drawBehind {
                            val base = currentProgress.coerceIn(0f, 1f)
                            drawRect(
                                color = Color.White.copy(alpha = 0.55f),
                                size = Size(size.width * base, size.height),
                            )
                        },
                )
            }
        }
    }
}

/**
 * In-App Floating Dynamic Island Capsule.
 * Floats inside the BitChord application window without opening any separate OS windows.
 */
@Composable
internal fun DesktopInAppFloatingDynamicIsland() {
    val isEnabled by DesktopFloatingLyricsManager.isEnabled.collectAsState()
    if (!isEnabled) return

    val song by DesktopFloatingLyricsManager.currentSong.collectAsState()
    val isPlaying by DesktopFloatingLyricsManager.isPlaying.collectAsState()
    val lines by DesktopFloatingLyricsManager.lyricsLines.collectAsState()
    val positionMs by DesktopFloatingLyricsManager.currentPositionMs.collectAsState()
    val repeatMode by DesktopFloatingLyricsManager.repeatMode.collectAsState()
    val isLocked by DesktopFloatingLyricsManager.isLocked.collectAsState()

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(16f) }

    val activeIndex = remember(lines, positionMs) {
        val list = lines.orEmpty()
        var idx = -1
        for (i in list.indices) {
            if (list[i].timeMs <= positionMs) {
                idx = i
            } else {
                break
            }
        }
        idx
    }

    val prevLineText = remember(lines, activeIndex) {
        if (activeIndex > 0) lines?.getOrNull(activeIndex - 1)?.text.orEmpty() else ""
    }
    val currentSong = song
    val currentLineText = remember(lines, activeIndex, currentSong) {
        val active = lines?.getOrNull(activeIndex)?.text
        when {
            !active.isNullOrBlank() -> active
            currentSong != null -> "${currentSong.title} — ${currentSong.artist}"
            else -> "BitChord • Menunggu pemutaran lagu..."
        }
    }
    val nextLineText = remember(lines, activeIndex) {
        if (activeIndex >= 0) lines?.getOrNull(activeIndex + 1)?.text.orEmpty() else ""
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val dragModifier = if (!isLocked) {
        Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                offsetX += dragAmount.x
                offsetY += dragAmount.y
            }
        }
    } else Modifier

    val pillShape = RoundedCornerShape(26.dp)

    Box(
        Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .width(660.dp)
                .height(98.dp)
                .hoverable(interactionSource)
                .then(dragModifier),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(pillShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xF7131317),
                                Color(0xFA09090C),
                            ),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isHovered) 0.35f else 0.18f),
                                Color.White.copy(alpha = 0.05f),
                            ),
                        ),
                        shape = pillShape,
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Sisi Kiri: Cover + Judul + Kontrol
                Column(
                    Modifier
                        .width(235.dp)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DesktopArtwork(
                            url = currentSong?.thumbnailUrl,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF222226)),
                            contentDescription = "Cover",
                        )

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = currentSong?.title ?: "Tidak ada lagu",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = currentSong?.artist ?: "BitChord Player",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IslandControlButton(
                            icon = Icons.Rounded.FastRewind,
                            tooltip = "Sebelumnya",
                            size = 28.dp,
                            iconSize = 16.dp,
                            onClick = { DesktopFloatingLyricsManager.onPrevious?.invoke() },
                        )

                        IslandControlButton(
                            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            tooltip = if (isPlaying) "Jeda / Stop" else "Putar",
                            size = 32.dp,
                            iconSize = 20.dp,
                            isPrimary = true,
                            onClick = { DesktopFloatingLyricsManager.onPlayPause?.invoke() },
                        )

                        IslandControlButton(
                            icon = Icons.Rounded.FastForward,
                            tooltip = "Lanjut (Berikutnya)",
                            size = 28.dp,
                            iconSize = 16.dp,
                            onClick = { DesktopFloatingLyricsManager.onNext?.invoke() },
                        )

                        val isRepeatActive = repeatMode != DesktopRepeatMode.OFF
                        IslandControlButton(
                            icon = BitChordIcons.Repeat,
                            tooltip = "Repeat: ${repeatMode.label()}",
                            size = 28.dp,
                            iconSize = 15.dp,
                            tint = if (isRepeatActive) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.65f),
                            badgeText = if (repeatMode == DesktopRepeatMode.ONE) "1" else null,
                            onClick = { DesktopFloatingLyricsManager.onToggleRepeat?.invoke() },
                        )
                    }
                }

                // Divider Pemisah Vertikal
                Box(
                    Modifier
                        .width(1.dp)
                        .height(64.dp)
                        .background(Color.White.copy(alpha = 0.12f)),
                )

                // Sisi Kanan: 3 Baris Lirik Bergulir
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 14.dp, end = 6.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Text(
                        text = prevLineText.ifBlank { " " },
                        color = Color.White.copy(alpha = 0.38f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    AnimatedContent(
                        targetState = Pair(currentLineText, activeIndex),
                        transitionSpec = {
                            (slideInVertically(animationSpec = tween(280)) { it / 2 } + fadeIn(tween(280)))
                                .togetherWith(slideOutVertically(animationSpec = tween(240)) { -it / 2 } + fadeOut(tween(240)))
                        },
                    ) { target ->
                        Text(
                            text = target.first,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.95f),
                                    offset = Offset(0f, 2f),
                                    blurRadius = 7f,
                                ),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Text(
                        text = nextLineText.ifBlank { " " },
                        color = Color.White.copy(alpha = 0.38f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Action Badges on Hover (Lock & Close)
            if (isHovered || isLocked) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IslandHoverBadge(
                        icon = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        tooltip = if (isLocked) "Terkunci" else "Bisa Digeser",
                        tint = if (isLocked) Color(0xFFFFD54F) else Color.White,
                        onClick = { DesktopFloatingLyricsManager.isLocked.value = !isLocked },
                    )

                    IslandHoverBadge(
                        icon = Icons.Rounded.Close,
                        tooltip = "Tutup Dynamic Island",
                        hoverBg = Color(0xFFC42B1C),
                        onClick = { DesktopFloatingLyricsManager.setEnabled(false) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IslandControlButton(
    icon: ImageVector,
    tooltip: String,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    isPrimary: Boolean = false,
    tint: Color = Color.White,
    badgeText: String? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    val bg = when {
        isPrimary -> if (isHovered) Color(0xFFFFFFFF) else Color(0xEEFFFFFF)
        isHovered -> Color.White.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val iconColor = if (isPrimary) Color.Black else tint

    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (badgeText != null) {
            Text(
                text = badgeText,
                color = tint,
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
private fun IslandHoverBadge(
    icon: ImageVector,
    tooltip: String,
    tint: Color = Color.White,
    hoverBg: Color = Color.White.copy(alpha = 0.25f),
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val bg = if (isHovered) hoverBg else Color.Black.copy(alpha = 0.5f)

    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(bg)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = tooltip, tint = tint, modifier = Modifier.size(11.dp))
    }
}
