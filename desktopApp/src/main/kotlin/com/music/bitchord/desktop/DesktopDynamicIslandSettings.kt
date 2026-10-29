package com.music.bitchord.desktop

import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * How much of the island is drawn.
 *
 * The three rungs are the same capsule at three densities, so switching between them never moves
 * the island's controls somewhere the eye has to find them again — only how much of the song is
 * shown beside them.
 */
internal enum class IslandLayout(
    private val labelKey: String,
    private val labelFallback: String,
    private val detailKey: String,
    private val detailFallback: String,
) {
    FULL(
        "d_island_layout_full",
        "Full",
        "d_island_layout_full_subtitle",
        "Cover, transport controls and three rolling lyric lines",
    ),
    COMPACT(
        "d_island_layout_compact",
        "Compact",
        "d_island_layout_compact_subtitle",
        "The line being sung, a cover and play/pause",
    ),
    MINI(
        "d_island_layout_mini",
        "Mini",
        "d_island_layout_mini_subtitle",
        "Artwork and the current line, nothing else",
    ),
    ;

    /** Read at draw time, so switching the app's language renames the island without a restart. */
    val label: String get() = DesktopStrings[labelKey, labelFallback]

    val detail: String get() = DesktopStrings[detailKey, detailFallback]
}

/**
 * Where the floating capsule rests, measured from the window's own edges.
 *
 * The anchor is the resting place, not a cage: the capsule can still be dragged anywhere inside the
 * window, and the chosen anchor is where it returns to when the drag is reset.
 */
internal enum class IslandAnchor(
    private val labelKey: String,
    private val labelFallback: String,
    val alignment: Alignment,
) {
    TOP_CENTER("d_island_top_center", "Top center", Alignment.TopCenter),
    TOP_LEFT("d_island_top_left", "Top left", Alignment.TopStart),
    TOP_RIGHT("d_island_top_right", "Top right", Alignment.TopEnd),
    BOTTOM_CENTER("d_island_bottom_center", "Bottom center", Alignment.BottomCenter),
    BOTTOM_LEFT("d_island_bottom_left", "Bottom left", Alignment.BottomStart),
    BOTTOM_RIGHT("d_island_bottom_right", "Bottom right", Alignment.BottomEnd),
    ;

    val label: String get() = DesktopStrings[labelKey, labelFallback]

    /** True for the three that hang off the bottom edge. */
    val isBottom: Boolean get() = this == BOTTOM_CENTER || this == BOTTOM_LEFT || this == BOTTOM_RIGHT
}

/**
 * Everything the Dynamic Island remembers between runs.
 *
 * One object rather than a dozen loose preference reads, because three places have to agree on the
 * same state: the island itself, the menu hanging off the toolbar, and the Settings page that
 * explains what the other two do.
 */
internal object DesktopDynamicIslandSettings {

    private val persistence = DesktopPersistence()

    /** Whether the floating capsule is drawn over the page. */
    val enabled: MutableStateFlow<Boolean> = MutableStateFlow(persistence.boolean(KEY_ENABLED, true))

    /**
     * Whether the slim island sits in the top bar beside the transport controls.
     *
     * Unset, it goes the other way from the floating capsule: a fresh profile gets the floating
     * island alone, because two capsules stacked one above the other read as a bug rather than as a
     * feature — and somebody who has already closed the floating one gets the top bar's instead of
     * nothing at all. Either can be switched from the island's own menu, and both are remembered.
     */
    val inTopBar: MutableStateFlow<Boolean> = MutableStateFlow(
        persistence.boolean(KEY_TOP_BAR, !persistence.boolean(KEY_ENABLED, true)),
    )

    val layout: MutableStateFlow<IslandLayout> =
        MutableStateFlow(storedEnum(KEY_LAYOUT, IslandLayout.FULL))

    val anchor: MutableStateFlow<IslandAnchor> =
        MutableStateFlow(storedEnum(KEY_ANCHOR, IslandAnchor.TOP_CENTER))

    val showCover: MutableStateFlow<Boolean> = MutableStateFlow(persistence.boolean(KEY_COVER, true))
    val showControls: MutableStateFlow<Boolean> = MutableStateFlow(persistence.boolean(KEY_CONTROLS, true))
    val showProgress: MutableStateFlow<Boolean> = MutableStateFlow(persistence.boolean(KEY_PROGRESS, true))

    /** Shrinks to the Mini pill while the pointer is away, and grows back when it returns. */
    val autoCollapse: MutableStateFlow<Boolean> = MutableStateFlow(persistence.boolean(KEY_AUTO_COLLAPSE, false))

    /** Stays out of the way while playback is paused. */
    val hideWhenPaused: MutableStateFlow<Boolean> =
        MutableStateFlow(persistence.boolean(KEY_HIDE_WHEN_PAUSED, false))

    /** Pins the capsule where it is, so a stray drag cannot move it. */
    val locked: MutableStateFlow<Boolean> = MutableStateFlow(persistence.boolean(KEY_LOCKED, false))

