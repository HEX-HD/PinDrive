package ai.hex.pindrive

import ai.hex.pindrive.core.LateralSide
import ai.hex.pindrive.core.PinDriveEngine
import ai.hex.pindrive.core.PinType
import ai.hex.pindrive.core.TelemetrySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinDriveEngineTest {

    @Test
    fun `captures cold-start and brake intervention in 15s care pin window`() {
        val engine = PinDriveEngine()
        val t0 = 1_000_000L

        engine.pushSample(
            TelemetrySample(
                timestampMs = t0,
                speedKmh = 36.0,
                odometerKm = 84320.0,
                tripDistanceKm = 0.8,
                tripElapsedSec = 175,
                brakePedalPressed = true,
                drivingMode = "COMFORT",
                exteriorTempC = -2.0,
                hvacActive = true,
                fuelOrSocPercent = 62,
                instantEconomyKmPerL = 14.5,
                activeTelltale = null,
                latitude = 37.4000,
                longitude = 127.1000,
                headingDeg = 90.0
            )
        )
        engine.pushSample(
            TelemetrySample(
                timestampMs = t0 + 2_000L,
                speedKmh = 24.0,
                odometerKm = 84320.1,
                tripDistanceKm = 0.9,
                tripElapsedSec = 177,
                brakePedalPressed = false,
                drivingMode = "COMFORT",
                exteriorTempC = -2.0,
                hvacActive = true,
                fuelOrSocPercent = 62,
                instantEconomyKmPerL = 14.8,
                activeTelltale = null,
                latitude = 37.4001,
                longitude = 127.1005,
                headingDeg = 90.0
            )
        )

        val utterance = "방지턱 넘을 때 오른쪽 앞에서 찌그덕 소음 났어"
        assertEquals(PinType.CARE_PIN, engine.classifyUtterance(utterance))

        val snapshot = engine.captureCarePin("C1", "07:45", utterance)
        assertTrue(snapshot.isColdStartCondition)
        assertTrue(snapshot.hadRecentBrakeIntervention)
        assertEquals(2, snapshot.windowSamples.size)
    }

    @Test
    fun `computes 70m look-back geo-offset at 72kmh with 3_5s reaction delay`() {
        val engine = PinDriveEngine()
        engine.pushSample(
            TelemetrySample(
                timestampMs = 2_000_000L,
                speedKmh = 72.0, // 20 m/s
                odometerKm = 84355.0,
                tripDistanceKm = 30.0,
                tripElapsedSec = 1800,
                brakePedalPressed = false,
                drivingMode = "ECO",
                exteriorTempC = 8.0,
                hvacActive = true,
                fuelOrSocPercent = 58,
                instantEconomyKmPerL = 19.2,
                activeTelltale = null,
                latitude = 37.5210,
                longitude = 127.3120,
                headingDeg = 90.0 // Heading East -> 3.5s ago was West (smaller longitude), Right side is South (smaller latitude)
            )
        )

        val pin = engine.captureLifePin(
            id = "L1",
            timeLabel = "14:10",
            utterance = "방금 오른쪽 강변 카페 핀 찍어줘",
            matchedPoiName = "리버사이드 베이커리",
            reactionDelayMs = 3_500L
        )

        assertEquals(70.0, pin.correctedOffsetMeters, 0.1)
        assertEquals(LateralSide.RIGHT, pin.lateralSide)
        assertTrue("Corrected longitude should be west of raw longitude", pin.correctedLng < pin.rawLng)
        assertTrue("Right-side POI when heading east should be south of road", pin.correctedLat < pin.rawLat)
    }
}
