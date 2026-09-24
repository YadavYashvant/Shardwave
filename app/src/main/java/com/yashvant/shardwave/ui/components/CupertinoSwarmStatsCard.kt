package com.yashvant.shardwave.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yashvant.shardwave.data.SwarmStats
import com.yashvant.shardwave.ui.theme.CupertinoFont
import io.github.alexzhirkevich.cupertino.CupertinoButton
import io.github.alexzhirkevich.cupertino.CupertinoButtonDefaults
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.CupertinoSurface
import io.github.alexzhirkevich.cupertino.CupertinoText
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.SquareAndArrowUp
import io.github.alexzhirkevich.cupertino.theme.*

@OptIn(ExperimentalCupertinoApi::class)
@Composable
fun CupertinoSwarmStatsCard(
    stats: SwarmStats,
    magnetLink: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    CupertinoSurface(
        color = CupertinoColors.systemGray6,
        shape = CupertinoTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CupertinoText(
                    text = "P2P Swarm Instrumentation",
                    style = CupertinoTheme.typography.title3,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CupertinoFont,
                    modifier = Modifier.weight(1f)
                )

                if (!magnetLink.isNull_or_blank()) {
                    CupertinoButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Shardwave Magnet", magnetLink)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Magnet Link Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        colors = CupertinoButtonDefaults.plainButtonColors()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CupertinoIcon(imageVector = CupertinoIcons.Outlined.SquareAndArrowUp, contentDescription = "Copy Magnet")
                            Spacer(modifier = Modifier.padding(start = 4.dp))
                            CupertinoText("Magnet", fontFamily = CupertinoFont)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(label = "Connected Peers", value = "${stats.connectedPeers}", modifier = Modifier.weight(1f))
                StatItem(label = "Local Reuse", value = "%.1f%%".format(stats.reusePercentage), modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(label = "Down Speed", value = formatSpeed(stats.downloadSpeedBytesPerSec), modifier = Modifier.weight(1f))
                StatItem(label = "Up Speed", value = formatSpeed(stats.uploadSpeedBytesPerSec), modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(label = "Chunks Verified", value = "${stats.chunksVerified}", modifier = Modifier.weight(1f))
                StatItem(label = "Chunks Reused", value = "${stats.chunksReusedLocal} / ${stats.chunksTotal}", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        CupertinoText(
            text = label,
            style = CupertinoTheme.typography.footnote,
            color = CupertinoColors.Gray,
            fontFamily = CupertinoFont
        )
        CupertinoText(
            text = value,
            style = CupertinoTheme.typography.body,
            fontWeight = FontWeight.SemiBold,
            fontFamily = CupertinoFont
        )
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> "%.2f MB/s".format(bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024 -> "%.1f KB/s".format(bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
