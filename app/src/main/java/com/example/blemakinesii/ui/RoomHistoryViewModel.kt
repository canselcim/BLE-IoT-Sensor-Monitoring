package com.example.blemakinesii.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blemakinesii.data.local.SensorRepository
import kotlinx.coroutines.flow.*

class RoomHistoryViewModel(
    private val repo: SensorRepository
) : ViewModel() {

    private val range = MutableStateFlow(TimeRange.LAST_24H)
    private val bucket = MutableStateFlow(Bucket.HOUR)
    private val metric = MutableStateFlow(Metric.DIST)

    private fun nowMillis() = System.currentTimeMillis()

    private val timeWindow: Flow<Pair<Long, Long>> =
        range.map { r -> (nowMillis() - r.millis) to nowMillis() }

    private val chartFlow =
        combine(timeWindow, bucket) { (from, to), b -> Triple(from, to, b) }
            .flatMapLatest { (from, to, b) -> repo.chart(from, to, b) }

    private val rawFlow =
        timeWindow.flatMapLatest { (from, to) -> repo.raw(from, to) }

    val uiState: StateFlow<HistoryUiState> =
        combine(range, bucket, metric, chartFlow, rawFlow) { r, b, m, pts, raw ->
            HistoryUiState(range = r, bucket = b, metric = m, points = pts, raw = raw)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun setRange(r: TimeRange) { range.value = r }
    fun setBucket(b: Bucket) { bucket.value = b }
    fun setMetric(m: Metric) { metric.value = m }
}
