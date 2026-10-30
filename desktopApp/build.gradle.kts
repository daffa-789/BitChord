import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/** What the packages, the launcher and the settings footer all say. */
val appVersion: String = providers.gradleProperty("bitchord.version").orNull
    ?.removePrefix("v")
    ?.takeIf { it.isNotBlank() }
    ?: "1.8.0"

/** Which platform this build is *for*, which is the host unless told otherwise. */
val hostIsWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
val hostIsLinux = System.getProperty("os.name").contains("Linux", ignoreCase = true)
val targetOs: String = (providers.gradleProperty("bitchord.target").orNull ?: when {
    hostIsWindows -> "windows"
    hostIsLinux -> "linux"
    else -> error("BitChord desktop supports Windows and Linux only")
}).lowercase()

// Windows installer metadata requires MAJOR.MINOR.BUILD even though the app's public version is
// intentionally displayed without a patch number (1.7 rather than 1.7.0).
// The build number is the desktop version code; it also keeps "-beta1" out of the numeric installer version.
// Release tags carry it — `v1.8.31` builds 1.8.31 — so CI names the packages after what it tagged. A
// plain checkout keeps the code below, which is what an installer already on a machine compares against
// when it decides whether this is an upgrade.
val desktopVersionCode: Int = providers.gradleProperty("bitchord.build").orNull
    ?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
    ?: 29
val nativePackageVersion = appVersion.substringBefore('-').split('.').take(2).joinToString(".") + ".$desktopVersionCode"

// FFmpeg decodes audio; see DesktopAudioDecoder.
val javacppVersion = "1.5.12"
val ffmpegVersion = "7.1.1-$javacppVersion"
val nativeClassifier = when (targetOs) {
    "windows" -> "windows-x86_64"
    "linux" -> "linux-x86_64"
    else -> error("BitChord desktop supports Windows and Linux only")
}
private fun localProperty(name: String): String = rootProject.file("local.properties")
    .takeIf { it.isFile }
    ?.readLines()
    ?.firstOrNull { it.startsWith("$name=") }
    ?.substringAfter('=')
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: System.getenv(name).orEmpty().trim()

// Last.fm signs every request with these, so signing in from inside the app
// needs them at hand. Supplied locally and never committed, as on Android.
val lastfmApiKey = localProperty("LASTFM_API_KEY")
val lastfmSecret = localProperty("LASTFM_SECRET")

/** Versions shared with the other modules; see the block in gradle.properties. */
fun property(name: String): String = providers.gradleProperty("bitchord.lib.$name").get()

