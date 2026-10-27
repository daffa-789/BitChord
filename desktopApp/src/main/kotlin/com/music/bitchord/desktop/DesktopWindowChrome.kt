package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import java.awt.MouseInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// The window's own frame, drawn by the application rather than by the system.

/** The strip where the system's title bar would have been. */
@Composable
internal fun DesktopTitleBar() {
    val enabled by DesktopTitleBarSetting.enabled.collectAsState()
    if (!DesktopPlatform.drawsOwnWindowFrame || !enabled) return
    val barHeight = if (DesktopPlatform.isWindows) 32.dp else CAPTION_HEIGHT
    DesktopTitleBarDragArea(
        Modifier
            .fillMaxWidth()
            .height(barHeight)
            .desktopWindowGlass(),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (DesktopPlatform.isWindows) {
                WindowsCaptionButtons(Modifier.align(Alignment.CenterEnd))
            } else {
                DesktopWindowButtons(Modifier.align(Alignment.CenterStart))
            }
        }
    }
}

/** Whether there is a title bar at all. */
internal object DesktopTitleBarSetting {

    internal const val KEY = "window_title_bar"

    private val _enabled = MutableStateFlow(DesktopPersistence().boolean(KEY, false))

    /** What Settings shows and writes. */
    val enabled: StateFlow<Boolean> = _enabled

    fun set(value: Boolean) {
        DesktopPersistence().saveBoolean(KEY, value)
        _enabled.value = value
    }
}

/** What the caption buttons act on. */
internal class DesktopWindowActions(
    val minimize: () -> Unit,
    val toggleMaximize: () -> Unit,
    val close: () -> Unit,
)

internal val LocalDesktopWindowActions = staticCompositionLocalOf<DesktopWindowActions?> { null }

/** The window, for the drag area. */
internal val LocalDesktopWindowScope = staticCompositionLocalOf<WindowScope?> { null }

/** A region that moves the window, and maximizes it on a double click. */
@Composable
internal fun DesktopTitleBarDragArea(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val scope = LocalDesktopWindowScope.current
    val actions = LocalDesktopWindowActions.current
    if (!DesktopPlatform.drawsOwnWindowFrame || scope == null) {
        Box(modifier) { content() }
        return
    }
    Box(modifier) {
        Box(Modifier.matchParentSize().captionPress(scope, actions))
        content()
    }
}

/** The caption buttons. In the title bar when there is one, otherwise on the toolbar. */
@Composable
internal fun DesktopWindowButtons(modifier: Modifier = Modifier) {
    if (!DesktopPlatform.drawsOwnWindowFrame) return
    if (DesktopPlatform.isWindows) {
        WindowsCaptionButtons(modifier)
    } else {
        MacWindowButtons(modifier)
    }
}

