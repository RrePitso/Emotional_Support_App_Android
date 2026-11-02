package com.example.myapplication

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// Request body for the FastAPI backend
data class ChatRequest(
    @SerializedName("user_input")
    val user_input: String,
    val entries: List<JournalEntry> = emptyList(),
    @SerializedName("mood_trend")
    val mood_trend: String = ""
)

// Response body from the FastAPI backend
data class ChatResponse(
    val response: String
)

data class JournalAnalysisRequest(
    val text: String
)

data class JournalAnalysisResponse(
    val emotions: List<EmotionLabel>,
    val embedding: List<Float>,
    @SerializedName(value = "embedding_model", alternate = ["embeddingModel"])
    val embeddingModel: String
)

// Retrofit API interface
interface ChatbotApi {
    @POST("/chat/")
    suspend fun getChatResponse(@Body request: ChatRequest): ChatResponse

    @POST("/journal/analyze")
    suspend fun analyzeJournalEntry(
        @Body request: JournalAnalysisRequest
    ): JournalAnalysisResponse

    companion object {
        fun create(): ChatbotApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(BuildConfig.BACKEND_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(ChatbotApi::class.java)
        }
    }
}