val ktor = property("ktor")

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    // jsoup comes in through the NewPipe extractor, as on Android.

    implementation(project(":shared"))
    // Its own copy of the player and pages; the phone and TV keep :sharedUi.
    implementation(project(":desktopUi"))
    // BotGuard, for the PoTokens YouTube's web clients need: the phone runs it in an Android
    // WebView, the desktop in JavaFX's (WebKit). Per-platform jars carry the natives.
    // `media` is not named here on purpose: javafx-web drags it in itself, so the archive carries
    // it either way and naming it only made it look optional.
    val javafxClassifier = when (targetOs) {
        "windows" -> "win"
        else -> "linux"
    }
    listOf("base", "graphics", "controls", "web").forEach { module ->
        implementation("org.openjfx:javafx-$module:21.0.10:$javafxClassifier")
    }

    // The shared player loads artwork through Coil, as the phone does. This jar is never imported
    // by name — Coil finds `OkHttpNetworkFetcher` by its hardcoded class name — so deleting it
    // would fail at the first artwork request, not at compile time.
    implementation("io.coil-kt.coil3:coil-network-okhttp:${property("coil")}")

    // Decoding, resampling and demuxing for the audio engine.
    implementation("org.bytedeco:javacpp:$javacppVersion")
    implementation("org.bytedeco:javacpp:$javacppVersion:$nativeClassifier")
    implementation("org.bytedeco:ffmpeg:$ffmpegVersion")
    implementation("org.bytedeco:ffmpeg:$ffmpegVersion:$nativeClassifier")

    // The same backdrop blur the Android build uses for its floating bars.
    // On Windows, 1.7.2 keeps this Compose Desktop 1.10.3 app on the matching UI/text/animation
    // runtime. Preserve Linux's existing dependency graph exactly as it was.
    val haze = if (targetOs == "windows") property("haze.windows") else property("haze.linux")
    implementation("dev.chrisbanes.haze:haze:$haze")
    implementation("dev.chrisbanes.haze:haze-materials:$haze")

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.components.resources)
    implementation(compose.materialIconsExtended)
    // The Skiko runtime rides in on this, and Skiko is per-platform.
    implementation(
        when (targetOs) {
            "windows" -> compose.desktop.windows_x64
            else -> compose.desktop.linux_x64
        }
    )

    // The engine the scrobbler, the JioSaavn client and the addon HTTP calls run on. Same version as
    // :shared, whose ktor 3.5.2 these used to be mixed with on one classpath.
    implementation("io.ktor:ktor-client-core:$ktor")
    implementation("io.ktor:ktor-client-cio:$ktor")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor")
    // WebSockets arrive with :shared, whose Discord gateway exports the engine.
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("serialization")}")
    implementation("org.slf4j:slf4j-nop:2.0.16")

    // Browsers keep their cookies in SQLite, and importing a session from one is how this app signs
    // in — see DesktopBrowserCookies. Never imported by name either: `DriverManager` picks the driver
    // up by SPI from the "jdbc:sqlite:" URL.
    implementation("org.xerial:sqlite-jdbc:${property("sqliteJdbc")}")
    // Chromium protects its Windows master key with DPAPI. JNA's platform helpers let the app
    // unwrap that key in the same Windows user session without invoking a shell or exposing it.
    implementation("net.java.dev.jna:jna-platform:${property("jna")}")

    // Linux desktop media controls use the standard session-bus MPRIS interfaces. They stay on the
    // Windows classpath too, because one source set holds both platforms' window and bus code; the
    // Windows build never loads them, and the strip below shrinks what they carry.
    implementation("com.github.hypfvieh:dbus-java-core:5.2.0")
    implementation("com.github.hypfvieh:dbus-java-transport-junixsocket:5.2.0")

    // Same URL deobfuscation helper used by Android's StreamResolver.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:${property("newpipe")}")

    testImplementation(kotlin("test"))
}

/**
 * Four of these jars carry a compiled native library for *every* platform they were built for, and
 * exactly one of them can ever be loaded here. [org.xerial:sqlite-jdbc] is the worst: 14,2 MB in the
 * archive, of which the Windows build reads one 1,5 MB `sqlitejdbc.dll`.
 *
 * [stripForeignNatives] rewrites those jars down to the target's own library and the runtime
 * classpath ships the rewritten copy, so the portable archive and the `jpackage` forms (MSI, deb,
 * rpm) all shrink together. The compile classpath still sees the untouched artifact, and a jar whose
 * entries are signed is never rewritten — the JVM rejects a signed jar whose contents changed, and a
 * broken signature is worse than a fat jar.
 */
val nativeFat by configurations.creating {
    isCanBeResolved = true
    // The same graph the app runs on, without the excludes applied below.
    extendsFrom(configurations.implementation.get())
}

/** Every `com/sun/jna/<dir>/` that holds a dispatcher binary rather than classes. */
val jnaNativeDirs = listOf(
    "aix-ppc", "aix-ppc64", "darwin-aarch64", "darwin-x86-64", "dragonflybsd-x86-64",
    "freebsd-aarch64", "freebsd-x86-64", "freebsd-x86",
    "linux-aarch64", "linux-arm", "linux-armel", "linux-loongarch64", "linux-mips64el",
    "linux-ppc", "linux-ppc64le", "linux-riscv64", "linux-s390x", "linux-x86", "linux-x86-64",
    "openbsd-x86-64", "sunos-sparc", "sunos-sparcv9", "sunos-x86", "sunos-x86-64",
    "win32-aarch64", "win32-x86", "win32-x86-64",
)

