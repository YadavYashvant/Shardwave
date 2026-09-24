package com.yashvant.shardwave

import com.yashvant.shardwave.inference.LlamaCppBridge
import com.yashvant.shardwave.inference.ModelRunner
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Phase5InferenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dummyModelFile: File

    @Before
    fun setUp() {
        dummyModelFile = tempFolder.newFile("qwen2.5-0.5b-instruct-q4_k_m.gguf")
        dummyModelFile.writeBytes(ByteArray(1024) { (it % 256).toByte() })
    }

    @Test
    fun testLlamaCppBridgeAndRunnerFlow() = runBlocking {
        val bridge = LlamaCppBridge(delayMs = 0L)
        val runner = ModelRunner(bridge)

        val loaded = runner.loadModel(dummyModelFile)
        assertTrue("Model load should succeed", loaded)

        val prompt = "Explain quantum computing in one sentence."
        val outputLines = runner.generateResponse(prompt).toList()

        assertTrue("Output lines should not be empty", outputLines.isNotEmpty())
        val fullResponse = outputLines.joinToString("")
        assertTrue(fullResponse.lowercase().contains("quantum computing"))

        runner.unload()
    }
}
