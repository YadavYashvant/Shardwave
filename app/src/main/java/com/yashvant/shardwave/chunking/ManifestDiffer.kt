package com.yashvant.shardwave.chunking

import com.yashvant.shardwave.storage.ChunkStore

data class ManifestDiffResult(
    val missingChunks: List<ChunkMeta>,
    val reusedChunks: List<ChunkMeta>,
    val totalChunks: Int,
    val reusedCount: Int,
    val reusePercentage: Float,
    val bytesReused: Long,
    val bytesMissing: Long
)

object ManifestDiffer {

    /**
     * Diffs target [manifest] against local [chunkStore].
     *
     * Identifies which chunks are already locally present (reused) vs missing (need download).
     */
    fun diff(manifest: ModelManifest, chunkStore: ChunkStore): ManifestDiffResult {
        val knownHashes = chunkStore.listKnownHashes().map { it.lowercase() }.toSet()

        val missing = mutableListOf<ChunkMeta>()
        val reused = mutableListOf<ChunkMeta>()
        var bytesReused = 0L
        var bytesMissing = 0L

        for (chunk in manifest.chunks) {
            val cleanHash = chunk.hash.removePrefix("sha256:").lowercase()
            if (knownHashes.contains(cleanHash) || chunkStore.hasChunk(cleanHash)) {
                reused.add(chunk)
                bytesReused += chunk.size
            } else {
                missing.add(chunk)
                bytesMissing += chunk.size
            }
        }

        val totalCount = manifest.chunks.size
        val reusedCount = reused.size
        val reusePct = if (totalCount > 0) (reusedCount.toFloat() / totalCount) * 100f else 0f

        return ManifestDiffResult(
            missingChunks = missing,
            reusedChunks = reused,
            totalChunks = totalCount,
            reusedCount = reusedCount,
            reusePercentage = reusePct,
            bytesReused = bytesReused,
            bytesMissing = bytesMissing
        )
    }
}