/** The same for junixsocket, which arrives with the Linux D-Bus transport. */
val junixsocketNativeDirs = listOf(
    "aarch64-Linux-clang", "aarch64-MacOSX-clang", "aarch64-Windows10-clang",
    "amd64-DragonFlyBSD-clang", "amd64-FreeBSD-clang", "amd64-Linux-clang",
    "amd64-NetBSD-clang", "amd64-OpenBSD-clang", "amd64-SunOS-clang", "amd64-Windows10-clang",
    "arm-Linux-clang", "loongarch64-Linux-clang", "ppc64-AIX-clang", "ppc64-OS400-clang",
    "ppc64le-Linux-clang", "riscv64-Linux-clang", "s390x-Linux-clang",
)

/**
 * Entry prefixes to drop, keyed by `group:module`.
 *
 * Linux keeps `Linux-Musl/x86_64` next to `Linux/x86_64`: the packages this build produces are glibc
 * amd64, but a musl host running the same image is a real thing people do, and one file is cheap.
 * Android and the 32-bit/ARM builds are not — no package form here can reach them.
 */
val fatNativeDrops: Map<String, List<String>> = when (targetOs) {
    "windows" -> mapOf(
        "org.xerial:sqlite-jdbc" to listOf(
            "org/sqlite/native/FreeBSD/", "org/sqlite/native/Linux/", "org/sqlite/native/Linux-Android/",
            "org/sqlite/native/Linux-Musl/", "org/sqlite/native/Mac/",
            "org/sqlite/native/Windows/aarch64/", "org/sqlite/native/Windows/armv7/",
            "org/sqlite/native/Windows/x86/",
        ),
        "net.java.dev.jna:jna" to jnaNativeDirs.filterNot { it == "win32-x86-64" }.map { "com/sun/jna/$it/" },
        "io.github.dokar3:quickjs-kt-jvm" to listOf(
            "jni/linux_aarch64/", "jni/linux_x64/", "jni/macos_aarch64/", "jni/macos_x64/",
        ),
        "com.kohlschutter.junixsocket:junixsocket-native-common" to
            junixsocketNativeDirs.filterNot { it == "amd64-Windows10-clang" }.map { "lib/$it/" },
    )

    else -> mapOf(
        "org.xerial:sqlite-jdbc" to listOf(
            "org/sqlite/native/Windows/", "org/sqlite/native/Mac/", "org/sqlite/native/FreeBSD/",
            "org/sqlite/native/Linux-Android/",
            "org/sqlite/native/Linux/aarch64/", "org/sqlite/native/Linux/arm/",
            "org/sqlite/native/Linux/armv6/", "org/sqlite/native/Linux/armv7/",
            "org/sqlite/native/Linux/ppc64/", "org/sqlite/native/Linux/riscv64/",
            "org/sqlite/native/Linux/x86/",
            "org/sqlite/native/Linux-Musl/aarch64/", "org/sqlite/native/Linux-Musl/x86/",
        ),
        "net.java.dev.jna:jna" to jnaNativeDirs.filterNot { it == "linux-x86-64" }.map { "com/sun/jna/$it/" },
        "io.github.dokar3:quickjs-kt-jvm" to listOf(
            "jni/windows_x64/", "jni/linux_aarch64/", "jni/macos_aarch64/", "jni/macos_x64/",
        ),
        "com.kohlschutter.junixsocket:junixsocket-native-common" to
            junixsocketNativeDirs.filterNot { it == "amd64-Linux-clang" }.map { "lib/$it/" },
    )
}

val strippedNativesDir = layout.buildDirectory.dir("stripped-natives/$targetOs")

