@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.blemakinesii.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.blemakinesii.MultiSensorChart
import com.example.blemakinesii.data.local.SensorReadingEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onRange: (TimeRange) -> Unit,
    onBucket: (Bucket) -> Unit,
    onMetric: (Metric) -> Unit
) {
    val df = remember { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr")) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RangeDropdown(state.range, onRange, Modifier.weight(1f))
            BucketDropdown(state.bucket, onBucket, Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))

        MetricDropdown(
            current = state.metric,
            onSelect = onMetric,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Seçilen metriğe göre TEK SERİ üret
        val temp = if (state.metric == Metric.TEMP) state.points.mapNotNull { it.avgTemp } else emptyList()
        val light = if (state.metric == Metric.LIGHT) state.points.mapNotNull { it.avgLight } else emptyList()
        val dist = if (state.metric == Metric.DIST) state.points.mapNotNull { it.avgDist } else emptyList()

        MultiSensorChart(
            tempHistory = temp,
            distHistory = dist,
            lightHistory = light
        )

        Spacer(Modifier.height(12.dp))

        Text("Kayıtlar", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            items(state.raw) { r ->
                val t = df.format(Date(r.timestamp))
                val line = metricLine(state.metric, r)

                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(t, style = MaterialTheme.typography.labelMedium)
                        Text(line)
                    }
                }
            }
        }
    }
}

private fun metricLine(metric: Metric, r: SensorReadingEntity): String {
    return when (metric) {
        Metric.TEMP -> "Sıcaklık: ${r.temperature ?: "-"} °C"
        Metric.LIGHT -> "Işık: ${r.light ?: "-"} %"
        Metric.DIST -> "Mesafe: ${r.distance ?: "-"} cm"
        Metric.SOUND -> "Ses: ${r.sound ?: "-"}"
        Metric.MOTOR -> "Motor: ${r.motorState ?: "-"}"
    }
}

@Composable
private fun MetricDropdown(
    current: Metric,
    onSelect: (Metric) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        TextField(
            value = current.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Gösterilecek Veri") },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Metric.values().forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.label) },
                    onClick = { onSelect(m); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun RangeDropdown(
    current: TimeRange,
    onSelect: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier) {
        TextField(
            value = current.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Zaman Aralığı") },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TimeRange.values().forEach {
                DropdownMenuItem(text = { Text(it.label) }, onClick = {
                    onSelect(it); expanded = false
                })
            }
        }
    }
}

@Composable
private fun BucketDropdown(
    current: Bucket,
    onSelect: (Bucket) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier) {
        TextField(
            value = current.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Ölçek") },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Bucket.values().forEach {
                DropdownMenuItem(text = { Text(it.label) }, onClick = {
                    onSelect(it); expanded = false
                })
            }
        }
    }
}
