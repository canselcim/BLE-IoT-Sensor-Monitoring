package com.example.blemakinesii

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.coroutines.resume

import com.example.blemakinesii.data.local.SensorRepository
import com.example.blemakinesii.data.local.AppDatabase
import com.example.blemakinesii.data.local.SensorReadingEntity


class BleViewModel(app: Application) : AndroidViewModel(app) {

    // UUID
    private val ESP32_SERVICE_UUID = "12345678-1234-5678-1234-56789abcdef0"
    private val ESP32_TEMP_CHAR_UUID = "00002A6E-0000-1000-8000-00805f9b34fb"
    private val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    private val SOUND_CHAR_UUID = "00002A58-0000-1000-8000-00805f9b34fb"
    private val LIGHT_CHAR_UUID = "00002AFB-0000-1000-8000-00805f9b34fb"
    private val DISTANCE_UUID = "00002A5D-0000-1000-8000-00805f9b34fb"

    private val MOTORCMD_UUID = "12345678-1234-5678-1234-56789abcdef7"
    private val MOTORSTATE_UUID = "12345678-1234-5678-1234-56789abcdef8"

    private val SCAN_PERIOD = 10_000L

    private val _ui = MutableStateFlow(BleUiState())
    val ui: StateFlow<BleUiState> = _ui

    private val foundDevices = LinkedHashMap<String, BluetoothDevice>() // address -> device
    private val sensorMap = mutableMapOf<String, DeviceRowUi>()         // address -> ui row

    private var bluetoothGatt: BluetoothGatt? = null
    private var motorCmdChar: BluetoothGattCharacteristic? = null

    private var scanJob: Job? = null

    private val ctx: Context get() = getApplication()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private val bluetoothLeScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    //  Descriptor write awaiting (CCCD)
    @Volatile private var pendingDescriptorCont: CancellableContinuation<Boolean>? = null
    @Volatile private var pendingDescriptorCharUuid: UUID? = null

