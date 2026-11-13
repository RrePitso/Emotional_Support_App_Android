package com.example.myapplication

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Journal : Screen("journal")
    object Chatbot : Screen("chatbot")
    object Report : Screen("report")
    object Onboarding : Screen("onboarding")
}
