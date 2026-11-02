package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class Message(val text: String, val isUser: Boolean)

class ChatbotViewModel(application: Application) : AndroidViewModel(application) {
    private val api = ChatbotApi.create()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    fun sendMessage(userMessage: String) {
        // Add the user's message to the conversation
        _messages.value = _messages.value + Message(userMessage, isUser = true)

        viewModelScope.launch {
            try {
                val journalEntries = loadJournalEntries(getApplication())
                val memoryEntries = journalEntries
                    .filter { !it.embedding.isNullOrEmpty() }
                    .takeLast(100)
                val request = ChatRequest(
                    user_input = userMessage,
                    entries = memoryEntries,
                    mood_trend = summarizeMoodTrend(journalEntries)
                )

                val response = api.getChatResponse(request)

                _messages.value = _messages.value + Message(response.response, isUser = false)
            } catch (e: Exception) {
                // Handle exceptions
                val errorMessage = "Error: ${e.message}"
                _messages.value = _messages.value + Message(errorMessage, isUser = false)
            }
        }
    }
}