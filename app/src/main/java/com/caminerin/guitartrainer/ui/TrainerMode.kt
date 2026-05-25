package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TrainerMode(
    onOpenScales: () -> Unit,
    onOpenCagedPractice: () -> Unit,
    onOpenQuiz: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Entrenador",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Elige qu\u00e9 quieres practicar",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // View scales
        Button(
            onClick = onOpenScales,
            modifier = Modifier.fillMaxWidth().height(68.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
        ) {
            Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.size(14.dp))
            Text("Ver escalas", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // CAGED practice with guitar
        Button(
            onClick = onOpenCagedPractice,
            modifier = Modifier.fillMaxWidth().height(68.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
        ) {
            Icon(Icons.Default.Piano, null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.size(14.dp))
            Column {
                Text("Pr\u00e1ctica CAGED", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Con guitarra \u2022 nota a nota al BPM", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quiz mode without guitar
        Button(
            onClick = onOpenQuiz,
            modifier = Modifier.fillMaxWidth().height(68.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
        ) {
            Icon(Icons.Default.Quiz, null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.size(14.dp))
            Column {
                Text("Quiz de escalas", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Sin guitarra \u2022 encuentra las notas", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
