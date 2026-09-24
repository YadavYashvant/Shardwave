package com.yashvant.shardwave.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.yashvant.shardwave.ui.theme.CupertinoFont
import io.github.alexzhirkevich.cupertino.CupertinoText
import io.github.alexzhirkevich.cupertino.theme.*

@Composable
fun CupertinoChunkProgressBar(
    totalChunks: Int,
    reusedChunks: Int,
    verifiedChunks: Int,
    modifier: Modifier = Modifier
) {
    val safeTotal = maxOf(1, totalChunks)
    val reusedWeight = (reusedChunks.toFloat() / safeTotal).coerceIn(0f, 1f)
    val verifiedWeight = (verifiedChunks.toFloat() / safeTotal).coerceIn(0f, 1f - reusedWeight)
    val pendingWeight = (1f - reusedWeight - verifiedWeight).coerceAtLeast(0f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CupertinoText(
                text = "Delta Chunk Transfer Map",
                style = CupertinoTheme.typography.subhead,
                fontFamily = CupertinoFont
            )
            Spacer(modifier = Modifier.weight(1f))
            CupertinoText(
                text = "$reusedChunks Reused • $verifiedChunks Downloaded / $totalChunks Total",
                style = CupertinoTheme.typography.footnote,
                color = CupertinoColors.Gray,
                fontFamily = CupertinoFont
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CupertinoColors.systemGray5)
        ) {
            if (reusedWeight > 0f) {
                Box(
                    modifier = Modifier
                        .weight(reusedWeight)
                        .height(18.dp)
                        .background(CupertinoColors.systemGreen) // iOS Green for local reuse
                )
            }
            if (verifiedWeight > 0f) {
                Box(
                    modifier = Modifier
                        .weight(verifiedWeight)
                        .height(18.dp)
                        .background(CupertinoColors.systemBlue) // iOS Blue for P2P downloaded
                )
            }
            if (pendingWeight > 0f) {
                Box(
                    modifier = Modifier
                        .weight(pendingWeight)
                        .height(18.dp)
                        .background(CupertinoColors.systemGray4) // iOS Gray for pending
                )
            }
        }
    }
}
