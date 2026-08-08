package com.rag.app.data.pdf

data class TextChunk(
    val chunkIndex: Int,
    val pageNumber: Int,
    val text: String
)

class TextChunker(
    private val chunkSizeChars: Int = 1000, // ~256 tokens
    private val overlapSizeChars: Int = 128   // ~32 tokens overlap
) {
    fun chunkText(pages: List<ParsedPdfPage>): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        var globalChunkIndex = 0

        for (page in pages) {
            val text = page.text.trim()
            if (text.isEmpty()) continue

            var start = 0
            while (start < text.length) {
                val end = minOf(start + chunkSizeChars, text.length)
                val chunkText = text.substring(start, end)

                chunks.add(
                    TextChunk(
                        chunkIndex = globalChunkIndex++,
                        pageNumber = page.pageNumber,
                        text = chunkText
                    )
                )

                if (end >= text.length) break
                start += (chunkSizeChars - overlapSizeChars)
            }
        }

        return chunks
    }
}