    private fun hasPermission(p: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, p) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun locationEnabled(): Boolean {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun setStatus(text: String) {
        _ui.update { it.copy(statusText = text) }
    }

    fun toggleScan() {
        if (_ui.value.scanning) stopScan() else startScan()
    }

    private val repo: SensorRepository by lazy {
        val db = AppDatabase.get(ctx)
        SensorRepository(db.sensorDao())
    }
    private val dao by lazy {
        AppDatabase.get(ctx).sensorDao()
    }


    // son değer buffer'ı (cihaz bazlı)
    private val latestSnapshot = mutableMapOf<String, SensorReadingEntity>()

    private var roomWriteJob: Job? = null


    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            setStatus("Durum: BLUETOOTH_SCAN izni yok")
            return
        }
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            setStatus("Durum: BLUETOOTH_CONNECT izni yok")
            return
        }
        if (bluetoothAdapter?.isEnabled != true) {
            setStatus("Durum: Bluetooth kapalı")
            return
        }
        if (!locationEnabled()) {
            setStatus("Durum: Konum kapalı (Settings'ten aç)")
            // UI tarafında istersen intent ile settings açtıracağız (MainActivity’de)
            return
        }

        if (_ui.value.scanning) return

        _ui.update { it.copy(scanning = true, statusText = "Durum: Tarama başladı, cihazlar aranıyor...") }
        bluetoothLeScanner?.startScan(leScanCallback)

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            delay(SCAN_PERIOD)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        if (!_ui.value.scanning) return

        bluetoothLeScanner?.stopScan(leScanCallback)
        scanJob?.cancel()
        scanJob = null

        _ui.update { it.copy(scanning = false, statusText = "Durum: Tarama durduruldu...") }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val device = foundDevices[address] ?: run {
            setStatus("Durum: Cihaz bulunamadı")
            return
        }
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            setStatus("Durum: BLUETOOTH_CONNECT izni yok")
            return
        }

        stopScan()

        bluetoothGatt?.close()
        bluetoothGatt = null

        setStatus("Durum: Bağlanılıyor: ${device.address}")
        bluetoothGatt = device.connectGatt(
            ctx,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    @SuppressLint("MissingPermission")
    fun sendMotorCommand(address: String, cmd: String) {
        val gatt = bluetoothGatt ?: return
        val ch = motorCmdChar ?: return

        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        ch.value = cmd.toByteArray(Charsets.UTF_8)

        val ok = gatt.writeCharacteristic(ch)
        Log.i("BLE_MOTOR", "WRITE cmd=$cmd ok=$ok")
    }


    private fun upsertDeviceRow(device: BluetoothDevice) {
        val addr = device.address
        val safeName = try { device.name } catch (_: SecurityException) { null } ?: return

        val type = DeviceType.fromBluetoothType(device.type)

        val current = sensorMap[addr]
        val merged = (current ?: DeviceRowUi(
            name = safeName,
            address = addr,
            type = type
        )).copy(
            name = safeName,
            type = type
        )

        sensorMap[addr] = merged
        _ui.update { it.copy(devices = sensorMap.values.toList()) }
    }

    private fun updateSensor(
        addr: String,
        temperature: Float? = null,
        sound: Int? = null,
        light: Int? = null,
        distance: Float? = null,
        motorState: String? = null,
        motorCapable: Boolean? = null
    ) {
        val old = sensorMap[addr] ?: return

        val newRow = old.copy(
            temperature = temperature ?: old.temperature,
            sound = sound ?: old.sound,
            light = light ?: old.light,
            distance = distance ?: old.distance,
            motorState = motorState ?: old.motorState,
            motorCapable = motorCapable ?: old.motorCapable
        )

        sensorMap[addr] = newRow
        _ui.update { it.copy(devices = sensorMap.values.toList()) }

        val now = System.currentTimeMillis()

        // device upsert (adı/type güncel kalsın)
        /* viewModelScope.launch(Dispatchers.IO) {
            repo.upsertDevice(
                address = old.address,
                name = newRow.name,
                typeLabel = newRow.type.label,
                now = now
            )
        } */


        // buffer’ı güncelle (timestamp burada önemli değil, ticker anında basacağız)
        latestSnapshot[addr] = SensorReadingEntity(
            deviceAddress = addr,
            timestamp = now,
            temperature = newRow.temperature,
            light = newRow.light,
            sound = newRow.sound,
            distance = newRow.distance,
            motorState = newRow.motorState
        )

    }

    // --- Scan callback ---
    private val leScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(type: Int, result: ScanResult) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
            val device = result.device
            if (!foundDevices.containsKey(device.address)) {
                foundDevices[device.address] = device
            }
            upsertDeviceRow(device)
        }
    }

    private fun startRoomSampling() {
        if (roomWriteJob != null) return

        roomWriteJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(5_000L)

                val now = System.currentTimeMillis()
                // buffer’da ne varsa DB’ye bas
                latestSnapshot.values.forEach { snap ->
                    dao.insertReading(snap.copy(timestamp = now))
                }

                // İstersen 30 gün öncesini temizle (opsiyonel)
                // repo.deleteOlderThan(now - 30L*24*60*60*1000)
            }
        }
    }

    private fun stopRoomSampling() {
        roomWriteJob?.cancel()
        roomWriteJob = null
    }


    // --- GATT ---
    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                _ui.update { it.copy(isConnected = true) }
                setStatus("Durum: Bağlantı başarılı → Servisler aranıyor...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _ui.update { it.copy(isConnected = false) }
                setStatus("Durum: Cihazla bağlantı kesildi.")
                bluetoothGatt?.close()
                bluetoothGatt = null
                motorCmdChar = null
                stopRoomSampling()
                latestSnapshot.remove(gatt.device.address)

            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return

            val service = gatt.getService(UUID.fromString(ESP32_SERVICE_UUID)) ?: run {
                setStatus("Durum: ESP32 service bulunamadı")
                return
            }

            val tempChar = service.getCharacteristic(UUID.fromString(ESP32_TEMP_CHAR_UUID))
            val soundChar = service.getCharacteristic(UUID.fromString(SOUND_CHAR_UUID))
            val lightChar = service.getCharacteristic(UUID.fromString(LIGHT_CHAR_UUID))
            val distanceChar = service.getCharacteristic(UUID.fromString(DISTANCE_UUID))
            val motorStateChar = service.getCharacteristic(UUID.fromString(MOTORSTATE_UUID))
            motorCmdChar = service.getCharacteristic(UUID.fromString(MOTORCMD_UUID))

            if (motorCmdChar != null) {
                val addr = gatt.device.address
                updateSensor(
                    addr,
                    motorState = sensorMap[addr]?.motorState ?: "STOP",
                    motorCapable = true
                )
            }


            motorCmdChar?.let { ch ->
                Log.d("BLE", "Motor CMD char found: ${ch.uuid}")
            }

            Log.i("GATT", "temp=${tempChar?.uuid} sound=${soundChar?.uuid} light=${lightChar?.uuid} dist=${distanceChar?.uuid} motorState=${motorStateChar?.uuid} motorCmd=${motorCmdChar?.uuid}")

            motorCmdChar?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            val notifyChars = listOfNotNull(tempChar, soundChar, lightChar, distanceChar, motorStateChar)
            notifyChars.forEach { ch -> gatt.setCharacteristicNotification(ch, true) }

            viewModelScope.launch(Dispatchers.IO) {
                // CCCD’leri sırayla yaz (Coroutine ile)
                for (ch in notifyChars) {
                    val ok = writeCccdAwait(gatt, ch)
                    Log.i("GATT", "CCCD wrote ${ch.uuid} ok=$ok")
                }
                withContext(Dispatchers.Main) {
                    setStatus("Durum: CCCD yazıldı, bildirimler aktif.")
                    startRoomSampling()
                }

                // İlk motor state read (senin mantığın)
                // motorStateChar?.let { gatt.readCharacteristic(it) }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val addr = gatt.device.address
            val uuid = characteristic.uuid

            when (uuid) {
                UUID.fromString(ESP32_TEMP_CHAR_UUID) -> {
                    val temp = ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN).float
                    updateSensor(addr, temperature = temp)
                }

                UUID.fromString(DISTANCE_UUID) -> {
                    val dist = ByteBuffer.wrap(characteristic.value).order(ByteOrder.LITTLE_ENDIAN).float
                    updateSensor(addr, distance = dist)
                }

                UUID.fromString(SOUND_CHAR_UUID) -> {
                    val sound = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    updateSensor(addr, sound = sound)
                }

                UUID.fromString(LIGHT_CHAR_UUID) -> {
                    val light = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    updateSensor(addr, light = light)
                }

                UUID.fromString(MOTORSTATE_UUID) -> {
                    val state = String(characteristic.value, Charsets.UTF_8).trim()
                    updateSensor(addr, motorState = state, motorCapable = true)
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val addr = gatt.device.address
            val uuidStr = characteristic.uuid.toString()

            val valueStr = characteristic.value?.toString(Charsets.UTF_8)?.trim() ?: return
            when {
                uuidStr.equals(ESP32_TEMP_CHAR_UUID, true) -> updateSensor(addr, temperature = valueStr.toFloatOrNull())
                uuidStr.equals(SOUND_CHAR_UUID, true) -> updateSensor(addr, sound = valueStr.toIntOrNull())
                uuidStr.equals(LIGHT_CHAR_UUID, true) -> updateSensor(addr, light = valueStr.toIntOrNull())
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val expected = pendingDescriptorCharUuid
            val cont = pendingDescriptorCont
            if (expected == null || cont == null) return

            if (descriptor.characteristic.uuid == expected) {
                pendingDescriptorCharUuid = null
                pendingDescriptorCont = null
                cont.resume(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.i("BLE_MOTOR", "onCharacteristicWrite uuid=${characteristic.uuid} status=$status")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeCccdAwait(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean = suspendCancellableCoroutine { cont ->

        val cccd = characteristic.getDescriptor(UUID.fromString(CCCD_UUID))
        if (cccd == null) {
            Log.e("GATT", "CCCD yok -> ${characteristic.uuid}")
            cont.resume(false)
            return@suspendCancellableCoroutine
        }

        pendingDescriptorCharUuid = characteristic.uuid
        pendingDescriptorCont = cont

        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val ok = gatt.writeDescriptor(cccd)
        Log.i("GATT", "CCCD write start -> ${characteristic.uuid}, ok=$ok")

        if (!ok) {
            pendingDescriptorCharUuid = null
            pendingDescriptorCont = null
            cont.resume(false)
        }
    }

    fun openLocationSettings() {
        // MainActivity bu intent’i çalıştıracak. Burada sadece state veriyoruz.
        setStatus("Durum: Konumu açmanız gerekiyor (Settings)")
    }

    @SuppressLint("MissingPermission")
    override fun onCleared() {
        super.onCleared()
        try {
            bluetoothGatt?.close()
        } catch (_: Exception) {}
        bluetoothGatt = null
        scanJob?.cancel()
        scanJob = null
    }
}
