package com.yashvant.shardwave.storage

import java.io.File

/**
 * Storage manager for downloading and persisting GGUF model files on disk.
 */
class ModelStorage(private val modelsDir: File) {

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }

    fun getModelFile(modelId: String): File {
        val cleanName = modelId.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
        val fileName = if (cleanName.endsWith(".gguf")) cleanName else "$cleanName.gguf"
        return File(modelsDir, fileName)
    }

    fun isModelDownloaded(modelId: String): Boolean {
        val file = getModelFile(modelId)
        return file.exists() && file.length() > 0
    }

    fun listLocalModels(): List<File> {
        val files = modelsDir.listFiles { _, name -> name.endsWith(".gguf") } ?: return emptyList()
        return files.toList()
    }

    fun deleteModel(modelId: String): Boolean {
        val file = getModelFile(modelId)
        return if (file.exists()) file.delete() else false
    }

    fun clearAllModels() {
        modelsDir.listFiles()?.forEach { it.delete() }
    }
}