    /** How opaque the capsule's glass is: 0.35 lets the page through, 1 is solid. */
    val opacity: MutableStateFlow<Float> = MutableStateFlow(persistence.float(KEY_OPACITY, 0.94f))

    /** The capsule's size, 0.8×–1.3×, so it can be read on a 4K panel or kept tidy on 1080p. */
    val scale: MutableStateFlow<Float> = MutableStateFlow(persistence.float(KEY_SCALE, 1f))

    /** Where the capsule was dragged to, as a offset in dp from the place its anchor puts it. */
    val offsetX: MutableStateFlow<Float> = MutableStateFlow(persistence.float(KEY_OFFSET_X, 0f))
    val offsetY: MutableStateFlow<Float> = MutableStateFlow(persistence.float(KEY_OFFSET_Y, 0f))

    /** True while either island is on, which is what the toolbar button reads. */
    val anyVisible: StateFlow<Boolean>
        get() = _anyVisible

    private val _anyVisible = MutableStateFlow(enabled.value || inTopBar.value)

    /** Kept in step by every setter below rather than by a coroutine nobody owns. */
    private fun refreshVisible() {
        _anyVisible.value = enabled.value || inTopBar.value
    }

    fun toggle() = setEnabled(!enabled.value)

    fun setEnabled(value: Boolean) {
        persistence.saveBoolean(KEY_ENABLED, value)
        enabled.value = value
        refreshVisible()
    }

    fun setInTopBar(value: Boolean) {
        persistence.saveBoolean(KEY_TOP_BAR, value)
        inTopBar.value = value
        refreshVisible()
    }

    fun setLayout(value: IslandLayout) {
        persistence.saveString(KEY_LAYOUT, value.name)
        layout.value = value
    }

    fun setAnchor(value: IslandAnchor) {
        persistence.saveString(KEY_ANCHOR, value.name)
        anchor.value = value
        // A capsule dragged to the far corner should not stay there when the corner changes.
        resetOffset()
    }

    fun setShowCover(value: Boolean) = writeBoolean(KEY_COVER, value, showCover)

    fun setShowControls(value: Boolean) = writeBoolean(KEY_CONTROLS, value, showControls)

    fun setShowProgress(value: Boolean) = writeBoolean(KEY_PROGRESS, value, showProgress)

    fun setAutoCollapse(value: Boolean) = writeBoolean(KEY_AUTO_COLLAPSE, value, autoCollapse)

    fun setHideWhenPaused(value: Boolean) = writeBoolean(KEY_HIDE_WHEN_PAUSED, value, hideWhenPaused)

    fun setLocked(value: Boolean) {
        persistence.saveBoolean(KEY_LOCKED, value)
        locked.value = value
    }

    fun setOpacity(value: Float) {
        val clamped = value.coerceIn(MIN_OPACITY, 1f)
        persistence.saveFloat(KEY_OPACITY, clamped)
        opacity.value = clamped
    }

    fun setScale(value: Float) {
        val clamped = value.coerceIn(MIN_SCALE, MAX_SCALE)
        persistence.saveFloat(KEY_SCALE, clamped)
        scale.value = clamped
    }

    /** Where the capsule is now, in dp from its anchor. Called once a drag settles. */
    fun saveOffset(x: Float, y: Float) {
        persistence.saveFloat(KEY_OFFSET_X, x)
        persistence.saveFloat(KEY_OFFSET_Y, y)
        offsetX.value = x
        offsetY.value = y
    }

    fun resetOffset() = saveOffset(0f, 0f)

    private fun writeBoolean(key: String, value: Boolean, into: MutableStateFlow<Boolean>) {
        persistence.saveBoolean(key, value)
        into.value = value
    }

    private inline fun <reified T : Enum<T>> storedEnum(key: String, fallback: T): T {
        val stored = persistence.string(key)
        if (stored.isBlank()) return fallback
        return runCatching { enumValueOf<T>(stored) }.getOrDefault(fallback)
    }

    private const val KEY_ENABLED = "floating_lyrics_enabled"
    private const val KEY_TOP_BAR = "island_top_bar"
    private const val KEY_LAYOUT = "island_layout"
    private const val KEY_ANCHOR = "island_anchor"
    private const val KEY_COVER = "island_cover"
    private const val KEY_CONTROLS = "island_controls"
    private const val KEY_PROGRESS = "island_progress"
    private const val KEY_AUTO_COLLAPSE = "island_auto_collapse"
    private const val KEY_HIDE_WHEN_PAUSED = "island_hide_when_paused"
    private const val KEY_LOCKED = "island_locked"
    private const val KEY_OPACITY = "island_opacity"
    private const val KEY_SCALE = "island_scale"
    private const val KEY_OFFSET_X = "island_offset_x"
    private const val KEY_OFFSET_Y = "island_offset_y"

    const val MIN_OPACITY = 0.35f
    const val MIN_SCALE = 0.80f
    const val MAX_SCALE = 1.30f
}
