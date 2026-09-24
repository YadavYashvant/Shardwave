package com.yashvant.shardwave.swarm

import com.yashvant.shardwave.data.SwarmStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.libtorrent4j.AlertListener
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.PeerConnectAlert
import org.libtorrent4j.alerts.PeerDisconnectedAlert
import org.libtorrent4j.alerts.PieceFinishedAlert
import org.libtorrent4j.alerts.SessionStatsAlert

/**
 * Tracks libtorrent session alerts and exposes reactive [SwarmStats].
 */
class PeerStatsTracker : AlertListener {

    private val _stats = MutableStateFlow(SwarmStats())
    val stats: StateFlow<SwarmStats> = _stats.asStateFlow()

    fun updateSessionState(active: Boolean) {
        _stats.update { it.copy(isSessionActive = active) }
    }

    fun setTransferMeta(totalChunks: Int, reusedChunks: Int) {
        _stats.update {
            it.copy(
                chunksTotal = totalChunks,
                chunksReusedLocal = reusedChunks,
                transferStartMs = System.currentTimeMillis(),
                transferEndMs = null,
                chunksVerified = 0,
                chunksRejected = 0
            )
        }
    }

    fun incrementVerifiedChunks() {
        _stats.update { current ->
            val updatedVerified = current.chunksVerified + 1
            val isFinished = current.chunksTotal > 0 && (updatedVerified + current.chunksReusedLocal) >= current.chunksTotal
            current.copy(
                chunksVerified = updatedVerified,
                transferEndMs = if (isFinished) System.currentTimeMillis() else current.transferEndMs
            )
        }
    }

    fun incrementRejectedChunks() {
        _stats.update { it.copy(chunksRejected = it.chunksRejected + 1) }
    }

    fun updateTorrentStats(
        peers: Int,
        downSpeed: Long,
        upSpeed: Long,
        totalDown: Long,
        totalUp: Long,
        progress: Float
    ) {
        _stats.update { current ->
            val isFinished = progress >= 1.0f
            current.copy(
                connectedPeers = peers,
                downloadSpeedBytesPerSec = downSpeed,
                uploadSpeedBytesPerSec = upSpeed,
                totalDownloadedBytes = totalDown,
                totalUploadedBytes = totalUp,
                progressFraction = progress,
                transferEndMs = if (isFinished && current.transferEndMs == null) System.currentTimeMillis() else current.transferEndMs
            )
        }
    }

    override fun types(): IntArray? {
        return intArrayOf(
            AlertType.PEER_CONNECT.swig(),
            AlertType.PEER_DISCONNECTED.swig(),
            AlertType.PIECE_FINISHED.swig(),
            AlertType.SESSION_STATS.swig()
        )
    }

    override fun alert(alert: Alert<*>?) {
        when (alert) {
            is PeerConnectAlert -> {
                _stats.update { it.copy(connectedPeers = it.connectedPeers + 1) }
            }
            is PeerDisconnectedAlert -> {
                _stats.update { it.copy(connectedPeers = maxOf(0, it.connectedPeers - 1)) }
            }
            is PieceFinishedAlert -> {
                incrementVerifiedChunks()
            }
            is SessionStatsAlert -> {
                // Fired periodically by libtorrent
            }
        }
    }
}
