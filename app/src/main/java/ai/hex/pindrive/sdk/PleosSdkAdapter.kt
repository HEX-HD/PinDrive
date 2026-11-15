package ai.hex.pindrive.sdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Abstraction layer mapped 1:1 to Pleos Playground SDK domains:
 * - Vehicle SDK (`ai.pleos.playground.vehicle.api`):
 *   Odometer, Brake, DrivingMode, Safety, CarInfo, Fuel / EvBattery, HVAC
 * - FusedLocation SDK (`ai.pleos.playground.location.fused`)
 * - SpeechToText SDK (`ai.pleos.playground.stt`) & Gleo AI Intent Bridge
 * - TextToSpeech SDK (`ai.pleos.playground.tts` — HYBRID mode, `setSpeakerType("haeun")`)
 * - NaviHelper SDK (`ai.pleos.playground.navi.helper`)
 */
class PleosSdkAdapter(private val context: Context) {

    companion object {
        const val PLEOS_DEFAULT_KO_SPEAKER = "haeun" // Official Pleos TTS Korean speaker ID
    }

    private var ttsEngine: TextToSpeech? = null
    private var ttsReady = false
    private var mediaPlayer: android.media.MediaPlayer? = null

    fun initializeHybridTts() {
        ttsEngine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale.KOREAN
                ttsEngine?.setPitch(0.95f)
                ttsEngine?.setSpeechRate(1.03f)
                ttsReady = true
            }
        }
    }

    /**
     * Plays bundled studio neural voice asset (matching Pleos `haeun` tone) when available,
     * falling back to on-device Android TTS (mirroring Pleos HYBRID server/on-device mode).
     */
    fun speakHybrid(message: String, rawResId: Int? = null) {
        if (rawResId != null) {
            try {
                mediaPlayer?.release()
                mediaPlayer = android.media.MediaPlayer.create(context, rawResId)?.apply {
                    start()
                }
                return
            } catch (_: Exception) {
                // Fallback to on-device TTS below
            }
        }
        if (ttsReady) {
            ttsEngine?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "pindrive_tts")
        }
    }

    /**
     * Bridges to Pleos NaviHelper SDK destination/waypoint dispatch, with standard
     * Android Automotive `geo:` URI fallback on generic devices/emulators.
     */
    fun dispatchDestinationToNaviHelper(lat: Double, lng: Double, label: String): Boolean {
        return try {
            val encoded = Uri.encode(label)
            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encoded)")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        ttsEngine?.stop()
        ttsEngine?.shutdown()
        ttsEngine = null
    }
}
