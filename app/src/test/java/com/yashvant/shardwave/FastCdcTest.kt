package com.yashvant.shardwave

import com.yashvant.shardwave.chunking.FastCdcChunker
import com.yashvant.shardwave.chunking.ManifestBuilder
import com.yashvant.shardwave.chunking.ManifestDiffer
import com.yashvant.shardwave.chunking.ModelManifest
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

class FastCdcTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var chunkStoreDir: File
    private lateinit var chunkStore: ChunkStore

    @Before
    fun setUp() {
        chunkStoreDir = tempFolder.newFolder("chunks_cdc")
        chunkStore = ChunkStore(chunkStoreDir)
    }

    @Test
    fun testFastCdcBoundsAndIntegrity() {
        val random = Random(123)
        val data = ByteArray(2 * 1024 * 1024) // 2 MB
        random.nextBytes(data)

        val minSize = 64 * 1024
        val targetSize = 256 * 1024
        val maxSize = 512 * 1024

        val chunker = FastCdcChunker(minSize = minSize, targetSize = targetSize, maxSize = maxSize)
        val chunks = chunker.chunk(data, keepBytes = true)

        assertTrue(chunks.isNotEmpty())

        var reassembledLength = 0
        for (c in chunks) {
            assertTrue("Chunk size ${c.size} below min $minSize", c.size >= minSize || c == chunks.last())
            assertTrue("Chunk size ${c.size} exceeds max $maxSize", c.size <= maxSize)
            reassembledLength += c.size
        }

        assertEquals(data.size, reassembledLength)
    }

    @Test
    fun testManifestJsonRoundTrip() {
        val chunker = FastCdcChunker(minSize = 32 * 1024, targetSize = 64 * 1024, maxSize = 128 * 1024)
        val data = "Shardwave Manifest Serializer Test Payload".toByteArray()
        val chunkInfos = chunker.chunk(data)

        val manifest = ManifestBuilder.buildManifest(
            modelId = "test-model-v1",
            version = 1,
            totalSize = data.size.toLong(),
            totalHash = HashVerifier.sha256(data),
            chunkInfos = chunkInfos
        )

        val jsonStr = manifest.toJson()
        val parsed = ModelManifest.fromJson(jsonStr)

        assertEquals(manifest.modelId, parsed.modelId)
        assertEquals(manifest.version, parsed.version)
        assertEquals(manifest.totalSize, parsed.totalSize)
        assertEquals(manifest.totalHash, parsed.totalHash)
        assertEquals(manifest.chunks.size, parsed.chunks.size)
    }

    @Test
    fun testDeltaChunkingReuseScenario() {
        val random = Random(999)

        // Generate base bytes (1 MB)
        val commonHeader = ByteArray(400 * 1024) { (it % 256).toByte() }
        val commonFooter = ByteArray(400 * 1024) { ((it + 50) % 256).toByte() }

        val v1Middle = ByteArray(200 * 1024)
        random.nextBytes(v1Middle)

        val v2Middle = ByteArray(200 * 1024)
        random.nextBytes(v2Middle)

        // Build v1 & v2 payloads
        val v1Bytes = commonHeader + v1Middle + commonFooter
        val v2Bytes = commonHeader + v2Middle + commonFooter

        val minSize = 64 * 1024
        val targetSize = 128 * 1024
        val maxSize = 256 * 1024
        val chunker = FastCdcChunker(minSize = minSize, targetSize = targetSize, maxSize = maxSize)

        // Chunk v1 and store in ChunkStore
        val v1Chunks = chunker.chunk(v1Bytes, keepBytes = true)
        for (c in v1Chunks) {
            c.bytes?.let { chunkStore.putChunk(c.hash, it) }
        }

        // Chunk v2 and build manifest
        val v2Chunks = chunker.chunk(v2Bytes, keepBytes = false)
        val v2Manifest = ManifestBuilder.buildManifest(
            modelId = "qwen2.5-0.5b",
            version = 2,
            totalSize = v2Bytes.size.toLong(),
            totalHash = HashVerifier.sha256(v2Bytes),
            chunkInfos = v2Chunks
        )

        // Diff v2 against local ChunkStore
        val diff = ManifestDiffer.diff(v2Manifest, chunkStore)

        assertTrue("Expected shared chunks between v1 and v2", diff.reusedCount > 0)
        assertTrue("Expected non-zero reuse percentage", diff.reusePercentage > 0f)
        assertTrue("Expected some missing chunks for updated section", diff.missingChunks.isNotEmpty())
        assertEquals(v2Manifest.chunks.size, diff.totalChunks)
    }
}
