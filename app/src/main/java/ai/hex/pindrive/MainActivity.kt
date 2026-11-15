package ai.hex.pindrive

import ai.hex.pindrive.core.CarePinSnapshot
import ai.hex.pindrive.core.GearState
import ai.hex.pindrive.core.LifePinBookmark
import ai.hex.pindrive.core.PinDriveEngine
import ai.hex.pindrive.core.PowertrainType
import ai.hex.pindrive.core.TelemetrySample
import ai.hex.pindrive.sdk.PleosSdkAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        seedRealisticColdMorningTelemetry(engine)

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

    private fun seedRealisticColdMorningTelemetry(targetEngine: PinDriveEngine) {
        val baseTime = 1_763_160_300_000L
        val speeds = listOf(38.0, 38.0, 36.0, 32.0, 27.0, 24.0, 24.0, 25.0, 28.0, 30.0)
        speeds.forEachIndexed { idx, spd ->
            targetEngine.pushSample(
                TelemetrySample(
                    timestampMs = baseTime + idx * 1000L,
                    speedKmh = spd,
                    odometerKm = 84320.0 + idx * 0.008,
                    tripDistanceKm = 0.9,
                    tripElapsedSec = 180 + idx,
                    brakePedalPressed = idx in 2..4,
                    drivingMode = "COMFORT",
                    exteriorTempC = -2.0,
                    hvacActive = true,
                    fuelOrSocPercent = 62,
                    instantEconomyKmPerL = 14.8,
                    activeTelltale = null,
                    latitude = 37.4020 + idx * 0.00007,
                    longitude = 127.1080 + idx * 0.00009,
                    headingDeg = 90.0
                )
            )
        }
    }
}

