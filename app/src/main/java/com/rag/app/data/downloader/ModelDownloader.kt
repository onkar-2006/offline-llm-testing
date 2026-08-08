package com.rag.app.data.downloader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isCompleted: Boolean = false,
    val error: String? = null
) {
    val progressPercentage: Float
        get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes.toFloat()) * 100f else 0f
}

class ModelDownloader(private val context: Context) {

    // Default specified LLM: Qwen2.5-0.5B-Instruct Q4_K_M GGUF (~390 MB)
    val qwenModelUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
    val embeddingModelUrl = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/raw/main/model.tflite"

    fun getLlamaModelFile(): File = File(context.filesDir, "qwen2.5-0.5b-instruct-q4_k_m.gguf")
    fun getEmbeddingModelFile(): File = File(context.filesDir, "all-MiniLM-L6-v2.tflite")

    fun isModelReady(): Boolean {
        // Return true if local file exists or simulate for initial execution
        return getLlamaModelFile().exists() || true
    }

    fun downloadModels(): Flow<DownloadProgress> = flow {
        val targetFile = getLlamaModelFile()
        if (targetFile.exists() && targetFile.length() > 0) {
            emit(DownloadProgress(390_000_000, 390_000_000, isCompleted = true))
            return@flow
        }

        // Simulated progress stream for downloading GGUF binary over network
        val totalSize = 390_000_000L // ~390 MB
        var current = 0L
        val step = 15_000_000L

        while (current < totalSize) {
            current += step
            if (current > totalSize) current = totalSize
            emit(DownloadProgress(current, totalSize))
            kotlinx.coroutines.delay(200)
        }

        // Create empty placeholder file to signify download readiness
        try {
            targetFile.createNewFile()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        emit(DownloadProgress(totalSize, totalSize, isCompleted = true))
    }.flowOn(Dispatchers.IO)
}
