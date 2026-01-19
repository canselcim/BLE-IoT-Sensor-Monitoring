package com.example.blemakinesii.ui.history

import com.example.blemakinesii.data.local.SensorReadingEntity

data class ChartPoint(
    val bucketStartMillis: Long,
    val avgTemp: Float?,
    val avgLight: Float?,
    val avgDist: Float?
)

enum class Metric(val label: String) {
    TEMP("Sıcaklık"),
    LIGHT("Işık"),
    DIST("Mesafe"),
    SOUND("Ses"),
    MOTOR("Motor")
}

enum class TimeRange(val label: String, val millis: Long) {
    LAST_1H("Son 1 Saat", 1L * 60 * 60 * 1000),
    LAST_24H("Son 24 Saat", 24L * 60 * 60 * 1000),
    LAST_7D("Son 7 Gün", 7L * 24 * 60 * 60 * 1000),
    LAST_30D("Son 30 Gün", 30L * 24 * 60 * 60 * 1000),
    LAST_365D("Son 1 Yıl", 365L * 24 * 60 * 60 * 1000),
}

enum class Bucket(val label: String) {
    MINUTE("Dakikalık"),
    HOUR("Saatlik"),
    DAY("Günlük"),
    MONTH("Aylık"),
    YEAR("Yıllık"),
}

data class HistoryUiState(
    val range: TimeRange = TimeRange.LAST_24H,
    val bucket: Bucket = Bucket.HOUR,
    val metric: Metric = Metric.DIST,
    val points: List<ChartPoint> = emptyList(),
    val raw: List<SensorReadingEntity> = emptyList()
)
