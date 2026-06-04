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

    var selectedTab by remember { mutableStateOf(GrooveTab.QUICK_PLAY) }
    var isPlaying by remember { mutableStateOf(false) }
    var bpm by remember { mutableIntStateOf(100) }
    var complexityLevel by remember { mutableIntStateOf(3) }
    var currentBar by remember { mutableIntStateOf(0) }
    var currentBeat by remember { mutableIntStateOf(0) }
    var playJob by remember { mutableStateOf<Job?>(null) }

    var categories by remember { mutableStateOf<List<GrooveCategory>>(emptyList()) }
    var selectedCategoryIdx by remember { mutableIntStateOf(0) }
    var categoryData by remember { mutableStateOf<GrooveCategoryData?>(null) }
    var selectedPatternIdx by remember { mutableIntStateOf(0) }

    var feel by remember { mutableStateOf(GrooveEngine.Feel.NATURAL) }
    var swing by remember { mutableFloatStateOf(0f) }
    var fillEveryBars by remember { mutableIntStateOf(0) }
    var countIn by remember { mutableStateOf(false) }

    var kickVol by remember { mutableFloatStateOf(1f) }
    var snareVol by remember { mutableFloatStateOf(1f) }
    var hihatVol by remember { mutableFloatStateOf(1f) }
    var rideVol by remember { mutableFloatStateOf(1f) }

    var silenceEveryBars by remember { mutableIntStateOf(0) }
    var tempoTarget by remember { mutableIntStateOf(0) }
    var tempoIncrement by remember { mutableIntStateOf(5) }

    var lastTapTime by remember { mutableLongStateOf(0L) }
    var tapCount by remember { mutableIntStateOf(0) }
    var tapSum by remember { mutableLongStateOf(0L) }
    var tapFlash by remember { mutableStateOf(false) }

    // 5 panel expanded states (only one open at a time)
    var expandedPanel by remember { mutableStateOf<String?>(null) }

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
            bpm = bpm, pattern = pattern, fills = data.fills,
            complexityLevel = complexityLevel, feel = feel, swing = swing,
            fillEveryBars = fillEveryBars, silenceEveryBars = silenceEveryBars,
            silenceDurationBars = 1, countIn = countIn,
            volumes = mapOf("kick" to kickVol, "snare" to snareVol, "hihat" to hihatVol, "ride" to rideVol, "crash" to rideVol),
            tempoProgression = if (tempoTarget > bpm) GrooveEngine.TempoProgression(tempoTarget, tempoIncrement, 8) else null
        )
        stopPlaying()
        isPlaying = true
        playJob = scope.launch {
            try {
                GrooveEngine.playGroove(context, config,
                    onBeat = { bar, beat ->
                        scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { currentBar = bar; currentBeat = beat }
                    },
                    onBpmChange = { newBpm ->
                        scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { bpm = newBpm }
                    })
            } catch (_: Exception) { }
            if (isPlaying) { isPlaying = false; currentBeat = 0; currentBar = 0 }
        }
    }

    fun stopPlaying() {
        isPlaying = false
        GrooveEngine.stop()
        playJob?.cancel()
        playJob = null
        currentBeat = 0; currentBar = 0
    }

    fun handleTapTempo() {
        val now = System.currentTimeMillis()
        tapFlash = true
        if (lastTapTime > 0L && now - lastTapTime < 2000L) {
            tapSum += (now - lastTapTime); tapCount++
            if (tapCount >= 2) bpm = (60000f / (tapSum.toFloat() / tapCount)).toInt().coerceIn(40, 240)
        } else { tapCount = 0; tapSum = 0L }
        lastTapTime = now
    }

    LaunchedEffect(tapFlash) {
        if (tapFlash) { kotlinx.coroutines.delay(150); tapFlash = false }
    }

    fun togglePanel(name: String) {
        expandedPanel = if (expandedPanel == name) null else name
    }

    // Derived display values
    val catName = categories.getOrNull(selectedCategoryIdx)?.displayName ?: "—"
    val patName = categoryData?.patterns?.getOrNull(selectedPatternIdx)?.name ?: "—"
    val intensityLabel = listOf("Simple", "Normal", "Groove", "Ghost", "Full").getOrElse(complexityLevel - 1) { "—" }
    val feelLabel = when (feel) {
        GrooveEngine.Feel.TIGHT -> "Tight"; GrooveEngine.Feel.NATURAL -> "Natural"; GrooveEngine.Feel.LOOSE -> "Loose"
    }
    val swingLabel = when {
        swing < 0.05f -> "Straight"; swing < 0.3f -> "Light Swing"; swing < 0.5f -> "Shuffle"; else -> "Heavy Shuffle"
    }
    val settingsSummary = listOfNotNull(
        feelLabel,
        if (swing > 0.05f) swingLabel else null,
        if (fillEveryBars > 0) "Fill c/$fillEveryBars" else null,
        if (countIn) "Count-in" else null
    ).joinToString(" · ")
    val mixerSummary = if (kickVol >= 0.99f && snareVol >= 0.99f && hihatVol >= 0.99f && rideVol >= 0.99f) "100%"
    else listOfNotNull(
        if (kickVol < 0.99f) "K:${(kickVol * 100).toInt()}%" else null,
        if (snareVol < 0.99f) "S:${(snareVol * 100).toInt()}%" else null,
        if (hihatVol < 0.99f) "HH:${(hihatVol * 100).toInt()}%" else null,
        if (rideVol < 0.99f) "R:${(rideVol * 100).toInt()}%" else null
    ).joinToString(" · ")

    // ==================== LAYOUT ====================
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        // ── SCROLLABLE CONTENT ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Back + title + beat dots (inline, scrollable)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(
                    Icons.Default.ArrowBack, contentDescription = "Volver",
                    tint = GradientColors.accent,
                    modifier = Modifier.size(20.dp).clickable { onBack() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Caja de Ritmos", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (isPlaying) {
                    Spacer(modifier = Modifier.width(8.dp))
                    for (beat in 0 until 4) {
                        val active = currentBeat == beat
                        val dotColor = when {
                            active && beat == 0 -> AppColors.error
                            active -> AppColors.success
                            else -> AppColors.textMuted.copy(alpha = 0.4f)
                        }
                        val dotScale by animateFloatAsState(if (active) 1.3f else 1.0f, tween(80), label = "d$beat")
                        Box(Modifier.size(8.dp).scale(dotScale).clip(CircleShape).background(dotColor))
                        if (beat < 3) Spacer(Modifier.width(3.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("C${currentBar + 1}", fontSize = 10.sp, color = AppColors.textMuted)
                }
            }

            when (selectedTab) {
                GrooveTab.QUICK_PLAY -> {
                    // ── TOP ROW: Estilo / Groove / Intensidad ──
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExpandablePanel(
                            title = "Estilo",
                            value = catName,
                            expanded = expandedPanel == "estilo",
                            onToggle = { togglePanel("estilo") },
                            modifier = Modifier.weight(1f)
                        ) {
                            ScrollableChipRow(
                                items = categories.map { it.displayName },
                                selectedIdx = selectedCategoryIdx,
                                onSelect = { idx ->
                                    val wasPlaying = isPlaying
                                    if (wasPlaying) stopPlaying()
                                    selectedCategoryIdx = idx; selectedPatternIdx = 0
                                    expandedPanel = null
                                    if (wasPlaying) {
                                        scope.launch {
                                            // Wait for categoryData to reload via LaunchedEffect
                                            kotlinx.coroutines.delay(200)
                                            startPlaying()
                                        }
                                    }
                                }
                            )
                        }
                        ExpandablePanel(
                            title = "Groove",
                            value = patName,
                            expanded = expandedPanel == "groove",
                            onToggle = { togglePanel("groove") },
                            modifier = Modifier.weight(1f)
                        ) {
                            val patterns = categoryData?.patterns ?: emptyList()
                            ScrollableChipRow(
                                items = patterns.map { it.name },
                                selectedIdx = selectedPatternIdx,
                                onSelect = { idx ->
                                    val wasPlaying = isPlaying
                                    if (wasPlaying) stopPlaying()
                                    selectedPatternIdx = idx
                                    expandedPanel = null
                                    if (wasPlaying) startPlaying()
                                }
                            )
                        }
                        ExpandablePanel(
                            title = "Intensidad",
                            value = intensityLabel,
                            expanded = expandedPanel == "intensidad",
                            onToggle = { togglePanel("intensidad") },
                            modifier = Modifier.weight(1f)
                        ) {
                            val labels = listOf("Simple", "Normal", "Groove", "Ghost", "Full")
                            labels.forEachIndexed { idx, label ->
                                val level = idx + 1
                                val selected = complexityLevel == level
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) GradientColors.accent else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { complexityLevel = level; expandedPanel = null }
                                        .padding(vertical = 6.dp, horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (selected) Color.Black else Color.White, fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // ── BOTTOM ROW: Ajustes / Mixer ──
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExpandablePanel(
                            title = "Ajustes",
                            value = settingsSummary,
                            expanded = expandedPanel == "ajustes",
                            onToggle = { togglePanel("ajustes") },
                            modifier = Modifier.weight(1f)
                        ) {
                            AjustesContent(
                                feel = feel, onFeelChange = { feel = it },
                                swing = swing, onSwingChange = { swing = it },
                                fillEveryBars = fillEveryBars, onFillChange = { fillEveryBars = it },
                                countIn = countIn, onCountInChange = { countIn = it },
                                hasFills = categoryData?.fills?.isNotEmpty() == true
                            )
                        }
                        ExpandablePanel(
                            title = "Mixer",
                            value = mixerSummary,
                            expanded = expandedPanel == "mixer",
                            onToggle = { togglePanel("mixer") },
                            modifier = Modifier.weight(1f)
                        ) {
                            MixerSlider("Kick", kickVol) { kickVol = it }
                            MixerSlider("Snare", snareVol) { snareVol = it }
                            MixerSlider("Hi-Hat", hihatVol) { hihatVol = it }
                            MixerSlider("Ride", rideVol) { rideVol = it }
                        }
                    }
                }

                GrooveTab.TRAINER -> TrainerContent(
                    silenceEveryBars = silenceEveryBars, onSilenceChange = { silenceEveryBars = it },
                    tempoTarget = tempoTarget, onTempoTargetChange = { tempoTarget = it },
                    tempoIncrement = tempoIncrement, onTempoIncrementChange = { tempoIncrement = it },
                    bpm = bpm
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── SINGLE BOTTOM BAR ──
        GrooveUnifiedBar(
            selectedTab = selectedTab, onTabChange = { selectedTab = it },
            bpm = bpm, onBpmChange = { bpm = it },
            isPlaying = isPlaying,
            onPlayStop = { if (isPlaying) stopPlaying() else startPlaying() },
            onTapTempo = { handleTapTempo() },
            tapFlash = tapFlash
        )
    }
}

// ==================== EXPANDABLE PANEL ====================

@Composable
private fun ExpandablePanel(
    title: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (expanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .border(
                width = if (expanded) 1.dp else 0.dp,
                color = if (expanded) GradientColors.accent.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .animateContentSize()
    ) {
        // Header (always visible)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                fontSize = 10.sp,
                color = AppColors.textMuted,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                value,
                fontSize = 12.sp,
                color = if (expanded) GradientColors.accent else Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = AppColors.textMuted,
                modifier = Modifier.size(14.dp)
            )
        }

        // Expandable content
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                content()
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ==================== AJUSTES CONTENT ====================

@Composable
private fun AjustesContent(
    feel: GrooveEngine.Feel, onFeelChange: (GrooveEngine.Feel) -> Unit,
    swing: Float, onSwingChange: (Float) -> Unit,
    fillEveryBars: Int, onFillChange: (Int) -> Unit,
    countIn: Boolean, onCountInChange: (Boolean) -> Unit,
    hasFills: Boolean
) {
    // Feel
    Text("Feel", color = AppColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        GrooveEngine.Feel.entries.forEach { f ->
            val selected = feel == f
            val label = when (f) {
                GrooveEngine.Feel.TIGHT -> "Tight"; GrooveEngine.Feel.NATURAL -> "Natural"; GrooveEngine.Feel.LOOSE -> "Loose"
            }
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (selected) GradientColors.accent else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onFeelChange(f) }.padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (selected) Color.Black else Color.White, fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }

    // Swing
    val swingLabel = when {
        swing < 0.05f -> "Straight"; swing < 0.3f -> "Light Swing"; swing < 0.5f -> "Shuffle"; else -> "Heavy"
    }
    Text("Swing: $swingLabel", color = AppColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    Slider(
        value = swing, onValueChange = onSwingChange, valueRange = 0f..0.67f,
        colors = SliderDefaults.colors(thumbColor = GradientColors.accent, activeTrackColor = GradientColors.accent.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth().height(28.dp)
    )

    // Fill
    if (hasFills) {
        Text("Fill", color = AppColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0 to "Off", 4 to "c/4", 8 to "c/8", 12 to "c/12").forEach { (bars, label) ->
                val selected = fillEveryBars == bars
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) GradientColors.accent else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onFillChange(bars) }.padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (selected) Color.Black else Color.White, fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }

    // Count-in
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (countIn) GradientColors.accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (countIn) GradientColors.accent else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onCountInChange(!countIn) }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Count-in", color = if (countIn) Color.White else AppColors.textSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(if (countIn) "ON" else "OFF", color = if (countIn) AppColors.success else AppColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ==================== MIXER SLIDER ====================

@Composable
private fun MixerSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AppColors.textSecondary, fontSize = 10.sp, modifier = Modifier.width(44.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Slider(
            value = value, onValueChange = onValueChange, valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = GradientColors.accent, activeTrackColor = GradientColors.accent.copy(alpha = 0.7f)),
            modifier = Modifier.weight(1f).height(24.dp)
        )
        Text("${(value * 100).toInt()}%", color = AppColors.textMuted, fontSize = 9.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
    }
}

// ==================== UNIFIED BOTTOM BAR ====================

@Composable
private fun GrooveUnifiedBar(
    selectedTab: GrooveTab, onTabChange: (GrooveTab) -> Unit,
    bpm: Int, onBpmChange: (Int) -> Unit,
    isPlaying: Boolean, onPlayStop: () -> Unit,
    onTapTempo: () -> Unit, tapFlash: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(GradientColors.grooveStart, AppColors.navBar)))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tab: Tocar
        TabButton("Tocar", selectedTab == GrooveTab.QUICK_PLAY) { onTabChange(GrooveTab.QUICK_PLAY) }
        // Tab: Trainer
        TabButton("Trainer", selectedTab == GrooveTab.TRAINER) { onTabChange(GrooveTab.TRAINER) }

        // BPM zone: [−5] [−] [100] [slider] [+] [+5]  (~1/3 width)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // −5
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { onBpmChange((bpm - 5).coerceAtLeast(40)) },
                contentAlignment = Alignment.Center
            ) { Text("-5", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold) }

            Spacer(Modifier.width(2.dp))

            // −1
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onBpmChange((bpm - 1).coerceAtLeast(40)) },
                contentAlignment = Alignment.Center
            ) { Text("−", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }

            // BPM number
            Text(
                "$bpm",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            // Slider (half size via weight)
            Slider(
                value = bpm.toFloat(), onValueChange = { onBpmChange(it.toInt()) }, valueRange = 40f..240f,
                colors = SliderDefaults.colors(
                    thumbColor = GradientColors.accent,
                    activeTrackColor = GradientColors.accent.copy(alpha = 0.7f),
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.weight(0.5f).height(20.dp)
            )

            // +1
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onBpmChange((bpm + 1).coerceAtMost(240)) },
                contentAlignment = Alignment.Center
            ) { Text("+", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }

            Spacer(Modifier.width(2.dp))

            // +5
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { onBpmChange((bpm + 5).coerceAtMost(240)) },
                contentAlignment = Alignment.Center
            ) { Text("+5", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
        }

        // Play
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(if (isPlaying) AppColors.error else AppColors.success)
                .clickable { onPlayStop() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Parar" else "Tocar",
                tint = Color.White, modifier = Modifier.size(20.dp)
            )
        }

        // Tap
        val tapBg by animateColorAsState(
            if (tapFlash) GradientColors.accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            tween(100), label = "tap"
        )
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(tapBg)
                .clickable { onTapTempo() }.padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("TAP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (tapFlash) Color.Black else Color.White)
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) GradientColors.accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable { onClick() }.padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.Black else Color.White, maxLines = 1)
    }
}

