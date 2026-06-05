package com.caminerin.guitartrainer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caminerin.guitartrainer.audio.PitchDetector
import com.caminerin.guitartrainer.audio.RiffSynth
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ═══════════════════════════════════════════════════════
// Quiz Hub — 18 exercises in 3 categories
// ═══════════════════════════════════════════════════════

private val HUB_BG = SHARED_BG
private val HUB_BAR = SHARED_TOOLBAR
private val HUB_ACCENT = SHARED_ACCENT
private val HUB_CARD = AppColors.cardBg
private val HUB_GREEN = AppColors.success
private val HUB_RED = AppColors.error

// Category colors
private val CAT_EAR = Color(0xFF42A5F5)    // Blue - Ear Training
private val CAT_FRET = Color(0xFF66BB6A)   // Green - Fretboard
private val CAT_THEORY = Color(0xFFAB47BC) // Purple - Theory

enum class QuizDifficulty(val label: String) {
    EASY("Fácil"), MEDIUM("Media"), HARD("Difícil")
}

data class QuizExercise(
    val id: String,
    val number: String,
    val title: String,
    val description: String,
    val difficulty: String,
    val category: String,
    val categoryColor: Color,
    val icon: ImageVector
)

private val ALL_EXERCISES = listOf(
    // Oído (Ear Training)
    QuizExercise("1.1", "1.1", "Sube o baja", "2 notas: ¿ascendente o descendente?", "Intro", "Oído", CAT_EAR, Icons.Default.Hearing),
    QuizExercise("1.2", "1.2", "Nota al aire", "Toca 1 cuerda al aire, identifica cuál", "Fácil", "Oído", CAT_EAR, Icons.Default.Hearing),
    QuizExercise("1.3", "1.3", "Mayor o Menor", "Toca acorde, ¿es mayor o menor?", "Fácil", "Oído", CAT_EAR, Icons.Default.Hearing),
    QuizExercise("1.4", "1.4", "Intervalos melódicos", "2 notas consecutivas, ¿qué intervalo?", "Fácil → Difícil", "Oído", CAT_EAR, Icons.Default.Hearing),
    QuizExercise("1.5", "1.5", "Intervalos armónicos", "2 notas simultáneas, ¿qué intervalo?", "Media", "Oído", CAT_EAR, Icons.Default.Hearing),
    QuizExercise("1.6", "1.6", "Tipo de acorde", "maj/min/7/dim/aug", "Difícil", "Oído", CAT_EAR, Icons.Default.Hearing),
    // Mástil (Fretboard)
    QuizExercise("2.1", "2.1", "Encuentra la nota", "Marca todas las Do en el mástil", "Fácil", "Mástil", CAT_FRET, Icons.Default.MusicNote),
    QuizExercise("2.2", "2.2", "¿Qué nota es?", "Señala posición, identifica nota", "Fácil", "Mástil", CAT_FRET, Icons.Default.MusicNote),
    QuizExercise("2.3", "2.3", "Notas de la escala", "Quiz interactivo de escalas (existente)", "Media", "Mástil", CAT_FRET, Icons.Default.MusicNote),
    QuizExercise("2.4", "2.4", "Tónica → Grado", "Desde esta tónica, pulsa la 3ª/5ª/7ª", "Media → Difícil", "Mástil", CAT_FRET, Icons.Default.MusicNote),
    QuizExercise("2.5", "2.5", "Patrón CAGED", "¿En qué posición CAGED está este acorde?", "Media", "Mástil", CAT_FRET, Icons.Default.MusicNote),
    QuizExercise("2.6", "2.6", "Octavas", "Encuentra la misma nota una octava arriba", "Fácil → Media", "Mástil", CAT_FRET, Icons.Default.MusicNote),
    // Teoría (Music Theory)
    QuizExercise("3.1", "3.1", "Notas en un acorde", "¿Qué notas tiene Am?", "Fácil", "Teoría", CAT_THEORY, Icons.Default.School),
    QuizExercise("3.2", "3.2", "Grado en la escala", "V grado de Re Mayor?", "Media", "Teoría", CAT_THEORY, Icons.Default.School),
    QuizExercise("3.3", "3.3", "Relativo menor/mayor", "¿Relativo menor de Sol?", "Fácil", "Teoría", CAT_THEORY, Icons.Default.School),
    QuizExercise("3.4", "3.4", "Fórmula de escala", "T-T-S-T-T-T-S = ¿qué escala?", "Difícil", "Teoría", CAT_THEORY, Icons.Default.School),
    QuizExercise("3.5", "3.5", "Acordes diatónicos", "¿Qué acordes van en Do Mayor?", "Media", "Teoría", CAT_THEORY, Icons.Default.School),
    QuizExercise("3.6", "3.6", "Nombre del intervalo", "De Do a Sol = ?", "Fácil", "Teoría", CAT_THEORY, Icons.Default.School)
)

