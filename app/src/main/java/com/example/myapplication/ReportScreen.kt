package com.example.myapplication

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Composable
fun ReportScreen(context: Context) {
    val records = remember { loadEmotionRecords(context) }
    val week = remember { buildDayStats(records, days = 7) }
    val previousWeek = remember { buildDayStats(records, days = 7, endDaysAgo = 7) }
    val forecast = remember { forecastTomorrow(records) }

    val weekCutoff = week.first().dayStart
    val weekRecords = records.filter { it.timestamp >= weekCutoff }
    val topEmotions = weekRecords.groupingBy { it.emotion }.eachCount()
        .entries.sortedByDescending { it.value }.take(5)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Weekly emotion report", style = MaterialTheme.typography.titleLarge)

        if (weekRecords.isEmpty()) {
            Text(
                "No emotion data yet. Chat with the AI or write a journal entry, " +
                    "and your patterns will appear here.",
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        // Summary
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("This week", style = MaterialTheme.typography.titleSmall)
                Text(summaryText(week, previousWeek, topEmotions.firstOrNull()?.key))
            }
        }

        // Chart
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Daily mood (up = positive, down = negative)",
                    style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                MoodBarChart(week)
                Row(Modifier.fillMaxWidth()) {
                    val fmt = SimpleDateFormat("EEE", Locale.getDefault())
                    week.forEach { day ->
                        Text(
                            text = fmt.format(Date(day.dayStart)),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // Top emotions
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Most frequent emotions", style = MaterialTheme.typography.titleSmall)
                val maxCount = topEmotions.maxOf { it.value }.toFloat()
                topEmotions.forEach { (emotion, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(emotion.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.width(110.dp),
                            style = MaterialTheme.typography.bodySmall)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(count / maxCount)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Text("  $count", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Forecast
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Looking ahead", style = MaterialTheme.typography.titleSmall)
                if (forecast == null) {
                    Text("Log your emotions on at least 3 different days to unlock a gentle forecast.")
                } else {
                    val outlook = when {
                        forecast.score > 0.25f -> "a lighter day"
                        forecast.score < -0.25f -> "a heavier day"
                        else -> "a fairly steady day"
                    }
                    Text("${forecast.dayName} looks like it may be $outlook.")
                    Text(
                        if (forecast.usedWeekdayPattern)
                            "Based on your last few days and how your past ${forecast.dayName}s felt."
                        else "Based on your last few days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (forecast.score < -0.25f) {
                        Text("Consider planning something small and kind for yourself.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("This is a pattern-based guess, not a prediction.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MoodBarChart(days: List<DayStat>) {
    val positive = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val axis = MaterialTheme.colorScheme.outline

    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        val width = size.width
        val height = size.height
        val mid = height / 2f
        val slot = width / days.size
        val barWidth = slot * 0.55f

        days.forEachIndexed { index, day ->
            val score = day.avgScore
            if (score != null) {
                val barHeight = max(abs(score) * (mid - 8f), 4f)
                val left = index * slot + (slot - barWidth) / 2f
                val top = if (score >= 0f) mid - barHeight else mid
                drawRect(
                    color = if (score >= 0f) positive else negative,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight)
                )
            }
        }
        drawLine(axis, Offset(0f, mid), Offset(width, mid), strokeWidth = 2f)
    }
}

private fun summaryText(week: List<DayStat>, prev: List<DayStat>, topEmotion: String?): String {
    val fmt = SimpleDateFormat("EEEE", Locale.getDefault())
    val logged = week.filter { it.avgScore != null }
    if (logged.isEmpty()) return "No data this week."

    val best = logged.maxByOrNull { it.avgScore!! }!!
    val hardest = logged.minByOrNull { it.avgScore!! }!!
    val parts = mutableListOf<String>()
    topEmotion?.let { parts.add("Your most frequent emotion was $it.") }
    parts.add("Best day: ${fmt.format(Date(best.dayStart))}.")
    if (hardest.dayStart != best.dayStart) {
        parts.add("Hardest day: ${fmt.format(Date(hardest.dayStart))}.")
    }

    val thisAvg = logged.map { it.avgScore!! }.average()
    val prevLogged = prev.filter { it.avgScore != null }
    if (prevLogged.isNotEmpty()) {
        val diff = thisAvg - prevLogged.map { it.avgScore!! }.average()
        parts.add(
            when {
                diff > 0.1 -> "Your mood is trending up compared with last week."
                diff < -0.1 -> "Your mood is a bit lower than last week."
                else -> "Your mood is similar to last week."
            }
        )
    }
    return parts.joinToString(" ")
}