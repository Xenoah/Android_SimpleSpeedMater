package com.example.android_simplespeedmater

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.example.android_simplespeedmater.ui.theme.Android_SimpleSpeedMaterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Android_SimpleSpeedMaterTheme(darkTheme = true) {
                SpeedometerApp()
            }
        }
    }
}

data class SpeedometerSettings(
    val backgroundColor: Color = Color.Black,
    val speedColor: Color = Color.Green,
    val unitColor: Color = Color.Gray,
    val sizeScale: Float = 1.0f,
    val yOffset: Float = 0f,
    val spacing: Float = 0f,
    val xOffsetUnit: Float = 0f,
    val updateInterval: Long = 500L,
    val showDecimal: Boolean = false,
    val decimalSizePercent: Float = 70f
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("speedometer_prefs", Context.MODE_PRIVATE)

    fun saveSettings(settings: SpeedometerSettings) {
        prefs.edit().apply {
            putInt("bg_color", settings.backgroundColor.toArgb())
            putInt("speed_color", settings.speedColor.toArgb())
            putInt("unit_color", settings.unitColor.toArgb())
            putFloat("size_scale", settings.sizeScale)
            putFloat("y_offset", settings.yOffset)
            putFloat("spacing", settings.spacing)
            putFloat("x_offset_unit", settings.xOffsetUnit)
            putLong("update_interval", settings.updateInterval)
            putBoolean("show_decimal", settings.showDecimal)
            putFloat("decimal_size_percent", settings.decimalSizePercent)
            apply()
        }
    }

    fun loadSettings(): SpeedometerSettings {
        return SpeedometerSettings(
            backgroundColor = Color(prefs.getInt("bg_color", Color.Black.toArgb())),
            speedColor = Color(prefs.getInt("speed_color", Color.Green.toArgb())),
            unitColor = Color(prefs.getInt("unit_color", Color.Gray.toArgb())),
            sizeScale = prefs.getFloat("size_scale", 1.0f),
            yOffset = prefs.getFloat("y_offset", 0f),
            spacing = prefs.getFloat("spacing", 0f),
            xOffsetUnit = prefs.getFloat("x_offset_unit", 0f),
            updateInterval = prefs.getLong("update_interval", 500L),
            showDecimal = prefs.getBoolean("show_decimal", false),
            decimalSizePercent = prefs.getFloat("decimal_size_percent", 70f)
        )
    }
}

data class SatelliteInfo(
    val count: Int = 0,
    val signals: List<Float> = emptyList(),
    val isGpsEnabled: Boolean = false,
    val hasFix: Boolean = false
)

