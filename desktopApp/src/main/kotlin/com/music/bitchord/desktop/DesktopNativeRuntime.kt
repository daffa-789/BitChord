package com.music.bitchord.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Unpacks what a jar-bundled native library needs out of the jar, once per install. */
internal object DesktopNativeRuntime {

    /** Where unpacked libraries live, alongside the desktop log. */
    private val home: Path by lazy {
        val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: "${System.getProperty("user.home")}/.local/share"
        Path.of(base, "bitchord", "native")
    }

    /**
     * Unpacks and loads a native library that travels in the jar.
     *
     * Windows has no way to load a library straight out of an archive, and the installer's own
     * directory is not writable for a per-user install, so each native helper is copied to a private
     * folder under the user's data home and loaded from there.
     */
    fun loadNative(name: String) {
        val fileName = System.mapLibraryName(name)
        System.load(unpack("/native/$fileName", fileName).toString())
    }

    /**
     * Copies a classpath resource to [name] under [home], skipping the copy when the file already
     * there is the same size and no older than the resource.
     *
     * Size alone is not enough: a rebuilt library can come out byte-for-byte the same length as the
     * one it replaces, and the stale copy was then loaded forever — a native fix that never ran.
     */
    private fun unpack(resource: String, name: String): Path {
        Files.createDirectories(home)
        val target = home.resolve(name)
        val stream = javaClass.getResourceAsStream(resource)
            ?: error("$resource is missing from this build")
        stream.use { input ->
            val connection = javaClass.getResource(resource)?.openConnection()
            val expected = connection?.contentLengthLong ?: -1L
            val builtAt = connection?.lastModified ?: 0L
            if (Files.exists(target) && expected > 0 && Files.size(target) == expected &&
                Files.getLastModifiedTime(target).toMillis() >= builtAt
            ) {
                return target
            }
            val partial = home.resolve("$name.partial")
            Files.copy(input, partial, StandardCopyOption.REPLACE_EXISTING)
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }
}