/** Copies [from] to [to] without the entries under [drop]; compression is kept as it was. */
fun rewriteJar(from: File, to: File, drop: List<String>): Pair<Int, Int> {
    var kept = 0
    var dropped = 0
    ZipFile(from).use { zip ->
        ZipOutputStream(to.outputStream().buffered()).use { out ->
            zip.entries().toList().forEach { entry ->
                if (drop.any { prefix -> entry.name.startsWith(prefix) }) {
                    dropped++
                    return@forEach
                }
                out.putNextEntry(
                    ZipEntry(entry.name).apply {
                        method = entry.method
                        if (entry.time >= 0L) time = entry.time
                        if (entry.method == ZipEntry.STORED) {
                            size = entry.size
                            compressedSize = entry.compressedSize
                            crc = entry.crc
                        }
                    },
                )
                zip.getInputStream(entry).use { it.copyTo(out) }
                out.closeEntry()
                kept++
            }
        }
    }
    return kept to dropped
}

val stripForeignNatives by tasks.registering {
    group = "distribution"
    description = "Rewrites the all-platform native jars down to $targetOs' own library."
    val source = nativeFat
    val drops = fatNativeDrops
    inputs.files(source)
    inputs.property("target", targetOs)
    outputs.dir(strippedNativesDir)
    doLast {
        val outDir = strippedNativesDir.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()
        val seen = mutableSetOf<String>()
        source.incoming.artifacts.artifacts.forEach { artifact ->
            val identifier = artifact.id.componentIdentifier.toString()
            val coordinate = identifier.substringBeforeLast(":")
            val drop = drops[coordinate] ?: return@forEach
            val from = artifact.file
            check(from.name.endsWith(".jar")) { "stripForeignNatives: $identifier is not a jar" }
            val signed = ZipFile(from).use { zip ->
                zip.entries().toList().any { entry ->
                    entry.name.startsWith("META-INF/") &&
                        (entry.name.endsWith(".SF") || entry.name.endsWith(".RSA") || entry.name.endsWith(".DSA"))
                }
            }
            check(!signed) { "stripForeignNatives: $identifier is signed; rewriting it would break that." }
            val (kept, dropped) = rewriteJar(from, File(outDir, from.name), drop)
            seen += coordinate
            logger.lifecycle(
                "stripForeignNatives: $identifier keeps $kept entries, drops $dropped — " +
                    "the other platforms' libraries, across ${drop.size} native dir(s)."
            )
        }
        // A module that stopped being part of the graph means the weight is back and nobody would
        // notice from a green build.
        val missing = drops.keys - seen
        check(missing.isEmpty()) {
            "stripForeignNatives: $missing is no longer on the classpath — this table is stale, " +
                "or the dependency that carried it is gone."
        }
    }
}

// The jars the task wrote, as classpath entries in their own right. Handing the classpath the
// directory instead would put no archive on it at all, and the SQLite driver would simply be absent
// when a browser's cookies are read.
val strippedNativeJars = files(strippedNativesDir.map { it.asFileTree }).builtBy(stripForeignNatives)

dependencies {
    runtimeOnly(strippedNativeJars)
}

fatNativeDrops.keys.forEach { coordinate ->
    configurations.named("runtimeClasspath") {
        exclude(mapOf("group" to coordinate.substringBefore(':'), "module" to coordinate.substringAfter(':')))
    }
}

tasks.withType<Test> {
    systemProperty("java.awt.headless", "true")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

/** The Windows media controls (SMTC), from the same C++ the Windows installer ships. */
val smtcNativeDir = layout.buildDirectory.dir("native-smtc/$targetOs")

/** The tiny Windows frame bridge is independent of the optional analyser and SMTC libraries. */
val windowNativeDir = layout.buildDirectory.dir("native-window/$targetOs")

/**
 * Whether this build is producing the analyser for a platform that is not the one running the
 * build.
 */
val crossBuildingForWindows = targetOs == "windows" && !hostIsWindows

/**
 * An executable's absolute path, from the PATH this build was invoked with.
 *
 * Resolved here rather than left to the child process. Gradle runs external
 * commands from the daemon, whose own PATH is whatever it was started with, so
 * a daemon reused from a shell that had no cmake cannot find the one the
 * current shell does — it fails with a bare "No such file or directory" while
 * `cmake --version` works fine in the terminal beside it.
 */
/**
 * Whether a tool on the PATH can actually be started.
 *
 * A PATH entry can be a wrapper script whose own interpreter is gone; it is a file, it is
 * executable, and it fails only at exec time — which surfaces as a build failure rather than as the
 * missing-tool path this build already handles.
 */
fun runs(tool: File): Boolean = runCatching {
    val process = ProcessBuilder(tool.absolutePath, "--version")
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        false
    } else {
        process.exitValue() == 0
    }
}.getOrDefault(false)

