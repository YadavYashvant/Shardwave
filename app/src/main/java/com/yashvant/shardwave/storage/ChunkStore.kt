package com.yashvant.shardwave.storage

import com.yashvant.shardwave.verification.HashVerifier
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel

/**
 * Content-addressed storage for chunks.
 * Chunks are saved under [baseDir]/<sha256>.chk.
 */
class ChunkStore(private val baseDir: File) {

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    private fun chunkFile(hash: String): File {
        val cleanHash = hash.removePrefix("sha256:").lowercase()
        return File(baseDir, "$cleanHash.chk")
    }

    fun hasChunk(hash: String): Boolean {
        val file = chunkFile(hash)
        return file.exists() && file.length() > 0
    }

    fun putChunk(hash: String, data: ByteArray): File {
        val cleanHash = hash.removePrefix("sha256:").lowercase()
        require(HashVerifier.verify(data, cleanHash)) {
            "Data hash mismatch for $hash"
        }
        val targetFile = chunkFile(cleanHash)
        if (!targetFile.exists()) {
            val tempFile = File(baseDir, "$cleanHash.tmp")
            try {
                tempFile.writeBytes(data)
                val moved = tempFile.renameTo(targetFile)
                if (!moved) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                throw e
            }
        }
        return targetFile
    }

    /**
     * Streams chunk bytes directly from [sourceFile] at [offset] to `.chk` without loading into JVM RAM.
     */
    fun putChunkStream(hash: String, sourceFile: File, offset: Long, size: Long): File {
        val cleanHash = hash.removePrefix("sha256:").lowercase()
        val targetFile = chunkFile(cleanHash)
        if (!targetFile.exists()) {
            val tempFile = File(baseDir, "$cleanHash.tmp")
            try {
                FileInputStream(sourceFile).use { fis ->
                    val srcChannel = fis.channel
                    FileOutputStream(tempFile).use { fos ->
                        val destChannel = fos.channel
                        var transferred = 0L
                        while (transferred < size) {
                            val bytes = srcChannel.transferTo(offset + transferred, size - transferred, destChannel)
                            if (bytes <= 0) break
                            transferred += bytes
                        }
                    }
                }
                val moved = tempFile.renameTo(targetFile)
                if (!moved) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                throw e
            }
        }
        return targetFile
    }

    fun getChunk(hash: String): File? {
        val file = chunkFile(hash)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun getChunkBytes(hash: String): ByteArray? {
        val file = getChunk(hash) ?: return null
        return file.readBytes()
    }

    fun getChunkSize(hash: String): Long {
        val file = getChunk(hash) ?: return -1L
        return file.length()
    }

    fun listKnownHashes(): Set<String> {
        val files = baseDir.listFiles { _, name -> name.endsWith(".chk") } ?: return emptySet()
        return files.map { it.name.removeSuffix(".chk") }.toSet()
    }

    fun clear() {
        baseDir.listFiles()?.forEach { it.delete() }
    }
}
