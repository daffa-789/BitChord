import fs from 'node:fs';
const ROOT = 'C:/Users/Daffa/Desktop/Folder Space AI/Folder Space Semester 7/Bit Chord/';
const U = ROOT + 'desktopUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/';
const SH = ROOT + 'shared/src/';
const gagal = [];

const baca = (f) => {
  const t = fs.readFileSync(f, 'utf8');
  return { lines: t.split(/\r\n|\n/), eol: t.includes('\r\n') ? '\r\n' : '\n', f };
};
const terap = (file, from, to, label) => {
  let t;
  try { t = fs.readFileSync(file, 'utf8'); } catch { gagal.push(label + ': file hilang'); return; }
  const crlf = t.includes('\r\n');
  const f = crlf ? from.replaceAll('\n', '\r\n') : from;
  const r = crlf ? to.replaceAll('\n', '\r\n') : to;
  if (!t.includes(f)) { gagal.push(label + ': ' + file.split('/').pop()); return; }
  fs.writeFileSync(file, t.replace(f, r));
};
/** Potong satu deklarasi tingkat-berkas (indentasi given) beserta komentar dokumentasinya. */
const potongDeklarasi = (file, declPrefix, closeLine, label) => {
  const b = baca(file);
  const i = b.lines.findIndex((l) => l.startsWith(declPrefix));
  if (i < 0) { gagal.push(label + ': awal'); return; }
  let s = i;
  for (;;) {
    const p = (b.lines[s - 1] ?? '').trimEnd();
    if (p.endsWith(' */') || p.startsWith(' * ') || p === '@Composable' || p === 'private' || p.startsWith('@')) s--;
    else break;
  }
  let e = i;
  while (e < b.lines.length && b.lines[e] !== closeLine) e++;
  if (e >= b.lines.length) { gagal.push(label + ': akhir'); return; }
  let stop = e + 1;
  if ((b.lines[stop] ?? '').trim() === '') stop++;
  b.lines.splice(s, stop - s);
  fs.writeFileSync(file, b.lines.join(b.eol));
};

// ============================ PlayerHost.kt ============================
const PH = U + 'PlayerHost.kt';
terap(PH, 'import com.music.bitchord.data.listentogether.PartyMember\n', '', 'PH1 impor');
terap(PH,
['    /** The Listen Together party this device is in, as the player draws it. */',
'    val party: StateFlow<PartyUi>'].join('\n'), '', 'PH2 anggota party');
potongDeklarasi(PH, 'data class PartyUi(', '}', 'PH3 kelas PartyUi');

// ============================ PlayerControls.kt ============================
const PC = U + 'PlayerControls.kt';
terap(PC, 'import com.music.bitchord.data.listentogether.PartyMember\n', '', 'PC1 impor');

terap(PC,
['    onOpenOutput: () -> Unit,',
'    onListenTogether: () -> Unit,',
'    /** Opens [ListenTogetherMembersSheet] rather than settings directly. */',
'    onOpenListenTogetherMembers: () -> Unit,'].join('\n'),
'    onOpenOutput: () -> Unit,', 'PC2 param PlayerActionRow');

terap(PC,
['            OutputPartyPill(',
'                onOutput = onOpenOutput,',
'                onParty = onListenTogether,',
'                onOpenMembers = onOpenListenTogetherMembers,',
'            )'].join('\n'),
'            OutputPill(onOutput = onOpenOutput)', 'PC3 pil di overflow');

terap(PC,
['                    OutputPartyPill(',
'                        onOutput = onOpenOutput,',
'                        onParty = onListenTogether,',
'                        onOpenMembers = onOpenListenTogetherMembers,',
'                    )'].join('\n'),
'                    OutputPill(onOutput = onOpenOutput)', 'PC4 pil biasa');

terap(PC,
['/**',
' * The two ends of "where is this playing": the output capsule.',
' *',
' * Both halves answer the same question and so belong to one control rather than',
' * two glyphs that happen to sit side by side — headphones for which speaker the',
' * sound leaves by, the party for which *people* it reaches.',
' *',
' * Video vs audio-only used to live here as a third segment; it now lives in',
' * the player\'s own three-dot menu, beside Revert to original and Upgrade',
' * quality — the same kind of choice, offered the same way. This capsule is',
' * back to the two icons it always otherwise had, at their original spacing.',
' */',
'@Composable',
'private fun OutputPartyPill(',
'    onOutput: () -> Unit,',
'    onParty: () -> Unit,',
'    /** Who\'s in it, before the settings page — see [ListenTogetherMembersSheet]. */',
'    onOpenMembers: () -> Unit,',
') {',
'    val badge = rememberPartyBadge()',
'    Pill {',
'        PillSegment(',
'            icon = Icons.Rounded.Headphones,',
'            iconSize = PILL_HEADPHONES_SIZE,',
'            contentDescription = stringResource(Res.string.audio_output),',
'            onClick = onOutput,',
'        )',
'        PillDivider()',
'        PillSegment(',
'            // Person rather than Groups: the three-person glyph is drawn half',
'            // the height of Headphones and wider than the segment holding it,',
'            // so the two halves of the capsule never looked like a pair.',
'            icon = Icons.Rounded.Person,',
'            iconSize = PILL_PARTY_SIZE,',
'            // The count is here and nowhere else: spoken, it is the whole',
'            // point of the control; drawn, it would cost the capsule its',
'            // symmetry for something the caption below already implies.',
'            contentDescription = if (badge.inParty) {',
'                stringResource(Res.string.listen_together_open_count, badge.members)',
'            } else {',
'                stringResource(Res.string.listen_together_open)',
'            },',
'            // Already in a party, this opens who\'s in it rather than the',
'            // settings page directly; there is nothing to create or join once',
'            // there is a party, so [onParty] only ever fires beforehand.',
'            onClick = if (badge.inParty) onOpenMembers else onParty,',
'            highlighted = badge.inParty,',
'            trailingLabel = badge.members.takeIf { badge.inParty }?.toString(),',
'        )',
'    }',
'}'].join('\n'),
['/**',
' * "Where is this playing": the output capsule, one segment wide.',
' *',
' * Video vs audio-only used to live here as a third segment; it now lives in',
' * the player\'s own three-dot menu, beside Revert to original and Upgrade',
' * quality — the same kind of choice, offered the same way.',
' */',
'@Composable',
'private fun OutputPill(onOutput: () -> Unit) {',
'    Pill {',
'        PillSegment(',
'            icon = Icons.Rounded.Headphones,',
'            iconSize = PILL_HEADPHONES_SIZE,',
'            contentDescription = stringResource(Res.string.audio_output),',
'            onClick = onOutput,',
'        )',
'    }',
'}'].join('\n'), 'PC5 pil output saja');

terap(PC, '    /** Per-segment override — see [OutputPartyPill]\'s three-up capsule. */', '    /** Per-segment override — see [OutputPill]. */', 'PC6 rujukan doc');

console.log(gagal.length ? 'BAGIAN GAGAL:\n' + gagal.join('\n') : 'PlayerControls/PlayerHost beres');
