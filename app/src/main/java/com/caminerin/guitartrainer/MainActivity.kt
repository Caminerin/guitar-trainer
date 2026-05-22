package com.caminerin.guitartrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caminerin.guitartrainer.audio.AudioProcessor
import com.caminerin.guitartrainer.ui.MainScreen
import com.caminerin.guitartrainer.ui.theme.GuitarTrainerTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GuitarTrainerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GuitarTrainerApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun GuitarTrainerApp() {
    val context = LocalContext.current
    val audioProcessor = remember { AudioProcessor(context) }
    val micPermissionState = rememberPermissionState(
        android.Manifest.permission.RECORD_AUDIO
    )

    if (micPermissionState.status.isGranted) {
        val pitchResult by audioProcessor.currentPitch.collectAsState()
        val isListening by audioProcessor.isListening.collectAsState()

        LaunchedEffect(Unit) {
            audioProcessor.startListening()
        }

        DisposableEffect(Unit) {
            onDispose {
                audioProcessor.stopListening()
            }
        }

        MainScreen(pitchResult = pitchResult, isListening = isListening)
    } else {
        PermissionRequest(
            shouldShowRationale = micPermissionState.status.shouldShowRationale,
            onRequestPermission = { micPermissionState.launchPermissionRequest() }
        )
    }
}

@Composable
private fun PermissionRequest(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (shouldShowRationale) {
                "Guitar Trainer necesita acceso al micrófono para detectar las notas que tocas."
            } else {
                "Para empezar, necesitamos permiso para usar el micrófono."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onRequestPermission) {
            Text("Permitir micrófono")
        }
    }
}
