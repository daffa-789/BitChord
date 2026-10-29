package com.music.bitchord.desktop

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import kotlin.math.roundToInt

/**
 * The floating capsule, as a window of its own.
 *
 * It used to be drawn inside the main window, which made "hide the app" and "keep the island" the
 * same request: hiding the window took the capsule with it. A Dynamic Island that disappears when
 * the app does is just a widget; the point of one is that it stays on screen over everything else
 * while the app itself is out of the way. So it is a separate, undecorated, transparent,
 * always-on-top window that the main window's visibility has no say over.
 *
 * It is deliberately not focusable and never takes the taskbar: it is a readout you glance at, not
 * a window you work in. Everything it shows comes from [DesktopFloatingLyricsManager], which the
 * main window keeps fed whether or not it is on screen.
 */
@Composable
internal fun DesktopIslandWindow() {
    val enabled by DesktopDynamicIslandSettings.enabled.collectAsState()
    val layoutSetting by DesktopDynamicIslandSettings.layout.collectAsState()
    val anchor by DesktopDynamicIslandSettings.anchor.collectAsState()
    val scale by DesktopDynamicIslandSettings.scale.collectAsState()
    val opacity by DesktopDynamicIslandSettings.opacity.collectAsState()
    val autoCollapse by DesktopDynamicIslandSettings.autoCollapse.collectAsState()
    val hideWhenPaused by DesktopDynamicIslandSettings.hideWhenPaused.collectAsState()
    val locked by DesktopDynamicIslandSettings.locked.collectAsState()

    val song by DesktopFloatingLyricsManager.currentSong.collectAsState()
    val isPlaying by DesktopFloatingLyricsManager.isPlaying.collectAsState()
    val lines by DesktopFloatingLyricsManager.lyricsLines.collectAsState()
    val repeatMode by DesktopFloatingLyricsManager.repeatMode.collectAsState()

    var menuOpen by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Same three rungs as the in-window capsule: nothing playing is the mini pill, and hovering
    // grows an auto-collapsed island back.
    val expanded = !autoCollapse || isHovered || menuOpen
    val shownLayout = when {
        song == null -> IslandLayout.MINI
        expanded -> layoutSetting
        else -> IslandLayout.MINI
    }

    val width = shownLayout.baseWidth * scale
    val height = shownLayout.baseHeight * scale
    val position = remember(anchor, width, height) {
        islandWindowPosition(anchor, width, height)
    }
    val state = rememberWindowState(
        size = islandWindowSize(width, height),
        position = position,
    )

    // The capsule changes size with its layout and the user's scale, and the window has to follow
    // or the capsule is either clipped or floating in a box of empty space that eats clicks.
    LaunchedEffect(width, height) {
        state.size = islandWindowSize(width, height)
    }
    LaunchedEffect(position) { state.position = position }

    // Nothing to say while stopped, when the user asked it to keep quiet between songs.
    val visible = enabled && !(hideWhenPaused && !isPlaying && song != null)

    Window(
        onCloseRequest = { DesktopDynamicIslandSettings.setEnabled(false) },
        visible = visible,
        title = "BitChord Island",
        undecorated = true,
        transparent = true,
        resizable = false,
        // Over other applications, which is the whole reason this is a window of its own.
        alwaysOnTop = true,
        // A readout, not a workspace: it must not steal focus from whatever is being typed into.
        focusable = false,
        state = state,
    ) {
        LaunchedEffect(window) {
            // Out of the taskbar and out of Alt-Tab, the way a system overlay behaves.
            runCatching { window.type = java.awt.Window.Type.UTILITY }
            window.background = java.awt.Color(0, 0, 0, 0)
            window.contentPane.background = java.awt.Color(0, 0, 0, 0)
            (window.contentPane as? javax.swing.JComponent)?.isOpaque = false
            window.rootPane.isOpaque = false
            // A transparent AWT window still paints its own shadow and border on some Windows
            // builds, which shows as a grey rectangle around the capsule.
            runCatching { window.minimumSize = Dimension(1, 1) }
        }

        val dragModifier = if (locked) {
            Modifier
        } else {
            Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                    var travelled = Offset.Zero
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        travelled += delta
                        if (!dragging && travelled.getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                        }
                        if (dragging) {
                            change.consume()
                            // The window is moved, not the capsule inside it: a separate window
                            // has its own position, and moving it is what the OS does cheaply.
                            val at = window.location
                            window.setLocation(at.x + delta.x.roundToInt(), at.y + delta.y.roundToInt())
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(ISLAND_WINDOW_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .hoverable(interactionSource)
                    .then(dragModifier)
                    .islandContextMenu { menuOpen = true },
            ) {
                DynamicIslandCard(
                    song = song,
                    lines = lines,
                    positionMs = { DesktopFloatingLyricsManager.currentPositionMs.value },
                    progress = {
                        islandProgress(
                            DesktopFloatingLyricsManager.currentPositionMs.value,
                            DesktopFloatingLyricsManager.durationMs.value,
                        )
                    },
                    isPlaying = isPlaying,
                    repeatMode = repeatMode,
                    layout = shownLayout,
                    scale = scale,
                    opacity = opacity,
                    hovered = isHovered,
                    interactive = true,
                    onPlayPause = { DesktopFloatingLyricsManager.onPlayPause?.invoke() },
                    onNext = { DesktopFloatingLyricsManager.onNext?.invoke() },
                    onPrevious = { DesktopFloatingLyricsManager.onPrevious?.invoke() },
                    onToggleRepeat = { DesktopFloatingLyricsManager.onToggleRepeat?.invoke() },
                    onOpenNowPlaying = { DesktopFloatingLyricsManager.onOpenNowPlaying?.invoke() },
                    menu = {
                        if (isHovered || menuOpen) {
                            Row(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 5.dp, end = 9.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IslandBadge(
                                    icon = if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                    tooltip = if (locked) "Unlock" else "Lock position",
                                    tint = if (locked) Color(0xFFFFD54F) else Color.White,
                                    onClick = { DesktopDynamicIslandSettings.setLocked(!locked) },
                                )
                                IslandBadge(
                                    icon = Icons.Rounded.MoreHoriz,
                                    tooltip = "Island menu",
                                    onClick = { menuOpen = true },
                                )
                                IslandBadge(
                                    icon = Icons.Rounded.Close,
                                    tooltip = "Hide the island",
                                    hoverBg = Color(0xFFC42B1C),
                                    onClick = { DesktopDynamicIslandSettings.setEnabled(false) },
                                )
                            }
                        }
                        DynamicIslandMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false })
                    },
                )
            }
        }
    }
}

