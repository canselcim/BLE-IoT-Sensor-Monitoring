package com.example.blemakinesii

import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import com.example.blemakinesii.BleUiState
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height



@Composable
fun MultiSensorChart(
    tempHistory: List<Float>,
    distHistory: List<Float>,
    lightHistory: List<Float>
) {


    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setPinchZoom(true)
                axisRight.isEnabled = false
                xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                legend.isEnabled = true

                // Eksenlerin otomatik ölçeklenmesini sağlayalım
                axisLeft.isGranularityEnabled = true
                axisLeft.granularity = 1f
            }
        },
        update = { chart ->
            val dataSets = mutableListOf<LineDataSet>()

            // 1. Sıcaklık (Kırmızı)
            if (tempHistory.isNotEmpty()) {
                val tempEntries = tempHistory.mapIndexed { i, v -> Entry(i.toFloat(), v) }
                dataSets.add(createDataSet(tempEntries, "Sıcaklık (°C)", android.graphics.Color.RED))
            }

            // 2. Mesafe Veri Seti (Mavi)
            /* if (distHistory.isNotEmpty()) {
                val distEntries = distHistory.mapIndexed { i, v ->
                    // Eğer veri çok dalgalıysa veya 0 geliyorsa kontrol edilebilir
                    Entry(i.toFloat(), v)
                }
                val distSet = createDataSet(distEntries, "Mesafe (cm)", android.graphics.Color.BLUE).apply {
                    // Mesafe verisi için özel stil: Çizgiyi biraz daha kalın yapalım
                    lineWidth = 3f
                    setDrawFilled(true)
                    fillColor = android.graphics.Color.BLUE
                    fillAlpha = 20
                }
                dataSets.add(distSet)
            }*/

            // 3. Işık (Turuncu)
            if (lightHistory.isNotEmpty()) {
                val lightEntries = lightHistory.mapIndexed { i, v -> Entry(i.toFloat(), v) }
                dataSets.add(createDataSet(lightEntries, "Işık (%)", android.graphics.Color.rgb(255, 165, 0)))
            }

            // ... Işık bloğundan hemen sonra ekle ...
            if (distHistory.isNotEmpty()) {
                val distEntries = distHistory.mapIndexed { i, v -> Entry(i.toFloat(), v) }
                dataSets.add(createDataSet(distEntries, "Mesafe (cm)", android.graphics.Color.BLUE))
            }

            if (dataSets.isNotEmpty()) {
                // Hata veren satırı bununla değiştir:
                chart.data = LineData(dataSets as MutableList<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>)

                // Eksenlerin tüm verileri (Mesafe dahil) kapsamasını sağla
                chart.notifyDataSetChanged()
                chart.data.notifyDataChanged()

                // Sol ekseni en yüksek değere göre otomatik ayarla
                chart.axisLeft.resetAxisMaximum()
                chart.axisLeft.resetAxisMinimum()

                chart.invalidate()
            }
        }
    )
}

// Yardımcı Fonksiyon: Veri seti stilini tek yerden yönetmek için
fun createDataSet(entries: List<Entry>, label: String, color: Int): LineDataSet {
    return LineDataSet(entries, label).apply {
        this.color = color
        this.lineWidth = 2f
        setDrawCircles(true)
        circleRadius = 2f
        setCircleColor(color)
        setDrawValues(false)
        mode = LineDataSet.Mode.CUBIC_BEZIER
    }
}
