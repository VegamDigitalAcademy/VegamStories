package com.vegamstories.app.domain.model

/** How the reader shows a story. Saved on the device and shared by every story. */
data class ReaderSettings(
    val scrollMode: ReaderScrollMode = ReaderScrollMode.PAGED,
    /** Stops the phone dimming and locking while a story is open. */
    val keepScreenOn: Boolean = true,
    val pageBackground: PageBackground = PageBackground.DEFAULT,
    /** The format the reader opens in when a story has it; the last one the user picked. */
    val preferredFormat: StoryFormat = StoryFormat.COMIC,
    /** Size of the story text in the Read tab. */
    val textSize: ReaderTextSize = ReaderTextSize.MEDIUM,
    /** Comic pages turn by themselves after [autoTurnSeconds] (Settings → Reading). */
    val autoTurnPages: Boolean = true,
    /** One of [AUTO_TURN_SECONDS_OPTIONS]. */
    val autoTurnSeconds: Int = 10,
    /** The corner of the first story's page has lifted once, to show pages can be turned. */
    val pageTurnHintShown: Boolean = false,
) {
    /** How long a comic page stays before turning by itself; null = auto-turn off. */
    val autoTurnMillis: Long? get() = if (autoTurnPages) autoTurnSeconds * 1_000L else null
}

/** The page times offered in Settings, in seconds. */
val AUTO_TURN_SECONDS_OPTIONS = listOf(5, 10, 15)

/** [PAGED] flips one page at a time sideways; [VERTICAL] scrolls all pages in one long strip. */
enum class ReaderScrollMode { PAGED, VERTICAL }

/** Colour behind the comic pages. [DEFAULT] follows the app theme. */
enum class PageBackground { DEFAULT, SEPIA, DARK }

/** Story text sizes in the Read tab, smallest first. */
enum class ReaderTextSize { SMALL, MEDIUM, LARGE, EXTRA_LARGE }
