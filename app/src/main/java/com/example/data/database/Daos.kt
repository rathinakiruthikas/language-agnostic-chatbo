package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    fun getUserFlow(email: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserSync(email: String): UserEntity?
}

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("UPDATE chat_sessions SET title = :title, lastUpdatedTime = :time WHERE sessionId = :sessionId")
    suspend fun renameSession(sessionId: String, title: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET lastUpdatedTime = :time WHERE sessionId = :sessionId")
    suspend fun updateSessionTime(sessionId: String, time: Long = System.currentTimeMillis())

    @Query("SELECT * FROM chat_sessions WHERE userId = :userId ORDER BY lastUpdatedTime DESC")
    fun getSessionsFlow(userId: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE userId = :userId ORDER BY lastUpdatedTime DESC")
    suspend fun getSessionsSync(userId: String): List<ChatSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesFlow(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesSync(sessionId: String): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesInSession(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE messageId = :messageId")
    suspend fun deleteMessageById(messageId: String)
}

@Dao
interface PreferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreference(preference: UserPreferenceEntity)

    @Query("SELECT * FROM user_preferences WHERE userId = :userId LIMIT 1")
    fun getPreferenceFlow(userId: String): Flow<UserPreferenceEntity?>

    @Query("SELECT * FROM user_preferences WHERE userId = :userId LIMIT 1")
    suspend fun getPreferenceSync(userId: String): UserPreferenceEntity?
}
