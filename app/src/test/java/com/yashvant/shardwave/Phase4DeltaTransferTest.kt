package com.yashvant.shardwave

import com.yashvant.shardwave.chunking.FastCdcChunker
import com.yashvant.shardwave.chunking.ManifestBuilder
import com.yashvant.shardwave.data.ModelRepository
import com.yashvant.shardwave.storage.ChunkStore
import com.yashvant.shardwave.verification.HashVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Random

class Phase4DeltaTransferTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repoDir: File
    private lateinit var repository: ModelRepository

    @Before
    fun setUp() {
        repoDir = tempFolder.newFolder("repo")
        repository = ModelRepository(repoDir)
    }

    @Test
    fun testDeltaTransferPreallocationAndReusedBytes() {
        val random = Random(777)

        // Model v1 and v2 with 65% shared content
        val sharedHeader = ByteArray(350 * 1024) { (it % 256).toByte() }
        val sharedFooter = ByteArray(300 * 1024) { ((it + 100) % 256).toByte() }

        val v1Delta = ByteArray(350 * 1024)
        random.nextBytes(v1Delta)

        val v2Delta = ByteArray(350 * 1024)
        random.nextBytes(v2Delta)

        val v1Bytes = sharedHeader + v1Delta + sharedFooter
        val v2Bytes = sharedHeader + v2Delta + sharedFooter

        val v1File = tempFolder.newFile("model_v1.bin")
        v1File.writeBytes(v1Bytes)

        val v2File = tempFolder.newFile("model_v2.bin")
        v2File.writeBytes(v2Bytes)

        val chunker = FastCdcChunker(minSize = 64 * 1024, targetSize = 128 * 1024, maxSize = 256 * 1024)

        // Ingest v1 into repo (populates local ChunkStore)
        val v1Manifest = repository.ingestLocalModel("qwen-test", version = 1, inputFile = v1File, chunker = chunker)
        assertTrue(v1Manifest.chunks.isNotEmpty())

        // Create v2 manifest
        val v2Chunks = chunker.chunk(v2Bytes, keepBytes = true)
        val v2Manifest = ManifestBuilder.buildManifest(
            modelId = "qwen-test",
            version = 2,
            totalSize = v2Bytes.size.toLong(),
            totalHash = HashVerifier.sha256(v2File),
            chunkInfos = v2Chunks
        )

        // Prepare delta download for v2
        val destFile = File(tempFolder.root, "dest_model_v2.bin")
        val prepared = repository.prepareDeltaDownload(v2Manifest, destFile)

        // Assert delta statistics
        val diff = prepared.diffResult
        assertTrue("Reused chunk count should be > 0", diff.reusedCount > 0)
        assertTrue("Reused bytes should be > 0", diff.bytesReused > 0)
        assertTrue("Missing chunks should exist for delta section", diff.missingChunks.isNotEmpty())

        val stats = repository.swarmStats.value
        assertEquals(diff.totalChunks, stats.chunksTotal)
        assertEquals(diff.reusedCount, stats.chunksReusedLocal)
        assertTrue(stats.reusePercentage > 0f)

        // Supply missing chunks into ChunkStore to simulate P2P swarm arrival
        for (c in v2Chunks) {
            c.bytes?.let { repository.chunkStore.putChunk(c.hash, it) }
        }

        // Write full destination file and verify
        destFile.writeBytes(v2Bytes)
        val verified = repository.verifyAndFinalizeModel(destFile, v2Manifest.totalHash)
        assertTrue("Final v2 model verification must succeed", verified)
    }
}
