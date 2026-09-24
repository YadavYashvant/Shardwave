package com.yashvant.shardwave

import com.yashvant.shardwave.swarm.PeerStatsTracker
import com.yashvant.shardwave.swarm.SwarmManager
import com.yashvant.shardwave.swarm.TorrentBuilderHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Phase2SwarmTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testDataFile: File
    private lateinit var torrentOutputFile: File

    @Before
    fun setUp() {
        testDataFile = tempFolder.newFile("model_test_payload.bin")
        testDataFile.writeBytes(ByteArray(256 * 1024) { (it % 256).toByte() })

        torrentOutputFile = File(tempFolder.root, "model_test_payload.torrent")
    }

    @Test
    fun testTorrentBuilderHelperCreatesValidTorrent() {
        val torrentFile = TorrentBuilderHelper.createTorrent(
            inputFile = testDataFile,
            outputTorrentFile = torrentOutputFile,
            pieceSize = 64 * 1024
        )

        assertTrue(torrentFile.exists())
        assertTrue(torrentFile.length() > 0)
    }

    @Test
    fun testPeerStatsTrackerStateFlow() {
        val tracker = PeerStatsTracker()
        tracker.setTransferMeta(totalChunks = 10, reusedChunks = 4)

        var currentStats = tracker.stats.value
        assertEquals(10, currentStats.chunksTotal)
        assertEquals(4, currentStats.chunksReusedLocal)
        assertEquals(40.0f, currentStats.reusePercentage, 0.01f)
        assertEquals(0, currentStats.chunksVerified)

        repeat(6) {
            tracker.incrementVerifiedChunks()
        }

        currentStats = tracker.stats.value
        assertEquals(6, currentStats.chunksVerified)
        assertTrue(currentStats.isTransferComplete)
        assertNotNull(currentStats.transferEndMs)
    }

    @Test
    fun testSwarmManagerLifecycle() {
        val tracker = PeerStatsTracker()
        val manager = SwarmManager(tracker)

        manager.startSession()
        manager.stopSession()
        assertTrue(!tracker.stats.value.isSessionActive)
    }
}
