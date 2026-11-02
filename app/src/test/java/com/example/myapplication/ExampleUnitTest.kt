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
}