fun findOnPath(tool: String): File? {
    // Windows keeps the extension on the file and off the command line, so a bare name matches
    // nothing on disk: PATHEXT is the list a shell would have tried.
    val names = if (!hostIsWindows) {
        listOf(tool)
    } else {
        val extensions = System.getenv("PATHEXT").orEmpty()
            .ifBlank { ".COM;.EXE;.BAT;.CMD" }
            .split(';')
            .filter(String::isNotBlank)
        listOf(tool) + extensions.map { tool + it.lowercase() }
    }
    val fallbackDirs = if (hostIsWindows) {
        val userHome = System.getProperty("user.home").replace('\\', '/')
        listOf(
            "C:/Program Files/CMake/bin",
            "$userHome/AppData/Local/Microsoft/WinGet/Links",
            "$userHome/AppData/Local/Microsoft/WinGet/Packages/BrechtSanders.WinLibs.POSIX.UCRT_Microsoft.Winget.Source_8wekyb3d8bbwe/mingw64/bin",
        )
    } else emptyList()
    val searchPaths = (System.getenv("PATH").orEmpty().split(File.pathSeparator) + fallbackDirs)
        .filter(String::isNotBlank)
    return searchPaths
        .flatMap { directory -> names.map { File(directory, it) } }
        // A PATHEXT match is executable by definition, and Windows' own canExecute is unreliable.
        .firstOrNull { it.isFile && (hostIsWindows || it.canExecute()) && runs(it) }
}

fun cmakeBinary(): String = findOnPath("cmake")?.absolutePath ?: "cmake"

/**
 * Builds the Win32 media controls (SMTC). Like the frame bridge it is Windows-only, and it is its
 * own CMake project so one missing toolchain cannot hide the other.
 */
val buildSmtcNative by tasks.registering {
    val cmakeSource = project.file("native/smtc")
    val outputDir = smtcNativeDir.get().asFile
    val crossing = crossBuildingForWindows
    val toolchain = project.file("native/mingw-w64.cmake")
    inputs.dir(cmakeSource)
    inputs.property("target", targetOs)
    outputs.dir(outputDir)
    onlyIf {
        if (targetOs != "windows") return@onlyIf false
        val cmake = findOnPath("cmake")
        val compiler = findOnPath(if (crossing) "x86_64-w64-mingw32-g++" else "g++")
        val ninja = findOnPath("ninja")
        if (cmake == null || compiler == null || ninja == null) {
            logger.lifecycle(
                "cmake, Ninja, or a MinGW C++ compiler is unavailable - the Windows build will " +
                    "have no SMTC media controls.",
            )
        }
        cmake != null && compiler != null && ninja != null
    }
    doLast {
        val javaHome = System.getProperty("java.home")
        val compiler = findOnPath(if (crossing) "x86_64-w64-mingw32-g++" else "g++")
            ?: error("MinGW C++ compiler disappeared while building the Windows media controls")
        val ninja = findOnPath("ninja")
            ?: error("Ninja disappeared while building the Windows media controls")
        providers.exec {
            commandLine(
                buildList {
                    addAll(
                        listOf(
                            cmakeBinary(),
                            "-S", cmakeSource.absolutePath,
                            "-B", outputDir.absolutePath,
                            "-G", "Ninja",
                            "-DCMAKE_MAKE_PROGRAM=${ninja.absolutePath}",
                            "-DCMAKE_CXX_COMPILER=${compiler.absolutePath}",
                            "-DCMAKE_BUILD_TYPE=Release",
                        ),
                    )
                    if (crossing) add("-DCMAKE_TOOLCHAIN_FILE=${toolchain.absolutePath}")
                },
            )
            environment("JAVA_HOME", javaHome)
        }.result.get().assertNormalExitValue()
        providers.exec {
            commandLine(cmakeBinary(), "--build", outputDir.absolutePath, "-j", "4")
            environment("JAVA_HOME", javaHome)
        }.result.get().assertNormalExitValue()
    }
}

