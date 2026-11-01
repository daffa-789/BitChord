package com.music.bitchord.desktop

import java.awt.EventQueue
import java.awt.Frame
import java.awt.Window
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the window is on screen, and what closing it means. */
internal object DesktopWindowVisibility {

    private val _visible = MutableStateFlow(true)

    /** What the window's own `visible` is driven from. */
    val visible: StateFlow<Boolean> = _visible

    private val _focused = MutableStateFlow(true)

    /**
     * Whether the main window holds the keyboard focus.
     *
     * The floating island is the player only when this window is out of the way. While it is in
     * front the dock is the player, and a second transport floating over other applications would
     * be a duplicate of the one already on screen.
     */
    val focused: StateFlow<Boolean> = _focused

    /** Whether a close should hide rather than quit. */
    @Volatile
    var keepRunningWhenClosed: Boolean = false

    private var windowRef: WeakReference<Window>? = null
    private var isListenerInstalled = false

    /** Attaches the active Compose window so show() can bring it to the foreground. */
    fun attachWindow(window: Window) {
        windowRef = WeakReference(window)
        _focused.value = window.isFocused
        if (!isListenerInstalled) {
            window.addWindowFocusListener(
                object : WindowFocusListener {
                    override fun windowGainedFocus(event: WindowEvent?) {
                        _focused.value = true
                    }

                    override fun windowLostFocus(event: WindowEvent?) {
                        _focused.value = false
                    }
                },
            )
            isListenerInstalled = true
        }
    }

    /** Brings the window back — from the tray menu or the icon itself. */
    fun show() {
        _visible.value = true
        EventQueue.invokeLater {
            val win = windowRef?.get()
            if (win != null) {
                if (win is Frame && (win.extendedState and Frame.ICONIFIED) != 0) {
                    win.extendedState = win.extendedState and Frame.ICONIFIED.inv()
                }
                win.isVisible = true
                win.toFront()
                win.requestFocus()
            }

        }
    }

    /**
     * Handles the window's close request.
     * @return true when the caller should end the application; false when the
     */
    fun onCloseRequest(): Boolean {
        if (!keepRunningWhenClosed) return true
        hide()
        return false
    }

    /**
     * Puts the main window away without ending the process.
     *
     * Playback continues and, since the floating island is a window of its own, so does the
     * capsule — which is the point: the app can be out of the way while the island stays on screen.
     */
    fun hide() {
        // A window put away full-screen comes back full-screen with nothing on it, which reads as
        // the app having broken rather than as it having been hidden.
        DesktopWindowMode.exit()
        _visible.value = false
        _focused.value = false
        DesktopTrackLog.log("window hidden; playback continues")
    }
}
