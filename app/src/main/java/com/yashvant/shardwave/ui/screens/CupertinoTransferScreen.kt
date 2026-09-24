package com.yashvant.shardwave.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yashvant.shardwave.data.SwarmStats
import com.yashvant.shardwave.ui.components.CupertinoChunkProgressBar
import com.yashvant.shardwave.ui.components.CupertinoSwarmStatsCard
import com.yashvant.shardwave.ui.theme.CupertinoFont
import io.github.alexzhirkevich.cupertino.CupertinoText
import io.github.alexzhirkevich.cupertino.theme.*

@Composable
fun CupertinoTransferScreen(
    stats: SwarmStats,
    magnetLink: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        CupertinoText(
            text = "Active Transfer Telemetry",
            style = CupertinoTheme.typography.largeTitle,
            fontWeight = FontWeight.Bold,
            fontFamily = CupertinoFont
        )
        CupertinoText(
            text = "Realtime P2P Swarm & FastCDC Chunk Telemetry",
            style = CupertinoTheme.typography.subhead,
            color = CupertinoColors.Gray,
            fontFamily = CupertinoFont
        )
        Spacer(modifier = Modifier.height(16.dp))

        CupertinoSwarmStatsCard(
            stats = stats,
            magnetLink = magnetLink
        )

        Spacer(modifier = Modifier.height(20.dp))

        CupertinoChunkProgressBar(
            totalChunks = stats.chunksTotal,
            reusedChunks = stats.chunksReusedLocal,
            verifiedChunks = stats.chunksVerified,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
