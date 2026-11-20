package com.vegamstories.app.core.logging

import com.vegamstories.app.data.remote.dto.SetStoryLikeRequest
import com.vegamstories.app.domain.model.Bookmark
import com.vegamstories.app.domain.model.StoryFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How data under a log line is printed: pretty JSON, cut short where it's long. */
class LogDataTest {

    @Test
    fun `json text is pretty printed`() {
        assertEquals(
            "{\n  \"id\": \"moral-1\",\n  \"pages\": [\n    1,\n    2\n  ]\n}",
            LogData.render("""{"id":"moral-1","pages":[1,2]}"""),
        )
    }

    @Test
    fun `plain text stays as it is`() {
        assertEquals("3 stories, tab ALL", LogData.render("3 stories, tab ALL"))
    }

    @Test
    fun `the app's own classes are printed field by field`() {
        val text = LogData.render(Bookmark("moral-1", "te", StoryFormat.TEXT, position = 3, offset = 40))
        assertTrue(text, text.contains("\"storyId\": \"moral-1\""))
        assertTrue(text, text.contains("\"format\": \"TEXT\""))
        assertTrue(text, text.contains("\"offset\": 40"))
    }

    @Test
    fun `server objects use the names they have on the wire`() {
        val text = LogData.render(SetStoryLikeRequest(storyId = "moral-1", deviceId = "abc", liked = true))
        assertEquals("{\n  \"p_story_id\": \"moral-1\",\n  \"p_device_id\": \"abc\",\n  \"p_liked\": true\n}", text)
    }

    @Test
    fun `long text and long lists are cut short`() {
        val text = LogData.render(mapOf("body" to "x".repeat(1_000), "pages" to (1..50).toList()))
        assertTrue(text, text.contains("… (+500 characters)"))
        assertTrue(text, text.contains("\"… 40 more\""))
        val response = LogData.render("[" + (1..30).joinToString { """{"id":$it}""" } + "]")
        assertTrue(response, response.contains("\"… 20 more\""))
    }

    @Test
    fun `a huge block is capped`() {
        val text = LogData.render((1..5_000).associate { "key$it" to "value $it" })
        assertTrue(text.length < LogData.MAX_CHARS + 200)
    }

    @Test
    fun `an object inside itself is shown once`() {
        val first = Node("first")
        first.next = Node("second").also { it.next = first }
        val text = LogData.render(first)
        assertTrue(text, text.contains("\"name\": \"second\""))
        assertTrue(text, text.contains("(the same object as above)"))
    }

    @Test
    fun `times and durations read well`() {
        assertEquals("never", logTime(null))
        assertEquals("850 ms", logDuration(850))
        assertEquals("42 s", logDuration(42_000))
        assertEquals("3 min 12 s", logDuration(192_000))
        assertEquals("1 h 05 min", logDuration(3_900_000))
    }

    private class Node(val name: String) {
        var next: Node? = null
    }
}
