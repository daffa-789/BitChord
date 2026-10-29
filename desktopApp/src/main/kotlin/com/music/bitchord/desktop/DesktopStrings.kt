package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The app's own copy, in whichever language is in force.
 *
 * The catalogues are Android's own, copied in at build time from its `res` values folders,
 * so a translation written once is read by both targets and desktop never drifts into its own
 * wording for a string Android already has.
 *
 * A key with no translation in the chosen language falls back to English rather than showing the
 * key: a half-translated locale should read as mostly-English, not as a debug build.
 */
object DesktopStrings {

    /**
     * The languages the picker offers, by IETF tag.
     *
     * Two, deliberately. The app is written and maintained in these two, so these are the two it
     * can promise are complete: a third option that is 60% translated reads as a broken app rather
     * than as a translated one. The other catalogues still ship — Android reads them and nothing
     * here deletes them — they are simply not offered.
     *
     * There is no "System default" row: with only two languages, the system's own choice is
     * resolved once at startup and shown as the language it actually picked, so the list never
     * shows a selection that is not in it.
     */
    val available: List<DesktopLanguage> = listOf(
        DesktopLanguage("en", "English"),
        DesktopLanguage("id", "Bahasa Indonesia"),
    )

    private val english: Map<String, String> by lazy { catalogue("en") }

    /**
     * Declared before anything reads the catalogues: object properties initialise top to bottom,
     * and `unescape` is called while `init` below is still loading its first file. It used to sit
     * at the bottom of the object, which left it null for that first read — and the exception it
     * threw was caught by `load`, so *every* catalogue came back empty and the whole app stayed
     * English whatever the language picker said.
     */
    private val UNICODE_ESCAPE = Regex("""\\u([0-9a-fA-F]{4})""")

    @Volatile
    private var current: Map<String, String> = emptyMap()

    private val _language = MutableStateFlow(DesktopPersistence().string(KEY_LANGUAGE))

    /** The stored choice: a tag, or blank for the system's own language. */
    val language: StateFlow<String> = _language

    init {
        current = catalogue(resolvedTag())
    }

    /**
     * Which catalogue is actually in use, after the system default is resolved.
     *
     * Always one of [available]. A profile stored before the list was cut to two may name a
     * language that is no longer offered; that lands on the closest offered language rather than
     * running a catalogue the picker cannot show or undo.
     */
    fun resolvedTag(): String {
        val chosen = _language.value
        if (chosen.isNotBlank()) return offeredTag(chosen)
        return offeredTag(
            com.music.bitchord.data.LocaleTags.normalizeAppTag(Locale.getDefault().toLanguageTag()),
        )
    }

    /** [tag] if the picker offers it, else the offered tag sharing its language, else English. */
    private fun offeredTag(tag: String): String {
        available.firstOrNull { it.tag.equals(tag, ignoreCase = true) }?.let { return it.tag }
        val language = tag.substringBefore('-').lowercase(Locale.ROOT)
        available.firstOrNull { it.tag.substringBefore('-').lowercase(Locale.ROOT) == language }
            ?.let { return it.tag }
        return FALLBACK_TAG
    }

    /**
     * One language, both layers.
     *
     * `<tag>.xml` is Android's own catalogue, copied in at build time. `desktop-<tag>.xml` holds
     * the copy only this target has — its window chrome, the source editor, output precision — and
     * is laid over the top, so a desktop key never collides with an Android one.
     */
    private fun catalogue(tag: String): Map<String, String> = load(tag) + load("desktop-$tag")

    fun setLanguage(tag: String) {
        val resolved = offeredTag(tag)
        DesktopPersistence().saveString(KEY_LANGUAGE, resolved)
        _language.value = resolved
        current = catalogue(resolved)
    }

    /**
     * The label shown for the language in force.
     *
     * With no stored choice the app follows the system, so this names the language that resolution
     * actually landed on rather than a "System default" row the picker no longer has.
     */
    fun languageLabel(): String {
        val tag = resolvedTag()
        return available.firstOrNull { it.tag.equals(tag, ignoreCase = true) }?.label
            ?: available.first().label
    }

    /**
     * The string for [key], or [fallback] when no catalogue has one.
     *
     * The fallback is the English text the call site would otherwise have held literally, so an
     * untranslated key still reads as the sentence it was written as.
     */
    operator fun get(key: String, fallback: String = key): String =
        current[key] ?: english[key] ?: fallback

    /** As [get], with Android's `%1$s`-style positional arguments filled in. */
    fun format(key: String, vararg arguments: Any?, fallback: String = key): String =
        runCatching { String.format(get(key, fallback), *arguments) }.getOrDefault(get(key, fallback))

    private fun load(tag: String): Map<String, String> = runCatching {
        val stream = DesktopStrings::class.java.getResourceAsStream("/strings/$tag.xml")
            ?: return@runCatching emptyMap()
        val document = stream.use { input ->
            // `newInstance()` consults the JAXP service providers on the classpath, and one of the
            // libraries this app ships puts its own factory there that cannot parse these files —
            // which silently emptied every catalogue and left the whole app in English however the
            // language picker was set. `newDefaultInstance()` asks the JDK for its own parser and
            // skips the providers entirely.
            val factory = runCatching { DocumentBuilderFactory.newDefaultInstance() }
                .getOrElse { DocumentBuilderFactory.newInstance() }
            factory.apply { isNamespaceAware = false }.newDocumentBuilder().parse(input)
        }
        val nodes = document.getElementsByTagName("string")
        buildMap {
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
                put(name, unescape(node.textContent.orEmpty()))
            }
        }
    }.getOrElse { failure ->
        // A catalogue that cannot be read used to vanish without a word, which is how the app ran
        // with every language falling back to English for months. Say so instead.
        DesktopTrackLog.log("strings: catalogue $tag could not be read: ${failure.message}")
        emptyMap()
    }

    /**
     * A catalogue value as the UI should read it.
     *
     * Android escapes an apostrophe for its own resource parser, and a `\uXXXX` can survive a
     * round trip out of source into a catalogue — either of which would otherwise be drawn
     * literally, which is how "Don't" once reached the screen as "Don\u2019t".
     */
    internal fun unescape(raw: String): String {
        val decoded = UNICODE_ESCAPE.replace(raw) { it.groupValues[1].toInt(16).toChar().toString() }
        return decoded.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
    }

    internal const val KEY_LANGUAGE = "app_language"

    /** What the app falls back to when neither the stored choice nor the system names an offered language. */
    private const val FALLBACK_TAG = "en"
}

/** One entry in the language picker. */
data class DesktopLanguage(val tag: String, val label: String)
