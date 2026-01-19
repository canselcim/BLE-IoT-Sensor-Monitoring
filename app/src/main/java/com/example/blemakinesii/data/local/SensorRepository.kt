package com.example.blemakinesii.data.local

import com.example.blemakinesii.ui.history.Bucket
import com.example.blemakinesii.ui.history.ChartPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SensorRepository(private val dao: SensorDao) {

    fun raw(fromMillis: Long, toMillis: Long): Flow<List<SensorReadingEntity>> =
        dao.readRawBetween(fromMillis, toMillis, limit = 2000)

    fun chart(fromMillis: Long, toMillis: Long, bucket: Bucket): Flow<List<ChartPoint>> {
        val src: Flow<List<ChartPointRow>> = when (bucket) {
            Bucket.MINUTE -> dao.aggByMinute(fromMillis, toMillis)
            Bucket.HOUR   -> dao.aggByHour(fromMillis, toMillis)
            Bucket.DAY    -> dao.aggByDay(fromMillis, toMillis)
            Bucket.MONTH  -> dao.aggByMonth(fromMillis, toMillis)
            Bucket.YEAR   -> dao.aggByYear(fromMillis, toMillis)
        }
        return src.map { rows ->
            rows.map { r ->
                ChartPoint(
                    bucketStartMillis = r.bucketStartMillis,
                    avgTemp = r.avgTemp,
                    avgLight = r.avgLight,
                    avgDist = r.avgDist
                )
            }
        }
    }
}