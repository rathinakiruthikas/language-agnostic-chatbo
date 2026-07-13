package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {
    private val TAG = "VoiceManager"
    
    // --- Text To Speech (TTS) ---
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var speechRate = 1.0f
    private var speechPitch = 1.0f
    
    // Text breakdown for pause/resume support
    private var fullTextToSpeak: String = ""
    private var sentences: List<String> = emptyList()
    private var currentSentenceIndex = 0
    private var isTtsPlaying = false
    private var currentLanguageLocale: Locale = Locale.US

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            tts?.language = Locale.US
            Log.d(TAG, "TTS Initialized successfully")
        } else {
            Log.e(TAG, "Failed to initialize TTS")
        }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate
        tts?.setSpeechRate(rate)
    }

    fun setSpeechPitch(pitch: Float) {
        speechPitch = pitch
        tts?.setPitch(pitch)
    }

    fun getAvailableVoices(): List<String> {
        return try {
            if (isTtsInitialized) {
                tts?.voices?.mapNotNull { it.name }?.distinct()?.sorted() ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setVoice(voiceName: String) {
        try {
            if (isTtsInitialized) {
                val voice = tts?.voices?.firstOrNull { it.name == voiceName }
                if (voice != null) {
                    tts?.voice = voice
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set voice", e)
        }
    }

    /**
     * Speaks the given text in the appropriate language locale.
     */
    fun speak(text: String, languageCode: String = "en", onDone: () -> Unit = {}) {
        if (!isTtsInitialized) {
            Log.w(TAG, "TTS not initialized")
            return
        }

        stopSpeaking()

        fullTextToSpeak = text
        // Split text by standard sentence delimiters
        sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        currentSentenceIndex = 0
        isTtsPlaying = true

        val locale = getLocaleFromLangCode(languageCode)
        currentLanguageLocale = locale
        tts?.language = locale
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(speechPitch)

        speakNextSentence(onDone)
    }

    private fun speakNextSentence(onDone: () -> Unit = {}) {
        if (!isTtsPlaying || tts == null) return

        if (currentSentenceIndex < sentences.size) {
            val sentence = sentences[currentSentenceIndex]
            
            // Set up utterance listeners if needed, or simply speak
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "sentence_$currentSentenceIndex")
            }
            
            tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, params, "sentence_$currentSentenceIndex")
            
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "Started speaking: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    currentSentenceIndex++
                    speakNextSentence(onDone)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS Error speaking sentence")
                }
            })
        } else {
            isTtsPlaying = false
            onDone()
        }
    }

    fun pauseSpeaking() {
        if (isTtsPlaying) {
            tts?.stop()
            isTtsPlaying = false
        }
    }

    fun resumeSpeaking(onDone: () -> Unit = {}) {
        if (!isTtsPlaying && currentSentenceIndex < sentences.size) {
            isTtsPlaying = true
            tts?.language = currentLanguageLocale
            speakNextSentence(onDone)
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        isTtsPlaying = false
        currentSentenceIndex = 0
        sentences = emptyList()
        fullTextToSpeak = ""
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }

    // --- Speech Recognition (STT) ---
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionIntent: Intent? = null
    private var isListening = false

    fun startListening(
        languageCode: String = "en",
        onReadyForSpeech: () -> Unit = {},
        onBeginningOfSpeech: () -> Unit = {},
        onBufferReceived: (ByteArray) -> Unit = {},
        onEndOfSpeech: () -> Unit = {},
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (speechRecognizer != null) {
            stopListening()
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onReadyForSpeech()
                }

                override fun onBeginningOfSpeech() {
                    onBeginningOfSpeech()
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {
                    if (buffer != null) onBufferReceived(buffer)
                }

                override fun onEndOfSpeech() {
                    isListening = false
                    onEndOfSpeech()
                }

                override fun onError(error: Int) {
                    isListening = false
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No recognition match found"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        else -> "Speech recognizer error"
                    }
                    onError(errorMsg)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResult(matches[0])
                    } else {
                        onError("No match found")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, getLanguageTagFromCode(languageCode))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(recognitionIntent)
            isListening = true
        } catch (e: Exception) {
            onError("Failed to start speech recognition: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    // --- Language Translation Helpers ---
    private fun getLocaleFromLangCode(code: String): Locale {
        return when (code.lowercase()) {
            "ta" -> Locale("ta", "IN") // Tamil
            "hi" -> Locale("hi", "IN") // Hindi
            "te" -> Locale("te", "IN") // Telugu
            "ml" -> Locale("ml", "IN") // Malayalam
            "kn" -> Locale("kn", "IN") // Kannada
            "mr" -> Locale("mr", "IN") // Marathi
            "bn" -> Locale("bn", "IN") // Bengali
            "gu" -> Locale("gu", "IN") // Gujarati
            "pa" -> Locale("pa", "IN") // Punjabi
            "ur" -> Locale("ur", "PK") // Urdu
            "fr" -> Locale.FRANCE
            "de" -> Locale.GERMANY
            "es" -> Locale("es", "ES")
            "ja" -> Locale.JAPAN
            "zh" -> Locale.CHINA
            "ko" -> Locale.KOREA
            "ar" -> Locale("ar", "SA")
            else -> Locale.US
        }
    }

    private fun getLanguageTagFromCode(code: String): String {
        return when (code.lowercase()) {
            "ta" -> "ta-IN"
            "hi" -> "hi-IN"
            "te" -> "te-IN"
            "ml" -> "ml-IN"
            "kn" -> "kn-IN"
            "mr" -> "mr-IN"
            "bn" -> "bn-IN"
            "gu" -> "gu-IN"
            "pa" -> "pa-IN"
            "ur" -> "ur-PK"
            "fr" -> "fr-FR"
            "de" -> "de-DE"
            "es" -> "es-ES"
            "ja" -> "ja-JP"
            "zh" -> "zh-CN"
            "ko" -> "ko-KR"
            "ar" -> "ar-SA"
            else -> "en-US"
        }
    }
}
