package com.rag.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rag.app.domain.RagOrchestrator
import kotlinx.coroutines.launch

data class ChatMessage(
    val sender: MessageSender,
    val text: String
)

enum class MessageSender {
    USER, ASSISTANT, SYSTEM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(orchestrator: RagOrchestrator) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var messages by remember { mutableStateOf(listOf(
        ChatMessage(MessageSender.SYSTEM, "Welcome to On-Device RAG! Upload a PDF to start asking offline questions powered by Qwen2.5-0.5B-Instruct.")
    )) }
    var inputText by remember { mutableStateOf("") }
    var isProcessingDoc by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isDownloadingModel by remember { mutableStateOf(false) }
    var downloadStatusText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (!orchestrator.modelDownloader.isModelReady()) {
            isDownloadingModel = true
            orchestrator.modelDownloader.downloadModels().collect { progress ->
                downloadProgress = progress.progressPercentage
                downloadStatusText = if (progress.error != null) {
                    "Error downloading model: ${progress.error}"
                } else if (progress.isCompleted) {
                    "Qwen2.5 model download complete!"
                } else {
                    "Downloading Qwen2.5-0.5B-Instruct model: ${progress.bytesDownloaded / 1_048_576} MB / ${progress.totalBytes / 1_048_576} MB (${progress.progressPercentage.toInt()}%)"
                }
                if (progress.isCompleted || progress.error != null) {
                    isDownloadingModel = false
                }
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                isProcessingDoc = true
                messages = messages + ChatMessage(MessageSender.SYSTEM, "Processing document into vector chunks...")
                val chunkCount = orchestrator.processPdfDocument(it)
                isProcessingDoc = false
                messages = messages + ChatMessage(MessageSender.SYSTEM, "Document indexed successfully ($chunkCount chunks). You can now ask questions!")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("On-Device RAG", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Model: Qwen2.5-0.5B-Instruct (Offline)", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                actions = {
                    Button(
                        onClick = { pdfPickerLauncher.launch("application/pdf") },
                        enabled = !isProcessingDoc && !isGenerating,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Import PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedVisibility(visible = isDownloadingModel) {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Text(downloadStatusText, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }
            }

            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask a question about your document...") },
                        modifier = Modifier.weight(1f),
                        enabled = !isGenerating && !isProcessingDoc
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val question = inputText
                                inputText = ""
                                messages = messages + ChatMessage(MessageSender.USER, question)
                                isGenerating = true

                                coroutineScope.launch {
                                    val assistantMsgIndex = messages.size
                                    messages = messages + ChatMessage(MessageSender.ASSISTANT, "")
                                    
                                    var currentText = ""
                                    orchestrator.query(question).collect { token ->
                                        currentText += token
                                        val updated = messages.toMutableList()
                                        if (assistantMsgIndex < updated.size) {
                                            updated[assistantMsgIndex] = ChatMessage(MessageSender.ASSISTANT, currentText)
                                            messages = updated
                                        }
                                    }
                                    isGenerating = false
                                }
                            }
                        },
                        enabled = inputText.isNotBlank() && !isGenerating && !isProcessingDoc
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER
    val isSystem = message.sender == MessageSender.SYSTEM

    val alignment = when {
        isUser -> Alignment.CenterEnd
        isSystem -> Alignment.Center
        else -> Alignment.CenterStart
    }

    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primary
        isSystem -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val textColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimary
        isSystem -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.text,
                color = textColor,
                fontSize = 14.sp
            )
        }
    }
}
