package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

enum class SoundTexture(val displayName: String, val description: String) {
    CRYSTAL("Crystal Chime", "Crystalline bell harmonics with shimmering decay"),
    ZEN_BELL("Zen Bell", "Singing bowl overtone resonance with gentle ring"),
    CYBER_PLUCK("Cyber Pluck", "Punchy resonant synthesizer pluck"),
    PURE_SINE("Pure Sine", "Warm, meditative sinusoidal fundamental tone")
}

enum class SoundScale(
    val displayName: String,
    val noteNames: List<String>,
    val frequencies: List<Float>,
    val defaultPalette: List<Color>
) {
    PENTATONIC(
        displayName = "Pentatonic Major",
        noteNames = listOf("C4", "D4", "E4", "G4", "A4", "C5", "D5", "E5", "G5", "A5"),
        frequencies = listOf(261.63f, 293.66f, 329.63f, 392.00f, 440.00f, 523.25f, 587.33f, 659.25f, 783.99f, 880.00f),
        defaultPalette = listOf(CrystalBlue, CyanGlow, GoldenSun, AuroraPink, NebulaViolet)
    ),
    HIRAJOSHI(
        displayName = "Zen Hirajoshi",
        noteNames = listOf("C4", "D4", "Eb4", "G4", "Ab4", "C5", "D5", "Eb5", "G5", "Ab5"),
        frequencies = listOf(261.63f, 293.66f, 311.13f, 392.00f, 415.30f, 523.25f, 587.33f, 622.25f, 783.99f, 830.61f),
        defaultPalette = listOf(EmeraldZen, ElectricTeal, CyanGlow, CrystalBlue, GoldenSun)
    ),
    SOLFEGGIO(
        displayName = "Solfeggio 528Hz",
        noteNames = listOf("396Hz", "417Hz", "528Hz", "639Hz", "741Hz", "852Hz", "963Hz", "1056Hz"),
        frequencies = listOf(396.00f, 417.00f, 528.00f, 639.00f, 741.00f, 852.00f, 963.00f, 1056.00f),
        defaultPalette = listOf(SolfeggioAmber, GoldenSun, AuroraPink, NebulaViolet, CrystalBlue)
    ),
    CELESTIAL(
        displayName = "Celestial Lydian",
        noteNames = listOf("C4", "D4", "E4", "F#4", "G4", "A4", "B4", "C5", "D5", "E5"),
        frequencies = listOf(261.63f, 293.66f, 329.63f, 369.99f, 392.00f, 440.00f, 493.88f, 523.25f, 587.33f, 659.25f),
        defaultPalette = listOf(NebulaViolet, AuroraPink, CrystalBlue, CyanGlow, GoldenSun)
    ),
    CYBER_MINOR(
        displayName = "Cyber Minor",
        noteNames = listOf("A3", "C4", "D4", "E4", "G4", "A4", "C5", "D5", "E5", "G5"),
        frequencies = listOf(220.00f, 261.63f, 293.66f, 329.63f, 392.00f, 440.00f, 523.25f, 587.33f, 659.25f, 783.99f),
        defaultPalette = listOf(CyanGlow, ElectricTeal, AuroraPink, NebulaViolet, CrystalBlue)
    )
}

enum class PhysicsMode(val displayName: String, val iconDescription: String) {
    FLOAT_DRIFT("Floating Drift", "Nodes drift smoothly with kinetic momentum"),
    ORBIT_GRAVITY("Orbit Gravity", "Nodes gently orbit around the central pulsar"),
    WALL_BOUNCE("Wall Bounce", "Nodes bounce off screen boundaries sounding on impact")
}

enum class AtmosphereMode(val displayName: String) {
    OFF("Atmosphere: Off"),
    RAIN_MURMUR("Rain Murmur"),
    COSMIC_CHORD("Cosmic Binaural Drone")
}

data class CanvasNode(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var radius: Float = 24f,
    var noteIndex: Int = 0,
    var frequency: Float = 440f,
    var colorValue: Long = 0xFF00B4D8,
    var isPinned: Boolean = false,
    var glow: Float = 0f,
    var lastTriggerTime: Long = 0L
)

data class Ripple(
    val id: Long,
    val x: Float,
    val y: Float,
    var radius: Float = 4f,
    val maxRadius: Float = 160f,
    val colorValue: Long = 0xFF00B4D8,
    var alpha: Float = 1f,
    val speed: Float = 4.5f
)

data class SparkParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float = 1f,
    val colorValue: Long,
    val size: Float
)
