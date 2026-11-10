// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
    }
    dependencies {
        /*
         * Overrides the D8/R8 that AGP 8.10.1 bundles (8.10.9). Gradle's conflict
         * resolution picks the higher version, so this is the whole mechanism.
         *
         * 8.10.x miscompiles `NowPlayingScreen`, which is large enough — 36
         * parameters, ~5,500 dex instructions, registers past v287 — to hit a
         * register-allocation bug in debug-mode dexing.
         * R8 8.13.23 fixes the register allocation bug and reads Kotlin 2.3 @Metadata.
         */
        classpath("com.android.tools:r8:8.13.23")
    }
}

extra["bitchordVersionCode"] = 32
extra["bitchordVersionName"] = "1.8.32"
extra["bitchordBetaSuffix"] = ""

plugins {
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.3.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
    id("org.jetbrains.compose") version "1.10.3" apply false
}
