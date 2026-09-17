package com.example.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ConstellationEntity
import com.example.data.PresetSoundscape
import com.example.model.*
import com.example.ui.theme.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarmonicCanvasScreen(
    viewModel: CanvasViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val ripples by viewModel.ripples.collectAsStateWithLifecycle()
    val sparks by viewModel.sparks.collectAsStateWithLifecycle()
    val currentScale by viewModel.currentScale.collectAsStateWithLifecycle()
    val currentTexture by viewModel.currentTexture.collectAsStateWithLifecycle()
    val physicsMode by viewModel.physicsMode.collectAsStateWithLifecycle()
    val atmosphereMode by viewModel.atmosphereMode.collectAsStateWithLifecycle()
    val bpm by viewModel.bpm.collectAsStateWithLifecycle()
    val isRadarActive by viewModel.isRadarActive.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val radarAngle by viewModel.radarSweepAngle.collectAsStateWithLifecycle()
    val radarPulseR by viewModel.radarPulseRadius.collectAsStateWithLifecycle()

    val savedConstellations by viewModel.savedConstellations.collectAsStateWithLifecycle()
    val presets = viewModel.builtInPresets

    var showPresetsSheet by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showTempoDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var saveTitleInput by remember { mutableStateOf("") }

    // Active drag tracking
    var draggedNodeId by remember { mutableStateOf<Long?>(null) }
    var lastDragPosition by remember { mutableStateOf(Offset.Zero) }

    // High performance frame loop for canvas physics & animations
    LaunchedEffect(Unit) {
        var lastNano = System.nanoTime()
        while (true) {
            withFrameNanos { currentNano ->
                val dt = (currentNano - lastNano) / 1_000_000_000f
                lastNano = currentNano
                viewModel.updatePhysics(dt, context)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DeepCosmic,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .onSizeChanged { size ->
                    viewModel.onCanvasSizeKnown(size.width.toFloat(), size.height.toFloat())
                }
        ) {
            // 1. Interactive Drawing Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("harmonic_canvas_surface")
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                viewModel.onCanvasTapped(offset.x, offset.y, context)
                            },
                            onDoubleTap = { offset ->
                                viewModel.onNodeDoubleTapped(offset.x, offset.y, context)
                            },
                            onLongPress = { offset ->
                                viewModel.onNodeLongPressed(offset.x, offset.y, context)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val target = nodes.find { node ->
                                    val dx = node.x - offset.x
                                    val dy = node.y - offset.y
                                    dx * dx + dy * dy <= (node.radius + 32f) * (node.radius + 32f)
                                }
                                if (target != null) {
                                    draggedNodeId = target.id
                                    lastDragPosition = offset
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val id = draggedNodeId ?: return@detectDragGestures
                                val newPos = lastDragPosition + dragAmount
                                lastDragPosition = newPos
                                viewModel.onNodeDragged(
                                    nodeId = id,
                                    x = newPos.x,
                                    y = newPos.y,
                                    vx = dragAmount.x * 25f,
                                    vy = dragAmount.y * 25f
                                )
                            },
                            onDragEnd = {
                                draggedNodeId = null
                            },
                            onDragCancel = {
                                draggedNodeId = null
                            }
                        )
                    }
            ) {
                val canvasW = size.width
                val canvasH = size.height
                val centerOffset = Offset(canvasW / 2f, canvasH / 2f)

                // Background subtle space nebula gradient
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF13192B),
                            Color(0xFF0C101A),
                            DeepCosmic
                        ),
                        center = centerOffset,
                        radius = max(canvasW, canvasH) * 0.75f
                    )
                )

                // Draw Radar Sequencer & Concentric Rings
                if (isRadarActive) {
                    drawRadarVisuals(
                        center = centerOffset,
                        angleDeg = radarAngle,
                        pulseR = radarPulseR,
                        maxRadius = sqrt(centerOffset.x * centerOffset.x + centerOffset.y * centerOffset.y),
                        primaryColor = currentScale.defaultPalette.firstOrNull() ?: CyanGlow
                    )
                }

                // Draw Proximity Constellation Lines between nearby nodes
                drawConstellationBonds(nodes)

                // Draw Expanding Ripples
                for (ripple in ripples) {
                    val color = Color(ripple.colorValue).copy(alpha = ripple.alpha.coerceIn(0f, 1f))
                    drawCircle(
                        color = color,
                        radius = ripple.radius,
                        center = Offset(ripple.x, ripple.y),
                        style = Stroke(width = (2.5f * (1f - ripple.radius / ripple.maxRadius)).coerceAtLeast(1f))
                    )
                }

                // Draw Resonating Nodes
                for (node in nodes) {
                    drawResonatingNode(node = node)
                }

                // Draw Sparks
                for (spark in sparks) {
                    val color = Color(spark.colorValue).copy(alpha = spark.alpha.coerceIn(0f, 1f))
                    drawCircle(
                        color = color,
                        radius = spark.size * spark.alpha,
                        center = Offset(spark.x, spark.y)
                    )
                }
            }

            // 2. Empty State Instructions Hint
            if (nodes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = CyanGlow.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Touch to create sound nodes",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap anywhere · Drag to throw · Double tap to burst\nUse the radar beam to trigger rhythmic harmony",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // 3. Top Floating Glass Bar
            TopBarGlass(
                nodesCount = nodes.size,
                currentTexture = currentTexture,
                isMuted = isMuted,
                onToggleMute = { viewModel.toggleMute() },
                onOpenPresets = { showPresetsSheet = true },
                onClearCanvas = { viewModel.clearCanvas() },
                onOpenInfo = { showInfoDialog = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // 4. Bottom Dynamic Controls Floating Dock
            BottomControlsGlass(
                currentScale = currentScale,
                currentTexture = currentTexture,
                physicsMode = physicsMode,
                atmosphereMode = atmosphereMode,
                bpm = bpm,
                isRadarActive = isRadarActive,
                onScaleSelected = { viewModel.setScale(it) },
                onTextureSelected = { viewModel.setSoundTexture(it) },
                onPhysicsSelected = { viewModel.setPhysicsMode(it) },
                onAtmosphereSelected = { viewModel.setAtmosphereMode(it) },
                onToggleRadar = { viewModel.toggleRadar() },
                onOpenTempoDialog = { showTempoDialog = true },
                onSaveClicked = {
                    saveTitleInput = "Soundscape ${savedConstellations.size + 1}"
                    showSaveDialog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }

    // Presets & Library Modal BottomSheet
    if (showPresetsSheet) {
        PresetsBottomSheet(
            presets = presets,
            savedConstellations = savedConstellations,
            onSelectPreset = { preset ->
                viewModel.loadPreset(preset)
                showPresetsSheet = false
            },
            onSelectSaved = { entity ->
                viewModel.loadSavedConstellation(entity)
                showPresetsSheet = false
            },
            onDeleteSaved = { id ->
                viewModel.deleteSavedConstellation(id)
            },
            onDismiss = { showPresetsSheet = false }
        )
    }

    // Save Constellation Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Text("Save Soundscape", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Save current node constellation, scale tuning, and rhythm configuration to your library.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = saveTitleInput,
                        onValueChange = { saveTitleInput = it },
                        label = { Text("Soundscape Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanGlow,
                            unfocusedBorderColor = CosmicBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_soundscape_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCurrentConstellation(saveTitleInput)
                        showSaveDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanGlow, contentColor = Color.Black),
                    modifier = Modifier.testTag("confirm_save_button")
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CosmicSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Tempo Adjustment Dialog
    if (showTempoDialog) {
        AlertDialog(
            onDismissRequest = { showTempoDialog = false },
            title = {
                Text("Tempo & Rhythm (BPM)", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$bpm BPM",
                        color = CyanGlow,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Slider(
                        value = bpm.toFloat(),
                        onValueChange = { viewModel.setBpm(it.toInt()) },
                        valueRange = 40f..160f,
                        steps = 23,
                        colors = SliderDefaults.colors(
                            thumbColor = CyanGlow,
                            activeTrackColor = CyanGlow,
                            inactiveTrackColor = CosmicBorder
                        ),
                        modifier = Modifier.testTag("bpm_slider")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("40 Adagio", color = TextMuted, fontSize = 12.sp)
                        Text("96 Moderato", color = TextMuted, fontSize = 12.sp)
                        Text("160 Vivace", color = TextMuted, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTempoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanGlow, contentColor = Color.Black)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CosmicSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Harmonic Canvas Info / Help Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyanGlow)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Harmonic Canvas", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "An interactive generative sound synthesizer and kinetic ambient soundscape.",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "• Tap anywhere to drop nodes\n" +
                        "• Pitch maps to vertical position (higher up = higher octave)\n" +
                        "• Drag nodes to fling with kinetic velocity\n" +
                        "• Long press a node to anchor/pin it in place\n" +
                        "• Double tap a node to burst and remove it\n" +
                        "• Radar Sequencer triggers rhythmic melodies as its beam sweeps\n" +
                        "• Switch musical tunings (Solfeggio, Hirajoshi, Pentatonic) for instant harmonious compositions",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanGlow, contentColor = Color.Black)
                ) {
                    Text("Got It", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CosmicSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

private fun DrawScope.drawRadarVisuals(
    center: Offset,
    angleDeg: Float,
    pulseR: Float,
    maxRadius: Float,
    primaryColor: Color
) {
    // 1. Faint Concentric rhythm guideline rings
    val ringSteps = 4
    for (step in 1..ringSteps) {
        val r = (maxRadius / (ringSteps + 1)) * step
        drawCircle(
            color = Color.White.copy(alpha = 0.04f),
            radius = r,
            center = center,
            style = Stroke(
                width = 1.2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 16f), 0f)
            )
        )
    }

    // 2. Central Pulsar Core
    drawCircle(
        color = primaryColor.copy(alpha = 0.15f),
        radius = 28f,
        center = center
    )
    drawCircle(
        color = primaryColor.copy(alpha = 0.7f),
        radius = 7f,
        center = center
    )

    // 3. Expanding circular pulse wave
    if (pulseR > 5f) {
        val pulseAlpha = (1f - (pulseR / maxRadius)).coerceIn(0f, 0.5f)
        drawCircle(
            color = primaryColor.copy(alpha = pulseAlpha),
            radius = pulseR,
            center = center,
            style = Stroke(width = 2f)
        )
    }

    // 4. Sweeping radar beam line
    val angleRad = Math.toRadians(angleDeg.toDouble())
    val endX = center.x + (cos(angleRad) * maxRadius).toFloat()
    val endY = center.y + (sin(angleRad) * maxRadius).toFloat()

    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(primaryColor.copy(alpha = 0.7f), primaryColor.copy(alpha = 0.0f)),
            start = center,
            end = Offset(endX, endY)
        ),
        start = center,
        end = Offset(endX, endY),
        strokeWidth = 2.2f
    )
}

private fun DrawScope.drawConstellationBonds(nodes: List<CanvasNode>) {
    val maxBondDistance = 220f
    for (i in nodes.indices) {
        for (j in i + 1 until nodes.size) {
            val n1 = nodes[i]
            val n2 = nodes[j]
            val dx = n1.x - n2.x
            val dy = n1.y - n2.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < maxBondDistance) {
                val bondAlpha = (1f - (dist / maxBondDistance)) * 0.28f
                drawLine(
                    color = Color(n1.colorValue).copy(alpha = bondAlpha),
                    start = Offset(n1.x, n1.y),
                    end = Offset(n2.x, n2.y),
                    strokeWidth = 1.4f
                )
            }
        }
    }
}

private fun DrawScope.drawResonatingNode(node: CanvasNode) {
    val baseColor = Color(node.colorValue)
    val glowColor = baseColor.copy(alpha = (0.25f + node.glow * 0.55f).coerceIn(0f, 0.85f))
    val center = Offset(node.x, node.y)

    // Outer glow aura
    drawCircle(
        color = glowColor,
        radius = node.radius + 14f + (node.glow * 18f),
        center = center
    )

    // Secondary rim ring
    drawCircle(
        color = baseColor.copy(alpha = 0.8f),
        radius = node.radius + (node.glow * 6f),
        center = center,
        style = Stroke(width = 2f)
    )

    // Core body with radial gradient
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f),
                baseColor,
                baseColor.copy(alpha = 0.85f)
            ),
            center = Offset(node.x - node.radius * 0.25f, node.y - node.radius * 0.25f),
            radius = node.radius
        ),
        radius = node.radius,
        center = center
    )

    // Pinned anchor indicator
    if (node.isPinned) {
        drawCircle(
            color = Color.White,
            radius = 3.5f,
            center = center
        )
    }
}

