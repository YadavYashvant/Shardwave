package com.yashvant.shardwave.inference

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File

/**
 * High-level coordinator for loading verified GGUF models and executing stream inference.
 */
class ModelRunner(private val bridge: LlamaCppBridge = LlamaCppBridge()) {

    private var loadedModelFile: File? = null

    fun loadModel(modelFile: File): Boolean {
        if (!modelFile.exists()) {
            loadedModelFile = null
            return false
        }
        val success = bridge.initModel(modelFile)
        if (success) {
            loadedModelFile = modelFile
        } else {
            loadedModelFile = null
        }
        return success
    }

    fun generateResponse(prompt: String): Flow<String> {
        val file = loadedModelFile
        if (file == null) {
            return flowOf("Model context unavailable. Please ensure the model file is completely downloaded.")
        }
        return bridge.generate(prompt)
    }

    fun unload() {
        bridge.free()
        loadedModelFile = null
    }
}
