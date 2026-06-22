package com.example.dailyspark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineStoryGeneratorTest {
    private val generator = OfflineStoryGenerator()

    @Test
    fun generate_cleansObservationAndCreatesQuestionAndSeed() {
        val draft = generator.generate(" um  the rain sounded bright on the windows\nlike the cat watched quietly ")

        assertEquals("The rain sounded bright on the windows the cat watched quietly.", draft.cleanedObservation)
        assertEquals(
            "What changed because you noticed The rain sounded bright on the windows the cat watched quietly?",
            draft.followUpQuestion
        )
        assertTrue(draft.storySeed.contains("A character pauses over this detail:"))
        assertTrue(draft.generatedStory.contains("Today, you noticed The rain sounded bright on the windows the cat watched quietly"))
        assertTrue(draft.storySeed.split(Regex("\\s+")).size <= 150)
    }

    @Test
    fun generate_usesFallbackForBlankTranscript() {
        val draft = generator.generate("  \n\t ")

        assertEquals("A quiet moment stood out today.", draft.cleanedObservation)
        assertEquals("What changed because you noticed A quiet moment stood out today?", draft.followUpQuestion)
        assertTrue(draft.storySeed.split(Regex("\\s+")).size <= 150)
    }
}
