package com.example.blemakinesii.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sensor_readings",
    indices = [Index(value = ["deviceAddress", "timestamp"])]
)
data class SensorReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String,
    val timestamp: Long,

    val temperature: Float? = null,
    val light: Int? = null,
    val sound: Int? = null,
    val distance: Float? = null,
    val motorState: String? = null
)
