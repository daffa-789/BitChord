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
import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window as AwtWindow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Windows' click-through for the capsule's window.
 *
 * `WS_EX_TRANSPARENT` (with `WS_EX_LAYERED`, its usual companion on a transparent window) is what
 * makes the system's hit test skip a window entirely, so a click lands on whatever is underneath.
 * With the flag on, the capsule receives no mouse input at all — no drag, no menu, no transport —
 * which is exactly what the toggle promises, and the way back is the toolbar's island button or
 * Settings, neither of which lives under the capsule.
 *
 * JNA's own User32 mapping is used rather than a new export in `bitchord_window.dll`: the frame
 * bridge installs on the main window and would need a second lookup for the island, while this is
 * one window whose handle the caller already holds. Linux has no equivalent here — its window
 * manager owns input shape, and the toggle is simply not offered there.
 */
internal object DesktopIslandClickThrough {

    suspend fun apply(window: AwtWindow, enabled: Boolean) {
        if (!DesktopPlatform.isWindows) return
        repeat(APPLY_ATTEMPTS) { attempt ->
            val done = runCatching {
                val handle = Native.getComponentPointer(window) ?: return@runCatching false
                val user32 = User32.INSTANCE
                val hwnd = HWND(handle)
                val style = user32.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
                val wanted = if (enabled) {
                    style or WinUser.WS_EX_TRANSPARENT or WinUser.WS_EX_LAYERED
                } else {
                    style and WinUser.WS_EX_TRANSPARENT.inv()
                }
                if (wanted != style) user32.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, wanted)
                true
            }.getOrElse {
                DesktopTrackLog.log("island click-through failed: ${it.message}")
                true
            }
            if (done) return
            // The AWT peer does not exist the instant the composable enters composition; give it
            // a moment rather than dropping the toggle the user just switched.
            delay(APPLY_RETRY_MILLIS)
        }
        DesktopTrackLog.log("island click-through: no native handle for the capsule's window")
    }

    private const val APPLY_ATTEMPTS = 20
    private const val APPLY_RETRY_MILLIS = 100L
}

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
 *
 * When the island is switched off, nothing is composed at all — the window is not merely hidden
 * but absent, so an off island costs no AWT peer, no Compose composition and no state collection.
 * Turning it back on builds a fresh window, which is also why the click-through style is applied
 * per-window rather than once.
 */
