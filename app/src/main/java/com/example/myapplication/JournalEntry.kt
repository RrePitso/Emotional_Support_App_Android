package com.example.myapplication

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

data class EmotionLabel(
    val label: String,
    val score: Float
)

data class JournalEntry(
    val text: String,
    val timestamp: Long,
    val id: String? = null,
    @SerializedName(value = "emotion_labels", alternate = ["emotionLabels"])
    val emotionLabels: List<EmotionLabel>? = null,
    val embedding: List<Float>? = null,
    @SerializedName(value = "embedding_model", alternate = ["embeddingModel"])
    val embeddingModel: String? = null
)

private const val JOURNAL_PREFERENCES = "JournalApp"
private const val JOURNAL_ENTRIES_KEY = "journalEntries"

internal fun loadJournalEntries(context: Context): List<JournalEntry> {
    val preferences = context.getSharedPreferences(JOURNAL_PREFERENCES, Context.MODE_PRIVATE)
    val savedData = preferences.getString(JOURNAL_ENTRIES_KEY, "[]")
    val type = object : TypeToken<List<JournalEntry>>() {}.type
    return Gson().fromJson<List<JournalEntry>>(savedData, type) ?: emptyList()
}

internal fun saveJournalEntries(context: Context, entries: List<JournalEntry>) {
    val preferences = context.getSharedPreferences(JOURNAL_PREFERENCES, Context.MODE_PRIVATE)
    preferences.edit().putString(JOURNAL_ENTRIES_KEY, Gson().toJson(entries)).apply()
}

internal fun summarizeMoodTrend(
    entries: List<JournalEntry>,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val cutoff = nowMillis - 7L * 24 * 60 * 60 * 1000
    val counts = entries
        .asSequence()
        .filter { it.timestamp in cutoff..nowMillis }
        .flatMap { it.emotionLabels.orEmpty().asSequence() }
        .groupingBy { it.label.lowercase() }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(3)

    if (counts.isEmpty()) return "No recent journal mood data is available."
    val trend = counts.joinToString { "${it.key} (${it.value})" }
    return "In the last 7 days, the app detected these recurring emotions: $trend."
}