package com.yashvant.shardwave.storage

import com.yashvant.shardwave.verification.HashVerifier
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/**
 * Reconstructs a complete target file (e.g. GGUF model) from a sequence of chunk hashes stored in [ChunkStore].
 */
class ModelAssembler(private val chunkStore: ChunkStore) {

    /**
     * Assembles a destination file from an ordered list of [chunkHashes].
     */
    fun assemble(
        destination: File,
        chunkHashes: List<String>,
        expectedTotalHash: String? = null
    ): Result<File> {
        destination.parentFile?.mkdirs()
        if (destination.exists()) {
            destination.delete()
        }

        try {
            // Calculate total expected file length
            val totalLength = chunkHashes.sumOf { hash ->
                chunkStore.getChunkSize(hash).takeIf { it > 0 }
                    ?: throw IllegalStateException("Missing chunk: $hash")
            }

            RandomAccessFile(destination, "rw").use { raf ->
                raf.setLength(totalLength) // Pre-allocate file on disk
                val destChannel: FileChannel = raf.channel
                var destPos = 0L

                for ((index, hash) in chunkHashes.withIndex()) {
                    val chunkFile = chunkStore.getChunk(hash)
                        ?: return Result.failure(
                            IllegalStateException("Missing chunk at index $index: $hash")
                        )

                    FileInputStream(chunkFile).use { fis ->
                        val srcChannel = fis.channel
                        val srcSize = srcChannel.size()
                        var transferred = 0L
                        while (transferred < srcSize) {
                            val bytes = srcChannel.transferTo(transferred, srcSize - transferred, destChannel.position(destPos + transferred))
                            if (bytes <= 0) break
                            transferred += bytes
                        }
                        destPos += srcSize
                    }
                }
            }

            if (expectedTotalHash != null) {
                val isValid = HashVerifier.verify(destination, expectedTotalHash)
                if (!isValid) {
                    val actualHash = HashVerifier.sha256(destination)
                    destination.delete()
                    return Result.failure(
                        IllegalStateException("Assembled file hash mismatch. Expected: $expectedTotalHash, Actual: $actualHash")
                    )
                }
            }

            return Result.success(destination)

        } catch (e: Exception) {
            if (destination.exists()) destination.delete()
            return Result.failure(e)
        }
    }

    /**
     * Pre-allocates destination file to [totalSize] and populates known [chunkOffsetMap] at specified offsets.
     */
    fun preallocateAndPopulate(
        destination: File,
        totalSize: Long,
        chunkOffsetMap: List<Pair<String, Long>>
    ): Result<File> {
        destination.parentFile?.mkdirs()
        try {
            RandomAccessFile(destination, "rw").use { raf ->
                if (totalSize > 0) {
                    raf.setLength(totalSize) // Pre-allocate whole file
                }
                val destChannel = raf.channel
                for ((hash, offset) in chunkOffsetMap) {
                    val chunkFile = chunkStore.getChunk(hash) ?: continue
                    FileInputStream(chunkFile).use { fis ->
                        val srcChannel = fis.channel
                        val srcSize = srcChannel.size()
                        var transferred = 0L
                        while (transferred < srcSize) {
                            val bytes = srcChannel.transferTo(transferred, srcSize - transferred, destChannel.position(offset + transferred))
                            if (bytes <= 0) break
                            transferred += bytes
                        }
                    }
                }
            }
            return Result.success(destination)
        } catch (e: Exception) {
            if (destination.exists()) destination.delete()
            return Result.failure(e)
        }
    }
}
