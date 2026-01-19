package com.example.blemakinesii.ui.history

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.blemakinesii.data.local.AppDatabase
import com.example.blemakinesii.data.local.SensorRepository

class RoomHistoryViewModelFactory(
    private val app: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val dao = AppDatabase.get(app).sensorDao()
        val repo = SensorRepository(dao)
        return RoomHistoryViewModel(repo) as T
    }
}
