package com.caminerin.guitartrainer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caminerin.guitartrainer.audio.GrooveCategory
import com.caminerin.guitartrainer.audio.GrooveCategoryData
import com.caminerin.guitartrainer.audio.GrooveEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Tab modes for the Groove Trainer
private enum class GrooveTab(val label: String) {
    QUICK_PLAY("Tocar"),
    TRAINER("Entrenar")
}

@Composable
fun GrooveTrainerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State
    var selectedTab by remember { mutableStateOf(GrooveTab.QUICK_PLAY) }
    var isPlaying by remember { mutableStateOf(false) }
    var bpm by remember { mutableIntStateOf(100) }
    var complexityLevel by remember { mutableIntStateOf(3) }
    var currentBar by remember { mutableIntStateOf(0) }
    var currentBeat by remember { mutableIntStateOf(0) }
    var playJob by remember { mutableStateOf<Job?>(null) }

    // Pattern selection
    var categories by remember { mutableStateOf<List<GrooveCategory>>(emptyList()) }
    var selectedCategoryIdx by remember { mutableIntStateOf(0) }
    var categoryData by remember { mutableStateOf<GrooveCategoryData?>(null) }
    var selectedPatternIdx by remember { mutableIntStateOf(0) }

    // Groove settings
    var feel by remember { mutableStateOf(GrooveEngine.Feel.NATURAL) }
    var swing by remember { mutableFloatStateOf(0f) }
    var fillEveryBars by remember { mutableIntStateOf(0) }
    var countIn by remember { mutableStateOf(false) }

    // Mixer volumes
    var kickVol by remember { mutableFloatStateOf(1f) }
    var snareVol by remember { mutableFloatStateOf(1f) }
    var hihatVol by remember { mutableFloatStateOf(1f) }
    var rideVol by remember { mutableFloatStateOf(1f) }

    // Trainer mode
    var silenceEveryBars by remember { mutableIntStateOf(0) }
    var tempoTarget by remember { mutableIntStateOf(0) }
    var tempoIncrement by remember { mutableIntStateOf(5) }

    // Tap tempo
    var lastTapTime by remember { mutableStateOf(0L) }
    var tapCount by remember { mutableIntStateOf(0) }
    var tapSum by remember { mutableStateOf(0L) }

    // Load categories on first compose
    LaunchedEffect(Unit) {
        GrooveEngine.init(context)
        categories = GrooveEngine.getCategories()
    }

    // Load patterns when category changes
    LaunchedEffect(selectedCategoryIdx, categories) {
        if (categories.isNotEmpty()) {
            val catId = categories[selectedCategoryIdx].id
            categoryData = GrooveEngine.loadCategoryPatterns(context, catId)
            selectedPatternIdx = 0
        }
    }

    // Stop when leaving
    DisposableEffect(Unit) {
        onDispose {
            GrooveEngine.stop()
            playJob?.cancel()
        }
    }

    fun startPlaying() {
        val data = categoryData ?: return
        if (data.patterns.isEmpty()) return
        val pattern = data.patterns[selectedPatternIdx.coerceIn(0, data.patterns.size - 1)]
        val fill = if (data.fills.isNotEmpty()) data.fills[0] else null

        val config = GrooveEngine.PlayConfig(
            bpm = bpm,
            pattern = pattern,
            fill = fill,
            complexityLevel = complexityLevel,
            feel = feel,
            swing = swing,
            fillEveryBars = fillEveryBars,
            silenceEveryBars = silenceEveryBars,
            silenceDurationBars = 1,
            countIn = countIn,
            volumes = mapOf("kick" to kickVol, "snare" to snareVol, "hihat" to hihatVol, "ride" to rideVol, "crash" to rideVol),
            tempoProgression = if (tempoTarget > bpm) GrooveEngine.TempoProgression(
                targetBpm = tempoTarget,
                bpmIncrement = tempoIncrement,
                barsPerStep = 8
            ) else null
        )

        playJob?.cancel()
        playJob = scope.launch {
            GrooveEngine.playGroove(
                context = context,
                config = config,
                onBeat = { bar, beat ->
                    currentBar = bar
                    currentBeat = beat
                },
                onBpmChange = { newBpm ->
                    bpm = newBpm
                }
            )
        }
        isPlaying = true
    }

    fun stopPlaying() {
        GrooveEngine.stop()
        playJob?.cancel()
        isPlaying = false
        currentBeat = 0
        currentBar = 0
    }

    fun handleTapTempo() {
        val now = System.currentTimeMillis()
        if (lastTapTime > 0 && now - lastTapTime < 2000) {
            tapSum += (now - lastTapTime)
            tapCount++
            if (tapCount >= 2) {
                val avgMs = tapSum.toFloat() / tapCount
                bpm = (60000f / avgMs).toInt().coerceIn(40, 240)
            }
        } else {
            tapCount = 0
            tapSum = 0L
        }
        lastTapTime = now
    }

    // UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF1a1033), Color(0xFF0d1b2a))))
    ) {
        // Top bar with back button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Volver",
                tint = GradientColors.accent,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Caja de Ritmos", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // Category + Pattern info
        val catName = categories.getOrNull(selectedCategoryIdx)?.displayName ?: ""
        val patName = categoryData?.patterns?.getOrNull(selectedPatternIdx)?.name ?: ""
        Text(
            "$catName · $patName",
            fontSize = 12.sp,
            color = Color(0xFF8899aa),
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
        )

        // Tab selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GrooveTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Color(0xFF2a4a6a) else Color(0xFF1a2a3a))
                        .border(1.dp, if (selected) Color(0xFF4a8aba) else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { selectedTab = tab }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tab.label, color = if (selected) Color.White else Color(0xFF6688aa), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main content - scrollable
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTab) {
                GrooveTab.QUICK_PLAY -> QuickPlayContent(
                    categories = categories,
                    selectedCategoryIdx = selectedCategoryIdx,
                    onCategoryChange = { selectedCategoryIdx = it; selectedPatternIdx = 0 },
                    categoryData = categoryData,
                    selectedPatternIdx = selectedPatternIdx,
                    onPatternChange = { selectedPatternIdx = it },
                    complexityLevel = complexityLevel,
                    onComplexityChange = { complexityLevel = it },
                    feel = feel,
                    onFeelChange = { feel = it },
                    swing = swing,
                    onSwingChange = { swing = it },
                    fillEveryBars = fillEveryBars,
                    onFillChange = { fillEveryBars = it },
                    countIn = countIn,
                    onCountInChange = { countIn = it },
                    kickVol = kickVol, onKickVolChange = { kickVol = it },
                    snareVol = snareVol, onSnareVolChange = { snareVol = it },
                    hihatVol = hihatVol, onHihatVolChange = { hihatVol = it },
                    rideVol = rideVol, onRideVolChange = { rideVol = it }
                )
                GrooveTab.TRAINER -> TrainerContent(
                    silenceEveryBars = silenceEveryBars,
                    onSilenceChange = { silenceEveryBars = it },
                    tempoTarget = tempoTarget,
                    onTempoTargetChange = { tempoTarget = it },
                    tempoIncrement = tempoIncrement,
                    onTempoIncrementChange = { tempoIncrement = it },
                    bpm = bpm
                )
            }
        }

        // Bottom control bar: BPM + Play/Stop + Tap + Beat indicator
        BottomControlBar(
            bpm = bpm,
            onBpmChange = { bpm = it },
            isPlaying = isPlaying,
            onPlayStop = { if (isPlaying) stopPlaying() else startPlaying() },
            onTapTempo = { handleTapTempo() },
            currentBeat = currentBeat,
            currentBar = currentBar
        )
    }
}

