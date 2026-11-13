package com.music.bitchord.desktop

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.music.bitchord.ui.theme.BcMotion
import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import java.awt.Dimension
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.Window as AwtWindow
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Windows' click-through for the capsule's window.
 *
 * `WS_EX_TRANSPARENT` (with `WS_EX_LAYERED`, its usual companion on a transparent window) is what
 * makes the system's hit test skip a window entirely, so a click lands on whatever is underneath.
 * With the flag on, the capsule receives no mouse input at all — no menu, no transport — which is
 * exactly what the toggle promises, and the way back is the toolbar's island button or Settings,
 * neither of which lives under the capsule.
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
 * The island: a frameless window that stays on screen and changes shape.
 *
 * It used to be drawn inside the main window, which made "hide the app" and "keep the island" the
 * same request: hiding the window took the capsule with it. A Dynamic Island that disappears when
 * the app does is just a widget; the point of one is that it stays on screen over everything else
 * while the app itself is out of the way. So it is a separate, undecorated, transparent,
 * always-on-top window that the main window's visibility has no say over.
 *
 * And it does not go away when the app comes forward either. That is the other half of "stuck to
 * the screen": the capsule rests at the top centre of the display, over whatever is in front of
 * it, and opens when the pointer lands on it. It is a readout you glance at, not a window you
 * work in — it never takes the focus, and it never appears in the taskbar or in Alt-Tab.
 *
 * It is not a window you move either. It belongs to the screen the way the notch belongs to the
 * phone, so there is no dragging, no place to put it and no way to lose it off the edge of the
 * display.
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

    val scale by DesktopDynamicIslandSettings.scale.collectAsState()
    val hideWhenPaused by DesktopDynamicIslandSettings.hideWhenPaused.collectAsState()
    val clickThrough by DesktopDynamicIslandSettings.clickThrough.collectAsState()

    val song by DesktopIslandState.currentSong.collectAsState()
    val isPlaying by DesktopIslandState.isPlaying.collectAsState()
    val lines by DesktopIslandState.lyricsLines.collectAsState()
    val repeatMode by DesktopIslandState.repeatMode.collectAsState()

    var menuOpen by remember { mutableStateOf(false) }
    // True while the pointer is down on the scrub bar, so the same drag scrubs the song.
    var seeking by remember { mutableStateOf(false) }

    /**
     * Whether the pointer is on the capsule.
     *
     * Not the platform's own answer. The window is resized on every frame of the morph, and a
     * window that moves under a stationary pointer has its enter and leave events delivered
     * whenever the system's hit test changes its mind — which, on a growing window sitting over
     * another one, it did: the pointer stayed on the glass and the island kept losing the hover,
     * so it opened and shut again and again while the mouse sat still.
     *
     * The pointer's place on the screen is read directly instead, and compared with the glass's
     * own rectangle. That cannot flicker: the same question gets the same answer until either
     * the glass or the pointer really has moved.
     */
    val hovered = remember { mutableStateOf(false) }

    /**
     * How far the capsule has grown, from 0 (the resting pill) to 1 (the full face).
     *
     * One number drives everything: the window's own bounds and the drawing inside it. That is
     * what makes the morph seamless — before, the window snapped to the new size while the
     * capsule animated inside it, so for a third of a second the glass was either clipped or
     * floating in a box of empty space.
     */
    val expansion = remember { mutableFloatStateOf(0f) }
    // A capsule that lets clicks through is a readout: it does not react to the pointer either. A
    // paused island is not a player that has stopped, but a player that has stopped — and a stopped
    // player is not something the pointer opens. Only a playing one does.
    val wantExpanded = (!clickThrough && hovered.value && isPlaying) || menuOpen || seeking
    LaunchedEffect(wantExpanded) {
        animate(
            initialValue = expansion.floatValue,
            targetValue = if (wantExpanded) 1f else 0f,
            animationSpec = BcMotion.islandSpring(),
        ) { value, _ -> expansion.floatValue = value }
    }

    val showing = song != null && !(hideWhenPaused && !isPlaying)

    /**
     * The window's state.
     *
     * It never changes. Compose applies a window's own state when the window is created, and
     * applying it again on every frame of the morph had the window resizing to two different
     * sizes in turn — the glass juddered between them. AWT is the only thing that moves the
     * bounds; see below.
     */
    val state = rememberWindowState(
        size = DpSize(1.dp, 1.dp),
        position = WindowPosition.Aligned(androidx.compose.ui.Alignment.TopCenter),
    )

    Window(
        onCloseRequest = { DesktopDynamicIslandSettings.setEnabled(false) },
        /**
         * The window is always on screen, even with nothing to show.
         *
         * A window that has never been shown has no size of its own yet, and Compose gives it
         * one when it is created — with the screen's scaling factor missing, so the capsule was
         * laid out at one size and drawn at another, a fifth apart. Bringing the window up once,
         * empty, settles that: it is on the display, it knows how big a pixel is, and it stays
         * that way for the rest of the session.
         *
         * An empty island is a window one pixel across, so it neither shows nor catches a click.
         */
        visible = true,
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
        /**
         * The window's own density — the factor that turns the dp everything here is written in
         * into the pixels that are actually drawn.
         *
         * It is read from inside the window rather than from the screen, because this window is
         * created before it is on screen and picks its density up as it is realised. The two
         * agree once the capsule is up; in the moment before, the capsule is smaller than it will
         * be, but it is still in the right place.
         */
        val density = LocalDensity.current.density
        val work = remember(density) { workAreaInDp(density) }

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

        /**
         * Where the pointer is, and whether it is on the glass.
         *
         * The glass is the window without the slack its shadow needs, so the island opens when
         * the pointer is on the capsule itself and not when it merely passes close by.
         */
        LaunchedEffect(window, density) {
            val slack = (ISLAND_WINDOW_PADDING * density).roundToInt()
            while (true) {
                val pointer = runCatching { java.awt.MouseInfo.getPointerInfo().location }.getOrNull()
                val onGlass = pointer != null &&
                    pointer.x >= window.x + slack && pointer.x < window.x + window.width - slack &&
                    pointer.y >= window.y + slack && pointer.y < window.y + window.height - slack
                hovered.value = onGlass
                delay(POINTER_POLL_MS)
            }
        }

        // Keyed on the window too: an island switched off and back on gets a fresh window, whose
        // style has to be set again if click-through is still asked for.
        LaunchedEffect(window, clickThrough) {
            DesktopIslandClickThrough.apply(window, clickThrough)
        }

        /**
         * The window follows the morph.
         *
         * Every frame the capsule's size changes, the window that holds it is told its new size
         * and its new place on the screen. The two therefore move as one thing: the glass is
         * never clipped by a frame that has not caught up, and never floats in a box of empty
         * space that would swallow the clicks meant for whatever is underneath.
         *
         * AWT is the only thing that touches the bounds. Compose re-applies a window's own size
         * whenever the window's state changes, and it applies it with a different density than
         * the one the capsule is drawn at — so the two took turns resizing the window to
         * different sizes, and the window juddered between them on every frame of the morph.
         * Leaving the window's state alone keeps one writer and one size.
         */
        LaunchedEffect(window, scale, density, showing) {
            snapshotFlow { expansion.floatValue }.collect { grown ->
                if (!showing) {
                    // Nothing to show: the window shrinks to a single pixel, so it neither shows
                    // nor stands between the pointer and whatever is underneath.
                    window.setBounds(0, 0, 1, 1)
                    return@collect
                }
                val width = (ISLAND_COLLAPSED_WIDTH +
                    (ISLAND_EXPANDED_WIDTH - ISLAND_COLLAPSED_WIDTH) * grown) * scale
                val height = (ISLAND_COLLAPSED_HEIGHT +
                    (ISLAND_EXPANDED_HEIGHT - ISLAND_COLLAPSED_HEIGHT) * grown) * scale
                val placed = work?.let { islandPlacedRect(it, width, height) }
                val x = ((placed?.left ?: 0f) - ISLAND_WINDOW_PADDING) * density
                val y = ((placed?.top ?: 0f) - ISLAND_WINDOW_PADDING) * density
                val w = (width + ISLAND_WINDOW_PADDING * 2f) * density
                val h = (height + ISLAND_WINDOW_PADDING * 2f) * density
                window.setBounds(
                    x.roundToInt(),
                    y.roundToInt(),
                    w.roundToInt(),
                    h.roundToInt(),
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(ISLAND_WINDOW_PADDING.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .islandContextMenu { menuOpen = true },
            ) {
                if (showing && song != null && !isPlaying) {
                    // A stopped player is not something you look at, and a stopped player is not
                    // something you play from here: the island keeps nothing but a button, which is
                    // what hands the screen back to the music instead of leaving a statue behind.
                    DesktopIslandDoorbell(onClick = { DesktopIslandState.onOpenNowPlaying?.invoke() })
                } else {
                    DesktopIslandCard(
                        song = song,
                        lines = lines,
                        positionMs = { DesktopIslandState.currentPositionMs.value },
                        durationMs = { DesktopIslandState.durationMs.value },
                        isPlaying = isPlaying,
                        repeatMode = repeatMode,
                        scale = scale,
                        hovered = hovered.value,
                        interactive = true,
                        onPlayPause = { DesktopIslandState.onPlayPause?.invoke() },
                        onNext = { DesktopIslandState.onNext?.invoke() },
                        onPrevious = { DesktopIslandState.onPrevious?.invoke() },
                        onToggleRepeat = { DesktopIslandState.onToggleRepeat?.invoke() },
                        onOpenNowPlaying = { DesktopIslandState.onOpenNowPlaying?.invoke() },
                        // Click-through hands every press to the window underneath, so a scrub bar
                        // there would only take space and promise something it cannot do.
                        onSeek = if (clickThrough) null else DesktopIslandState.onSeek,
                        onSeekStarted = { seeking = true },
                        onSeekFinished = { seeking = false },
                        menu = {
                            DynamicIslandMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false })
                        },
                    )
                }
            }
        }
    }
}

