package com.rag.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rag.app.domain.RagOrchestrator
import com.rag.app.ui.ChatScreen
import com.rag.app.ui.theme.OnDeviceRAGTheme

class MainActivity : ComponentActivity() {

    private lateinit var orchestrator: RagOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        orchestrator = RagOrchestrator(applicationContext)

        setContent {
            OnDeviceRAGTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen(orchestrator = orchestrator)
                }
            }
        }
    }
}
