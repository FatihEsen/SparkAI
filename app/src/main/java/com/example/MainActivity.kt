package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bluetooth.BondedObdDevice
import com.example.bluetooth.ConnectionState
import com.example.bluetooth.Obd2BluetoothManager
import com.example.ui.VehicleProfile
import com.example.ui.ObdDashboardViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    ObdDashboardApp(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun ObdDashboardApp(
    modifier: Modifier = Modifier,
    viewModel: ObdDashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val rpm by viewModel.rpm.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val isPedalPressed by viewModel.isPedalPressed.collectAsState()
    val isManualSliderActive by viewModel.isManualSliderActive.collectAsState()
    val manualRpmValue by viewModel.manualRpmSliderValue.collectAsState()
    val consoleLogs by viewModel.terminalConsoleLogs.collectAsState()

    // Collect advanced diagnostics telemetry states
    val coolantTemp by viewModel.coolantTemp.collectAsState()
    val throttle by viewModel.throttle.collectAsState()
    val engineLoad by viewModel.engineLoad.collectAsState()
    val intakeTemp by viewModel.intakeTemp.collectAsState()
    val manifoldPressure by viewModel.manifoldPressure.collectAsState()
    val fuelLevel by viewModel.fuelLevel.collectAsState()
    val dtcCodes by viewModel.dtcCodes.collectAsState()
    val ecuProtocol by viewModel.ecuProtocol.collectAsState()
    val ecuVin by viewModel.ecuVin.collectAsState()
    val ecuMilStatus by viewModel.ecuMilStatus.collectAsState()
    val isDtcScanning by viewModel.isDtcScanning.collectAsState()
    val dtcScanProgress by viewModel.dtcScanProgress.collectAsState()
    val connectedDeviceAddress by viewModel.connectedDeviceAddress.collectAsState()

    val rpmHistory by viewModel.rpmHistory.collectAsState()
    val loadHistory by viewModel.loadHistory.collectAsState()
    val boostHistory by viewModel.boostHistory.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: HUD, 1: Arıza Teşhis, 2: Grafik, 3: Ses, 4: Bağlantı

    // Theme assets
    val carbonBg = Color(0xFF0F1115)       // Deep space background
    val cardBg = Color(0xFF1C1E26)         // Cozy card surface
    val sportAccentRed = Color(0xFFD13438) // Vibrant Luxury Sport Red
    val amberWarning = Color(0xFFFF9500)   // Warning Orange
    val electricCyan = Color(0xFF4ADE80)   // Vibrant active connection glowing green
    val terminalBg = Color(0xFF13151D)     // Terminal/console background
    val labelGray = Color(0xFF919196)      // Modern metadata gray

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted) {
            viewModel.refreshPairedDevices()
        }
    }

    // Auto-check permissions on launch
    LaunchedEffect(Unit) {
        if (!viewModel.hasBluetoothPermissions()) {
            launcher.launch(permissionsToRequest)
        } else {
            viewModel.refreshPairedDevices()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(carbonBg)
    ) {
        // App Header Status Strip (Immersive HUD structure)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SparkAI",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = when (connectionState) {
                                is ConnectionState.Connected -> electricCyan
                                is ConnectionState.Initializing -> Color.Yellow
                                is ConnectionState.Error -> sportAccentRed
                                else -> Color.Gray
                            },
                            shape = CircleShape
                        )
                )
                Text(
                    text = when (connectionState) {
                        is ConnectionState.Disconnected -> "Çevrim dışı"
                        is ConnectionState.Connecting -> "OBD2 Soketine Bağlanıyor..."
                        is ConnectionState.Initializing -> "OBD2 Protokolü Kuruluyor..."
                        is ConnectionState.Connected -> {
                            if (connectedDeviceAddress == "00:BB:DD:32:00:11") {
                                "Çevrim içi (virtual)"
                            } else {
                                val device = pairedDevices.find { it.address == connectedDeviceAddress }
                                val name = device?.name?.replace("🚗 ", "")?.replace(" (Simülatör)", "") ?: "OBD Device"
                                "Bağlı($name)"
                            }
                        }
                        is ConnectionState.Error -> "Hata: " + (connectionState as ConnectionState.Error).message
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFFE2E2E6),
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        // Tab views rendering
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeTab) {
                0 -> CockpitScreen(
                    rpm = rpm,
                    speed = speed,
                    coolantTemp = coolantTemp,
                    throttle = throttle,
                    engineLoad = engineLoad,
                    manifoldPressure = manifoldPressure,
                    fuelLevel = fuelLevel,
                    intakeTemp = intakeTemp,
                    activeProfile = activeProfile,
                    viewModel = viewModel,
                    cardBg = cardBg,
                    electricCyan = electricCyan,
                    sportAccentRed = sportAccentRed
                )
                1 -> DrivingKitScreen(
                    viewModel = viewModel,
                    rpmHistory = rpmHistory,
                    loadHistory = loadHistory,
                    boostHistory = boostHistory,
                    currentRpm = rpm,
                    currentLoad = engineLoad,
                    currentBoost = manifoldPressure,
                    maxProductRpm = activeProfile.maxRpm,
                    cardBg = cardBg,
                    electricCyan = electricCyan,
                    sportAccentRed = sportAccentRed
                )
                2 -> AiDrivingScreen(
                    viewModel = viewModel,
                    cardBg = cardBg,
                    electricCyan = electricCyan,
                    sportAccentRed = sportAccentRed
                )
                3 -> DiagnosticsScreen(
                    isDtcScanning = isDtcScanning,
                    dtcScanProgress = dtcScanProgress,
                    dtcCodes = dtcCodes,
                    ecuProtocol = ecuProtocol,
                    ecuVin = ecuVin,
                    ecuMilStatus = ecuMilStatus,
                    viewModel = viewModel,
                    cardBg = cardBg,
                    electricCyan = electricCyan,
                    sportAccentRed = sportAccentRed,
                    amberWarning = amberWarning
                )
                4 -> SettingsScreen(
                    viewModel = viewModel,
                    cardBg = cardBg,
                    electricCyan = electricCyan,
                    sportAccentRed = sportAccentRed
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        NavigationBar(
            containerColor = Color(0xFF13151D),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            NavigationBarItem(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Gösterge") },
                label = { Text("Gösterge", fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = sportAccentRed,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
            NavigationBarItem(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                icon = { Icon(Icons.Default.LocationOn, contentDescription = "Sürüş Kiti") },
                label = { Text("Sürüş Kiti", fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = sportAccentRed,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
            NavigationBarItem(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                icon = { Icon(Icons.Default.Star, contentDescription = "AI") },
                label = { Text("AI", fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = sportAccentRed,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
            NavigationBarItem(
                selected = activeTab == 3,
                onClick = { activeTab = 3 },
                icon = { Icon(Icons.Default.Warning, contentDescription = "Teşhis") },
                label = { Text("Teşhis", fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = sportAccentRed,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
            NavigationBarItem(
                selected = activeTab == 4,
                onClick = { activeTab = 4 },
                icon = { Icon(Icons.Default.Settings, contentDescription = "Ayarlar") },
                label = { Text("Ayarlar", fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = sportAccentRed,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun CockpitScreen(
    rpm: Float,
    speed: Int,
    coolantTemp: Float,
    throttle: Float,
    engineLoad: Float,
    manifoldPressure: Int,
    fuelLevel: Float,
    intakeTemp: Int,
    activeProfile: VehicleProfile,
    viewModel: ObdDashboardViewModel,
    cardBg: Color,
    electricCyan: Color,
    sportAccentRed: Color
) {
    val scrollState = rememberScrollState()
    
    // Auto-animate gauge updates smoothly
    val animatedRpm by animateFloatAsState(
        targetValue = rpm,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "RpmAnimation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Redline indicator LED panel (F1 style shift lights)
        ShiftLightsIndicator(
            currentRpm = rpm,
            maxRpm = activeProfile.maxRpm
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Custom Radial Tachometer Gauge with Immersive Background Glow
        Box(
            modifier = Modifier
                .size(280.dp)
                .padding(8.dp)
                .drawBehind {
                    // Soft atmospheric red radial gradient background glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(sportAccentRed.copy(alpha = 0.18f), Color.Transparent),
                            center = center,
                            radius = size.width / 1.4f
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            TachometerGauge(
                rpm = animatedRpm,
                maxRpm = activeProfile.maxRpm,
                modifier = Modifier.fillMaxSize(),
                cyanColor = electricCyan,
                redColor = sportAccentRed
            )

            // Digital readout in the center of the gauge styled in clean light tabular numbers
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                DigitalSevenSegmentDisplay(
                    text = String.format("%.0f", rpm),
                    color = if (rpm >= activeProfile.maxRpm * 0.82f) sportAccentRed else Color.White,
                    digitWidth = 24.dp,
                    digitHeight = 44.dp,
                    thickness = 3.5.dp,
                    spacing = 1.2.dp,
                    gap = 4.dp
                )
            }

            // Digital speed indicator positioned lower (Porsche/Sport styling)
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Hız",
                    tint = electricCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                DigitalSevenSegmentDisplay(
                    text = "$speed",
                    color = electricCyan,
                    digitWidth = 14.dp,
                    digitHeight = 24.dp,
                    thickness = 2.dp,
                    spacing = 0.8.dp,
                    gap = 2.dp
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "km/h",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF919196)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Diagnostic Live Sensors 2x3 Grid HUD

        val batteryVoltage = if (rpm > 0f) {
            13.8f + (rpm / 8000f) * 0.4f + (Math.sin(System.currentTimeMillis() / 2000.0) * 0.05).toFloat()
        } else if (fuelLevel > 0f) {
            12.4f
        } else {
            0.0f
        }

        val maxProfileHp = when (activeProfile.id) {
            "lfa" -> 560f
            "v12_skyline" -> 500f
            "rally" -> 300f
            else -> 150f
        }
        val maxProfileTorque = when (activeProfile.id) {
            "lfa" -> 480f
            "v12_skyline" -> 600f
            "rally" -> 400f
            else -> 220f
        }

        val calculatedHp = if (rpm > 0f) {
            ((engineLoad / 100f) * (rpm / activeProfile.maxRpm) * maxProfileHp).coerceIn(0f, maxProfileHp + 30f) + (throttle * 0.1f)
        } else {
            0.0f
        }

        val calculatedTorque = if (rpm > 0f) {
            val rpmPeak = when (activeProfile.id) {
                "lfa" -> 6800f
                "v12_skyline" -> 4500f
                "rally" -> 3500f
                else -> 2500f
            }
            val rpmDiffFraction = (kotlin.math.abs(rpm - rpmPeak) / activeProfile.maxRpm)
            ((engineLoad / 100f) * maxProfileTorque * (1.0f - 0.4f * rpmDiffFraction)).coerceIn(0f, maxProfileTorque)
        } else {
            0.0f
        }

        val instantConsVal = if (rpm > 0f) {
            if (speed > 5) {
                ((engineLoad * 0.15f * (rpm / 2000f)) / (speed / 100f)).coerceIn(3.0f, 29.9f)
            } else {
                (0.8f + (rpm / 5000f) * 1.5f + (throttle * 0.05f))
            }
        } else {
            0.0f
        }

        var averageCons by remember { mutableStateOf(6.8f) }
        LaunchedEffect(rpm, speed) {
            if (rpm > 0f) {
                averageCons = ((averageCons * 149f) + instantConsVal) / 150f
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Anlık Güç & Motor Torku
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TelemetryHUDItem(
                        title = "ANLIK GÜÇ",
                        value = "${calculatedHp.toInt()} HP",
                        progress = (calculatedHp / maxProfileHp).coerceIn(0f, 1f),
                        progressColor = Color(0xFFFFCC00),
                        cardBg = cardBg
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    TelemetryHUDItem(
                        title = "MOTOR TORKU",
                        value = "${calculatedTorque.toInt()} Nm",
                        progress = (calculatedTorque / maxProfileTorque).coerceIn(0f, 1f),
                        progressColor = Color(0xFFFF5E00),
                        cardBg = cardBg
                    )
                }
            }

            // Row 2: Motor Sıvısı & Akü Gerilimi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TelemetryHUDItem(
                        title = "MOTOR SIVISI",
                        value = "${coolantTemp.toInt()} °C",
                        progress = ((coolantTemp - 40f) / 100f).coerceIn(0f, 1f),
                        progressColor = when {
                            coolantTemp > 105 -> sportAccentRed
                            coolantTemp > 95 -> Color(0xFFFF9500)
                            else -> electricCyan
                        },
                        cardBg = cardBg
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    TelemetryHUDItem(
                        title = "AKÜ GERİLİMİ",
                        value = if (batteryVoltage > 0f) String.format("%.1f V", batteryVoltage) else "0.0 V",
                        progress = if (batteryVoltage > 0f) (batteryVoltage - 11f) / 4f else 0f,
                        progressColor = if (batteryVoltage > 11.5f) Color(0xFF00C7FF) else sportAccentRed,
                        cardBg = cardBg
                    )
                }
            }

            // Row 3: Motor Yükü & Gaz Kelebeği
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TelemetryHUDItem(
                        title = "MOTOR YÜKÜ",
                        value = "${engineLoad.toInt()}%",
                        progress = engineLoad / 100f,
                        progressColor = Color(0xFFFF9500),
                        cardBg = cardBg
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    TelemetryHUDItem(
                        title = "GAZ KELEBEĞİ",
                        value = "${throttle.toInt()}%",
                        progress = throttle / 100f,
                        progressColor = electricCyan,
                        cardBg = cardBg
                    )
                }
            }

            // Row 4: Emme Sıcaklığı & Manifold Basıncı (MAP)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TelemetryHUDItem(
                        title = "EMME SICAKLIĞI",
                        value = "$intakeTemp °C",
                        progress = ((intakeTemp - 10f) / 80f).coerceIn(0f, 1f),
                        progressColor = if (intakeTemp > 50) sportAccentRed else Color(0xFF00C7FF),
                        cardBg = cardBg
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    TelemetryHUDItem(
                        title = "MANİFOLD BASINCI",
                        value = "$manifoldPressure kPa",
                        progress = (manifoldPressure / 250f).coerceIn(0f, 1f),
                        progressColor = Color(0xFFA55EEA),
                        cardBg = cardBg
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ShiftLightsIndicator(
    currentRpm: Float,
    maxRpm: Float,
    modifier: Modifier = Modifier
) {
    // 10-LED high indicators. Under 55%: off. 55%-85%: green to amber. 85%+: flashing red.
    val percent = currentRpm / maxRpm
    val flashingTransition = rememberInfiniteTransition(label = "FlashingLeds")
    val isFlashingZone = percent >= 0.85f
    
    val activeCount = when {
        currentRpm < 600f -> 0
        currentRpm in 600f..850f -> 1
        else -> {
            val ratio = (currentRpm - 850f) / (maxRpm - 850f)
            val scaledLeds = 1 + (ratio * 9f)
            scaledLeds.toInt().coerceIn(1, 10)
        }
    }

    val flashAlpha by flashingTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaFlash"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(vertical = 10.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(10) { i ->
            val isLit = i < activeCount

            val ledColor = when {
                !isLit -> Color(0xFF22262F) // Dim/Off
                i < 4 -> Color(0xFF34C759) // Green (First 4)
                i < 8 -> Color(0xFFFF9500) // Amber (Middle 4)
                else -> Color(0xFFFF3B30)   // Redline (Last 2)
            }

            Box(
                modifier = Modifier
                    .size(width = 18.dp, height = 14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        color = if (isFlashingZone && isLit) {
                            ledColor.copy(alpha = flashAlpha)
                        } else {
                            ledColor
                        }
                    )
                    .border(
                        1.dp,
                        if (isLit) Color.White.copy(alpha = 0.3f) else Color.Transparent,
                        RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

@Composable
fun TachometerGauge(
    rpm: Float,
    maxRpm: Float,
    modifier: Modifier = Modifier,
    cyanColor: Color,
    redColor: Color
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val center = Offset(width / 2f, height / 2f)
        val radius = size.minDimension / 2f - 16.dp.toPx()

        // 1. Draw dial semi-transparent back track arc (270 degrees total sweep, from 135 to 405)
        drawArc(
            color = Color(0xFF202534),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
        )

        // Calculate dynamic dial sweep based on RPM
        val rpmPercent = (rpm / maxRpm).coerceIn(0f, 1f)
        val sweepAngle = rpmPercent * 270f

        // 2. Draw active glowing sweep line representing real devir speed
        val activeBrush = Brush.linearGradient(
            colors = listOf(cyanColor, Color.Yellow, redColor),
            start = Offset(0f, height),
            end = Offset(width, 0f)
        )
        
        drawArc(
            brush = activeBrush,
            startAngle = 135f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
        )

        // 3. Draw redzone danger zone arc marking
        // Starts at 82% of maximum devir threshold
        val redzoneStartOffset = 0.82f * 270f
        val redzoneSweep = (1.0f - 0.82f) * 270f
        drawArc(
            color = redColor.copy(alpha = 0.4f),
            startAngle = 135f + redzoneStartOffset,
            sweepAngle = redzoneSweep,
            useCenter = false,
            style = Stroke(width = 14.dp.toPx())
        )

        // 4. Draw Dial ticks and numbers around the gauge face
        val tickCount = 10
        for (i in 0..tickCount) {
            val angleDeg = 135f + (i * (270f / tickCount))
            val angleRad = Math.toRadians(angleDeg.toDouble())
            
            val isRedZone = i >= tickCount * 0.82
            val tickCol = if (isRedZone) redColor else Color.White.copy(alpha = 0.5f)
            val tickLen = if (i % 2 == 0) 12.dp.toPx() else 6.dp.toPx()
            val strokeW = if (i % 2 == 0) 3.dp.toPx() else 1.5.dp.toPx()

            val outerPoint = Offset(
                x = center.x + (radius - 1.dp.toPx()) * cos(angleRad).toFloat(),
                y = center.y + (radius - 1.dp.toPx()) * sin(angleRad).toFloat()
            )
            val innerPoint = Offset(
                x = center.x + (radius - tickLen) * cos(angleRad).toFloat(),
                y = center.y + (radius - tickLen) * sin(angleRad).toFloat()
            )

            drawLine(
                color = tickCol,
                start = innerPoint,
                end = outerPoint,
                strokeWidth = strokeW
            )
        }

        // 5. Draw pointer needle segment (outer part only, floating)
        val needleAngleDeg = 135f + sweepAngle
        val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
        
        val needleEndPoint = Offset(
            x = center.x + (radius + 2.dp.toPx()) * cos(needleAngleRad).toFloat(),
            y = center.y + (radius + 2.dp.toPx()) * sin(needleAngleRad).toFloat()
        )
        
        val needleInnerPoint = Offset(
            x = center.x + (radius - 20.dp.toPx()) * cos(needleAngleRad).toFloat(),
            y = center.y + (radius - 20.dp.toPx()) * sin(needleAngleRad).toFloat()
        )

        drawLine(
            color = redColor,
            start = needleInnerPoint,
            end = needleEndPoint,
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}



@Composable
fun AiDrivingScreen(
    viewModel: ObdDashboardViewModel,
    cardBg: Color,
    electricCyan: Color,
    sportAccentRed: Color
) {
    val isGeminiActive by viewModel.isGeminiActive.collectAsState()
    val geminiPersonality by viewModel.geminiPersonality.collectAsState()
    val geminiLog by viewModel.geminiLog.collectAsState()

    val aiDtcReport by viewModel.aiDtcReport.collectAsState()
    val isAiDtcLoading by viewModel.isAiDtcLoading.collectAsState()

    val aiCoachReport by viewModel.aiCoachReport.collectAsState()
    val isAiCoachLoading by viewModel.isAiCoachLoading.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "YAPAY ZEKA (AI) KOKPİT ASİSTANI",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
        )

        // 1. --- Gemini AI Sürüş Otopilot Paneli ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (isGeminiActive) sportAccentRed.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🤖 GEMINI OTOPİLOT SÜRÜCÜ",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isGeminiActive) sportAccentRed else Color.LightGray
                            )
                        )
                        Text(
                            text = "Yapay zeka gaz pedalını otomatik yönetir",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }

                    Switch(
                        checked = isGeminiActive,
                        onCheckedChange = { viewModel.setGeminiActive(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = sportAccentRed,
                            checkedTrackColor = sportAccentRed.copy(alpha = 0.5f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.LightGray.copy(alpha = 0.2f)
                        )
                    )
                }

                val geminiApiKey by viewModel.geminiApiKey.collectAsState()
                val geminiApiBase by viewModel.geminiApiBase.collectAsState()
                val geminiModelName by viewModel.geminiModelName.collectAsState()

                OutlinedTextField(
                    value = geminiApiKey,
                    onValueChange = { viewModel.setGeminiApiKey(it) },
                    label = { Text("API Anahtarı (Opsiyonel / .env)", fontSize = 11.sp, color = Color.Gray) },
                    placeholder = { Text("API anahtarınızı buraya girin...", fontSize = 12.sp, color = Color.DarkGray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = sportAccentRed,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedContainerColor = Color.Black.copy(alpha = 0.15f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.15f)
                    )
                )

                GeminiProviderAndModelSelector(
                    viewModel = viewModel,
                    sportAccentRed = sportAccentRed,
                    electricCyan = electricCyan
                )

                // Slider to adjust the interval dynamically and avoid 429
                val geminiInterval by viewModel.geminiInterval.collectAsState()
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔄 AI Sürüş Komut Sıklığı:",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$geminiInterval saniye",
                            style = MaterialTheme.typography.bodySmall.copy(color = electricCyan, fontWeight = FontWeight.ExtraBold)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = geminiInterval.toFloat(),
                        onValueChange = { viewModel.setGeminiInterval(it.toInt()) },
                        valueRange = 10f..120f,
                        colors = SliderDefaults.colors(
                            thumbColor = electricCyan,
                            activeTrackColor = electricCyan,
                            inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    )
                    Text(
                        text = "İpucu: Ücretsiz anahtar kullanıyorsanız 429 API sınırı hatasını engellemek için bu süreyi 45-60 saniye veya üzerine getirin.",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontSize = 10.sp)
                    )
                }

                Text(
                    text = "Sürüş Tarzı (Karakter):",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("sport" to "⚡ SPOR", "commuter" to "🚇 ŞEHİR", "peaceful" to "🍃 CHILL").forEach { (id, label) ->
                        val isSelected = geminiPersonality == id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) sportAccentRed.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) sportAccentRed else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.setGeminiPersonality(id) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color.Gray
                                )
                            )
                        }
                    }
                }

                // Console feedback box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = if (isGeminiActive) electricCyan else Color.Gray,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = geminiLog,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isGeminiActive) electricCyan else Color.LightGray,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        // 2. --- AI OBD Teşhis Raporu Bölümü ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔍 AI OTO TEŞHİS ASİSTANI",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "OBD2 telemetrilerini ve arıza kodlarını analiz edin",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = { viewModel.generateAiDtcReport() },
                        enabled = !isAiDtcLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = electricCyan),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (isAiDtcLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Text("Teşhis Et", color = Color.Black, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                if (aiDtcReport.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = aiDtcReport,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.LightGray,
                                lineHeight = 18.sp
                            )
                        )
                    }
                } else {
                    Text(
                        text = "Henüz bir teşhis raporu oluşturulmadı. Başlatmak için 'Teşhis Et' butonuna tıklayın.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        // 3. --- AI Eco / Performans Sürüş Koç Paneli ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🍃 AI SURUŞ & ECO KOÇU",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Sürüş stilinizi değerlendirin ve tasarruf önerileri alın",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = { viewModel.generateAiCoachReport() },
                        enabled = !isAiCoachLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2A000)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (isAiCoachLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Text("Puanla", color = Color.Black, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                if (aiCoachReport.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = aiCoachReport,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.LightGray,
                                lineHeight = 18.sp
                            )
                        )
                    }
                } else {
                    Text(
                        text = "Sürüş davranışınızı değerlendirmek ve yakıt tasarrufu seviyenizi öğrenmek için 'Puanla' butonuna tıklayın.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceItemCard(
    device: BondedObdDevice,
    isActiveConnection: Boolean,
    isAnyOtherConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    cardBg: Color,
    electricCyan: Color,
    sportAccentRed: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (device.isMock) Color.Yellow.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (device.isMock) Icons.Default.PlayArrow else Icons.Default.Check,
                        contentDescription = "Bağlantı Türü",
                        tint = if (device.isMock) Color.Yellow else electricCyan
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            // Connection button (show only if this is the active connection OR no other device is connected)
            if (isActiveConnection || !isAnyOtherConnected) {
                Button(
                    onClick = {
                        if (isActiveConnection) {
                            onDisconnect()
                        } else {
                            onConnect()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActiveConnection) sportAccentRed else if (device.isMock) Color(0xFFFF9500) else electricCyan
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isActiveConnection) "Kes" else "Bağlan",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: ObdDashboardViewModel,
    cardBg: Color,
    electricCyan: Color,
    sportAccentRed: Color
) {
    val scrollState = rememberScrollState()

    // Connection states collected from ViewModel for inline Connection Area at the very top
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedDeviceAddress by viewModel.connectedDeviceAddress.collectAsState()
    val consoleLogs by viewModel.terminalConsoleLogs.collectAsState()
    val terminalBg = Color(0xFF13151D)

    // Gemini states
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Live Diagnostic Terminal logs (inline collapsible/compact)
        Text(
            text = "CANLI OBD2 SERİ TERMİNAL GÜNLÜĞÜ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = terminalBg),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                if (consoleLogs.isEmpty()) {
                    Text(
                        text = "Terminal beklemede...\nOBD bağlantısını başlattığınızda ham paket alışverişleri buraya dökülür.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val terminalScrollState = rememberScrollState()
                    LaunchedEffect(consoleLogs.size) {
                        terminalScrollState.animateScrollTo(terminalScrollState.maxValue)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(terminalScrollState)
                    ) {
                        consoleLogs.takeLast(30).forEach { log ->
                            val color = when {
                                log.startsWith("❌") || log.contains("Hata") -> sportAccentRed
                                log.contains("TX:") -> Color.Yellow
                                log.contains("RX:") -> electricCyan
                                log.contains("✅") -> Color.Green
                                else -> Color.LightGray
                            }
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = color,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 16.sp
                                ),
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(modifier = Modifier.height(4.dp))
        
        // ==========================================
        // 1. OBD2 BAĞLANTI AYARLARI (CONNECTION SECTION AT THE VERY TOP)
        // ==========================================
        Text(
            text = "CANLI OBD2 BAĞLANTI & PORT AYARLARI",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Bilgi",
                    tint = electricCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Lütfen aracınızın OBD2 portuna Bluetooth cihazını (ELM327) bağlayın, telefon ayarlardan eşleştirin og ardından listeden seçip bağlanın. Test için en üstteki 'Sanal OBD-II Car' simülatörünü kullanabilirsiniz.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EŞLEŞMİŞ OBD BLUETOOTH CİHAZLARI",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
            )
            
            IconButton(
                onClick = { viewModel.refreshPairedDevices() }
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Yenile", tint = electricCyan)
            }
        }

        // List devices inline to avoid nested scrolling lazy lists
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (pairedDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bluetooth cihazı bulunamadı. Eşleşen cihazları yenileyin.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
                    )
                }
            } else {
                val sortedDevices = remember(pairedDevices, connectedDeviceAddress) {
                    if (connectedDeviceAddress != null) {
                        pairedDevices.sortedWith(compareBy { it.address != connectedDeviceAddress })
                    } else {
                        pairedDevices
                    }
                }
                val isAnyConnectionActive = connectionState is ConnectionState.Connected || connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Initializing
                sortedDevices.forEach { device ->
                    val isThisActive = isAnyConnectionActive && device.address == connectedDeviceAddress
                    val isAnyOtherConnected = isAnyConnectionActive && !isThisActive
                    DeviceItemCard(
                        device = device,
                        isActiveConnection = isThisActive,
                        isAnyOtherConnected = isAnyOtherConnected,
                        onConnect = { viewModel.connectToObd(device.address) },
                        onDisconnect = { viewModel.disconnectObd() },
                        cardBg = cardBg,
                        electricCyan = electricCyan,
                        sportAccentRed = sportAccentRed
                    )
                }
            }
        }




    }
}


@Composable
fun OldSoundCustomizerScreen() {
    // Deprecated
}


@Composable
fun TelemetryHUDItem(
    title: String,
    value: String,
    progress: Float,
    progressColor: Color,
    cardBg: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF919196),
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                )
            }
            
            // Progress Bar representing intensity level
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = progressColor,
                trackColor = Color.White.copy(alpha = 0.05f)
            )
        }
    }
}

@Composable
fun DiagnosticsScreen(
    isDtcScanning: Boolean,
    dtcScanProgress: Float,
    dtcCodes: List<String>,
    ecuProtocol: String,
    ecuVin: String,
    ecuMilStatus: Boolean,
    viewModel: ObdDashboardViewModel,
    cardBg: Color,
    electricCyan: Color,
    sportAccentRed: Color,
    amberWarning: Color
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "CANLI OBD2 ARIZA TEŞHİS MERKEZİ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
        )

        // Yellow Dashboard Glow Alert Check-Engine representation
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (ecuMilStatus) sportAccentRed.copy(alpha = 0.5f) else electricCyan.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val blinkTransition = rememberInfiniteTransition(label = "CheckEngineBlink")
                val alphaMultiplier by if (ecuMilStatus) {
                    blinkTransition.animateFloat(
                        initialValue = 1.0f,
                        targetValue = 0.4f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "MILBlinkAlpha"
                    )
                } else {
                    remember { mutableStateOf(1.0f) }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (ecuMilStatus) sportAccentRed.copy(alpha = 0.12f) else electricCyan.copy(alpha = 0.1f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Check Engine MIL Light",
                        tint = if (ecuMilStatus) amberWarning.copy(alpha = alphaMultiplier) else electricCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (ecuMilStatus) "MOTOR İKAZ LAMBASI (MIL) AKTİF" else "KONTROL SİSTEMLERİ SAĞLIKLI",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (ecuMilStatus) amberWarning else Color.White
                        )
                    )
                    Text(
                        text = if (ecuMilStatus) "Araç şanzıman veya motor sensörlerinde arıza saptandı." else "ECU hafızasında bekleyen aktif bir hata saptanmadı.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                }
            }
        }

        // ECU System Metadata Identity row
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ECU KİMLİK VERİLERİ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                )
                
                Divider(color = Color.White.copy(alpha = 0.05f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "İletişim Standardı:", style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray))
                    Text(text = ecuProtocol, style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Şasi Numarası (VIN):", style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray))
                    Text(text = ecuVin, style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Aktif Emisyon Sınıfı:", style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray))
                    Text(text = "EURO 6D-Temp / OBD-II", style = MaterialTheme.typography.bodyMedium.copy(color = electricCyan, fontWeight = FontWeight.Bold))
                }
            }
        }

        // Active Diagnostics Control console
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "DİAGNOSTİK SORGUSU VE TARAMA",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "ECU hata hafızasını okuyarak ateşleme, silindir tekleme, lambda ve turbo basınç emniyet limit aşımı kontrolü yapar.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )

                if (isDtcScanning) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = sportAccentRed,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "ECU Belleği Taranıyor: %" + (dtcScanProgress * 100).toInt(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        LinearProgressIndicator(
                            progress = { dtcScanProgress },
                            color = sportAccentRed,
                            trackColor = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.scanFaultCodes() },
                            colors = ButtonDefaults.buttonColors(containerColor = electricCyan),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Taramayı Başlat", tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SİSTEMİ TARA", fontWeight = FontWeight.Bold, color = Color.Black)
                        }

                        if (dtcCodes.isNotEmpty()) {
                            Button(
                                onClick = { viewModel.clearFaultCodes() },
                                colors = ButtonDefaults.buttonColors(containerColor = sportAccentRed),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Kodları Sıfırla", tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("BELLEĞİ SİL", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Active codes list segment
        Text(
            text = "KAYDEDİLEN AKTİF OBD HATA KODLARI",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
        )

        if (dtcCodes.isEmpty() && !isDtcScanning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Hata Yok",
                        tint = electricCyan,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Arıza Kaydı Bulunamadı",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Tüm ECU sensör gerilimleri ve emisyon normları sağlıklı limitlerde.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (dtcCodes.isNotEmpty() && !isDtcScanning) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                dtcCodes.forEach { code ->
                    DtcItemCard(
                        code = code,
                        cardBg = cardBg,
                        accentRed = sportAccentRed,
                        orangeWarning = amberWarning
                    )
                }
            }
        }
    }
}

@Composable
fun DtcItemCard(
    code: String,
    cardBg: Color,
    accentRed: Color,
    orangeWarning: Color
) {
    val (label, explanation, risk) = when (code) {
        "P0300" -> Triple(
            "P0300 - Çoklu/Rastgele Silindir Tekleme Saptandı",
            "Random/Multiple Cylinder Misfire Detected. ECU, silindirlere giden yakıtın düzensiz patladığını veya ateşleme bobinlerinde anlık voltaj çökmesi oluştuğunu rapor etmiştir.",
            "Buji aşınması, bobin çatlaması veya kalitesiz yakıt karışımı."
        )
        "P0171" -> Triple(
            "P0171 - Hava/Yakıt Karışımı Fakir (Sıra 1)",
            "System Too Lean (Bank 1). Motor emme manifolduna giren hava miktarına oranla silindirlere püskürtülen yakıt debisi yetersiz kalmaktadır (fakir karışım).",
            "Emme hattı vakum sızıntıları, MAF/MAP sensör kirliliği veya tıkalı enjektörler."
        )
        else -> Triple(
            "$code - OBD-II Standart Hata Bildirimi",
            "Genel aktarma organı kontrol ünitesi arızası kaydedilmiştir.",
            "Canlı terminal log listesindeki detaylı HEX verilerini inceleyin."
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accentRed.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = accentRed
                    )
                )
                Box(
                    modifier = Modifier
                        .background(accentRed.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "AKTİF",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = accentRed
                        )
                    )
                }
            }

            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Olası Sorun",
                    tint = orangeWarning,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Teşhis: Olası neden -> $risk",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
fun DrivingKitScreen(
    viewModel: ObdDashboardViewModel,
    rpmHistory: List<Float>,
    loadHistory: List<Float>,
    boostHistory: List<Float>,
    currentRpm: Float,
    currentLoad: Float,
    currentBoost: Int,
    maxProductRpm: Float,
    cardBg: Color,
    electricCyan: Color,
    sportAccentRed: Color
) {
    val scrollState = rememberScrollState()

    // 1. GPS Map State
    val routePoints by viewModel.gpsRoutePoints.collectAsState()
    val bearing by viewModel.gpsBearing.collectAsState()
    val speed by viewModel.speed.collectAsState() // Using calculated OBD speed
    val isRouteSim by viewModel.isRouteSimulationActive.collectAsState()

    // 2. Performance Timer State
    val timerMillis by viewModel.timerMillis.collectAsState()
    val sprint60 by viewModel.sprint60Time.collectAsState()
    val sprint100 by viewModel.sprint100Time.collectAsState()
    val statusText by viewModel.timerStatusText.collectAsState()
    val pastRuns by viewModel.pastRuns.collectAsState()

    // 4. G-Force & Inclinometer
    val gForceX by viewModel.gForceX.collectAsState()
    val gForceY by viewModel.gForceY.collectAsState()
    val pitch by viewModel.pitch.collectAsState()
    val roll by viewModel.roll.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // ============================
        // 1. 0-100 SPRINT TIMER (MOVED UP)
        // ============================
        Text(
            text = "PERFORMANS SÜRESİ (0-100) & KRONOMETRE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium.copy(color = if (sprint100 > 0f) Color.Green else sportAccentRed, fontWeight = FontWeight.Bold)
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                val seconds = timerMillis / 1000
                val fractions = (timerMillis % 1000) / 10

                Text(
                    text = String.format("%02d.%02d s", seconds, fractions),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("0-60 km/h", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray))
                        Text(
                            text = if (sprint60 > 0f) String.format("%.2f s", sprint60) else "--",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = electricCyan)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("0-100 km/h", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray))
                        Text(
                            text = if (sprint100 > 0f) String.format("%.2f s", sprint100) else "--",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = sportAccentRed)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { viewModel.resetPerformanceTimer() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Sıfırla / Tekrar Dene", color = Color.White)
                }

                if (pastRuns.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Önceki Dereceler", style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray))
                    pastRuns.reversed().take(3).forEach { runMsg ->
                        Text(
                            text = runMsg,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // ============================
        // 2. G-FORCE & INCLINOMETER (MOVED UP)
        // ============================
        Text(
            text = "G-KUVVETİ & EĞİM ÖLÇER",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // G-Force Vector Ball
            Card(
                modifier = Modifier.weight(1f).aspectRatio(1f),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp).fillMaxSize()
                ) {
                    Text("G-Kuvveti", style = MaterialTheme.typography.labelMedium.copy(color = Color.LightGray))
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        // Crosshair
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.align(Alignment.Center))
                        VerticalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.align(Alignment.Center))

                        // Max G limits
                        val maxG = 1.5f
                        val limitedX = gForceX.coerceIn(-maxG, maxG)
                        val limitedY = gForceY.coerceIn(-maxG, maxG)
                        val offX = (limitedX / maxG) * 50.dp.value
                        val offY = (-limitedY / maxG) * 50.dp.value // Y axis inverted for UI

                        Box(
                            modifier = Modifier
                                .offset(x = offX.dp, y = offY.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(electricCyan)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "X: %.2f Y: %.2f".format(gForceX, gForceY),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color.Gray)
                    )
                }
            }

            // Pitch & Roll
            Card(
                modifier = Modifier.weight(1f).aspectRatio(1f),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp).fillMaxSize()
                ) {
                    Text("Eğim (Inclinometer)", style = MaterialTheme.typography.labelMedium.copy(color = Color.LightGray))
                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .graphicsLayer(rotationZ = -roll), // Rotate chassis based on roll
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw an abstract car chassis back view
                            val w = size.width
                            val h = size.height
                            drawRoundRect(
                                color = sportAccentRed.copy(alpha = 0.5f),
                                topLeft = Offset(w*0.2f, h*0.4f),
                                size = Size(w*0.6f, h*0.3f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                            )
                            // Wheels
                            drawRoundRect(color = Color.DarkGray, topLeft = Offset(w*0.1f, h*0.45f), size = Size(w*0.15f, h*0.4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                            drawRoundRect(color = Color.DarkGray, topLeft = Offset(w*0.75f, h*0.45f), size = Size(w*0.15f, h*0.4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                            
                            // Pitch baseline
                            val pitchOffset = (pitch / 45f) * (h / 2)
                            drawLine(
                                color = electricCyan,
                                start = Offset(0f, (h/2) + pitchOffset),
                                end = Offset(w, (h/2) + pitchOffset),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Eğim: %.0f° Yatış: %.0f°".format(pitch, roll),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color.Gray)
                    )
                }
            }
        }

    }
}

@Composable
fun SevenSegmentDigit(
    digit: Char,
    color: Color,
    modifier: Modifier = Modifier,
    thickness: androidx.compose.ui.unit.Dp = 4.dp,
    spacing: androidx.compose.ui.unit.Dp = 2.dp
) {
    val active = when (digit) {
        '0' -> booleanArrayOf(true, true, true, true, true, true, false)
        '1' -> booleanArrayOf(false, true, true, false, false, false, false)
        '2' -> booleanArrayOf(true, true, false, true, true, false, true)
        '3' -> booleanArrayOf(true, true, true, true, false, false, true)
        '4' -> booleanArrayOf(false, true, true, false, false, true, true)
        '5' -> booleanArrayOf(true, false, true, true, false, true, true)
        '6' -> booleanArrayOf(true, false, true, true, true, true, true)
        '7' -> booleanArrayOf(true, true, true, false, false, false, false)
        '8' -> booleanArrayOf(true, true, true, true, true, true, true)
        '9' -> booleanArrayOf(true, true, true, true, false, true, true)
        '-' -> booleanArrayOf(false, false, false, false, false, false, true)
        else -> booleanArrayOf(false, false, false, false, false, false, false)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val t = thickness.toPx()
        val s = spacing.toPx()

        val onColor = color
        val offColor = color.copy(alpha = 0.05f)

        // A (Top Horizontal)
        drawRoundRect(
            color = if (active[0]) onColor else offColor,
            topLeft = Offset(t + s, 0f),
            size = Size(w - 2 * (t + s), t),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2, t / 2)
        )

        // B (Top Right)
        drawRoundRect(
            color = if (active[1]) onColor else offColor,
            topLeft = Offset(w - t, t + s),
            size = Size(t, h / 2 - t - 1.5f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2, t / 2)
        )

        // C (Bottom Right)
        drawRoundRect(
            color = if (active[2]) onColor else offColor,
            topLeft = Offset(w - t, h / 2 + 0.5f * s),
            size = Size(t, h / 2 - t - 1.5f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2, t / 2)
        )

        // D (Bottom Horizontal)
        drawRoundRect(
            color = if (active[3]) onColor else offColor,
            topLeft = Offset(t + s, h - t),
            size = Size(w - 2 * (t + s), t),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2, t / 2)
        )

        // E (Bottom Left)
        drawRoundRect(
            color = if (active[4]) onColor else offColor,
            topLeft = Offset(0f, h / 2 + 0.5f * s),
            size = Size(t, h / 2 - t - 1.5f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2, t / 2)
        )

        // F (Top Left)
        drawRoundRect(
            color = if (active[5]) onColor else offColor,
            topLeft = Offset(0f, t + s),
            size = Size(t, h / 2 - t - 1.5f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2, t / 2)
        )

        // G (Middle Horizontal)
        drawRoundRect(
            color = if (active[6]) onColor else offColor,
            topLeft = Offset(t + s, h / 2 - t / 2),
            size = Size(w - 2 * (t + s), t),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2, t / 2)
        )
    }
}

@Composable
fun DigitalSevenSegmentDisplay(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    digitWidth: androidx.compose.ui.unit.Dp = 26.dp,
    digitHeight: androidx.compose.ui.unit.Dp = 48.dp,
    thickness: androidx.compose.ui.unit.Dp = 3.5.dp,
    spacing: androidx.compose.ui.unit.Dp = 1.5.dp,
    gap: androidx.compose.ui.unit.Dp = 6.dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        text.forEach { char ->
            if (char == '.' || char == ',') {
                Box(
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .padding(bottom = 2.dp)
                        .size(thickness)
                        .background(color, shape = CircleShape)
                )
            } else {
                SevenSegmentDigit(
                    digit = char,
                    color = color,
                    modifier = Modifier.size(width = digitWidth, height = digitHeight),
                    thickness = thickness,
                    spacing = spacing
                )
            }
        }
    }
}

@Composable
fun GeminiProviderAndModelSelector(
    viewModel: ObdDashboardViewModel,
    sportAccentRed: Color = Color(0xFFD13438),
    electricCyan: Color = Color(0xFF00E5FF)
) {
    val geminiApiBase by viewModel.geminiApiBase.collectAsState()
    val geminiModelName by viewModel.geminiModelName.collectAsState()

    // Providers mapping (url to label)
    val providers = listOf(
        Pair("https://generativelanguage.googleapis.com", "Google Gemini API"),
        Pair("https://api.groq.com/openai/v1", "Groq API"),
        Pair("https://openrouter.ai/api/v1", "OpenRouter"),
        Pair("https://api.openai.com/v1", "OpenAI API"),
        Pair("https://api.deepseek.com/v1", "DeepSeek API"),
        Pair("custom", "Özel Sağlayıcı...")
    )

    // Current selected provider based on baseurl state
    val currentProvider = remember(geminiApiBase) {
        providers.find { it.first == geminiApiBase } ?: providers.last()
    }

    var baseDropdownExpanded by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    // Models listed per provider
    val suggestedModels = remember(currentProvider.first) {
        when (currentProvider.first) {
            "https://generativelanguage.googleapis.com" -> listOf(
                Pair("gemini-1.5-flash", "Gemini 1.5 Flash (Düşük Ücret / Ücretsiz Kotası Var)"),
                Pair("gemini-1.5-pro", "Gemini 1.5 Pro (Gelişmiş Zeka)"),
                Pair("gemini-2.0-flash", "Gemini 2.0 Flash (Hızlı / Yeni Nesil)"),
                Pair("gemini-2.0-flash-lite-preview", "Gemini 2.0 Flash Lite (Hızlı & Hesaplı)"),
                Pair("gemini-2.0-pro-exp", "Gemini 2.0 Pro Experimental"),
                Pair("custom", "Özel Model Belirt...")
            )
            "https://api.groq.com/openai/v1" -> listOf(
                Pair("llama-3.3-70b-versatile", "Llama 3.3 70B (En Yeni & Yüksek Hız)"),
                Pair("llama3-8b-8192", "Llama 3 8B (Yüksek Performans)"),
                Pair("mixtral-8x7b-32768", "Mixtral 8x7B (Akıcı)"),
                Pair("gemma2-9b-it", "Gemma 2 9B (Google)"),
                Pair("custom", "Özel Model Belirt...")
            )
            "https://openrouter.ai/api/v1" -> listOf(
                Pair("google/gemini-2.5-flash", "Gemini 2.5 Flash"),
                Pair("google/gemini-2.0-flash-exp:free", "Gemini 2.0 Flash (Free)"),
                Pair("deepseek/deepseek-chat", "DeepSeek V3 Chat"),
                Pair("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B (Free)"),
                Pair("custom", "Özel Model Belirt...")
            )
            "https://api.openai.com/v1" -> listOf(
                Pair("gpt-4o-mini", "GPT-4o Mini (Hızlı & Tasarruflu)"),
                Pair("gpt-4o", "GPT-4o (Güçlü Akıl Sürüşü)"),
                Pair("gpt-3.5-turbo", "GPT-3.5 Turbo (Eski Nesil)"),
                Pair("custom", "Özel Model Belirt...")
            )
            "https://api.deepseek.com/v1" -> listOf(
                Pair("deepseek-chat", "DeepSeek V3 Chat (Önerilen)"),
                Pair("deepseek-reasoner", "DeepSeek R1 Reasoner (Düşünce Zinciri)"),
                Pair("custom", "Özel Model Belirt...")
            )
            else -> listOf(Pair("custom", "Özel Model Belirt..."))
        }
    }

    val currentSuggestedModel = remember(geminiModelName, suggestedModels) {
        suggestedModels.find { it.first == geminiModelName } ?: suggestedModels.last()
    }

    var isCustomBaseSelected by remember { mutableStateOf(currentProvider.first == "custom") }
    var isCustomModelSelected by remember { mutableStateOf(currentSuggestedModel.first == "custom") }

    LaunchedEffect(currentProvider) {
        isCustomBaseSelected = currentProvider.first == "custom"
    }
    LaunchedEffect(currentSuggestedModel) {
        isCustomModelSelected = currentSuggestedModel.first == "custom"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // --- PROVIDER SECTION ---
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "🌐 API Sağlayıcı / Base URL:",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { baseDropdownExpanded = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentProvider.second,
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Seç",
                            tint = electricCyan
                        )
                    }
                }

                DropdownMenu(
                    expanded = baseDropdownExpanded,
                    onDismissRequest = { baseDropdownExpanded = false },
                    modifier = Modifier
                        .width(300.dp)
                        .background(Color(0xFF13151D))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                ) {
                    providers.forEach { p ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = p.second,
                                    color = if (p.first == currentProvider.first) electricCyan else Color.White,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                baseDropdownExpanded = false
                                if (p.first == "custom") {
                                    isCustomBaseSelected = true
                                    viewModel.setGeminiApiBase("")
                                } else {
                                    isCustomBaseSelected = false
                                    viewModel.setGeminiApiBase(p.first)
                                    // Match default recommended model for this provider
                                    val firstModel = when (p.first) {
                                        "https://generativelanguage.googleapis.com" -> "gemini-1.5-flash"
                                        "https://api.groq.com/openai/v1" -> "llama-3.3-70b-versatile"
                                        "https://openrouter.ai/api/v1" -> "google/gemini-2.0-flash-exp:free"
                                        "https://api.openai.com/v1" -> "gpt-4o-mini"
                                        "https://api.deepseek.com/v1" -> "deepseek-chat"
                                        else -> ""
                                    }
                                    if (firstModel.isNotEmpty()) {
                                        viewModel.setGeminiModelName(firstModel)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Custom base URL text field if Custom selected
        if (isCustomBaseSelected) {
            OutlinedTextField(
                value = geminiApiBase,
                onValueChange = { viewModel.setGeminiApiBase(it) },
                label = { Text("Özel Base URL Girin", fontSize = 10.sp, color = Color.Gray) },
                placeholder = { Text("https://kendi-api-hizmetiniz.com/v1", fontSize = 11.sp, color = Color.DarkGray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = sportAccentRed,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color.Black.copy(alpha = 0.15f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.15f)
                )
            )
        }

        // --- MODEL SECTION ---
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "🤖 Model Seçimi:",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { modelDropdownExpanded = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isCustomModelSelected) "Özel Model Belirt..." else currentSuggestedModel.first,
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Seç",
                            tint = electricCyan
                        )
                    }
                }

                DropdownMenu(
                    expanded = modelDropdownExpanded,
                    onDismissRequest = { modelDropdownExpanded = false },
                    modifier = Modifier
                        .width(300.dp)
                        .background(Color(0xFF13151D))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                ) {
                    suggestedModels.forEach { m ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = m.first,
                                        color = if (m.first == currentSuggestedModel.first) electricCyan else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (m.second.isNotEmpty() && m.second != m.first) {
                                        Text(
                                            text = m.second,
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            },
                            onClick = {
                                modelDropdownExpanded = false
                                if (m.first == "custom") {
                                    isCustomModelSelected = true
                                    viewModel.setGeminiModelName("")
                                } else {
                                    isCustomModelSelected = false
                                    viewModel.setGeminiModelName(m.first)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Custom model name text field if Custom selected
        if (isCustomModelSelected || currentProvider.first == "custom") {
            OutlinedTextField(
                value = geminiModelName,
                onValueChange = { viewModel.setGeminiModelName(it) },
                label = { Text("Özel Model İsmi Girin", fontSize = 10.sp, color = Color.Gray) },
                placeholder = { Text("Örn: deepseek-chat", fontSize = 11.sp, color = Color.DarkGray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = sportAccentRed,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color.Black.copy(alpha = 0.15f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.15f)
                )
            )
        }
    }
}
