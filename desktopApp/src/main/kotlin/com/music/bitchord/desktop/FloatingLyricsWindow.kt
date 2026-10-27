package com.music.bitchord.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import java.awt.Toolkit
import kotlinx.coroutines.flow.MutableStateFlow

/** Available animation styles for Floating Desktop Lyrics. */
enum class FloatingLyricsStyle(val title: String) {
    PING_PONG("Ping-Pong (Kiri-Kanan)"),
    CAPSULE_POP("Capsule Pop (Atas)"),
    KARAOKE_SWEEP("Karaoke Sweep"),
    AMBIENT_GHOST("Ambient Ghost"),
}

/** Global Manager to control and feed state into the Floating Lyrics Overlay. */
object DesktopFloatingLyricsManager {
    val isEnabled = MutableStateFlow(false)
    val selectedStyle = MutableStateFlow(FloatingLyricsStyle.PING_PONG)
    val isLocked = MutableStateFlow(false)
    val fontSizeSp = MutableStateFlow(22)

    // Playback state fed from DesktopApp
    val currentSong = MutableStateFlow<Song?>(null)
    val isPlaying = MutableStateFlow(false)
    val lyricsLines = MutableStateFlow<List<LyricLine>?>(null)
    val currentPositionMs = MutableStateFlow(0L)

    fun toggle() {
        isEnabled.value = !isEnabled.value
    }

    fun cycleStyle() {
        val all = FloatingLyricsStyle.values()
        val nextIndex = (selectedStyle.value.ordinal + 1) % all.size
        selectedStyle.value = all[nextIndex]
    }
}

/** The secondary Floating Desktop Lyrics Window. */
@Composable
internal fun FloatingLyricsWindow() {
    val isEnabled by DesktopFloatingLyricsManager.isEnabled.collectAsState()
    if (!isEnabled) return

    val screenSize = Toolkit.getDefaultToolkit().screenSize
    val initialWidth = 720
    val initialHeight = 110
    val startX = (screenSize.width - initialWidth) / 2
    val startY = screenSize.height - initialHeight - 90 // Float above taskbar by default

    val windowState = rememberWindowState(
        position = WindowPosition(startX.dp, startY.dp),
        size = DpSize(initialWidth.dp, initialHeight.dp),
    )

    Window(
        onCloseRequest = { DesktopFloatingLyricsManager.isEnabled.value = false },
        title = "BitChord - Floating Lyrics",
        state = windowState,
        alwaysOnTop = true,
        undecorated = true,
        transparent = true,
        resizable = true,
    ) {
        val composeWindow = window
        LaunchedEffect(composeWindow) {
            composeWindow.background = java.awt.Color(0, 0, 0, 0)
            (composeWindow.contentPane as? javax.swing.JComponent)?.isOpaque = false
            composeWindow.rootPane.isOpaque = false
        }

        FloatingLyricsContent(
            onDragWindow = { dx, dy ->
                val currentLoc = composeWindow.location
                composeWindow.setLocation(currentLoc.x + dx, currentLoc.y + dy)
            },
        )
    }
}

