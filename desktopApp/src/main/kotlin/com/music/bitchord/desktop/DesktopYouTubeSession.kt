package com.music.bitchord.desktop

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

/**
 * The YouTube session shared by search and playback: who a stored cookie actually acts as.
 */
internal object DesktopYouTubeSession {
    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

    private val client = HttpClient(CIO)

    /**
     * Reads who the stored cookie actually acts as out of the signed-in music.youtube.com shell.
     */
    suspend fun adoptSessionScope(cookie: String): DesktopYouTubeAuth.Session? {
        val html = runCatching {
            client.get("https://music.youtube.com/") {
                header("User-Agent", WEB_USER_AGENT)
                header("Accept-Language", "en-US,en;q=0.9")
                header("Cookie", cookie)
                DesktopYouTubeAuth.signatureFor(cookie, DesktopYouTubeAuth.MUSIC_ORIGIN)
                    ?.let { header("Authorization", it) }
            }.bodyAsText()
        }.getOrElse {
            DesktopTrackLog.log("could not read the session scope: ${it.message}")
            return null
        }

        // The one value that must not be guessed.
        val signedIn = CONFIG_LOGGED_IN.find(html)?.groupValues?.get(1) == "true"
        if (!signedIn) {
            DesktopTrackLog.log("music.youtube.com served a signed-out shell; not scoping requests")
            return null
        }
        return DesktopYouTubeAuth.Session(
            cookie = cookie,
            authUser = CONFIG_SESSION_INDEX.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: "0",
            pageId = CONFIG_PAGE_ID.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() },
            // `<accountSyncId>||<sessionSyncId>`; only the first half names the account, and the
            // second changes on its own schedule.
            dataSyncId = CONFIG_DATASYNC_ID.find(html)?.groupValues?.get(1)
                ?.substringBefore("||")
                ?.takeIf { it.isNotBlank() },
        )
    }

    private val CONFIG_LOGGED_IN = Regex(""""LOGGED_IN"\s*:\s*(true|false)""")
    private val CONFIG_DATASYNC_ID = Regex(""""DATASYNC_ID"\s*:\s*"([^"]+)"""")
    private val CONFIG_PAGE_ID = Regex(""""DELEGATED_SESSION_ID"\s*:\s*"([^"]+)"""")
    private val CONFIG_SESSION_INDEX = Regex(""""SESSION_INDEX"\s*:\s*"?(\d+)""")
}
