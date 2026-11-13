package com.music.bitchord.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.icons.BitChordIcons

/**
 * Everything the island can be told to do, in one list.
 *
 * The same menu hangs off the island (right-click) and off the toolbar button, because a setting
 * you have to hunt for is one nobody uses. Settings itself is the last entry, for the things that
 * need a slider rather than a tick.
 */
@Composable
internal fun DynamicIslandMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    val enabled by DesktopDynamicIslandSettings.enabled.collectAsState()
    val clickThrough by DesktopDynamicIslandSettings.clickThrough.collectAsState()
    val song by DesktopIslandState.currentSong.collectAsState()
    val isPlaying by DesktopIslandState.isPlaying.collectAsState()
    val queueSongs by DesktopIslandState.queueSongs.collectAsState()
    val queueIndex by DesktopIslandState.queueIndex.collectAsState()

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
                DesktopIslandState.onPlayPause?.invoke()
                onDismissRequest()
            },
        )
        MenuAction(DesktopStrings["widget_next", "Next"], Icons.Rounded.FastForward) {
            DesktopIslandState.onNext?.invoke()
            onDismissRequest()
        }
        MenuAction(DesktopStrings["widget_previous", "Previous"], Icons.Rounded.FastRewind) {
            DesktopIslandState.onPrevious?.invoke()
            onDismissRequest()
        }

        // The queue's tail, one row per song: tapping one starts it, exactly as tapping the row in
        // the queue sheet does. Kept to a handful so a radio queue of fifty does not become a
        // menu of fifty — the rest are one "Open BitChord" away.
        val upcoming = queueSongs.drop(queueIndex + 1)
        if (upcoming.isNotEmpty()) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            MenuCaption(DesktopStrings["d_island_up_next", "Up next"])
            upcoming.take(UP_NEXT_SHOWN).forEachIndexed { at, up ->
                MenuAction(
                    label = "${up.title} — ${up.artist}",
                    icon = BitChordIcons.MusicNote,
                    onClick = {
                        DesktopIslandState.onPlayQueueIndex?.invoke(queueIndex + 1 + at)
                        onDismissRequest()
                    },
                )
            }
            if (upcoming.size > UP_NEXT_SHOWN) {
                MenuCaption(DesktopStrings["d_island_up_next_more", "…and more in the queue"])
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        MenuCheck(DesktopStrings["d_island_floating", "Floating island"], enabled) {
            DesktopDynamicIslandSettings.setEnabled(it)
        }
        if (DesktopPlatform.isWindows) {
            MenuCheck(DesktopStrings["d_island_click_through", "Click-through"], clickThrough) {
                DesktopDynamicIslandSettings.setClickThrough(it)
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        MenuAction(DesktopStrings["d_island_open_app", "Open BitChord…"], BitChordIcons.MusicNote) {
            onDismissRequest()
            // "Hide BitChord" put the window away; this is the way back, landing on the player.
            DesktopWindowVisibility.show()
            DesktopIslandState.onOpenNowPlaying?.invoke()
        }
        MenuAction(DesktopStrings["d_island_hide_app", "Hide BitChord"], Icons.Rounded.VisibilityOff) {
            onDismissRequest()
            // The island is its own window, so this puts the main window away without taking the
            // capsule with it — the app is out of sight, the island is not.
            DesktopWindowVisibility.hide()
        }
        MenuAction(DesktopStrings["d_island_settings", "Island settings…"], Icons.Rounded.Settings) {
            onDismissRequest()
            DesktopIslandState.onOpenSettings?.invoke()
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
    val enabled by DesktopDynamicIslandSettings.enabled.collectAsState()

    Box {
        DesktopToolbarButton(onClick = { open = true }, size = 32.dp) {
            Icon(
                BitChordIcons.MusicNote,
                "Dynamic Island",
                tint = if (enabled) Color.White else DesktopSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        DynamicIslandMenu(expanded = open, onDismissRequest = { open = false })
    }
}

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

/** How many "Up next" rows the island menu lists before pointing at the app for the rest. */
private const val UP_NEXT_SHOWN = 6

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
        text = text,
        color = DesktopSecondary.copy(alpha = 0.75f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun MenuAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
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
                Icon(
                    Icons.Rounded.Check,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
        },
        onClick = { onCheckedChange(!checked) },
    )
}
