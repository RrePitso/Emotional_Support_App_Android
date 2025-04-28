package com.example.myapplication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChatbotScreen(viewModel: ChatbotViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsState()
    var userInput by remember { mutableStateOf(TextFieldValue("")) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat history
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            reverseLayout = true // Show the latest message at the bottom
        ) {
            items(messages) { message ->
                ChatMessage(message)
            }
        }

        // Input field and send button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Input field
            BasicTextField(
                value = userInput,
                onValueChange = { userInput = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
                    .fillMaxWidth()
                    .background(Color.LightGray) // Set a background color for the input field
                    .padding(8.dp) // Add padding inside the text field
            )

            // Send button
            Button(onClick = {
                if (userInput.text.isNotBlank()) {
                    viewModel.sendMessage(userInput.text)
                    userInput = TextFieldValue("") // Clear the input field after sending
                }
            }) {
                Text("Send")
            }
        }
    }
}

@Composable
fun ChatMessage(message: Message) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val backgroundColor = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .padding(8.dp)
                .background(backgroundColor) // Set the background color for the message bubble
                .padding(16.dp) // Add padding inside the bubble
        ) {
            Text(
                text = message.text,
                color = textColor // Set the text color for better contrast
            )
        }
    }
}