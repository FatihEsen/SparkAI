package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Initializing : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class BondedObdDevice(
    val name: String,
    val address: String,
    val isMock: Boolean = false
)

class Obd2BluetoothManager(private val context: Context) {
    companion object {
        private const val TAG = "Obd2BluetoothManager"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        
        // Mock Device Constant
        const val MOCK_DEVICE_ADDRESS = "00:BB:DD:32:00:11"
        const val MOCK_DEVICE_NAME = "🚗 Virtual OBD-II Car (Simülatör)"
    }

    // Wrap context with attribution for Bluetooth auditing in Android 11+ (API 30+)
    private val attributionContext: Context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.createAttributionContext("bluetooth")
    } else {
        context
    }

    private val bluetoothManager = attributionContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress

    private val _rpmFlow = MutableStateFlow(0f)
    val rpmFlow: StateFlow<Float> = _rpmFlow

    private val _speedFlow = MutableStateFlow(0)
    val speedFlow: StateFlow<Int> = _speedFlow

    // Detailed ECU Diagnostic Telemetry
    private val _coolantTempFlow = MutableStateFlow(0f)
    val coolantTempFlow: StateFlow<Float> = _coolantTempFlow

    private val _throttleFlow = MutableStateFlow(0f)
    val throttleFlow: StateFlow<Float> = _throttleFlow

    private val _engineLoadFlow = MutableStateFlow(0f)
    val engineLoadFlow: StateFlow<Float> = _engineLoadFlow

    private val _intakeTempFlow = MutableStateFlow(0)
    val intakeTempFlow: StateFlow<Int> = _intakeTempFlow

    private val _manifoldPressureFlow = MutableStateFlow(0) // kPa
    val manifoldPressureFlow: StateFlow<Int> = _manifoldPressureFlow

    private val _fuelLevelFlow = MutableStateFlow(0f) // %
    val fuelLevelFlow: StateFlow<Float> = _fuelLevelFlow

    private val _dtcCodesFlow = MutableStateFlow<List<String>>(emptyList()) // Fault code simulator seed (empty on start)
    val dtcCodesFlow: StateFlow<List<String>> = _dtcCodesFlow

    private val _ecuProtocol = MutableStateFlow("Bağlantı Yok / Bilinmiyor")
    val ecuProtocol: StateFlow<String> = _ecuProtocol

    private val _ecuVin = MutableStateFlow("Bağlantı Yok / Bilinmiyor")
    val ecuVin: StateFlow<String> = _ecuVin

    private val _ecuMilStatus = MutableStateFlow(false) // Engine Warning MIL active
    val ecuMilStatus: StateFlow<Boolean> = _ecuMilStatus

    private val _terminalLogs = MutableSharedFlow<String>(replay = 50)
    val terminalLogs: SharedFlow<String> = _terminalLogs

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var connectionJob: Job? = null
    private val isDisconnecting = AtomicBoolean(false)

    // Socket resources
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // For Mock Connection state
    private var mockPedalPressed = false
    private var mockTargetRpm = 800f
    private var mockCurrentRpm = 800f

    fun setMockInput(pedalPressed: Boolean, targetRpm: Float) {
        this.mockPedalPressed = pedalPressed
        this.mockTargetRpm = targetRpm
    }

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            // Below API 31, location check is usually needed for Discovery but classic paired listing works with BLUETOOTH
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BondedObdDevice> {
        val list = mutableListOf<BondedObdDevice>()
        
        // Always append the Virtual Simulator for testing in sandbox emulator
        list.add(BondedObdDevice(MOCK_DEVICE_NAME, MOCK_DEVICE_ADDRESS, isMock = true))

        if (!hasPermissions()) {
            Log.w(TAG, "Requesting paired devices but permissions are missing.")
            return list
        }

        try {
            val paired = bluetoothAdapter?.bondedDevices
            if (paired != null) {
                for (device in paired) {
                    val name = device.name ?: "Bilinmeyen Cihaz"
                    val address = device.address ?: "00:00:00:00:00:00"
                    list.add(BondedObdDevice(name, address, isMock = false))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieval of bonded devices", e)
        }

        return list
    }

    fun connect(address: String) {
        disconnect() // Clean up any existing connection first
        _connectedDeviceAddress.value = address
        _connectionState.value = ConnectionState.Connecting
        isDisconnecting.set(false)

        if (address != MOCK_DEVICE_ADDRESS) {
            _dtcCodesFlow.value = emptyList()
            _ecuMilStatus.value = false
            _ecuProtocol.value = "Bulunuyor..."
            _ecuVin.value = "Okunuyor..."
        } else {
            _dtcCodesFlow.value = listOf("P0300", "P0171")
            _ecuMilStatus.value = true
            _ecuProtocol.value = "ISO 15765-4 (CAN 11/500)"
            _ecuVin.value = "WBA51AF03PCE7980X_M"
            _coolantTempFlow.value = 82f
            _throttleFlow.value = 12f
            _engineLoadFlow.value = 18f
            _intakeTempFlow.value = 28
            _manifoldPressureFlow.value = 101
            _fuelLevelFlow.value = 64.5f
            _rpmFlow.value = 800f
            _speedFlow.value = 0
        }

        connectionJob = coroutineScope.launch {
            if (address == MOCK_DEVICE_ADDRESS) {
                runMockConnection()
            } else {
                runRealBluetoothConnection(address)
            }
        }
    }

    fun disconnect() {
        isDisconnecting.set(true)
        connectionJob?.cancel()
        connectionJob = null

        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing connections", e)
        }

        inputStream = null
        outputStream = null
        socket = null

        _connectedDeviceAddress.value = null
        _connectionState.value = ConnectionState.Disconnected
        _rpmFlow.value = 0f
        _speedFlow.value = 0
        _coolantTempFlow.value = 0f
        _throttleFlow.value = 0f
        _engineLoadFlow.value = 0f
        _intakeTempFlow.value = 0
        _manifoldPressureFlow.value = 0
        _fuelLevelFlow.value = 0f
        logTerminal("🔌 Bağlantı kesildi.")
    }

    fun clearDtcCodes() {
        coroutineScope.launch {
            val out = outputStream
            val input = inputStream
            if (out != null && input != null && socket?.isConnected == true) {
                logTerminal("⚙️ TX: [04] -> Request Clear Codes...")
                try {
                    sendObdCommand(out, "04\r")
                    val resp = readObdResponse(input)
                    logTerminal("📩 RX: $resp")
                    delay(500)
                    _dtcCodesFlow.value = emptyList()
                    _ecuMilStatus.value = false
                    logTerminal("✅ Arıza kodları başarıyla temizlendi.")
                } catch (e: Exception) {
                    logTerminal("❌ Kod temizleme hatası: ${e.localizedMessage}")
                }
            } else {
                logTerminal("⚙️ TX: [04] -> Clear Diagnostic Trouble Codes...")
                delay(500)
                logTerminal("📩 RX: [44] -> MIL/Check Engine Light turned OFF. DTC Cleared.")
                _dtcCodesFlow.value = emptyList()
                _ecuMilStatus.value = false
            }
        }
    }

    fun parseRealDtcCodes(response: String): List<String> {
        val codes = mutableListOf<String>()
        val clean = response.replace("\\s".toRegex(), "").replace("\r", "").replace("\n", "").trim().uppercase()
        if (clean.contains("NODATA") || clean.contains("4300") || clean.isEmpty()) {
            return emptyList()
        }
        val index = clean.indexOf("43")
        if (index != -1) {
            val data = clean.substring(index + 2)
            var i = 0
            while (i + 4 <= data.length) {
                val hexStr = data.substring(i, i + 4)
                if (hexStr == "0000") {
                    i += 4
                    continue
                }
                val firstChar = hexStr[0]
                val category = when (firstChar) {
                    '0', '1', '2', '3' -> 'P'
                    '4', '5', '6', '7' -> 'C'
                    '8', '9', 'A', 'B' -> 'B'
                    else -> 'U'
                }
                val secondDigit = when (firstChar) {
                    '0', '4', '8', 'C' -> '0'
                    '1', '5', '9', 'D' -> '1'
                    '2', '6', 'A', 'E' -> '2'
                    else -> '3'
                }
                val dtc = "$category$secondDigit${hexStr.substring(2)}"
                if (dtc != "P0000" && !codes.contains(dtc)) {
                    codes.add(dtc)
                }
                i += 4
            }
        }
        return codes
    }

    fun triggerDtcScan() {
        coroutineScope.launch {
            val out = outputStream
            val input = inputStream
            if (out != null && input != null && socket?.isConnected == true) {
                logTerminal("⚙️ TX: [0101] -> Request MIL / DTC Status check...")
                try {
                    sendObdCommand(out, "0101\r")
                    val milResp = readObdResponse(input)
                    logTerminal("📩 RX: $milResp")
                    delay(300)

                    logTerminal("⚙️ TX: [03] -> Request Trouble Codes...")
                    sendObdCommand(out, "03\r")
                    val dtcResp = readObdResponse(input)
                    logTerminal("📩 RX: $dtcResp")

                    val realCodes = parseRealDtcCodes(dtcResp)
                    _dtcCodesFlow.value = realCodes
                    _ecuMilStatus.value = realCodes.isNotEmpty()
                    logTerminal("✅ Teşhis Sonucu: ${realCodes.size} arıza kodu tespit edildi: $realCodes")
                } catch (e: Exception) {
                    logTerminal("❌ Teşhis hatası: ${e.localizedMessage}")
                }
            } else {
                logTerminal("⚙️ TX: [0101] -> Mil Status Check")
                delay(300)
                logTerminal("📩 RX: [41 01 82 07 65 04] -> MIL Active, 2 DTC Pending")
                delay(400)
                logTerminal("⚙️ TX: [03] -> Read DTC Error Codes")
                delay(1200)
                if (_ecuMilStatus.value) {
                    _dtcCodesFlow.value = listOf("P0300", "P0171")
                    logTerminal("📩 RX: [43 01 03 01 71 00 00] -> P0300 (Alternatif Silindir Tekleme Yakalandı), P0171 (Sistem Fakir Karışım Bank 1)")
                } else {
                    _dtcCodesFlow.value = emptyList()
                    logTerminal("📩 RX: [43 00] -> Sistem Temiz, Hata Kodu Yok.")
                }
            }
        }
    }

    private fun logTerminal(line: String) {
        Log.d(TAG, "Terminal: $line")
        _terminalLogs.tryEmit(line)
    }

    private suspend fun runMockConnection() {
        logTerminal("🔄 Simülatör Bağlantısı Başlatılıyor...")
        _connectionState.value = ConnectionState.Initializing
        delay(1200)

        logTerminal("⚙️ [ATZ] Gönderiliyor -> OBD2 Reset")
        delay(400)
        logTerminal("📩 [ATZ] Alındı -> ELM327 v2.1 Simulator")
        delay(300)

        logTerminal("⚙️ [ATE0] Gönderiliyor -> Echo Disabled")
        delay(400)
        logTerminal("📩 [ATE0] Alındı -> OK")
        delay(300)

        logTerminal("⚙️ [ATSP0] Gönderiliyor -> Protocol Auto-Detect")
        delay(400)
        logTerminal("📩 [ATSP0] Alındı -> OK (CAN 11-bit / 500kbps)")
        delay(500)

        logTerminal("✅ Sanal OBD2 Başarıyla Bağlandı!")
        _connectionState.value = ConnectionState.Connected

        mockCurrentRpm = 800f
        var speedVal = 0
        var loopCount = 0

        while (connectionJob?.isActive == true && !isDisconnecting.get()) {
            // Continuously read RPM PID: 010C
            logTerminal("⚙️ TX: [010C] -> Request Engine RPM")
            delay(100)

            // Simulate Engine inertia physics for mock mode
            val rxFactor = if (mockPedalPressed) 0.18f else 0.14f
            mockCurrentRpm += (mockTargetRpm - mockCurrentRpm) * rxFactor
            if (mockCurrentRpm < 800f) mockCurrentRpm = 800f

            val calculatedHexVal = (mockCurrentRpm * 4).toInt()
            val byteA = (calculatedHexVal shr 8) and 0xFF
            val byteB = calculatedHexVal and 0xFF
            
            val responseHex = String.format("41 0C %02X %02X", byteA, byteB)
            logTerminal("📩 RX: [$responseHex] (${mockCurrentRpm.toInt()} RPM)")
            _rpmFlow.value = mockCurrentRpm

            // Simulate related sensors continuously
            val targetThrottle = if (mockPedalPressed) 92f else 12f
            _throttleFlow.value += (targetThrottle - _throttleFlow.value) * 0.2f

            val targetLoad = if (mockPedalPressed) 85f else 18f
            _engineLoadFlow.value += (targetLoad - _engineLoadFlow.value) * 0.15f

            // Coolant temp warms up with RPM
            val targetCoolant = 88f + (mockCurrentRpm - 800f) / 120f
            _coolantTempFlow.value += (targetCoolant - _coolantTempFlow.value) * 0.012f

            // Intake temp fluctuates around ambient
            _intakeTempFlow.value = (28f + (mockCurrentRpm / 3000f) + (Math.random() * 0.6).toFloat()).toInt()

            // Manifold pressure (MAP) simulates vacuum and turbo boost pressure!
            // Vacuum is around 35 kPa, ambient is 101 kPa, boost goes up to 210 kPa
            val targetKbps = if (mockPedalPressed) {
                101f + (mockCurrentRpm - 1500f) / 35f // Builds boost over 1500 RPM
            } else {
                35f + (mockCurrentRpm - 800f) * 0.05f
            }.coerceIn(28f, 220f)
            _manifoldPressureFlow.value = targetKbps.toInt()

            // Slow fuel decay
            _fuelLevelFlow.value = (_fuelLevelFlow.value - 0.0004f).coerceAtLeast(1.0f)

            // Periodic secondary PID telemetry query log in terminal for immersive simulation
            loopCount++
            if (loopCount % 3 == 0) {
                delay(80)
                logTerminal("⚙️ TX: [010D] -> Request Speed")
                delay(80)

                val targetSpeed = (((mockCurrentRpm - 800) / 38) * if(mockPedalPressed) 1.2f else 0.8f).toInt().coerceIn(0, 250)
                speedVal += ((targetSpeed - speedVal) * 0.15f).toInt()
                val speedHex = String.format("41 0D %02X", speedVal.coerceIn(0, 255))
                logTerminal("📩 RX: [$speedHex] (${speedVal} km/h)")
                _speedFlow.value = speedVal
            } else if (loopCount % 7 == 0) {
                delay(80)
                logTerminal("⚙️ TX: [0105] -> Request Engine Coolant Temperature")
                delay(80)
                val coolantInt = _coolantTempFlow.value.toInt().coerceIn(0, 255)
                val coolantHex = String.format("41 05 %02X", coolantInt + 40) // A = Temp + 40 -> Hex = Temp + 40
                logTerminal("📩 RX: [$coolantHex] (${_coolantTempFlow.value.toInt()} °C)")
            } else if (loopCount % 13 == 0) {
                delay(80)
                logTerminal("⚙️ TX: [010B] -> Request MAP Pressure (Manifold)")
                delay(80)
                val mapInt = _manifoldPressureFlow.value.coerceIn(0, 255)
                val mapHex = String.format("41 0B %02X", mapInt)
                logTerminal("📩 RX: [$mapHex] (${mapInt} kPa)")
            }

            delay(250) // Simulates natural OBD processing interval
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runRealBluetoothConnection(address: String) {
        if (!hasPermissions()) {
            _connectionState.value = ConnectionState.Error("Bluetooth İzinleri Eksik!")
            logTerminal("❌ Hata: Android Bluetooth izni verilmedi.")
            return
        }

        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            _connectionState.value = ConnectionState.Error("Cihaz bulunamadı.")
            logTerminal("❌ Hata: Mac adresi bulunamadı.")
            return
        }

        logTerminal("🔄 ${device.name ?: "Cihaz"} Bluetooth RFCOMM soketi kuruluyor...")
        
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: Exception) {
            logTerminal("❌ Soket hatası: ${e.localizedMessage}")
            _connectionState.value = ConnectionState.Error("Soket oluşturulamadı.")
            return
        }

        try {
            socket?.connect()
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream
            logTerminal("📡 RFCOMM kanalına bağlandı. Protokol sıfırlanıyor...")
            _connectionState.value = ConnectionState.Initializing
        } catch (e: Exception) {
            logTerminal("❌ Bağlantı hatası: Araç kontağının açık olduğundan ve OBD adaptörüyle bağlantınızın kesilmediğinden emin olun.")
            _connectionState.value = ConnectionState.Error("Bluetooth bağlantısı başarısız.")
            return
        }

        val out = outputStream
        val input = inputStream
        if (out == null || input == null) {
            _connectionState.value = ConnectionState.Error("Akış kanalı açılamadı.")
            logTerminal("❌ Hata: Girdi/Çıktı akışı boş.")
            return
        }

        // Send OBD2 setup initialization commands
        try {
            sendObdCommand(out, "ATZ\r")
            val resetResp = readObdResponse(input)
            logTerminal("📩 OBD: $resetResp")
            delay(500)

            sendObdCommand(out, "ATE0\r")
            val echoResp = readObdResponse(input)
            logTerminal("📩 OBD: $echoResp")
            delay(300)

            sendObdCommand(out, "ATSP0\r")
            val protoResp = readObdResponse(input)
            logTerminal("📩 OBD: $protoResp")
            delay(300)

            sendObdCommand(out, "AT DP\r")
            val rawProto = readObdResponse(input).trim()
            logTerminal("📩 OBD Protokol Standardı: $rawProto")
            if (rawProto.isNotEmpty() && !rawProto.contains("?") && !rawProto.contains("ERROR")) {
                _ecuProtocol.value = rawProto.replace("AT DP", "").replace(">", "").trim()
            } else {
                _ecuProtocol.value = "ISO 15765-4 (CAN 11bit)"
            }
            delay(300)

            logTerminal("⚙️ Şase No Okunuyor (PID 0902)...")
            sendObdCommand(out, "0902\r")
            val vinResp = readObdResponse(input)
            val parsedVin = parseVinResponse(vinResp)
            if (parsedVin != null) {
                _ecuVin.value = parsedVin
                logTerminal("📩 Şase Numarası OKUDU: $parsedVin")
            } else {
                _ecuVin.value = "Okunamadı / Desteklenmiyor"
                logTerminal("⚠️ Şase numarası okunamadı veya araç ECU'su desteklemiyor.")
            }
            delay(300)

            logTerminal("✅ Gerçek OBD2 Hazır ve Aktif!")
            _connectionState.value = ConnectionState.Connected
        } catch (e: Exception) {
            logTerminal("⚠️ Başlatma uyarısı: ${e.localizedMessage}. Devam ediliyor...")
            _connectionState.value = ConnectionState.Connected
        }

        // Active Poll loop
        var errorCount = 0
        var loopIndex = 0
        while (connectionJob?.isActive == true && !isDisconnecting.get()) {
            try {
                // 1. ALWAYS Fetch RPM (High priority)
                sendObdCommand(out, "010C\r")
                val rpmResp = readObdResponse(input)
                val parsedRpm = parseObdRpm(rpmResp)
                if (parsedRpm != null) {
                    _rpmFlow.value = parsedRpm
                    errorCount = 0
                } else {
                    _rpmFlow.value = 0f
                    _speedFlow.value = 0
                    val cleanResp = rpmResp.trim().uppercase()
                    when {
                        cleanResp.contains("SEARCHING") -> {
                            logTerminal("📡 OBD Durumu: Araç protokolü aranıyor, lütfen bekleyin (SEARCHING)...")
                        }
                        cleanResp.contains("STOPPED") -> {
                            logTerminal("📡 OBD Durumu: Bağlantı durduruldu (STOPPED). Yeniden başvuru yapılıyor...")
                        }
                        cleanResp.contains("NO DATA") || cleanResp.contains("NODATA") -> {
                            logTerminal("📡 OBD Durumu: Veri yok, Kontak kapalı veya desteklenmeyen PID (NO DATA)")
                        }
                        cleanResp.contains("UNABLE TO CONNECT") || cleanResp.contains("UNABLETOCONNECT") -> {
                            logTerminal("❌ OBD Durumu: Araca bağlanılamadı (UNABLE TO CONNECT). Renault K-Line veya CAN aranıyor...")
                        }
                        cleanResp.contains("BUS INIT") || cleanResp.contains("BUSINIT") -> {
                            logTerminal("📡 OBD Durumu: Veri yolu başlatılıyor (BUS INIT)...")
                        }
                        cleanResp.contains("ERROR") -> {
                            logTerminal("⚠️ OBD Sorunu: $rpmResp")
                        }
                        cleanResp == "?" -> {
                            logTerminal("⚠️ OBD: Bilinmeyen komut (?)")
                        }
                        rpmResp.isBlank() -> {
                            // Suppress blank logs to make output clean
                        }
                        else -> {
                            logTerminal("⚠️ RX (RPM Beklenmeyen): $rpmResp")
                        }
                    }
                    errorCount++
                    // On error/negotiating state, wait 1500ms and continue to retry RPM only, keeping the communication channel clean.
                    delay(1500)
                    continue
                }
                delay(80)

                // 2. Fetch Speed every 2 loops
                if (loopIndex % 2 == 0) {
                    sendObdCommand(out, "010D\r")
                    val speedResp = readObdResponse(input)
                    val parsedSpeed = parseObdSpeed(speedResp)
                    if (parsedSpeed != null) {
                        _speedFlow.value = parsedSpeed
                    }
                    delay(80)
                }

                // 3. Round-Robin query diagnostics every 5 loops to prevent OBD-II serial bus throttling
                if (loopIndex % 5 == 0) {
                    val targetDiagnosticIndex = (loopIndex / 5) % 6
                    when (targetDiagnosticIndex) {
                        0 -> { // Coolant Temperature (PID 0105)
                            sendObdCommand(out, "0105\r")
                            val resp = readObdResponse(input)
                            parseGenericSingleByte(resp, "4105")?.let { raw ->
                                val coolant = raw - 40f
                                _coolantTempFlow.value = coolant
                                logTerminal("📡 Canlı OBD2 -> Motor Isısı: $coolant °C")
                            }
                        }
                        1 -> { // Throttle Position (PID 0111)
                            sendObdCommand(out, "0111\r")
                            val resp = readObdResponse(input)
                            parseGenericSingleByte(resp, "4111")?.let { raw ->
                                val throttle = (raw * 100f / 255f)
                                _throttleFlow.value = throttle
                            }
                        }
                        2 -> { // Engine Load (PID 0104)
                            sendObdCommand(out, "0104\r")
                            val resp = readObdResponse(input)
                            parseGenericSingleByte(resp, "4104")?.let { raw ->
                                val load = (raw * 100f / 255f)
                                _engineLoadFlow.value = load
                            }
                        }
                        3 -> { // Intake Air Temp (PID 010F)
                            sendObdCommand(out, "010F\r")
                            val resp = readObdResponse(input)
                            parseGenericSingleByte(resp, "410F")?.let { raw ->
                                val iat = (raw - 40).toInt()
                                _intakeTempFlow.value = iat
                            }
                        }
                        4 -> { // Manifold Absolute Pressure / MAP (PID 010B)
                            sendObdCommand(out, "010B\r")
                            val resp = readObdResponse(input)
                            parseGenericSingleByte(resp, "410B")?.let { raw ->
                                _manifoldPressureFlow.value = raw.toInt()
                            }
                        }
                        5 -> { // Fuel Level (PID 012F)
                            sendObdCommand(out, "012F\r")
                            val resp = readObdResponse(input)
                            val parsed = parseGenericSingleByte(resp, "412F")
                            if (parsed != null) {
                                _fuelLevelFlow.value = (parsed * 100f / 255f)
                            } else {
                                if (_fuelLevelFlow.value <= 1.0f) {
                                    val randomStart = 64.0f + (Math.random() * 18.0f).toFloat()
                                    _fuelLevelFlow.value = randomStart
                                    logTerminal("⚠️ Araç PID 012F desteklemiyor. Tahmini Yakıt Seviyesi atanıyor: %${String.format("%.1f", randomStart)}")
                                } else {
                                    if (_rpmFlow.value > 1000f) {
                                        _fuelLevelFlow.value = (_fuelLevelFlow.value - 0.0003f).coerceAtLeast(1.0f)
                                    }
                                }
                            }
                        }
                    }
                    delay(80)
                }

                loopIndex++
                delay(120) // Polling debounce
            } catch (e: Exception) {
                logTerminal("⚠️ İletişim hatası: ${e.localizedMessage}")
                errorCount++
                if (errorCount > 8) {
                    logTerminal("❌ Sürekli hata. Bağlantı koparılıyor...")
                    _connectionState.value = ConnectionState.Error("OBD2 veri okuma hatası.")
                    break
                }
                delay(1000)
            }
        }
    }

    private fun sendObdCommand(out: OutputStream, cmd: String) {
        try {
            out.write(cmd.toByteArray())
            out.flush()
            Log.v(TAG, "Sent OBD cmd: ${cmd.trim()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed send OBD command", e)
            throw e
        }
    }

    private suspend fun readObdResponse(input: InputStream, timeoutMs: Long = 5000): String {
        val stringBuilder = StringBuilder()
        val startTime = System.currentTimeMillis()
        try {
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (input.available() > 0) {
                    val c = input.read()
                    if (c == -1) break
                    val char = c.toChar()
                    stringBuilder.append(char)
                    if (char == '>') {
                        break
                    }
                } else {
                    delay(5)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading OBD response", e)
        }
        return stringBuilder.toString().replace(">", "").trim()
    }

    fun parseObdRpm(response: String): Float? {
        // Obd response formats:
        // "41 0C 1A E1" or "410C1AE1" or "010C\r41 0C 1A E1"
        try {
            // Remove spaces, newlines, carriage returns
            val clean = response.replace("\\s".toRegex(), "").replace("\r", "").replace("\n", "").uppercase()
            
            // Search for substring "410C"
            val index = clean.indexOf("410C")
            if (index != -1 && index + 8 <= clean.length) {
                val hexByteA = clean.substring(index + 4, index + 6)
                val hexByteB = clean.substring(index + 6, index + 8)
                val valA = hexByteA.toInt(16)
                val valB = hexByteB.toInt(16)
                return ((valA * 256) + valB) / 4f
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing RPM response: $response", e)
        }
        return null
    }

    fun parseObdSpeed(response: String): Int? {
        // Format: "41 0D 20" (Hex 20 is 32 km/h) So Speed = value in Hex
        try {
            val clean = response.replace("\\s".toRegex(), "").replace("\r", "").replace("\n", "").uppercase()
            val index = clean.indexOf("410D")
            if (index != -1 && index + 6 <= clean.length) {
                val hexByte = clean.substring(index + 4, index + 6)
                return hexByte.toInt(16)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing SPEED response: $response", e)
        }
        return null
    }

    fun parseGenericSingleByte(response: String, pidPrefix: String): Float? {
        try {
            val clean = response.replace("\\s".toRegex(), "").replace("\r", "").replace("\n", "").uppercase()
            val index = clean.indexOf(pidPrefix)
            if (index != -1 && index + pidPrefix.length + 2 <= clean.length) {
                val hexByte = clean.substring(index + pidPrefix.length, index + pidPrefix.length + 2)
                return hexByte.toInt(16).toFloat()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing generic PID $pidPrefix: $response", e)
        }
        return null
    }

    private fun parseVinResponse(resp: String): String? {
        val cleaned = resp.replace(">", "").replace("\r", " ").replace("\n", " ").trim()
        if (cleaned.contains("NO DATA") || cleaned.contains("ERROR") || cleaned.contains("?")) {
            return null
        }
        
        val hexPairs = ArrayList<String>()
        val words = cleaned.split("\\s+".toRegex())
        for (w in words) {
            val word = w.trim().uppercase()
            if (word.length == 2 && word.all { it in "0123456789ABCDEF" }) {
                hexPairs.add(word)
            } else if (word.contains(":")) {
                val parts = word.split(":")
                for (part in parts) {
                    val p = part.trim()
                    if (p.length == 2 && p.all { it in "0123456789ABCDEF" }) {
                        hexPairs.add(p)
                    }
                }
            } else {
                val cleanWord = word.filter { it in "0123456789ABCDEF" }
                if (cleanWord.length >= 4 && cleanWord.length % 2 == 0) {
                    for (i in 0 until cleanWord.length step 2) {
                        hexPairs.add(cleanWord.substring(i, i + 2))
                    }
                }
            }
        }

        val vinBytes = StringBuilder()
        var headerSkipCount = 0
        for (hex in hexPairs) {
            if (headerSkipCount < 3 && (hex == "49" || hex == "02" || hex == "01")) {
                headerSkipCount++
                continue
            }
            try {
                val byteValue = hex.toInt(16)
                if ((byteValue in 48..57) || (byteValue in 65..90)) {
                    vinBytes.append(byteValue.toChar())
                }
            } catch (ignored: Exception) {}
        }

        val result = vinBytes.toString().trim()
        return if (result.length >= 10) result.take(17) else null
    }
}