/**
 * A screen's work area, in device pixels.
 *
 * The island's own window is not the main window, so it cannot borrow the main window's idea of
 * a screen: this is the display the capsule is actually resting on.
 */
private class IslandScreenPixels(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

private fun screenArea(config: GraphicsConfiguration): IslandScreenPixels {
    val bounds = config.bounds
    val insets = runCatching { Toolkit.getDefaultToolkit().getScreenInsets(config) }.getOrNull()
    return IslandScreenPixels(
        left = bounds.x + (insets?.left ?: 0),
        top = bounds.y + (insets?.top ?: 0),
        right = bounds.x + bounds.width - (insets?.right ?: 0),
        bottom = bounds.y + bounds.height - (insets?.bottom ?: 0),
    )
}

private fun defaultScreenArea(): IslandScreenPixels? = runCatching {
    screenArea(GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration)
}.getOrNull()

/**
 * The work area of the display the capsule rests on, in the window's own dp.
 *
 * The divisor is the island window's density, not the screen's: that is the factor Compose will
 * use to turn the dp it is given into the pixels it draws, so dividing by it here is what keeps
 * the capsule the same physical size as everything else and the same distance from the screen's
 * edges. The two are the same on an unscaled display and differ by exactly the scaling factor on
 * a scaled one — which is where the capsule used to drift off centre and shrink.
 */
private fun workAreaInDp(density: Float): IslandScreen? {
    val screen = defaultScreenArea() ?: return null
    if (density <= 0f) return null
    return IslandScreen(
        left = screen.left / density,
        top = screen.top / density,
        right = screen.right / density,
        bottom = screen.bottom / density,
    )
}

/** Slack around the capsule inside its window, so the drop shadow has somewhere to land. */
private const val ISLAND_WINDOW_PADDING = 14f

/** How often the pointer's place on the screen is read. */
private const val POINTER_POLL_MS = 16L
