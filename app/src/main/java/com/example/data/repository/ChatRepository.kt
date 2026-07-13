package com.example.data.repository

import com.example.data.database.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class ChatRepository(
    private val userDao: UserDao,
    private val chatDao: ChatDao,
    private val preferenceDao: PreferenceDao
) {
    // --- User Operations ---
    suspend fun createOrUpdateUser(email: String, name: String, avatarUrl: String): UserEntity {
        val user = UserEntity(email = email, name = name, avatarUrl = avatarUrl)
        userDao.insertUser(user)
        
        // Also ensure default preference exists for this user
        val existingPref = preferenceDao.getPreferenceSync(email)
        if (existingPref == null) {
            preferenceDao.insertPreference(UserPreferenceEntity(userId = email))
        }
        return user
    }

    fun getUserFlow(email: String): Flow<UserEntity?> = userDao.getUserFlow(email)

    suspend fun getUserSync(email: String): UserEntity? = userDao.getUserSync(email)

    // --- Chat Session Operations ---
    fun getSessionsFlow(userId: String): Flow<List<ChatSessionEntity>> = chatDao.getSessionsFlow(userId)

    suspend fun createNewSession(userId: String, title: String, language: String = "Auto"): ChatSessionEntity {
        val session = ChatSessionEntity(
            sessionId = UUID.randomUUID().toString(),
            userId = userId,
            title = title,
            targetLanguage = language
        )
        chatDao.insertSession(session)
        return session
    }

    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteMessagesInSession(sessionId)
        chatDao.deleteSession(sessionId)
    }

    suspend fun renameSession(sessionId: String, title: String) {
        chatDao.renameSession(sessionId, title)
    }

    suspend fun updateSessionTime(sessionId: String) {
        chatDao.updateSessionTime(sessionId)
    }

    // --- Message Operations ---
    fun getMessagesFlow(sessionId: String): Flow<List<ChatMessageEntity>> = chatDao.getMessagesFlow(sessionId)

    suspend fun getMessagesSync(sessionId: String): List<ChatMessageEntity> = chatDao.getMessagesSync(sessionId)

    suspend fun insertMessage(
        sessionId: String,
        role: String,
        content: String,
        attachedFileUri: String? = null,
        attachedFileName: String? = null,
        attachedFileType: String? = null,
        detectedLanguage: String? = null
    ): ChatMessageEntity {
        val message = ChatMessageEntity(
            messageId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            content = content,
            attachedFileUri = attachedFileUri,
            attachedFileName = attachedFileName,
            attachedFileType = attachedFileType,
            detectedLanguage = detectedLanguage
        )
        chatDao.insertMessage(message)
        chatDao.updateSessionTime(sessionId)
        return message
    }

    suspend fun deleteMessage(messageId: String) {
        chatDao.deleteMessageById(messageId)
    }

    // --- User Preferences Operations ---
    fun getPreferenceFlow(userId: String): Flow<UserPreferenceEntity?> = preferenceDao.getPreferenceFlow(userId)

    suspend fun getPreferenceSync(userId: String): UserPreferenceEntity? = preferenceDao.getPreferenceSync(userId)

    suspend fun savePreference(preference: UserPreferenceEntity) {
        preferenceDao.insertPreference(preference)
    }

    // --- Statistics / Analytics ---
    suspend fun getDashboardStats(userId: String): DashboardStats {
        val sessions = chatDao.getSessionsSync(userId)
        val totalConversations = sessions.size
        
        var totalMessages = 0
        val langCountMap = mutableMapOf<String, Int>()
        
        for (session in sessions) {
            val messages = chatDao.getMessagesSync(session.sessionId)
            totalMessages += messages.size
            messages.forEach { msg ->
                if (msg.role == "user") {
                    val lang = msg.detectedLanguage ?: session.targetLanguage
                    if (lang != "Auto") {
                        langCountMap[lang] = (langCountMap[lang] ?: 0) + 1
                    }
                }
            }
        }
        
        val mostUsedLanguage = langCountMap.maxByOrNull { it.value }?.key ?: "English"
        val averageResponseTime = if (totalConversations > 0) 1.25f else 0.0f // Simulated baseline average response time in seconds
        
        // Generate daily usage over past 5 days
        val usageData = List(5) { index ->
            val dayName = when (index) {
                0 -> "Mon"
                1 -> "Tue"
                2 -> "Wed"
                3 -> "Thu"
                else -> "Fri"
            }
            // Dynamic/simulated distribution based on actual message volume
            val ratio = when (index) {
                0 -> 0.15f
                1 -> 0.25f
                2 -> 0.20f
                3 -> 0.30f
                else -> 0.10f
            }
            BarChartItem(dayName, (totalMessages * ratio).coerceAtLeast(1f).toInt())
        }

        return DashboardStats(
            totalConversations = totalConversations,
            totalMessages = totalMessages,
            mostUsedLanguage = mostUsedLanguage,
            averageResponseTimeSec = averageResponseTime,
            usageChartData = usageData
        )
    }
}

data class DashboardStats(
    val totalConversations: Int,
    val totalMessages: Int,
    val mostUsedLanguage: String,
    val averageResponseTimeSec: Float,
    val usageChartData: List<BarChartItem>
)

data class BarChartItem(
    val label: String,
    val value: Int
)
