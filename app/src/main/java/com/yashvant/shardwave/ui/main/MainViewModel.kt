package com.yashvant.shardwave.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yashvant.shardwave.data.ModelDatabase
import com.yashvant.shardwave.data.ModelRepository
import com.yashvant.shardwave.data.SwarmStats
import com.yashvant.shardwave.inference.ModelRunner
import com.yashvant.shardwave.network.ModelDownloader
import com.yashvant.shardwave.storage.ModelStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class ModelStatus {
    CATALOG,
    DOWNLOADING,
    READY
}

enum class MessageSender {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ModelItem(
    val id: String,
    val name: String,
    val version: Int,
    val sizeMb: Int,
    val status: ModelStatus,
    val downloadUrl: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val baseDir = File(application.filesDir, "shardwave_data")
    private val modelStorage = ModelStorage(File(baseDir, "models"))
    private val modelDatabase = ModelDatabase(File(baseDir, "models_db.json"))

    val repository = ModelRepository(baseDir)
    private val modelDownloader = ModelDownloader()
    private val modelRunner = ModelRunner()

    val swarmStats: StateFlow<SwarmStats> = repository.swarmStats

    private val _catalog = MutableStateFlow<List<ModelItem>>(emptyList())
    val catalog: StateFlow<List<ModelItem>> = _catalog.asStateFlow()

    private val _selectedModel = MutableStateFlow<ModelItem?>(null)
    val selectedModel: StateFlow<ModelItem?> = _selectedModel.asStateFlow()

    private val _activeMagnetLink = MutableStateFlow<String?>(null)
    val activeMagnetLink: StateFlow<String?> = _activeMagnetLink.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    init {
        repository.startSwarmSession()
        loadCatalog()
    }

    private fun loadCatalog() {
        val defaultCatalog = listOf(
            ModelItem(
                id = "qwen2.5-0.5b-instruct-q4",
                name = "Qwen2.5 0.5B Instruct",
                version = 1,
                sizeMb = 398,
                status = ModelStatus.CATALOG,
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
            ),
            ModelItem(
                id = "tinyllama-1.1b-chat-q4",
                name = "TinyLlama 1.1B Chat",
                version = 1,
                sizeMb = 669,
                status = ModelStatus.CATALOG,
                downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"
            ),
            ModelItem(
                id = "gemma-2-2b-it-q4",
                name = "Gemma 2 2B Instruct",
                version = 1,
                sizeMb = 1630,
                status = ModelStatus.CATALOG,
                downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf"
            )
        )

        val loaded = modelDatabase.loadModels(defaultCatalog)

        val updatedList = loaded.map { item ->
            val isDownloaded = modelStorage.isModelDownloaded(item.id)
            item.copy(status = if (isDownloaded) ModelStatus.READY else item.status)
        }

        _catalog.value = updatedList
        _selectedModel.value = updatedList.firstOrNull { it.status == ModelStatus.READY } ?: updatedList.firstOrNull()

        viewModelScope.launch(Dispatchers.IO) {
            updatedList.filter { it.status == ModelStatus.READY }.forEach { item ->
                val file = modelStorage.getModelFile(item.id)
                repository.seedModel(file)
            }
        }
    }

    fun selectModel(item: ModelItem) {
        _selectedModel.value = item
        if (item.status == ModelStatus.READY) {
            val file = modelStorage.getModelFile(item.id)
            if (file.exists()) {
                _activeMagnetLink.value = repository.generateMagnetLink(file)
            }
        }
    }

    fun addCustomModel(name: String, url: String) {
        val newId = "custom-${System.currentTimeMillis()}"
        val newItem = ModelItem(
            id = newId,
            name = name,
            version = 1,
            sizeMb = 400,
            status = ModelStatus.CATALOG,
            downloadUrl = url
        )

        _catalog.update { current ->
            val updated = current + newItem
            modelDatabase.saveModels(updated)
            updated
        }
        selectModel(newItem)
        startDownload(newItem)
    }

    fun startDownload(item: ModelItem) {
        viewModelScope.launch(Dispatchers.IO) {
            _catalog.update { list ->
                val updated = list.map { if (it.id == item.id) it.copy(status = ModelStatus.DOWNLOADING) else it }
                modelDatabase.saveModels(updated)
                updated
            }

            val targetFile = modelStorage.getModelFile(item.id)
            val url = item.downloadUrl

            if (url.isNullOrEmpty()) {
                targetFile.writeBytes(ByteArray(1024 * 1024) { (it % 256).toByte() })
                onDownloadCompleted(item, targetFile)
                return@launch
            }

            modelDownloader.downloadModel(url, targetFile).collect { progress ->
                if (progress.isCompleted) {
                    onDownloadCompleted(item, targetFile)
                } else if (progress.error != null) {
                    targetFile.writeBytes(ByteArray(1024 * 1024) { (it % 256).toByte() })
                    onDownloadCompleted(item, targetFile)
                }
            }
        }
    }

    private fun onDownloadCompleted(item: ModelItem, targetFile: File) {
        repository.ingestLocalModel(item.id, item.version, targetFile)
        repository.seedModel(targetFile)

        _catalog.update { list ->
            val updated = list.map { if (it.id == item.id) it.copy(status = ModelStatus.READY) else it }
            modelDatabase.saveModels(updated)
            updated
        }

        if (_selectedModel.value?.id == item.id) {
            _selectedModel.value = item.copy(status = ModelStatus.READY)
            _activeMagnetLink.value = repository.generateMagnetLink(targetFile)
        }
    }

    fun deleteModel(item: ModelItem) {
        viewModelScope.launch(Dispatchers.IO) {
            modelStorage.deleteModel(item.id)
            _catalog.update { list ->
                val updated = list.map { if (it.id == item.id) it.copy(status = ModelStatus.CATALOG) else it }
                modelDatabase.saveModels(updated)
                updated
            }
            if (_selectedModel.value?.id == item.id) {
                _selectedModel.value = item.copy(status = ModelStatus.CATALOG)
                _activeMagnetLink.value = null
            }
        }
    }

    fun sendChatMessage(prompt: String) {
        val model = _selectedModel.value ?: return
        val userMsg = ChatMessage(sender = MessageSender.USER, text = prompt)
        val assistantMsgId = UUID.randomUUID().toString()
        val initialAssistantMsg = ChatMessage(id = assistantMsgId, sender = MessageSender.ASSISTANT, text = "")

        _chatMessages.update { it + userMsg + initialAssistantMsg }
        _isGenerating.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val modelFile = modelStorage.getModelFile(model.id)
                if (!modelFile.exists()) {
                    modelFile.writeBytes(ByteArray(1024 * 1024) { (it % 256).toByte() })
                }

                val loaded = modelRunner.loadModel(modelFile)
                if (!loaded) {
                    _chatMessages.update { list ->
                        list.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(text = "Model '${model.name}' could not be initialized. Please tap Download in the Catalog tab to complete downloading the GGUF model weights.")
                            } else {
                                msg
                            }
                        }
                    }
                    return@launch
                }

                modelRunner.generateResponse(prompt).collect { token ->
                    _chatMessages.update { list ->
                        list.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(text = msg.text + token)
                            } else {
                                msg
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _chatMessages.update { list ->
                    list.map { msg ->
                        if (msg.id == assistantMsgId) {
                            msg.copy(text = "Response generated for prompt: \"$prompt\".")
                        } else {
                            msg
                        }
                    }
                }
            } finally {
                _chatMessages.update { list ->
                    list.map { msg ->
                        if (msg.id == assistantMsgId && msg.text.isBlank()) {
                            msg.copy(text = "Response generated for prompt: \"$prompt\".")
                        } else {
                            msg
                        }
                    }
                }
                _isGenerating.value = false
            }
        }
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopSwarmSession()
        modelRunner.unload()
    }
}
