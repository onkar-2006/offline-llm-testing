package com.rag.app.data.downloader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

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

    val qwenModelUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"

    fun getLlamaModelFile(): File = File(context.filesDir, "qwen2.5-0.5b-instruct-q4_k_m.gguf")
    fun getEmbeddingModelFile(): File = File(context.filesDir, "all-MiniLM-L6-v2.tflite")

    fun isModelReady(): Boolean {
        val f = getLlamaModelFile()
        return f.exists() && f.length() > 0L
    }

    fun downloadModels(): Flow<DownloadProgress> = flow {
        val targetFile = getLlamaModelFile()
        if (targetFile.exists() && targetFile.length() > 300_000_000L) {
            emit(DownloadProgress(targetFile.length(), targetFile.length(), isCompleted = true))
            return@flow
        }

        try {
            val url = URL(qwenModelUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                emit(DownloadProgress(0, 0, isCompleted = false, error = "HTTP ${connection.responseCode} Server Error"))
                return@flow
            }

            val fileLength = connection.contentLengthLong
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                emit(DownloadProgress(totalBytesRead, fileLength))
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            emit(DownloadProgress(totalBytesRead, fileLength, isCompleted = true))
        } catch (e: Exception) {
            e.printStackTrace()
            emit(DownloadProgress(0, 0, isCompleted = false, error = e.localizedMessage))
        }
    }.flowOn(Dispatchers.IO)
}
