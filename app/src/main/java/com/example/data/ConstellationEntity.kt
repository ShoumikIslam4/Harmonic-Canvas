package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "constellations")
data class ConstellationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val scaleName: String,
    val soundMode: String,
    val bpm: Int,
    val isRadarActive: Boolean,
    val atmosphereMode: String,
    val physicsMode: String,
    val nodeCount: Int,
    val nodesData: String, // Comma-delimited or simple encoded coordinates: "x,y,noteIndex;x,y,noteIndex"
    val timestamp: Long = System.currentTimeMillis()
)