@Composable
internal fun MacWindowButtons(modifier: Modifier = Modifier) {
    val actions = LocalDesktopWindowActions.current ?: return
    val maximized by DesktopWindowMode.maximized.collectAsState()
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val isFocused = windowInfo.isWindowFocused
    val inactiveFill = if (!isFocused && DesktopPlatform.isMac) Color.White.copy(alpha = 0.22f) else null

    Row(
        modifier = modifier.height(CAPTION_HEIGHT).padding(start = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MacCaptionButton("Close", MAC_CLOSE, onClick = actions.close, inactiveFill = inactiveFill) { glyph ->
            val reach = GLYPH_HALF.toPx()
            val line = HAIRLINE.toPx()
            drawLine(
                color = glyph,
                start = Offset(center.x - reach, center.y - reach),
                end = Offset(center.x + reach, center.y + reach),
                strokeWidth = line,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = glyph,
                start = Offset(center.x + reach, center.y - reach),
                end = Offset(center.x - reach, center.y + reach),
                strokeWidth = line,
                cap = StrokeCap.Round,
            )
        }
        MacCaptionButton("Minimize", MAC_MINIMIZE, onClick = actions.minimize, inactiveFill = inactiveFill) { glyph ->
            drawLine(
                color = glyph,
                start = Offset(center.x - GLYPH_HALF.toPx(), center.y),
                end = Offset(center.x + GLYPH_HALF.toPx(), center.y),
                strokeWidth = HAIRLINE.toPx(),
                cap = StrokeCap.Round,
            )
        }
        MacCaptionButton(
            if (maximized) "Restore" else "Maximize",
            MAC_MAXIMIZE,
            onClick = actions.toggleMaximize,
            inactiveFill = inactiveFill,
        ) { glyph ->
            val reach = GLYPH_HALF.toPx()
            val line = HAIRLINE.toPx()
            drawLine(glyph, Offset(center.x - reach, center.y + reach), Offset(center.x + reach, center.y - reach), line, StrokeCap.Round)
            drawLine(glyph, Offset(center.x + reach, center.y - reach), Offset(center.x + 1.dp.toPx(), center.y - reach), line, StrokeCap.Round)
            drawLine(glyph, Offset(center.x + reach, center.y - reach), Offset(center.x + reach, center.y - 1.dp.toPx()), line, StrokeCap.Round)
            drawLine(glyph, Offset(center.x - reach, center.y + reach), Offset(center.x - 1.dp.toPx(), center.y + reach), line, StrokeCap.Round)
            drawLine(glyph, Offset(center.x - reach, center.y + reach), Offset(center.x - reach, center.y + 1.dp.toPx()), line, StrokeCap.Round)
        }
    }
}

/** Native Windows 11 Fluent Caption Buttons (Minimize, Maximize/Restore, Close). */
@Composable
internal fun WindowsCaptionButtons(modifier: Modifier = Modifier) {
    if (!DesktopPlatform.drawsOwnWindowFrame) return
    val actions = LocalDesktopWindowActions.current ?: return
    val maximized by DesktopWindowMode.maximized.collectAsState()
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val isFocused = windowInfo.isWindowFocused
    val fgColor = if (isFocused) Color.White else Color.White.copy(alpha = 0.55f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Minimize Button
        WindowsCaptionButton(
            label = "Minimize",
            onClick = actions.minimize,
        ) {
            val reach = 5.dp.toPx()
            val stroke = 1.dp.toPx()
            drawLine(
                color = fgColor,
                start = Offset(center.x - reach, center.y),
                end = Offset(center.x + reach, center.y),
                strokeWidth = stroke,
                cap = StrokeCap.Square,
            )
        }

        // Maximize / Restore Button
        WindowsCaptionButton(
            label = if (maximized) "Restore" else "Maximize",
            onClick = actions.toggleMaximize,
        ) {
            val stroke = 1.dp.toPx()
            if (maximized) {
                val s = 8.dp.toPx()
                val offset = 2.dp.toPx()
                drawRect(
                    color = fgColor,
                    topLeft = Offset(center.x - s / 2 + offset, center.y - s / 2 - offset),
                    size = Size(s, s),
                    style = Stroke(stroke),
                )
                drawRect(
                    color = fgColor,
                    topLeft = Offset(center.x - s / 2 - offset / 2, center.y - s / 2 + offset / 2),
                    size = Size(s, s),
                    style = Stroke(stroke),
                )
            } else {
                val s = 10.dp.toPx()
                drawRect(
                    color = fgColor,
                    topLeft = Offset(center.x - s / 2, center.y - s / 2),
                    size = Size(s, s),
                    style = Stroke(stroke),
                )
            }
        }

        // Close Button
        WindowsCaptionButton(
            label = "Close",
            isClose = true,
            onClick = actions.close,
        ) { isHovered ->
            val reach = 4.5.dp.toPx()
            val stroke = 1.dp.toPx()
            val color = if (isHovered) Color.White else fgColor
            drawLine(
                color = color,
                start = Offset(center.x - reach, center.y - reach),
                end = Offset(center.x + reach, center.y + reach),
                strokeWidth = stroke,
                cap = StrokeCap.Square,
            )
            drawLine(
                color = color,
                start = Offset(center.x + reach, center.y - reach),
                end = Offset(center.x - reach, center.y + reach),
                strokeWidth = stroke,
                cap = StrokeCap.Square,
            )
        }
    }
}

@Composable
private fun WindowsCaptionButton(
    label: String,
    isClose: Boolean = false,
    onClick: () -> Unit,
    glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bgColor = when {
        isClose && hovered -> Color(0xFFC42B1C) // Windows 11 signature close red
        hovered -> Color.White.copy(alpha = 0.09f)
        else -> Color.Transparent
    }

    Box(
        Modifier
            .fillMaxHeight()
            .width(46.dp)
            .background(bgColor)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Default)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .drawBehind { glyph(hovered) },
        contentAlignment = Alignment.Center,
    ) {}
}

@Composable
private fun MacCaptionButton(
    label: String,
    fill: Color,
    onClick: () -> Unit,
    inactiveFill: Color? = null,
    glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val buttonColor = if (inactiveFill != null && !hovered) inactiveFill else fill
    Box(
        Modifier
            .size(CAPTION_TOUCH_SIZE)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Default)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .drawBehind {
                drawCircle(buttonColor, radius = MAC_DOT_RADIUS.toPx())
                if (hovered) glyph(MAC_GLYPH)
            },
        contentAlignment = Alignment.Center,
    ) {}
}

private val CAPTION_TOUCH_SIZE = 22.dp
private val CAPTION_HEIGHT = 24.dp
private val MAC_DOT_RADIUS = 6.dp

private val GLYPH_HALF = 2.5.dp
private val HAIRLINE = 0.75.dp

private val MAC_CLOSE = Color(0xFFFF5F57)
private val MAC_MINIMIZE = Color(0xFFFFBD2E)
private val MAC_MAXIMIZE = Color(0xFF28C840)
private val MAC_GLYPH = Color(0xB3000000)

private fun Modifier.captionPress(scope: WindowScope, actions: DesktopWindowActions?): Modifier =
    pointerInput(scope, actions) {
        var lastPressAt = 0L
        var lastPressPosition = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown()
            val double = down.uptimeMillis - lastPressAt <= viewConfiguration.doubleTapTimeoutMillis &&
                (down.position - lastPressPosition).getDistance() <= viewConfiguration.touchSlop
            if (double) {
                lastPressAt = 0L
                down.consume()
                actions?.toggleMaximize()
                return@awaitEachGesture
            }
            lastPressAt = down.uptimeMillis
            lastPressPosition = down.position
            if (DesktopWindowsFrame.startDrag()) return@awaitEachGesture
            if (DesktopPlatform.isMac && DesktopMacFrame.startDrag()) return@awaitEachGesture
            val window = scope.window
            val windowAtStart = window.location
            val pointerAtStart = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
            drag(down.id) {
                val pointer = MouseInfo.getPointerInfo()?.location ?: return@drag
                window.setLocation(
                    windowAtStart.x + pointer.x - pointerAtStart.x,
                    windowAtStart.y + pointer.y - pointerAtStart.y,
                )
            }
        }
    }
