package com.vegamstories.app.domain

import com.vegamstories.app.domain.model.findMoral
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The moral for the end-of-story card: the labelled line near the end of the text. */
class MoralTest {

    @Test
    fun `the moral is found in every language the stories come in`() {
        assertEquals("Together, any difficulty becomes easy.", findMoral(listOf("Once upon a time…", "Moral: Together, any difficulty becomes easy.")))
        assertEquals("కలిసి ఉంటే ఏ కష్టమైనా సులభమే.", findMoral(listOf("ఒకప్పుడు…", "నీతి: కలిసి ఉంటే ఏ కష్టమైనా సులభమే.")))
        assertEquals("ମିଳିମିଶି ରହିଲେ ସବୁ କଷ୍ଟ ସହଜ ହୁଏ।", findMoral(listOf("ଏକଦା…", "ନୀତି: ମିଳିମିଶି ରହିଲେ ସବୁ କଷ୍ଟ ସହଜ ହୁଏ।")))
    }

    @Test
    fun `labels are matched loosely, the text is kept as written`() {
        assertEquals("Honesty pays.", findMoral(listOf("…", "  MORAL OF THE STORY - Honesty pays.  ")))
        assertEquals("Be kind.", findMoral(listOf("…", "moral – Be kind.", "The End")))
    }

    @Test
    fun `no labelled line, or an empty one, means no moral`() {
        assertNull(findMoral(listOf("Once upon a time: a king.", "He ruled well.")))
        assertNull(findMoral(listOf("Moral:")))
        assertNull(findMoral(emptyList()))
        // Only the end of the text counts: a "moral" early on is part of the story.
        assertNull(findMoral(listOf("Moral: not this.", "a", "b", "c")))
    }
}
