package com.yashvant.shardwave.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progressFraction: Float,
    val speedBytesPerSec: Long,
    val etaSeconds: Long,
    val isCompleted: Boolean = false,
    val error: String? = null
)

/**
 * High-performance HTTP/HTTPS downloader for real GGUF AI models from HuggingFace and direct URLs.
 */
class ModelDownloader {

    companion object {
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB buffer
    }

    /**
     * Downloads a file from [downloadUrl] to [targetFile] emitting [DownloadProgress].
     */
    fun downloadModel(
        downloadUrl: String,
        targetFile: File
    ): Flow<DownloadProgress> = flow {
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            val url = URL(downloadUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Shardwave-P2P-Engine/1.0")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                emit(DownloadProgress(0, 0, 0f, 0, 0, isCompleted = false, error = "HTTP Error $responseCode"))
                return@flow
            }

            val totalLength = connection.contentLengthLong
            inputStream = connection.inputStream
            outputStream = FileOutputStream(tempFile, false)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesDownloaded = 0L
            var lastTime = System.currentTimeMillis()
            var bytesSinceLastTime = 0L

            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                bytesDownloaded += bytesRead
                bytesSinceLastTime += bytesRead

                val currentTime = System.currentTimeMillis()
                val deltaTime = currentTime - lastTime

                if (deltaTime >= 500) { // Emit progress every 500ms
                    val speed = if (deltaTime > 0) (bytesSinceLastTime * 1000) / deltaTime else 0L
                    val fraction = if (totalLength > 0) (bytesDownloaded.toFloat() / totalLength) else 0f
                    val remainingBytes = if (totalLength > 0) totalLength - bytesDownloaded else 0L
                    val eta = if (speed > 0) remainingBytes / speed else 0L

                    emit(
                        DownloadProgress(
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalLength,
                            progressFraction = fraction,
                            speedBytesPerSec = speed,
                            etaSeconds = eta,
                            isCompleted = false
                        )
                    )

                    lastTime = currentTime
                    bytesSinceLastTime = 0L
                }
            }

            outputStream.flush()
            outputStream.close()
            outputStream = null
            inputStream.close()
            inputStream = null

            // Move completed temp file to target file
            if (tempFile.exists()) {
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }

            emit(
                DownloadProgress(
                    bytesDownloaded = totalLength.coerceAtLeast(bytesDownloaded),
                    totalBytes = totalLength.coerceAtLeast(bytesDownloaded),
                    progressFraction = 1.0f,
                    speedBytesPerSec = 0,
                    etaSeconds = 0,
                    isCompleted = true
                )
            )

        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            emit(DownloadProgress(0, 0, 0f, 0, 0, isCompleted = false, error = e.message ?: "Download failed"))
        } finally {
            outputStream?.close()
            inputStream?.close()
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)
}
