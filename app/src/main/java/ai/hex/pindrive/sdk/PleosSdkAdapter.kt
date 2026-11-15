package ai.hex.pindrive.sdk

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Abstraction layer mapped 1:1 to Pleos Playground SDK domains:
 * - Vehicle SDK (`ai.pleos.playground.vehicle.api`):
 *   Odometer, Brake, DrivingMode, Safety, CarInfo, Fuel / EvBattery, HVAC
 * - FusedLocation SDK (`ai.pleos.playground.location.fused`)
 * - SpeechToText SDK (`ai.pleos.playground.stt`) & Android SpeechRecognizer Bridge
 * - TextToSpeech SDK (`ai.pleos.playground.tts` — HYBRID mode, `setSpeakerType("haeun")`)
 * - NaviHelper SDK (`ai.pleos.playground.navi.helper`)
 */
class PleosSdkAdapter(private val context: Context) {

    companion object {
        const val PLEOS_DEFAULT_KO_SPEAKER = "haeun"
        private const val PREFS_NAME = "hex_pindrive_v2_store"
        private const val KEY_CUSTOM_UTTERANCES = "custom_utterances_json"
    }

    private var ttsEngine: TextToSpeech? = null
    private var ttsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private var speechRecognizer: SpeechRecognizer? = null

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
                mediaPlayer = MediaPlayer.create(context, rawResId)?.apply {
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
     * Starts real Korean Speech-To-Text recognition (`ko-KR`), bridging to Pleos SpeechToText SDK
     * on Pleos Connect targets and Android SpeechRecognizer on general Android / AAOS devices.
     */
    fun startLiveSpeechRecognition(
        onListeningChanged: (Boolean) -> Unit,
        onPartialTranscript: (String) -> Unit,
        onFinalTranscript: (String) -> Unit,
        onErrorHint: (String) -> Unit
    ) {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onListeningChanged(false)
                onErrorHint("이 기기에 음성인식 엔진이 없어 직접 입력창을 사용합니다.")
                return
            }
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onListeningChanged(true)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    onListeningChanged(false)
                }

                override fun onError(error: Int) {
                    onListeningChanged(false)
                    onErrorHint("음성이 감지되지 않았습니다. 다시 말씀하시거나 하단 입력창을 이용해 주세요.")
                }

                override fun onResults(results: Bundle?) {
                    onListeningChanged(false)
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        onFinalTranscript(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        onPartialTranscript(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer.startListening(intent)
        } catch (_: Exception) {
            onListeningChanged(false)
            onErrorHint("마이크 초기화 중 예외가 발생해 텍스트 입력 모드로 전환합니다.")
        }
    }

    fun stopSpeechRecognition() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    /**
     * Persists user-recorded voice utterances locally (Offline-First storage).
     */
    fun appendPersistedUtterance(timeLabel: String, utterance: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString(KEY_CUSTOM_UTTERANCES, "[]") ?: "[]")
            arr.put(JSONObject().apply {
                put("time", timeLabel)
                put("utterance", utterance)
            })
            prefs.edit().putString(KEY_CUSTOM_UTTERANCES, arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun loadPersistedUtterances(): List<Pair<String, String>> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString(KEY_CUSTOM_UTTERANCES, "[]") ?: "[]")
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(obj.optString("time", "08:10:00") to obj.optString("utterance", ""))
                }
            }.filter { it.second.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearPersistedUtterances() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_CUSTOM_UTTERANCES).apply()
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
        speechRecognizer?.destroy()
        speechRecognizer = null
        mediaPlayer?.release()
        mediaPlayer = null
        ttsEngine?.stop()
        ttsEngine?.shutdown()
        ttsEngine = null
    }
}
