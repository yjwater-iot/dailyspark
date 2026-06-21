package com.example.dailyspark

import kotlinx.coroutines.flow.Flow

class StoryRepository(private val storyDao: StoryDao) {
    val stories: Flow<List<Story>> = storyDao.observeStories()

    suspend fun saveTranscript(transcript: String) {
        val cleanTranscript = transcript.trim()
        if (cleanTranscript.isNotEmpty()) {
            storyDao.insert(
                Story(
                    transcript = cleanTranscript,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        }
    }
}
