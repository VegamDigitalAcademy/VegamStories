package com.vegam.myapplication.domain

import com.vegam.myapplication.domain.model.YouTubeLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeLinksTest {

    private val id = "M7lc1UVf-VE"

    @Test
    fun `reads the id from every common video link`() {
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?feature=share&v=$id&t=30s",
            "https://youtu.be/$id",
            "https://youtu.be/$id?si=abc123",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id",
            "https://www.youtube.com/live/$id",
            "  $id  ",
        ).forEach { assertEquals(it, id, YouTubeLinks.videoIdOf(it)) }
    }

    @Test
    fun `channel, playlist and search links are not single videos`() {
        listOf(
            "https://www.youtube.com/@VegamStories",
            "https://www.youtube.com/playlist?list=PL1234567890",
            "https://www.youtube.com/results?search_query=telugu+stories",
            "",
        ).forEach { assertNull(it, YouTubeLinks.videoIdOf(it)) }
    }
}