@Composable
internal fun DesktopIslandWindow() {
    val enabled by DesktopDynamicIslandSettings.enabled.collectAsState()
    // An off island is no island: no window, no composition, nothing to weigh the process down.
    if (!enabled) return
    val layoutSetting by DesktopDynamicIslandSettings.layout.collectAsState()
    val anchor by DesktopDynamicIslandSettings.anchor.collectAsState()
    val scale by DesktopDynamicIslandSettings.scale.collectAsState()
    val opacity by DesktopDynamicIslandSettings.opacity.collectAsState()
    val autoCollapse by DesktopDynamicIslandSettings.autoCollapse.collectAsState()
    val hideWhenPaused by DesktopDynamicIslandSettings.hideWhenPaused.collectAsState()
    val locked by DesktopDynamicIslandSettings.locked.collectAsState()
    val clickThrough by DesktopDynamicIslandSettings.clickThrough.collectAsState()
    val offsetX by DesktopDynamicIslandSettings.offsetX.collectAsState()
    val offsetY by DesktopDynamicIslandSettings.offsetY.collectAsState()

    val mainFocused by DesktopWindowVisibility.focused.collectAsState()
    val mainVisible by DesktopWindowVisibility.visible.collectAsState()

    val song by DesktopFloatingLyricsManager.currentSong.collectAsState()
    val isPlaying by DesktopFloatingLyricsManager.isPlaying.collectAsState()
    val lines by DesktopFloatingLyricsManager.lyricsLines.collectAsState()
    val repeatMode by DesktopFloatingLyricsManager.repeatMode.collectAsState()

    var menuOpen by remember { mutableStateOf(false) }
    // True while the pointer is down on the FULL capsule's scrub bar, so the same drag scrubs the
    // song instead of carrying the window across the screen.
    var seeking by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Same three rungs as the in-window capsule: nothing playing is the mini pill, and hovering
    // grows an auto-collapsed island back. A scrub keeps it grown too — dragging past the edge of
    // the capsule would otherwise collapse the very bar being dragged.
    val expanded = !autoCollapse || isHovered || menuOpen || seeking
    val shownLayout = when {
        song == null -> IslandLayout.MINI
        expanded -> layoutSetting
        else -> IslandLayout.MINI
    }

    val width = shownLayout.baseWidth * scale
    val height = shownLayout.baseHeight * scale
    val position = remember(anchor, width, height, offsetX, offsetY) {
        islandWindowPosition(anchor, width, height, offsetX, offsetY)
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

    // The dock is the player while the main window is in front; the capsule takes over the moment
    // that window is hidden or another application gets the focus.
    val mainInFront = mainVisible && mainFocused
    // Nothing to say before a song is loaded: a capsule reading "Nothing playing" over every other
    // window is a permanent advertisement, not a player.
    val visible = enabled && song != null && !mainInFront && !(hideWhenPaused && !isPlaying)

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

        // Keyed on the window too: an island switched off and back on gets a fresh window, whose
        // style has to be set again if click-through is still asked for.
        LaunchedEffect(window, clickThrough) {
            DesktopIslandClickThrough.apply(window, clickThrough)
        }

        // While the scrub bar holds the pointer the capsule must stay put: both gestures read the
        // same drag, so the key change cancels this loop the moment a scrub begins.
        val dragModifier = if (locked || seeking) {
            Modifier
        } else {
            Modifier.pointerInput(window, anchor, width, height) {
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
                    // Only a real drag is remembered; a click that happened to land on the glass
                    // must not rewrite the capsule's resting place.
                    if (dragging) settleIslandWindow(window, anchor, width, height)
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
                    durationMs = { DesktopFloatingLyricsManager.durationMs.value },
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
                    // Click-through hands every press to the window underneath, so a scrub bar
                    // there would only take space and promise something it cannot do.
                    onSeek = if (clickThrough) null else DesktopFloatingLyricsManager.onSeek,
                    onSeekStarted = { seeking = true },
                    onSeekFinished = { seeking = false },
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
 * A screen's work area, and how many device pixels one dp is on it.
 *
 * AWT positions windows and reports screen bounds in device pixels, while everything the capsule
 * is drawn in is dp; the scale is what lets the two agree on a 125% display.
 */
private class IslandScreenArea(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val scale: Double,
)

private fun screenArea(config: java.awt.GraphicsConfiguration): IslandScreenArea {
    val bounds = config.bounds
    val insets = runCatching { Toolkit.getDefaultToolkit().getScreenInsets(config) }.getOrNull()
    val scale = runCatching { config.defaultTransform.scaleX }.getOrDefault(1.0).takeIf { it > 0.0 } ?: 1.0
    return IslandScreenArea(
        left = bounds.x + (insets?.left ?: 0),
        top = bounds.y + (insets?.top ?: 0),
        right = bounds.x + bounds.width - (insets?.right ?: 0),
        bottom = bounds.y + bounds.height - (insets?.bottom ?: 0),
        scale = scale,
    )
}

private fun defaultScreenArea(): IslandScreenArea? = runCatching {
    screenArea(GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration)
}.getOrNull()

/** The display [x], [y] falls on, or null when the point is outside every screen. */
private fun screenAreaContaining(x: Int, y: Int): IslandScreenArea? = runCatching {
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        .map { screenArea(it.defaultConfiguration) }
        .firstOrNull { x >= it.left && x < it.right && y >= it.top && y < it.bottom }
}.getOrNull()

/** The capsule window's size in the device pixels AWT positions it in. */
private fun islandSizePx(width: Dp, height: Dp, scale: Double): Pair<Int, Int> {
    val w = ((width.value + ISLAND_WINDOW_PADDING.value * 2) * scale).roundToInt()
    val h = ((height.value + ISLAND_WINDOW_PADDING.value * 2) * scale).roundToInt()
    return w to h
}

/** Where [anchor] rests on the primary screen, before any drag offset. Device pixels. */
private fun islandBaseLocation(anchor: IslandAnchor, width: Dp, height: Dp): Point? {
    val screen = defaultScreenArea() ?: return null
    val (w, h) = islandSizePx(width, height, screen.scale)
    val margin = (ISLAND_SCREEN_MARGIN * screen.scale).roundToInt()
    val x = when (anchor) {
        IslandAnchor.TOP_LEFT, IslandAnchor.BOTTOM_LEFT -> screen.left + margin
        IslandAnchor.TOP_RIGHT, IslandAnchor.BOTTOM_RIGHT -> screen.right - w - margin
        else -> screen.left + (screen.right - screen.left - w) / 2
    }
    val y = if (anchor.isBottom) screen.bottom - h - margin else screen.top + margin
    return Point(x, y)
}

/**
 * Where the capsule's window sits on screen: its anchor's resting place, shifted by the nudge the
 * listener dragged it to, and held inside the display it lands on.
 *
 * Compose hands [WindowPosition.Absolute] values straight to `Window.setLocation`, which is device
 * pixels, so the numbers here are pixels even though the type says dp.
 */
private fun islandWindowPosition(
    anchor: IslandAnchor,
    width: Dp,
    height: Dp,
    offsetX: Float,
    offsetY: Float,
): WindowPosition {
    val base = islandBaseLocation(anchor, width, height)
        ?: return WindowPosition.Aligned(anchor.alignment)
    val screen = defaultScreenArea() ?: return WindowPosition.Aligned(anchor.alignment)
    val (w, h) = islandSizePx(width, height, screen.scale)
    val desiredX = base.x + (offsetX * screen.scale).roundToInt()
    val desiredY = base.y + (offsetY * screen.scale).roundToInt()
    // The display the capsule's centre lands on decides how far it may go, so a saved nudge can
    // never leave it half off-screen — or on a monitor that is no longer attached.
    val landing = screenAreaContaining(desiredX + w / 2, desiredY + h / 2) ?: screen
    val x = desiredX.coerceIn(landing.left, (landing.right - w).coerceAtLeast(landing.left))
    val y = desiredY.coerceIn(landing.top, (landing.bottom - h).coerceAtLeast(landing.top))
    return WindowPosition(x.dp, y.dp)
}

/**
 * Nests the capsule against the edge it was dropped near and remembers where that was.
 *
 * The offset is stored in dp from the anchor's resting place, so a nudge survives a resolution
 * change or a different display scale. Within [ISLAND_SNAP_DP] of a work-area edge the capsule
 * snaps to it, which is how "top centre" is reached again after a free-handed drag.
 */
private fun settleIslandWindow(window: AwtWindow, anchor: IslandAnchor, width: Dp, height: Dp) {
    val base = islandBaseLocation(anchor, width, height) ?: return
    val scale = defaultScreenArea()?.scale ?: 1.0
    val at = window.location
    val landing = screenAreaContaining(at.x + window.width / 2, at.y + window.height / 2)
    val snapped = snapIslandLocation(at, window.width, window.height, landing)
    if (snapped != at) window.setLocation(snapped)
    DesktopDynamicIslandSettings.saveOffset(
        x = ((snapped.x - base.x) / scale).toFloat(),
        y = ((snapped.y - base.y) / scale).toFloat(),
    )
}

/** Pulls [location] onto a work-area edge when it is close, and back inside the screen otherwise. */
private fun snapIslandLocation(
    location: Point,
    width: Int,
    height: Int,
    screen: IslandScreenArea?,
): Point {
    if (screen == null) return location
    val margin = (ISLAND_SCREEN_MARGIN * screen.scale).roundToInt()
    val snap = (ISLAND_SNAP_DP * screen.scale).roundToInt()
    val left = screen.left + margin
    val right = screen.right - width - margin
    val top = screen.top + margin
    val bottom = screen.bottom - height - margin
    val x = when {
        left <= right && abs(location.x - left) <= snap -> left
        left <= right && abs(location.x - right) <= snap -> right
        else -> location.x.coerceIn(screen.left, (screen.right - width).coerceAtLeast(screen.left))
    }
    val y = when {
        top <= bottom && abs(location.y - top) <= snap -> top
        top <= bottom && abs(location.y - bottom) <= snap -> bottom
        else -> location.y.coerceIn(screen.top, (screen.bottom - height).coerceAtLeast(screen.top))
    }
    return Point(x, y)
}

/** Slack around the capsule inside its window, so the drop shadow has somewhere to land. */
private val ISLAND_WINDOW_PADDING = 14.dp

/** The capsule plus its shadow slack, in dp. */
private fun islandWindowSize(width: Dp, height: Dp): DpSize =
    DpSize(width + ISLAND_WINDOW_PADDING * 2, height + ISLAND_WINDOW_PADDING * 2)

/** How far the capsule's window sits from the screen edge. */
private const val ISLAND_SCREEN_MARGIN = 18

/** How close to an edge a drop must land to be pulled onto it. */
private const val ISLAND_SNAP_DP = 48
