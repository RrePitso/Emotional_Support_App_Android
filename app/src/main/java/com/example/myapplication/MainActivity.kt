package com.example.myapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // 2) Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 3) Launcher for the Google Sign-In intent
        val signInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: Exception) {
                Log.w("MainActivity", "Google sign-in failed", e)
            }
        }

        // 4) Compose UI
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()

                if (!BuildConfig.DEBUG && auth.currentUser == null) {
                    // Show Google Sign-In button if not signed in
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(onClick = {
                            val signInIntent = googleSignInClient.signInIntent
                            signInLauncher.launch(signInIntent)
                        }) {
                            Text("Sign in with Google")
                        }
                    }
                } else {
                    // User is signed in → show main app navigation
                    Scaffold(modifier = Modifier.fillMaxSize()) {
                        NavigationHost(navController = navController)
                    }
                }
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("MainActivity", "signInWithCredential:success")
                } else {
                    Log.w("MainActivity", "signInWithCredential:failure", task.exception)
                }
            }
    }
}

@Composable
fun NavigationHost(navController: androidx.navigation.NavHostController) {
    androidx.navigation.compose.NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController)
        }
        composable(Screen.Journal.route) {
            JournalScreen(LocalContext.current)
        }
        composable(Screen.Chatbot.route) {
            ChatbotScreen()
        }
    }
}
