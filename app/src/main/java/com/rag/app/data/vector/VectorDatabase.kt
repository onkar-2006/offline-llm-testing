package com.rag.app.data.vector

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

data class DocumentChunkEntity(
    val id: Long = 0,
    val text: String,
    val embedding: FloatArray,
    val pageNumber: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DocumentChunkEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

class VectorDatabase {

    private val storage = mutableListOf<DocumentChunkEntity>()
    private var nextId = 1L

    suspend fun insertChunks(chunks: List<DocumentChunkEntity>) = withContext(Dispatchers.IO) {
        chunks.forEach { chunk ->
            storage.add(chunk.copy(id = nextId++))
        }
    }

    suspend fun searchTopK(queryEmbedding: FloatArray, k: Int = 3): List<DocumentChunkEntity> = withContext(Dispatchers.Default) {
        if (storage.isEmpty()) return@withContext emptyList()

        storage.map { entity ->
            val score = cosineSimilarity(queryEmbedding, entity.embedding)
            Pair(entity, score)
        }
        .sortedByDescending { it.second }
        .take(k)
        .map { it.first }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        storage.clear()
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        val denominator = (sqrt(normA.toDouble()) * sqrt(normB.toDouble())).toFloat()
        return if (denominator == 0f) 0f else dotProduct / denominator
    }
}
