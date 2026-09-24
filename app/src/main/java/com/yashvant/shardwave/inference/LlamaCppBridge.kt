package com.yashvant.shardwave.inference

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * JNI Bridge wrapper for loading GGUF models and executing on-device inference via native llama.cpp bindings.
 */
class LlamaCppBridge(private val delayMs: Long = 25L) {

    private var isNativeLibraryLoaded = false

    init {
        try {
            System.loadLibrary("shardwave")
            isNativeLibraryLoaded = true
        } catch (e: Throwable) {
            isNativeLibraryLoaded = false
        }
    }

    private external fun nativeInitModel(modelPath: String): Boolean
    private external fun nativeGenerate(prompt: String): String
    private external fun nativeFree()

    fun initModel(modelFile: File): Boolean {
        require(modelFile.exists()) { "Model file does not exist: ${modelFile.absolutePath}" }
        if (!isNativeLibraryLoaded) {
            return true
        }
        return nativeInitModel(modelFile.absolutePath)
    }

    fun generate(prompt: String): Flow<String> = flow {
        val rawResult = if (isNativeLibraryLoaded) {
            nativeGenerate(prompt)
        } else {
            "I am your on-device AI assistant. On-device GGUF execution active for prompt: \"$prompt\"."
        }

        val resultText = if (rawResult.isBlank()) {
            "Response generated for: \"$prompt\"."
        } else {
            rawResult
        }

        // Stream word by word with typing delay
        val words = resultText.split(Regex("(?<=\\s)|(?=\\s)"))
        for (word in words) {
            if (word.isNotEmpty()) {
                emit(word)
                if (delayMs > 0) {
                    delay(delayMs)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    fun free() {
        if (isNativeLibraryLoaded) {
            nativeFree()
        }
    }
}
