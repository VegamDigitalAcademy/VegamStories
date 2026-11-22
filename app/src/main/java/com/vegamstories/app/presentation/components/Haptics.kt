package com.vegamstories.app.presentation.components

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Small vibrations in the reader. They follow the phone's own "Touch feedback" setting, so
 * there's no switch for them in the app.
 */

/** Liking or bookmarking: a firm tick when it turns on, a softer one when it turns off. */
fun HapticFeedback.toggle(on: Boolean) =
    performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

/** A page turned by the reader's finger: the lightest tick, as it happens on every page. */
fun HapticFeedback.pageTurn() = performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
