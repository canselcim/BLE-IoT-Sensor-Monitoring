package com.example.blemakinesii

import android.bluetooth.BluetoothDevice

enum class DeviceType(val raw: Int, val label: String) {
    UNKNOWN(0, "Bilinmeyen"),
    COMPUTER(1, "Bilgisayar"),
    PHONE_AUDIO_DEV(2, "Telefon / Kulaklık / Geliştirme Kartları"),
    TV_AUDIO_VIDEO(3, "Ses/Görüntü - TV"),
    PERIPHERAL(4, "Çevre Birimi (Klavye/Mouse)"),
    WEARABLE(5, "Giyilebilir");

    companion object {
        fun fromBluetoothType(type: Int): DeviceType = when (type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> COMPUTER        // 1
            BluetoothDevice.DEVICE_TYPE_LE -> PHONE_AUDIO_DEV      // 2 (Android const’ları böyle)
            BluetoothDevice.DEVICE_TYPE_DUAL -> TV_AUDIO_VIDEO     // 3
            4 -> PERIPHERAL
            5 -> WEARABLE
            else -> UNKNOWN
        }
    }
}
