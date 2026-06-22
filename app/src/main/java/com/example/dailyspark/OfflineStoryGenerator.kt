package com.example.dailyspark

class OfflineStoryGenerator {
    fun generate(rawTranscriptSinceYesterday: String): StoryDraft {
        val cleanedObservation = cleanObservation(rawTranscriptSinceYesterday)
        val followUpQuestion = buildFollowUpQuestion(cleanedObservation)
        val storySeed = buildStorySeed(cleanedObservation)
        val generatedStory = buildGeneratedStory(cleanedObservation)

        return StoryDraft(
            cleanedObservation = cleanedObservation,
            followUpQuestion = followUpQuestion,
            storySeed = storySeed,
            generatedStory = generatedStory
        )
    }

    private fun cleanObservation(rawTranscript: String): String {
        val cleaned = rawTranscript
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(FILLER_WORDS_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim(' ', ',', '.', ';', ':', '-')

        return if (cleaned.isBlank()) {
            "A quiet moment stood out today."
        } else {
            cleaned.replaceFirstChar { firstChar ->
                if (firstChar.isLowerCase()) firstChar.titlecase() else firstChar.toString()
            }.let { sentence ->
                if (sentence.last() in ENDING_PUNCTUATION) sentence else "$sentence."
            }
        }
    }

    private fun buildFollowUpQuestion(cleanedObservation: String): String {
        val focus = cleanedObservation
            .removeSuffix(".")
            .removeSuffix("!")
            .removeSuffix("?")
            .take(80)
            .trim()
            .ifBlank { "that moment" }

        return "What changed because you noticed $focus?"
    }

    private fun buildStorySeed(cleanedObservation: String): String {
        val compactObservation = cleanedObservation
            .split(WHITESPACE_REGEX)
            .take(28)
            .joinToString(" ")
            .trim()
            .ifBlank { "A small detail asks to be noticed." }

        val seed = "A character pauses over this detail: $compactObservation They follow it through one ordinary place and discover a small choice waiting for them."
        return seed.limitToWords(MAX_STORY_SEED_WORDS)
    }

    private fun buildGeneratedStory(cleanedObservation: String): String {
        val detail = cleanedObservation
            .trim()
            .removeSuffix(".")

        return "Here is your DailySpark story. Today, you noticed $detail. That small spark became a bright reminder to pause, wonder, and carry a little more attention into the rest of your day."
    }

    private fun String.limitToWords(maxWords: Int): String {
        val words = trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        return if (words.size <= maxWords) {
            trim()
        } else {
            words.take(maxWords).joinToString(" ").trimEnd(',', ';', ':', '-') + "…"
        }
    }

    private companion object {
        const val MAX_STORY_SEED_WORDS = 150
        val WHITESPACE_REGEX = Regex("\\s+")
        val FILLER_WORDS_REGEX = Regex("\\b(?:um+|uh+|like|you know)\\b", RegexOption.IGNORE_CASE)
        val ENDING_PUNCTUATION = setOf('.', '!', '?')
    }
}

data class StoryDraft(
    val cleanedObservation: String,
    val followUpQuestion: String,
    val storySeed: String,
    val generatedStory: String
)