@Composable
fun TopBarGlass(
    nodesCount: Int,
    currentTexture: SoundTexture,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onOpenPresets: () -> Unit,
    onClearCanvas: () -> Unit,
    onOpenInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = CosmicSurface.copy(alpha = 0.82f),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onOpenInfo() }
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CyanGlow.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, CyanGlow.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "App Icon",
                        tint = CyanGlow,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Harmonic Canvas",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$nodesCount nodes · ${currentTexture.displayName}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Info button
                IconButton(
                    onClick = onOpenInfo,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("info_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Information",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Presets button
                IconButton(
                    onClick = onOpenPresets,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("presets_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = "Presets",
                        tint = CyanGlow,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Mute button
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("mute_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = if (isMuted) AuroraPink else TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Clear button
                IconButton(
                    onClick = onClearCanvas,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Clear Canvas",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BottomControlsGlass(
    currentScale: SoundScale,
    currentTexture: SoundTexture,
    physicsMode: PhysicsMode,
    atmosphereMode: AtmosphereMode,
    bpm: Int,
    isRadarActive: Boolean,
    onScaleSelected: (SoundScale) -> Unit,
    onTextureSelected: (SoundTexture) -> Unit,
    onPhysicsSelected: (PhysicsMode) -> Unit,
    onAtmosphereSelected: (AtmosphereMode) -> Unit,
    onToggleRadar: () -> Unit,
    onOpenTempoDialog: () -> Unit,
    onSaveClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedMenu by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier,
        color = CosmicSurface.copy(alpha = 0.88f),
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Row 1: Tuning Scales Horizontal Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SoundScale.values().forEach { scale ->
                    val isSelected = scale == currentScale
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onScaleSelected(scale) }
                            .testTag("scale_chip_${scale.name}"),
                        color = if (isSelected) CyanGlow.copy(alpha = 0.22f) else CosmicSurfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) CyanGlow else CosmicBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        scale.defaultPalette.firstOrNull() ?: CyanGlow,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = scale.displayName,
                                color = if (isSelected) CyanGlow else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Tactical Action Icons & Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Radar Play/Pause
                Button(
                    onClick = onToggleRadar,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRadarActive) CyanGlow else CosmicSurfaceVariant,
                        contentColor = if (isRadarActive) Color.Black else TextPrimary
                    ),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("radar_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isRadarActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isRadarActive) "Radar" else "Paused",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // BPM / Tempo Button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenTempoDialog() }
                        .testTag("bpm_button"),
                    color = CosmicSurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = GoldenSun,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$bpm",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Sound Profile Selector
                Box {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                expandedMenu = if (expandedMenu == "texture") null else "texture"
                            }
                            .testTag("sound_texture_button"),
                        color = CosmicSurfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = NebulaViolet,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (currentTexture) {
                                    SoundTexture.CRYSTAL -> "Chime"
                                    SoundTexture.ZEN_BELL -> "Zen"
                                    SoundTexture.CYBER_PLUCK -> "Pluck"
                                    SoundTexture.PURE_SINE -> "Sine"
                                },
                                color = TextPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expandedMenu == "texture",
                        onDismissRequest = { expandedMenu = null },
                        modifier = Modifier.background(CosmicSurface)
                    ) {
                        SoundTexture.values().forEach { texture ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(texture.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                        Text(texture.description, color = TextSecondary, fontSize = 10.sp)
                                    }
                                },
                                onClick = {
                                    onTextureSelected(texture)
                                    expandedMenu = null
                                }
                            )
                        }
                    }
                }

                // Atmosphere Mode Selector
                Box {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                expandedMenu = if (expandedMenu == "atmosphere") null else "atmosphere"
                            }
                            .testTag("atmosphere_button"),
                        color = CosmicSurfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (atmosphereMode) {
                                    AtmosphereMode.OFF -> Icons.Outlined.CloudOff
                                    AtmosphereMode.RAIN_MURMUR -> Icons.Default.WaterDrop
                                    AtmosphereMode.COSMIC_CHORD -> Icons.Default.Waves
                                },
                                contentDescription = null,
                                tint = if (atmosphereMode == AtmosphereMode.OFF) TextMuted else CrystalBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expandedMenu == "atmosphere",
                        onDismissRequest = { expandedMenu = null },
                        modifier = Modifier.background(CosmicSurface)
                    ) {
                        AtmosphereMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.displayName, color = TextPrimary) },
                                onClick = {
                                    onAtmosphereSelected(mode)
                                    expandedMenu = null
                                }
                            )
                        }
                    }
                }

                // Physics Mode Selector
                Box {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                expandedMenu = if (expandedMenu == "physics") null else "physics"
                            }
                            .testTag("physics_button"),
                        color = CosmicSurfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (physicsMode) {
                                    PhysicsMode.FLOAT_DRIFT -> Icons.Default.FilterVintage
                                    PhysicsMode.ORBIT_GRAVITY -> Icons.Default.BrightnessLow
                                    PhysicsMode.WALL_BOUNCE -> Icons.Default.SwapCalls
                                },
                                contentDescription = null,
                                tint = AuroraPink,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expandedMenu == "physics",
                        onDismissRequest = { expandedMenu = null },
                        modifier = Modifier.background(CosmicSurface)
                    ) {
                        PhysicsMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(mode.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                        Text(mode.iconDescription, color = TextSecondary, fontSize = 10.sp)
                                    }
                                },
                                onClick = {
                                    onPhysicsSelected(mode)
                                    expandedMenu = null
                                }
                            )
                        }
                    }
                }

                // Save Constellation Button
                IconButton(
                    onClick = onSaveClicked,
                    modifier = Modifier
                        .size(38.dp)
                        .background(CyanGlow.copy(alpha = 0.18f), CircleShape)
                        .border(1.dp, CyanGlow.copy(alpha = 0.5f), CircleShape)
                        .testTag("save_constellation_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = "Save Soundscape",
                        tint = CyanGlow,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsBottomSheet(
    presets: List<PresetSoundscape>,
    savedConstellations: List<ConstellationEntity>,
    onSelectPreset: (PresetSoundscape) -> Unit,
    onSelectSaved: (ConstellationEntity) -> Unit,
    onDeleteSaved: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CosmicSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = CosmicBorder) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Soundscapes & Library",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Tabs: Presets vs Saved
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CosmicSurfaceVariant,
                contentColor = CyanGlow,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = CyanGlow
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Presets (${presets.size})", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Saved (${savedConstellations.size})", fontWeight = FontWeight.SemiBold) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                // Built-in Presets
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presets) { preset ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSelectPreset(preset) },
                            color = CosmicSurfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.title,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = preset.subtitle,
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(CyanGlow.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Load Preset",
                                        tint = CyanGlow,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // User Saved Constellations
                if (savedConstellations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No saved soundscapes yet.\nTap the bookmark button to save your creations!",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(savedConstellations) { item ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onSelectSaved(item) },
                                color = CosmicSurfaceVariant,
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CosmicBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            color = TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "${item.scaleName} · ${item.nodeCount} nodes · ${item.bpm} BPM",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onDeleteSaved(item.id) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = "Delete",
                                                tint = AuroraPink,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
