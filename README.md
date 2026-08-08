Hitting a 1GB total limit (for both app and runtime models) while keeping everything 100% offline is a realistic engineering target.

By selecting high-efficiency, sub-billion parameter models and native C++ execution engines, the entire on-device RAG system can run with a ~550 MB total footprint and under 1.5 GB runtime RAM usage.

The Low-Footprint Tech Stack (Budget ~550 MB Total)
Component
Technology Choice
Size on Disk
Base App Code & UI
Native Kotlin + Jetpack Compose
~15 MB
PDF Processing Engine
PdfRenderer (Native Android API)
0 MB (Built into Android OS)
Embedding Model
all-MiniLM-L6-v2 (.tflite / INT8)
~30 MB
Local Vector DB
ObjectBox Vector or sqlite-vec (C-extension)
~5 MB
Mobile LLM Engine
llama.cpp JNI wrapper
~10 MB
Quantized Local LLM
SmolLM2 135M / Qwen2.5 0.5B (Q4_K_M)
~140 MB – 390 MB
TOTAL DISK SIZE
All components combined
~200 MB – 500 MB (Well under 1GB!)


Phase-by-Phase Implementation Roadmap
Phase 1: Foundation (Android + UI Setup)
  └── Phase 2: PDF Parsing & Text Chunking
        └── Phase 3: Local Embeddings Engine (LiteRT)
              └── Phase 4: Embedded Vector DB
                    └── Phase 5: LLM Integration (llama.cpp JNI)
                          └── Phase 6: End-to-End Orchestration & Polish

Phase 1: App Setup & On-Demand Model Downloader
Goal: Keep the initial APK under 25 MB on the Play Store.
Tasks:
Build a basic Jetpack Compose UI with document selection, a chat interface, and a download progress indicator.
Implement an initial download manager (using Android's DownloadManager or Ktor-client) to pull your quantized LLM (smollm2-135m-instruct-q8_0.gguf ~138MB or qwen2.5-0.5b-instruct-q4_k_m.gguf ~398MB) directly into context.filesDir upon first launch.

Phase 2: On-Device PDF Processing
Goal: Extract text without heavy third-party Java libraries.
Tasks:
Use Android's built-in android.graphics.pdf.PdfRenderer or lightweight PDFBox-Android.
Write a lightweight sliding-window text chunker function:
Split document text into chunks of 256 tokens (~1,000 characters) with a 32-token overlap to maintain semantic context across page splits.

Phase 3: Ultra-Fast Vector Embeddings
Goal: Convert text chunks into 384-dimensional floating-point array vectors.
Tasks:
Download the quantized all-MiniLM-L6-v2.tflite model (~30 MB).
Add the LiteRT (TensorFlow Lite) dependency in build.gradle.kts:
Kotlin
implementation("org.tensorflow:tensorflow-lite:2.14.0")
implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")




Pass extracted text chunks through LiteRT to generate dense FloatArray(384) embeddings natively on the GPU or NPU.

Phase 4: Embedded Local Vector Database
Goal: Perform cosine similarity search directly inside SQLite.
Tasks:
Integrate ObjectBox Vector DB or compile sqlite-vec as a native shared C library (.so) into your Android project.
Set up your entity schema:
Kotlin
data class DocumentChunk(
    val id: Long = 0,
    val text: String,
    val embedding: FloatArray,
    val pageNumber: Int
)




On user queries, embed the query text, perform an HNSW or KNN vector search in SQLite, and return the top 3 matching text chunks.
Phase 5: Local LLM Engine Setup (llama.cpp + JNI)
Goal: Execute Qwen2.5 0.5B or SmolLM2 135M with low memory overhead.
Tasks:
Clone llama.cpp and build Android native libraries (libllama.so) using the Android NDK for arm64-v8a.
Expose a simple C++ / Kotlin JNI interface:
Kotlin

class LlamaBridge {
    external fun initModel(modelPath: String): Long
    external fun generate(modelPtr: Long, prompt: String, onToken: (String) -> Unit)
}




Load the downloaded .gguf file into memory mapped storage (mmap), restricting context window size to 2,048 tokens to minimize RAM consumption.
Phase 6: End-to-End RAG Pipeline & Memory Guardrails
Goal: Connect retrieval output directly to the LLM prompt.
Tasks:
Construct the Prompt:
Plaintext
<|im_start|>system
Answer the question using ONLY the provided document context.
Context: {Top_3_Retrieved_Chunks}
<|im_end|>
<|im_start|>user
{User_Question}<|im_end|>
<|im_start|>assistant




Memory & Threading Management:
Run embedding creation and vector search sequentially on Kotlin Dispatchers.Default.
Unload the LiteRT embedding engine from RAM before triggering the LLM generation to prevent memory spikes.
Stream response tokens onto the UI in real-time.




On-Device Document AI Tech Stack
1. Application Layer (User Interface & Control)
Language: Kotlin
UI Framework: Jetpack Compose (Modern declarative UI)
Background Processing: Kotlin Coroutines & Flows (Asynchronous streaming without blocking the UI thread)
Networking (Model Downloader): Ktor Client or Android DownloadManager
2. Document Parsing & Text Chunking
Parser: Native Android PdfRenderer API
Why: Built directly into the Android OS, taking up 0 MB of extra application storage.
Text Extractor & Chunker: Custom Kotlin Recursive Character Chunker
Configuration: Chunks of 256 tokens (~1,000 characters) with a 32-token overlap.
3. On-Device Embedding Engine
Inference Runtime: LiteRT (Google's re-architected, lightweight engine replacing TensorFlow Lite)
Embedding Model: all-MiniLM-L6-v2 (.tflite / INT8 Quantized)
Disk Footprint: ~30 MB
Output: 384-dimensional dense floating-point vector arrays.
4. Local Vector Database
Database Engine: sqlite-vec (Compiled as a native C-extension) or ObjectBox Vector DB
Disk Footprint: ~5 MB
Search Algorithm: Local HNSW (Hierarchical Navigable Small World) or Cosine Similarity index inside standard Android SQLite/Room storage.
5. Mobile LLM Engine & Quantized Weights
Execution Engine: llama.cpp compiled via Android NDK (Native Development Kit)
Bridge Layer: JNI (Java Native Interface) to call low-level C++ matrix multiplications from Kotlin.
LLM Weights Options (Pick One):
Option A (Ultra Lightweight): SmolLM2-135M-Instruct (Q4_K_M or Q8_0 GGUF)
Disk Size: ~105 MB – 145 MB | RAM Needed: ~500 MB
Option B (Higher Quality Reasoning): Qwen2.5-0.5B-Instruct (Q4_K_M GGUF)
Disk Size: ~350 MB | RAM Needed: ~1.2 GB
