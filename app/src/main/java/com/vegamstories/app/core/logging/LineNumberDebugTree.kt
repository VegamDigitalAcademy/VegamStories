package com.vegamstories.app.core.logging

import timber.log.Timber

/**
 * Debug-build Logcat tree. Tags each log with "Class:line" (e.g. `StoryRepositoryImpl:58`) so a
 * click in Logcat jumps to the code. Long messages (big JSON) are split by Timber automatically,
 * so nothing is cut off at Logcat's ~4 KB limit.
 */
class LineNumberDebugTree : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String =
        "${super.createStackElementTag(element)}:${element.lineNumber}"
}
