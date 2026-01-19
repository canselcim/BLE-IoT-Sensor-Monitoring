// data/local/SensorDao.kt
package com.example.blemakinesii.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow


import androidx.room.Insert
import androidx.room.OnConflictStrategy

@Dao
interface SensorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: SensorReadingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadings(list: List<SensorReadingEntity>)

    @Query("""
        SELECT * FROM sensor_readings
        WHERE timestamp BETWEEN :fromMillis AND :toMillis
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    fun readRawBetween(fromMillis: Long, toMillis: Long, limit: Int = 1000): Flow<List<SensorReadingEntity>>

    // Dakikalık agregasyon
    @Query("""
        SELECT
          (strftime('%s', datetime(timestamp/1000, 'unixepoch', 'localtime'), 'start of minute') * 1000) AS bucketStartMillis,
          AVG(temperature) AS avgTemp,
          AVG(light) AS avgLight,
          AVG(distance) AS avgDist
        FROM sensor_readings
        WHERE timestamp BETWEEN :fromMillis AND :toMillis
        GROUP BY strftime('%Y-%m-%d %H:%M', datetime(timestamp/1000, 'unixepoch', 'localtime'))
        ORDER BY bucketStartMillis ASC
    """)
    fun aggByMinute(fromMillis: Long, toMillis: Long): Flow<List<ChartPointRow>>

    // Saatlik
    @Query("""
        SELECT
          (strftime('%s', datetime(timestamp/1000, 'unixepoch', 'localtime'), 'start of hour') * 1000) AS bucketStartMillis,
          AVG(temperature) AS avgTemp,
          AVG(light) AS avgLight,
          AVG(distance) AS avgDist
        FROM sensor_readings
        WHERE timestamp BETWEEN :fromMillis AND :toMillis
        GROUP BY strftime('%Y-%m-%d %H', datetime(timestamp/1000, 'unixepoch', 'localtime'))
        ORDER BY bucketStartMillis ASC
    """)
    fun aggByHour(fromMillis: Long, toMillis: Long): Flow<List<ChartPointRow>>

    // Günlük
    @Query("""
        SELECT
          (strftime('%s', date(timestamp/1000, 'unixepoch', 'localtime')) * 1000) AS bucketStartMillis,
          AVG(temperature) AS avgTemp,
          AVG(light) AS avgLight,
          AVG(distance) AS avgDist
        FROM sensor_readings
        WHERE timestamp BETWEEN :fromMillis AND :toMillis
        GROUP BY strftime('%Y-%m-%d', datetime(timestamp/1000, 'unixepoch', 'localtime'))
        ORDER BY bucketStartMillis ASC
    """)
    fun aggByDay(fromMillis: Long, toMillis: Long): Flow<List<ChartPointRow>>

    // Aylık
    @Query("""
        SELECT
          (strftime('%s', date(timestamp/1000, 'unixepoch', 'localtime', 'start of month')) * 1000) AS bucketStartMillis,
          AVG(temperature) AS avgTemp,
          AVG(light) AS avgLight,
          AVG(distance) AS avgDist
        FROM sensor_readings
        WHERE timestamp BETWEEN :fromMillis AND :toMillis
        GROUP BY strftime('%Y-%m', datetime(timestamp/1000, 'unixepoch', 'localtime'))
        ORDER BY bucketStartMillis ASC
    """)
    fun aggByMonth(fromMillis: Long, toMillis: Long): Flow<List<ChartPointRow>>

    // Yıllık
    @Query("""
        SELECT
          (strftime('%s', date(timestamp/1000, 'unixepoch', 'localtime', 'start of year')) * 1000) AS bucketStartMillis,
          AVG(temperature) AS avgTemp,
          AVG(light) AS avgLight,
          AVG(distance) AS avgDist
        FROM sensor_readings
        WHERE timestamp BETWEEN :fromMillis AND :toMillis
        GROUP BY strftime('%Y', datetime(timestamp/1000, 'unixepoch', 'localtime'))
        ORDER BY bucketStartMillis ASC
    """)
    fun aggByYear(fromMillis: Long, toMillis: Long): Flow<List<ChartPointRow>>

    @Query("""
  SELECT * FROM sensor_readings
  WHERE deviceAddress = :address AND timestamp BETWEEN :from AND :to
  ORDER BY timestamp ASC
""")
    suspend fun readingsBetween(address: String, from: Long, to: Long): List<SensorReadingEntity>

}
