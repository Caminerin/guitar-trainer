package com.caminerin.guitartrainer.ui

import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import kotlin.random.Random

// ═══════════════════════════════════════════════════════
// Fretboard Quiz — interactive exercises 2.1, 2.2, 2.4, 2.6
// User taps cells on the fretboard or note-name buttons.
// ═══════════════════════════════════════════════════════

private enum class FretboardMode { TAP_ALL, TAP_ONE, BUTTON }

private data class FretboardQuestion(
    val prompt: String,
    val mode: FretboardMode,
    val highlightCells: Set<Pair<Int, Int>> = emptySet(),
    val targetCells: Set<Pair<Int, Int>> = emptySet(),
    val buttons: List<String> = emptyList(),
    val correctButton: String = "",
    val maxFret: Int = 12
)

private data class FretHitTarget(
    val stringIdx: Int,
    val fret: Int,
    val noteIdx: Int,
    val cx: Float,
    val cy: Float,
    val radius: Float
)

@Composable
fun FretboardQuizScreen(
    exercise: QuizExercise,
    questionCount: Int,
    difficulty: QuizDifficulty,
    onBack: () -> Unit
) {
    var currentQuestion by remember { mutableIntStateOf(0) }
    var correctCount by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf(generateFretboardQuestion(exercise.id, difficulty)) }
    val tappedCorrect = remember { mutableStateListOf<Pair<Int, Int>>() }
    val tappedWrong = remember { mutableStateListOf<Pair<Int, Int>>() }
    var selectedButton by remember { mutableStateOf<String?>(null) }
    var answered by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }
    var feedbackIsCorrect by remember { mutableStateOf(true) }

    if (feedbackText.isNotEmpty()) {
        LaunchedEffect(feedbackText) {
            delay(2500)
            feedbackText = ""
        }
    }

    if (showResult) {
        QuizResultScreen(
            correct = correctCount,
            total = questionCount,
            avgTime = 0L,
            exercise = exercise,
            onRepeat = {
                currentQuestion = 0; correctCount = 0; showResult = false
                question = generateFretboardQuestion(exercise.id, difficulty)
                tappedCorrect.clear(); tappedWrong.clear()
                selectedButton = null; answered = false
            },
            onBack = onBack
        )
        return
    }

    val density = LocalDensity.current
    val zoom = 1.4f
    val fretWidthDp = (60f * zoom).dp
    val openStringWidth = 48.dp
    val totalWidthDp = openStringWidth + (question.maxFret * 60 * zoom + 60).dp

    fun advanceQuestion() {
        if (currentQuestion + 1 >= questionCount) {
            showResult = true
        } else {
            currentQuestion++
            question = generateFretboardQuestion(exercise.id, difficulty)
            tappedCorrect.clear(); tappedWrong.clear()
            selectedButton = null; answered = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(SHARED_BG)) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SHARED_TOOLBAR)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text(
                "${exercise.number} ${exercise.title}",
                color = exercise.categoryColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text("${currentQuestion + 1}/$questionCount", color = Color.White.copy(0.5f), fontSize = 12.sp)
        }

        // Progress bar
        LinearProgressIndicator(
            progress = (currentQuestion.toFloat() + 1) / questionCount,
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = exercise.categoryColor,
            trackColor = Color.White.copy(0.1f)
        )

        // Question prompt
        Text(
            question.prompt,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        // Fretboard
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
            ) {
                val hitTargets = remember { mutableStateListOf<FretHitTarget>() }
                Canvas(
                    modifier = Modifier
                        .width(totalWidthDp)
                        .fillMaxHeight()
                        .pointerInput(question) {
                            detectTapGestures { tapOffset ->
                                if (answered) return@detectTapGestures
                                if (question.mode == FretboardMode.BUTTON) return@detectTapGestures

                                var closest: FretHitTarget? = null
                                var closestDist = Float.MAX_VALUE
                                for (t in hitTargets) {
                                    val dx = tapOffset.x - t.cx
                                    val dy = tapOffset.y - t.cy
                                    val d = sqrt(dx * dx + dy * dy)
                                    if (d < t.radius * 1.5f && d < closestDist) {
                                        closestDist = d; closest = t
                                    }
                                }
                                if (closest == null) return@detectTapGestures
                                val key = closest.stringIdx to closest.fret
                                if (key in tappedCorrect || key in tappedWrong) return@detectTapGestures

                                when (question.mode) {
                                    FretboardMode.TAP_ALL -> {
                                        if (key in question.targetCells) {
                                            tappedCorrect.add(key)
                                            feedbackText = "✓ ${getNoteName(closest.noteIdx)}"
                                            feedbackIsCorrect = true
                                            if (tappedCorrect.toSet().containsAll(question.targetCells)) {
                                                if (tappedWrong.isEmpty()) correctCount++
                                                answered = true
                                            }
                                        } else {
                                            tappedWrong.add(key)
                                            feedbackText = "✗ ${getNoteName(closest.noteIdx)}"
                                            feedbackIsCorrect = false
                                        }
                                    }
                                    FretboardMode.TAP_ONE -> {
                                        if (key in question.targetCells) {
                                            tappedCorrect.add(key)
                                            correctCount++
                                            feedbackText = "✓ ¡Correcto!"
                                            feedbackIsCorrect = true
                                        } else {
                                            tappedWrong.add(key)
                                            feedbackText = "✗ ${getNoteName(closest.noteIdx)}"
                                            feedbackIsCorrect = false
                                        }
                                        answered = true
                                    }
                                    FretboardMode.BUTTON -> { /* handled by button row */ }
                                }
                            }
                        }
                ) {
                    hitTargets.clear()
                    drawFretboardQuiz(
                        maxFret = question.maxFret,
                        highlightCells = question.highlightCells,
                        targetCells = question.targetCells,
                        tappedCorrect = tappedCorrect.toSet(),
                        tappedWrong = tappedWrong.toSet(),
                        answered = answered,
                        fretWidthPx = with(density) { fretWidthDp.toPx() },
                        openStringWidthPx = with(density) { openStringWidth.toPx() },
                        hitTargets = hitTargets
                    )
                }
            }

            // Feedback toast
            if (feedbackText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (feedbackIsCorrect) Color(0xFF2E7D32).copy(0.95f)
                            else Color(0xFFC62828).copy(0.95f)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(feedbackText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Note buttons (BUTTON mode: 2.2, 2.5)
        if (question.mode == FretboardMode.BUTTON && question.buttons.isNotEmpty()) {
            val chunked = question.buttons.chunked(4)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                chunked.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 3.dp)
                    ) {
                        row.forEach { btn ->
                            val isSelected = selectedButton == btn
                            val isCorrect = btn == question.correctButton
                            val bgColor = when {
                                selectedButton == null -> AppColors.cardBg
                                isSelected && isCorrect -> AppColors.success.copy(0.4f)
                                isSelected && !isCorrect -> AppColors.error.copy(0.4f)
                                !isSelected && isCorrect && selectedButton != null -> AppColors.success.copy(0.2f)
                                else -> AppColors.cardBg
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgColor)
                                    .clickable(enabled = !answered) {
                                        selectedButton = btn
                                        answered = true
                                        if (btn == question.correctButton) {
                                            correctCount++
                                            feedbackText = "✓ ¡Correcto!"
                                            feedbackIsCorrect = true
                                        } else {
                                            feedbackText = "✗ Era ${question.correctButton}"
                                            feedbackIsCorrect = false
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(btn, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Siguiente button
        if (answered) {
            Button(
                onClick = { advanceQuestion() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = exercise.categoryColor)
            ) {
                Text("Siguiente", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ─── Question generation ────────────────────────────────────

private fun generateFretboardQuestion(exerciseId: String, difficulty: QuizDifficulty): FretboardQuestion {
    return when (exerciseId) {
        "2.1" -> generateFindNote(difficulty)
        "2.2" -> generateWhatNote(difficulty)
        "2.4" -> generateDegreeOnFretboard(difficulty)
        "2.6" -> generateOctaveOnFretboard(difficulty)
        else -> FretboardQuestion("Ejercicio no disponible", FretboardMode.BUTTON)
    }
}

/** 2.1 — Encuentra la nota: marca todas las X en el mástil. */
private fun generateFindNote(difficulty: QuizDifficulty): FretboardQuestion {
    val targetPc = Random.nextInt(12)
    val targetName = getNoteName(targetPc)
    val maxFret = when (difficulty) {
        QuizDifficulty.EASY -> 5; QuizDifficulty.MEDIUM -> 9; QuizDifficulty.HARD -> 12
    }
    val targets = mutableSetOf<Pair<Int, Int>>()
    for (s in 0..5) for (f in 0..maxFret) {
        if ((STANDARD_TUNING_MIDI[s] + f) % 12 == targetPc) targets.add(s to f)
    }
    return FretboardQuestion(
        prompt = "Marca todas las $targetName en el mástil",
        mode = FretboardMode.TAP_ALL,
        targetCells = targets,
        maxFret = maxFret
    )
}

/** 2.2 — ¿Qué nota es? Se marca una casilla; elige la nota correcta. */
private fun generateWhatNote(difficulty: QuizDifficulty): FretboardQuestion {
    val maxFret = when (difficulty) {
        QuizDifficulty.EASY -> 5; QuizDifficulty.MEDIUM -> 9; QuizDifficulty.HARD -> 12
    }
    val s = Random.nextInt(6)
    val f = Random.nextInt(0, maxFret + 1)
    val noteIdx = (STANDARD_TUNING_MIDI[s] + f) % 12
    val correctName = getNoteName(noteIdx)
    val optCount = when (difficulty) {
        QuizDifficulty.EASY -> 4; QuizDifficulty.MEDIUM -> 6; QuizDifficulty.HARD -> 8
    }
    val options = mutableSetOf(correctName)
    while (options.size < optCount) options.add(getNoteName(Random.nextInt(12)))
    return FretboardQuestion(
        prompt = "¿Qué nota es la marcada?",
        mode = FretboardMode.BUTTON,
        highlightCells = setOf(s to f),
        buttons = options.toList().shuffled(),
        correctButton = correctName,
        maxFret = maxFret
    )
}

/** 2.4 — Tónica → Grado: desde la tónica marcada, pulsa la 3ª/5ª/7ª. */
private fun generateDegreeOnFretboard(difficulty: QuizDifficulty): FretboardQuestion {
    val degrees = when (difficulty) {
        QuizDifficulty.EASY -> listOf(4 to "3ª Mayor", 7 to "5ª Justa")
        QuizDifficulty.MEDIUM -> listOf(
            3 to "3ª menor", 4 to "3ª Mayor", 5 to "4ª Justa", 7 to "5ª Justa"
        )
        QuizDifficulty.HARD -> listOf(
            2 to "2ª Mayor", 3 to "3ª menor", 4 to "3ª Mayor", 5 to "4ª Justa",
            7 to "5ª Justa", 9 to "6ª Mayor", 11 to "7ª Mayor"
        )
    }
    val (interval, degreeName) = degrees[Random.nextInt(degrees.size)]
    val maxFret = 12
    val s = Random.nextInt(6)
    val f = Random.nextInt(0, 8)
    val tonicMidi = STANDARD_TUNING_MIDI[s] + f
    val targetMidi = tonicMidi + interval
    val targets = mutableSetOf<Pair<Int, Int>>()
    for (s2 in 0..5) {
        val f2 = targetMidi - STANDARD_TUNING_MIDI[s2]
        if (f2 in 0..maxFret) targets.add(s2 to f2)
    }
    if (targets.isEmpty()) return generateDegreeOnFretboard(difficulty)
    val tonicName = getNoteName(tonicMidi % 12)
    return FretboardQuestion(
        prompt = "Desde $tonicName: pulsa la $degreeName",
        mode = FretboardMode.TAP_ONE,
        highlightCells = setOf(s to f),
        targetCells = targets,
        maxFret = maxFret
    )
}

/** 2.6 — Octavas: se marca una nota; encuentra la misma una octava arriba. */
private fun generateOctaveOnFretboard(difficulty: QuizDifficulty): FretboardQuestion {
    val maxFret = when (difficulty) {
        QuizDifficulty.EASY -> 12; QuizDifficulty.MEDIUM -> 15; QuizDifficulty.HARD -> 19
    }
    val s = Random.nextInt(6)
    val f = Random.nextInt(0, 7)
    val sourceMidi = STANDARD_TUNING_MIDI[s] + f
    val targetMidi = sourceMidi + 12
    val targets = mutableSetOf<Pair<Int, Int>>()
    for (s2 in 0..5) {
        val f2 = targetMidi - STANDARD_TUNING_MIDI[s2]
        if (f2 in 0..maxFret) targets.add(s2 to f2)
    }
    if (targets.isEmpty()) return generateOctaveOnFretboard(difficulty)
    val noteName = getNoteName(sourceMidi % 12)
    return FretboardQuestion(
        prompt = "$noteName: encuentra su octava ↑",
        mode = FretboardMode.TAP_ONE,
        highlightCells = setOf(s to f),
        targetCells = targets,
        maxFret = maxFret
    )
}

// ─── Fretboard drawing ──────────────────────────────────────

private fun DrawScope.drawFretboardQuiz(
    maxFret: Int,
    highlightCells: Set<Pair<Int, Int>>,
    targetCells: Set<Pair<Int, Int>>,
    tappedCorrect: Set<Pair<Int, Int>>,
    tappedWrong: Set<Pair<Int, Int>>,
    answered: Boolean,
    fretWidthPx: Float,
    openStringWidthPx: Float,
    hitTargets: MutableList<FretHitTarget>
) {
    val h = size.height
    val topPad = h * 0.08f
    val bottomPad = h * 0.08f
    val fbTop = topPad
    val fbBottom = h - bottomPad
    val fbHeight = fbBottom - fbTop
    val stringSpacing = fbHeight / 7f
    val nutX = openStringWidthPx
    val nutWidth = 12f

    // Wood background
    drawRoundRect(
        FRETBOARD_WOOD, Offset(nutX, fbTop - 4f),
        Size(size.width - nutX, fbHeight + 8f), CornerRadius(3f)
    )
    // Nut
    drawRect(FRETBOARD_NUT, Offset(nutX, fbTop - 6f), Size(nutWidth, fbHeight + 12f))
    // Fret wires
    for (fret in 1..maxFret) {
        val x = nutX + nutWidth + fret * fretWidthPx
        drawLine(FRETBOARD_FRET_WIRE, Offset(x, fbTop - 2f), Offset(x, fbBottom + 2f), strokeWidth = 2.5f)
    }
    // Inlay dots
    val dotRadius = (fretWidthPx * 0.08f).coerceIn(4f, 12f)
    for (fret in listOf(3, 5, 7, 9, 15, 17, 19, 21)) {
        if (fret > maxFret) continue
        val cx = nutX + nutWidth + (fret - 0.5f) * fretWidthPx
        drawCircle(FRETBOARD_INLAY, dotRadius, Offset(cx, fbBottom + bottomPad * 0.5f))
    }
    for (fret in listOf(12)) {
        if (fret > maxFret) continue
        val cx = nutX + nutWidth + (fret - 0.5f) * fretWidthPx
        drawCircle(FRETBOARD_INLAY, dotRadius, Offset(cx, fbBottom + bottomPad * 0.3f))
        drawCircle(FRETBOARD_INLAY, dotRadius, Offset(cx, fbBottom + bottomPad * 0.7f))
    }
    // Fret numbers
    val fretNumPaint = AndroidPaint().apply {
        color = android.graphics.Color.argb(180, 200, 200, 200)
        textSize = 56f; textAlign = AndroidPaint.Align.CENTER
        isFakeBoldText = true; isAntiAlias = true
    }
    for (fret in 1..maxFret) {
        val x = nutX + nutWidth + (fret - 0.5f) * fretWidthPx
        drawContext.canvas.nativeCanvas.drawText("$fret", x, fbTop - 10f, fretNumPaint)
    }
    // Strings
    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (6 - s)
        drawLine(
            FRETBOARD_STRING_COLORS[s], Offset(nutX, y), Offset(size.width, y),
            strokeWidth = FRETBOARD_STRING_WIDTHS[s]
        )
    }
    // Open string names
    val openPaint = AndroidPaint().apply {
        color = android.graphics.Color.argb(200, 240, 240, 240)
        textSize = 60f; textAlign = AndroidPaint.Align.CENTER
        isFakeBoldText = true; isAntiAlias = true
    }
    val openNames = getOpenStringNames()
    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (6 - s)
        drawContext.canvas.nativeCanvas.drawText(openNames[s], nutX * 0.5f, y + 20f, openPaint)
    }

    // Notes / hit targets
    val noteRadius = (stringSpacing * 0.42f).coerceIn(32f, 72f)
    val notePaint = AndroidPaint().apply {
        color = android.graphics.Color.WHITE; textSize = 44f
        textAlign = AndroidPaint.Align.CENTER; isFakeBoldText = true; isAntiAlias = true
    }

    for (s in 0 until 6) {
        val openNote = STANDARD_TUNING_MIDI[s]
        val y = fbTop + stringSpacing * (6 - s)

        for (fret in 0..maxFret) {
            val noteIdx = (openNote + fret) % 12
            val cx = if (fret == 0) nutX * 0.5f else nutX + nutWidth + (fret - 0.5f) * fretWidthPx
            val key = s to fret
            hitTargets.add(FretHitTarget(s, fret, noteIdx, cx, y, noteRadius))

            val isHighlight = key in highlightCells
            val isCorrectTap = key in tappedCorrect
            val isWrongTap = key in tappedWrong
            val showAsTarget = answered && key in targetCells && !isCorrectTap

            when {
                isHighlight && !isCorrectTap && !isWrongTap -> {
                    drawCircle(Color(0x55000000), noteRadius + 2f, Offset(cx + 1f, y + 1.5f))
                    drawCircle(SHARED_ACCENT, noteRadius, Offset(cx, y))
                    drawCircle(Color(0x44000000), noteRadius, Offset(cx, y), style = Stroke(2f))
                    notePaint.color = android.graphics.Color.WHITE
                    val label = getNoteName(noteIdx)
                    drawContext.canvas.nativeCanvas.drawText(label, cx, y + notePaint.textSize * 0.35f, notePaint)
                }
                isCorrectTap -> {
                    drawCircle(Color(0x55000000), noteRadius + 2f, Offset(cx + 1f, y + 1.5f))
                    drawCircle(AppColors.success, noteRadius, Offset(cx, y))
                    drawCircle(Color(0x44000000), noteRadius, Offset(cx, y), style = Stroke(2f))
                    notePaint.color = android.graphics.Color.WHITE
                    val label = getNoteName(noteIdx)
                    drawContext.canvas.nativeCanvas.drawText(label, cx, y + notePaint.textSize * 0.35f, notePaint)
                }
                isWrongTap -> {
                    drawCircle(AppColors.error.copy(0.7f), noteRadius * 0.7f, Offset(cx, y))
                    drawLine(
                        Color.White,
                        Offset(cx - noteRadius * 0.3f, y - noteRadius * 0.3f),
                        Offset(cx + noteRadius * 0.3f, y + noteRadius * 0.3f), 3f
                    )
                    drawLine(
                        Color.White,
                        Offset(cx + noteRadius * 0.3f, y - noteRadius * 0.3f),
                        Offset(cx - noteRadius * 0.3f, y + noteRadius * 0.3f), 3f
                    )
                }
                showAsTarget -> {
                    drawCircle(AppColors.success.copy(0.3f), noteRadius, Offset(cx, y))
                    drawCircle(AppColors.success.copy(0.5f), noteRadius, Offset(cx, y), style = Stroke(2f))
                    notePaint.color = android.graphics.Color.argb(150, 255, 255, 255)
                    val label = getNoteName(noteIdx)
                    drawContext.canvas.nativeCanvas.drawText(label, cx, y + 44f * 0.35f, notePaint)
                    notePaint.color = android.graphics.Color.WHITE
                }
                else -> {
                    if (fret > 0) drawCircle(Color.White.copy(0.06f), noteRadius * 0.5f, Offset(cx, y))
                }
            }
        }
    }
}
