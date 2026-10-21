package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import com.example.ui.navigation.AppNavigation
import com.example.ui.screens.SignInScreen
import com.example.ui.theme.ABVFarmTheme
import com.example.utils.FarmNotificationManager
import com.example.utils.GoogleAuthHelper
import com.example.viewmodels.FarmViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    private val viewModel: FarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        FarmNotificationManager.createAllNotificationChannels(this)
        requestNotificationPermission()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val systemIsDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                1 -> false // Light
                2 -> true  // Dark
                else -> systemIsDark // 0 = Auto / System
            }

            var currentUser by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
            var bypassAuthGate by remember { mutableStateOf(false) }

            DisposableEffect(Unit) {
                val listener = FirebaseAuth.AuthStateListener { auth ->
                    currentUser = auth.currentUser
                }
                FirebaseAuth.getInstance().addAuthStateListener(listener)
                onDispose {
                    FirebaseAuth.getInstance().removeAuthStateListener(listener)
                }
            }

            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                if (currentUser == null) {
                    val credentialManager = CredentialManager.create(context)
                    GoogleAuthHelper.attemptAutoSignIn(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            currentUser = FirebaseAuth.getInstance().currentUser
                        },
                        onUnauthenticated = {
                            // User will see SignInScreen
                        },
                        scope = scope
                    )
                }
            }

            ABVFarmTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (currentUser == null && !bypassAuthGate) {
                        SignInScreen(
                            onAuthSuccess = {
                                currentUser = FirebaseAuth.getInstance().currentUser
                            },
                            onContinueOffline = {
                                bypassAuthGate = true
                            }
                        )
                    } else {
                        AppNavigation(viewModel)
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
