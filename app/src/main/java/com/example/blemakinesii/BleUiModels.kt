package com.example.blemakinesii

data class DeviceRowUi(
    val name: String,
    val address: String,
    val type: DeviceType,
    val temperature: Float? = null,
    val sound: Int? = null,
    val light: Int? = null,
    val distance: Float? = null,
    val motorState: String? = null,
    val motorCapable: Boolean = false
)

data class BleUiState(
    val scanning: Boolean = false,
    val statusText: String = "Durum: Bilgi gelecek ...",
    val isConnected: Boolean = false,
    val devices: List<DeviceRowUi> = emptyList()
)
