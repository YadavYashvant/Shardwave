package com.yashvant.shardwave.chunking

import com.yashvant.shardwave.verification.HashVerifier
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Random

data class ChunkInfo(
    val hash: String,
    val offset: Long,
    val size: Int,
    val bytes: ByteArray? = null
)

/**
 * FastCDC (Fast Content-Defined Chunking) engine using gear-hash rolling fingerprint.
 */
class FastCdcChunker(
    val minSize: Int = 512 * 1024,        // 512 KB
    val targetSize: Int = 2 * 1024 * 1024, // 2 MB
    val maxSize: Int = 8 * 1024 * 1024     // 8 MB
) {

    init {
        require(minSize > 0) { "minSize must be > 0" }
        require(minSize < targetSize) { "minSize must be < targetSize" }
        require(targetSize < maxSize) { "targetSize must be < maxSize" }
    }

    companion object {
        private val GEAR_TABLE: LongArray = run {
            val table = LongArray(256)
            val random = Random(0x46617374434443L) // "FastCDC" deterministic seed
            for (i in 0 until 256) {
                table[i] = random.nextLong()
            }
            table
        }

        private fun bitsToMask(bits: Int): Long {
            val clampedBits = bits.coerceIn(1, 62)
            return (1L shl clampedBits) - 1L
        }
    }

    private val maskNormalBits = log2Floor(targetSize)
    private val mask1 = bitsToMask(maskNormalBits + 1)
    private val mask2 = bitsToMask(maskNormalBits - 1)

    private fun log2Floor(n: Int): Int {
        var res = 0
        var temp = n
        while (temp > 1) {
            temp = temp ushr 1
            res++
        }
        return res
    }

    /**
     * Chunks a byte array into content-defined chunks.
     */
    fun chunk(data: ByteArray, keepBytes: Boolean = false): List<ChunkInfo> {
        val chunks = mutableListOf<ChunkInfo>()
        val dataLen = data.size
        var offset = 0

        while (offset < dataLen) {
            val remaining = dataLen - offset
            if (remaining <= minSize) {
                val chunkBytes = data.copyOfRange(offset, dataLen)
                val hash = HashVerifier.sha256(chunkBytes)
                chunks.add(
                    ChunkInfo(
                        hash = hash,
                        offset = offset.toLong(),
                        size = chunkBytes.size,
                        bytes = if (keepBytes) chunkBytes else null
                    )
                )
                break
            }

            val maxChunkLen = minOf(remaining, maxSize)
            var fp = 0L
            var i = minSize

            while (i < maxChunkLen) {
                val byteVal = data[offset + i].toInt() and 0xFF
                fp = (fp shl 1) + GEAR_TABLE[byteVal]

                val mask = if (i < targetSize) mask1 else mask2
                if ((fp and mask) == 0L) {
                    i++
                    break
                }
                i++
            }

            val chunkLen = i
            val chunkBytes = data.copyOfRange(offset, offset + chunkLen)
            val hash = HashVerifier.sha256(chunkBytes)
            chunks.add(
                ChunkInfo(
                    hash = hash,
                    offset = offset.toLong(),
                    size = chunkLen,
                    bytes = if (keepBytes) chunkBytes else null
                )
            )
            offset += chunkLen
        }

        return chunks
    }

    /**
     * Stream chunking for large files without loading the entire payload into RAM.
     */
    fun chunkFile(file: File, keepBytes: Boolean = false): List<ChunkInfo> {
        FileInputStream(file).use { fis ->
            return chunkStream(fis, file.length(), keepBytes)
        }
    }

    /**
     * Chunks an InputStream in chunks up to [maxSize].
     */
    fun chunkStream(inputStream: InputStream, totalSize: Long = -1L, keepBytes: Boolean = false): List<ChunkInfo> {
        val chunks = mutableListOf<ChunkInfo>()
        var globalOffset = 0L
        val buffer = ByteArray(maxSize)
        var bufferPos = 0

        while (true) {
            val read = inputStream.read(buffer, bufferPos, buffer.size - bufferPos)
            if (read == -1) break
            bufferPos += read

            if (bufferPos <= minSize) {
                continue
            }

            var fp = 0L
            var i = minSize

            while (i < bufferPos) {
                val byteVal = buffer[i].toInt() and 0xFF
                fp = (fp shl 1) + GEAR_TABLE[byteVal]

                val mask = if (i < targetSize) mask1 else mask2
                if ((fp and mask) == 0L || i == maxSize - 1) {
                    i++
                    break
                }
                i++
            }

            val chunkLen = i
            val chunkBytes = buffer.copyOfRange(0, chunkLen)
            val hash = HashVerifier.sha256(chunkBytes)
            chunks.add(
                ChunkInfo(
                    hash = hash,
                    offset = globalOffset,
                    size = chunkLen,
                    bytes = if (keepBytes) chunkBytes else null
                )
            )

            globalOffset += chunkLen

            // Shift remaining un-chunked bytes in buffer to start
            val remaining = bufferPos - chunkLen
            System.arraycopy(buffer, chunkLen, buffer, 0, remaining)
            bufferPos = remaining
        }

        // Process leftover buffer
        if (bufferPos > 0) {
            val tailBytes = buffer.copyOfRange(0, bufferPos)
            val hash = HashVerifier.sha256(tailBytes)
            chunks.add(
                ChunkInfo(
                    hash = hash,
                    offset = globalOffset,
                    size = bufferPos,
                    bytes = if (keepBytes) tailBytes else null
                )
            )
        }

        return chunks
    }
}
