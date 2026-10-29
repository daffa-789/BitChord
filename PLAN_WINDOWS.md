# Blueprint & Rencana Eksekusi: BitChord Windows Edition & Floating Desktop Lyrics

Dokumen ini adalah rencana acuan utama (*single source of truth*) untuk membangun, menyesuaikan, dan memodernisasi klien musik BitChord khusus untuk sistem operasi **Windows 11**. Fokus utama proyek ini adalah mengembalikan versi desktop, membuang elemen antarmuka macOS, mengintegrasikan kontrol jendela native Windows 11, serta menambahkan fitur **Floating Desktop Lyrics (Lirik Mengambang / Pop-up)** yang ramah produktivitas (*multitasking-friendly*).

---

## 1. Analisis Masalah & Kebutuhan Utama

Berdasarkan pengujian pada versi desktop BitChord saat ini:
1. **Antarmuka Bergaya macOS (Kurang Cocok di Windows):**
   - Jendela aplikasi masih menggambar tombol kontrol jendela *traffic light* khas Mac (merah, kuning, hijau) di pojok kiri atas via `MacCaptionButton`.
   - Tidak ada integrasi tombol kontrol Windows 11 (Minimize, Maximize/Restore, Close) di pojok kanan atas.
2. **Lirik Fullscreen Takeover (Menghambat Kerja):**
   - Tampilan *Now Playing* saat ini mengambil alih seluruh layar monitor. Ketika pengguna sedang bekerja (koding di Antigravity IDE / VS Code, desain di Blender, browsing, menulis dokumen), layar penuh tertutup sehingga pengguna tidak bisa menikmati lirik sambil beraktivitas.
3. **Solusi yang Dibangun:**
   - **Floating Desktop Lyrics Window:** Jendela kedua yang independen, melayang di atas semua aplikasi (*Always-on-Top*), transparan, dengan animasi kemunculan lirik dinamis.
   - **Mini-Player Mode:** Mode ringkas di pojok layar dengan navigasi dasar lagu.
   - **System Tray:** Aplikasi bisa diminimize ke area notifikasi taskbar Windows agar tidak memenuhi taskbar kerja.

---

## 2. Keputusan Arsitektur & Lingkungan Pengembangan (Development Stack)

### A. Runtime & Toolchain: Mengapa Wajib JDK 21?
* **Java Development Kit (JDK 21):**
  - Modul desktop BitChord (`desktopApp/build.gradle.kts`) secara eksplisit mengunci target toolchain ke Java 21:
    ```kotlin
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }
    java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
    ```
  - **Mengapa bukan JDK 17?** Gradle akan menolak kompilasi jika JDK yang terpasang hanya versi 17 karena requirement toolchain versi 21. Sebaliknya, JDK 21 sepenuhnya *backward compatible* (bisa mengompilasi modul shared/Android bertarget Java 17).
  - **Paket Resmi:** `EclipseAdoptium.Temurin.21.JDK` via Windows Package Manager (`winget`).

### B. IDE & Editor: Antigravity IDE (Tidak Perlu Android Studio)
* **Antigravity IDE** (berbasis VS Code) adalah lingkungan kerja utama:
  - **Antigravity Tab:** Menyediakan AI autocomplete cerdas, prediksi import otomatis, dan supercomplete di file Kotlin/Compose.
  - **Inline Refactor (<kbd>Ctrl</kbd> + <kbd>I</kbd>):** Generator dan modifikator kode di baris aktif.
  - **Peek Definition (<kbd>Alt</kbd> + <kbd>F12</kbd>):** Melihat implementasi fungsi dan snippet tanpa pindah tab.
* **Tanpa Android Studio:** Modul desktop berjalan langsung di atas JVM Windows (`./gradlew :desktopApp:run`). Android SDK tidak diperlukan, sehingga menghemat ruang disk dan RAM 4–8 GB.

---

## 3. Penanganan Autentikasi YouTube Music di Desktop

BitChord di desktop telah memiliki dua mesin penangkap autentikasi yang sangat andal di dalam kode sumbernya:

```
[Pengguna di Windows]
        |
        +---> Opsi A: In-App WebView (DesktopWebSignIn.kt)
        |     - Membuka dialog login accounts.google.com
        |     - Otomatis menangkap cookie SAPISID saat diarahkan ke music.youtube.com
        |
        +---> Opsi B: Browser Cookie Sync (DesktopBrowserCookies.kt)
              - Mendeteksi profil Google Chrome, Edge, Brave, dan Firefox di Windows
              - Membaca database SQLite cookies lokal (%LOCALAPPDATA% / DPAPI Windows)
              - Pengguna langsung login otomatis tanpa perlu mengetik ulang kata sandi!
```

---

