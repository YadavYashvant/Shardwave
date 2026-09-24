package com.yashvant.shardwave.swarm

import com.yashvant.shardwave.data.SwarmStats
import kotlinx.coroutines.flow.StateFlow
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages libtorrent SessionManager configured for local network P2P model distribution.
 */
class SwarmManager(val tracker: PeerStatsTracker = PeerStatsTracker()) {

    var sessionManager: SessionManager? = null
        private set
    private val activeHandles = ConcurrentHashMap<String, TorrentHandle>()

    val stats: StateFlow<SwarmStats> = tracker.stats

    fun startSession() {
        try {
            if (sessionManager == null) {
                sessionManager = SessionManager()
            }
            val sm = sessionManager ?: return
            if (sm.isRunning) return

            val sp = SettingsPack()
            val swigSp = sp.swig()
            swigSp.set_bool(settings_pack.bool_types.enable_lsd.swigValue(), true)
            swigSp.set_bool(settings_pack.bool_types.enable_dht.swigValue(), false)
            swigSp.set_bool(settings_pack.bool_types.enable_upnp.swigValue(), false)
            swigSp.set_bool(settings_pack.bool_types.enable_natpmp.swigValue(), false)
            swigSp.set_str(settings_pack.string_types.listen_interfaces.swigValue(), "0.0.0.0:6881")

            sm.addListener(tracker)
            sm.start(SessionParams(sp))
            tracker.updateSessionState(true)
        } catch (e: Throwable) {
            tracker.updateSessionState(false)
        }
    }

    fun stopSession() {
        try {
            val sm = sessionManager
            if (sm != null && sm.isRunning) {
                sm.stop()
            }
        } catch (ignored: Throwable) {
        } finally {
            activeHandles.clear()
            tracker.updateSessionState(false)
        }
    }

    fun addTorrent(torrentFile: File, saveDir: File): TorrentHandle? {
        require(torrentFile.exists()) { "Torrent file not found: ${torrentFile.absolutePath}" }
        val sm = sessionManager ?: return null
        val torrentInfo = TorrentInfo(torrentFile)
        val handle = sm.find(torrentInfo.infoHash())
            ?: run {
                sm.download(torrentInfo, saveDir)
                sm.find(torrentInfo.infoHash())
            }

        handle?.let {
            activeHandles[torrentInfo.infoHash().toString()] = it
        }

        return handle
    }

    fun getHandle(infoHash: String): TorrentHandle? {
        return activeHandles[infoHash]
    }

    fun pollStats() {
        for (handle in activeHandles.values) {
            if (!handle.isValid) continue
            val status = handle.status()
            tracker.updateTorrentStats(
                peers = status.numPeers(),
                downSpeed = status.downloadPayloadRate().toLong(),
                upSpeed = status.uploadPayloadRate().toLong(),
                totalDown = status.totalDone(),
                totalUp = status.totalUpload(),
                progress = status.progress()
            )
        }
    }
}
