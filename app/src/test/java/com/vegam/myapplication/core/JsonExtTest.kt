package com.vegam.myapplication.core

import com.vegam.myapplication.core.util.toJson
import com.vegam.myapplication.core.util.toPrettyJson
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonExtTest {

    @Test
    fun `objects and arrays are pretty printed`() {
        val pretty = """{"id":"moral-1","pages":[1,2]}""".toJson()
        assertEquals(
            """
            {
                "id": "moral-1",
                "pages": [
                    1,
                    2
                ]
            }
            """.trimIndent(),
            pretty,
        )
    }

    @Test
    fun `telugu text stays readable`() {
        assertTrue("""{"title":"నిజాయితీ బహుమతి"}""".toJson().contains("నిజాయితీ బహుమతి"))
    }

    @Test
    fun `non json text comes back unchanged`() {
        listOf("", "hello", "<html></html>", "{broken").forEach { text ->
            assertSame(text, text.toJson())
        }
    }

    @Serializable
    data class Sample(val id: String, val episode: Int)

    @Test
    fun `serializable objects become pretty json`() {
        assertEquals("{\n    \"id\": \"moral-1\",\n    \"episode\": 1\n}", Sample("moral-1", 1).toPrettyJson())
    }
}
