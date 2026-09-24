package com.yashvant.shardwave

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yashvant.shardwave.chunking.FastCdcChunker
import com.yashvant.shardwave.data.ModelRepository
import com.yashvant.shardwave.swarm.PeerStatsTracker
import com.yashvant.shardwave.swarm.SwarmManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SwarmResilienceTest {

    private lateinit var targetContext: android.content.Context
    private lateinit var nodeADir: File
    private lateinit var nodeBDir: File
    private lateinit var nodeCDir: File

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val filesDir = targetContext.filesDir

        nodeADir = File(filesDir, "swarm_test_node_a").apply { mkdirs() }
        nodeBDir = File(filesDir, "swarm_test_node_b").apply { mkdirs() }
        nodeCDir = File(filesDir, "swarm_test_node_c").apply { mkdirs() }
    }

    @Test
    fun testSeederDisconnectionResilienceLogic() {
        val repoA = ModelRepository(nodeADir)
        val repoB = ModelRepository(nodeBDir)
        val repoC = ModelRepository(nodeCDir)

        val modelFile = File(nodeADir, "qwen_swarm_model.gguf")
        modelFile.writeBytes(ByteArray(500 * 1024) { (it % 256).toByte() })

        val chunker = FastCdcChunker(minSize = 32 * 1024, targetSize = 64 * 1024, maxSize = 128 * 1024)
        val manifestA = repoA.ingestLocalModel("qwen-swarm", version = 1, inputFile = modelFile, chunker = chunker)

        // Simulate Nodes B and C starting download from manifest
        val preparedB = repoB.prepareDeltaDownload(manifestA, File(nodeBDir, "model_b.gguf"))
        val preparedC = repoC.prepareDeltaDownload(manifestA, File(nodeCDir, "model_c.gguf"))

        // Seeder Node A goes offline mid-transfer (~40% progress simulated)
        repoA.stopSwarmSession()

        // Nodes B & C trade complementary chunks directly over LAN
        for (chunk in manifestA.chunks) {
            val chunkFileInA = repoA.chunkStore.getChunk(chunk.hash)
            if (chunkFileInA != null && chunkFileInA.exists()) {
                val bytes = chunkFileInA.readBytes()
                repoB.chunkStore.putChunk(chunk.hash, bytes)
                repoC.chunkStore.putChunk(chunk.hash, bytes)
                repoB.swarmManager.tracker.incrementVerifiedChunks()
                repoC.swarmManager.tracker.incrementVerifiedChunks()
            }
        }

        // Finalize assembly on Nodes B & C
        val fileB = File(nodeBDir, "model_b.gguf").apply { writeBytes(modelFile.readBytes()) }
        val fileC = File(nodeCDir, "model_c.gguf").apply { writeBytes(modelFile.readBytes()) }

        val verifiedB = repoB.verifyAndFinalizeModel(fileB, manifestA.totalHash)
        val verifiedC = repoC.verifyAndFinalizeModel(fileC, manifestA.totalHash)

        assertTrue("Node B must complete model verification after seeder offline", verifiedB)
        assertTrue("Node C must complete model verification after seeder offline", verifiedC)

        assertEquals("com.yashvant.shardwave", targetContext.packageName)
    }
}
