@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.blemakinesii

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import com.example.blemakinesii.data.local.AppDatabase
import com.example.blemakinesii.data.local.SensorReadingEntity

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.compose.ui.platform.LocalContext
import com.example.blemakinesii.ui.history.RoomHistoryViewModel
import com.example.blemakinesii.ui.history.RoomHistoryViewModelFactory
import com.example.blemakinesii.ui.history.HistoryScreen


import android.util.Log
import android.widget.Toast
import com.google.firebase.FirebaseApp

import com.google.firebase.messaging.FirebaseMessaging

import androidx.navigation.compose.*

import android.app.Application

class MainActivity : ComponentActivity() {

    private val blePermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // UI state zaten ViewModel’de; burada ekstra bir şey yapmaya gerek yok.
        }

    private val enableBtLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            // BT açıldıktan sonra otomatik scan başlat
            // (toggleScan start/stop olduğu için burada "startScan()" gibi ayrı fonksiyon varsa onu çağırmak daha temiz)
            // Şimdilik aynı toggle mantığına dönelim:
            // Not: Eğer açıkken toggleScan çağırırsan bazen stop'a denk gelebilir.
        }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun promptEnableLocation() {
        Toast.makeText(
            this,
            "BLE taraması için Konum açık olmalıdır. Lütfen açın.",
            Toast.LENGTH_LONG
        ).show()

        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val db = AppDatabase.get(this)
        val dao = db.sensorDao()

        lifecycleScope.launch {
            dao.insertReading(
                SensorReadingEntity(
                    deviceAddress = "test-device",
                    timestamp = System.currentTimeMillis(),
                    temperature = 23.5f,
                    light = 50,
                    sound = 0,
                    distance = 100f,
                    motorState = "TEST"
                )
            )
            Log.d("ROOM", "TEST INSERT OK")
        }



        //Android için bildirim izni
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }

        com.google.firebase.FirebaseApp.initializeApp(this)
        Log.d("FCM", "MainActivity onCreate çalıştı")
        FirebaseApp.initializeApp(this)
        Log.d("FCM", "FirebaseApp init OK")


        FirebaseMessaging.getInstance()
            .subscribeToTopic("sensor-alerts")
            .addOnCompleteListener { task: com.google.android.gms.tasks.Task<Void> ->
                if (task.isSuccessful) {
                    Log.d("FCM", "sensor-alerts topic'e abone olundu")
                } else {
                    Log.e("FCM", "Topic aboneliği başarısız", task.exception)
                }
            }


        requestNeededPermissions()

        setContent {
            MaterialTheme {
                val nav = rememberNavController()

                val bleVm: BleViewModel = viewModel()

                NavHost(navController = nav, startDestination = "main") {

                    composable("main") {
                        val ui by bleVm.ui.collectAsState()

                        BleScreen(
                            ui = ui,

                            onToggleScan = {
                                if (!hasAllPermissions()) {
                                    requestNeededPermissions()
                                    return@BleScreen
                                }
                                if (!isLocationEnabled()) {
                                    promptEnableLocation()
                                    return@BleScreen
                                }
                                ensureBluetoothEnabled()
                                bleVm.toggleScan()
                            },

                            onDeviceClick = { addr ->
                                if (!hasAllPermissions()) {
                                    requestNeededPermissions()
                                    return@BleScreen
                                }
                                if (!isLocationEnabled()) {
                                    promptEnableLocation()
                                    return@BleScreen
                                }
                                bleVm.connect(addr)
                            },

                            onOpenCmd = { addr -> bleVm.sendMotorCommand(addr, "OPEN") },
                            onStopCmd = { addr -> bleVm.sendMotorCommand(addr, "STOP") },
                            onCloseCmd = { addr -> bleVm.sendMotorCommand(addr, "CLOSE") },
                            onOpenLocationSettings = {
                                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            },
                            onOpenHistory = { nav.navigate("history") }
                        )

                    }

                    composable("history") {

                        val app = (LocalContext.current.applicationContext as Application)
                        val historyVm: RoomHistoryViewModel = viewModel(
                            factory = RoomHistoryViewModelFactory(app)
                        )
                        val state by historyVm.uiState.collectAsState()

                        HistoryScreen(
                            state = state,
                            onRange = historyVm::setRange,
                            onBucket = historyVm::setBucket,
                            onMetric = historyVm::setMetric
                        )
                    }
                }
            }
        }



    }

    private fun hasAllPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blePermissions.all { p ->
                ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
            }
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(blePermissions)
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    private fun ensureBluetoothEnabled() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter?.isEnabled == false) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }
}

@Composable
private fun BleScreen(
    ui: BleUiState,
    onToggleScan: () -> Unit,
    onDeviceClick: (String) -> Unit,
    onOpenCmd: (String) -> Unit,
    onStopCmd: (String) -> Unit,
    onCloseCmd: (String) -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val fvm: FirebaseSensorViewModel = viewModel()
    val st by fvm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("BLE Tarayıcı") })
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(12.dp)
                .fillMaxSize()
        ) {
            Text(ui.statusText)

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ui.devices, key = { it.address }) { row ->
                    DeviceCard(
                        row = row,
                        isConnected = ui.isConnected,
                        onClick = { onDeviceClick(row.address) },
                        onOpenCmd = { onOpenCmd(row.address) },
                        onStopCmd = { onStopCmd(row.address) },
                        onCloseCmd = { onCloseCmd (row.address)}
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (st.tempHistory.size >= 2) {
                Text("Sensör Geçmişi", style = MaterialTheme.typography.titleMedium)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    MultiSensorChart(
                        tempHistory = st.tempHistory,
                        distHistory = st.distHistory, // ViewModel'de bu listelerin olduğundan emin ol
                        lightHistory = st.lightHistory
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onToggleScan,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (ui.scanning) "TARAMA DURDUR" else "TARAMA BAŞLAT")

                }
                Button(onClick = onOpenHistory) { Text("History (Room)") }



                /* OutlinedButton(
                   onClick = onOpenLocationSettings,
                   modifier = Modifier.weight(1f)
               ) {
                   Text("Konum Ayarları")
               }*/
            }
        }
    }
}

@Composable
private fun DeviceCard(
    row: DeviceRowUi,
    isConnected: Boolean,
    onClick: () -> Unit,
    onOpenCmd: () -> Unit,
    onStopCmd: () -> Unit,
    onCloseCmd: () -> Unit
) {
    Card(onClick = onClick) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            Text(row.address, style = MaterialTheme.typography.bodySmall)
            Text("Cihaz Türü: ${row.type.label}")

            Spacer(Modifier.height(8.dp))

            row.sound?.let { sound ->
                Text(
                    text = if (sound == 1) "Evde Ses: VAR" else "Evde Ses: YOK",
                    color = if (sound == 1) Color(0xFFD32F2F) else Color.Gray
                )
            }

            row.light?.let { Text("Işık: %$it", color = Color(0xFFF57C00)) }
            row.temperature?.let { Text("Sıcaklık: %.2f °C".format(it), color = Color(0xFF7B1FA2)) }
            row.distance?.let { Text("Mesafe: $it cm", color = Color(0xFF1976D2)) }

            val showMotor = isConnected && row.motorCapable
            if (row.motorCapable) {
                Text("Motor: ${row.motorState ?: "?"}", color = Color(0xFF388E3C))
            }

            if (showMotor) {
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Button(onClick = onOpenCmd) { Text("PERDEYİ AÇ") }
                    Button(onClick = onStopCmd) { Text("DURDUR") }
                    Button(onClick = onCloseCmd) { Text("KAPAT") }
                }
            }
        }
    }




}



