package com.example.myapplication

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun legacyJournalEntriesDeserializeWithoutMetadata() {
        val savedJson = """[{"text":"Existing note","timestamp":1234}]"""
        val entryListType = object : TypeToken<List<JournalEntry>>() {}.type

        val entries = Gson().fromJson<List<JournalEntry>>(savedJson, entryListType)

        assertEquals(1, entries.size)
        assertEquals("Existing note", entries[0].text)
        assertEquals(1234L, entries[0].timestamp)
        assertNull(entries[0].id)
        assertNull(entries[0].emotionLabels)
        assertNull(entries[0].embedding)
        assertNull(entries[0].embeddingModel)
    }

    @Test
    fun journalMetadataRoundTripsThroughGson() {
        val entry = JournalEntry(
            text = "A hopeful day",
            timestamp = 5678L,
            id = "entry-1",
            emotionLabels = listOf(EmotionLabel("joy", 0.92f)),
            embedding = listOf(0.1f, 0.2f),
            embeddingModel = "test-model"
        )

        val restored = Gson().fromJson(Gson().toJson(entry), JournalEntry::class.java)

        assertEquals(entry, restored)
    }

    @Test
    fun snakeCaseMetadataFromBackendDeserializes() {
        val savedJson = """
            {
              "text":"A hopeful day",
              "timestamp":5678,
              "id":"entry-1",
              "emotion_labels":[{"label":"joy","score":0.92}],
              "embedding":[0.1,0.2],
              "embedding_model":"test-model"
            }
        """.trimIndent()

        val entry = Gson().fromJson(savedJson, JournalEntry::class.java)

        assertEquals(listOf(EmotionLabel("joy", 0.92f)), entry.emotionLabels)
        assertEquals(listOf(0.1f, 0.2f), entry.embedding)
        assertEquals("test-model", entry.embeddingModel)
    }

    @Test
    fun moodTrendUsesOnlyThePreviousSevenDays() {
        val now = 1_800_000_000_000L
        val entries = listOf(
            JournalEntry(
                text = "Recent",
                timestamp = now - 1_000,
                emotionLabels = listOf(EmotionLabel("joy", 0.9f))
            ),
            JournalEntry(
                text = "Old",
                timestamp = now - 8L * 24 * 60 * 60 * 1000,
                emotionLabels = listOf(EmotionLabel("sadness", 0.9f))
            )
        )

        val trend = summarizeMoodTrend(entries, now)

        assertEquals(
            "In the last 7 days, the app detected these recurring emotions: joy (1).",
            trend
        )
    }
}