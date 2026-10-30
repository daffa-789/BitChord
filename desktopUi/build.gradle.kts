/*
 * The desktop's own Compose UI — the player, lyrics, queue and the Home /
 * Explore / Library / Search pages.
 *
 * It began as a copy of :sharedUi, which the phone, tablet and TV keep drawing
 * from. The two now move separately: a desktop redesign lands here and never
 * reaches the phone, and the phone's player never moves the desktop's. Packages
 * and the resource class are unchanged from :sharedUi, so the desktop's imports
 * did not move; that is safe only because no module depends on both.
 */
/** Versions shared with the desktop modules; see the block in gradle.properties. */
fun property(name: String): String = providers.gradleProperty("bitchord.lib.$name").get()

/**
 * Which platform this build is *for*, which is the host unless `-Pbitchord.target` says otherwise.
 * The same rule desktopApp/build.gradle.kts uses, so a cross build cannot compile this module
 * against the other platform's pairing.
 */
val hostIsWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
val hostIsMac = System.getProperty("os.name").contains("Mac", ignoreCase = true)
val targetOs: String = (providers.gradleProperty("bitchord.target").orNull ?: when {
    hostIsWindows -> "windows"
    hostIsMac -> "macos"
    else -> "linux"
}).lowercase()

/**
 * What the desktop graph resolves, and what this module is compiled against.
 *
 * Skia's Matrix33 became an inline float[9] between skiko 0.9 and 0.15
 * (`makeTranslate` went from returning a Matrix33 to a name-mangled float[]
 * overload), so bytecode compiled against 0.9 linked its object call and
 * crashed with NoSuchMethodError on a desktop running 0.15 the first time a
 * lyric line grew. Compiling against the version the desktop actually resolves
 * keeps the compile-time link and the runtime link the same.
 */
val cmp = when (targetOs) {
    "windows" -> property("compose.windows")
    "macos" -> property("compose.macos")
    else -> property("compose.linux")
}

// Haze 1.7.x, which the Windows build uses (see desktopApp/build.gradle.kts), was compiled
// against a ShaderBrush.createShader that returns skia's Shader; 1.12 changed that return
// type, so on 1.12 every blur dies with NoSuchMethodError. Windows stays on the plugin's
// own 1.10.3 / skiko 0.9; only Linux moves to 1.12.
val haze = when (targetOs) {
    "windows" -> property("haze.windows")
    "macos" -> property("haze.macos")
    else -> property("haze.linux")
}

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        // Kept from :sharedUi so the two trees line up file for file; with one
        // target it is simply the body of jvmMain.
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(jvmSharedMain)

        commonMain.dependencies {
            api(project(":shared"))
            api("org.jetbrains.compose.runtime:runtime:$cmp")
            api("org.jetbrains.compose.foundation:foundation:$cmp")
            api("org.jetbrains.compose.animation:animation:$cmp")
            api("org.jetbrains.compose.ui:ui:$cmp")
            // Both of these were pinned a full generation behind what the desktop packages actually
            // carry (material3 1.7.3 against a resolved 1.9.0, haze 1.3.1 against 1.7.x), which is
            // the drift `cmp` above exists to prevent. These are the shipped versions.
            api("org.jetbrains.compose.material3:material3:${property("material3")}")
            api("org.jetbrains.compose.material:material-icons-extended:${property("materialIcons")}")
            api("org.jetbrains.compose.components:components-resources:$cmp")
            // 2.9.6, not the 2.8.4 this used to name: same reason as material3.
            api("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
            api("io.coil-kt.coil3:coil-compose:${property("coil")}")
            api("dev.chrisbanes.haze:haze:$haze")
            api("dev.chrisbanes.haze:haze-materials:$haze")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("coroutines")}")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("serialization")}")
        }
        // See `cmp` above.
        jvmMain.dependencies {
            api("org.jetbrains.compose.ui:ui-desktop:$cmp")
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation("junit:junit:4.13.2")
        }
    }
}

compose.resources {
    publicResClass = true
    // The same class :sharedUi generates, so the desktop's `Res` imports did not move.
    packageOfResClass = "com.music.bitchord.sharedui.resources"
    generateResClass = always
}