@Composable
fun SpeedometerApp() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    var settings by remember { mutableStateOf(settingsManager.loadSettings()) }

    LaunchedEffect(settings) {
        settingsManager.saveSettings(settings)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = settings.backgroundColor
    ) {
        SpeedometerScreen(
            settings = settings,
            onSettingsChange = { settings = it }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedometerScreen(
    settings: SpeedometerSettings,
    onSettingsChange: (SpeedometerSettings) -> Unit
) {
    val context = LocalContext.current
    var speed by remember { mutableStateOf(0f) }
    var satelliteInfo by remember { mutableStateOf(SatelliteInfo()) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showSettings by remember { mutableStateOf(false) }
    var showSatellites by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "スピードメーターを使用するには位置情報の権限が必要です",
                color = if (settings.backgroundColor.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.padding(16.dp)
            )
            Button(onClick = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                Text("権限を許可する")
            }
        }
    } else {
        DisposableEffect(settings.updateInterval) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (location.provider == LocationManager.GPS_PROVIDER) {
                        speed = if (location.hasSpeed()) location.speed * 3.6f else 0f
                        satelliteInfo = satelliteInfo.copy(hasFix = true)
                    } else {
                        satelliteInfo = satelliteInfo.copy(hasFix = true)
                    }
                    satelliteInfo = satelliteInfo.copy(isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                }
                override fun onProviderEnabled(provider: String) {
                    if (provider == LocationManager.GPS_PROVIDER) satelliteInfo = satelliteInfo.copy(isGpsEnabled = true)
                }
                override fun onProviderDisabled(provider: String) {
                    if (provider == LocationManager.GPS_PROVIDER) satelliteInfo = satelliteInfo.copy(isGpsEnabled = false, hasFix = false)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }

            val gnssStatusCallback = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    val count = status.satelliteCount
                    val signals = mutableListOf<Float>()
                    var usedCount = 0
                    for (i in 0 until count) {
                        signals.add(status.getCn0DbHz(i))
                        if (status.usedInFix(i)) usedCount++
                    }
                    satelliteInfo = satelliteInfo.copy(
                        count = usedCount,
                        signals = signals.sortedDescending().take(12),
                        isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    )
                }
            }

            try {
                val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val bestLast = if (lastGps != null && (lastNet == null || lastGps.time > lastNet.time)) lastGps else lastNet
                
                bestLast?.let {
                    if (System.currentTimeMillis() - it.time < 60000) {
                        speed = if (it.hasSpeed()) it.speed * 3.6f else 0f
                        satelliteInfo = satelliteInfo.copy(hasFix = true)
                    }
                }

                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, settings.updateInterval, 0f, locationListener)
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, settings.updateInterval * 2, 0f, locationListener)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    locationManager.registerGnssStatusCallback(context.mainExecutor, gnssStatusCallback)
                } else {
                    locationManager.registerGnssStatusCallback(gnssStatusCallback, Handler(Looper.getMainLooper()))
                }
                
                satelliteInfo = satelliteInfo.copy(isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            } catch (e: SecurityException) {}

            onDispose {
                locationManager.removeUpdates(locationListener)
                locationManager.unregisterGnssStatusCallback(gnssStatusCallback)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = settings.yOffset.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(
                        modifier = Modifier.zIndex(1f),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val speedInt = speed.toInt()
                        val speedDec = ((speed - speedInt) * 10).toInt()
                        
                        Text(
                            text = speedInt.toString(),
                            fontSize = (160 * settings.sizeScale).sp,
                            fontWeight = FontWeight.Black,
                            color = settings.speedColor
                        )
                        
                        if (settings.showDecimal) {
                            Text(
                                text = ".%d".format(speedDec),
                                fontSize = (160 * settings.sizeScale * (settings.decimalSizePercent / 100f)).sp,
                                fontWeight = FontWeight.Black,
                                color = settings.speedColor,
                                modifier = Modifier.padding(bottom = (20 * settings.sizeScale).dp)
                            )
                        }
                    }
                    
                    Text(
                        text = "km/h",
                        fontSize = (32 * settings.sizeScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = settings.unitColor,
                        modifier = Modifier
                            .zIndex(2f)
                            .offset(
                                x = settings.xOffsetUnit.dp,
                                y = (70 * settings.sizeScale + settings.spacing).dp
                            )
                    )
                }
            }

            val statusColor = when {
                !satelliteInfo.isGpsEnabled -> Color.Gray
                !satelliteInfo.hasFix -> Color.Red
                satelliteInfo.count < 4 -> Color.Yellow
                else -> Color.Green
            }
            
            Surface(
                onClick = { showSatellites = true },
                modifier = Modifier
                    .padding(16.dp)
                    .size(40.dp)
                    .align(Alignment.TopStart),
                shape = CircleShape,
                color = Color.DarkGray.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(12.dp).background(statusColor, CircleShape))
                }
            }

            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Settings, contentDescription = "設定", tint = Color.White)
            }

            if (showSettings) {
                ModalBottomSheet(
                    onDismissRequest = { showSettings = false },
                    containerColor = Color(0xCC1C1C1C),
                    contentColor = Color.White,
                    scrimColor = Color.Transparent
                ) {
                    SettingsContent(settings = settings, onSettingsChange = onSettingsChange)
                }
            }

            if (showSatellites) {
                SatelliteDialog(info = satelliteInfo, onDismiss = { showSatellites = false })
            }
        }
    }
}

