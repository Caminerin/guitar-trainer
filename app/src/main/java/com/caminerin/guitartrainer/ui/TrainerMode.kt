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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caminerin.guitartrainer.audio.PitchDetector

// ===== VISUALIZER MENU =====
@Composable
fun VisualizerMenu(
    onOpenScales: () -> Unit,
    onOpenChords: () -> Unit,
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
            MenuButton(
                onClick = onOpenScales,
                icon = Icons.Default.MusicNote,
                title = "Escalas",
                subtitle = "Ver escalas en el m\u00e1stil",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MenuButton(
                onClick = onOpenChords,
                icon = Icons.Default.Piano,
                title = "Acordes",
                subtitle = "Digitaciones y formas",
                color = Color(0xFF7B1FA2),
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
                text = "Visualizador",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Explora escalas y acordes en el m\u00e1stil",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            MenuButton(
                onClick = onOpenScales,
                icon = Icons.Default.MusicNote,
                title = "Escalas",
                subtitle = "Ver escalas en el m\u00e1stil",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            MenuButton(
                onClick = onOpenChords,
                icon = Icons.Default.Piano,
                title = "Acordes",
                subtitle = "Digitaciones y formas",
                color = Color(0xFF7B1FA2),
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )
        }
    }
}

// ===== PRACTICE MENU =====
@Composable
fun PracticeMenu(
    onOpenCagedPractice: () -> Unit,
    onOpenChordPractice: () -> Unit,
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
            MenuButton(
                onClick = onOpenCagedPractice,
                icon = Icons.Default.MusicNote,
                title = "Escalas por posiciones",
                subtitle = "Guía visual \u2022 nota a nota",
                color = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MenuButton(
                onClick = onOpenChordPractice,
                icon = Icons.Default.Piano,
                title = "Acordes",
                subtitle = "Progresiones \u2022 compases",
                color = Color(0xFF7B1FA2),
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
                text = "Pr\u00e1ctica",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Entrena con tu guitarra",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            MenuButton(
                onClick = onOpenCagedPractice,
                icon = Icons.Default.MusicNote,
                title = "Escalas por posiciones",
                subtitle = "Guía visual \u2022 nota a nota al BPM",
                color = Color(0xFF4CAF50),
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            MenuButton(
                onClick = onOpenChordPractice,
                icon = Icons.Default.Piano,
                title = "Acordes",
                subtitle = "Progresiones \u2022 compases",
                color = Color(0xFF7B1FA2),
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )
        }
    }
}

// ===== QUIZ MENU =====
@Composable
fun QuizMenu(
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
            MenuButton(
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
                text = "Quiz",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pon a prueba tu conocimiento",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            MenuButton(
                onClick = onOpenQuiz,
                icon = Icons.Default.Quiz,
                title = "Quiz de escalas",
                subtitle = "Sin guitarra \u2022 encuentra las notas",
                color = Color(0xFFFF9800),
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )
        }
    }
}

// ===== TOOLS MENU (Tuner + Metronome) =====
@Composable
fun ToolsMenu(
    pitchResult: PitchDetector.PitchResult?,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var selectedTool by rememberSaveable { mutableStateOf("none") }

    when (selectedTool) {
        "tuner" -> {
            Column(modifier = modifier.fillMaxSize()) {
                TextButton(
                    onClick = { selectedTool = "none" },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("\u2190 Volver a Herramientas", fontSize = 14.sp)
                }
                TunerMode(pitchResult = pitchResult)
            }
        }
        "metronome" -> {
            Column(modifier = modifier.fillMaxSize()) {
                TextButton(
                    onClick = { selectedTool = "none" },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("\u2190 Volver a Herramientas", fontSize = 14.sp)
                }
                MetronomeMode()
            }
        }
        else -> {
            if (isLandscape) {
                Row(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MenuButton(
                        onClick = { selectedTool = "tuner" },
                        icon = Icons.Default.Tune,
                        title = "Afinador",
                        subtitle = "Afina tu guitarra",
                        color = Color(0xFF0277BD),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    MenuButton(
                        onClick = { selectedTool = "metronome" },
                        icon = Icons.Default.Speed,
                        title = "Metr\u00f3nomo",
                        subtitle = "Controla el tempo",
                        color = Color(0xFFE65100),
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
                        text = "Herramientas",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Afinador y metr\u00f3nomo",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    MenuButton(
                        onClick = { selectedTool = "tuner" },
                        icon = Icons.Default.Tune,
                        title = "Afinador",
                        subtitle = "Afina tu guitarra",
                        color = Color(0xFF0277BD),
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MenuButton(
                        onClick = { selectedTool = "metronome" },
                        icon = Icons.Default.Speed,
                        title = "Metr\u00f3nomo",
                        subtitle = "Controla el tempo",
                        color = Color(0xFFE65100),
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )
                }
            }
        }
    }
}

// Shared button component
@Composable
private fun MenuButton(
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
            Icon(icon, null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
        }
    }
}
