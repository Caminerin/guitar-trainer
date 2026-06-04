package com.caminerin.guitartrainer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caminerin.guitartrainer.audio.DrumEngine
import com.caminerin.guitartrainer.audio.GrooveCategory
import com.caminerin.guitartrainer.audio.GrooveCategoryData
import com.caminerin.guitartrainer.audio.GrooveEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class GrooveTab { QUICK_PLAY, TRAINER }

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

    // Collapsible sections
    var showSettings by remember { mutableStateOf(false) }
    var showMixer by remember { mutableStateOf(false) }

    // Stop other engines and load
    LaunchedEffect(Unit) {
        DrumEngine.stop()
        GrooveEngine.init(context)
        categories = GrooveEngine.getCategories()
    }

    LaunchedEffect(selectedCategoryIdx, categories) {
        if (categories.isNotEmpty()) {
            val catId = categories[selectedCategoryIdx].id
            categoryData = GrooveEngine.loadCategoryPatterns(context, catId)
            selectedPatternIdx = 0
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            GrooveEngine.stop()
            playJob?.cancel()
            GrooveEngine.release()
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

    LaunchedEffect(tapFlash) {
        if (tapFlash) {
            kotlinx.coroutines.delay(150)
            tapFlash = false
        }
    }

    // ==================== LAYOUT ====================
    // Only 2 layers: scrollable content + single bottom control bar
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── SCROLLABLE CONTENT (takes all available space) ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Back + title inline (not a fixed bar)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Volver",
                    tint = GradientColors.accent,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onBack() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Caja de Ritmos",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (isPlaying) {
                    Spacer(modifier = Modifier.width(8.dp))
                    // Inline beat dots
                    for (beat in 0 until 4) {
                        val active = currentBeat == beat
                        val dotColor = when {
                            active && beat == 0 -> AppColors.error
                            active -> AppColors.success
                            else -> AppColors.textMuted.copy(alpha = 0.4f)
                        }
                        val dotScale by animateFloatAsState(
                            targetValue = if (active) 1.3f else 1.0f,
                            animationSpec = tween(80),
                            label = "dot$beat"
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .scale(dotScale)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        if (beat < 3) Spacer(modifier = Modifier.width(3.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "C${currentBar + 1}",
                        fontSize = 10.sp,
                        color = AppColors.textMuted
                    )
                }
            }

            // Current selection subtitle
            val catName = categories.getOrNull(selectedCategoryIdx)?.displayName ?: ""
            val patName = categoryData?.patterns?.getOrNull(selectedPatternIdx)?.name ?: ""
            if (catName.isNotEmpty()) {
                Text(
                    "$catName · $patName",
                    fontSize = 11.sp,
                    color = AppColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Tab content
            when (selectedTab) {
                GrooveTab.QUICK_PLAY -> QuickPlayContent(
                    categories = categories,
                    selectedCategoryIdx = selectedCategoryIdx,
                    onCategoryChange = {
                        selectedCategoryIdx = it; selectedPatternIdx = 0
                        if (isPlaying) { stopPlaying(); scope.launch { kotlinx.coroutines.delay(100); startPlaying() } }
                    },
                    categoryData = categoryData,
                    selectedPatternIdx = selectedPatternIdx,
                    onPatternChange = {
                        selectedPatternIdx = it
                        if (isPlaying) { stopPlaying(); scope.launch { kotlinx.coroutines.delay(100); startPlaying() } }
                    },
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
                    rideVol = rideVol, onRideVolChange = { rideVol = it },
                    showSettings = showSettings,
                    onToggleSettings = { showSettings = !showSettings },
                    showMixer = showMixer,
                    onToggleMixer = { showMixer = !showMixer }
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

        // ── SINGLE BOTTOM BAR ──
        // Order: [Tocar ya] [Entrenar] [BPM 1/3 width] [Play] [TAP]
        GrooveUnifiedBar(
            selectedTab = selectedTab,
            onTabChange = { selectedTab = it },
            bpm = bpm,
            onBpmChange = { bpm = it },
            isPlaying = isPlaying,
            onPlayStop = { if (isPlaying) stopPlaying() else startPlaying() },
            onTapTempo = { handleTapTempo() },
            tapFlash = tapFlash
        )
    }
}

// ==================== UNIFIED BOTTOM BAR ====================

@Composable
private fun GrooveUnifiedBar(
    selectedTab: GrooveTab,
    onTabChange: (GrooveTab) -> Unit,
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    isPlaying: Boolean,
    onPlayStop: () -> Unit,
    onTapTempo: () -> Unit,
    tapFlash: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        GradientColors.grooveStart,
                        AppColors.navBar
                    )
                )
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab: Tocar ya
            val qpSelected = selectedTab == GrooveTab.QUICK_PLAY
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (qpSelected) GradientColors.accent
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                    .clickable { onTabChange(GrooveTab.QUICK_PLAY) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Tocar",
                    fontSize = 10.sp,
                    fontWeight = if (qpSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (qpSelected) Color.Black else Color.White,
                    maxLines = 1
                )
            }

            // Tab: Entrenar
            val trSelected = selectedTab == GrooveTab.TRAINER
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (trSelected) GradientColors.accent
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                    .clickable { onTabChange(GrooveTab.TRAINER) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Trainer",
                    fontSize = 10.sp,
                    fontWeight = if (trSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (trSelected) Color.Black else Color.White,
                    maxLines = 1
                )
            }

            // BPM control (takes 1/3 of remaining width)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onBpmChange((bpm - 1).coerceAtLeast(40)) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("−", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        "$bpm",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "BPM",
                        fontSize = 7.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onBpmChange((bpm + 1).coerceAtMost(240)) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Play/Stop
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) AppColors.error else AppColors.success)
                    .clickable { onPlayStop() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Parar" else "Tocar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Tap tempo
            val tapBg by animateColorAsState(
                targetValue = if (tapFlash) GradientColors.accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                animationSpec = tween(100),
                label = "tap"
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(tapBg)
                    .clickable { onTapTempo() }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "TAP",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (tapFlash) Color.Black else Color.White
                )
            }
        }

        // BPM slider (thin, below the row)
        Slider(
            value = bpm.toFloat(),
            onValueChange = { onBpmChange(it.toInt()) },
            valueRange = 40f..240f,
            colors = SliderDefaults.colors(
                thumbColor = GradientColors.accent,
                activeTrackColor = GradientColors.accent.copy(alpha = 0.7f),
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        )
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
    rideVol: Float, onRideVolChange: (Float) -> Unit,
    showSettings: Boolean,
    onToggleSettings: () -> Unit,
    showMixer: Boolean,
    onToggleMixer: () -> Unit
) {
    // Style selector
    GrooveSectionLabel("Estilo")
    ScrollableChipRow(
        items = categories.map { it.displayName },
        selectedIdx = selectedCategoryIdx,
        onSelect = onCategoryChange
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Groove selector
    if (categoryData != null && categoryData.patterns.isNotEmpty()) {
        GrooveSectionLabel("Groove (${categoryData.patterns.size})")
        ScrollableChipRow(
            items = categoryData.patterns.map { it.name },
            selectedIdx = selectedPatternIdx,
            onSelect = onPatternChange
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Intensity
    GrooveSectionLabel("Intensidad")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
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
                        if (selected) GradientColors.accent
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onComplexityChange(level) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Collapsible: Ajustes
    CollapsibleSection(
        title = "Ajustes",
        subtitle = buildSettingsSummary(feel, swing, fillEveryBars, countIn),
        expanded = showSettings,
        onToggle = onToggleSettings
    ) {
        // Feel
        GrooveSectionLabel("Feel")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            if (selected) GradientColors.accent
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onFeelChange(f) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Swing
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
            colors = SliderDefaults.colors(
                thumbColor = GradientColors.accent,
                activeTrackColor = GradientColors.accent.copy(alpha = 0.7f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Fill options
        val hasFills = categoryData != null && categoryData.fills.isNotEmpty()
        if (hasFills) {
            GrooveSectionLabel("Fill automático")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val fillOptions = listOf(0 to "Off", 4 to "c/4", 8 to "c/8", 12 to "c/12")
                fillOptions.forEach { (bars, label) ->
                    val selected = fillEveryBars == bars
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) GradientColors.accent
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onFillChange(bars) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        } else {
            Text(
                "Sin fills disponibles para este estilo",
                color = AppColors.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Count-in
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (countIn) GradientColors.accent.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .border(
                    1.dp,
                    if (countIn) GradientColors.accent else Color.Transparent,
                    RoundedCornerShape(8.dp)
                )
                .clickable { onCountInChange(!countIn) }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Count-in (4 clicks)",
                color = if (countIn) Color.White else AppColors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (countIn) "ON" else "OFF",
                color = if (countIn) AppColors.success else AppColors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Collapsible: Mixer
    CollapsibleSection(
        title = "Mixer",
        subtitle = buildMixerSummary(kickVol, snareVol, hihatVol, rideVol),
        expanded = showMixer,
        onToggle = onToggleMixer
    ) {
        GrooveMixerSlider("Kick", kickVol, onKickVolChange)
        GrooveMixerSlider("Snare", snareVol, onSnareVolChange)
        GrooveMixerSlider("Hi-Hat", hihatVol, onHihatVolChange)
        GrooveMixerSlider("Ride / Crash", rideVol, onRideVolChange)
    }
}

private fun buildSettingsSummary(feel: GrooveEngine.Feel, swing: Float, fill: Int, countIn: Boolean): String {
    val parts = mutableListOf<String>()
    parts.add(when (feel) {
        GrooveEngine.Feel.TIGHT -> "Tight"
        GrooveEngine.Feel.NATURAL -> "Natural"
        GrooveEngine.Feel.LOOSE -> "Loose"
    })
    if (swing > 0.05f) {
        parts.add(when {
            swing < 0.3f -> "Swing"
            swing < 0.5f -> "Shuffle"
            else -> "Heavy"
        })
    }
    if (fill > 0) parts.add("Fill c/$fill")
    if (countIn) parts.add("Count-in")
    return parts.joinToString(" · ")
}

private fun buildMixerSummary(kick: Float, snare: Float, hihat: Float, ride: Float): String {
    val allMax = kick >= 0.99f && snare >= 0.99f && hihat >= 0.99f && ride >= 0.99f
    if (allMax) return "Todo al 100%"
    val parts = mutableListOf<String>()
    if (kick < 0.99f) parts.add("K:${(kick * 100).toInt()}%")
    if (snare < 0.99f) parts.add("S:${(snare * 100).toInt()}%")
    if (hihat < 0.99f) parts.add("HH:${(hihat * 100).toInt()}%")
    if (ride < 0.99f) parts.add("R:${(ride * 100).toInt()}%")
    return parts.joinToString(" · ")
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
        "La batería desaparece cada X compases para entrenar tu pulso.",
        color = AppColors.textMuted,
        fontSize = 10.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val options = listOf(0 to "Off", 2 to "c/2", 4 to "c/4", 8 to "c/8")
        options.forEach { (bars, label) ->
            val selected = silenceEveryBars == bars
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) GradientColors.accent
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSilenceChange(bars) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    GrooveSectionLabel("Progresión de tempo")
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
                .padding(horizontal = 10.dp, vertical = 5.dp)
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
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) { Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }

    if (tempoTarget > 0) {
        Spacer(modifier = Modifier.height(8.dp))
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
                            if (selected) GradientColors.accent
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onTempoIncrementChange(inc) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        "+$inc",
                        color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "De $bpm → $tempoTarget BPM (+$tempoIncrement cada 8 compases)",
            color = AppColors.textSecondary,
            fontSize = 10.sp
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    GrooveSectionLabel("Rutinas rápidas")
    val routines = listOf(
        Triple("5 min calentamiento", "70→100 BPM, silencio c/8", Triple(100, 5, 8)),
        Triple("10 min resistencia", "90 BPM fijo, silencio c/4", Triple(0, 5, 4)),
        Triple("15 min progresivo", "60→120 BPM, +5 c/8", Triple(120, 5, 0))
    )
    routines.forEach { (name, desc, config) ->
        val (target, inc, silence) = config
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    onTempoTargetChange(target)
                    onTempoIncrementChange(inc)
                    onSilenceChange(silence)
                }
                .padding(10.dp)
        ) {
            Column {
                Text(
                    name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    desc,
                    color = AppColors.textMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ==================== SHARED COMPONENTS ====================

@Composable
private fun CollapsibleSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                tint = GradientColors.accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                subtitle,
                color = AppColors.textMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Colapsar" else "Expandir",
                tint = AppColors.textMuted,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun GrooveSectionLabel(text: String) {
    Text(
        text,
        color = AppColors.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun ScrollableChipRow(items: List<String>, selectedIdx: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items.forEachIndexed { idx, label ->
            val selected = idx == selectedIdx
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) GradientColors.accent
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(idx) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
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
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = AppColors.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(68.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = GradientColors.accent,
                activeTrackColor = GradientColors.accent.copy(alpha = 0.7f)
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            "${(value * 100).toInt()}%",
            color = AppColors.textMuted,
            fontSize = 10.sp,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
    }
}
