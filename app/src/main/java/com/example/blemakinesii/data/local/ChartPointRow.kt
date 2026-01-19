// data/local/ChartPointRow.kt
package com.example.blemakinesii.data.local

data class ChartPointRow(
    val bucketStartMillis: Long,
    val avgTemp: Float?,
    val avgLight: Float?,
    val avgDist: Float?
)
