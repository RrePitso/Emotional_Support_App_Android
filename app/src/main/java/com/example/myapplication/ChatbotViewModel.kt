package com.example.myapplication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class Message(val text: String, val isUser: Boolean)

class ChatbotViewModel : ViewModel() {
    private val api = ChatbotApi.create()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    fun sendMessage(userMessage: String) {
        // Add the user's message to the conversation
        _messages.value = _messages.value + Message(userMessage, isUser = true)

        viewModelScope.launch {
            try {
                // Prepare the request
                val request = ChatRequest(user_input = userMessage)

                // Make the API call
                val response = api.getChatResponse(request)

                // Add the chatbot's response to the conversation
                val botMessage = response.response // Updated to match FastAPI's response
                _messages.value = _messages.value + Message(botMessage, isUser = false)
            } catch (e: Exception) {
                // Handle exceptions
                val errorMessage = "Error: ${e.message}"
                _messages.value = _messages.value + Message(errorMessage, isUser = false)
            }
        }
    }
}