package ai.hex.pindrive

import ai.hex.pindrive.core.CarePinSnapshot
import ai.hex.pindrive.core.GearState
import ai.hex.pindrive.core.LifePinBookmark
import ai.hex.pindrive.core.PinDriveEngine
import ai.hex.pindrive.core.PinType
import ai.hex.pindrive.core.PowertrainType
import ai.hex.pindrive.core.TelemetrySample
import ai.hex.pindrive.sdk.PleosSdkAdapter
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Hyundai Motor Group CI & Pleos Connect Restraint Palette
private val HyundaiBlue = Color(0xFF002C5F)
private val ActiveBlue = Color(0xFF00AAD2)
private val ActiveRed = Color(0xFFE63312)
private val HyundaiSandBg = Color(0xFFF6F3F2)
private val HyundaiSandBorder = Color(0xFFE4DCD3)
private val PleosCanvas = Color(0xFFECEFF3)
private val PleosDrivingBg = Color(0xFFF5F7F9)
private val PleosBorder = Color(0xFFD8DEE6)
private val InkPrimary = Color(0xFF141619)
private val InkSecondary = Color(0xFF4E5661)
private val InkMuted = Color(0xFF7A828E)

class MainActivity : ComponentActivity() {

    private lateinit var pleosAdapter: PleosSdkAdapter
    private val engine = PinDriveEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pleosAdapter = PleosSdkAdapter(this).also { it.initializeHybridTts() }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    background = PleosCanvas,
                    surface = Color.White,
                    primary = HyundaiBlue,
                    secondary = ActiveBlue
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = PleosCanvas) {
                    PinDriveCockpitScreen(
                        engine = engine,
                        adapter = pleosAdapter
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        pleosAdapter.release()
        super.onDestroy()
    }
}