/**
 * Where the capsule's window sits on screen for [anchor].
 *
 * The anchor is measured against the screen's usable area rather than its bounds, so a capsule
 * anchored to the bottom does not land under the taskbar.
 */
private fun islandWindowPosition(anchor: IslandAnchor, width: Dp, height: Dp): WindowPosition {
    val config = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    }.getOrNull() ?: return WindowPosition.Aligned(anchor.alignment)
    val insets = runCatching { Toolkit.getDefaultToolkit().getScreenInsets(config) }.getOrNull()
    val scale = runCatching { config.defaultTransform.scaleX }.getOrDefault(1.0).takeIf { it > 0.0 } ?: 1.0
    val bounds = config.bounds
    val left = bounds.x + (insets?.left ?: 0)
    val top = bounds.y + (insets?.top ?: 0)
    val usableWidth = bounds.width - (insets?.left ?: 0) - (insets?.right ?: 0)
    val usableHeight = bounds.height - (insets?.top ?: 0) - (insets?.bottom ?: 0)

    // In device pixels, which is what AWT positions in; the capsule is measured in dp.
    val w = (width.value + ISLAND_WINDOW_PADDING.value * 2) * scale
    val h = (height.value + ISLAND_WINDOW_PADDING.value * 2) * scale
    val margin = ISLAND_SCREEN_MARGIN * scale

    val x = when (anchor) {
        IslandAnchor.TOP_LEFT, IslandAnchor.BOTTOM_LEFT -> left + margin
        IslandAnchor.TOP_RIGHT, IslandAnchor.BOTTOM_RIGHT -> left + usableWidth - w - margin
        else -> left + (usableWidth - w) / 2.0
    }
    val y = if (anchor.isBottom) {
        top + usableHeight - h - margin
    } else {
        top + margin
    }
    return WindowPosition(x.roundToInt().dp, y.roundToInt().dp)
}

/** Slack around the capsule inside its window, so the drop shadow has somewhere to land. */
private val ISLAND_WINDOW_PADDING = 14.dp

/** The capsule plus its shadow slack, in dp. */
private fun islandWindowSize(width: Dp, height: Dp): DpSize =
    DpSize(width + ISLAND_WINDOW_PADDING * 2, height + ISLAND_WINDOW_PADDING * 2)

/** How far the capsule's window sits from the screen edge. */
private const val ISLAND_SCREEN_MARGIN = 18
