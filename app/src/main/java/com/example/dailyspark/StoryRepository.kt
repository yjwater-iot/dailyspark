package com.example.dailyspark

import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class StoryRepository(
    private val storyDao: StoryDao,
    private val storyGenerator: OfflineStoryGenerator = OfflineStoryGenerator(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    val stories: Flow<List<Story>> = storyDao.observeStories()

    suspend fun saveTranscript(transcript: String) {
        val cleanTranscript = transcript.trim()
        if (cleanTranscript.isNotEmpty() && !isAppPrompt(cleanTranscript)) {
            val now = clock()
            val sinceYesterday = now - TimeUnit.DAYS.toMillis(1)
            val rawTranscriptSinceYesterday = (storyDao.transcriptsSince(sinceYesterday) + cleanTranscript)
                .joinToString(separator = "\n")
            val draft = storyGenerator.generate(rawTranscriptSinceYesterday)

            storyDao.insert(
                Story(
                    transcript = cleanTranscript,
                    cleanedObservation = draft.cleanedObservation,
                    followUpQuestion = draft.followUpQuestion,
                    storySeed = draft.storySeed,
                    generatedStory = draft.generatedStory,
                    createdAtMillis = now
                )
            )
        }
    }

    suspend fun clearAllRecords() {
        storyDao.deleteAll()
    }

    private fun isAppPrompt(transcript: String): Boolean {
        val normalizedTranscript = transcript
            .trim()
            .trimEnd('.', '?', '!')
            .lowercase()

        return normalizedTranscript in PROMPT_TRANSCRIPTS
    }

    private companion object {
        val PROMPT_TRANSCRIPTS = setOf(
            "what small thing did you notice today",
            "tap talk and share what you noticed today"
        )
    }
}
