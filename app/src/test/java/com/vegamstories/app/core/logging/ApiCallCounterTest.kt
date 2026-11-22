package com.vegamstories.app.core.logging

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The counts behind Settings → API calls (debug builds): per API, latest call first, kept on the phone. */
@RunWith(AndroidJUnit4::class)
class ApiCallCounterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `counts each API's calls, the latest call first`() {
        val counter = ApiCallCounter(context)
        counter.record("getStories", at = 1_000)
        counter.record("getCategories", at = 2_000)
        counter.record("getStories", at = 3_000)

        assertEquals(
            listOf(ApiCallCount("getStories", 2, 3_000), ApiCallCount("getCategories", 1, 2_000)),
            counter.counts.value,
        )
    }

    @Test
    fun `the counts go on after the app restarts, until Reset`() {
        ApiCallCounter(context).record("getPointer", at = 1_000)

        val afterRestart = ApiCallCounter(context)
        assertEquals(listOf(ApiCallCount("getPointer", 1, 1_000)), afterRestart.counts.value)

        afterRestart.reset()
        assertEquals(emptyList<ApiCallCount>(), afterRestart.counts.value)
        assertEquals(emptyList<ApiCallCount>(), ApiCallCounter(context).counts.value)
    }
}