## 4. Arsitektur Jendela Mengambang (Floating Desktop Lyrics)

### A. Struktur Multi-Window Compose Desktop
Compose Multiplatform mendukung banyak jendela berjalan serentak dalam satu lifecycle aplikasi:

```kotlin
// Jendela Kedua Khusus Lirik (Floating Overlay)
if (isFloatingLyricsEnabled) {
    Window(
        onCloseRequest = { isFloatingLyricsEnabled = false },
        state = rememberWindowState(
            position = WindowPosition(savedX.dp, savedY.dp),
            size = DpSize(650.dp, 100.dp)
        ),
        alwaysOnTop = true,        // Tetap melayang di atas IDE/Browser
        undecorated = true,        // Tanpa frame/border OS yang kaku
        transparent = true         // Latar belakang transparan
    ) {
        FloatingLyricsView(
            currentLine = playbackState.currentLine,
            nextLine = playbackState.nextLine,
            progressMs = lyricClock.currentOffsetMs,
            animationStyle = userSelectedStyle
        )
    }
}
```

### B. 4 Preset Gaya Animasi Lirik (Lyrics Entrance & Transition Styles)
1. **Gaya Ping-Pong Slide (Kiri & Kanan Bergantian):**
   - Baris ganjil meluncur masuk dari sisi kiri (`slideInHorizontally { -it } + fadeIn()`).
   - Baris genap meluncur masuk dari sisi kanan (`slideInHorizontally { it } + fadeIn()`).
   - Baris yang selesai memudar keluar ke arah berlawanan.
2. **Gaya Dynamic Capsule Pop (Muncul dari Atas Layar):**
   - Menempel di bagian tengah atas layar (seperti Dynamic Island / HUD).
   - Teks baru meluncur turun dari atas (`slideInVertically { -it / 2 } + fadeIn() + spring()`).
3. **Gaya Karaoke Gradient Sweep (Syllable-Level Highlight):**
   - Teks lirik ditampilkan redup (transparan 35%), lalu warna aksen terang menyapu per suku kata seiring vokal dinyanyikan.
4. **Gaya Ambient Ghost (Zero-Distraction untuk Koding):**
   - Teks putih tipis berbayang halus tanpa kotak background, melayang di sudut layar.
   - Pergantian lirik menggunakan *smooth vertical float* (naik 8px) + *crossfade*.
   - Dilengkapi toggle *Click-Through* (Win32 `WS_EX_TRANSPARENT`) agar klik mouse tembus ke aplikasi di bawahnya.

---

## 5. Roadmap & Tahapan Eksekusi

```mermaid
flowchart TD
    Fase1["Fase 1: Setup Lingkungan (JDK 21 & Restore Modul)"] --> Fase2["Fase 2: Windows 11 Native Frame & Test Run"]
    Fase2 --> Fase3["Fase 3: Jendela Floating Lyrics & State Binding"]
    Fase3 --> Fase4["Fase 4: 4 Gaya Animasi & Click-Through"]
    Fase4 --> Fase5["Fase 5: Mini-Player, Tray Icon & Packaging"]
```

### Rincian Checklist:

