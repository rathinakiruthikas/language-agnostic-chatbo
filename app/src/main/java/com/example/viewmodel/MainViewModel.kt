package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.*
import com.example.data.repository.*
import com.example.service.GeminiServiceClient
import com.example.service.VoiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    application: Application,
    private val repository: ChatRepository
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val voiceManager = VoiceManager(context)

    companion object {
        private const val TAG = "MainViewModel"
        
        const val SYSTEM_INSTRUCTION = """
You are LinguaVoice AI, an advanced language-agnostic voice chatbot.
Core Behaviors:
1. DETECT THE USER'S LANGUAGE AUTOMATICALLY: You MUST respond in the EXACT SAME language used by the user in their query (e.g. if they text/speak in Tamil, reply in Tamil; if Hindi, reply in Hindi; if Spanish, reply in Spanish).
2. MULTILINGUAL CAPABILITY: You support and excel in English, Tamil, Hindi, Telugu, Malayalam, Kannada, Marathi, Bengali, Gujarati, Punjabi, Urdu, French, German, Spanish, Japanese, Chinese, Korean, Arabic, and more.
3. CONVERSATION CONTEXT: Keep track of previous dialog turns. If the user changes language mid-conversation, transition naturally into the new language while fully preserving context.
4. MULTIFUNCTIONAL TUTORING: Expertly solve educational tasks, write and explain clean code, conduct mock interviews, correct grammar, solve mathematics, and summarize document contents.
5. STYLE: Be extremely polite, professional, accurate, and conversational. Keep responses clear and formatted with markdown when helpful.
"""
    }

    // --- Authentication State ---
    private val _currentUserEmail = MutableStateFlow<String?>(null)
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // --- Active Chat Session States ---
    private val _activeSession = MutableStateFlow<ChatSessionEntity?>(null)
    val activeSession: StateFlow<ChatSessionEntity?> = _activeSession.asStateFlow()

    private val _chatSearchQuery = MutableStateFlow("")
    val chatSearchQuery: StateFlow<String> = _chatSearchQuery.asStateFlow()

    // Dynamically loaded lists
    val sessions: StateFlow<List<ChatSessionEntity>> = _currentUserEmail
        .flatMapLatest { email ->
            if (email != null) repository.getSessionsFlow(email) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMessages: StateFlow<List<ChatMessageEntity>> = _activeSession
        .flatMapLatest { session ->
            if (session != null) repository.getMessagesFlow(session.sessionId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- User Preferences State ---
    val userPreferences: StateFlow<UserPreferenceEntity?> = _currentUserEmail
        .flatMapLatest { email ->
            if (email != null) repository.getPreferenceFlow(email) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Attached File State ---
    private val _attachedFile = MutableStateFlow<AttachedFileInfo?>(null)
    val attachedFile: StateFlow<AttachedFileInfo?> = _attachedFile.asStateFlow()

    // --- AI Thinking State ---
    private val _isAITyping = MutableStateFlow(false)
    val isAITyping: StateFlow<Boolean> = _isAITyping.asStateFlow()

    // --- Speech State ---
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _ttsIsPlaying = MutableStateFlow(false)
    val ttsIsPlaying: StateFlow<Boolean> = _ttsIsPlaying.asStateFlow()

    private val _ttsIsPaused = MutableStateFlow(false)
    val ttsIsPaused: StateFlow<Boolean> = _ttsIsPaused.asStateFlow()

    private val _sttError = MutableStateFlow<String?>(null)
    val sttError: StateFlow<String?> = _sttError.asStateFlow()

    // --- Dashboard Statistics State ---
    private val _dashboardStats = MutableStateFlow<DashboardStats?>(null)
    val dashboardStats: StateFlow<DashboardStats?> = _dashboardStats.asStateFlow()

    init {
        // Collect preferences to configure TTS voice rate and pitch when changed
        viewModelScope.launch {
            userPreferences.collect { pref ->
                if (pref != null) {
                    voiceManager.setSpeechRate(pref.speechSpeed)
                    voiceManager.setSpeechPitch(pref.speechPitch)
                    if (pref.voiceName != "default") {
                        voiceManager.setVoice(pref.voiceName)
                    }
                }
            }
        }
    }

    // --- Auth Actions ---
    fun loginWithEmail(email: String, name: String) {
        viewModelScope.launch {
            _isLoggingIn.value = true
            _authError.value = null
            try {
                val formattedEmail = email.trim().lowercase()
                if (formattedEmail.isEmpty() || !formattedEmail.contains("@")) {
                    _authError.value = "Please enter a valid email address."
                    _isLoggingIn.value = false
                    return@launch
                }
                val nickname = name.trim().ifEmpty { formattedEmail.substringBefore("@") }
                val avatar = "https://api.dicebear.com/7.x/bottts/png?seed=$nickname"
                
                val user = repository.createOrUpdateUser(formattedEmail, nickname, avatar)
                _currentUser.value = user
                _currentUserEmail.value = formattedEmail
                loadDashboardStats()
            } catch (e: Exception) {
                _authError.value = "Login failed: ${e.localizedMessage}"
            } finally {
                _isLoggingIn.value = false
            }
        }
    }

    fun loginWithGoogleMock() {
        loginWithEmail("rathinakiruthikas02@gmail.com", "Kiruthika S")
    }

    fun loginAsGuest() {
        loginWithEmail("guest_user", "Guest User")
    }

    fun logout() {
        stopSpeaking()
        voiceManager.stopSpeaking()
        _currentUserEmail.value = null
        _currentUser.value = null
        _activeSession.value = null
        _dashboardStats.value = null
    }

    // --- Session Actions ---
    fun selectSession(session: ChatSessionEntity?) {
        _activeSession.value = session
        stopSpeaking()
    }

    fun createNewChat(title: String = "New Conversation", language: String = "Auto") {
        val email = _currentUserEmail.value ?: return
        viewModelScope.launch {
            val session = repository.createNewSession(email, title, language)
            selectSession(session)
            loadDashboardStats()
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_activeSession.value?.sessionId == sessionId) {
                _activeSession.value = null
            }
            loadDashboardStats()
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameSession(sessionId, newTitle)
            if (_activeSession.value?.sessionId == sessionId) {
                _activeSession.value = _activeSession.value?.copy(title = newTitle)
            }
        }
    }

    // --- Messaging and AI Response ---
    fun attachFile(uri: Uri, name: String, type: String) {
        _attachedFile.value = AttachedFileInfo(uri = uri.toString(), name = name, type = type)
    }

    fun clearAttachedFile() {
        _attachedFile.value = null
    }

    fun sendMessage(text: String) {
        val session = _activeSession.value ?: return
        val trimmedText = text.trim()
        val attachment = _attachedFile.value
        
        if (trimmedText.isEmpty() && attachment == null) return

        viewModelScope.launch {
            // Stop any ongoing speech
            stopSpeaking()

            // 1. Insert user message to database
            val userMsg = repository.insertMessage(
                sessionId = session.sessionId,
                role = "user",
                content = trimmedText,
                attachedFileUri = attachment?.uri,
                attachedFileName = attachment?.name,
                attachedFileType = attachment?.type
            )
            clearAttachedFile()

            _isAITyping.value = true

            // Automatically name the chat if it is the first message
            val currentMessages = repository.getMessagesSync(session.sessionId)
            if (currentMessages.size <= 1 && trimmedText.isNotEmpty()) {
                val briefTitle = if (trimmedText.length > 20) trimmedText.substring(0, 18) + "..." else trimmedText
                renameSession(session.sessionId, briefTitle)
            }

            // 2. Fetch history context and call Gemini API
            val responseText = GeminiServiceClient.generateResponse(
                context = context,
                history = currentMessages,
                systemInstruction = SYSTEM_INSTRUCTION
            )

            // 3. Save AI response message
            val detectedLang = detectLanguageLocale(responseText)
            val aiMsg = repository.insertMessage(
                sessionId = session.sessionId,
                role = "model",
                content = responseText,
                detectedLanguage = detectedLang
            )

            _isAITyping.value = false
            loadDashboardStats()

            // 4. If auto read is enabled, automatically read responses
            val pref = userPreferences.value
            if (pref?.autoReadResponses == true) {
                speakMessage(aiMsg.content, detectedLang)
            }
        }
    }

    fun regenerateResponse() {
        val session = _activeSession.value ?: return
        viewModelScope.launch {
            val messages = repository.getMessagesSync(session.sessionId)
            if (messages.isEmpty()) return@launch

            // Find last user message to base the regeneration on
            val lastUserMsgIdx = messages.indexOfLast { it.role == "user" }
            if (lastUserMsgIdx == -1) return@launch

            // Delete all messages after that user message
            for (i in messages.size - 1 downTo lastUserMsgIdx + 1) {
                repository.deleteMessage(messages[i].messageId)
            }

            // Now call sendMessage with user query again
            val lastUserMsg = messages[lastUserMsgIdx]
            _isAITyping.value = true

            // Get shortened history
            val shortenedHistory = messages.subList(0, lastUserMsgIdx + 1)
            val responseText = GeminiServiceClient.generateResponse(
                context = context,
                history = shortenedHistory,
                systemInstruction = SYSTEM_INSTRUCTION
            )

            val detectedLang = detectLanguageLocale(responseText)
            val aiMsg = repository.insertMessage(
                sessionId = session.sessionId,
                role = "model",
                content = responseText,
                detectedLanguage = detectedLang
            )

            _isAITyping.value = false
            loadDashboardStats()

            val pref = userPreferences.value
            if (pref?.autoReadResponses == true) {
                speakMessage(aiMsg.content, detectedLang)
            }
        }
    }

    // --- Voice Input (STT) ---
    fun startSpeechToText(langCode: String = "en") {
        _sttError.value = null
        _isRecording.value = true
        voiceManager.startListening(
            languageCode = langCode,
            onReadyForSpeech = { Log.d(TAG, "STT Ready") },
            onBeginningOfSpeech = { Log.d(TAG, "STT Listening started") },
            onEndOfSpeech = { _isRecording.value = false },
            onResult = { recognizedText ->
                _isRecording.value = false
                if (recognizedText.isNotBlank()) {
                    sendMessage(recognizedText)
                }
            },
            onError = { error ->
                _isRecording.value = false
                _sttError.value = error
                Log.e(TAG, "STT Error: $error")
            }
        )
    }

    fun stopSpeechToText() {
        voiceManager.stopListening()
        _isRecording.value = false
    }

    // --- Voice Output (TTS) ---
    fun speakMessage(text: String, languageCode: String = "en") {
        _ttsIsPlaying.value = true
        _ttsIsPaused.value = false
        voiceManager.speak(text, languageCode) {
            // On complete callback
            _ttsIsPlaying.value = false
            _ttsIsPaused.value = false
        }
    }

    fun pauseSpeaking() {
        voiceManager.pauseSpeaking()
        _ttsIsPlaying.value = false
        _ttsIsPaused.value = true
    }

    fun resumeSpeaking() {
        _ttsIsPlaying.value = true
        _ttsIsPaused.value = false
        voiceManager.resumeSpeaking {
            _ttsIsPlaying.value = false
            _ttsIsPaused.value = false
        }
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
        _ttsIsPlaying.value = false
        _ttsIsPaused.value = false
    }

    fun getAvailableVoices(): List<String> = voiceManager.getAvailableVoices()

    // --- Preferences Actions ---
    fun updateThemeMode(isDarkMode: Boolean) {
        val pref = userPreferences.value ?: return
        viewModelScope.launch {
            repository.savePreference(pref.copy(isDarkMode = isDarkMode))
        }
    }

    fun updateAccentColor(colorHex: String) {
        val pref = userPreferences.value ?: return
        viewModelScope.launch {
            repository.savePreference(pref.copy(accentColorHex = colorHex))
        }
    }

    fun updateFontSize(multiplier: Float) {
        val pref = userPreferences.value ?: return
        viewModelScope.launch {
            repository.savePreference(pref.copy(fontSizeMultiplier = multiplier))
        }
    }

    fun updateVoiceConfig(voiceName: String, speed: Float, pitch: Float) {
        val pref = userPreferences.value ?: return
        viewModelScope.launch {
            repository.savePreference(
                pref.copy(
                    voiceName = voiceName,
                    speechSpeed = speed,
                    speechPitch = pitch
                )
            )
        }
    }

    fun updateReadPreferences(autoRead: Boolean, autoDetect: Boolean) {
        val pref = userPreferences.value ?: return
        viewModelScope.launch {
            repository.savePreference(
                pref.copy(
                    autoReadResponses = autoRead,
                    autoDetectLanguage = autoDetect
                )
            )
        }
    }

    // --- Statistics Helper ---
    fun loadDashboardStats() {
        val email = _currentUserEmail.value ?: return
        viewModelScope.launch {
            val stats = repository.getDashboardStats(email)
            _dashboardStats.value = stats
        }
    }

    // --- Clean Up ---
    override fun onCleared() {
        super.onCleared()
        voiceManager.shutdown()
    }

    // --- Simple Rule-Based Locale Predictor for TTS Language Match ---
    private fun detectLanguageLocale(text: String): String {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return "en"

        // Check for unicode scripts to map major supported languages
        return when {
            // Tamil range: \u0B80-\u0BFF
            cleaned.any { it in '\u0B80'..'\u0BFF' } -> "ta"
            // Telugu range: \u0C00-\u0C7F
            cleaned.any { it in '\u0C00'..'\u0C7F' } -> "te"
            // Malayalam range: \u0D00-\u0D7F
            cleaned.any { it in '\u0D00'..'\u0D7F' } -> "ml"
            // Kannada range: \u0C80-\u0CFF
            cleaned.any { it in '\u0C80'..'\u0CFF' } -> "kn"
            // Bengali range: \u0980-\u09FF
            cleaned.any { it in '\u0980'..'\u09FF' } -> "bn"
            // Gujarati range: \u0A80-\u0AFF
            cleaned.any { it in '\u0A80'..'\u0AFF' } -> "gu"
            // Devanagari range (Hindi/Marathi): \u0900-\u097F
            cleaned.any { it in '\u0900'..'\u097F' } -> "hi" // Hindi default for Devanagari
            // Arabic range (Arabic/Urdu): \u0600-\u06FF
            cleaned.any { it in '\u0600'..'\u06FF' } -> "ar"
            // Japanese (Hiragana/Katakana/Kanji)
            cleaned.any { it in '\u0430'..'\u04FF' } -> "ja"
            cleaned.any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' || it in '\u4E00'..'\u9FFF' } -> "ja"
            // Korean: \uAC00-\uD7AF
            cleaned.any { it in '\uAC00'..'\uD7AF' } -> "ko"
            else -> "en"
        }
    }
}

data class AttachedFileInfo(
    val uri: String,
    val name: String,
    val type: String
)

// Factory class to instantiate our ViewModel
class MainViewModelFactory(
    private val application: Application,
    private val repository: ChatRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