private val INTERVAL_NAMES = listOf(
    "Unísono", "2ª menor", "2ª Mayor", "3ª menor", "3ª Mayor",
    "4ª Justa", "Tritono", "5ª Justa", "6ª menor", "6ª Mayor",
    "7ª menor", "7ª Mayor"
)

private val CAGED_POSITIONS = listOf("C", "A", "G", "E", "D")

@Composable
fun QuizHubScreen(
    onBack: () -> Unit,
    pitchResult: PitchDetector.PitchResult? = null,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    var selectedExercise by remember { mutableStateOf<QuizExercise?>(null) }
    var questionCount by remember { mutableIntStateOf(10) }
    var difficulty by remember { mutableStateOf(QuizDifficulty.EASY) }
    var showConfig by remember { mutableStateOf(false) }
    var pendingExercise by remember { mutableStateOf<QuizExercise?>(null) }

    LaunchedEffect(Unit) {
        ChordRepository.loadChords(context)
    }

    if (selectedExercise != null) {
        when (selectedExercise!!.id) {
            "2.3" -> {
                ScaleQuizScreen(
                    onBack = { selectedExercise = null },
                    pitchResult = pitchResult,
                    showBackButton = true
                )
            }
            else -> {
                GenericQuizScreen(
                    exercise = selectedExercise!!,
                    questionCount = questionCount,
                    difficulty = difficulty,
                    onBack = { selectedExercise = null }
                )
            }
        }
        return
    }

    // Config dialog
    if (showConfig && pendingExercise != null) {
        QuizConfigDialog(
            exercise = pendingExercise!!,
            questionCount = questionCount,
            difficulty = difficulty,
            onQuestionCountChange = { questionCount = it },
            onDifficultyChange = { difficulty = it },
            onStart = {
                selectedExercise = pendingExercise
                showConfig = false
                pendingExercise = null
            },
            onDismiss = { showConfig = false; pendingExercise = null }
        )
    }

    // Hub list
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HUB_BG)
            .verticalScroll(rememberScrollState())
    ) {
        // Title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HUB_BAR)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Text("Quiz Hub", color = HUB_ACCENT, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp))
            Spacer(modifier = Modifier.weight(1f))
            Text("18 ejercicios", color = Color.White.copy(0.4f), fontSize = 11.sp)
        }

        // Categories
        val categories = listOf(
            Triple("Oído", CAT_EAR, true),
            Triple("Mástil", CAT_FRET, false),
            Triple("Teoría", CAT_THEORY, false)
        )

        categories.forEach { (catName, catColor, defaultExpanded) ->
            val exercises = ALL_EXERCISES.filter { it.category == catName }
            CategorySection(
                name = catName,
                color = catColor,
                exercises = exercises,
                defaultExpanded = defaultExpanded,
                onExerciseClick = { exercise ->
                    if (exercise.id == "2.3") {
                        selectedExercise = exercise
                    } else {
                        pendingExercise = exercise
                        showConfig = true
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
private fun CategorySection(
    name: String,
    color: Color,
    exercises: List<QuizExercise>,
    defaultExpanded: Boolean,
    onExerciseClick: (QuizExercise) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(defaultExpanded) }

    Column {
        // Category header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .background(color.copy(0.1f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text("${exercises.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(name, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, tint = color, modifier = Modifier.size(24.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                exercises.forEach { exercise ->
                    ExerciseCard(exercise = exercise, onClick = { onExerciseClick(exercise) })
                }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: QuizExercise,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(HUB_CARD)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(exercise.categoryColor.copy(0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(exercise.number, color = exercise.categoryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(exercise.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(exercise.description, color = Color.White.copy(0.5f), fontSize = 11.sp, maxLines = 1)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(exercise.categoryColor.copy(0.1f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(exercise.difficulty, color = exercise.categoryColor, fontSize = 10.sp)
        }
    }
}

@Composable
private fun QuizConfigDialog(
    exercise: QuizExercise,
    questionCount: Int,
    difficulty: QuizDifficulty,
    onQuestionCountChange: (Int) -> Unit,
    onDifficultyChange: (QuizDifficulty) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HUB_CARD,
        title = {
            Text("${exercise.number} ${exercise.title}", color = exercise.categoryColor,
                fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(exercise.description, color = Color.White.copy(0.6f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))

                // Question count
                Text("Preguntas", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 10, 15, 20).forEach { count ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (questionCount == count) HUB_ACCENT else Color.White.copy(0.08f))
                                .clickable { onQuestionCountChange(count) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("$count", color = if (questionCount == count) Color.Black else Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Difficulty
                Text("Dificultad", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuizDifficulty.entries.forEach { diff ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (difficulty == diff) HUB_ACCENT else Color.White.copy(0.08f))
                                .clickable { onDifficultyChange(diff) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(diff.label, color = if (difficulty == diff) Color.Black else Color.White,
                                fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = HUB_GREEN)
            ) {
                Text("Empezar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.White.copy(0.5f))
            }
        }
    )
}

// ═══════════════════════════════════════════════════════
// Generic Quiz Screen — handles all 17 exercises (except 2.3)
// ═══════════════════════════════════════════════════════
@Composable
private fun GenericQuizScreen(
    exercise: QuizExercise,
    questionCount: Int,
    difficulty: QuizDifficulty,
    onBack: () -> Unit
) {
    var currentQuestion by remember { mutableIntStateOf(0) }
    var correctCount by remember { mutableIntStateOf(0) }
    var questionData by remember { mutableStateOf(generateQuestion(exercise, difficulty)) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var answerTimes by remember { mutableStateOf(listOf<Long>()) }
    var questionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Auto-play the audio for ear-training questions when a new one appears
    LaunchedEffect(questionData) {
        questionData.audio?.let { a -> scope.launch(Dispatchers.Default) { playQuizAudio(context, a) } }
    }
    // Stop any sound when leaving the exercise
    DisposableEffect(Unit) {
        onDispose { RiffSynth.stop() }
    }

    if (showResult) {
        QuizResultScreen(
            correct = correctCount,
            total = questionCount,
            avgTime = if (answerTimes.isNotEmpty()) answerTimes.average().toLong() else 0L,
            exercise = exercise,
            onRepeat = {
                currentQuestion = 0
                correctCount = 0
                answerTimes = emptyList()
                showResult = false
                questionData = generateQuestion(exercise, difficulty)
                questionStartTime = System.currentTimeMillis()
            },
            onBack = onBack
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HUB_BG)
    ) {
        // Top bar with progress
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HUB_BAR)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { RiffSynth.stop(); onBack() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text("${exercise.number} ${exercise.title}", color = exercise.categoryColor,
                fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${currentQuestion + 1}/$questionCount", color = Color.White.copy(0.5f), fontSize = 12.sp)
        }

        // Progress bar
        LinearProgressIndicator(
            progress = (currentQuestion.toFloat() + 1) / questionCount,
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = exercise.categoryColor,
            trackColor = Color.White.copy(0.1f),
        )

        // Question area (scrollable so options never get hidden behind the Next bar)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Question text
            Text(
                questionData.question,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Play button (ear-training exercises with audio)
            if (questionData.audio != null) {
                Button(
                    onClick = {
                        questionData.audio?.let { a ->
                            scope.launch(Dispatchers.Default) { playQuizAudio(context, a) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = exercise.categoryColor),
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reproducir", fontWeight = FontWeight.Bold)
                }
            }

            // Answer options
            questionData.options.forEach { option ->
                val isSelected = selectedAnswer == option
                val isCorrect = option == questionData.correctAnswer
                val bgColor by animateColorAsState(
                    targetValue = when {
                        selectedAnswer == null -> HUB_CARD
                        isSelected && isCorrect -> HUB_GREEN.copy(0.3f)
                        isSelected && !isCorrect -> HUB_RED.copy(0.3f)
                        !isSelected && isCorrect && selectedAnswer != null -> HUB_GREEN.copy(0.15f)
                        else -> HUB_CARD
                    },
                    label = "optionBg"
                )
                val borderColor by animateColorAsState(
                    targetValue = when {
                        selectedAnswer == null -> Color.Transparent
                        isSelected && isCorrect -> HUB_GREEN
                        isSelected && !isCorrect -> HUB_RED
                        !isSelected && isCorrect && selectedAnswer != null -> HUB_GREEN.copy(0.5f)
                        else -> Color.Transparent
                    },
                    label = "optionBorder"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                        .clickable(enabled = selectedAnswer == null) {
                            selectedAnswer = option
                            val elapsed = System.currentTimeMillis() - questionStartTime
                            answerTimes = answerTimes + elapsed
                            if (option == questionData.correctAnswer) {
                                correctCount++
                            }
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        option,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Next button (after answering)
        if (selectedAnswer != null) {
            Button(
                onClick = {
                    if (currentQuestion + 1 >= questionCount) {
                        RiffSynth.stop()
                        showResult = true
                    } else {
                        currentQuestion++
                        selectedAnswer = null
                        questionData = generateQuestion(exercise, difficulty)
                        questionStartTime = System.currentTimeMillis()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = exercise.categoryColor)
            ) {
                Text(
                    if (currentQuestion + 1 >= questionCount) "Ver resultado" else "Siguiente",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun QuizResultScreen(
    correct: Int,
    total: Int,
    avgTime: Long,
    exercise: QuizExercise,
    onRepeat: () -> Unit,
    onBack: () -> Unit
) {
    val percentage = if (total > 0) (correct * 100) / total else 0
    val resultColor = when {
        percentage >= 80 -> HUB_GREEN
        percentage >= 50 -> HUB_ACCENT
        else -> HUB_RED
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HUB_BG)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Resultado", color = exercise.categoryColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))

        Text("$correct / $total", color = resultColor, fontSize = 48.sp, fontWeight = FontWeight.Black)
        Text("$percentage%", color = resultColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(12.dp))

        if (avgTime > 0) {
            Text(
                "Tiempo medio: ${avgTime / 1000}.${(avgTime % 1000) / 100}s por pregunta",
                color = Color.White.copy(0.5f), fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) {
                Text("Volver", color = Color.White)
            }
            Button(
                onClick = onRepeat,
                colors = ButtonDefaults.buttonColors(containerColor = exercise.categoryColor)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Repetir", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Question generation for all exercise types
// ═══════════════════════════════════════════════════════

data class QuestionData(
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
    val audio: QuizAudio? = null
)

/** Audio payload for ear-training questions. Uses MIDI note numbers (60 = C4). */
sealed class QuizAudio {
    /** Notes played one after another. */
    data class Melody(val midis: List<Int>) : QuizAudio()
    /** Notes played simultaneously (chord / harmonic interval). */
    data class Harmony(val midis: List<Int>) : QuizAudio()
}

// Open-string MIDI for standard tuning, indexed by string number 1..6 (1 = high E).
private val GUITAR_OPEN_MIDI = intArrayOf(64, 59, 55, 50, 45, 40)

/** Map a MIDI note to a playable (string, fret) on the guitar (fret 0..12). */
private fun midiToStringFret(midi: Int): Pair<Int, Int> {
    for (s in 6 downTo 1) {
        val fret = midi - GUITAR_OPEN_MIDI[s - 1]
        if (fret in 0..12) return s to fret
    }
    return if (midi >= GUITAR_OPEN_MIDI[0]) 1 to (midi - GUITAR_OPEN_MIDI[0]).coerceIn(0, 12)
    else 6 to (midi - GUITAR_OPEN_MIDI[5]).coerceIn(0, 12)
}

/** Render and play a quiz audio payload via the guitar synth. Call off the main thread. */
private fun playQuizAudio(context: Context, audio: QuizAudio) {
    RiffSynth.init(context)
    val events = when (audio) {
        is QuizAudio.Melody -> audio.midis.mapIndexed { i, m ->
            val (s, f) = midiToStringFret(m)
            RiffSynth.NoteEvent(string = s, fret = f, startMs = i * 700L, durationMs = 650)
        }
        is QuizAudio.Harmony -> audio.midis.mapIndexed { i, m ->
            val (s, f) = midiToStringFret(m)
            RiffSynth.NoteEvent(string = s, fret = f, startMs = i * 22L, durationMs = 1500)
        }
    }
    RiffSynth.playSequence(events, "clean")
}

private fun generateQuestion(exercise: QuizExercise, difficulty: QuizDifficulty): QuestionData {
    return when (exercise.id) {
        "1.1" -> generateUpOrDown()
        "1.2" -> generateOpenStringQuestion()
        "1.3" -> generateMajorMinorQuestion()
        "1.4" -> generateMelodicIntervalQuestion(difficulty)
        "1.5" -> generateHarmonicIntervalQuestion(difficulty)
        "1.6" -> generateChordTypeQuestion(difficulty)
        "2.1" -> generateFindNoteQuestion()
        "2.2" -> generateWhatNoteQuestion()
        "2.4" -> generateDegreeQuestion(difficulty)
        "2.5" -> generateCAGEDQuestion()
        "2.6" -> generateOctaveQuestion()
        "3.1" -> generateChordNotesQuestion()
        "3.2" -> generateScaleDegreeQuestion()
        "3.3" -> generateRelativeQuestion()
        "3.4" -> generateScaleFormulaQuestion()
        "3.5" -> generateDiatonicChordsQuestion()
        "3.6" -> generateIntervalNameQuestion()
        else -> QuestionData("Ejercicio no disponible", listOf("Volver"), "Volver")
    }
}

// ─── Ear Training questions ───
// These play real audio; the answer must be identified by ear, so the note
// names are NOT revealed in the question text.

private fun generateUpOrDown(): QuestionData {
    val up = Random.nextBoolean()
    val rootMidi = 52 + Random.nextInt(12)
    val offset = Random.nextInt(2, 10)
    val secondMidi = if (up) rootMidi + offset else rootMidi - offset
    return QuestionData(
        question = "🎧 Escucha las 2 notas.\n¿Sube o baja?",
        options = listOf("Sube (ascendente)", "Baja (descendente)").shuffled(),
        correctAnswer = if (up) "Sube (ascendente)" else "Baja (descendente)",
        audio = QuizAudio.Melody(listOf(rootMidi, secondMidi))
    )
}

private fun generateOpenStringQuestion(): QuestionData {
    val strings = listOf("Mi agudo (1ª)", "Si (2ª)", "Sol (3ª)", "Re (4ª)", "La (5ª)", "Mi grave (6ª)")
    val openMidi = listOf(64, 59, 55, 50, 45, 40)
    val idx = Random.nextInt(strings.size)
    return QuestionData(
        question = "🎸 Escucha la cuerda al aire.\n¿Cuál es?",
        options = strings.shuffled(),
        correctAnswer = strings[idx],
        audio = QuizAudio.Melody(listOf(openMidi[idx]))
    )
}

private fun generateMajorMinorQuestion(): QuestionData {
    val isMajor = Random.nextBoolean()
    val rootMidi = 48 + Random.nextInt(12)
    val third = if (isMajor) 4 else 3
    return QuestionData(
        question = "🎵 Escucha el acorde.\n¿Mayor o menor?",
        options = listOf("Mayor", "Menor"),
        correctAnswer = if (isMajor) "Mayor" else "Menor",
        audio = QuizAudio.Harmony(listOf(rootMidi, rootMidi + third, rootMidi + 7))
    )
}

private fun generateMelodicIntervalQuestion(difficulty: QuizDifficulty): QuestionData {
    val maxInterval = when (difficulty) {
        QuizDifficulty.EASY -> 5
        QuizDifficulty.MEDIUM -> 8
        QuizDifficulty.HARD -> 11
    }
    val interval = Random.nextInt(1, minOf(maxInterval, INTERVAL_NAMES.size - 1) + 1)
    val rootMidi = 50 + Random.nextInt(12)
    val correct = INTERVAL_NAMES[interval]

    val options = mutableSetOf(correct)
    while (options.size < 4) {
        options.add(INTERVAL_NAMES[Random.nextInt(1, INTERVAL_NAMES.size)])
    }

    return QuestionData(
        question = "🎵 Escucha las 2 notas (melódico).\n¿Qué intervalo es?",
        options = options.toList().shuffled(),
        correctAnswer = correct,
        audio = QuizAudio.Melody(listOf(rootMidi, rootMidi + interval))
    )
}

private fun generateHarmonicIntervalQuestion(difficulty: QuizDifficulty): QuestionData {
    val maxInterval = when (difficulty) {
        QuizDifficulty.EASY -> 5
        QuizDifficulty.MEDIUM -> 8
        QuizDifficulty.HARD -> 11
    }
    val interval = Random.nextInt(1, minOf(maxInterval, INTERVAL_NAMES.size - 1) + 1)
    val rootMidi = 48 + Random.nextInt(12)
    val correct = INTERVAL_NAMES[interval]

    val options = mutableSetOf(correct)
    while (options.size < 4) {
        options.add(INTERVAL_NAMES[Random.nextInt(1, INTERVAL_NAMES.size)])
    }

    return QuestionData(
        question = "🎵 Escucha las 2 notas (a la vez).\n¿Qué intervalo es?",
        options = options.toList().shuffled(),
        correctAnswer = correct,
        audio = QuizAudio.Harmony(listOf(rootMidi, rootMidi + interval))
    )
}

private fun generateChordTypeQuestion(difficulty: QuizDifficulty): QuestionData {
    val types = when (difficulty) {
        QuizDifficulty.EASY -> listOf(
            "Mayor" to listOf(0, 4, 7),
            "Menor" to listOf(0, 3, 7)
        )
        QuizDifficulty.MEDIUM -> listOf(
            "Mayor" to listOf(0, 4, 7),
            "Menor" to listOf(0, 3, 7),
            "7ª dominante" to listOf(0, 4, 7, 10)
        )
        QuizDifficulty.HARD -> listOf(
            "Mayor" to listOf(0, 4, 7),
            "Menor" to listOf(0, 3, 7),
            "7ª dominante" to listOf(0, 4, 7, 10),
            "Disminuido" to listOf(0, 3, 6),
            "Aumentado" to listOf(0, 4, 8)
        )
    }
    val (correct, intervals) = types[Random.nextInt(types.size)]
    val rootMidi = 48 + Random.nextInt(12)
    return QuestionData(
        question = "🎵 Escucha el acorde.\n¿Qué tipo es?",
        options = types.map { it.first }.shuffled(),
        correctAnswer = correct,
        audio = QuizAudio.Harmony(intervals.map { rootMidi + it })
    )
}

// ─── Fretboard questions ───

private fun generateFindNoteQuestion(): QuestionData {
    val noteIdx = Random.nextInt(12)
    val note = AMERICAN_NOTE_NAMES[noteIdx]
    // Count occurrences in first 12 frets
    val openStrings = listOf(4, 11, 7, 2, 9, 4) // E B G D A E
    var count = 0
    for (s in openStrings) {
        for (fret in 0..12) {
            if ((s + fret) % 12 == noteIdx) count++
        }
    }
    val options = listOf("$count posiciones", "${count - 1} posiciones", "${count + 1} posiciones", "${count + 2} posiciones")
        .distinct().take(4)
    return QuestionData(
        question = "🎸 ¿Cuántas posiciones de $note hay en los primeros 12 trastes?",
        options = options.shuffled(),
        correctAnswer = "$count posiciones"
    )
}

private fun generateWhatNoteQuestion(): QuestionData {
    val string = Random.nextInt(6)
    val fret = Random.nextInt(0, 13)
    val openStrings = listOf(4, 11, 7, 2, 9, 4)
    val noteIdx = (openStrings[string] + fret) % 12
    val correct = AMERICAN_NOTE_NAMES[noteIdx]
    val stringNames = listOf("1ª (Mi agudo)", "2ª (Si)", "3ª (Sol)", "4ª (Re)", "5ª (La)", "6ª (Mi grave)")

    val options = mutableSetOf(correct)
    while (options.size < 4) {
        options.add(AMERICAN_NOTE_NAMES[Random.nextInt(12)])
    }

    return QuestionData(
        question = "🎸 Cuerda ${stringNames[string]}, traste $fret\n¿Qué nota es?",
        options = options.toList().shuffled(),
        correctAnswer = correct
    )
}

private fun generateDegreeQuestion(difficulty: QuizDifficulty): QuestionData {
    val rootIdx = Random.nextInt(12)
    val root = AMERICAN_NOTE_NAMES[rootIdx]
    val degrees = when (difficulty) {
        QuizDifficulty.EASY -> listOf(4 to "3ª Mayor", 7 to "5ª Justa")
        QuizDifficulty.MEDIUM -> listOf(3 to "3ª menor", 4 to "3ª Mayor", 7 to "5ª Justa", 10 to "7ª menor")
        QuizDifficulty.HARD -> listOf(2 to "2ª Mayor", 3 to "3ª menor", 4 to "3ª Mayor", 5 to "4ª Justa",
            7 to "5ª Justa", 9 to "6ª Mayor", 10 to "7ª menor", 11 to "7ª Mayor")
    }
    val (semitones, degreeName) = degrees[Random.nextInt(degrees.size)]
    val correct = AMERICAN_NOTE_NAMES[(rootIdx + semitones) % 12]

    val options = mutableSetOf(correct)
    while (options.size < 4) {
        options.add(AMERICAN_NOTE_NAMES[Random.nextInt(12)])
    }

    return QuestionData(
        question = "Desde $root, ¿cuál es la $degreeName?",
        options = options.toList().shuffled(),
        correctAnswer = correct
    )
}

private fun generateCAGEDQuestion(): QuestionData {
    val correct = CAGED_POSITIONS[Random.nextInt(CAGED_POSITIONS.size)]
    val root = AMERICAN_NOTE_NAMES[Random.nextInt(12)]
    return QuestionData(
        question = "🎸 Acorde $root en posición abierta\n¿Qué forma CAGED usa?",
        options = CAGED_POSITIONS.shuffled(),
        correctAnswer = correct
    )
}

private fun generateOctaveQuestion(): QuestionData {
    val string = Random.nextInt(4) // strings 0-3, octave usually 2 strings + 2-3 frets up
    val fret = Random.nextInt(0, 10)
    val openStrings = listOf(4, 11, 7, 2, 9, 4)
    val noteIdx = (openStrings[string] + fret) % 12
    val note = AMERICAN_NOTE_NAMES[noteIdx]

    // Find octave position
    val targetString = string + 2
    if (targetString >= 6) return generateWhatNoteQuestion() // fallback
    val fretOffset = if (targetString <= 3) 2 else 3
    val octaveFret = fret + fretOffset
    val stringNames = listOf("1ª", "2ª", "3ª", "4ª", "5ª", "6ª")
    val correct = "Cuerda ${stringNames[targetString]}, traste $octaveFret"

    val options = mutableSetOf(correct)
    while (options.size < 4) {
        val s = Random.nextInt(6)
        val f = Random.nextInt(0, 15)
        options.add("Cuerda ${stringNames[s]}, traste $f")
    }

    return QuestionData(
        question = "🎸 $note en cuerda ${stringNames[string]}, traste $fret\n¿Dónde está una octava arriba?",
        options = options.toList().shuffled(),
        correctAnswer = correct
    )
}

// ─── Theory questions ───

private fun generateChordNotesQuestion(): QuestionData {
    val rootIdx = Random.nextInt(12)
    val root = AMERICAN_NOTE_NAMES[rootIdx]
    val isMinor = Random.nextBoolean()
    val third = if (isMinor) 3 else 4
    val notes = listOf(
        AMERICAN_NOTE_NAMES[rootIdx],
        AMERICAN_NOTE_NAMES[(rootIdx + third) % 12],
        AMERICAN_NOTE_NAMES[(rootIdx + 7) % 12]
    )
    val correct = notes.joinToString(" - ")
    val chordLabel = "$root${if (isMinor) "m" else ""}"

    val options = mutableSetOf(correct)
    while (options.size < 4) {
        val r = Random.nextInt(12)
        val t = if (Random.nextBoolean()) 3 else 4
        options.add(listOf(
            AMERICAN_NOTE_NAMES[r],
            AMERICAN_NOTE_NAMES[(r + t) % 12],
            AMERICAN_NOTE_NAMES[(r + 7) % 12]
        ).joinToString(" - "))
    }

    return QuestionData(
        question = "¿Qué notas tiene $chordLabel?",
        options = options.toList().shuffled(),
        correctAnswer = correct
    )
}

private fun generateScaleDegreeQuestion(): QuestionData {
    val rootIdx = Random.nextInt(12)
    val root = AMERICAN_NOTE_NAMES[rootIdx]
    // Major scale intervals: 0, 2, 4, 5, 7, 9, 11
    val majorIntervals = listOf(0, 2, 4, 5, 7, 9, 11)
    val degreeNames = listOf("I", "II", "III", "IV", "V", "VI", "VII")
    val degreeIdx = Random.nextInt(7)
    val correct = AMERICAN_NOTE_NAMES[(rootIdx + majorIntervals[degreeIdx]) % 12]

    val options = mutableSetOf(correct)
    while (options.size < 4) {
        options.add(AMERICAN_NOTE_NAMES[Random.nextInt(12)])
    }

    return QuestionData(
        question = "¿${degreeNames[degreeIdx]} grado de $root Mayor?",
        options = options.toList().shuffled(),
        correctAnswer = correct
    )
}

private fun generateRelativeQuestion(): QuestionData {
    val rootIdx = Random.nextInt(12)
    val root = AMERICAN_NOTE_NAMES[rootIdx]
    val isMajor = Random.nextBoolean()
    val relativeOffset = if (isMajor) 9 else 3 // major->relative minor = -3 = +9, minor->relative major = +3
    val correct = AMERICAN_NOTE_NAMES[(rootIdx + relativeOffset) % 12]
    val questionType = if (isMajor) "menor" else "Mayor"
    val keyType = if (isMajor) "Mayor" else "menor"

    val options = mutableSetOf(correct)
    while (options.size < 4) {
        options.add(AMERICAN_NOTE_NAMES[Random.nextInt(12)])
    }

    return QuestionData(
        question = "¿Relativo $questionType de $root $keyType?",
        options = options.toList().shuffled(),
        correctAnswer = correct
    )
}

private fun generateScaleFormulaQuestion(): QuestionData {
    val scales = listOf(
        "T-T-S-T-T-T-S" to "Mayor (Jónica)",
        "T-S-T-T-S-T-T" to "Menor natural (Eólica)",
        "T-S-T-T-T-S-T" to "Dórica",
        "S-T-T-T-S-T-T" to "Frigia",
        "T-T-T-S-T-T-S" to "Lidia",
        "T-T-S-T-T-S-T" to "Mixolidia"
    )
    val (formula, correct) = scales[Random.nextInt(scales.size)]

    val options = scales.map { it.second }.shuffled().take(4).toMutableList()
    if (correct !in options) {
        options[Random.nextInt(options.size)] = correct
    }

    return QuestionData(
        question = "Fórmula: $formula\n¿Qué escala es?",
        options = options.shuffled(),
        correctAnswer = correct
    )
}

private fun generateDiatonicChordsQuestion(): QuestionData {
    val rootIdx = Random.nextInt(12)
    val root = AMERICAN_NOTE_NAMES[rootIdx]
    // Major scale diatonic: I ii iii IV V vi vii°
    val majorIntervals = listOf(0, 2, 4, 5, 7, 9, 11)
    val qualities = listOf("", "m", "m", "", "", "m", "dim")
    val diatonic = majorIntervals.mapIndexed { i, interval ->
        "${AMERICAN_NOTE_NAMES[(rootIdx + interval) % 12]}${qualities[i]}"
    }
    val correct = diatonic.joinToString(", ")

    // Generate wrong options by shifting one chord
    val options = mutableSetOf(correct)
    while (options.size < 4) {
        val modified = diatonic.toMutableList()
        val changeIdx = Random.nextInt(modified.size)
        val wrongRoot = AMERICAN_NOTE_NAMES[(rootIdx + majorIntervals[changeIdx] + (if (Random.nextBoolean()) 1 else -1) + 12) % 12]
        modified[changeIdx] = "$wrongRoot${qualities[changeIdx]}"
        options.add(modified.joinToString(", "))
    }

    return QuestionData(
        question = "¿Acordes diatónicos de $root Mayor?",
        options = options.toList().shuffled(),
        correctAnswer = correct
    )
}

private fun generateIntervalNameQuestion(): QuestionData {
    val rootIdx = Random.nextInt(12)
    val interval = Random.nextInt(1, 12)
    val root = AMERICAN_NOTE_NAMES[rootIdx]
    val target = AMERICAN_NOTE_NAMES[(rootIdx + interval) % 12]
    val correct = INTERVAL_NAMES[interval]

    val options = mutableSetOf(correct)
    while (options.size < 4) {
        options.add(INTERVAL_NAMES[Random.nextInt(1, INTERVAL_NAMES.size)])
    }

    return QuestionData(
        question = "De $root a $target = ¿qué intervalo?",
        options = options.toList().shuffled(),
        correctAnswer = correct
    )
}