// ==================== TRAINER TAB ====================

@Composable
private fun TrainerContent(
    silenceEveryBars: Int, onSilenceChange: (Int) -> Unit,
    tempoTarget: Int, onTempoTargetChange: (Int) -> Unit,
    tempoIncrement: Int, onTempoIncrementChange: (Int) -> Unit,
    bpm: Int
) {
    SectionLabel("Silencio inteligente")
    Text("La batería desaparece cada X compases para entrenar tu pulso.",
        color = AppColors.textMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0 to "Off", 2 to "c/2", 4 to "c/4", 8 to "c/8").forEach { (bars, label) ->
            val selected = silenceEveryBars == bars
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (selected) GradientColors.accent else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSilenceChange(bars) }.padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (selected) Color.Black else Color.White, fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    SectionLabel("Progresión de tempo")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Objetivo:", color = AppColors.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onTempoTargetChange((tempoTarget - 5).coerceAtLeast(0)) }.padding(horizontal = 10.dp, vertical = 5.dp)
        ) { Text("−", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
        Text(if (tempoTarget > 0) "$tempoTarget BPM" else "Off", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onTempoTargetChange((tempoTarget + 5).coerceAtMost(240)) }.padding(horizontal = 10.dp, vertical = 5.dp)
        ) { Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }

    if (tempoTarget > 0) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Incremento:", color = AppColors.textSecondary, fontSize = 12.sp)
            listOf(2, 5, 10).forEach { inc ->
                val selected = tempoIncrement == inc
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (selected) GradientColors.accent else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onTempoIncrementChange(inc) }.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("+$inc", color = if (selected) Color.Black else Color.White, fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("De $bpm → $tempoTarget BPM (+$tempoIncrement cada 8 compases)", color = AppColors.textSecondary, fontSize = 10.sp)
    }

    Spacer(Modifier.height(12.dp))

    SectionLabel("Rutinas rápidas")
    listOf(
        Triple("5 min calentamiento", "70→100 BPM, silencio c/8", Triple(100, 5, 8)),
        Triple("10 min resistencia", "90 BPM fijo, silencio c/4", Triple(0, 5, 4)),
        Triple("15 min progresivo", "60→120 BPM, +5 c/8", Triple(120, 5, 0))
    ).forEach { (name, desc, config) ->
        val (target, inc, silence) = config
        Box(
            Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onTempoTargetChange(target); onTempoIncrementChange(inc); onSilenceChange(silence) }
                .padding(10.dp)
        ) {
            Column {
                Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(desc, color = AppColors.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ==================== SHARED ====================

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = AppColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun ScrollableChipRow(items: List<String>, selectedIdx: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { idx, label ->
            val selected = idx == selectedIdx
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (selected) GradientColors.accent else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(idx) }.padding(vertical = 6.dp, horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(label, color = if (selected) Color.Black else Color.White, fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
