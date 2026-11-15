package ai.hex.pindrive.core

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Core telemetry ring-buffer and look-back geo-offset engine for PinDrive.
 *
 * 1. Maintains an in-memory circular buffer of recent vehicle telemetry samples.
 * 2. Extracts [CarePinSnapshot] with 4 real-driver diagnostic context tags:
 *    - Cold vs. Warm state (trip time <= 300s or trip distance <= 2.0km)
 *    - Pre-event brake intervention within the preceding 10 seconds
 *    - Exterior temperature & HVAC compressor/heater load
 *    - Transient telltale / warning history from Pleos Safety domain
 * 3. Computes [LifePinBookmark] using Look-back Geo-Offset (default 3.5s reaction delay).
 */
class PinDriveEngine(
    private val maxBufferSamples: Int = 150 // 15s @ 10Hz (or 150s @ 1Hz)
) {
    private val ringBuffer = ArrayDeque<TelemetrySample>(maxBufferSamples)

    fun pushSample(sample: TelemetrySample) {
        if (ringBuffer.size >= maxBufferSamples) {
            ringBuffer.removeFirst()
        }
        ringBuffer.addLast(sample)
    }

    fun currentSamples(): List<TelemetrySample> = ringBuffer.toList()

    fun classifyUtterance(utterance: String): PinType {
        val careKeywords = listOf(
            "소음", "찌그덕", "덜컥", "소리", "진동", "변속", "울컥",
            "경고등", "브레이크", "하부", "냄새", "출력", "떨림", "이상"
        )
        return if (careKeywords.any { utterance.contains(it) }) {
            PinType.CARE_PIN
        } else {
            PinType.LIFE_PIN
        }
    }

    fun detectLateralSide(utterance: String): LateralSide = when {
        utterance.contains("오른쪽") || utterance.contains("우측") -> LateralSide.RIGHT
        utterance.contains("왼쪽") || utterance.contains("좌측") -> LateralSide.LEFT
        else -> LateralSide.NONE
    }

    fun captureCarePin(
        id: String,
        timeLabel: String,
        utterance: String,
        powertrain: PowertrainType = PowertrainType.ICE_DIESEL,
        windowMs: Long = 15_000L
    ): CarePinSnapshot {
        val latest = ringBuffer.lastOrNull()
            ?: error("Ring buffer must contain at least one telemetry sample")
        val cutoff = latest.timestampMs - windowMs
        val window = ringBuffer.filter { it.timestampMs >= cutoff }

        val isCold = latest.tripElapsedSec <= 300 || latest.tripDistanceKm <= 2.0
        val thermalText = if (isCold) {
            "냉간 주행 초기 (시동 ${latest.tripElapsedSec / 60}분 / ${latest.tripDistanceKm}km)"
        } else {
            "열간 정상 주행 (${latest.tripElapsedSec / 60}분 / ${latest.tripDistanceKm}km)"
        }

        val hadBrake = window.any { it.brakePedalPressed }
        val brakeText = if (hadBrake) {
            "직전 10초 내 감속 브레이크 개입 감지"
        } else {
            "브레이크 미개입 (정속/코스팅 중 발생)"
        }

        val hvacText = "외기 ${latest.exteriorTempC}°C · " +
            if (latest.hvacActive) "공조(HVAC) ON" else "공조 OFF"

        val transientWarning = window.mapNotNull { it.activeTelltale }.lastOrNull()
        val telltaleText = transientWarning ?: "Telltale 정상 (순간점등 0건)"

        val category = when {
            utterance.contains("하부") || utterance.contains("방지턱") || utterance.contains("찌그덕") ->
                "현가/하부 소음 (Suspension)"
            utterance.contains("변속") || utterance.contains("울컥") ->
                "구동/변속 충격 (Drivetrain)"
            utterance.contains("경고등") ->
                "전장/경고등 간헐 점등 (Safety Telltale)"
            else -> "일반 주행 이상 증상"
        }

        val hint = buildString {
            if (isCold && latest.exteriorTempC <= 0.0) append("영하권 냉간 수축 조건 · ")
            if (hadBrake) append("감속 하중 이동 직후 · ")
            append("$category 중심 초기 점검 권장")
        }

        return CarePinSnapshot(
            id = id,
            capturedAtLabel = timeLabel,
            utterance = utterance,
            symptomCategory = category,
            isColdStartCondition = isCold,
            thermalSummary = thermalText,
            hadRecentBrakeIntervention = hadBrake,
            brakeSummary = brakeText,
            hvacSummary = hvacText,
            telltaleSummary = telltaleText,
            odometerKm = latest.odometerKm,
            powertrainType = powertrain,
            windowSamples = window,
            engineerHint = hint
        )
    }

    /**
     * Computes look-back geo-offset coordinate to compensate for the ~3.5s driver
     * perception-to-speech delay (e.g., 72 km/h = 20 m/s -> 70m travel distance).
     */
    fun captureLifePin(
        id: String,
        timeLabel: String,
        utterance: String,
        matchedPoiName: String,
        reactionDelayMs: Long = 3_500L,
        lateralRoadsideOffsetMeters: Double = 18.0
    ): LifePinBookmark {
        val latest = ringBuffer.lastOrNull()
            ?: error("Ring buffer must contain at least one telemetry sample")
        val targetTimestamp = latest.timestampMs - reactionDelayMs

        val lookBackSample = ringBuffer.minByOrNull {
            kotlin.math.abs(it.timestampMs - targetTimestamp)
        } ?: latest

        val side = detectLateralSide(utterance)
        val speedMps = latest.speedKmh / 3.6
        val longitudinalOffsetMeters = if (lookBackSample !== latest) {
            speedMps * ((latest.timestampMs - lookBackSample.timestampMs) / 1000.0)
        } else {
            speedMps * (reactionDelayMs / 1000.0)
        }

        // Base coordinate from historical sample (or backward dead-reckoning if single sample)
        val baseLat: Double
        val baseLng: Double
        if (lookBackSample !== latest) {
            baseLat = lookBackSample.latitude
            baseLng = lookBackSample.longitude
        } else {
            val backRad = Math.toRadians((latest.headingDeg + 180.0) % 360.0)
            val dNorth = longitudinalOffsetMeters * cos(backRad)
            val dEast = longitudinalOffsetMeters * sin(backRad)
            baseLat = latest.latitude + (dNorth / 111_320.0)
            baseLng = latest.longitude + (dEast / (111_320.0 * cos(Math.toRadians(latest.latitude))))
        }

        // Apply lateral offset (Right = +90 deg, Left = -90 deg from vehicle heading)
        val (finalLat, finalLng) = if (side != LateralSide.NONE) {
            val sideAngle = if (side == LateralSide.RIGHT) 90.0 else -90.0
            val latRad = Math.toRadians((lookBackSample.headingDeg + sideAngle + 360.0) % 360.0)
            val nMeters = lateralRoadsideOffsetMeters * cos(latRad)
            val eMeters = lateralRoadsideOffsetMeters * sin(latRad)
            Pair(
                baseLat + (nMeters / 111_320.0),
                baseLng + (eMeters / (111_320.0 * cos(Math.toRadians(baseLat))))
            )
        } else {
            Pair(baseLat, baseLng)
        }

        return LifePinBookmark(
            id = id,
            capturedAtLabel = timeLabel,
            utterance = utterance,
            rawLat = latest.latitude,
            rawLng = latest.longitude,
            correctedLat = finalLat,
            correctedLng = finalLng,
            correctedOffsetMeters = (longitudinalOffsetMeters * 10.0).roundToInt() / 10.0,
            lateralSide = side,
            matchedPoiName = matchedPoiName,
            speedAtCaptureKmh = latest.speedKmh
        )
    }
}
