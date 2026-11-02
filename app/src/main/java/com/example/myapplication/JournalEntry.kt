package com.example.myapplication

data class EmotionLabel(
    val label: String,
    val score: Float
)

data class JournalEntry(
    val text: String,
    val timestamp: Long,
    val id: String? = null,
    val emotionLabels: List<EmotionLabel>? = null,
    val embedding: List<Float>? = null,
    val embeddingModel: String? = null
)