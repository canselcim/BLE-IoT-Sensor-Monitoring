package com.example.blemakinesii

import androidx.lifecycle.ViewModel
import com.google.firebase.database.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class FirebaseSensorState(
    val tempHistory: List<Float> = emptyList(),
    val distHistory: List<Float> = emptyList(),
    val lightHistory: List<Float> = emptyList()
)

class FirebaseSensorViewModel : ViewModel() {

    private val _state = MutableStateFlow(FirebaseSensorState())
    val state: StateFlow<FirebaseSensorState> = _state

    // Firebase referanslarını tanımlayalım
    private val rootRef = FirebaseDatabase.getInstance().getReference("sensors/current")
    private val tempRef = rootRef.child("temp")
    private val distRef = rootRef.child("distance") // Firebase'deki tam yoluna göre güncelle
    private val lightRef = rootRef.child("light")    // Firebase'deki tam yoluna göre güncelle

    init {
        listenTemp()
        listenDist()
        listenLight()
    }

    private fun listenTemp() {
        tempRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val value = snapshot.getValue(Float::class.java) ?: return
                val newList = (_state.value.tempHistory + value).takeLast(30)
                _state.value = _state.value.copy(tempHistory = newList)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenDist() {
        distRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // snapshot.value'yu önce Any olarak alıp güvenli dönüşüm yapıyoruz
                val raw = snapshot.value
                val distance = when (raw) {
                    is Number -> raw.toFloat()
                    is String -> raw.toFloatOrNull() ?: 0f
                    else -> return
                }

                val newList = (_state.value.distHistory + distance).takeLast(30)
                _state.value = _state.value.copy(distHistory = newList)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }



    private fun listenLight() {
        lightRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val value = snapshot.getValue(Float::class.java) ?: return
                val newList = (_state.value.lightHistory + value).takeLast(30)
                _state.value = _state.value.copy(lightHistory = newList)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}