@Composable
fun PinDriveCockpitScreen(
    engine: PinDriveEngine,
    adapter: PleosSdkAdapter
) {
    var gear by remember { mutableStateOf(GearState.D) }
    var selectedParkedTab by remember { mutableIntStateOf(0) }
    var statusMessage by remember {
        mutableStateOf("하부 소음 케어 핀이 직전 10초 차량 상태와 함께 저장되었습니다.")
    }
    var lastUtterance by remember {
        mutableStateOf("“방금 하부 소음 핀 찍어줘 — 방지턱 넘을 때 오른쪽 앞에서 찌그덕 소리 났어”")
    }
    var naviSentBanner by remember { mutableStateOf<String?>(null) }

    val carePins = remember {
        mutableStateListOf(
            engine.captureCarePin(
                id = "CARE-01",
                timeLabel = "07:45:03",
                utterance = "방지턱 넘을 때 오른쪽 앞에서 찌그덕 소리 났어",
                powertrain = PowertrainType.ICE_DIESEL
            )
        )
    }

    val lifePins = remember {
        val sampleTime = 1_763_180_000_000L
        engine.pushSample(
            TelemetrySample(
                timestampMs = sampleTime,
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
        mutableStateListOf(
            engine.captureLifePin(
                id = "LIFE-01",
                timeLabel = "14:10:04",
                utterance = "방금 오른쪽 강변 카페 핀 찍어줘",
                matchedPoiName = "리버사이드 로스터리 베이커리"
            )
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Pleos Connect System Status Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Color.White)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(16.dp)
                        .background(HyundaiBlue)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pleos Connect / PinDrive", color = InkPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(16.dp))
                GearToggleStrip(
                    gear = gear,
                    onToggle = {
                        gear = if (gear == GearState.D) GearState.P else GearState.D
                        if (gear == GearState.P) {
                            adapter.speakHybrid(
                                "정차가 감지되어 주행 핀 리포트를 활성화합니다.",
                                R.raw.tts_parked_report
                            )
                        }
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            .background(ActiveBlue)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gleo TTS · haeun", color = HyundaiBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "ICE 1.6 Diesel (84,320 km)  |  외기 -2.0°C",
                    color = InkSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PleosBorder))

        // 2. Main Split Workspace
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Driving View (Cluster)
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(PleosDrivingBg)
                    .border(1.dp, PleosBorder, RoundedCornerShape(6.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = if (gear == GearState.D) "24" else "0",
                            color = InkPrimary,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (gear == GearState.D) "km/h" else "PARKED",
                                color = InkSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (gear == GearState.D) "COMFORT" else "P 정차 리포트",
                                color = HyundaiBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    SpecTile("주행 경과 · Odometer", "시동 3분 · 0.9 km (냉간)")
                    Spacer(modifier = Modifier.height(6.dp))
                    SpecTile("제동 이력 · Brake", "직전 2.1초 감속 개입")
                    Spacer(modifier = Modifier.height(6.dp))
                    SpecTile("공조 / 경고등 · Safety", "외기 -2.0°C · Telltale 정상")
                }

                // Voice Simulation Controls
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("VOICE TRIGGER SIMULATION", color = InkMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            gear = GearState.D
                            lastUtterance = "“방금 하부 소음 핀 찍어줘 — 방지턱 우측 전륜 찌그덕 소리”"
                            statusMessage = "하부 소음 케어 핀이 직전 10초 차량 상태와 함께 저장되었습니다."
                            adapter.speakHybrid(statusMessage, R.raw.tts_care_saved)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HyundaiBlue)
                    ) {
                        Text("Care Pin 음성 발화 시뮬레이션", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            gear = GearState.D
                            lastUtterance = "“핀드라이브, 방금 오른쪽 강변 카페 핀 찍어줘”"
                            statusMessage = "3.5초 전 주행 좌표를 70미터 역산하여 강변 카페 북마크를 저장했습니다."
                            adapter.speakHybrid(statusMessage, R.raw.tts_life_saved)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActiveBlue)
                    ) {
                        Text("Life Pin 음성 발화 (-70m 역산)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            gear = if (gear == GearState.D) GearState.P else GearState.D
                            if (gear == GearState.P) {
                                adapter.speakHybrid(
                                    "정차가 감지되어 주행 핀 리포트를 활성화합니다.",
                                    R.raw.tts_parked_report
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = InkPrimary)
                    ) {
                        Text(
                            if (gear == GearState.D) "P단 정차 전환 (리포트 열람)" else "D단 주행 화면 복귀",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                    .padding(18.dp)
            ) {
                if (gear == GearState.D) {
                    DrivingZeroTouchStage(
                        utterance = lastUtterance,
                        ttsReply = statusMessage,
                        careCount = carePins.size,
                        lifeCount = lifePins.size
                    )
                } else {
                    ParkedRichReportStage(
                        selectedTab = selectedParkedTab,
                        onSelectTab = { selectedParkedTab = it },
                        carePin = carePins.first(),
                        lifePin = lifePins.first(),
                        naviBanner = naviSentBanner,
                        onDispatchNavi = { lat, lng, title ->
                            adapter.dispatchDestinationToNaviHelper(lat, lng, title)
                            naviSentBanner = "NaviHelper 경로 전송 완료 — $title"
                            val clipRes = if (selectedParkedTab == 0) R.raw.tts_navi_care else R.raw.tts_navi_life
                            adapter.speakHybrid("$title 목적지를 내비게이션으로 전송했습니다.", clipRes)
                        }
                    )
                }
            }
        }

        // 3. Pleos Connect Bottom GNB Dock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(InkPrimary)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("22.0°C  AUTO · DRIVER", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Pleos Connect · PinDrive v1.0 (HEX)", color = Color(0xFF9CA3AF), fontSize = 11.sp)
            Text("22.5°C  PASSENGER", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(if (gear == GearState.P) HyundaiBlue else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "P",
                color = if (gear == GearState.P) Color.White else InkMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(if (gear == GearState.D) HyundaiBlue else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "D",
                color = if (gear == GearState.D) Color.White else InkMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SpecTile(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .border(1.dp, PleosBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(label, color = InkMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = InkPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DrivingZeroTouchStage(
    utterance: String,
    ttsReply: String,
    careCount: Int,
    lifeCount: Int
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Restrained Voice Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PleosBorder, RoundedCornerShape(4.dp))
                .background(Color.White)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(52.dp)
                    .background(HyundaiBlue)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "STT / GLEO VOICE CAPTURE",
                        color = HyundaiBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "주행 중 화면 전환 잠금 (Zero-Touch Safety UX)",
                        color = InkSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(utterance, color = InkPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(3.dp))
                Text("TTS 음성 안내: $ttsReply", color = InkSecondary, fontSize = 12.sp)
            }
        }

        // Clean Daylight Navigation Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE9EEF2))
                .border(1.dp, PleosBorder, RoundedCornerShape(4.dp))
        ) {
            val path = Path().apply {
                moveTo(size.width * 0.16f, size.height * 0.92f)
                cubicTo(
                    size.width * 0.36f, size.height * 0.62f,
                    size.width * 0.56f, size.height * 0.45f,
                    size.width * 0.88f, size.height * 0.12f
                )
            }
            drawPath(path, color = HyundaiBlue, style = Stroke(width = 12f))
            drawPath(path, color = ActiveBlue, style = Stroke(width = 4f))
            drawCircle(
                color = ActiveRed,
                radius = 12f,
                center = Offset(size.width * 0.46f, size.height * 0.56f)
            )
            drawCircle(
                color = HyundaiBlue,
                radius = 16f,
                center = Offset(size.width * 0.62f, size.height * 0.40f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(HyundaiBlue)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    "NaviHelper 경로 안내 중 · 350 m 앞 우회전",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "누적 기록: Care Pin ${careCount}건 · Life Pin ${lifeCount}건 (정차 시 열람)",
                color = InkSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ParkedRichReportStage(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    carePin: CarePinSnapshot,
    lifePin: LifePinBookmark,
    naviBanner: String?,
    onDispatchNavi: (Double, Double, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
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
                    Text("P단 정차 감지", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("주행 핀 리포트", color = InkPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { onSelectTab(0) },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 0) HyundaiBlue else PleosDrivingBg,
                        contentColor = if (selectedTab == 0) Color.White else InkSecondary
                    )
                ) {
                    Text("Care Pin 01 · 정비 스냅샷", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onSelectTab(1) },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 1) HyundaiBlue else PleosDrivingBg,
                        contentColor = if (selectedTab == 1) Color.White else InkSecondary
                    )
                ) {
                    Text("Life Pin 01 · -${lifePin.correctedOffsetMeters}m 역산", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (naviBanner != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HyundaiSandBg)
                    .border(1.dp, HyundaiSandBorder, RoundedCornerShape(4.dp))
                    .padding(10.dp)
            ) {
                Text(naviBanner, color = HyundaiBlue, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(modifier = Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(HyundaiSandBg)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(carePin.symptomCategory, color = HyundaiBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("“${carePin.utterance}”", color = InkPrimary, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                        }
                    }
                    SpecTile("구동계 열간 상태 (Odometer SDK)", carePin.thermalSummary)
                    SpecTile("제동 개입 맥락 (Brake SDK)", carePin.brakeSummary)
                    SpecTile("공조·외기 환경 (HVAC SDK)", carePin.hvacSummary)
                    SpecTile("경고등·누적거리 (Safety / CarInfo)", "${carePin.telltaleSummary} · ${carePin.odometerKm.toInt()} km")
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, PleosBorder, RoundedCornerShape(4.dp))
                        .background(PleosDrivingBg)
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("BLUEHANDS 정비사 스캔용 데이터 시트", color = HyundaiBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    QrMatrixCanvas()
                    Text(
                        "입고 시 증상이 재현되지 않아도 담당 엔지니어가 QR 스캔으로 15초 센서 맥락을 즉시 열람합니다.",
                        color = InkSecondary,
                        fontSize = 11.sp
                    )
                    Button(
                        onClick = { onDispatchNavi(37.4012, 127.1086, "블루핸즈 판교테크노밸리점") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HyundaiBlue)
                    ) {
                        Text("NaviHelper 목적지로 설정", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HyundaiSandBg)
                        .padding(12.dp)
                ) {
                    Column {
                        Text("LOOK-BACK GEO-OFFSET (속도 비례 좌표 역산)", color = HyundaiBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("“${lifePin.utterance}”", color = InkPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                SpecTile(
                    "발화 지연 거리 역산 결과 (FusedLocation SDK)",
                    "주행속도 ${lifePin.speedAtCaptureKmh.toInt()} km/h × 지연 3.5초 = -${lifePin.correctedOffsetMeters} m 이전 좌표 + 우측 헤딩 보정"
                )
                SpecTile(
                    "매칭된 도로변 장소 (POI)",
                    lifePin.matchedPoiName
                )
                Button(
                    onClick = {
                        onDispatchNavi(lifePin.correctedLat, lifePin.correctedLng, lifePin.matchedPoiName)
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

@Composable
private fun QrMatrixCanvas() {
    Canvas(
        modifier = Modifier
            .size(100.dp)
            .background(Color.White)
            .border(1.dp, PleosBorder)
            .padding(8.dp)
    ) {
        val grid = 9
        val cell = size.width / grid
        val pattern = listOf(
            "111010111",
            "101000101",
            "111011111",
            "000101000",
            "101110110",
            "001001010",
            "111010101",
            "101011010",
            "111001111"
        )
        pattern.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                if (ch == '1') {
                    drawRect(
                        color = InkPrimary,
                        topLeft = Offset(c * cell, r * cell),
                        size = Size(cell, cell)
                    )
                }
            }
        }
    }
}