private fun seedScenarioPins(
    engine: PinDriveEngine,
    adapter: PleosSdkAdapter
): Pair<List<CarePinSnapshot>, List<LifePinBookmark>> {
    val cares = mutableListOf<CarePinSnapshot>()
    val lifes = mutableListOf<LifePinBookmark>()

    // 1. CARE-01: Cold Morning Speed Bump Suspension Noise (-2.0C, Brake ON)
    val t1 = 1_763_160_300_000L
    listOf(38.0, 38.0, 35.0, 29.0, 24.0, 24.0, 26.0, 30.0).forEachIndexed { idx, spd ->
        engine.pushSample(
            TelemetrySample(
                timestampMs = t1 + idx * 1000L,
                speedKmh = spd,
                odometerKm = 84320.0,
                tripDistanceKm = 0.9,
                tripElapsedSec = 180 + idx,
                brakePedalPressed = idx in 2..4,
                drivingMode = "COMFORT",
                exteriorTempC = -2.0,
                hvacActive = true,
                fuelOrSocPercent = 62,
                instantEconomyKmPerL = 14.8,
                activeTelltale = null,
                latitude = 37.4020,
                longitude = 127.1085,
                headingDeg = 90.0
            )
        )
    }
    cares += engine.captureCarePin(
        id = "CARE-01",
        timeLabel = "07:45:03",
        utterance = "방지턱 넘을 때 오른쪽 앞에서 찌그덕 소리 났어",
        powertrain = PowertrainType.ICE_DIESEL
    )

    // 2. CARE-02: Cold 2->3 Gear DCT Shift Shudder
    val t2 = t1 + 360_000L
    listOf(22.0, 26.0, 31.0, 28.0, 33.0, 36.0, 38.0).forEachIndexed { idx, spd ->
        engine.pushSample(
            TelemetrySample(
                timestampMs = t2 + idx * 1000L,
                speedKmh = spd,
                odometerKm = 84323.4,
                tripDistanceKm = 3.4,
                tripElapsedSec = 540 + idx,
                brakePedalPressed = false,
                drivingMode = "COMFORT",
                exteriorTempC = -1.5,
                hvacActive = true,
                fuelOrSocPercent = 61,
                instantEconomyKmPerL = 11.2,
                activeTelltale = null,
                latitude = 37.4062,
                longitude = 127.1120,
                headingDeg = 90.0
            )
        )
    }
    cares += engine.captureCarePin(
        id = "CARE-02",
        timeLabel = "07:51:18",
        utterance = "2단에서 3단 넘어갈 때 변속기 울컥거렸어",
        powertrain = PowertrainType.ICE_DIESEL
    )

    // 3. CARE-03: Transient DPF/Exhaust Warning Light (2.0s ON -> Extinguished)
    val t3 = t2 + 780_000L
    listOf(62.0, 63.0, 64.0, 64.0, 63.0, 62.0, 62.0).forEachIndexed { idx, spd ->
        engine.pushSample(
            TelemetrySample(
                timestampMs = t3 + idx * 1000L,
                speedKmh = spd,
                odometerKm = 84331.6,
                tripDistanceKm = 11.6,
                tripElapsedSec = 1320 + idx,
                brakePedalPressed = false,
                drivingMode = "ECO",
                exteriorTempC = -1.0,
                hvacActive = true,
                fuelOrSocPercent = 60,
                instantEconomyKmPerL = 17.6,
                activeTelltale = if (idx in 2..3) "DPF 배기 경고등 2.0초 점등 포착" else null,
                latitude = 37.4180,
                longitude = 127.1250,
                headingDeg = 90.0
            )
        )
    }
    cares += engine.captureCarePin(
        id = "CARE-03",
        timeLabel = "08:04:42",
        utterance = "계기판에 배기 경고등 잠깐 깜빡였다 꺼졌어",
        powertrain = PowertrainType.ICE_DIESEL
    )

    // 4. LIFE-01 & LIFE-02
    val t4 = t3 + 10_000_000L
    engine.pushSample(
        TelemetrySample(
            timestampMs = t4,
            speedKmh = 72.0,
            odometerKm = 84355.4,
            tripDistanceKm = 36.3,
            tripElapsedSec = 2400,
            brakePedalPressed = false,
            drivingMode = "ECO",
            exteriorTempC = 6.5,
            hvacActive = true,
            fuelOrSocPercent = 58,
            instantEconomyKmPerL = 19.4,
            activeTelltale = null,
            latitude = 37.5210,
            longitude = 127.3120,
            headingDeg = 90.0
        )
    )
    lifes += engine.captureLifePin(
        id = "LIFE-01",
        timeLabel = "14:10:04",
        utterance = "방금 오른쪽 강변 카페 핀 찍어줘",
        matchedPoiName = "리버사이드 로스터리 베이커리"
    )

    val t5 = t4 + 1_200_000L
    engine.pushSample(
        TelemetrySample(
            timestampMs = t5,
            speedKmh = 54.0,
            odometerKm = 84369.0,
            tripDistanceKm = 49.9,
            tripElapsedSec = 3500,
            brakePedalPressed = false,
            drivingMode = "COMFORT",
            exteriorTempC = 6.0,
            hvacActive = true,
            fuelOrSocPercent = 56,
            instantEconomyKmPerL = 16.2,
            activeTelltale = null,
            latitude = 37.3948,
            longitude = 127.1014,
            headingDeg = 90.0
        )
    )
    lifes += engine.captureLifePin(
        id = "LIFE-02",
        timeLabel = "14:28:39",
        utterance = "방금 오른쪽 경유 특가 주유소 핀 찍어줘",
        matchedPoiName = "판교 IC 알뜰 셀프주유소"
    )

    // Load any previously persisted custom utterances
    adapter.loadPersistedUtterances().forEach { (savedTime, utter) ->
        if (engine.classifyUtterance(utter) == PinType.CARE_PIN) {
            val id = "CARE-${(cares.size + 1).toString().padStart(2, '0')}"
            cares.add(0, engine.captureCarePin(id, savedTime, utter))
        } else {
            val id = "LIFE-${(lifes.size + 1).toString().padStart(2, '0')}"
            val poi = utter.replace("핀 찍어줘", "").replace("방금", "").trim().ifEmpty { "도로변 북마크" }
            lifes.add(0, engine.captureLifePin(id, savedTime, utter, poi))
        }
    }

    return cares to lifes
}

