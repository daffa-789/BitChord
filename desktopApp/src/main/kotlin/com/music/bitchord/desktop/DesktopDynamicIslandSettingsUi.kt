package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song

/**
 * Settings for the Dynamic Island, in place of the handful of tick boxes it used to be.
 *
 * The live preview at the top is the whole point of putting these here: every chip, slider and
 * switch below redraws the capsule above it with the song that is playing, so a choice is seen
 * before it is lived with.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DynamicIslandSettingsGroup() {
    val floating by DesktopDynamicIslandSettings.enabled.collectAsState()
    val inTopBar by DesktopDynamicIslandSettings.inTopBar.collectAsState()
    val layout by DesktopDynamicIslandSettings.layout.collectAsState()
    val anchor by DesktopDynamicIslandSettings.anchor.collectAsState()
    val scale by DesktopDynamicIslandSettings.scale.collectAsState()
    val opacity by DesktopDynamicIslandSettings.opacity.collectAsState()
    val showCover by DesktopDynamicIslandSettings.showCover.collectAsState()
    val showControls by DesktopDynamicIslandSettings.showControls.collectAsState()
    val showProgress by DesktopDynamicIslandSettings.showProgress.collectAsState()
    val autoCollapse by DesktopDynamicIslandSettings.autoCollapse.collectAsState()
    val hideWhenPaused by DesktopDynamicIslandSettings.hideWhenPaused.collectAsState()
    val locked by DesktopDynamicIslandSettings.locked.collectAsState()

    SettingsToggle(
        title = DesktopStrings["d_island_floating", "Floating island"],
        subtitle = DesktopStrings["d_island_floating_subtitle", "A capsule over the page with the cover, the controls and the lyric being sung"],
        checked = floating,
        onCheckedChange = DesktopDynamicIslandSettings::setEnabled,
    )
    SettingsToggle(
        title = DesktopStrings["d_island_top_bar", "Island in the top bar"],
        subtitle = DesktopStrings["d_island_top_bar_subtitle", "The slim island that shares the toolbar with the transport controls"],
        checked = inTopBar,
        onCheckedChange = DesktopDynamicIslandSettings::setInTopBar,
    )

    if (floating || inTopBar) {
        val previewTitle = DesktopStrings["d_island_preview", "Preview"]
        val previewSubtitle = DesktopStrings["d_island_preview_subtitle", "How the island looks right now"]
        if (settingsRowVisible(previewTitle, previewSubtitle)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(previewTitle, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                DynamicIslandPreview()
            }
        }
    }

    if (floating || inTopBar) {
        val layoutTitle = DesktopStrings["d_island_layout_title", "Island layout"]
        val layoutSubtitle = layout.detail
        if (settingsRowVisible(layoutTitle, layoutSubtitle)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(layoutTitle, fontWeight = FontWeight.Medium)
                Text(layoutSubtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                ChipRow(IslandLayout.entries, layout, { it.label }) { option ->
                    DesktopDynamicIslandSettings.setLayout(option)
                }
            }
        }
    }

    if (floating) {
        val positionTitle = DesktopStrings["d_island_position", "Island position"]
        val positionSubtitle = DesktopStrings["d_island_position_subtitle", "Where the capsule rests. It can still be dragged anywhere in the window."]
        if (settingsRowVisible(positionTitle, positionSubtitle)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(positionTitle, fontWeight = FontWeight.Medium)
                Text(positionSubtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IslandAnchor.entries.forEach { option ->
                        FilterChip(
                            colors = desktopChipColors(),
                            selected = anchor == option,
                            onClick = { DesktopDynamicIslandSettings.setAnchor(option) },
                            label = { Text(option.label) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (locked) {
                        DesktopStrings["d_island_locked_on", "Locked: on — the capsule stays put"]
                    } else {
                        DesktopStrings["d_island_locked_off", "Locked: off — drag it by its body"]
                    },
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        IslandSliderRow(
            title = DesktopStrings["d_island_size", "Island size"],
            value = "${(scale * 100).toInt()}%",
            slider = scale,
            range = DesktopDynamicIslandSettings.MIN_SCALE..DesktopDynamicIslandSettings.MAX_SCALE,
            onValueChange = DesktopDynamicIslandSettings::setScale,
        )
        IslandSliderRow(
            title = DesktopStrings["d_island_opacity", "Island opacity"],
            value = "${(opacity * 100).toInt()}%",
            slider = opacity,
            range = DesktopDynamicIslandSettings.MIN_OPACITY..1f,
            onValueChange = DesktopDynamicIslandSettings::setOpacity,
        )
    }


    SettingsToggle(
        title = DesktopStrings["d_island_cover", "Cover art"],
        subtitle = DesktopStrings["d_island_cover_subtitle", "Shows the album cover beside the song"],
        checked = showCover,
        onCheckedChange = DesktopDynamicIslandSettings::setShowCover,
    )
    SettingsToggle(
        title = DesktopStrings["d_island_controls", "Transport controls"],
        subtitle = DesktopStrings["d_island_controls_subtitle", "Previous, play/pause, next and repeat inside the capsule"],
        checked = showControls,
        onCheckedChange = DesktopDynamicIslandSettings::setShowControls,
    )
    SettingsToggle(
        title = DesktopStrings["d_island_progress", "Progress line"],
        subtitle = DesktopStrings["d_island_progress_subtitle", "The hairline along the capsule's bottom edge"],
        checked = showProgress,
        onCheckedChange = DesktopDynamicIslandSettings::setShowProgress,
    )
    if (floating) {
        SettingsToggle(
            title = DesktopStrings["d_island_collapse", "Collapse when idle"],
            subtitle = DesktopStrings["d_island_collapse_subtitle", "Shrinks to the Mini pill while the pointer is away, and grows back when it returns"],
            checked = autoCollapse,
            onCheckedChange = DesktopDynamicIslandSettings::setAutoCollapse,
        )
        SettingsToggle(
            title = DesktopStrings["d_island_hide_paused", "Hide while paused"],
            subtitle = DesktopStrings["d_island_hide_paused_subtitle", "Takes the capsule off the page whenever playback is paused"],
            checked = hideWhenPaused,
            onCheckedChange = DesktopDynamicIslandSettings::setHideWhenPaused,
        )
        SettingsToggle(
            title = DesktopStrings["d_island_lock", "Lock position"],
            subtitle = DesktopStrings["d_island_lock_subtitle", "Stops a stray drag from moving the capsule"],
            checked = locked,
            onCheckedChange = DesktopDynamicIslandSettings::setLocked,
        )
        SettingsNavigationRow(
            title = DesktopStrings["d_island_reset_position_title", "Reset island position"],
            subtitle = DesktopStrings["d_island_reset_position_subtitle", "Puts the capsule back at its chosen corner"],
            onClick = { DesktopDynamicIslandSettings.resetOffset() },
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

/** The chip row the rest of Settings uses, for any set of options. */
@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                colors = desktopChipColors(),
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

