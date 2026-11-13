package com.music.bitchord.desktop

import androidx.compose.runtime.mutableStateOf
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What the island shows, and what it can be told to do.
 *
 * One object rather than a dozen loose flows, because three surfaces read the same state: the
 * capsule on screen, the menu that hangs off the toolbar, and the Settings page. Whatever one of
 * them changes, the other two see it.
 */
internal object DesktopIslandState {

    /** Delegated so the island, its menu and Settings all write through one state. */
    val isEnabled: MutableStateFlow<Boolean> get() = DesktopDynamicIslandSettings.enabled

    val currentSong = MutableStateFlow<Song?>(null)
    val isPlaying = MutableStateFlow(false)
    val lyricsLines = MutableStateFlow<List<LyricLine>?>(null)

    /**
     * The playhead, as Compose state rather than a flow.
     *
     * The engine publishes a position four times a second. Collected as a flow, every one of those
     * ticks recomposed the whole island — the artwork, the transport row, the animated lyric block —
     * to move a two-pixel hairline. As snapshot state it can be read inside `derivedStateOf` (so a
     * recomposition happens only when the *line* changes) and inside `drawBehind` (so the hairline
     * redraws without any composition at all).
     */
    val currentPositionMs = mutableStateOf(0L)
    val durationMs = mutableStateOf(0L)

    val repeatMode = MutableStateFlow(DesktopRepeatMode.OFF)

    /**
     * The queue as the app holds it, and the row it stands on — what the island menu's "Up next"
     * section reads. Fed from the app alongside the song, so the capsule can start any song the
     * queue holds (YouTube Music's own radio and playlists included) without the main window
     * being open.
     */
    val queueSongs = MutableStateFlow<List<Song>>(emptyList())
    val queueIndex = MutableStateFlow(0)

    // Player action callbacks, wired once by the app composable.
    var onPlayPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
    var onToggleRepeat: (() -> Unit)? = null
    var onOpenNowPlaying: (() -> Unit)? = null

    /**
     * Jumps the playhead to a fraction of the song, for the scrub row under the expanded capsule.
     *
     * Only fires on release: a drag previews the position locally and asks the engine to move once,
     * so scrubbing a four-minute song is one seek rather than hundreds.
     */
    var onSeek: ((Float) -> Unit)? = null

    /** Jumps the queue to a row's index — the same move the queue sheet's tap makes. */
    var onPlayQueueIndex: ((Int) -> Unit)? = null

    /** Opens Settings, where the island is configured. */
    var onOpenSettings: (() -> Unit)? = null

    /** Opens the lyrics column beside the page. */
    var onOpenLyrics: (() -> Unit)? = null

    fun toggle() = DesktopDynamicIslandSettings.toggle()

    fun setEnabled(enabled: Boolean) = DesktopDynamicIslandSettings.setEnabled(enabled)
}
