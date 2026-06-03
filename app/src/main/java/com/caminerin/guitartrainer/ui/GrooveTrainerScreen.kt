package com.caminerin.guitartrainer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caminerin.guitartrainer.audio.GrooveCategory
import com.caminerin.guitartrainer.audio.GrooveCategoryData
import com.caminerin.guitartrainer.audio.GrooveEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class GrooveTab(val label: String) {
    QUICK_PLAY("Tocar ya"),
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
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var tapCount by remember { mutableIntStateOf(0) }
    var tapSum by remember { mutableLongStateOf(0L) }
    var tapFlash by remember { mutableStateOf(false) }

    // Load categories
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

    // Stop on leave
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

        val config = GrooveEngine.PlayConfig(
            bpm = bpm,
            pattern = pattern,
            fills = data.fills,
            complexityLevel = complexityLevel,
            feel = feel,
            swing = swing,
            fillEveryBars = fillEveryBars,
            silenceEveryBars = silenceEveryBars,
            silenceDurationBars = 1,
            countIn = countIn,
            volumes = mapOf(
                "kick" to kickVol, "snare" to snareVol,
                "hihat" to hihatVol, "ride" to rideVol, "crash" to rideVol
            ),
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
                onBeat = { bar, beat -> currentBar = bar; currentBeat = beat },
                onBpmChange = { newBpm -> bpm = newBpm }
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
        tapFlash = true
        if (lastTapTime > 0L && now - lastTapTime < 2000L) {
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

    // Reset tap flash
    LaunchedEffect(tapFlash) {
        if (tapFlash) {
            kotlinx.coroutines.delay(150)
            tapFlash = false
        }
    }

    // ==================== UI LAYOUT ====================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header with back button (matches ToolScreenWrapper / SettingsScreen style)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Caja de Ritmos",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val catName = categories.getOrNull(selectedCategoryIdx)?.displayName ?: ""
                val patName = categoryData?.patterns?.getOrNull(selectedPatternIdx)?.name ?: ""
                if (catName.isNotEmpty()) {
                    Text(
                        "$catName · $patName",
                        fontSize = 11.sp,
                        color = AppColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Tab selector (pill style like metronome)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GrooveTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { selectedTab = tab }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab.label,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Beat indicator row
        GrooveBeatIndicator(
            isPlaying = isPlaying,
            currentBeat = currentBeat,
            currentBar = currentBar
        )

        // Main content (scrollable)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .animateContentSize()
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
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Bottom control bar
        GrooveBottomBar(
            bpm = bpm,
            onBpmChange = { bpm = it },
            isPlaying = isPlaying,
            onPlayStop = { if (isPlaying) stopPlaying() else startPlaying() },
            onTapTempo = { handleTapTempo() },
            tapFlash = tapFlash
        )
    }
}

// ==================== BEAT INDICATOR ====================

@Composable
private fun GrooveBeatIndicator(isPlaying: Boolean, currentBeat: Int, currentBar: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        for (beat in 0 until 4) {
            val isActive = isPlaying && currentBeat == beat

            val baseColor = when {
                isActive && beat == 0 -> AppColors.error  // downbeat = red
                isActive -> AppColors.success             // other beats = green
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val color by animateColorAsState(targetValue = baseColor, animationSpec = tween(80), label = "beat$beat")
            val pulseScale by animateFloatAsState(
                targetValue = if (isActive) 1.25f else 1.0f,
                animationSpec = tween(100),
                label = "pulse$beat"
            )

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${beat + 1}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            if (beat < 3) Spacer(modifier = Modifier.width(8.dp))
        }
        // Bar counter
        if (isPlaying) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                "Compás ${currentBar + 1}",
                fontSize = 11.sp,
                color = AppColors.textMuted,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

// ==================== BOTTOM BAR ====================

@Composable
private fun GrooveBottomBar(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    isPlaying: Boolean,
    onPlayStop: () -> Unit,
    onTapTempo: () -> Unit,
    tapFlash: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.navBar)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // BPM control
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onBpmChange((bpm - 1).coerceAtLeast(40)) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("−", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$bpm", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("BPM", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onBpmChange((bpm + 1).coerceAtMost(240)) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Play/Stop button
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (isPlaying) AppColors.error else AppColors.success)
                .clickable { onPlayStop() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Parar" else "Tocar",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        // Tap tempo with flash feedback
        val tapBg by animateColorAsState(
            targetValue = if (tapFlash) GradientColors.accent else MaterialTheme.colorScheme.surfaceVariant,
            animationSpec = tween(100),
            label = "tap"
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(tapBg)
                .clickable { onTapTempo() }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                "TAP",
                color = if (tapFlash) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ==================== QUICK PLAY TAB ====================

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
    // Category selector
    GrooveSectionLabel("Estilo")
    ScrollableChipRow(
        items = categories.map { it.displayName },
        selectedIdx = selectedCategoryIdx,
        onSelect = onCategoryChange
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Pattern selector
    if (categoryData != null && categoryData.patterns.isNotEmpty()) {
        GrooveSectionLabel("Groove (${categoryData.patterns.size})")
        ScrollableChipRow(
            items = categoryData.patterns.map { it.name },
            selectedIdx = selectedPatternIdx,
            onSelect = onPatternChange
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    // Complexity level
    GrooveSectionLabel("Intensidad")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val labels = listOf("Simple", "Normal", "Groove", "Ghost", "Full")
        labels.forEachIndexed { idx, label ->
            val level = idx + 1
            val selected = complexityLevel == level
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onComplexityChange(level) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Feel selector
    GrooveSectionLabel("Feel")
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onFeelChange(f) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Swing slider
    val swingLabel = when {
        swing < 0.05f -> "Straight"
        swing < 0.3f -> "Light Swing"
        swing < 0.5f -> "Shuffle"
        else -> "Heavy Shuffle"
    }
    GrooveSectionLabel("Swing: $swingLabel")
    Slider(
        value = swing,
        onValueChange = onSwingChange,
        valueRange = 0f..0.67f,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Fill frequency
    GrooveSectionLabel("Fill automático")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val fillOptions = listOf(0 to "Off", 4 to "c/4", 8 to "c/8", 12 to "c/12")
        fillOptions.forEach { (bars, label) ->
            val selected = fillEveryBars == bars
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onFillChange(bars) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Count-in toggle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (countIn) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                1.dp,
                if (countIn) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onCountInChange(!countIn) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Count-in (4 clicks)",
            color = if (countIn) Color.White else AppColors.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (countIn) "ON" else "OFF",
            color = if (countIn) AppColors.success else AppColors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Mixer
    GrooveSectionLabel("Mixer")
    GrooveMixerSlider("Kick", kickVol, onKickVolChange)
    GrooveMixerSlider("Snare", snareVol, onSnareVolChange)
    GrooveMixerSlider("Hi-Hat", hihatVol, onHihatVolChange)
    GrooveMixerSlider("Ride / Crash", rideVol, onRideVolChange)

    Spacer(modifier = Modifier.height(16.dp))
}

// ==================== TRAINER TAB ====================

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
    GrooveSectionLabel("Silencio inteligente")
    Text(
        "La batería desaparece cada X compases para entrenar tu pulso interno.",
        color = AppColors.textMuted,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val options = listOf(0 to "Off", 2 to "c/2", 4 to "c/4", 8 to "c/8")
        options.forEach { (bars, label) ->
            val selected = silenceEveryBars == bars
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSilenceChange(bars) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Tempo progression
    GrooveSectionLabel("Progresión de tempo")
    Text(
        "Sube el BPM automáticamente cada 8 compases hasta el objetivo.",
        color = AppColors.textMuted,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Objetivo:", color = AppColors.textSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onTempoTargetChange((tempoTarget - 5).coerceAtLeast(0)) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) { Text("−", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            if (tempoTarget > 0) "$tempoTarget BPM" else "Off",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onTempoTargetChange((tempoTarget + 5).coerceAtMost(240)) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) { Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }

    if (tempoTarget > 0) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Incremento:", color = AppColors.textSecondary, fontSize = 12.sp)
            listOf(2, 5, 10).forEach { inc ->
                val selected = tempoIncrement == inc
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onTempoIncrementChange(inc) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "+$inc",
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "De $bpm → $tempoTarget BPM (+$tempoIncrement cada 8 compases)",
            color = AppColors.textSecondary,
            fontSize = 11.sp
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Preset routines
    GrooveSectionLabel("Rutinas rápidas")
    val routines = listOf(
        Triple("5 min calentamiento", "70→100 BPM, silencio c/8", Triple(100, 5, 8)),
        Triple("10 min resistencia", "90 BPM fijo, silencio c/4", Triple(0, 5, 4)),
        Triple("15 min progresivo", "60→120 BPM, +5 c/8 compases", Triple(120, 5, 0))
    )
    routines.forEach { (name, desc, config) ->
        val (target, inc, silence) = config
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    onTempoTargetChange(target)
                    onTempoIncrementChange(inc)
                    onSilenceChange(silence)
                }
                .padding(12.dp)
        ) {
            Column {
                Text(
                    name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    desc,
                    color = AppColors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
}

// ==================== SHARED COMPONENTS ====================

@Composable
private fun GrooveSectionLabel(text: String) {
    Text(
        text,
        color = AppColors.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun ScrollableChipRow(items: List<String>, selectedIdx: Int, onSelect: (Int) -> Unit) {
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
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(idx) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    label,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GrooveMixerSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = AppColors.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(72.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${(value * 100).toInt()}%",
            color = AppColors.textMuted,
            fontSize = 10.sp,
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.End
        )
    }
}
