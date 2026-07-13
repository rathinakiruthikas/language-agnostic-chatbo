package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.ChatRepository
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LandingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Initialize local Room Database & Repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ChatRepository(
            userDao = database.userDao(),
            chatDao = database.chatDao(),
            preferenceDao = database.preferenceDao()
        )

        // 2. Instantiate MainViewModel via standard Factory
        val viewModel = ViewModelProvider(
            this,
            MainViewModelFactory(application, repository)
        )[MainViewModel::class.java]

        enableEdgeToEdge()

        setContent {
            // Get user preferences (theme state)
            val preferences by viewModel.userPreferences.collectAsState()
            val isDark = preferences?.isDarkMode ?: true
            val accentHex = preferences?.accentColorHex ?: "#00F5FF"

            MyApplicationTheme(
                darkTheme = isDark,
                accentColorHex = accentHex
            ) {
                // Request dynamic microphone permission for voice inputs
                RequestMicrophonePermission(activity = this)

                // State-driven routing: show onboarding landing or core dashboard
                val userEmail by viewModel.currentUserEmail.collectAsState()

                if (userEmail == null) {
                    LandingScreen(
                        viewModel = viewModel,
                        onLoginSuccess = {
                            // Automatically routes on flow
                        }
                    )
                } else {
                    DashboardScreen(
                        viewModel = viewModel,
                        onLogout = {
                            // Automatically routes on flow
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RequestMicrophonePermission(activity: ComponentActivity) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                activity,
                "Microphone permission is required for voice voice chatbot. Please enable it in Settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissionCheck = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
        )
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
