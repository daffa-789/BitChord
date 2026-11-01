/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard).
 *
 * Copyright (C) 2026 SFG545 (original Orchard implementation)
 * Copyright (C) 2026 Kushagra Singh (BitChord adaptation)
 *
 * Orchard's original source is licensed under the GNU Affero General Public
 * License, version 3 or later. Per AGPLv3 section 13, this file is combined
 * here into BitChord -- a work licensed under the GNU General Public
 * License, version 3 or later -- and remains itself governed by the AGPLv3
 * as part of that combination.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.music.bitchord.playback.smart

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * The minimal facts about a queue item the transition planner needs,
 * independent of Media3's `MediaItem` — kept separate so this file stays pure
 * and testable without constructing one.
 */
data class TransitionTrackInfo(
    val id: String,
    val durationMs: Long,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: String = "",
)

/** How the renderer should execute a planned transition. */
enum class TransitionStyle {
    /** A constant-power fade, unfiltered. */
    EQUAL_POWER,

    /** Album siblings played through: a near-instant handoff, not a mix. */
    GAPLESS,

    /** Beat-aligned blend with a bass swap. */
    DJ_BLEND,

    /** Filtered handoff. */
    DJ_FILTER,
}

/**
 * The gesture used inside a [TransitionStyle.DJ_FILTER].
 *
 * Kept because the renderer and its tests still describe these gestures, even
 * though the planner now only produces [TransitionStyle.EQUAL_POWER].
 */
enum class FilterTransitionVariant {
    /** Complementary high/low-pass sweep. */
    SWEEP,

    /** Lighter colour change with a low-end and fader handoff. */
    BASS_HANDOFF,

    /** Filter ride whose outgoing side trails away through a beat-synced echo. */
    ECHO_RIDE,
}

/**
 * The planned transition for one pair of tracks, in outgoing-track timeline
 * seconds.
 *
 * A plan is produced on every tick; [shouldStart] is what says the playhead
 * has actually reached it. When [blocked] is true nothing should happen at all
 * and [reason] says why.
 */
data class TransitionPlan(
    val shouldStart: Boolean = false,
    val markerVisible: Boolean = false,
    val blocked: Boolean = false,
    val reason: String = "",
    val transitionStart: Double = 0.0,
    val transitionEnd: Double = 0.0,
    val fadeSeconds: Double = 0.0,
    val transitionStyle: TransitionStyle = TransitionStyle.EQUAL_POWER,
    /** Where in the incoming track playback should be cued to when the transition opens. */
    val incomingCueTime: Double = 0.0,
    /** Where the incoming track's arrangement lands, on its own timeline. */
    val incomingHandoffTime: Double = 0.0,
    val incomingPlaybackRate: Double = 1.0,
    val outgoingPlaybackRate: Double = 1.0,
    val handoffStartSeconds: Double = 0.0,
    val handoffDuration: Double = 0.0,
    val pickupSeconds: Double = 0.0,
    val transitionBeats: Int = 0,
    val bassSwap: Boolean = false,
    val handoffFraction: Double = HANDOFF_FRACTION,
    val bedPosition: Double = BED_POSITION,
    val bassSwapFraction: Double = 0.7,
    val filterSweep: Double = 0.0,
    /** The stable per-pair gesture for [TransitionStyle.DJ_FILTER]. */
    val filterVariant: FilterTransitionVariant = FilterTransitionVariant.SWEEP,
    /** How strongly the two tracks are expected to be singing over each other, 0..1. */
    val vocalOverlap: Double = 0.0,
    val outgoingBpm: Double = 0.0,
    val incomingBpm: Double = 0.0,
    /** One beat of the outgoing grid in seconds, or 0 when that grid is not trusted. */
    val beatSeconds: Double = 0.0,
    val phaseLock: Boolean = false,
    /** Echo-out delay time in seconds, or 0 for no echo. */
    val echoSeconds: Double = 0.0,
    val policyReasons: List<String> = emptyList(),
) {
    /** Convenience for the engine, which schedules in milliseconds. */
    val fadeMs: Long get() = (fadeSeconds * 1000).roundToLong()
}

const val HANDOFF_FRACTION = 0.5
const val BED_POSITION = 0.5

private fun blocked(reason: String) = TransitionPlan(blocked = true, reason = reason)

private fun trackDurationSeconds(track: TransitionTrackInfo?): Double =
    if (track == null || track.durationMs <= 0) 0.0 else track.durationMs / 1000.0

private fun standardTransition(
    length: Double,
    playbackTime: Double,
    fadeSeconds: Double,
    minFadeSeconds: Double,
    reason: String = "standard",
): TransitionPlan {
    val fade = clamp(fadeSeconds, minFadeSeconds, 12.0)
    val transitionStart = max(0.0, length - fade)
    val started = playbackTime >= transitionStart
    return TransitionPlan(
        shouldStart = started,
        markerVisible = true,
        transitionStart = transitionStart,
        transitionEnd = length,
        fadeSeconds = fade,
        transitionStyle = TransitionStyle.EQUAL_POWER,
        reason = if (started) reason else "before-$reason-window",
    )
}

private fun clamp(value: Double, min: Double, max: Double): Double =
    if (value.isFinite()) max(min, min(max, value)) else min

/**
 * Plans the equal-power crossfade out of [currentTrack].
 *
 * Called on every playback tick; the returned plan describes the transition
 * whether or not it has started yet.
 *
 * @param currentTime the outgoing track's playhead, in seconds.
 */
fun planTransition(
    currentTrack: TransitionTrackInfo? = null,
    currentTime: Double = 0.0,
    duration: Double = 0.0,
    fadeSeconds: Double = 6.0,
    minFadeSeconds: Double = 1.0,
): TransitionPlan {
    val length = max(duration, trackDurationSeconds(currentTrack))
    val playbackTime = max(0.0, currentTime)
    if (length <= 0) return blocked("no-duration")
    return standardTransition(length, playbackTime, fadeSeconds, minFadeSeconds)
}
