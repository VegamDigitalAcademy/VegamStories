package com.vegamstories.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records what runs when someone opens the app, scrolls Home, opens a story and turns its pages,
 * for the app's Baseline Profile (see build.gradle.kts). Needs internet, for the stories.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()

        // A fresh install asks for the story language first.
        device.wait(Until.findObject(By.text("Let's go")), 3_000)?.let { go ->
            device.findObject(By.text("English"))?.click()
            go.click()
        }
        device.wait(Until.hasObject(By.text("VEGAM STORIES")), 10_000)
        device.waitForIdle()

        // Scroll Home, and back up.
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(width / 2, height * 3 / 4, width / 2, height / 4, 20)
        device.waitForIdle()
        device.swipe(width / 2, height / 4, width / 2, height * 3 / 4, 20)
        device.waitForIdle()

        // Open the first story in the grid: its card grows into the reader, and the cover turns.
        device.click(width / 4, height * 3 / 5)
        device.waitForIdle()
        Thread.sleep(3_500)

        // Turn a few pages, by tapping the right side and by swiping.
        repeat(3) {
            device.click(width * 9 / 10, height / 2)
            Thread.sleep(1_400)
        }
        repeat(2) {
            device.swipe(width * 4 / 5, height / 2, width / 5, height / 2, 15)
            Thread.sleep(1_400)
        }

        // Back to Home: the reader shrinks into its card.
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(1_000)
    }

    private companion object {
        const val PACKAGE = "com.vegamstories.app"
    }
}