@Composable
fun PinDriveCockpitScreen(
    engine: PinDriveEngine,
    adapter: PleosSdkAdapter
) {
    val context = LocalContext.current
    var gear by remember { mutableStateOf(GearState.D) }
    var selectedParkedTab by remember { mutableIntStateOf(0) }
    var selectedCareIndex by remember { mutableIntStateOf(0) }
    var selectedLifeIndex by remember { mutableIntStateOf(0) }
    var showMechanicSheet by remember { mutableStateOf(false) }

    var isListeningMic by remember { mutableStateOf(false) }
    var customInputText by remember { mutableStateOf("") }
    var naviSentBanner by remember { mutableStateOf<String?>(null) }

    // Live 10Hz Vehicle Telemetry State
    var liveSpeed by remember { mutableDoubleStateOf(26.0) }
    var targetSpeed by remember { mutableDoubleStateOf(30.0) }
    var tripDistanceKm by remember { mutableDoubleStateOf(0.92) }
    var tripElapsedSec by remember { mutableIntStateOf(185) }
    var liveBrake by remember { mutableStateOf(false) }
    var activeWarningTelltale by remember { mutableStateOf<String?>(null) }
    var drivingMode by remember { mutableStateOf("COMFORT") }
    var routeProgress by remember { mutableFloatStateOf(0.45f) }
    val liveWaveform = remember {
        mutableStateListOf(38f, 36f, 32f, 27f, 24f, 24f, 25f, 27f, 28f, 29f, 28f, 27f, 26f, 27f, 28f, 29f)
    }

    var lastUtterance by remember {
        mutableStateOf("“방금 하부 소음 핀 찍어줘 — 방지턱 넘을 때 오른쪽 앞에서 찌그덕 소리 났어”")
    }
    var statusMessage by remember {
        mutableStateOf("하부 소음 케어 핀이 직전 10초 차량 상태와 함께 저장되었습니다.")
    }

    val seeded = remember { seedScenarioPins(engine, adapter) }
    val carePins = remember { mutableStateListOf<CarePinSnapshot>().apply { addAll(seeded.first) } }
    val lifePins = remember { mutableStateListOf<LifePinBookmark>().apply { addAll(seeded.second) } }

    // Live 4Hz / 10Hz Driving Telemetry Loop
    LaunchedEffect(gear) {
        var tick = 0
        while (gear == GearState.D) {
            delay(250L)
            tick++
            val diff = targetSpeed - liveSpeed
            liveSpeed = (liveSpeed + diff.coerceIn(-1.2, 1.2) + sin(tick * 0.4) * 0.25).coerceIn(0.0, 110.0)
            tripDistanceKm += (liveSpeed / 3600.0) * 0.25
            if (tick % 4 == 0) tripElapsedSec += 1

            liveWaveform.add(liveSpeed.toFloat())
            if (liveWaveform.size > 24) liveWaveform.removeAt(0)

            routeProgress += 0.0045f * (liveSpeed.toFloat() / 30f)
            if (routeProgress > 0.92f) routeProgress = 0.14f

            engine.pushSample(
                TelemetrySample(
                    timestampMs = System.currentTimeMillis(),
                    speedKmh = liveSpeed,
                    odometerKm = 84320.0 + tripDistanceKm,
                    tripDistanceKm = (tripDistanceKm * 10.0).roundToInt() / 10.0,
                    tripElapsedSec = tripElapsedSec,
                    brakePedalPressed = liveBrake,
                    drivingMode = drivingMode,
                    exteriorTempC = -2.0,
                    hvacActive = true,
                    fuelOrSocPercent = 62,
                    instantEconomyKmPerL = if (liveBrake) 19.8 else 14.8,
                    activeTelltale = activeWarningTelltale,
                    latitude = 37.4020 + routeProgress * 0.01,
                    longitude = 127.1085 + routeProgress * 0.01,
                    headingDeg = 90.0
                )
            )
        }
    }

    // Auto-extinguish transient DPF warning after 2.2 seconds (proving ring-buffer value!)
    LaunchedEffect(activeWarningTelltale) {
        if (activeWarningTelltale != null) {
            delay(2200L)
            activeWarningTelltale = null
        }
    }

    fun recordLiveUtterance(utterance: String, persist: Boolean = true) {
        val clean = utterance.trim()
        if (clean.isEmpty()) return
        val nowLabel = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        lastUtterance = "“$clean”"

        if (engine.classifyUtterance(clean) == PinType.CARE_PIN) {
            val newId = "CARE-${(carePins.size + 1).toString().padStart(2, '0')}"
            val snap = engine.captureCarePin(
                id = newId,
                timeLabel = nowLabel,
                utterance = clean,
                powertrain = PowertrainType.ICE_DIESEL
            )
            carePins.add(0, snap)
            selectedCareIndex = 0
            statusMessage = "[$newId] 직전 10초 차량 센서 맥락과 음성 메모가 케어 핀으로 저장되었습니다."
            adapter.speakHybrid(statusMessage, R.raw.tts_custom_care)
        } else {
            val newId = "LIFE-${(lifePins.size + 1).toString().padStart(2, '0')}"
            val poi = clean.replace("핀 찍어줘", "").replace("방금", "").replace("오른쪽", "").replace("왼쪽", "").trim()
                .ifEmpty { "도로변 사용자 북마크" }
            val life = engine.captureLifePin(
                id = newId,
                timeLabel = nowLabel,
                utterance = clean,
                matchedPoiName = poi
            )
            lifePins.add(0, life)
            selectedLifeIndex = 0
            statusMessage = "[$newId] ${liveSpeed.roundToInt()}km/h 속도 비례 -${life.correctedOffsetMeters}m 이전 좌표로 북마크했습니다."
            adapter.speakHybrid(statusMessage, R.raw.tts_custom_life)
        }
        if (persist) {
            adapter.appendPersistedUtterance(nowLabel, clean)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            adapter.startLiveSpeechRecognition(
                onListeningChanged = { isListeningMic = it },
                onPartialTranscript = { lastUtterance = "“$it...”" },
                onFinalTranscript = { recordLiveUtterance(it, persist = true) },
                onErrorHint = { statusMessage = it }
            )
        } else {
            statusMessage = "마이크 권한이 거부되어 하단 텍스트 입력 또는 시나리오 버튼을 이용해 주세요."
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Pleos Connect System Status Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(Color.White)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(4.dp).height(15.dp).background(HyundaiBlue))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pleos Connect / PinDrive v2.0", color = InkPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Spacer(modifier = Modifier.width(14.dp))
                GearToggleStrip(
                    gear = gear,
                    onToggle = {
                        gear = if (gear == GearState.D) GearState.P else GearState.D
                        if (gear == GearState.P) {
                            adapter.speakHybrid("정차가 감지되어 주행 핀 리포트를 활성화합니다.", R.raw.tts_parked_report)
                        }
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .border(1.dp, PleosBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isListeningMic) ActiveRed else ActiveBlue)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gleo TTS · haeun", color = HyundaiBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Avante AD 1.6 Diesel (${84320 + tripDistanceKm.toInt()} km) | 외기 -2.0°C",
                    color = InkSecondary,
                    fontSize = 11.5.sp
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PleosBorder))

        // 2. Main Split Workspace
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Live Driving Cluster
            Column(
                modifier = Modifier
                    .width(262.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(PleosDrivingBg)
                    .border(1.dp, PleosBorder, RoundedCornerShape(6.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = if (gear == GearState.D) "${liveSpeed.roundToInt()}" else "0",
                            color = InkPrimary,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (gear == GearState.D) "km/h · LIVE" else "PARKED",
                                color = InkSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (gear == GearState.D) drivingMode else "P단 통합 리포트",
                                color = HyundaiBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val isCold = tripDistanceKm < 2.0
                    SpecTile(
                        "주행 경과 · Odometer",
                        "시동 ${max(1, tripElapsedSec / 60)}분 · ${"%.2f".format(tripDistanceKm)}km (${if (isCold) "냉간" else "열간"})"
                    )
                    SpecTile(
                        "제동 페달 · Brake",
                        if (liveBrake) "감속 페달 작동 중 (ON)" else "직전 감속 이력 모니터링",
                        highlightAlert = liveBrake
                    )
                    SpecTile(
                        "경고등 감시 · Safety",
                        activeWarningTelltale ?: "현재 정상 (버퍼 포착 ${carePins.count { it.telltaleSummary.contains("점등") }}건)",
                        highlightAlert = activeWarningTelltale != null
                    )

                    // Live 15s Ring Buffer Waveform Strip
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .border(1.dp, PleosBorder, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text("15s Ring Buffer (Live 10Hz)", color = HyundaiBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Canvas(modifier = Modifier.fillMaxWidth().height(34.dp)) {
                            if (liveWaveform.size > 1) {
                                val p = Path()
                                liveWaveform.forEachIndexed { i, v ->
                                    val x = (i.toFloat() / (liveWaveform.size - 1)) * size.width
                                    val y = size.height - (v / 95f).coerceIn(0.08f, 0.92f) * size.height
                                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                                }
                                drawPath(p, color = HyundaiBlue, style = Stroke(width = 3.5f))
                            }
                        }
                    }
                }

                // Quick Gear Switch Button
                Button(
                    onClick = {
                        gear = if (gear == GearState.D) GearState.P else GearState.D
                        if (gear == GearState.P) {
                            adapter.speakHybrid("정차가 감지되어 주행 핀 리포트를 활성화합니다.", R.raw.tts_parked_report)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = InkPrimary)
                ) {
                    Text(
                        if (gear == GearState.D) "P단 정차 전환 (통합 리포트 열람)" else "D단 실시간 주행 복귀",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Right Application Window
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .border(1.dp, PleosBorder, RoundedCornerShape(6.dp))
                    .padding(14.dp)
            ) {
                if (gear == GearState.D) {
                    DrivingLiveStage(
                        utterance = lastUtterance,
                        ttsReply = statusMessage,
                        isListeningMic = isListeningMic,
                        routeProgress = routeProgress,
                        careCount = carePins.size,
                        lifeCount = lifePins.size,
                        customInput = customInputText,
                        onCustomInputChange = { customInputText = it },
                        onSubmitCustom = {
                            val txt = customInputText.trim()
                            if (txt.isNotEmpty()) {
                                customInputText = ""
                                recordLiveUtterance(txt, persist = true)
                            }
                        },
                        onToggleMic = {
                            if (isListeningMic) {
                                adapter.stopSpeechRecognition()
                                isListeningMic = false
                            } else {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    adapter.startLiveSpeechRecognition(
                                        onListeningChanged = { isListeningMic = it },
                                        onPartialTranscript = { lastUtterance = "“$it...”" },
                                        onFinalTranscript = { recordLiveUtterance(it, persist = true) },
                                        onErrorHint = { statusMessage = it }
                                    )
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        onTriggerScenario = { scenario ->
                            when (scenario) {
                                "bump" -> {
                                    liveBrake = true
                                    liveSpeed = 35.0
                                    targetSpeed = 24.0
                                    drivingMode = "COMFORT"
                                    lastUtterance = "“방금 하부 소음 핀 찍어줘 — 방지턱 우측 전륜 찌그덕 소리”"
                                    statusMessage = "하부 소음 케어 핀이 직전 10초 감속·냉간 상태와 함께 저장되었습니다."
                                    selectedCareIndex = carePins.indexOfFirst { it.id == "CARE-01" }.coerceAtLeast(0)
                                    adapter.speakHybrid(statusMessage, R.raw.tts_care_saved)
                                    liveBrake = false
                                }
                                "shift" -> {
                                    liveBrake = false
                                    liveSpeed = 31.0
                                    targetSpeed = 37.0
                                    drivingMode = "COMFORT"
                                    lastUtterance = "“글레오, 방금 2단에서 3단 넘어갈 때 변속기 울컥거렸어”"
                                    statusMessage = "구동 변속 충격 케어 핀이 가속 맥락과 함께 저장되었습니다."
                                    selectedCareIndex = carePins.indexOfFirst { it.id == "CARE-02" }.coerceAtLeast(0)
                                    adapter.speakHybrid(statusMessage, R.raw.tts_shift_saved)
                                }
                                "dpf" -> {
                                    liveBrake = false
                                    liveSpeed = 63.0
                                    targetSpeed = 64.0
                                    drivingMode = "ECO"
                                    activeWarningTelltale = "DPF 배기 경고등 (2초 점등)"
                                    lastUtterance = "“방금 계기판에 배기 경고등 잠깐 깜빡였다 꺼졌어. 핀 찍어줘”"
                                    statusMessage = "간헐 점등된 배기 경고등 이력이 15초 링버퍼에 영구 고정되었습니다."
                                    selectedCareIndex = carePins.indexOfFirst { it.id == "CARE-03" }.coerceAtLeast(0)
                                    adapter.speakHybrid(statusMessage, R.raw.tts_dpf_saved)
                                }
                                "cafe" -> {
                                    liveBrake = false
                                    liveSpeed = 72.0
                                    targetSpeed = 72.0
                                    drivingMode = "ECO"
                                    lastUtterance = "“핀드라이브, 방금 오른쪽 강변 카페 핀 찍어줘”"
                                    statusMessage = "3.5초 전 주행 좌표를 70미터 역산하여 강변 카페 북마크를 저장했습니다."
                                    selectedLifeIndex = 0
                                    adapter.speakHybrid(statusMessage, R.raw.tts_life_saved)
                                }
                            }
                        },
                        onOpenParkedReport = {
                            gear = GearState.P
                            adapter.speakHybrid("정차가 감지되어 주행 핀 리포트를 활성화합니다.", R.raw.tts_parked_report)
                        }
                    )
                } else {
                    ParkedMasterDetailStage(
                        selectedTab = selectedParkedTab,
                        onSelectTab = { selectedParkedTab = it },
                        carePins = carePins,
                        selectedCareIdx = selectedCareIndex.coerceIn(0, max(0, carePins.size - 1)),
                        onSelectCareIdx = { selectedCareIndex = it; naviSentBanner = null },
                        lifePins = lifePins,
                        selectedLifeIdx = selectedLifeIndex.coerceIn(0, max(0, lifePins.size - 1)),
                        onSelectLifeIdx = { selectedLifeIndex = it; naviSentBanner = null },
                        naviBanner = naviSentBanner,
                        onOpenMechanicModal = { showMechanicSheet = true },
                        onResetCustom = {
                            adapter.clearPersistedUtterances()
                            val fresh = seedScenarioPins(engine, adapter)
                            carePins.clear(); carePins.addAll(fresh.first)
                            lifePins.clear(); lifePins.addAll(fresh.second)
                            selectedCareIndex = 0; selectedLifeIndex = 0
                        },
                        onDispatchNavi = { lat, lng, title, isCare ->
                            adapter.dispatchDestinationToNaviHelper(lat, lng, title)
                            naviSentBanner = "NaviHelper 경로 전송 완료 — $title"
                            adapter.speakHybrid(
                                "$title 목적지를 내비게이션으로 전송했습니다.",
                                if (isCare) R.raw.tts_navi_care else R.raw.tts_navi_life
                            )
                        }
                    )
                }
            }
        }

        // 3. Pleos Connect Bottom GNB Dock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(InkPrimary)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("22.0°C  AUTO · DRIVER", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Text("Pleos Connect · PinDrive v2.0 Live Engine (HEX)", color = Color(0xFF9CA3AF), fontSize = 11.sp)
            Text("22.5°C  PASSENGER", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
    }

    // Bluehands Engineer Mobile Scan Sheet Dialog
    if (showMechanicSheet && carePins.isNotEmpty()) {
        val activePin = carePins[selectedCareIndex.coerceIn(0, carePins.size - 1)]
        MechanicQrScanDialog(
            pin = activePin,
            onDismiss = { showMechanicSheet = false }
        )
    }
}

@Composable
private fun DrivingLiveStage(
    utterance: String,
    ttsReply: String,
    isListeningMic: Boolean,
    routeProgress: Float,
    careCount: Int,
    lifeCount: Int,
    customInput: String,
    onCustomInputChange: (String) -> Unit,
    onSubmitCustom: () -> Unit,
    onToggleMic: () -> Unit,
    onTriggerScenario: (String) -> Unit,
    onOpenParkedReport: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Voice Banner + Live STT Mic Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (isListeningMic) ActiveRed else PleosBorder, RoundedCornerShape(4.dp))
                .background(Color.White)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(46.dp)
                    .background(if (isListeningMic) ActiveRed else HyundaiBlue)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isListeningMic) "LIVE MICROPHONE LISTENING (ko-KR)" else "STT / GLEO VOICE CAPTURE · 주행 중 화면 전환 잠금",
                    color = if (isListeningMic) ActiveRed else HyundaiBlue,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    utterance,
                    color = InkPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "TTS: $ttsReply",
                    color = InkSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onToggleMic,
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListeningMic) ActiveRed else HyundaiBlue
                )
            ) {
                Text(
                    if (isListeningMic) "듣는 중..." else "실제 마이크 발화",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Animated Daylight Navigation Canvas with Moving Vehicle Puck
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE9EEF2))
                .border(1.dp, PleosBorder, RoundedCornerShape(4.dp))
        ) {
            // Secondary roads
            drawLine(Color.White, Offset(size.width * 0.32f, 0f), Offset(size.width * 0.38f, size.height), strokeWidth = 18f)
            drawLine(Color.White, Offset(0f, size.height * 0.44f), Offset(size.width, size.height * 0.48f), strokeWidth = 18f)

            // Riverbank
            drawRect(
                color = Color(0xFFD5E3EC),
                topLeft = Offset(0f, size.height * 0.80f),
                size = Size(size.width, size.height * 0.20f)
            )

            // Main Hyundai Blue Route
            val start = Offset(size.width * 0.14f, size.height * 0.92f)
            val end = Offset(size.width * 0.88f, size.height * 0.10f)
            drawLine(HyundaiBlue, start, end, strokeWidth = 14f)
            drawLine(ActiveBlue, start, end, strokeWidth = 4.5f)

            // Captured Pin Markers along Route
            val bumpPt = Offset(
                start.x + (end.x - start.x) * 0.38f,
                start.y + (end.y - start.y) * 0.38f
            )
            drawCircle(ActiveRed.copy(alpha = 0.22f), radius = 18f, center = bumpPt)
            drawCircle(ActiveRed, radius = 7f, center = bumpPt)

            // Moving Vehicle Puck
            val carPt = Offset(
                start.x + (end.x - start.x) * routeProgress,
                start.y + (end.y - start.y) * routeProgress
            )
            drawCircle(HyundaiBlue.copy(alpha = 0.18f), radius = 24f, center = carPt)
            drawCircle(Color.White, radius = 13f, center = carPt)
            drawCircle(HyundaiBlue, radius = 10f, center = carPt)
        }

        // Scenario Simulator Chips + Custom Input Row
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScenarioChip("방지턱 소음") { onTriggerScenario("bump") }
                ScenarioChip("2→3단 변속충격") { onTriggerScenario("shift") }
                ScenarioChip("DPF 경고등 2초 점등") { onTriggerScenario("dpf") }
                ScenarioChip("강변 카페 (-70m)") { onTriggerScenario("cafe") }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(HyundaiSandBg)
                        .border(1.dp, HyundaiSandBorder, RoundedCornerShape(4.dp))
                        .clickable { onOpenParkedReport() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "누적 Care ${careCount}건 · Life ${lifeCount}건 (열람 →)",
                        color = HyundaiBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = onCustomInputChange,
                    placeholder = { Text("직접 발화 테스트 (예: 브레이크 끼익 소리, 왼쪽 주유소)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).height(46.dp)
                )
                Button(
                    onClick = onSubmitCustom,
                    modifier = Modifier.height(46.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = InkPrimary)
                ) {
                    Text("음성 핀 기록", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ScenarioChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(PleosDrivingBg)
            .border(1.dp, PleosBorder, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) {
        Text(label, color = InkPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ParkedMasterDetailStage(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    carePins: List<CarePinSnapshot>,
    selectedCareIdx: Int,
    onSelectCareIdx: (Int) -> Unit,
    lifePins: List<LifePinBookmark>,
    selectedLifeIdx: Int,
    onSelectLifeIdx: (Int) -> Unit,
    naviBanner: String?,
    onOpenMechanicModal: () -> Unit,
    onResetCustom: () -> Unit,
    onDispatchNavi: (Double, Double, String, Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(HyundaiBlue)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("P단 정차 감지", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
                Text("주행 핀 통합 리포트", color = InkPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { onSelectTab(0) },
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 0) HyundaiBlue else PleosDrivingBg,
                        contentColor = if (selectedTab == 0) Color.White else InkSecondary
                    )
                ) {
                    Text("Care Pin (${carePins.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onSelectTab(1) },
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 1) HyundaiBlue else PleosDrivingBg,
                        contentColor = if (selectedTab == 1) Color.White else InkSecondary
                    )
                ) {
                    Text("Life Pin (${lifePins.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (naviBanner != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HyundaiSandBg)
                    .border(1.dp, HyundaiSandBorder, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(naviBanner, color = HyundaiBlue, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedTab == 0 && carePins.isNotEmpty()) {
            val activeCare = carePins[selectedCareIdx]
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Master Timeline List
                Column(
                    modifier = Modifier
                        .width(175.dp)
                        .fillMaxHeight()
                        .border(1.dp, PleosBorder, RoundedCornerShape(4.dp))
                        .background(PleosDrivingBg)
                        .padding(6.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("누적 목록", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = InkSecondary)
                        Text(
                            "초기화",
                            fontSize = 10.sp,
                            color = HyundaiBlue,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onResetCustom() }
                        )
                    }
                    carePins.forEachIndexed { idx, item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                                .border(
                                    width = if (idx == selectedCareIdx) 1.5.dp else 1.dp,
                                    color = if (idx == selectedCareIdx) HyundaiBlue else PleosBorder,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { onSelectCareIdx(idx) }
                                .padding(7.dp)
                        ) {
                            Text("${item.id} · ${item.capturedAtLabel}", fontSize = 9.5.sp, color = InkMuted, fontWeight = FontWeight.Bold)
                            Text(item.symptomCategory, fontSize = 10.5.sp, color = HyundaiBlue, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("“${item.utterance}”", fontSize = 10.5.sp, color = InkPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // Detail Telemetry Spec
                Column(
                    modifier = Modifier
                        .weight(1.25f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().background(HyundaiSandBg).padding(9.dp)) {
                        Column {
                            Text("${activeCare.id} · ${activeCare.symptomCategory}", color = HyundaiBlue, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            Text("“${activeCare.utterance}”", color = InkPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    SpecTile("구동계 열간 상태 (Odometer SDK)", activeCare.thermalSummary)
                    SpecTile("제동 개입 맥락 (Brake SDK)", activeCare.brakeSummary)
                    SpecTile("공조·외기 환경 (HVAC SDK)", activeCare.hvacSummary)
                    SpecTile(
                        "경고등·누적거리 (Safety / CarInfo)",
                        "${activeCare.telltaleSummary} · ${activeCare.odometerKm.toInt()} km",
                        highlightAlert = activeCare.telltaleSummary.contains("점등")
                    )
                }

                // Right QR + Mechanic Modal + NaviHelper
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                        .border(1.dp, PleosBorder, RoundedCornerShape(4.dp))
                        .background(PleosDrivingBg)
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("BLUEHANDS 정비사 스캔 QR", color = HyundaiBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        QrMatrixCanvas(seed = activeCare.id + activeCare.utterance)
                        OutlinedButton(
                            onClick = onOpenMechanicModal,
                            modifier = Modifier.fillMaxWidth().height(34.dp),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("정비사 스캔 화면 보기", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = HyundaiBlue)
                        }
                    }
                    Button(
                        onClick = { onDispatchNavi(37.4012, 127.1086, "블루핸즈 판교테크노밸리점", true) },
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HyundaiBlue)
                    ) {
                        Text("NaviHelper 목적지 설정", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (lifePins.isNotEmpty()) {
            val activeLife = lifePins[selectedLifeIdx]
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    modifier = Modifier
                        .width(175.dp)
                        .fillMaxHeight()
                        .border(1.dp, PleosBorder, RoundedCornerShape(4.dp))
                        .background(PleosDrivingBg)
                        .padding(6.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("누적 북마크", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = InkSecondary)
                    lifePins.forEachIndexed { idx, item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                                .border(
                                    width = if (idx == selectedLifeIdx) 1.5.dp else 1.dp,
                                    color = if (idx == selectedLifeIdx) HyundaiBlue else PleosBorder,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { onSelectLifeIdx(idx) }
                                .padding(7.dp)
                        ) {
                            Text("${item.id} · -${item.correctedOffsetMeters}m", fontSize = 9.5.sp, color = InkMuted, fontWeight = FontWeight.Bold)
                            Text(item.matchedPoiName, fontSize = 10.5.sp, color = HyundaiBlue, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("“${item.utterance}”", fontSize = 10.5.sp, color = InkPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().background(HyundaiSandBg).padding(10.dp)) {
                        Column {
                            Text("LOOK-BACK GEO-OFFSET (속도 비례 좌표 역산)", color = HyundaiBlue, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            Text("“${activeLife.utterance}”", color = InkPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    SpecTile(
                        "발화 지연 거리 역산 결과 (FusedLocation SDK)",
                        "주행속도 ${activeLife.speedAtCaptureKmh.toInt()} km/h × 지연 3.5초 = -${activeLife.correctedOffsetMeters} m 이전 좌표 보정"
                    )
                    SpecTile("매칭된 도로변 장소 (POI)", activeLife.matchedPoiName)
                    Button(
                        onClick = {
                            onDispatchNavi(activeLife.correctedLat, activeLife.correctedLng, activeLife.matchedPoiName, false)
                        },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HyundaiBlue)
                    ) {
                        Text("NaviHelper 귀갓길 경유지로 추가", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MechanicQrScanDialog(pin: CarePinSnapshot, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(1.dp, PleosBorder, RoundedCornerShape(8.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HyundaiBlue)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("HYUNDAI BLUEHANDS · SMART RECEPTION PAD", color = ActiveBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("정비사 QR 스캔 문진표 — ${pin.id}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "닫기",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "차종: Avante AD 1.6 Diesel (${pin.odometerKm.toInt()} km)  |  기록 시각: ${pin.capturedAtLabel}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary
                )
                Box(modifier = Modifier.fillMaxWidth().background(HyundaiSandBg).padding(10.dp)) {
                    Text("사전 문진 요약: ${pin.engineerHint}", fontSize = 11.5.sp, color = InkPrimary)
                }
                val jsonPreview = """
                    {
                      "schema": "pleos.pindrive.v2",
                      "pinId": "${pin.id}",
                      "category": "${pin.symptomCategory}",
                      "thermal": "${pin.thermalSummary}",
                      "brake": "${pin.brakeSummary}",
                      "safety": "${pin.telltaleSummary}"
                    }
                """.trimIndent()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(InkPrimary)
                        .padding(10.dp)
                ) {
                    Text(jsonPreview, color = Color(0xFFE5E7EB), fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun GearToggleStrip(gear: GearState, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFE6E9EF))
            .clickable { onToggle() }
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(GearState.P to "P", GearState.D to "D").forEach { (g, label) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (gear == g) HyundaiBlue else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = label,
                    color = if (gear == g) Color.White else InkMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }
        }
    }
}

@Composable
private fun SpecTile(label: String, value: String, highlightAlert: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (highlightAlert) Color(0xFFFFF5F3) else Color.White)
            .border(1.dp, if (highlightAlert) ActiveRed else PleosBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) {
        Text(label, color = InkMuted, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
        Text(
            value,
            color = if (highlightAlert) ActiveRed else InkPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QrMatrixCanvas(seed: String) {
    Canvas(
        modifier = Modifier
            .size(86.dp)
            .background(Color.White)
            .border(1.dp, PleosBorder)
            .padding(6.dp)
    ) {
        val grid = 11
        val cell = size.width / grid
        val hashBase = abs(seed.hashCode())
        for (r in 0 until grid) {
            for (c in 0 until grid) {
                val isCorner = (r < 3 && c < 3) || (r < 3 && c >= grid - 3) || (r >= grid - 3 && c < 3)
                val bit = if (isCorner) {
                    !(r == 1 && c == 1 || r == 1 && c == grid - 2 || r == grid - 2 && c == 1)
                } else {
                    ((hashBase shr ((r * 3 + c) % 24)) and 1) == 1 || ((r * 7 + c * 13 + hashBase) % 3 == 0)
                }
                if (bit) {
                    drawRect(
                        color = InkPrimary,
                        topLeft = Offset(c * cell, r * cell),
                        size = Size(min(cell, size.width - c * cell), min(cell, size.height - r * cell))
                    )
                }
            }
        }
    }
}
