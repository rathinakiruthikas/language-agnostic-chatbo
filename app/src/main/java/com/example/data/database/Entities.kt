package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val email: String, // "guest" for Guest Mode
    val name: String,
    val avatarUrl: String,
    val joinedDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val sessionId: String,
    val userId: String, // foreign key relation to users.email
    val title: String,
    val createdTime: Long = System.currentTimeMillis(),
    val lastUpdatedTime: Long = System.currentTimeMillis(),
    val targetLanguage: String = "Auto" // "Auto" or specific language
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val messageId: String,
    val sessionId: String,
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachedFileUri: String? = null,
    val attachedFileName: String? = null,
    val attachedFileType: String? = null, // "image", "txt", "pdf", "docx"
    val detectedLanguage: String? = null,
    val isSpeaking: Boolean = false // UI state
)

@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey val userId: String, // maps to users.email
    val isDarkMode: Boolean = true,
    val accentColorHex: String = "#00F5FF", // Cyan accent by default
    val fontSizeMultiplier: Float = 1.0f,
    val voiceName: String = "default",
    val speechSpeed: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val autoReadResponses: Boolean = false,
    val autoDetectLanguage: Boolean = true
)
