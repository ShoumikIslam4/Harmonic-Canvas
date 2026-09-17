package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.HarmonicAudioEngine
import com.example.data.AppDatabase
import com.example.data.ConstellationEntity
import com.example.data.ConstellationRepository
import com.example.data.PresetSoundscape
import com.example.model.*
import com.example.util.HapticHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

class CanvasViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ConstellationRepository
    val audioEngine = HarmonicAudioEngine()

    private val _nodes = MutableStateFlow<List<CanvasNode>>(emptyList())
    val nodes: StateFlow<List<CanvasNode>> = _nodes.asStateFlow()

    private val _ripples = MutableStateFlow<List<Ripple>>(emptyList())
    val ripples: StateFlow<List<Ripple>> = _ripples.asStateFlow()

    private val _sparks = MutableStateFlow<List<SparkParticle>>(emptyList())
    val sparks: StateFlow<List<SparkParticle>> = _sparks.asStateFlow()

    private val _currentScale = MutableStateFlow(SoundScale.SOLFEGGIO)
    val currentScale: StateFlow<SoundScale> = _currentScale.asStateFlow()

    private val _currentTexture = MutableStateFlow(SoundTexture.CRYSTAL)
    val currentTexture: StateFlow<SoundTexture> = _currentTexture.asStateFlow()

    private val _physicsMode = MutableStateFlow(PhysicsMode.FLOAT_DRIFT)
    val physicsMode: StateFlow<PhysicsMode> = _physicsMode.asStateFlow()

    private val _atmosphereMode = MutableStateFlow(AtmosphereMode.OFF)
    val atmosphereMode: StateFlow<AtmosphereMode> = _atmosphereMode.asStateFlow()

    private val _bpm = MutableStateFlow(72)
    val bpm: StateFlow<Int> = _bpm.asStateFlow()

    private val _isRadarActive = MutableStateFlow(true)
    val isRadarActive: StateFlow<Boolean> = _isRadarActive.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // Radar scan state
    private val _radarSweepAngle = MutableStateFlow(0f)
    val radarSweepAngle: StateFlow<Float> = _radarSweepAngle.asStateFlow()

    private val _radarPulseRadius = MutableStateFlow(0f)
    val radarPulseRadius: StateFlow<Float> = _radarPulseRadius.asStateFlow()

    var canvasWidth: Float = 1080f
    var canvasHeight: Float = 1920f
    private var hasInitializedPreset = false

    val savedConstellations: StateFlow<List<ConstellationEntity>>
    val builtInPresets: List<PresetSoundscape>

    private var nextNodeId = 1L
    private var nextRippleId = 1L

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ConstellationRepository(db.constellationDao())
        builtInPresets = repository.builtInPresets
        savedConstellations = repository.savedConstellations.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        audioEngine.start()
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.stop()
    }

    fun onCanvasSizeKnown(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val wasUninitialized = !hasInitializedPreset
        canvasWidth = w
        canvasHeight = h

        if (wasUninitialized) {
            hasInitializedPreset = true
            loadPreset(builtInPresets.first(), triggerSound = false)
        }
    }

    fun setScale(scale: SoundScale) {
        _currentScale.value = scale
        // Re-tune existing nodes to the new scale frequencies and colors
        val updated = _nodes.value.map { node ->
            val noteIdx = node.noteIndex % scale.frequencies.size
            val freq = scale.frequencies[noteIdx]
            val color = scale.defaultPalette[noteIdx % scale.defaultPalette.size]
            node.copy(
                noteIndex = noteIdx,
                frequency = freq,
                colorValue = color.value.toLong()
            )
        }
        _nodes.value = updated
    }

    fun setSoundTexture(texture: SoundTexture) {
        _currentTexture.value = texture
    }

    fun setPhysicsMode(mode: PhysicsMode) {
        _physicsMode.value = mode
    }

    fun setAtmosphereMode(mode: AtmosphereMode) {
        _atmosphereMode.value = mode
        audioEngine.atmosphereMode = mode
    }

    fun setBpm(newBpm: Int) {
        _bpm.value = newBpm.coerceIn(40, 160)
    }

    fun toggleRadar() {
        _isRadarActive.value = !_isRadarActive.value
    }

    fun toggleMute() {
        val next = !_isMuted.value
        _isMuted.value = next
        audioEngine.isMuted = next
    }

    fun onCanvasTapped(x: Float, y: Float, context: Context?) {
        // Check if tapping existing node
        val currentNodes = _nodes.value
        val clickedNode = currentNodes.find { node ->
            val dx = node.x - x
            val dy = node.y - y
            dx * dx + dy * dy <= (node.radius + 28f) * (node.radius + 28f)
        }

        if (clickedNode != null) {
            // Trigger the tapped node
            triggerNodeSound(clickedNode, context)
        } else {
            // Spawn new node
            spawnNodeAt(x, y, context)
        }
    }

    fun onNodeLongPressed(x: Float, y: Float, context: Context?) {
        val currentNodes = _nodes.value
        val target = currentNodes.find { node ->
            val dx = node.x - x
            val dy = node.y - y
            dx * dx + dy * dy <= (node.radius + 32f) * (node.radius + 32f)
        } ?: return

        // Pin/unpin node or cycle note
        val updated = currentNodes.map { node ->
            if (node.id == target.id) {
                node.copy(isPinned = !node.isPinned)
            } else node
        }
        _nodes.value = updated
        context?.let { HapticHelper.playHeavyClick(it) }
        triggerNodeSound(target, context)
    }

    fun onNodeDoubleTapped(x: Float, y: Float, context: Context?) {
        val currentNodes = _nodes.value
        val target = currentNodes.find { node ->
            val dx = node.x - x
            val dy = node.y - y
            dx * dx + dy * dy <= (node.radius + 32f) * (node.radius + 32f)
        } ?: return

        // Burst node into sparks and remove
        burstNodeSparks(target.x, target.y, target.colorValue)
        _nodes.value = currentNodes.filter { it.id != target.id }
        context?.let { HapticHelper.playTick(it) }
    }

    fun onNodeDragged(nodeId: Long, x: Float, y: Float, vx: Float, vy: Float) {
        val updated = _nodes.value.map { node ->
            if (node.id == nodeId) {
                node.x = x.coerceIn(node.radius, canvasWidth - node.radius)
                node.y = y.coerceIn(node.radius, canvasHeight - node.radius)
                node.vx = vx
                node.vy = vy
                node
            } else node
        }
        _nodes.value = updated
    }

    private fun spawnNodeAt(x: Float, y: Float, context: Context?, initialVx: Float = 0f, initialVy: Float = 0f) {
        val scale = _currentScale.value
        val clampedX = x.coerceIn(30f, canvasWidth - 30f)
        val clampedY = y.coerceIn(30f, canvasHeight - 30f)

        // Musical mapping: calculate note index based on vertical height + harmonic distance
        val normalizedY = 1.0f - (clampedY / canvasHeight.coerceAtLeast(1f))
        val noteIdx = (normalizedY * scale.frequencies.size).toInt().coerceIn(0, scale.frequencies.size - 1)
        val freq = scale.frequencies[noteIdx]
        val color = scale.defaultPalette[noteIdx % scale.defaultPalette.size]

        val radius = 22f + (1f - (noteIdx.toFloat() / scale.frequencies.size.toFloat())) * 14f

        val newNode = CanvasNode(
            id = nextNodeId++,
            x = clampedX,
            y = clampedY,
            vx = if (initialVx == 0f) Random.nextFloat() * 20f - 10f else initialVx,
            vy = if (initialVy == 0f) Random.nextFloat() * 20f - 10f else initialVy,
            radius = radius,
            noteIndex = noteIdx,
            frequency = freq,
            colorValue = color.value.toLong(),
            glow = 1.0f,
            lastTriggerTime = System.currentTimeMillis()
        )

        _nodes.value = _nodes.value + newNode
        triggerNodeSound(newNode, context)
        burstNodeSparks(clampedX, clampedY, color.value.toLong(), count = 12)
    }

    private fun triggerNodeSound(node: CanvasNode, context: Context?, velocity: Float = 0.85f) {
        audioEngine.playNote(node.frequency, _currentTexture.value, velocity)
        context?.let { HapticHelper.playTick(it) }

        // Trigger visual ripple
        val ripple = Ripple(
            id = nextRippleId++,
            x = node.x,
            y = node.y,
            radius = node.radius,
            maxRadius = node.radius + 150f,
            colorValue = node.colorValue,
            alpha = 0.95f
        )
        _ripples.value = _ripples.value + ripple

        // Update node glow
        val updated = _nodes.value.map { n ->
            if (n.id == node.id) {
                n.copy(glow = 1.0f, lastTriggerTime = System.currentTimeMillis())
            } else n
        }
        _nodes.value = updated
    }

    private fun burstNodeSparks(x: Float, y: Float, colorVal: Long, count: Int = 16) {
        val newSparks = List(count) {
            val angle = Random.nextDouble(0.0, 2.0 * PI)
            val speed = Random.nextDouble(40.0, 180.0).toFloat()
            SparkParticle(
                x = x,
                y = y,
                vx = cos(angle).toFloat() * speed,
                vy = sin(angle).toFloat() * speed,
                alpha = 1f,
                colorValue = colorVal,
                size = Random.nextDouble(2.5, 6.0).toFloat()
            )
        }
        _sparks.value = _sparks.value + newSparks
    }

    fun clearCanvas() {
        _nodes.value = emptyList()
        _ripples.value = emptyList()
        _sparks.value = emptyList()
    }

    fun updatePhysics(dtSeconds: Float, context: Context?) {
        val dt = dtSeconds.coerceIn(0.001f, 0.05f)
        val mode = _physicsMode.value
        val currentNodes = _nodes.value
        val centerX = canvasWidth / 2f
        val centerY = canvasHeight / 2f
        val now = System.currentTimeMillis()

        // 1. Radar animation
        if (_isRadarActive.value) {
            val bpmVal = _bpm.value.toFloat()
            val sweepSpeed = (bpmVal / 60f) * 180f // degrees per second
            val newAngle = (_radarSweepAngle.value + sweepSpeed * dt) % 360f
            _radarSweepAngle.value = newAngle

            val maxRadius = sqrt(centerX * centerX + centerY * centerY)
            val pulseSpeed = maxRadius * (bpmVal / 60f) * 0.45f
            var newPulseR = _radarPulseRadius.value + pulseSpeed * dt
            if (newPulseR > maxRadius) {
                newPulseR = 0f
            }
            _radarPulseRadius.value = newPulseR

            // Radar beam sweep collision detection: check if sweep angle passes node
            for (node in currentNodes) {
                val dx = node.x - centerX
                val dy = node.y - centerY
                var nodeAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (nodeAngle < 0f) nodeAngle += 360f

                val angleDiff = abs(nodeAngle - newAngle)
                val isAngleClose = angleDiff < 3.5f || angleDiff > 356.5f

                val distFromCenter = sqrt(dx * dx + dy * dy)
                val isPulseClose = abs(distFromCenter - newPulseR) < 14f

                if ((isAngleClose || isPulseClose) && (now - node.lastTriggerTime > 320L)) {
                    triggerNodeSound(node, context, 0.75f)
                }
            }
        }

        // 2. Node movement
        for (node in currentNodes) {
            if (node.isPinned) continue

            when (mode) {
                PhysicsMode.FLOAT_DRIFT -> {
                    node.x += node.vx * dt
                    node.y += node.vy * dt

                    // Soft bounce off edges
                    if (node.x < node.radius) {
                        node.x = node.radius
                        node.vx = abs(node.vx) * 0.8f
                    } else if (node.x > canvasWidth - node.radius) {
                        node.x = canvasWidth - node.radius
                        node.vx = -abs(node.vx) * 0.8f
                    }

                    if (node.y < node.radius) {
                        node.y = node.radius
                        node.vy = abs(node.vy) * 0.8f
                    } else if (node.y > canvasHeight - node.radius) {
                        node.y = canvasHeight - node.radius
                        node.vy = -abs(node.vy) * 0.8f
                    }

                    // Gentle fluid damping
                    node.vx *= 0.992f
                    node.vy *= 0.992f
                }

                PhysicsMode.ORBIT_GRAVITY -> {
                    val dx = centerX - node.x
                    val dy = centerY - node.y
                    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(60f)

                    // Gravitational attraction
                    val gravityStrength = 1400f / dist
                    val ax = (dx / dist) * gravityStrength
                    val ay = (dy / dist) * gravityStrength

                    // Tangential velocity for orbit
                    val tangX = -dy / dist * 65f
                    val tangY = dx / dist * 65f

                    node.vx += (ax + tangX * 0.05f) * dt
                    node.vy += (ay + tangY * 0.05f) * dt

                    node.x += node.vx * dt
                    node.y += node.vy * dt

                    node.vx *= 0.995f
                    node.vy *= 0.995f
                }

                PhysicsMode.WALL_BOUNCE -> {
                    node.x += node.vx * dt
                    node.y += node.vy * dt

                    var bounced = false
                    if (node.x <= node.radius) {
                        node.x = node.radius
                        node.vx = abs(node.vx)
                        bounced = true
                    } else if (node.x >= canvasWidth - node.radius) {
                        node.x = canvasWidth - node.radius
                        node.vx = -abs(node.vx)
                        bounced = true
                    }

                    if (node.y <= node.radius) {
                        node.y = node.radius
                        node.vy = abs(node.vy)
                        bounced = true
                    } else if (node.y >= canvasHeight - node.radius) {
                        node.y = canvasHeight - node.radius
                        node.vy = -abs(node.vy)
                        bounced = true
                    }

                    if (bounced && (now - node.lastTriggerTime > 300L)) {
                        triggerNodeSound(node, context, 0.7f)
                    }
                }
            }

            // Glow fade
            if (node.glow > 0f) {
                node.glow = (node.glow - dt * 2.2f).coerceAtLeast(0f)
            }
        }

        // 3. Update Ripples
        val updatedRipples = _ripples.value.mapNotNull { ripple ->
            ripple.radius += ripple.speed * (dt * 60f)
            ripple.alpha -= 0.022f * (dt * 60f)
            if (ripple.alpha <= 0.02f || ripple.radius >= ripple.maxRadius) {
                null
            } else {
                ripple
            }
        }
        _ripples.value = updatedRipples

        // 4. Update Sparks
        val updatedSparks = _sparks.value.mapNotNull { spark ->
            spark.x += spark.vx * dt
            spark.y += spark.vy * dt
            spark.vx *= 0.96f
            spark.vy *= 0.96f
            spark.alpha -= 0.035f * (dt * 60f)
            if (spark.alpha <= 0.02f) null else spark
        }
        _sparks.value = updatedSparks
    }

    fun loadPreset(preset: PresetSoundscape, triggerSound: Boolean = true) {
        val targetScale = try {
            SoundScale.valueOf(preset.scaleName)
        } catch (_: Exception) {
            SoundScale.SOLFEGGIO
        }
        val targetTexture = try {
            SoundTexture.valueOf(preset.soundMode)
        } catch (_: Exception) {
            SoundTexture.CRYSTAL
        }
        val targetPhysics = try {
            PhysicsMode.valueOf(preset.physicsMode)
        } catch (_: Exception) {
            PhysicsMode.FLOAT_DRIFT
        }
        val targetAtmosphere = try {
            AtmosphereMode.valueOf(preset.atmosphereMode)
        } catch (_: Exception) {
            AtmosphereMode.OFF
        }

        _currentScale.value = targetScale
        _currentTexture.value = targetTexture
        _physicsMode.value = targetPhysics
        setAtmosphereMode(targetAtmosphere)
        _bpm.value = preset.bpm
        _isRadarActive.value = preset.isRadarActive

        val w = if (canvasWidth > 100f) canvasWidth else 1080f
        val h = if (canvasHeight > 100f) canvasHeight else 1920f

        val spawnedNodes = preset.relativeNodeCoords.mapIndexed { index, (rx, ry, noteIdx) ->
            val safeNoteIdx = noteIdx % targetScale.frequencies.size
            val freq = targetScale.frequencies[safeNoteIdx]
            val color = targetScale.defaultPalette[safeNoteIdx % targetScale.defaultPalette.size]
            val radius = 24f + (1f - (safeNoteIdx.toFloat() / targetScale.frequencies.size.toFloat())) * 12f

            CanvasNode(
                id = nextNodeId++,
                x = rx * w,
                y = ry * h,
                vx = Random.nextFloat() * 16f - 8f,
                vy = Random.nextFloat() * 16f - 8f,
                radius = radius,
                noteIndex = safeNoteIdx,
                frequency = freq,
                colorValue = color.value.toLong(),
                glow = 0.8f,
                lastTriggerTime = System.currentTimeMillis()
            )
        }

        _nodes.value = spawnedNodes
        _ripples.value = emptyList()

        if (triggerSound && spawnedNodes.isNotEmpty()) {
            triggerNodeSound(spawnedNodes.first(), null)
        }
    }

    fun saveCurrentConstellation(title: String) {
        viewModelScope.launch {
            val safeTitle = title.trim().ifEmpty { "My Soundscape" }
            val currentNodes = _nodes.value
            val w = canvasWidth.coerceAtLeast(1f)
            val h = canvasHeight.coerceAtLeast(1f)

            // Encode nodes into simple format: rx,ry,noteIndex;...
            val encodedData = currentNodes.joinToString(";") {
                val rx = it.x / w
                val ry = it.y / h
                "%.4f,%.4f,%d".format(rx, ry, it.noteIndex)
            }

            val entity = ConstellationEntity(
                title = safeTitle,
                scaleName = _currentScale.value.name,
                soundMode = _currentTexture.value.name,
                bpm = _bpm.value,
                isRadarActive = _isRadarActive.value,
                atmosphereMode = _atmosphereMode.value.name,
                physicsMode = _physicsMode.value.name,
                nodeCount = currentNodes.size,
                nodesData = encodedData
            )
            repository.saveConstellation(entity)
        }
    }

    fun loadSavedConstellation(entity: ConstellationEntity) {
        val targetScale = try {
            SoundScale.valueOf(entity.scaleName)
        } catch (_: Exception) {
            SoundScale.SOLFEGGIO
        }
        val targetTexture = try {
            SoundTexture.valueOf(entity.soundMode)
        } catch (_: Exception) {
            SoundTexture.CRYSTAL
        }
        val targetPhysics = try {
            PhysicsMode.valueOf(entity.physicsMode)
        } catch (_: Exception) {
            PhysicsMode.FLOAT_DRIFT
        }
        val targetAtmosphere = try {
            AtmosphereMode.valueOf(entity.atmosphereMode)
        } catch (_: Exception) {
            AtmosphereMode.OFF
        }

        _currentScale.value = targetScale
        _currentTexture.value = targetTexture
        _physicsMode.value = targetPhysics
        setAtmosphereMode(targetAtmosphere)
        _bpm.value = entity.bpm
        _isRadarActive.value = entity.isRadarActive

        val w = if (canvasWidth > 100f) canvasWidth else 1080f
        val h = if (canvasHeight > 100f) canvasHeight else 1920f

        val coords = entity.nodesData.split(";").mapNotNull { chunk ->
            val parts = chunk.split(",")
            if (parts.size >= 3) {
                val rx = parts[0].toFloatOrNull() ?: return@mapNotNull null
                val ry = parts[1].toFloatOrNull() ?: return@mapNotNull null
                val noteIdx = parts[2].toIntOrNull() ?: 0
                Triple(rx, ry, noteIdx)
            } else null
        }

        val restoredNodes = coords.map { (rx, ry, noteIdx) ->
            val safeNoteIdx = noteIdx % targetScale.frequencies.size
            val freq = targetScale.frequencies[safeNoteIdx]
            val color = targetScale.defaultPalette[safeNoteIdx % targetScale.defaultPalette.size]
            val radius = 24f + (1f - (safeNoteIdx.toFloat() / targetScale.frequencies.size.toFloat())) * 12f

            CanvasNode(
                id = nextNodeId++,
                x = rx * w,
                y = ry * h,
                vx = Random.nextFloat() * 16f - 8f,
                vy = Random.nextFloat() * 16f - 8f,
                radius = radius,
                noteIndex = safeNoteIdx,
                frequency = freq,
                colorValue = color.value.toLong(),
                glow = 0.8f,
                lastTriggerTime = System.currentTimeMillis()
            )
        }

        _nodes.value = restoredNodes
        _ripples.value = emptyList()
    }

    fun deleteSavedConstellation(id: Long) {
        viewModelScope.launch {
            repository.deleteConstellation(id)
        }
    }
}
