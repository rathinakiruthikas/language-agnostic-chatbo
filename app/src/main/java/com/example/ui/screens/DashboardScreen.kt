package com.example.ui.screens

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.database.ChatMessageEntity
import com.example.data.database.ChatSessionEntity
import com.example.data.repository.DashboardStats
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AttachedFileInfo
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class ActiveScreen {
    CHAT, ANALYTICS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Core states collected from ViewModel
    val currentUser by viewModel.currentUser.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val activeMessages by viewModel.activeMessages.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val isAITyping by viewModel.isAITyping.collectAsState()
    val attachedFile by viewModel.attachedFile.collectAsState()
    
    // STT / TTS Voice states
    val isRecording by viewModel.isRecording.collectAsState()
    val ttsIsPlaying by viewModel.ttsIsPlaying.collectAsState()
    val ttsIsPaused by viewModel.ttsIsPaused.collectAsState()
    val sttError by viewModel.sttError.collectAsState()
    
    // Statistics
    val stats by viewModel.dashboardStats.collectAsState()

    // Screen navigation
    var currentPanel by remember { mutableStateOf(ActiveScreen.CHAT) }
    
    // UI states
    var isDrawerOpen by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var chatSearchInput by remember { mutableStateOf("") }
    var textInput by remember { mutableStateOf("") }
    
    // Dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameSessionTarget by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var renameSessionText by remember { mutableStateOf("") }

    // Scroll state for automatic scrolling to bottom on new messages
    val listState = rememberLazyListState()

    LaunchedEffect(activeMessages.size, isAITyping) {
        if (activeMessages.isNotEmpty()) {
            listState.animateScrollToItem(activeMessages.size - 1)
        }
    }

    LaunchedEffect(sttError) {
        if (sttError != null) {
            Toast.makeText(context, "STT Error: $sttError", Toast.LENGTH_SHORT).show()
        }
    }

    // Load stats once upon entry
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            viewModel.loadDashboardStats()
        }
    }

    // Launcher for selecting Image attachments
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileNameFromUri(context, uri) ?: "image.jpg"
            viewModel.attachFile(uri, fileName, "image/jpeg")
        }
    }

    // Launcher for selecting Text / Document attachments
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileNameFromUri(context, uri) ?: "document.txt"
            val type = if (fileName.endsWith(".pdf")) "application/pdf" else "text/plain"
            viewModel.attachFile(uri, fileName, type)
        }
    }

    // Accent and Theme parsing
    val accentHex = userPreferences?.accentColorHex ?: "#00F5FF"
    val accentColor = Color(android.graphics.Color.parseColor(accentHex))
    val isDark = userPreferences?.isDarkMode ?: true
    val fontScale = userPreferences?.fontSizeMultiplier ?: 1.0f

    MyApplicationTheme(
        darkTheme = isDark,
        accentColorHex = accentHex
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (currentPanel == ActiveScreen.ANALYTICS) {
                                    "Analytics Dashboard"
                                } else {
                                    activeSession?.title ?: "Select Conversation"
                                },
                                fontSize = (18 * fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentPanel == ActiveScreen.CHAT && activeSession != null) {
                                Text(
                                    text = "Target Lang: ${activeSession?.targetLanguage ?: "Auto"}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { isDrawerOpen = true },
                            modifier = Modifier.testTag("drawer_menu_button")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Drawer")
                        }
                    },
                    actions = {
                        if (currentPanel == ActiveScreen.CHAT && activeSession != null) {
                            // Print/Export Menu
                            Box {
                                var showMenu by remember { mutableStateOf(false) }
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.Share, contentDescription = "Export Chat")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Export as TXT") },
                                        onClick = {
                                            showMenu = false
                                            exportChatAsTxt(context, activeSession?.title ?: "chat", activeMessages)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export as Markdown") },
                                        onClick = {
                                            showMenu = false
                                            exportChatAsMd(context, activeSession?.title ?: "chat", activeMessages)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Print Chat / Save PDF") },
                                        onClick = {
                                            showMenu = false
                                            exportChatAsPdf(context, activeSession?.title ?: "chat", activeMessages)
                                        }
                                    )
                                }
                            }

                            // Rename Chat option
                            IconButton(
                                onClick = {
                                    renameSessionTarget = activeSession
                                    renameSessionText = activeSession?.title ?: ""
                                    showRenameDialog = true
                                }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename Conversation")
                            }

                            // Delete current chat
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Conversation", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        // Theme Quick Toggle
                        IconButton(onClick = { viewModel.updateThemeMode(!isDark) }) {
                            Icon(
                                imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme"
                            )
                        }

                        // Statistics Panel Toggle
                        IconButton(
                            onClick = {
                                currentPanel = if (currentPanel == ActiveScreen.CHAT) {
                                    viewModel.loadDashboardStats()
                                    ActiveScreen.ANALYTICS
                                } else {
                                    ActiveScreen.CHAT
                                }
                            },
                            modifier = Modifier.testTag("stats_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (currentPanel == ActiveScreen.ANALYTICS) Icons.Default.ChatBubble else Icons.Default.BarChart,
                                contentDescription = "Toggle View",
                                tint = if (currentPanel == ActiveScreen.ANALYTICS) accentColor else LocalContentColor.current
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.45f)
                    )
                )
            },
            bottomBar = {
                if (currentPanel == ActiveScreen.CHAT && activeSession != null) {
                    ChatInputBar(
                        textValue = textInput,
                        onValueChange = { textInput = it },
                        attachedFile = attachedFile,
                        onClearAttachment = { viewModel.clearAttachedFile() },
                        onImageAttach = { imagePickerLauncher.launch("image/*") },
                        onDocAttach = { documentPickerLauncher.launch("*/*") },
                        onMicClick = {
                            if (isRecording) {
                                viewModel.stopSpeechToText()
                            } else {
                                val lang = activeSession?.targetLanguage ?: "Auto"
                                val sttCode = if (lang == "Auto") "en" else getLangCodeFromName(lang)
                                viewModel.startSpeechToText(sttCode)
                            }
                        },
                        isRecording = isRecording,
                        onSend = {
                            viewModel.sendMessage(textInput)
                            textInput = ""
                        },
                        accentColor = accentColor
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Frosted Glass Mesh Backdrop
                FloatingParticlesBackground()

                // --- Active Screen Routing ---
                when (currentPanel) {
                    ActiveScreen.CHAT -> {
                        if (activeSession == null) {
                            // Empty state: Select or Create a chat
                            EmptyChatState(
                                onCreateChat = {
                                    viewModel.createNewChat("Conversation ${sessions.size + 1}")
                                },
                                accentColor = accentColor
                            )
                        } else {
                            // Chat flow
                            ChatMessagesList(
                                messages = activeMessages,
                                listState = listState,
                                isAITyping = isAITyping,
                                viewModel = viewModel,
                                accentColor = accentColor,
                                fontScale = fontScale
                            )
                        }
                    }
                    ActiveScreen.ANALYTICS -> {
                        AnalyticsDashboard(
                            stats = stats,
                            accentColor = accentColor,
                            fontScale = fontScale
                        )
                    }
                }

                // --- Lateral Sliding Navigation Drawer ---
                AnimatedVisibility(
                    visible = isDrawerOpen,
                    enter = slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn(),
                    exit = slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut()
                ) {
                    DrawerSidebar(
                        sessions = sessions,
                        activeSession = activeSession,
                        currentUser = currentUser,
                        chatSearchQuery = chatSearchInput,
                        onSearchChange = { chatSearchInput = it },
                        onSessionSelect = {
                            viewModel.selectSession(it)
                            currentPanel = ActiveScreen.CHAT
                            isDrawerOpen = false
                        },
                        onCreateNewChat = {
                            isDrawerOpen = false
                            currentPanel = ActiveScreen.CHAT
                            viewModel.createNewChat("Chat ${sessions.size + 1}")
                        },
                        onOpenSettings = {
                            isDrawerOpen = false
                            showSettingsSheet = true
                        },
                        onLogout = {
                            viewModel.logout()
                            onLogout()
                        },
                        onDismiss = { isDrawerOpen = false },
                        accentColor = accentColor,
                        fontScale = fontScale
                    )
                }

                // --- Voice speech overlay when recording is active ---
                if (isRecording) {
                    RecordingOverlay(
                        onStopRecording = { viewModel.stopSpeechToText() },
                        accentColor = accentColor
                    )
                }
            }
        }

        // --- Custom Bottom Sheet for Settings ---
        if (showSettingsSheet) {
            SettingsDialog(
                viewModel = viewModel,
                userPreferences = userPreferences,
                accentColor = accentColor,
                onDismiss = { showSettingsSheet = false }
            )
        }

        // --- Rename Dialog ---
        if (showRenameDialog && renameSessionTarget != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Chat") },
                text = {
                    OutlinedTextField(
                        value = renameSessionText,
                        onValueChange = { renameSessionText = it },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.renameSession(renameSessionTarget!!.sessionId, renameSessionText)
                            showRenameDialog = false
                            renameSessionTarget = null
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // --- Delete Confirmation Dialog ---
        if (showDeleteDialog && activeSession != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Chat?") },
                text = { Text("Are you sure you want to delete this conversation history? This cannot be undone.") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            viewModel.deleteSession(activeSession!!.sessionId)
                            showDeleteDialog = false
                        }
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// --- Empty Chat Composable ---
@Composable
fun EmptyChatState(
    onCreateChat: () -> Unit,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(accentColor.copy(alpha = 0.08f), CircleShape)
                .border(2.dp, accentColor.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Forum,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(48.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Start a New Chat",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Text(
            text = "Initiate an automated translation assistant. Google Gemini will automatically detect your language and reply dynamically.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(top = 8.dp, bottom = 32.dp)
        )

        Button(
            onClick = onCreateChat,
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .height(52.dp)
                .testTag("new_chat_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Conversation", fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

// --- Chat Log Feeds ---
@Composable
fun ChatMessagesList(
    messages: List<ChatMessageEntity>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isAITyping: Boolean,
    viewModel: MainViewModel,
    accentColor: Color,
    fontScale: Float
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        items(messages) { msg ->
            val isUser = msg.role == "user"
            val isDark = MaterialTheme.colorScheme.background.run { (red + green + blue) < 1.5f }
            val bubbleBg = if (isUser) {
                accentColor.copy(alpha = 0.85f)
            } else {
                if (isDark) Color(0x19FFFFFF) else Color(0x0C000000)
            }
            val bubbleBorder = if (isUser) {
                accentColor.copy(alpha = 0.25f)
            } else {
                if (isDark) Color(0x26FFFFFF) else Color(0x16000000)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                if (!isUser) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(accentColor.copy(alpha = 0.1f), CircleShape)
                            .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column(
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Chat bubble
                    Box(
                        modifier = Modifier
                            .background(
                                color = bubbleBg,
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                )
                            )
                            .border(
                                1.dp,
                                bubbleBorder,
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column {
                            // If user uploaded a document/image, show badge
                            if (msg.attachedFileName != null) {
                                Row(
                                    modifier = Modifier
                                        .padding(bottom = 8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (msg.attachedFileType?.startsWith("image") == true) Icons.Default.Image else Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = accentColor
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = msg.attachedFileName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Text(
                                text = msg.content,
                                fontSize = (14 * fontScale).sp,
                                color = if (isUser) Color.White else MaterialTheme.colorScheme.onBackground,
                                fontFamily = FontFamily.SansSerif,
                                lineHeight = (20 * fontScale).sp
                            )
                        }
                    }

                    // Bottom utility buttons (Copy, Speak, Regenerate)
                    Row(
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimestamp(msg.timestamp),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )

                        // Copy button
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy text",
                            modifier = Modifier
                                .size(14.dp)
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(msg.content))
                                    Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )

                        // TTS Speech speaker icon (only for AI responses)
                        if (!isUser) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Read aloud",
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable {
                                        viewModel.speakMessage(msg.content, msg.detectedLanguage ?: "en")
                                    },
                                tint = accentColor.copy(alpha = 0.7f)
                            )
                        }

                        // Regenerate button (last AI response option)
                        if (!isUser && msg == messages.lastOrNull { it.role == "model" }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate answer",
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable {
                                        viewModel.regenerateResponse()
                                    },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                if (isUser) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (isAITyping) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(accentColor.copy(alpha = 0.1f), CircleShape)
                            .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    // Thinking Bubble
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        ThinkingAnimation(accentColor = accentColor)
                    }
                }
            }
        }
    }
}

// --- Dynamic AI Typing Animation ---
@Composable
fun ThinkingAnimation(accentColor: Color) {
    val transition = rememberInfiniteTransition(label = "dots")
    
    @Composable
    fun animatedDotAlpha(delayMillis: Int): Float {
        val alpha by transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1200
                    0.2f at delayMillis
                    1f at (delayMillis + 300)
                    0.2f at (delayMillis + 600)
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "dot"
        )
        return alpha
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor.copy(alpha = animatedDotAlpha(0))))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor.copy(alpha = animatedDotAlpha(300))))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor.copy(alpha = animatedDotAlpha(600))))
    }
}

