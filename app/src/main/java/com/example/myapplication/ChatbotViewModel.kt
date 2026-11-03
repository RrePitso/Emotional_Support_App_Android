package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

data class Message(val text: String, val isUser: Boolean)

class ChatbotViewModel(application: Application) : AndroidViewModel(application) {
    private val api = ChatbotApi.create()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    private val _slowHint = MutableStateFlow(false)
    val slowHint: StateFlow<Boolean> = _slowHint

    fun sendMessage(userMessage: String) {
        if (_isTyping.value) return
        _messages.value = _messages.value + Message(userMessage, isUser = true)
        _isTyping.value = true
        _slowHint.value = false

        viewModelScope.launch {
            val hintJob = launch {
                delay(8000)
                _slowHint.value = true
            }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.value = _messages.value + Message(friendlyError(e), isUser = false)
            } finally {
                hintJob.cancel()
                _isTyping.value = false
                _slowHint.value = false
            }
        }
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is SocketTimeoutException ->
            "That took longer than expected. Please try sending it again."
        is HttpException ->
            if (e.code() == 503) "I'm having trouble responding right now. Please try again in a moment."
            else "Something went wrong on my side. Please try again."
        is IOException ->
            "I can't reach the server. Please check your connection and try again."
        else -> "Something went wrong. Please try again."
    }
}