@Composable
private fun BottomControlBar(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    isPlaying: Boolean,
    onPlayStop: () -> Unit,
    onTapTempo: () -> Unit,
    currentBeat: Int,
    currentBar: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0d1520))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Beat indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            for (i in 0 until 4) {
                val isActive = isPlaying && currentBeat == i
                val color by animateColorAsState(
                    if (isActive) {
                        if (i == 0) Color(0xFFff6644) else Color(0xFF44aaff)
                    } else Color(0xFF2a3a4a),
                    label = "beat"
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                if (i < 3) Spacer(modifier = Modifier.width(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // BPM control
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1a2a3a))
                        .clickable { onBpmChange((bpm - 1).coerceAtLeast(40)) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("−", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$bpm", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("BPM", color = Color(0xFF6688aa), fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1a2a3a))
                        .clickable { onBpmChange((bpm + 1).coerceAtMost(240)) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Play/Stop button
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) Color(0xFFcc3333) else Color(0xFF22aa55))
                    .clickable { onPlayStop() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Tap tempo
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1a2a3a))
                    .clickable { onTapTempo() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("TAP", color = Color(0xFF88aacc), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun QuickPlayContent(
    categories: List<GrooveCategory>,
    selectedCategoryIdx: Int,
    onCategoryChange: (Int) -> Unit,
    categoryData: GrooveCategoryData?,
    selectedPatternIdx: Int,
    onPatternChange: (Int) -> Unit,
    complexityLevel: Int,
    onComplexityChange: (Int) -> Unit,
    feel: GrooveEngine.Feel,
    onFeelChange: (GrooveEngine.Feel) -> Unit,
    swing: Float,
    onSwingChange: (Float) -> Unit,
    fillEveryBars: Int,
    onFillChange: (Int) -> Unit,
    countIn: Boolean,
    onCountInChange: (Boolean) -> Unit,
    kickVol: Float, onKickVolChange: (Float) -> Unit,
    snareVol: Float, onSnareVolChange: (Float) -> Unit,
    hihatVol: Float, onHihatVolChange: (Float) -> Unit,
    rideVol: Float, onRideVolChange: (Float) -> Unit
) {
    // Category selector (horizontal chips)
    SectionLabel("Estilo")
    ChipRow(
        items = categories.map { it.displayName },
        selectedIdx = selectedCategoryIdx,
        onSelect = onCategoryChange
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Pattern selector
    if (categoryData != null && categoryData.patterns.isNotEmpty()) {
        SectionLabel("Groove")
        ChipRow(
            items = categoryData.patterns.map { it.name },
            selectedIdx = selectedPatternIdx,
            onSelect = onPatternChange
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // Complexity level
    SectionLabel("Intensidad")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val labels = listOf("Simple", "Normal", "Groove", "Ghost", "Full")
        labels.forEachIndexed { idx, label ->
            val level = idx + 1
            val selected = complexityLevel == level
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) Color(0xFF2a5a8a) else Color(0xFF1a2a3a))
                    .border(1.dp, if (selected) Color(0xFF4a9aca) else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { onComplexityChange(level) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (selected) Color.White else Color(0xFF6688aa), fontSize = 11.sp)
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Feel
    SectionLabel("Feel")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GrooveEngine.Feel.entries.forEach { f ->
            val selected = feel == f
            val label = when (f) {
                GrooveEngine.Feel.TIGHT -> "Tight"
                GrooveEngine.Feel.NATURAL -> "Natural"
                GrooveEngine.Feel.LOOSE -> "Loose"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) Color(0xFF2a5a3a) else Color(0xFF1a2a3a))
                    .border(1.dp, if (selected) Color(0xFF4aca6a) else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { onFeelChange(f) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (selected) Color.White else Color(0xFF6688aa), fontSize = 12.sp)
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Swing
    SectionLabel("Swing: ${(swing * 100).toInt()}%")
    Slider(
        value = swing,
        onValueChange = onSwingChange,
        valueRange = 0f..0.67f,
        steps = 5,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFF4a9aca),
            activeTrackColor = Color(0xFF2a5a8a)
        ),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Fills
    SectionLabel("Fill automático")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val fillOptions = listOf(0 to "Off", 4 to "4", 8 to "8", 12 to "12")
        fillOptions.forEach { (bars, label) ->
            val selected = fillEveryBars == bars
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) Color(0xFF5a2a5a) else Color(0xFF1a2a3a))
                    .border(1.dp, if (selected) Color(0xFFaa4aaa) else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { onFillChange(bars) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (bars == 0) label else "c/$label",
                    color = if (selected) Color.White else Color(0xFF6688aa),
                    fontSize = 12.sp
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Count-in toggle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (countIn) Color(0xFF2a4a3a) else Color(0xFF1a2a3a))
            .clickable { onCountInChange(!countIn) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Count-in (4 clicks)", color = if (countIn) Color.White else Color(0xFF6688aa), fontSize = 13.sp)
        Spacer(modifier = Modifier.weight(1f))
        Text(if (countIn) "ON" else "OFF", color = if (countIn) Color(0xFF44cc66) else Color(0xFF556677), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Mixer
    SectionLabel("Mixer")
    MixerSlider("Kick", kickVol, onKickVolChange)
    MixerSlider("Snare", snareVol, onSnareVolChange)
    MixerSlider("Hi-Hat", hihatVol, onHihatVolChange)
    MixerSlider("Ride/Crash", rideVol, onRideVolChange)

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun TrainerContent(
    silenceEveryBars: Int,
    onSilenceChange: (Int) -> Unit,
    tempoTarget: Int,
    onTempoTargetChange: (Int) -> Unit,
    tempoIncrement: Int,
    onTempoIncrementChange: (Int) -> Unit,
    bpm: Int
) {
    SectionLabel("Silencio inteligente")
    Text(
        "La batería desaparece cada X compases para entrenar tu pulso interno.",
        color = Color(0xFF6688aa), fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val options = listOf(0 to "Off", 4 to "c/4", 8 to "c/8", 2 to "c/2")
        options.forEach { (bars, label) ->
            val selected = silenceEveryBars == bars
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) Color(0xFF4a3a2a) else Color(0xFF1a2a3a))
                    .border(1.dp, if (selected) Color(0xFFcc8844) else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { onSilenceChange(bars) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (selected) Color.White else Color(0xFF6688aa), fontSize = 12.sp)
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Tempo progression
    SectionLabel("Progresión de tempo")
    Text(
        "Sube el BPM automáticamente cada 8 compases hasta el objetivo.",
        color = Color(0xFF6688aa), fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Objetivo:", color = Color(0xFF8899aa), fontSize = 12.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1a2a3a))
                .clickable { onTempoTargetChange((tempoTarget - 5).coerceAtLeast(0)) }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) { Text("−", color = Color.White, fontSize = 16.sp) }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            if (tempoTarget > 0) "$tempoTarget BPM" else "Off",
            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1a2a3a))
                .clickable { onTempoTargetChange((tempoTarget + 5).coerceAtMost(240)) }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) { Text("+", color = Color.White, fontSize = 16.sp) }
    }

    if (tempoTarget > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Incremento:", color = Color(0xFF8899aa), fontSize = 12.sp)
            Spacer(modifier = Modifier.width(8.dp))
            val increments = listOf(2, 5, 10)
            increments.forEach { inc ->
                val selected = tempoIncrement == inc
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) Color(0xFF2a4a3a) else Color(0xFF1a2a3a))
                        .clickable { onTempoIncrementChange(inc) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("+$inc", color = if (selected) Color.White else Color(0xFF6688aa), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "De $bpm a $tempoTarget BPM (+$tempoIncrement c/8 compases)",
            color = Color(0xFF88aacc), fontSize = 11.sp
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Preset routines
    SectionLabel("Rutinas rápidas")
    val routines = listOf(
        "5 min calentamiento" to "70→100 BPM, silencio c/8",
        "10 min resistencia" to "90 BPM fijo, silencio c/4",
        "15 min progresivo" to "60→120 BPM, +5 c/8 compases"
    )
    routines.forEach { (name, desc) ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1a2a3a))
                .clickable {
                    when (name) {
                        "5 min calentamiento" -> {
                            onTempoTargetChange(100)
                            onTempoIncrementChange(5)
                            onSilenceChange(8)
                        }
                        "10 min resistencia" -> {
                            onTempoTargetChange(0)
                            onSilenceChange(4)
                        }
                        "15 min progresivo" -> {
                            onTempoTargetChange(120)
                            onTempoIncrementChange(5)
                            onSilenceChange(0)
                        }
                    }
                }
                .padding(12.dp)
        ) {
            Column {
                Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(desc, color = Color(0xFF6688aa), fontSize = 11.sp)
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
}

// ==================== REUSABLE COMPONENTS ====================

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Color(0xFFaabbcc),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun ChipRow(items: List<String>, selectedIdx: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEachIndexed { idx, label ->
            val selected = idx == selectedIdx
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) Color(0xFF2a5a8a) else Color(0xFF1a2a3a))
                    .border(1.dp, if (selected) Color(0xFF4a9aca) else Color(0xFF2a3a4a), RoundedCornerShape(16.dp))
                    .clickable { onSelect(idx) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(label, color = if (selected) Color.White else Color(0xFF6688aa), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MixerSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFF8899aa), fontSize = 11.sp, modifier = Modifier.width(70.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF4a9aca),
                activeTrackColor = Color(0xFF2a5a8a),
                inactiveTrackColor = Color(0xFF1a2a3a)
            ),
            modifier = Modifier.weight(1f)
        )
        Text("${(value * 100).toInt()}%", color = Color(0xFF6688aa), fontSize = 10.sp, modifier = Modifier.width(35.dp), textAlign = TextAlign.End)
    }
}


