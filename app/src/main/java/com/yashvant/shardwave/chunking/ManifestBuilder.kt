package com.yashvant.shardwave.chunking

data class ChunkMeta(
    val hash: String,
    val offset: Long,
    val size: Int
)

data class ModelManifest(
    val modelId: String,
    val version: Int,
    val totalSize: Long,
    val totalHash: String,
    val chunks: List<ChunkMeta>
) {
    fun toJson(): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"modelId\": \"$modelId\",\n")
        sb.append("  \"version\": $version,\n")
        sb.append("  \"totalSize\": $totalSize,\n")
        sb.append("  \"totalHash\": \"$totalHash\",\n")
        sb.append("  \"chunks\": [\n")

        chunks.forEachIndexed { index, c ->
            sb.append("    {\"hash\": \"${c.hash}\", \"offset\": ${c.offset}, \"size\": ${c.size}}")
            if (index < chunks.size - 1) sb.append(",")
            sb.append("\n")
        }

        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): ModelManifest {
            fun extractString(key: String): String {
                val regex = "\"$key\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                return regex.find(jsonStr)?.groupValues?.get(1) ?: ""
            }

            fun extractLong(key: String): Long {
                val regex = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                return regex.find(jsonStr)?.groupValues?.get(1)?.toLong() ?: 0L
            }

            fun extractInt(key: String): Int {
                val regex = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                return regex.find(jsonStr)?.groupValues?.get(1)?.toInt() ?: 0
            }

            val modelId = extractString("modelId")
            val version = extractInt("version")
            val totalSize = extractLong("totalSize")
            val totalHash = extractString("totalHash")

            val chunksList = mutableListOf<ChunkMeta>()
            val chunkRegex = "\\{\\s*\"hash\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"offset\"\\s*:\\s*(\\d+)\\s*,\\s*\"size\"\\s*:\\s*(\\d+)\\s*\\}".toRegex()
            chunkRegex.findAll(jsonStr).forEach { match ->
                val hash = match.groupValues[1]
                val offset = match.groupValues[2].toLong()
                val size = match.groupValues[3].toInt()
                chunksList.add(ChunkMeta(hash, offset, size))
            }

            return ModelManifest(
                modelId = modelId,
                version = version,
                totalSize = totalSize,
                totalHash = totalHash,
                chunks = chunksList
            )
        }
    }
}

object ManifestBuilder {

    fun buildManifest(
        modelId: String,
        version: Int,
        totalSize: Long,
        totalHash: String,
        chunkInfos: List<ChunkInfo>
    ): ModelManifest {
        val chunkMetas = chunkInfos.map {
            ChunkMeta(hash = it.hash, offset = it.offset, size = it.size)
        }
        return ModelManifest(
            modelId = modelId,
            version = version,
            totalSize = totalSize,
            totalHash = totalHash,
            chunks = chunkMetas
        )
    }
}
