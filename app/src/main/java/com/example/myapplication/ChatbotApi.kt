package com.example.myapplication

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// Request body for the FastAPI backend
data class ChatRequest(
    val user_input: String // Matches the FastAPI JSON structure
)

// Response body from the FastAPI backend
data class ChatResponse(
    val response: String // Matches the FastAPI JSON response structure
)

// Retrofit API interface
interface ChatbotApi {
    @POST("/chat/")
    suspend fun getChatResponse(@Body request: ChatRequest): ChatResponse

    companion object {
        fun create(): ChatbotApi {
            val retrofit = Retrofit.Builder()
                .baseUrl("http://172.20.56.83:8000/") // Replace with your FastAPI server's IP and port
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(ChatbotApi::class.java)
        }
    }
}

