package com.yashvant.shardwave.verification

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Optimized utility for SHA-256 hash calculation and verification over bytes, buffers, and files.
 */
object HashVerifier {

    private const val ALGORITHM = "SHA-256"
    private const val BUFFER_SIZE = 64 * 1024 // 64 KB for high-throughput streaming

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val hashBytes = digest.digest(bytes)
        return bytesToHex(hashBytes)
    }

    fun sha256(buffer: ByteBuffer): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val readOnlyBuffer = buffer.duplicate()
        readOnlyBuffer.rewind()
        val temp = ByteArray(minOf(BUFFER_SIZE, readOnlyBuffer.remaining()))
        while (readOnlyBuffer.hasRemaining()) {
            val length = minOf(readOnlyBuffer.remaining(), temp.size)
            readOnlyBuffer.get(temp, 0, length)
            digest.update(temp, 0, length)
        }
        return bytesToHex(digest.digest())
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return bytesToHex(digest.digest())
    }

    fun verify(file: File, expectedHash: String): Boolean {
        val normalizedExpected = expectedHash.removePrefix("sha256:").lowercase()
        val actualHash = sha256(file).lowercase()
        return actualHash == normalizedExpected
    }

    fun verify(bytes: ByteArray, expectedHash: String): Boolean {
        val normalizedExpected = expectedHash.removePrefix("sha256:").lowercase()
        val actualHash = sha256(bytes).lowercase()
        return actualHash == normalizedExpected
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val result = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            result[i * 2] = HEX_CHARS[v ushr 4]
            result[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(result)
    }
}
