package com.example.myapplication

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


@Composable
fun JournalScreen(context: Context) {
    var journalEntries by remember { mutableStateOf(loadJournalEntries(context)) }
    var journalText by remember { mutableStateOf("") }
    var saveStatus by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Journal", style = MaterialTheme.typography.titleLarge)

        ReminderCard()

        TextField(
            value = journalText,
            onValueChange = { journalText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            placeholder = { Text("Write your journal entry here...") },
            singleLine = false
        )

        Button(
            onClick = {
                if (journalText.isNotBlank()) {
                    val newEntry = JournalEntry(
                        text = journalText,
                        timestamp = System.currentTimeMillis(),
                        id = UUID.randomUUID().toString()
                    )
                    journalEntries = journalEntries + newEntry
                    saveJournalEntries(context, journalEntries)
                    journalText = ""
                    saveStatus = "Saved on this device. Analyzing entry for memory."

                    coroutineScope.launch {
                        try {
                            val analysis = ChatbotApi.create().analyzeJournalEntry(
                                JournalAnalysisRequest(newEntry.text)
                            )
                            journalEntries = journalEntries.map { entry ->
                                if (entry.id == newEntry.id) {
                                    entry.copy(
                                        emotionLabels = analysis.emotions,
                                        embedding = analysis.embedding,
                                        embeddingModel = analysis.embeddingModel
                                    )
                                } else {
                                    entry
                                }
                            }
                            saveJournalEntries(context, journalEntries)

                            analysis.emotions.firstOrNull()?.let { top ->
                                addEmotionRecord(
                                    context,
                                    EmotionRecord(
                                        timestamp = newEntry.timestamp,
                                        emotion = top.label,
                                        intensity = 3,
                                        source = "journal"
                                    )
                                )
                            }
                            saveStatus = "Entry saved with emotion and memory data."
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            saveStatus = "Saved locally, but entry analysis failed: " +
                                (error.message ?: "backend unavailable")
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Entry")
        }
        saveStatus?.let { status ->
            Text(text = status, style = MaterialTheme.typography.bodySmall)
        }

        Text(text = "Previous Entries", style = MaterialTheme.typography.titleMedium)

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(journalEntries.sortedByDescending { it.timestamp }) { entry ->
                JournalEntryCard(entry)
            }
        }
    }
}

@Composable
fun JournalEntryCard(entry: JournalEntry) {
    val formattedTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(entry.timestamp))
    val topEmotion = entry.emotionLabels?.firstOrNull()?.label

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = entry.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (topEmotion != null) "$formattedTimestamp  •  $topEmotion" else formattedTimestamp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}