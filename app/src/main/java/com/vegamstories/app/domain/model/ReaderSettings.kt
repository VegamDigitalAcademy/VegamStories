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
)

/** [PAGED] flips one page at a time sideways; [VERTICAL] scrolls all pages in one long strip. */
enum class ReaderScrollMode { PAGED, VERTICAL }

/** Colour behind the comic pages. [DEFAULT] follows the app theme. */
enum class PageBackground { DEFAULT, SEPIA, DARK }

/** Story text sizes in the Read tab, smallest first. */
enum class ReaderTextSize { SMALL, MEDIUM, LARGE, EXTRA_LARGE }
