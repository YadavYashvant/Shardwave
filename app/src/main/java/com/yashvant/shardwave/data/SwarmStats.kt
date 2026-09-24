package com.yashvant.shardwave.data

/**
 * Real-time telemetry and state metrics for the P2P swarm and delta transfer engine.
 */
data class SwarmStats(
    val isSessionActive: Boolean = false,
    val connectedPeers: Int = 0,
    val downloadSpeedBytesPerSec: Long = 0L,
    val uploadSpeedBytesPerSec: Long = 0L,
    val totalDownloadedBytes: Long = 0L,
    val totalUploadedBytes: Long = 0L,
    val progressFraction: Float = 0f,
    val chunksVerified: Int = 0,
    val chunksRejected: Int = 0,
    val chunksReusedLocal: Int = 0,
    val chunksTotal: Int = 0,
    val transferStartMs: Long = 0L,
    val transferEndMs: Long? = null
) {
    val reusePercentage: Float
        get() = if (chunksTotal > 0) (chunksReusedLocal.toFloat() / chunksTotal) * 100f else 0f

    val isTransferComplete: Boolean
        get() = progressFraction >= 1.0f || (chunksTotal > 0 && (chunksReusedLocal + chunksVerified) >= chunksTotal)
}
