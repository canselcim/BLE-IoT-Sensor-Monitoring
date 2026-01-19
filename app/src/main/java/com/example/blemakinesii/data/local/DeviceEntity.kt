package com.example.blemakinesii.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val address: String, // MAC address
    val name: String,
    val typeLabel: String,
    val lastSeenAt: Long
)