@Composable
fun SatelliteDialog(info: SatelliteInfo, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xEE252525)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Satellite Info", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("GPS Status: ${if (info.isGpsEnabled) "ON" else "OFF"}", color = Color.LightGray)
                Text("Used Satellites: ${info.count}", color = Color.LightGray)
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Signal Strengths (dBHz):", fontSize = 14.sp, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.height(100.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    info.signals.forEach { signal ->
                        val heightFactor = (signal / 50f).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(heightFactor)
                                .background(if (signal > 30) Color.Green else Color.Yellow, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        )
                    }
                    if (info.signals.isEmpty()) {
                        Text("No signal data", color = Color.Gray, modifier = Modifier.fillMaxWidth(), fontSize = 12.sp)
                    }
                }
                
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(top = 16.dp)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun SettingsContent(
    settings: SpeedometerSettings,
    onSettingsChange: (SpeedometerSettings) -> Unit
) {
    var editingColorType by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 48.dp)
    ) {
        Text("カスタマイズ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        ColorRow("背景色", settings.backgroundColor) { editingColorType = "bg" }
        ColorRow("速度数値の色", settings.speedColor) { editingColorType = "speed" }
        ColorRow("単位(km/h)の色", settings.unitColor) { editingColorType = "unit" }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.DarkGray.copy(alpha = 0.5f))

        Text("更新頻度: ${"%.1f".format(settings.updateInterval / 1000f)} 秒", color = Color.LightGray)
        Slider(
            value = settings.updateInterval.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(updateInterval = it.toLong())) },
            valueRange = 100f..3000f,
            steps = 28
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = settings.showDecimal,
                onCheckedChange = { onSettingsChange(settings.copy(showDecimal = it)) }
            )
            Text("小数点を表示する", color = Color.LightGray)
        }
        
        if (settings.showDecimal) {
            Text("小数点サイズ: ${settings.decimalSizePercent.toInt()}%", color = Color.LightGray)
            Slider(
                value = settings.decimalSizePercent,
                onValueChange = { onSettingsChange(settings.copy(decimalSizePercent = it)) },
                valueRange = 30f..100f
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.DarkGray.copy(alpha = 0.5f))

        Text("数値と単位の間隔 (上下): ${settings.spacing.toInt()}", color = Color.LightGray)
        Slider(
            value = settings.spacing, 
            onValueChange = { onSettingsChange(settings.copy(spacing = it)) }, 
            valueRange = -300f..200f
        )

        Text("数値と単位の位置 (左右): ${settings.xOffsetUnit.toInt()}", color = Color.LightGray)
        Slider(
            value = settings.xOffsetUnit, 
            onValueChange = { onSettingsChange(settings.copy(xOffsetUnit = it)) }, 
            valueRange = -300f..300f
        )

        Text("全体サイズ: ${"%.1f".format(settings.sizeScale)}", color = Color.LightGray)
        Slider(value = settings.sizeScale, onValueChange = { onSettingsChange(settings.copy(sizeScale = it)) }, valueRange = 0.5f..2.5f)

        Text("全体の上下位置: ${settings.yOffset.toInt()}", color = Color.LightGray)
        Slider(value = settings.yOffset, onValueChange = { onSettingsChange(settings.copy(yOffset = it)) }, valueRange = -400f..400f)
    }

    editingColorType?.let { type ->
        val initialColor = when(type) {
            "bg" -> settings.backgroundColor
            "speed" -> settings.speedColor
            else -> settings.unitColor
        }
        AdvancedColorPickerDialog(
            initialColor = initialColor,
            onDismiss = { editingColorType = null },
            onColorSelected = { newColor ->
                onSettingsChange(when(type) {
                    "bg" -> settings.copy(backgroundColor = newColor)
                    "speed" -> settings.copy(speedColor = newColor)
                    else -> settings.copy(unitColor = newColor)
                })
            }
        )
    }
}

@Composable
fun ColorRow(label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(32.dp).background(color, RoundedCornerShape(4.dp)).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, fontSize = 16.sp)
        Spacer(modifier = Modifier.weight(1f))
        Text("変更 >", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun AdvancedColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var hsv by remember {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), arr)
        mutableStateOf(Triple(arr[0], arr[1], arr[2]))
    }

    val currentColor = remember(hsv) { Color.hsv(hsv.first, hsv.second, hsv.third) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xEE252525)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Edit colors", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.height(200.dp)) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                        .pointerInput(hsv.first) {
                            detectDragGestures { change, _ ->
                                val s = (change.position.x / size.width).coerceIn(0f, 1f)
                                val v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                hsv = Triple(hsv.first, s, v)
                                onColorSelected(Color.hsv(hsv.first, s, v))
                            }
                        }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hsv.first, 1f, 1f))))
                            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                            
                            val x = hsv.second * size.width
                            val y = (1f - hsv.third) * size.height
                            drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.width(40.dp).fillMaxHeight().background(currentColor, RoundedCornerShape(4.dp)).border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(4.dp)))
                    Spacer(modifier = Modifier.width(12.dp))

                    Box(modifier = Modifier.width(20.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                val h = (change.position.y / size.height).coerceIn(0f, 360f)
                                hsv = Triple(h, hsv.second, hsv.third)
                                onColorSelected(Color.hsv(h, hsv.second, hsv.third))
                            }
                        }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val hues = (0..360).map { Color.hsv(it.toFloat(), 1f, 1f) }
                            drawRect(Brush.verticalGradient(hues))
                            val y = (hsv.first / 360f) * size.height
                            drawRect(Color.White, topLeft = Offset(0f, y - 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 4.dp.toPx()))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hexText = "#%06X".format(0xFFFFFF and currentColor.toArgb())
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.weight(1f),
                        label = { Text("Hex", fontSize = 10.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        RgbInput("Red", (currentColor.red * 255).toInt()) { r -> onColorSelected(currentColor.copy(red = r / 255f)) }
                        RgbInput("Green", (currentColor.green * 255).toInt()) { g -> onColorSelected(currentColor.copy(green = g / 255f)) }
                        RgbInput("Blue", (currentColor.blue * 255).toInt()) { b -> onColorSelected(currentColor.copy(blue = b / 255f)) }
                    }
                }

                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(top = 16.dp)) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun RgbInput(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        TextField(
            value = value.toString(),
            onValueChange = { val newVal = it.toIntOrNull()?.coerceIn(0, 255); if (newVal != null) onValueChange(newVal) },
            modifier = Modifier.width(60.dp).height(48.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
