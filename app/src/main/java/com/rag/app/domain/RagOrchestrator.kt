package com.rag.app.domain

import android.content.Context
import android.net.Uri
import com.rag.app.data.downloader.ModelDownloader
import com.rag.app.data.embedding.EmbeddingEngine
import com.rag.app.data.llm.LlamaBridge
import com.rag.app.data.pdf.PdfParser
import com.rag.app.data.pdf.TextChunker
import com.rag.app.data.vector.DocumentChunkEntity
import com.rag.app.data.vector.VectorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class RagOrchestrator(private val context: Context) {

    private val pdfParser = PdfParser(context)
    private val textChunker = TextChunker()
    private val embeddingEngine = EmbeddingEngine(context)
    private val vectorDatabase = VectorDatabase()
    private val llamaBridge = LlamaBridge()
    val modelDownloader = ModelDownloader(context)

    suspend fun processPdfDocument(uri: Uri): Int = withContext(Dispatchers.IO) {
        val pages = pdfParser.extractTextFromUri(uri)
        val chunks = textChunker.chunkText(pages)

        embeddingEngine.loadModel(modelDownloader.getEmbeddingModelFile())

        val entities = mutableListOf<DocumentChunkEntity>()
        for (chunk in chunks) {
            val vector = embeddingEngine.generateEmbedding(chunk.text)
            entities.add(
                DocumentChunkEntity(
                    text = chunk.text,
                    embedding = vector,
                    pageNumber = chunk.pageNumber
                )
            )
        }

        vectorDatabase.clear()
        vectorDatabase.insertChunks(entities)
        
        // Memory Guardrail: Unload LiteRT engine after vector database indexing
        embeddingEngine.close()

        return@withContext chunks.size
    }

    fun query(userQuestion: String): Flow<String> = flow {
        // Step 1: Embed User Query
        embeddingEngine.loadModel(modelDownloader.getEmbeddingModelFile())
        val queryEmbedding = embeddingEngine.generateEmbedding(userQuestion)
        embeddingEngine.close() // Unload embedding model before LLM inference to optimize RAM usage

        // Step 2: Vector Search Top 3 Chunks
        val topChunks = vectorDatabase.searchTopK(queryEmbedding, k = 3)
        val contextText = if (topChunks.isNotEmpty()) {
            topChunks.joinToString("\n\n") { "[Page ${it.pageNumber}]: ${it.text}" }
        } else {
            "No document uploaded or matching context found."
        }

        // Step 3: Construct Prompt with Qwen Chat Format
        val formattedPrompt = """
            <|im_start|>system
            Answer the question using ONLY the provided document context.
            Context:
            $contextText
            <|im_end|>
            <|im_start|>user
            $userQuestion<|im_end|>
            <|im_start|>assistant
        """.trimIndent()

        // Step 4: Run llama.cpp Native LLM Inference (Qwen2.5-0.5B-Instruct)
        val modelFile = modelDownloader.getLlamaModelFile()
        llamaBridge.initialize(modelFile)

        llamaBridge.generate(formattedPrompt).collect { token ->
            emit(token)
        }

        llamaBridge.close()
    }.flowOn(Dispatchers.Default)
}
