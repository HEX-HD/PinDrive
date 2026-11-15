package ai.hex.pindrive.core

enum class GearState { P, R, N, D }

enum class PowertrainType { ICE_DIESEL, HEV, EV }

enum class PinType { CARE_PIN, LIFE_PIN }

enum class LateralSide { LEFT, RIGHT, NONE }

data class TelemetrySample(
    val timestampMs: Long,
    val speedKmh: Double,
    val odometerKm: Double,
    val tripDistanceKm: Double,
    val tripElapsedSec: Int,
    val brakePedalPressed: Boolean,
    val drivingMode: String,
    val exteriorTempC: Double,
    val hvacActive: Boolean,
    val fuelOrSocPercent: Int,
    val instantEconomyKmPerL: Double,
    val activeTelltale: String?,
    val latitude: Double,
    val longitude: Double,
    val headingDeg: Double
)

data class CarePinSnapshot(
    val id: String,
    val capturedAtLabel: String,
    val utterance: String,
    val symptomCategory: String,
    val isColdStartCondition: Boolean,
    val thermalSummary: String,
    val hadRecentBrakeIntervention: Boolean,
    val brakeSummary: String,
    val hvacSummary: String,
    val telltaleSummary: String,
    val odometerKm: Double,
    val powertrainType: PowertrainType,
    val windowSamples: List<TelemetrySample>,
    val engineerHint: String
)

data class LifePinBookmark(
    val id: String,
    val capturedAtLabel: String,
    val utterance: String,
    val rawLat: Double,
    val rawLng: Double,
    val correctedLat: Double,
    val correctedLng: Double,
    val correctedOffsetMeters: Double,
    val lateralSide: LateralSide,
    val matchedPoiName: String,
    val speedAtCaptureKmh: Double
)
