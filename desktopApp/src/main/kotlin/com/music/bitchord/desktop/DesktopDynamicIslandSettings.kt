package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Everything the Dynamic Island remembers between runs.
 *
 * One object rather than a dozen loose preference reads, because three places have to agree on
 * the same state: the island itself, the menu hanging off the toolbar, and the Settings page.
 *
 * There are four of them, and that is the point. The island used to carry a layout ladder, a
 * switch for each part of the capsule, a transparency slider, a rule about when to shrink, six
 * places to put it and a way to drag it elsewhere — knobs that all said the same thing, which is
 * "how much of this is showing, and where". The island is part of the screen now: it rests at the
 * top centre and opens when the pointer lands on it. What is left is only what a person
 * genuinely has an opinion about — whether it is on, how big it is, and whether clicks go
 * through it.
 */
internal object DesktopDynamicIslandSettings {

    private val persistence = DesktopPersistence()

    /**
     * Whether the floating capsule is drawn over the page.
     *
     * The top-bar island is gone, so a profile that had only that one switched on has its old
     * preference carried over to the capsule rather than ending up with no island at all.
     */
    val enabled: MutableStateFlow<Boolean> = MutableStateFlow(
        persistence.boolean(KEY_ENABLED, true) || persistence.boolean(KEY_LEGACY_TOP_BAR, false),
    )

    /** Stays out of the way while playback is paused. */
    val hideWhenPaused: MutableStateFlow<Boolean> =
        MutableStateFlow(persistence.boolean(KEY_HIDE_WHEN_PAUSED, false))

    /**
     * Lets mouse clicks fall through the floating capsule to the window underneath it.
     *
     * With it on, the capsule is a readout only — no menu, no transport — and it is switched back
     * from Settings or the toolbar's island button, both of which live in windows the capsule
     * does not cover.
     */
    val clickThrough: MutableStateFlow<Boolean> =
        MutableStateFlow(persistence.boolean(KEY_CLICK_THROUGH, false))

    /** The capsule's size, 0.8×–1.3×, so it can be read on a 4K panel or kept tidy on 1080p. */
    val scale: MutableStateFlow<Float> = MutableStateFlow(persistence.float(KEY_SCALE, 1f))

    fun toggle() = setEnabled(!enabled.value)

    fun setEnabled(value: Boolean) {
        persistence.saveBoolean(KEY_ENABLED, value)
        enabled.value = value
    }

    fun setHideWhenPaused(value: Boolean) = writeBoolean(KEY_HIDE_WHEN_PAUSED, value, hideWhenPaused)

    fun setClickThrough(value: Boolean) = writeBoolean(KEY_CLICK_THROUGH, value, clickThrough)

    fun setScale(value: Float) {
        val clamped = value.coerceIn(MIN_SCALE, MAX_SCALE)
        persistence.saveFloat(KEY_SCALE, clamped)
        scale.value = clamped
    }

    private fun writeBoolean(key: String, value: Boolean, into: MutableStateFlow<Boolean>) {
        persistence.saveBoolean(key, value)
        into.value = value
    }

    private const val KEY_ENABLED = "floating_lyrics_enabled"

    /** Written by the old top-bar capsule. Read once, as a migration, never written again. */
    private const val KEY_LEGACY_TOP_BAR = "island_top_bar"
    private const val KEY_HIDE_WHEN_PAUSED = "island_hide_when_paused"
    private const val KEY_CLICK_THROUGH = "island_click_through"
    private const val KEY_SCALE = "island_scale"

    const val MIN_SCALE = 0.80f
    const val MAX_SCALE = 1.30f
}