@Composable
private fun FloatingLyricsContent(onDragWindow: (Int, Int) -> Unit) {
    val song by DesktopFloatingLyricsManager.currentSong.collectAsState()
    val isPlaying by DesktopFloatingLyricsManager.isPlaying.collectAsState()
    val lines by DesktopFloatingLyricsManager.lyricsLines.collectAsState()
    val positionMs by DesktopFloatingLyricsManager.currentPositionMs.collectAsState()
    val currentStyle by DesktopFloatingLyricsManager.selectedStyle.collectAsState()
    val isLocked by DesktopFloatingLyricsManager.isLocked.collectAsState()
    val fontSize by DesktopFloatingLyricsManager.fontSizeSp.collectAsState()

    // Determine current active lyric line
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

    val activeLine = lines?.getOrNull(activeIndex)
    val nextLine = lines?.getOrNull(activeIndex + 1)
    val lyricText = when {
        activeLine != null && activeLine.text.isNotBlank() -> activeLine.text
        song != null -> "${song?.title} · ${song?.artist}"
        else -> "BitChord Floating Lyrics"
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Drag gesture modifier
    val dragModifier = if (!isLocked) {
        Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                onDragWindow(dragAmount.x.toInt(), dragAmount.y.toInt())
            }
        }
    } else Modifier

    // Outer container: translucent dark glass with subtle glow
    val bgAlpha = if (currentStyle == FloatingLyricsStyle.AMBIENT_GHOST) 0.0f else (if (isHovered) 0.88f else 0.72f)
    val shape = RoundedCornerShape(18.dp)

    Box(
        Modifier
            .fillMaxSize()
            .padding(4.dp)
            .hoverable(interactionSource)
            .then(dragModifier),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color(0xFF161618).copy(alpha = bgAlpha))
                .border(
                    width = if (currentStyle == FloatingLyricsStyle.AMBIENT_GHOST) 0.dp else 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isHovered) 0.25f else 0.12f),
                            Color.White.copy(alpha = 0.03f),
                        ),
                    ),
                    shape = shape,
                )
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            // Control action buttons on hover (Lock, Change Style, Close)
            if (isHovered || isLocked) {
                Row(
                    Modifier.align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Style switch badge
                    FloatingActionBadge(
                        icon = Icons.Rounded.FormatPaint,
                        tooltip = currentStyle.title,
                        onClick = { DesktopFloatingLyricsManager.cycleStyle() },
                    )
                    // Lock toggle
                    FloatingActionBadge(
                        icon = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        tooltip = if (isLocked) "Terkunci" else "Bisa Digeser",
                        tint = if (isLocked) Color(0xFFFFD54F) else Color.White,
                        onClick = { DesktopFloatingLyricsManager.isLocked.value = !isLocked },
                    )
                    // Close button
                    FloatingActionBadge(
                        icon = Icons.Rounded.Close,
                        tooltip = "Tutup Lirik",
                        hoverBg = Color(0xFFC42B1C),
                        onClick = { DesktopFloatingLyricsManager.isEnabled.value = false },
                    )
                }
            }

            // Central Lyrics Area with Animated Transitions
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AnimatedContent(
                    targetState = Pair(lyricText, activeIndex),
                    transitionSpec = {
                        val isEven = targetState.second % 2 == 0
                        when (currentStyle) {
                            FloatingLyricsStyle.PING_PONG -> {
                                if (isEven) {
                                    (slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { -it / 2 } + fadeIn())
                                        .togetherWith(slideOutHorizontally(tween(300)) { it / 2 } + fadeOut())
                                } else {
                                    (slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn())
                                        .togetherWith(slideOutHorizontally(tween(300)) { -it / 2 } + fadeOut())
                                }
                            }
                            FloatingLyricsStyle.CAPSULE_POP -> {
                                (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 400f)) { -it / 2 } + fadeIn() + scaleIn(initialScale = 0.92f))
                                    .togetherWith(slideOutVertically(tween(250)) { it / 3 } + fadeOut())
                            }
                            FloatingLyricsStyle.KARAOKE_SWEEP -> {
                                (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 })
                                    .togetherWith(fadeOut(tween(250)) + slideOutVertically(tween(250)) { -it / 4 })
                            }
                            FloatingLyricsStyle.AMBIENT_GHOST -> {
                                (fadeIn(tween(400)) + slideInVertically(tween(400)) { 8 })
                                    .togetherWith(fadeOut(tween(350)))
                            }
                        }
                    },
                ) { pair ->
                    val textToRender = pair.first
                    val isLyric = activeLine != null

                    Text(
                        text = textToRender,
                        fontSize = fontSize.sp,
                        fontWeight = if (isLyric) FontWeight.Bold else FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.90f),
                                offset = Offset(0f, 2f),
                                blurRadius = 8f,
                            ),
                        ),
                    )
                }

                // Next lyric preview line (subtle)
                if (nextLine != null && nextLine.text.isNotBlank() && currentStyle != FloatingLyricsStyle.AMBIENT_GHOST) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = nextLine.text,
                        fontSize = (fontSize - 7).coerceAtLeast(12).sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingActionBadge(
    icon: ImageVector,
    tooltip: String,
    tint: Color = Color.White,
    hoverBg: Color = Color.White.copy(alpha = 0.18f),
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val bg = if (isHovered) hoverBg else Color.Black.copy(alpha = 0.35f)

    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(bg)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = tooltip, tint = tint, modifier = Modifier.size(13.dp))
    }
}