/**
 * Builds the Win32/DWM frame bridge even when the much larger analyser build is skipped during a
 * desktop UI run. It is intentionally absent from Linux builds, whose window manager remains the
 * sole owner of decoration and behaviour.
 */
val buildWindowNative by tasks.registering {
    val cmakeSource = project.file("native/window")
    val outputDir = windowNativeDir.get().asFile
    val crossing = crossBuildingForWindows
    val toolchain = project.file("native/mingw-w64.cmake")
    inputs.dir(cmakeSource)
    inputs.property("target", targetOs)
    outputs.dir(outputDir)
    onlyIf {
        if (targetOs != "windows") return@onlyIf false
        val cmake = findOnPath("cmake")
        val compiler = findOnPath(if (crossing) "x86_64-w64-mingw32-g++" else "g++")
        val ninja = findOnPath("ninja")
        if (cmake == null || compiler == null || ninja == null) {
            logger.lifecycle(
                "cmake, Ninja, or a MinGW C++ compiler is unavailable — the Windows build will " +
                    "fall back to Compose's frameless window behaviour.",
            )
        }
        cmake != null && compiler != null && ninja != null
    }
    doLast {
        val javaHome = System.getProperty("java.home")
        val compiler = findOnPath(if (crossing) "x86_64-w64-mingw32-g++" else "g++")
            ?: error("MinGW C++ compiler disappeared while building the Windows frame")
        val ninja = findOnPath("ninja")
            ?: error("Ninja disappeared while building the Windows frame")
        providers.exec {
            commandLine(
                buildList {
                    addAll(
                        listOf(
                            cmakeBinary(),
                            "-S", cmakeSource.absolutePath,
                            "-B", outputDir.absolutePath,
                            "-G", "Ninja",
                            "-DCMAKE_MAKE_PROGRAM=${ninja.absolutePath}",
                            "-DCMAKE_CXX_COMPILER=${compiler.absolutePath}",
                            "-DCMAKE_BUILD_TYPE=Release",
                        ),
                    )
                    if (crossing) add("-DCMAKE_TOOLCHAIN_FILE=${toolchain.absolutePath}")
                },
            )
            environment("JAVA_HOME", javaHome)
        }.result.get().assertNormalExitValue()
        providers.exec {
            commandLine(cmakeBinary(), "--build", outputDir.absolutePath, "-j", "4")
            environment("JAVA_HOME", javaHome)
        }.result.get().assertNormalExitValue()
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(buildSmtcNative, buildWindowNative)
    from(smtcNativeDir) {
        include("*.dll")
        into("native")
    }
    from(windowNativeDir) {
        include("*.dll")
        into("native")
    }
}

// Kotlin 2.4 no longer contributes this JVM target's compiler output to Gradle's plain `jar`
// task in this project. Compose's dev runner and the portable launcher both use that jar, so add
// the output explicitly instead of producing a resources-only application archive.
tasks.named<Jar>("jar") {
    val kotlinClasses = tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin")
    dependsOn(kotlinClasses)
    from(kotlinClasses.flatMap { it.destinationDirectory })
}

/** A Windows build that can be assembled here, since an installer cannot be. */
val windowsPortableDir = layout.buildDirectory.dir("windows-portable")

val windowsPortableAssemble by tasks.registering {
    group = "distribution"
    description = "Lays out a Windows-runnable folder: application jars, Windows natives, launcher."
    dependsOn(tasks.named("jar"))
    val stage = windowsPortableDir.map { it.dir("BitChord") }
    val appJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    val runtime = configurations.named("runtimeClasspath")
    val version = appVersion
    val lastfmKey = lastfmApiKey
    val lastfmSharedSecret = lastfmSecret
    val mainClass = composeMainClass
    // Declared, not implied.
    inputs.file(appJar)
    inputs.files(runtime)
    inputs.property("version", version)
    inputs.property("lastfm", lastfmKey.isNotBlank())
    inputs.property("mainClass", mainClass)
    outputs.dir(stage)
    doLast {
        val target = stage.get().asFile
        val lib = File(target, "lib")
        lib.deleteRecursively()
        lib.mkdirs()

        // Named by coordinate rather than by file name.
        val staged = mutableSetOf<File>()
        runtime.get().incoming.artifacts.artifacts.forEach { artifact ->
            val id = artifact.id.componentIdentifier
            val group = (id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier)?.group
            val name = if (group.isNullOrBlank()) artifact.file.name else "$group.${artifact.file.name}"
            artifact.file.copyTo(File(lib, name), overwrite = true)
            staged += artifact.file
        }
        // The stripped natives come in as plain file dependencies, and whether a file dependency shows
        // up in the artifact view is a Gradle-version detail. Anything the loop above missed is copied
        // under its own name rather than quietly left out of the archive.
        (runtime.get().files - staged).forEach { file ->
            if (file.name.endsWith(".jar")) file.copyTo(File(lib, file.name), overwrite = true)
        }
        appJar.get().asFile.copyTo(File(lib, "bitchord-app.jar"), overwrite = true)

        // `lib\*` rather than a spelled-out classpath: the wildcard is what keeps this working when
        // a dependency is added, and the JVM is what expands it.
        File(target, "BitChord.bat").writeText(
            buildString {
                appendLine("@echo off")
                appendLine("setlocal")
                appendLine("rem BitChord, portable. Needs a Java 21+ runtime on PATH or in JAVA_HOME.")
                appendLine("set \"HERE=%~dp0\"")
                appendLine("set \"RUNNER=java\"")
                appendLine("if defined JAVA_HOME set \"RUNNER=%JAVA_HOME%\\bin\\java\"")
                append("\"%RUNNER%\" -Dbitchord.version=$version")
                if (lastfmKey.isNotBlank() && lastfmSharedSecret.isNotBlank()) {
                    append(" -Dbitchord.lastfm.key=$lastfmKey -Dbitchord.lastfm.secret=$lastfmSharedSecret")
                }
                appendLine(" -cp \"%HERE%lib\\*\" $mainClass %*")
                appendLine("endlocal")
            },
        )

        // What "it builds" is not the same as "it runs", and the difference is exactly what shipped
        // a broken archive.
        val jars = lib.listFiles { file: File -> file.name.endsWith(".jar") }.orEmpty()
        fun holds(entry: String) = jars.any { jar ->
            ZipFile(jar).use { zip -> zip.getEntry(entry) != null }
        }
        fun holdsUnder(prefix: String) = jars.any { jar ->
            ZipFile(jar).use { zip -> zip.entries().toList().any { it.name.startsWith(prefix) } }
        }
        val problems = buildList {
            if (!holds("com/music/bitchord/desktop/MainKt.class")) {
                add("the BitChord desktop entry point is missing")
            }
            if (!holds("androidx/compose/runtime/Composer.class")) {
                add("the Compose runtime is missing")
            }
            if (jars.none { it.name.contains("skiko-awt-runtime-windows") }) {
                add("the Windows Skiko renderer is missing")
            }
            if (jars.any { it.name.contains("skiko-awt-runtime-linux") }) {
                add("a Linux Skiko renderer is in a Windows archive")
            }
            if (!holds("org/bytedeco/ffmpeg/windows-x86_64/avformat.dll") &&
                jars.none { it.name.contains("ffmpeg") && it.name.contains("windows-x86_64") }
            ) {
                add("the Windows FFmpeg natives are missing")
            }
            // The stripped jars are the one place where a dependency is rewritten rather than copied,
            // so both halves have to be proven: this platform's library survived, the others did not.
            if (!holds("org/sqlite/native/Windows/x86_64/sqlitejdbc.dll")) {
                add("the SQLite driver's Windows library is missing — browser cookie import would fail")
            }
            if (!holds("com/sun/jna/win32-x86-64/jnidispatch.dll")) {
                add("JNA's Windows dispatcher is missing — DPAPI and the island window would fail")
            }
            if (!holds("jni/windows_x64/libquickjs.dll")) {
                add("the QuickJS Windows library is missing — YouTube's signature challenge would fail")
            }
            val foreignLeft = listOf(
                "org/sqlite/native/Mac/", "org/sqlite/native/Linux/", "com/sun/jna/darwin-", "jni/linux_",
            ).filter { holdsUnder(it) }
            if (foreignLeft.isNotEmpty()) {
                add("foreign natives are still in the archive (${foreignLeft.joinToString(", ")}) — " +
                    "stripForeignNatives did not reach them")
            }
        }
        check(problems.isEmpty()) { "windowsPortable: " + problems.joinToString("; ") }

        val mediaControls = File(smtcNativeDir.get().asFile, "bitchord_smtc.dll").isFile
        logger.lifecycle(
            if (mediaControls) {
                "windowsPortable: verified — Compose runtime, Windows renderer, Windows FFmpeg, " +
                    "stripped natives, SMTC media controls."
            } else {
                "windowsPortable: verified, but no bitchord_smtc.dll: Windows media keys will not work in this archive. " +
                    "Rebuild with cmake, Ninja and a MinGW C++ compiler on PATH."
            },
        )
    }
}

val windowsPortable by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Zips the portable Windows folder for copying to a Windows machine."
    dependsOn(windowsPortableAssemble)
    from(windowsPortableDir)
    archiveFileName.set("BitChord-$appVersion-windows-portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    doLast { logger.lifecycle("windowsPortable: ${archiveFile.get().asFile.absolutePath}") }
}

val composeMainClass = "com.music.bitchord.desktop.MainKt"

compose.desktop {
    application {
        javaHome = javaToolchains.launcherFor(java.toolchain).map {
            it.metadata.installationPath.asFile.absolutePath
        }.get()
        mainClass = composeMainClass
        jvmArgs("-Xmx512m")
        jvmArgs("-XX:+UseG1GC", "-XX:G1PeriodicGCInterval=20000", "-XX:G1PeriodicGCSystemLoadThreshold=0")
        jvmArgs("-Dbitchord.version=$appVersion")
        if (lastfmApiKey.isNotBlank() && lastfmSecret.isNotBlank()) {
            jvmArgs("-Dbitchord.lastfm.key=$lastfmApiKey", "-Dbitchord.lastfm.secret=$lastfmSecret")
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
            obfuscate.set(false)
            optimize.set(false)
            configurationFiles.from(project.file("packaging/proguard-rules.pro"))
        }

        nativeDistributions {
            targetFormats(
                TargetFormat.Exe,
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.Rpm,
            )
            packageName = "BitChord"
            packageVersion = nativePackageVersion
            description = "BitChord"
            vendor = "BitChord contributors"
            copyright = "Copyright © 2026 BitChord contributors"

            modules(
                "java.base",
                "java.desktop",
                "java.instrument",
                "java.logging",
                "java.management",
                "java.naming",
                "java.net.http",
                "java.prefs",
                "java.sql",
                "java.xml",
                "jdk.crypto.ec",
                // JavaFX WebEngine returns JavaScript objects through netscape.javascript.JSObject.
                // Without this module the slim production runtime crashes in twkExecuteScript,
                // while development runs work because the full JDK already contains it.
                "jdk.jsobject",
                "jdk.localedata",
                "jdk.security.auth",
                "jdk.unsupported",
                "jdk.zipfs",
            )

            linux {
                iconFile.set(project.file("packaging/icons/AppIcon.png"))
                menuGroup = "Audio"
                appCategory = "AudioVideo;Audio;Player"
                debMaintainer = "85984486+AbhiTheModder@users.noreply.github.com"
                rpmLicenseType = "GPL-3.0"
            }

            windows {
                iconFile.set(project.file("packaging/icons/AppIcon.ico"))
                upgradeUuid = "8f3c1d24-6a2b-4f5e-9c17-2b8d54e0a913"
                menuGroup = "BitChord"
                shortcut = true
                dirChooser = false
                perUserInstall = true
            }
        }
    }

}
