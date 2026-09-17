package com.example.data

import kotlinx.coroutines.flow.Flow

data class PresetSoundscape(
    val id: String,
    val title: String,
    val subtitle: String,
    val scaleName: String,
    val soundMode: String,
    val bpm: Int,
    val isRadarActive: Boolean,
    val atmosphereMode: String,
    val physicsMode: String,
    val relativeNodeCoords: List<Triple<Float, Float, Int>> // normalized (0..1) x, y, noteIndex
)

class ConstellationRepository(private val dao: ConstellationDao) {

    val savedConstellations: Flow<List<ConstellationEntity>> = dao.getAllConstellations()

    suspend fun saveConstellation(entity: ConstellationEntity): Long {
        return dao.insertConstellation(entity)
    }

    suspend fun deleteConstellation(id: Long) {
        dao.deleteConstellationById(id)
    }

    val builtInPresets: List<PresetSoundscape> = listOf(
        PresetSoundscape(
            id = "preset_cosmic_spiral",
            title = "Cosmic Spiral",
            subtitle = "Solfeggio 528Hz · Crystal Chime · Deep Binaural Drone",
            scaleName = "SOLFEGGIO",
            soundMode = "CRYSTAL",
            bpm = 68,
            isRadarActive = true,
            atmosphereMode = "COSMIC_CHORD",
            physicsMode = "ORBIT_GRAVITY",
            relativeNodeCoords = listOf(
                Triple(0.50f, 0.36f, 0),
                Triple(0.62f, 0.42f, 1),
                Triple(0.65f, 0.54f, 2),
                Triple(0.56f, 0.65f, 3),
                Triple(0.42f, 0.65f, 4),
                Triple(0.35f, 0.54f, 5),
                Triple(0.38f, 0.40f, 6),
                Triple(0.50f, 0.46f, 7)
            )
        ),
        PresetSoundscape(
            id = "preset_zen_mandala",
            title = "Zen Rain Mandala",
            subtitle = "Hirajoshi Scale · Zen Bell · Gentle Rain Murmur",
            scaleName = "HIRAJOSHI",
            soundMode = "ZEN_BELL",
            bpm = 56,
            isRadarActive = true,
            atmosphereMode = "RAIN_MURMUR",
            physicsMode = "FLOAT_DRIFT",
            relativeNodeCoords = listOf(
                Triple(0.50f, 0.28f, 0),
                Triple(0.68f, 0.35f, 2),
                Triple(0.72f, 0.53f, 4),
                Triple(0.60f, 0.68f, 6),
                Triple(0.40f, 0.68f, 7),
                Triple(0.28f, 0.53f, 5),
                Triple(0.32f, 0.35f, 3),
                Triple(0.50f, 0.45f, 1),
                Triple(0.50f, 0.57f, 8)
            )
        ),
        PresetSoundscape(
            id = "preset_cyber_matrix",
            title = "Cyber Neon Pulse",
            subtitle = "Cyber Minor · Resonant Pluck · Kinetic Wall Bounce",
            scaleName = "CYBER_MINOR",
            soundMode = "CYBER_PLUCK",
            bpm = 104,
            isRadarActive = true,
            atmosphereMode = "OFF",
            physicsMode = "WALL_BOUNCE",
            relativeNodeCoords = listOf(
                Triple(0.30f, 0.32f, 0),
                Triple(0.70f, 0.32f, 2),
                Triple(0.50f, 0.48f, 4),
                Triple(0.25f, 0.62f, 5),
                Triple(0.75f, 0.62f, 7),
                Triple(0.50f, 0.76f, 9)
            )
        ),
        PresetSoundscape(
            id = "preset_celestial_dream",
            title = "Celestial Lydian",
            subtitle = "Lydian Flying Scale · Pure Sine · Cosmic Drone",
            scaleName = "CELESTIAL",
            soundMode = "PURE_SINE",
            bpm = 74,
            isRadarActive = true,
            atmosphereMode = "COSMIC_CHORD",
            physicsMode = "ORBIT_GRAVITY",
            relativeNodeCoords = listOf(
                Triple(0.50f, 0.30f, 1),
                Triple(0.66f, 0.38f, 3),
                Triple(0.62f, 0.58f, 5),
                Triple(0.50f, 0.66f, 7),
                Triple(0.38f, 0.58f, 6),
                Triple(0.34f, 0.38f, 4),
                Triple(0.50f, 0.48f, 2)
            )
        )
    )
}
