package com.music.bitchord.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings for the Dynamic Island.
 *
 * Four rows, and that is all there is to say about it. The island used to fill a page of its own:
 * a live preview, a ladder of layouts, a switch for the cover, one for the transport, one for the
 * progress line, a rule about when to shrink, a transparency slider, six places to put the
 * capsule and a way to drag it somewhere else. Almost all of that was asking the same question
 * twice.
 *
 * The island is part of the screen now: it rests at the top centre and opens when the pointer
 * lands on it, so there is nothing to place and nothing to drag. What is left is what a person
 * genuinely has an opinion about: whether it is on, how big it is, and whether clicks go through
 * it.
 */
@Composable
internal fun DynamicIslandSettingsGroup() {
    val floating by DesktopDynamicIslandSettings.enabled.collectAsState()
    val scale by DesktopDynamicIslandSettings.scale.collectAsState()
    val hideWhenPaused by DesktopDynamicIslandSettings.hideWhenPaused.collectAsState()
    val clickThrough by DesktopDynamicIslandSettings.clickThrough.collectAsState()

    SettingsToggle(
        title = DesktopStrings["d_island_floating", "Floating island"],
        subtitle = DesktopStrings[
            "d_island_floating_subtitle",
            "A capsule that stays at the top of the screen while you work, and opens into the player when the pointer lands on it",
        ],
        checked = floating,
        onCheckedChange = DesktopDynamicIslandSettings::setEnabled,
    )
    if (!floating) return

    IslandSliderRow(
        title = DesktopStrings["d_island_size", "Island size"],
        value = "${(scale * 100).toInt()}%",
        slider = scale,
        range = DesktopDynamicIslandSettings.MIN_SCALE..DesktopDynamicIslandSettings.MAX_SCALE,
        onValueChange = DesktopDynamicIslandSettings::setScale,
    )

    SettingsToggle(
        title = DesktopStrings["d_island_hide_paused", "Hide while paused"],
        subtitle = DesktopStrings[
            "d_island_hide_paused_subtitle",
            "Takes the capsule off the screen whenever playback is paused",
        ],
        checked = hideWhenPaused,
        onCheckedChange = DesktopDynamicIslandSettings::setHideWhenPaused,
    )

    if (DesktopPlatform.isWindows) {
        SettingsToggle(
            title = DesktopStrings["d_island_click_through", "Click-through"],
            subtitle = DesktopStrings[
                "d_island_click_through_subtitle",
                "Clicks over the capsule go to the window underneath. Switch it off here to grab the capsule again.",
            ],
            checked = clickThrough,
            onCheckedChange = DesktopDynamicIslandSettings::setClickThrough,
        )
    }
}

/** A slider with its name on the left and its value on the right, as the crossfade row reads. */
@Composable
private fun IslandSliderRow(
    title: String,
    value: String,
    slider: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    if (!settingsRowVisible(title, value)) return
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(value, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
        }
        DesktopBareSlider(
            value = slider,
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}
