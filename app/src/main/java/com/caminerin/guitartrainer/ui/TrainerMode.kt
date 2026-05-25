package com.caminerin.guitartrainer.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrainerButton(
                onClick = onOpenScales,
                icon = Icons.Default.MusicNote,
                title = "Ver escalas",
                subtitle = null,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            TrainerButton(
                onClick = onOpenCagedPractice,
                icon = Icons.Default.Piano,
                title = "Pr\u00e1ctica CAGED",
                subtitle = "Con guitarra \u2022 nota a nota",
                color = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            TrainerButton(
                onClick = onOpenQuiz,
                icon = Icons.Default.Quiz,
                title = "Quiz de escalas",
                subtitle = "Sin guitarra \u2022 encuentra notas",
                color = Color(0xFFFF9800),
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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

            TrainerButton(
                onClick = onOpenScales,
                icon = Icons.Default.MusicNote,
                title = "Ver escalas",
                subtitle = null,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(68.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            TrainerButton(
                onClick = onOpenCagedPractice,
                icon = Icons.Default.Piano,
                title = "Pr\u00e1ctica CAGED",
                subtitle = "Con guitarra \u2022 nota a nota al BPM",
                color = Color(0xFF4CAF50),
                modifier = Modifier.fillMaxWidth().height(68.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            TrainerButton(
                onClick = onOpenQuiz,
                icon = Icons.Default.Quiz,
                title = "Quiz de escalas",
                subtitle = "Sin guitarra \u2022 encuentra las notas",
                color = Color(0xFFFF9800),
                modifier = Modifier.fillMaxWidth().height(68.dp)
            )
        }
    }
}

@Composable
private fun TrainerButton(
    onClick: () -> Unit,
    icon: ImageVector,
    title: String,
    subtitle: String?,
    color: Color,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
        }
    }
}
