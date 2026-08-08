package com.rag.app

import com.rag.app.data.pdf.ParsedPdfPage
import com.rag.app.data.pdf.TextChunker
import com.rag.app.data.vector.DocumentChunkEntity
import com.rag.app.data.vector.VectorDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RagPipelineTest {

    @Test
    fun testTextChunkerSlidingWindow() {
        val chunker = TextChunker(chunkSizeChars = 100, overlapSizeChars = 20)
        val pages = listOf(
            ParsedPdfPage(1, "This is line 1 of page 1 text that needs to be chunked into multiple pieces for embedding processing."),
            ParsedPdfPage(2, "This is page 2 content which also needs vectorization.")
        )

        val chunks = chunker.chunkText(pages)
        assertTrue(chunks.isNotEmpty())
        assertEquals(1, chunks[0].pageNumber)
    }

    @Test
    fun testVectorSimilaritySearch() = runBlocking {
        val vectorDb = VectorDatabase()

        val emb1 = floatArrayOf(1.0f, 0.0f, 0.0f)
        val emb2 = floatArrayOf(0.0f, 1.0f, 0.0f)
        val query = floatArrayOf(0.9f, 0.1f, 0.0f)

        vectorDb.insertChunks(listOf(
            DocumentChunkEntity(id = 1, text = "First document about AI", embedding = emb1, pageNumber = 1),
            DocumentChunkEntity(id = 2, text = "Second document about Cooking", embedding = emb2, pageNumber = 2)
        ))

        val results = vectorDb.searchTopK(query, k = 1)
        assertEquals(1, results.size)
        assertEquals("First document about AI", results[0].text)
    }
}
