import fs from 'node:fs';
const F = 'C:/Users/Daffa/Desktop/Folder Space AI/Folder Space Semester 7/Bit Chord/desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopApp.kt';
const t = fs.readFileSync(F, 'utf8');
const eol = t.includes('\r\n') ? '\r\n' : '\n';
let lines = t.split(/\r\n|\n/);
const laporan = [];

/** Ganti rentang baris (1-index, inklusif) — dipakai dari bawah ke atas supaya nomor tidak bergeser. */
const gantiRentang = (dari, sampai, pengganti, label) => {
  const awal = (lines[dari - 1] ?? '').trim().slice(0, 46);
  const akhir = (lines[sampai - 1] ?? '').trim().slice(0, 46);
  lines.splice(dari - 1, sampai - dari + 1, ...pengganti);
  laporan.push(`${label}: ${dari}-${sampai} ["${awal}" .. "${akhir}"]`);
};

// ---- dari bawah ke atas ----
gantiRentang(1683, 1698, [], 'blok supplier autoplay party');
gantiRentang(1659, 1667, [
'    LaunchedEffect(crossfadeOnSkip, crossfadeSeconds, audioQuality, selectedSong?.videoId, queue, liveQueue.index, shuffle, repeatMode) {',
'        playbackEngine.setCrossfadeSeconds(crossfadeSeconds)',
'        playbackEngine.setSkipMixEnabled(crossfadeOnSkip)',
], 'efek crossfade tanpa party');
gantiRentang(1627, 1632, [
'                onSeek = { positionMs ->',
'                    playbackEngine.seekTo(positionMs)',
'                },',
], 'seek jendela Windows');
gantiRentang(1605, 1610, [
'            onSeek = { positionMs ->',
'                playbackEngine.seekTo(positionMs)',
'            },',
], 'seek MPRIS');
gantiRentang(1502, 1577, [
'    fun togglePlayPauseFromUser() {',
'        playbackEngine.togglePlayPause()',
'    }',
'    fun playFromUser() {',
'        playbackEngine.play()',
'    }',
'    fun pauseFromUser() {',
'        playbackEngine.pause()',
'    }',
], 'partySync & kendali pengguna');
gantiRentang(1260, 1266, [], 'setAutoplay milik server party');
gantiRentang(1219, 1229, ['                } else {'], 'top-up autoplay party');
gantiRentang(1196, 1202, [
'                // The listener may have moved on while the station was being fetched; appending',
'                // then would attach it to the wrong seed.',
'                if (selectedSong?.videoId != current.videoId ||',
'                    repeatMode == DesktopRepeatMode.ALL,',
'                ) return@launch',
], 'penjagaan autoplay party');
gantiRentang(1180, 1189, [
'                // What the queue holds plus everything this session has already offered.',
'                val suggestions = DesktopAutoplay.tracksFor(',
'                    existing = if (dontRepeatSuggestions) {',
'                        liveQueue.songs + sessionSongHistory',
'                    } else {',
'                        liveQueue.songs',
'                    },',
'                    seedSong = current,',
'                    limit = remaining,',
'                ).getOrElse { failure ->',
], 'usulan tanpa queue party');
gantiRentang(1162, 1166, ['        val needed = MAX_QUEUED_AUTOPLAY - liveQueue.songs.drop(liveQueue.index + 1).count { it.fromAutoplay }'], 'hitung antrean party');
gantiRentang(1154, 1158, ['        if (!autoplay || repeatMode == DesktopRepeatMode.ALL) return'], 'gerbang loadAutoplaySongs');
gantiRentang(1002, 1002, [], 'radio diblokir host');
gantiRentang(993, 994, ['            // AutoPlay\'s section behind the seed, as the phone queues a station.'], 'komentar radio party');
gantiRentang(965, 976, [
'     * queue" at its end, above the album and AutoPlay either way.',
'     */',
'    fun enqueue(song: Song, next: Boolean) {',
], 'batas antrean party');
gantiRentang(963, 964, [
'    /**',
'     * Queues a track by hand, into the listener\'s own section — "Play next" at its head, "Add to',
], 'doc enqueue');
gantiRentang(887, 892, [], 'playAlbum jalur party');
gantiRentang(815, 820, [
'        // The phone\'s one-off queue: the song, then whatever the listener had queued by hand.',
'        val songs = QueueTimeline.buildOneOffQueue(liveQueue.songs, liveQueue.index, tapped, tapped.queueSource())',
], 'playSong jalur party');
gantiRentang(808, 808, [], 'sinyal party saat playCurrent');
gantiRentang(792, 795, [], 'saveQueue saat party');

fs.writeFileSync(F, lines.join(eol));
console.log(laporan.join('\n'));

// ---- pergantian teks untuk baris pendek yang unik ----
let isi = fs.readFileSync(F, 'utf8');
const teks = (dari, ke, label) => {
  if (!isi.includes(dari)) { console.log('GAGAL teks: ' + label); return; }
  isi = isi.split(dari).join(ke);
};
teks('DesktopAutoplay.enabled(partyState, autoplay)', 'autoplay', 'autoplayEnabled');
teks('DesktopAutoplay.enabled(DesktopListenTogether.state.value, autoplay)', 'autoplay', 'autoplay playNext');
teks('val latestParty = DesktopListenTogether.state.value\n', '', 'latestParty');
teks('if (latestParty.inParty) {\n', '', 'sisa cabang party');
teks('val party = DesktopListenTogether.state.value\n', '', 'party lokal');
teks('val queued = partyAutoplayWaiting(party, current.videoId)\n            ?: liveQueue', 'val queued = liveQueue', 'queued');
teks('if (party.inParty && liveQueue.upcoming.size', 'if (false && liveQueue.upcoming.size', 'batas');
fs.writeFileSync(F, isi);
console.log('sisa rujukan party:', isi.split(/\r\n|\n/).filter((l) => /party|Party|ListenTogether/i.test(l)).length);