/**
 * The capsule, drawn over a stand-in backdrop with whatever is playing.
 *
 * Sized down to fit the Settings page rather than to the user's chosen scale: it is here to show
 * the arrangement and the state, and the real capsule is a click away on the page behind.
 */
@Composable
private fun DynamicIslandPreview() {
    val song by DesktopFloatingLyricsManager.currentSong.collectAsState()
    val lines by DesktopFloatingLyricsManager.lyricsLines.collectAsState()
    val isPlaying by DesktopFloatingLyricsManager.isPlaying.collectAsState()
    val repeatMode by DesktopFloatingLyricsManager.repeatMode.collectAsState()
    val layout by DesktopDynamicIslandSettings.layout.collectAsState()
    val scale by DesktopDynamicIslandSettings.scale.collectAsState()
    val opacity by DesktopDynamicIslandSettings.opacity.collectAsState()

    val hasSong = song != null
    val previewSong = song ?: PREVIEW_SONG
    val previewLines = if (hasSong) lines else PREVIEW_LYRICS
    val previewPosition = { if (hasSong) DesktopFloatingLyricsManager.currentPositionMs.value else PREVIEW_POSITION_MS }
    val previewProgress = {
        when {
            hasSong && DesktopFloatingLyricsManager.durationMs.value > 0L ->
                (DesktopFloatingLyricsManager.currentPositionMs.value.toFloat() /
                    DesktopFloatingLyricsManager.durationMs.value).coerceIn(0f, 1f)
            hasSong -> 0f
            else -> 0.38f
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF26262E),
                        Color(0xFF141419),
                        Color(0xFF0B0B0E),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        DynamicIslandCard(
            song = previewSong,
            lines = previewLines,
            positionMs = previewPosition,
            progress = previewProgress,
            isPlaying = if (hasSong) isPlaying else true,
            repeatMode = repeatMode,
            layout = layout,
            // Held to the width the Settings page has, whatever size the real one is.
            scale = (scale * 0.70f).coerceIn(0.6f, 0.92f),
            opacity = opacity,
            hovered = false,
            interactive = false,
        )
    }
}

/** What the preview shows before anything has been played, in the app's own language. */
private val PREVIEW_SONG: Song
    get() = Song(
        videoId = "preview",
        title = DesktopStrings["d_island_preview_title", "Nothing playing yet"],
        artist = DesktopStrings["d_island_preview_artist", "Play something and this preview follows it"],
        thumbnailUrl = null,
    )

private val PREVIEW_LYRICS = listOf(
    LyricLine(timeMs = 0L, text = "Every window keeps its own light"),
    LyricLine(timeMs = 4_000L, text = "and the night keeps time"),
    LyricLine(timeMs = 8_000L, text = "with the songs we left behind"),
)

private const val PREVIEW_POSITION_MS = 5_600L
