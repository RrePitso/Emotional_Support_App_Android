package com.example.myapplication

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class EmotionRecord(
    val timestamp: Long,
    val emotion: String,
    val intensity: Int,        // 1..5
    val source: String,        // "chat" or "journal"
    val trigger: String = ""
)

data class DayStat(
    val dayStart: Long,
    val avgScore: Float?,      // -1..1, null = no data that day
    val count: Int,
    val topEmotion: String?
)

data class Forecast(val score: Float, val dayName: String, val usedWeekdayPattern: Boolean)

private const val EMOTION_PREFS = "EmotionLog"
private const val EMOTION_KEY = "records"
private const val MAX_RECORDS = 2000

private val lock = Any()

internal fun loadEmotionRecords(context: Context): List<EmotionRecord> {
    val prefs = context.getSharedPreferences(EMOTION_PREFS, Context.MODE_PRIVATE)
    val json = prefs.getString(EMOTION_KEY, "[]")
    val type = object : TypeToken<List<EmotionRecord>>() {}.type
    return try {
        Gson().fromJson<List<EmotionRecord>>(json, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

internal fun addEmotionRecord(context: Context, record: EmotionRecord) {
    synchronized(lock) {
        val updated = (loadEmotionRecords(context) + record).takeLast(MAX_RECORDS)
        context.getSharedPreferences(EMOTION_PREFS, Context.MODE_PRIVATE)
            .edit().putString(EMOTION_KEY, Gson().toJson(updated)).apply()
    }
}

/** -1 (very negative) .. +1 (very positive) */
internal fun emotionValence(emotion: String): Float = when (emotion.lowercase()) {
    "joy", "love" -> 1.0f
    "gratitude", "excitement", "pride" -> 0.9f
    "amusement" -> 0.8f
    "admiration", "optimism" -> 0.7f
    "caring", "relief" -> 0.6f
    "approval" -> 0.5f
    "curiosity" -> 0.3f
    "realization", "desire" -> 0.2f
    "surprise" -> 0.1f
    "neutral" -> 0.0f
    "confusion" -> -0.2f
    "disapproval" -> -0.4f
    "annoyance", "embarrassment" -> -0.5f
    "nervousness", "disappointment", "remorse" -> -0.6f
    "disgust" -> -0.7f
    "fear", "anger" -> -0.8f
    "sadness" -> -0.9f
    "grief" -> -1.0f
    else -> 0.0f
}

internal fun recordScore(r: EmotionRecord): Float =
    emotionValence(r.emotion) * (r.intensity.coerceIn(1, 5) / 5f)

private fun startOfDay(millis: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** One DayStat per day, oldest first. endDaysAgo = 0 ends today, 7 ends a week ago. */
internal fun buildDayStats(
    records: List<EmotionRecord>,
    days: Int,
    endDaysAgo: Int = 0,
    now: Long = System.currentTimeMillis()
): List<DayStat> {
    val result = mutableListOf<DayStat>()
    for (offset in (endDaysAgo + days - 1) downTo endDaysAgo) {
        val c = Calendar.getInstance()
        c.timeInMillis = startOfDay(now)
        c.add(Calendar.DAY_OF_YEAR, -offset)
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 1)
        val end = c.timeInMillis
        val dayRecords = records.filter { it.timestamp in start until end }
        val avg = if (dayRecords.isEmpty()) null
        else dayRecords.map { recordScore(it) }.average().toFloat()
        val top = dayRecords.groupingBy { it.emotion }.eachCount()
            .maxByOrNull { it.value }?.key
        result.add(DayStat(start, avg, dayRecords.size, top))
    }
    return result
}

private fun dayOfWeek(millis: Long): Int {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    return c.get(Calendar.DAY_OF_WEEK)
}

/** Blend of the last 3 logged days and the same weekday's history. Needs 3+ days of data. */
internal fun forecastTomorrow(
    records: List<EmotionRecord>,
    now: Long = System.currentTimeMillis()
): Forecast? {
    val logged = buildDayStats(records, days = 90, now = now).filter { it.avgScore != null }
    if (logged.size < 3) return null

    val recent = logged.takeLast(3).map { it.avgScore!! }.average()
    val tomorrow = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, 1)
    }
    val sameWeekday = logged
        .filter { dayOfWeek(it.dayStart) == tomorrow.get(Calendar.DAY_OF_WEEK) }
        .map { it.avgScore!! }

    val usePattern = sameWeekday.size >= 2
    val predicted = if (usePattern) 0.6 * recent + 0.4 * sameWeekday.average() else recent
    val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(tomorrow.timeInMillis))
    return Forecast(predicted.toFloat(), dayName, usePattern)
}