#### Fase 1: Setup Lingkungan & Restore Modul Desktop
- [x] Install JDK 21 via `winget install EclipseAdoptium.Temurin.21.JDK`.
- [x] Verifikasi ketersediaan perintah `java -version` dan `javac -version`.
- [x] Restore folder `desktopApp` dan `desktopUi` dari riwayat commit git `e4dd63d8^1`.
- [x] Daftarkan kembali `include(":desktopApp")` di [settings.gradle.kts](file:///c:/Users/Daffa/Desktop/Folder Space AI/Folder Space Semester 7/Bit Chord/settings.gradle.kts).

#### Fase 2: Native Windows 11 Window Chrome & Smoke Test
- [x] Hapus pemanggilan `MacCaptionButton` (lampu merah-kuning-hijau) di `desktopApp/.../DesktopWindowChrome.kt`.
- [x] Pasang kontrol jendela Windows 11 (Minimize, Maximize, Close) di pojok kanan atas dengan styling Fluent / hover effects.
- [x] Jalankan `./gradlew :desktopApp:run` dan pastikan jendela utama terbuka normal di Windows 11.
- [x] Nonaktifkan notifikasi popup update mengganggu dari upstream.

#### Fase 3: Pembuatan Jendela Floating Lyrics (Overlay)
- [x] Buat file baru `FloatingLyricsWindow.kt` dengan parameter `alwaysOnTop = true`, `undecorated = true`, `transparent = true`.
- [x] Sambungkan *state* lirik aktif dari `PlaybackService` dan `LyricClock` ke komponen floating.
- [x] Implementasikan fitur *draggable window* (bisa digeser ke mana saja).

#### Fase 4: Dynamic Island Lyrics & YouTube Music Session Persistence (Selesai!)
- [x] **Dynamic Island Lyrics Window (Format 3 Baris Lirik + Media Control)**:
  - Format Kapsul Mengambang Dynamic Island (`660.dp x 104.dp`) dengan deep OLED glass background & border glowing halus.
  - **Kiri**: Artwork Cover, Judul Lagu, Artis, dan Kontrol Pemutar Musik (`⏮ Prev`, `▶/⏸ Play/Pause/Stop`, `⏭ Next/Lanjut`, `🔁 Repeat Mode`).
  - **Kanan (3 Baris Lirik Rolling)**:
    - Baris 1: `(Lirik Lagu Sebelum)` — teks redup 38%
    - Baris 2: `(Lirik Lagu Sekarang)` — teks aktif bold menyala 100% dengan animasi transisi bergulir
    - Baris 3: `(Lirik Lagu Sesudah)` — teks redup 38%
  - Hover Action Badges: Pin/Lock toggle & Close button.
- [x] **Ingat Sesi Login YouTube Music Permanen**:
  - Menyimpan cookie dan identitas sesi ke `DesktopPersistence` dan backup file lokal di `~/.bitchord/`.
  - Mengaktifkan auto-restore sesi saat aplikasi diluncurkan di `DesktopApp.kt` dan `DesktopAccounts.kt`.

#### Fase 5: Menu Dynamic Island & Polish Desktop (Selesai!)
- [x] **Menu Dynamic Island** — satu menu yang sama muncul di tiga tempat:
  - **Klik kanan pada kapsul** (dibuka saat tombol dilepas, supaya klik kanan tidak ikut memilih menu).
  - **Tombol 🎵 di toolbar**, yang ikonnya menyala saat island aktif di mana pun.
  - **Halaman Settings → Dynamic Island**, lengkap dengan *live preview* yang memakai lagu yang sedang diputar.
- [x] **Isi menu**: Play/Pause, Next, Previous, nyalakan/matikan kapsul mengambang, nyalakan/matikan island di top bar, pilih layout (Full/Compact/Mini), Lock/Reset posisi, buka panel lirik, dan pintasan ke Settings.
- [x] **`DesktopDynamicIslandSettings.kt`** — semua preferensi island disimpan di satu tempat (`DesktopPersistence`): layout, sudut (6 posisi), ukuran 80–130%, opacity 35–100%, cover/kontrol/progress on-off, auto-collapse saat idle, sembunyi saat pause, lock posisi, dan offset hasil drag.
- [x] **Tampilan lebih baik**:
  - Tiga layout: **Full** (cover + 4 kontrol + 3 baris lirik), **Compact** (1 baris + play/pause), **Mini** (artwork + 1 baris).
  - **Lirik bergulir**: blok tiga baris bergeser naik satu baris saat ganti lirik; baris aktif langsung putih penuh — tanpa animasi warna karaoke, sama seperti lembar lirik di layar Now Playing.
  - Progress hairline dengan titik glow di ujung playhead, ukuran berubah dengan animasi spring.
  - Kapsul otomatis mengecil ke Mini saat belum ada lagu (tidak ada kapsul besar yang kosong).
  - Posisi default 60 dp di bawah toolbar, supaya tidak menutupi kontrol jendela.
  - Drag hanya dengan tombol kiri (klik kanan tidak lagi menggeser kapsul).
- [x] **Shortcut keyboard** <kbd>Ctrl</kbd> + <kbd>Alt</kbd> + <kbd>D</kbd> untuk menyalakan/mematikan island.
- [x] **"Putar di Dynamic Island" di menu tiga titik setiap lagu** (`DesktopSongActions.onPlayInIsland`): menyalakan kapsul, lalu memutar lagunya kalau bukan lagu yang sedang diputar. Ada di menu baris lagu mana pun dan di menu pemutar.
- [x] **Terjemahan Indonesia + Inggris** untuk seluruh UI island (48 kunci `d_island_*` di `desktop-en.xml` dan `desktop-id.xml`), dibaca saat digambar sehingga ganti bahasa langsung berlaku tanpa restart.
- [x] **Memperbaiki terjemahan aplikasi yang selama ini tidak pernah aktif**:
  - `UNICODE_ESCAPE` dideklarasikan *setelah* blok `init`, jadi nilainya masih `null` saat katalog pertama dimuat → NPE → `load()` menelan galat dan mengembalikan peta kosong. Akibatnya **semua** bahasa selalu jatuh ke Inggris.
  - Regex dipindah ke atas `init`, dan kegagalan memuat katalog sekarang dicatat ke log (`DesktopTrackLog`) supaya tidak hilang diam-diam.
  - Parser XML memakai `DocumentBuilderFactory.newDefaultInstance()` agar tidak tertukar dengan provider JAXP dari library lain di classpath.
- [x] **Perbaikan build desktop**:
  - `processResources` menduplikasi `models/*.onnx` sehingga `:desktopApp:run` gagal total — duplikat dihapus.
  - Jendela kini dibuka sesuai *work area* layar (`openingWindowSize()`), jadi di layar 1080p dengan scaling 125–156% jendela tidak lagi terbuka lebih tinggi dari layar dan baris Settings tidak hilang di bawah.

#### Fase 6: Mini-Player, System Tray & Distribusi
- [ ] Aktifkan `DesktopAwtTray.kt` agar aplikasi bisa bersembunyi di System Tray Windows saat di-minimize.
- [ ] Tambahkan toggle *Click-Through Mode* (`WS_EX_TRANSPARENT`) via JNI jika diinginkan.
- [ ] Tambahkan entri "Dynamic Island" di menu tray.
- [ ] Terjemahkan label menu/settings island ke katalog `desktop-id.xml` (sekarang masih Inggris, sama seperti fallback bahasa lain).
- [ ] Paketkan menjadi installer Windows (`.msi` / executable mandiri) via Gradle task `packageMsi` / `packageDistributionForCurrentOS`.

### Fase 7: Optimasi & Pemangkasan Fitur (Klien Windows Saja)

Tujuan: aplikasi ringan — lebih kecil paketnya, lebih sedikit yang jalan di latar belakang, dan
tidak ada fitur yang memang tidak pernah bisa aktif di mesin ini. Yang dibuang beserta alasannya:

- [x] **Automix + transisi DJ** — penyebab terberat di aplikasi:
  - `onnxruntime` **51,9 MB** satu jar, model `beat_this_int8.onnx` (4,5 MB) + `vocals_umxhq_int8.onnx` (9 MB),
    analyser C++ (`native/analyzer`, `bitchord_analysis.dll`), dan ~2.500 baris Kotlin.
  - Butuh inferensi latar belakang saat lagu diputar (thread analisis + ONNX session) — beban CPU yang
    tidak terasa manfaatnya untuk mendengarkan lagu di desktop.
  - Efek transisi echo / beat-roll / reverb / flanger-crush-phaser-vibrato ikut dibuang; **crossfade
    manual 0–12 detik dan "Crossfade saat skip" tetap ada**, lengkap dengan penanda di scrubber.
  - Penggantinya: satu loader native umum `DesktopNativeRuntime.kt` (dipakai frame Windows + SMTC),
    dan proyek CMake SMTC dipisah (`desktopApp/native/smtc/`) supaya tombol media Windows tidak ikut hilang.
- [ ] **Modul JS (GraalVM)** — `polyglot` + `js-language` + `truffle` + ICU bawaan ≈ **61 MB**, hanya dipakai
  host plugin yang tidak punya UI instal; indeks modul upstream sudah 404 dan preferensi
  `MODULE_INDEX_URL` tidak pernah dibaca kode.
- [x] **Listen Together / PartySync / QR** — butuh server pihak ketiga yang tidak pernah diisi
  (tombolnya tetap muncul, tapi gagal dengan "Set the party server address first"). Yang dibuang:
  mesin party (`DesktopListenTogether`, `DesktopPartySync`, dialog + UI-nya, QR undangan dan `zxing`),
  kontrak `party` di host pemutar, pil "party" di kapsul output, lembar daftar anggota, kunci-kunci
  `listen_together_*` (37 baris katalog), serta penjagaan `controlsLocked` di transport, antrean,
  seek, MPRIS dan kartu media Windows. AutoPlay kini hanya mengikuti preferensi lokal — tidak ada
  lagi pemilihan "supplier" dan penitipan antrean pribadi.
- [x] **Telemetri Presence** — ping "aplikasi terbuka" ke server upstream tiap ~5 menit, tanpa
  cara mematikannya dari Settings.
- [ ] **Kode macOS** — `DesktopMacFrame` (550 baris, JNA) dan cabang `macos` di skrip Gradle: build
  ini menargetkan Windows dan Linux, jadi bingkai Mac tidak pernah dipakai. Belum disentuh.
- [x] **Kode Linux dipertahankan** — MPRIS, StatusNotifier, `dbus-java` dan pemantau audio ALSA
  sengaja TIDAK dihapus: target repo ini Windows + Linux, dan job `desktop-linux` di CI menjaganya
  tetap terkompilasi.
- [ ] **Sumber JioSaavn dipertahankan** — sempat masuk daftar buang karena ikut balapan resolusi di
  setiap lagu; ditahan karena ia sumber lossy 320kbps yang benar-benar dipakai.

