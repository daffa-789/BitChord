import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/** Versions shared with the desktop modules; see the block in gradle.properties. */
fun property(name: String): String = providers.gradleProperty("bitchord.lib.$name").get()

val ktor = property("ktor")
val coroutines = property("coroutines")
val serialization = property("serialization")

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                // The network stack the lyrics providers and the YouTube Music client run on.
                api("io.ktor:ktor-client-core:$ktor")
                api("io.ktor:ktor-client-okhttp:$ktor")
                api("io.ktor:ktor-client-websockets:$ktor")
                api("io.ktor:ktor-client-content-negotiation:$ktor")
                api("io.ktor:ktor-serialization-kotlinx-json:$ktor")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines")
                api("org.jsoup:jsoup:${property("jsoup")}")
                // YouTube stream resolution: InnerTubeX's client catalog and cipher tiers, NewPipe as the fallback.
                api("com.github.MetrolistGroup.innertubex:innertubex:${property("innertubex")}")
                compileOnly("com.github.TeamNewPipe:NewPipeExtractor:${property("newpipe")}")
            }
        }
        jvmMain.get().dependsOn(jvmSharedMain)

        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("com.squareup.okhttp3:mockwebserver:5.3.2")
        }
    }
}
