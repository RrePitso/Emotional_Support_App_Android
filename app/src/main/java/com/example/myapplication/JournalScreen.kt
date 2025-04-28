package com.example.myapplication

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*


@Composable
fun JournalScreen(context: Context) {
    // SharedPreferences setup
    val sharedPreferences = remember { context.getSharedPreferences("JournalApp", Context.MODE_PRIVATE) }
    val gson = Gson()

    // Load saved journal entries from SharedPreferences
    var journalEntries by remember {
        val savedData = sharedPreferences.getString("journalEntries", "[]")
        val type = object : TypeToken<List<JournalEntry>>() {}.type
        mutableStateOf(gson.fromJson(savedData, type) ?: emptyList<JournalEntry>())
    }

    var journalText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp) // Add spacing between elements
    ) {
        // Title
        Text(
            text = "Journal",
            style = MaterialTheme.typography.titleLarge
        )

        // Styled TextField
        TextField(
            value = journalText,
            onValueChange = { journalText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp), // Ensure enough height for multi-line input
            placeholder = { Text("Write your journal entry here...") },
            singleLine = false // Allow multi-line input
        )

        // Save Button
        Button(
            onClick = {
                if (journalText.isNotBlank()) {
                    val newEntry = JournalEntry(journalText, System.currentTimeMillis())
                    journalEntries = journalEntries + newEntry // Add the new entry to the list

                    // Save entries to SharedPreferences
                    with(sharedPreferences.edit()) {
                        putString("journalEntries", gson.toJson(journalEntries))
                        apply()
                    }

                    journalText = "" // Clear the input field
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Entry")
        }

        // Display Saved Entries
        Text(
            text = "Previous Entries",
            style = MaterialTheme.typography.titleMedium
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(journalEntries) { entry ->
                JournalEntryCard(entry)
            }
        }
    }
}

@Composable
fun JournalEntryCard(entry: JournalEntry) {
    // Format the timestamp
    val formattedTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        .format(Date(entry.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formattedTimestamp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun JournalScreenPreview() {
    // Context is not available in preview mode
}