// --- Speech dictation Recording Dialog Overlay ---
@Composable
fun RecordingOverlay(
    onStopRecording: () -> Unit,
    accentColor: Color
) {
    val transition = rememberInfiniteTransition(label = "recording_pulse")
    val scale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(24.dp)
                )
                .border(1.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                .padding(40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                // Wave pulse background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(scale * 45f)
                        .background(accentColor.copy(alpha = 0.15f * (2f - scale)), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size((60 * scale).dp)
                        .background(accentColor.copy(alpha = 0.12f), CircleShape)
                )
                // Microphone icon button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(accentColor, CircleShape)
                        .clickable { onStopRecording() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Stop",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Listening Natively...",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "Speak clearly. Automatic language translation will trigger on stop.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            OutlinedButton(
                onClick = onStopRecording,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Text("Stop Recording", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// --- Chat Input Composable ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    textValue: String,
    onValueChange: (String) -> Unit,
    attachedFile: AttachedFileInfo?,
    onClearAttachment: () -> Unit,
    onImageAttach: () -> Unit,
    onDocAttach: () -> Unit,
    onMicClick: () -> Unit,
    isRecording: Boolean,
    onSend: () -> Unit,
    accentColor: Color
) {
    val isDark = MaterialTheme.colorScheme.background.run { (red + green + blue) < 1.5f }
    val footerBg = if (isDark) Color(0x1C0F172A) else Color(0x1CDFE4EC)
    val glassInputBg = if (isDark) Color(0x14FFFFFF) else Color(0x0C000000)
    val glassInputBorder = if (isDark) Color(0x1CFFFFFF) else Color(0x1A000000)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(footerBg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Render Active File attachment preview badge if present
        if (attachedFile != null) {
            Row(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .background(
                        accentColor.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
                    .border(1.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (attachedFile.type.startsWith("image")) Icons.Default.Image else Icons.Default.Description,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = attachedFile.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear attachment",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onClearAttachment() }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attachment launcher options
            IconButton(onClick = onImageAttach) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach Document", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            IconButton(onClick = onDocAttach) {
                Icon(Icons.Default.Image, contentDescription = "Attach Image", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Input TextField
            OutlinedTextField(
                value = textValue,
                onValueChange = onValueChange,
                placeholder = { Text("Ask LinguaVoice...", fontSize = 14.sp) },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = glassInputBorder,
                    focusedContainerColor = glassInputBg,
                    unfocusedContainerColor = glassInputBg
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_text_field"),
                trailingIcon = {
                    IconButton(
                        onClick = onMicClick,
                        modifier = Modifier.testTag("mic_button")
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.StopCircle else Icons.Default.Mic,
                            contentDescription = "Speech Record",
                            tint = if (isRecording) MaterialTheme.colorScheme.error else accentColor
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send icon
            IconButton(
                onClick = onSend,
                enabled = textValue.isNotBlank() || attachedFile != null,
                modifier = Modifier
                    .background(
                        if (textValue.isNotBlank() || attachedFile != null) accentColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        CircleShape
                    )
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send Message",
                    tint = if (textValue.isNotBlank() || attachedFile != null) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
}

// --- Lateral Drawer Sidebar ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerSidebar(
    sessions: List<ChatSessionEntity>,
    activeSession: ChatSessionEntity?,
    currentUser: com.example.data.database.UserEntity?,
    chatSearchQuery: String,
    onSearchChange: (String) -> Unit,
    onSessionSelect: (ChatSessionEntity) -> Unit,
    onCreateNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color,
    fontScale: Float
) {
    // Backdrop click dismisses drawer
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() }
    ) {
        // Left Column body
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.82f)
                .widthIn(max = 340.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                .clickable(
                    enabled = false,
                    onClick = {}
                ) // Prevent click through
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            // Brand title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Hearing,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "LinguaVoice AI",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Create New Conversation Button
            Button(
                onClick = onCreateNewChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("drawer_new_chat_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Conversation", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search History Input
            OutlinedTextField(
                value = chatSearchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search chats...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Recent Conversations",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Chat History sessions scrollable list
            val filteredSessions = sessions.filter {
                it.title.lowercase().contains(chatSearchQuery.lowercase())
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredSessions) { session ->
                    val isActive = activeSession?.sessionId == session.sessionId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isActive) accentColor.copy(alpha = 0.08f) else Color.Transparent
                            )
                            .border(
                                1.dp,
                                if (isActive) accentColor.copy(alpha = 0.25f) else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSessionSelect(session) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = session.title,
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) accentColor else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))

            // Settings clicker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Settings & Voice Config", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // User Info Footer block
            if (currentUser != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = currentUser.avatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentUser.email,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier.size(32.dp).testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- Analytics Dashboard Panel ---
@Composable
fun AnalyticsDashboard(
    stats: DashboardStats?,
    accentColor: Color,
    fontScale: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "AI Usage Analytics",
            fontSize = (22 * fontScale).sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            modifier = Modifier.align(Alignment.Start)
        )
        Text(
            text = "Activity logs and conversational statistics calculated natively.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.Start)
                .padding(top = 2.dp, bottom = 24.dp)
        )

        if (stats == null) {
            CircularProgressIndicator(color = accentColor, modifier = Modifier.padding(48.dp))
        } else {
            // Metrics grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    title = "Total Chats",
                    value = stats.totalConversations.toString(),
                    icon = Icons.Default.Forum,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Total Messages",
                    value = stats.totalMessages.toString(),
                    icon = Icons.Default.Message,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    title = "Top Language",
                    value = stats.mostUsedLanguage,
                    icon = Icons.Default.Translate,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Avg Latency",
                    value = "${stats.averageResponseTimeSec}s",
                    icon = Icons.Default.Timer,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Custom Canvas Bar Chart Drawing ---
            Text(
                text = "Conversational Distribution (Past 5 Days)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp)
            ) {
                val chartItems = stats.usageChartData
                val maxVal = (chartItems.maxOfOrNull { it.value } ?: 10).coerceAtLeast(1)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    val paddingLeftRight = 20f
                    val chartHeight = h - 50f
                    val barWidth = 45f
                    val totalBars = chartItems.size
                    
                    val spacing = (w - (paddingLeftRight * 2) - (barWidth * totalBars)) / (totalBars - 1)

                    chartItems.forEachIndexed { i, item ->
                        val leftX = paddingLeftRight + i * (barWidth + spacing)
                        val ratio = item.value.toFloat() / maxVal.toFloat()
                        val barHeight = chartHeight * ratio
                        val topY = chartHeight - barHeight

                        // Draw background bar tracks
                        drawRoundRect(
                            color = accentColor.copy(alpha = 0.05f),
                            topLeft = Offset(leftX, 0f),
                            size = Size(barWidth, chartHeight),
                            cornerRadius = CornerRadius(8f, 8f)
                        )

                        // Draw actual message volume bars with custom accent gradient
                        drawRoundRect(
                            color = accentColor.copy(alpha = 0.85f),
                            topLeft = Offset(leftX, topY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }
                }

                // Bar Labels text overlays (simulated alignment overlays)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    chartItems.forEach { item ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(45.dp)
                        ) {
                            Text(
                                text = item.value.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                            Text(
                                text = item.label,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.run { (red + green + blue) < 1.5f }
    val glassBg = if (isDark) Color(0x14FFFFFF) else Color(0x0D000000)
    val glassBorder = if (isDark) Color(0x1AFFFFFF) else Color(0x12000000)

    Card(
        modifier = modifier
            .border(
                1.dp,
                glassBorder,
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = glassBg
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// --- Settings Dialog Dialog ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: MainViewModel,
    userPreferences: com.example.data.database.UserPreferenceEntity?,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val accentList = listOf(
        "#00F5FF" to "Cyan",
        "#FFFF007F" to "Pink",
        "#9D4EDD" to "Purple",
        "#39FF14" to "Green",
        "#FFFF6B35" to "Orange"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Preferences", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (userPreferences != null) {
                    // Dark Mode toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Dark Theme", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Switch app appearance", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = userPreferences.isDarkMode,
                            onCheckedChange = { viewModel.updateThemeMode(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = accentColor)
                        )
                    }

                    // Accent Colors Row
                    Column {
                        Text("Theme Accent Color", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            accentList.forEach { (hex, name) ->
                                val active = userPreferences.accentColorHex.lowercase() == hex.lowercase()
                                val col = Color(android.graphics.Color.parseColor(hex))
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(col, CircleShape)
                                        .border(
                                            2.dp,
                                            if (active) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            CircleShape
                                        )
                                        .clickable { viewModel.updateAccentColor(hex) }
                                )
                            }
                        }
                    }

                    // Font Multiplier
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Typography Font Size", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${(userPreferences.fontSizeMultiplier * 100).toInt()}%", fontSize = 12.sp, color = accentColor, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = userPreferences.fontSizeMultiplier,
                            onValueChange = { viewModel.updateFontSize(it) },
                            valueRange = 0.8f..1.4f,
                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // Text-To-Speech Speech configurations
                    Text("Voice Speech Output Config", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accentColor)

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Speech Playback Speed", fontSize = 13.sp)
                            Text("${String.format("%.2f", userPreferences.speechSpeed)}x", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentColor)
                        }
                        Slider(
                            value = userPreferences.speechSpeed,
                            onValueChange = { viewModel.updateVoiceConfig(userPreferences.voiceName, it, userPreferences.speechPitch) },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(thumbColor = accentColor)
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Speech Pitch", fontSize = 13.sp)
                            Text("${String.format("%.2f", userPreferences.speechPitch)}x", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentColor)
                        }
                        Slider(
                            value = userPreferences.speechPitch,
                            onValueChange = { viewModel.updateVoiceConfig(userPreferences.voiceName, userPreferences.speechSpeed, it) },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(thumbColor = accentColor)
                        )
                    }

                    // Auto Read check-toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Read Responses", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Instantly speak replies aloud", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(
                            checked = userPreferences.autoReadResponses,
                            onCheckedChange = { viewModel.updateReadPreferences(it, userPreferences.autoDetectLanguage) },
                            colors = CheckboxDefaults.colors(checkedColor = accentColor)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = accentColor, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// --- HELPER UTILS ---

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

private fun formatTimestamp(time: Long): String {
    val date = Date(time)
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(date)
}

// Translate language display names to codes
fun getLangCodeFromName(name: String): String {
    return when (name) {
        "Tamil (தமிழ்)" -> "ta"
        "Hindi (हिन्दी)" -> "hi"
        "Telugu (తెలుగు)" -> "te"
        "Malayalam (മലയാളം)" -> "ml"
        "Kannada (ಕன்னಡ)" -> "kn"
        "Marathi (मराठी)" -> "mr"
        "Bengali (বাংলা)" -> "bn"
        "Gujarati (ગુજરાતી)" -> "gu"
        "Punjabi (ਪੰਜਾਬੀ)" -> "pa"
        "Urdu (اردو)" -> "ur"
        "French (Français)" -> "fr"
        "German (Deutsch)" -> "de"
        "Spanish (Español)" -> "es"
        "Japanese (日本語)" -> "ja"
        "Chinese (中文)" -> "zh"
        "Korean (한국어)" -> "ko"
        "Arabic (العربية)" -> "ar"
        else -> "en"
    }
}

// File exporting triggers
fun exportChatAsTxt(context: Context, chatTitle: String, messages: List<ChatMessageEntity>) {
    try {
        val stringBuilder = StringBuilder()
        stringBuilder.append("LinguaVoice AI Chat Transcript: $chatTitle\n")
        stringBuilder.append("Exported: ${Date()}\n")
        stringBuilder.append("=========================================\n\n")
        
        messages.forEach { msg ->
            val roleName = if (msg.role == "user") "User" else "AI"
            stringBuilder.append("[$roleName] - ${formatTimestamp(msg.timestamp)}\n")
            stringBuilder.append("${msg.content}\n")
            stringBuilder.append("-----------------------------------------\n")
        }

        val folder = context.getExternalFilesDir(null)
        val file = File(folder, "$chatTitle-chat_transcript.txt")
        FileOutputStream(file).use { out ->
            out.write(stringBuilder.toString().toByteArray())
        }
        Toast.makeText(context, "Exported successfully to files directory!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to export: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun exportChatAsMd(context: Context, chatTitle: String, messages: List<ChatMessageEntity>) {
    try {
        val stringBuilder = StringBuilder()
        stringBuilder.append("# LinguaVoice AI Chat: $chatTitle\n\n")
        stringBuilder.append("*Exported on: ${Date()}*\n\n")
        
        messages.forEach { msg ->
            val roleName = if (msg.role == "user") "**User**" else "**AI Assistant**"
            stringBuilder.append("### $roleName *(${formatTimestamp(msg.timestamp)})*\n\n")
            stringBuilder.append("${msg.content}\n\n")
            stringBuilder.append("---\n\n")
        }

        val folder = context.getExternalFilesDir(null)
        val file = File(folder, "$chatTitle-chat_markdown.md")
        FileOutputStream(file).use { out ->
            out.write(stringBuilder.toString().toByteArray())
        }
        Toast.makeText(context, "Exported as markdown in files folder!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to export markdown: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun exportChatAsPdf(context: Context, chatTitle: String, messages: List<ChatMessageEntity>) {
    // Generate a file PDF document representing print out
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 page
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = android.graphics.Paint()
        
        paint.textSize = 14f
        paint.color = android.graphics.Color.BLACK
        
        canvas.drawText("LinguaVoice AI - Conversation Printout", 50f, 50f, paint)
        paint.textSize = 10f
        canvas.drawText("Chat Title: $chatTitle", 50f, 75f, paint)
        canvas.drawText("Printed: ${Date()}", 50f, 95f, paint)
        
        var currentY = 130f
        messages.take(15).forEach { msg -> // Sample subset for single page printing preview
            val prefix = if (msg.role == "user") "User: " else "AI: "
            val line = prefix + if (msg.content.length > 60) msg.content.substring(0, 58) + "..." else msg.content
            canvas.drawText(line, 50f, currentY, paint)
            currentY += 25f
        }
        
        pdfDocument.finishPage(page)
        
        val folder = context.getExternalFilesDir(null)
        val file = File(folder, "$chatTitle-transcript.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        Toast.makeText(context, "Saved PDF to local files!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to compile PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
