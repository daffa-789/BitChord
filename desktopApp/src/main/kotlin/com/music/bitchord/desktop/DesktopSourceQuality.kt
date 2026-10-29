package com.music.bitchord.desktop

import java.util.Locale

/**
 * The quality rungs a source can report a stream in, read out of whatever text the source itself
 * uses — a codec name, a MIME type, a bitrate label, a file extension.
 */
internal object DesktopSourceQuality {
    internal const val LOSSLESS = "LOSSLESS"
    internal const val DOLBY = "DOLBY"
    internal const val HIGH = "HIGH"
    internal const val LOW = "LOW"

    /** The rung [text] describes, or null when it says nothing about quality. */
    internal fun tier(text: String): String? {
        val value = text.uppercase(Locale.ROOT)
        return when {
            "ATMOS" in value || "DOLBY" in value || "EAC3" in value || "E-AC-3" in value -> DOLBY
            "LOSSLESS" in value || "FLAC" in value || "ALAC" in value ||
                "HI-RES" in value || "HIRES" in value || "24-BIT" in value -> LOSSLESS
            Regex("\\b(320|256|192)\\s*KBPS\\b").containsMatchIn(value) || "HIGH" in value -> HIGH
            Regex("\\b(64|96|128)\\s*KBPS\\b").containsMatchIn(value) || "LOW" in value -> LOW
            else -> null
        }
    }

    /** How high [value] sits on the ladder, for picking the best of several offers. */
    internal fun rank(value: String): Int = when (tier(value)) {
        DOLBY -> 4
        LOSSLESS -> 3
        HIGH -> 2
        LOW -> 1
        else -> 0
    }
}
