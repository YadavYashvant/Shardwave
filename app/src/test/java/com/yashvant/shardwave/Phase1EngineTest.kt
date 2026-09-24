package com.yashvant.shardwave

import com.yashvant.shardwave.storage.ChunkStore
import com.yashvant.shardwave.storage.ModelAssembler
import com.yashvant.shardwave.verification.HashVerifier
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.util.Random

class Phase1EngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var chunkStoreDir: File
    private lateinit var chunkStore: ChunkStore
    private lateinit var modelAssembler: ModelAssembler

    @Before
    fun setUp() {
        chunkStoreDir = tempFolder.newFolder("chunks")
        chunkStore = ChunkStore(chunkStoreDir)
        modelAssembler = ModelAssembler(chunkStore)
    }

    @Test
    fun testHashVerifierByteArrayAndByteBuffer() {
        val testData = "Shardwave P2P Test String".toByteArray(Charsets.UTF_8)
        val hashStr = HashVerifier.sha256(testData)
        assertEquals(64, hashStr.length)

        val buffer = ByteBuffer.wrap(testData)
        val hashBuf = HashVerifier.sha256(buffer)
        assertEquals(hashStr, hashBuf)

        assertTrue(HashVerifier.verify(testData, hashStr))
        assertFalse(HashVerifier.verify(testData, "0000000000000000000000000000000000000000000000000000000000000000"))
    }

    @Test
    fun testChunkStorePutAndGet() {
        val data = ByteArray(1024) { (it % 256).toByte() }
        val hash = HashVerifier.sha256(data)

        val file = chunkStore.putChunk(hash, data)
        assertTrue(file.exists())
        assertTrue(chunkStore.hasChunk(hash))
        assertArrayEquals(data, chunkStore.getChunkBytes(hash))
        assertEquals(setOf(hash), chunkStore.listKnownHashes())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testChunkStoreRejectsCorruptHash() {
        val data = "Valid Data".toByteArray()
        val wrongHash = "a" * 64
        chunkStore.putChunk(wrongHash, data)
    }

    @Test
    fun testChunkVerifyReassembleRoundTrip10Runs() {
        val random = Random(42)

        repeat(10) { runIndex ->
            // Generate synthetic model payload (e.g. 500 KB)
            val fileSize = 500 * 1024
            val originalBytes = ByteArray(fileSize)
            random.nextBytes(originalBytes)

            val originalFile = tempFolder.newFile("original_model_$runIndex.bin")
            originalFile.writeBytes(originalBytes)

            val expectedTotalHash = HashVerifier.sha256(originalFile)

            // Split into 5 fixed 100 KB chunks for Phase 1 test
            val chunkSize = 100 * 1024
            val chunkHashes = mutableListOf<String>()

            for (i in 0 until 5) {
                val chunkBytes = originalBytes.copyOfRange(i * chunkSize, (i + 1) * chunkSize)
                val chunkHash = HashVerifier.sha256(chunkBytes)
                chunkStore.putChunk(chunkHash, chunkBytes)
                chunkHashes.add(chunkHash)
            }

            // Reassemble
            val assembledFile = tempFolder.newFile("assembled_model_$runIndex.bin")
            val result = modelAssembler.assemble(
                destination = assembledFile,
                chunkHashes = chunkHashes,
                expectedTotalHash = expectedTotalHash
            )

            assertTrue("Run $runIndex failed", result.isSuccess)
            val output = result.getOrThrow()
            assertTrue(output.exists())
            assertArrayEquals("Run $runIndex byte mismatch", originalBytes, output.readBytes())
            assertEquals("Run $runIndex hash mismatch", expectedTotalHash, HashVerifier.sha256(output))
        }
    }

    private operator fun String.times(n: Int): String = this.repeat(n)
}
