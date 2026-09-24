package com.yashvant.shardwave.data

import com.yashvant.shardwave.chunking.FastCdcChunker
import com.yashvant.shardwave.chunking.ManifestBuilder
import com.yashvant.shardwave.chunking.ManifestDiffResult
import com.yashvant.shardwave.chunking.ManifestDiffer
import com.yashvant.shardwave.chunking.ModelManifest
import com.yashvant.shardwave.storage.ChunkStore
import com.yashvant.shardwave.storage.ModelAssembler
import com.yashvant.shardwave.swarm.SwarmManager
import com.yashvant.shardwave.swarm.TorrentBuilderHelper
import com.yashvant.shardwave.verification.HashVerifier
import kotlinx.coroutines.flow.StateFlow
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File

data class PreparedTransfer(
    val diffResult: ManifestDiffResult,
    val destinationFile: File,
    val torrentHandle: TorrentHandle?
)

/**
 * Repository orchestrating AI model manifests, FastCDC chunking, delta transfers, and BitTorrent P2P distribution.
 */
class ModelRepository(
    val baseDir: File,
    val chunkStore: ChunkStore = ChunkStore(File(baseDir, "chunks")),
    val swarmManager: SwarmManager = SwarmManager()
) {

    val modelAssembler = ModelAssembler(chunkStore)
    val swarmStats: StateFlow<SwarmStats> = swarmManager.stats

    fun startSwarmSession() {
        swarmManager.startSession()
    }

    fun stopSwarmSession() {
        swarmManager.stopSession()
    }

    /**
     * Seeds a completed GGUF model file on the local Wi-Fi P2P swarm via Local Service Discovery (LSD).
     */
    fun seedModel(modelFile: File): TorrentHandle? {
        if (!modelFile.exists()) return null
        return try {
            val torrentsDir = File(baseDir, "torrents").apply { mkdirs() }
            val torrentFile = File(torrentsDir, "${modelFile.name}.torrent")

            TorrentBuilderHelper.createTorrent(
                inputFile = modelFile,
                outputTorrentFile = torrentFile
            )

            swarmManager.addTorrent(torrentFile, modelFile.parentFile ?: baseDir)
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Generates a P2P magnet URI link for sharing a local model file.
     */
    fun generateMagnetLink(modelFile: File): String {
        val torrentsDir = File(baseDir, "torrents")
        val torrentFile = File(torrentsDir, "${modelFile.name}.torrent")
        if (!torrentFile.exists()) {
            seedModel(modelFile)
        }
        return try {
            val torrentInfo = TorrentInfo(torrentFile)
            "magnet:?xt=urn:btih:${torrentInfo.infoHash()}&dn=${modelFile.name}"
        } catch (e: Throwable) {
            "magnet:?xt=urn:btih:${HashVerifier.sha256(modelFile)}&dn=${modelFile.name}"
        }
    }

    /**
     * Ingests a local file as a model version: runs FastCDC, indexes chunks into [ChunkStore] via NIO stream, and returns [ModelManifest].
     */
    fun ingestLocalModel(
        modelId: String,
        version: Int,
        inputFile: File,
        chunker: FastCdcChunker = FastCdcChunker()
    ): ModelManifest {
        require(inputFile.exists()) { "Model file not found: ${inputFile.absolutePath}" }

        val chunkInfos = chunker.chunkFile(inputFile, keepBytes = false)

        for (info in chunkInfos) {
            chunkStore.putChunkStream(info.hash, inputFile, info.offset, info.size.toLong())
        }

        val totalHash = HashVerifier.sha256(inputFile)
        return ManifestBuilder.buildManifest(
            modelId = modelId,
            version = version,
            totalSize = inputFile.length(),
            totalHash = totalHash,
            chunkInfos = chunkInfos
        )
    }

    /**
     * Prepares a delta transfer for [targetManifest]:
     * 1. Diffs target manifest against local ChunkStore
     * 2. Pre-allocates destination file with local reused chunks
     * 3. Configures swarm telemetry tracker with total/reused counts
     * 4. Adds torrent file if available and triggers forceRecheck
     */
    fun prepareDeltaDownload(
        targetManifest: ModelManifest,
        destinationFile: File,
        torrentFile: File? = null
    ): PreparedTransfer {
        val diff = ManifestDiffer.diff(targetManifest, chunkStore)

        // Pre-populate reused chunks at their exact offsets in the target file
        val reusedPairs = diff.reusedChunks.map { it.hash to it.offset }
        modelAssembler.preallocateAndPopulate(
            destination = destinationFile,
            totalSize = targetManifest.totalSize,
            chunkOffsetMap = reusedPairs
        )

        // Telemetry update
        swarmManager.tracker.setTransferMeta(
            totalChunks = diff.totalChunks,
            reusedChunks = diff.reusedCount
        )

        var handle: TorrentHandle? = null
        if (torrentFile != null && torrentFile.exists()) {
            handle = swarmManager.addTorrent(torrentFile, destinationFile.parentFile ?: baseDir)
            handle?.forceRecheck()
        }

        return PreparedTransfer(
            diffResult = diff,
            destinationFile = destinationFile,
            torrentHandle = handle
        )
    }

    /**
     * Finalizes and verifies destination model file after transfer completion.
     */
    fun verifyAndFinalizeModel(destinationFile: File, expectedTotalHash: String): Boolean {
        val isValid = HashVerifier.verify(destinationFile, expectedTotalHash)
        if (isValid) {
            // Index newly downloaded chunks from verified final file into ChunkStore using stream chunker
            val chunker = FastCdcChunker()
            val chunkInfos = chunker.chunkFile(destinationFile, keepBytes = false)
            for (info in chunkInfos) {
                chunkStore.putChunkStream(info.hash, destinationFile, info.offset, info.size.toLong())
            }
            seedModel(destinationFile)
        }
        return isValid
    }
}
