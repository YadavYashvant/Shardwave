package com.yashvant.shardwave.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yashvant.shardwave.ui.components.AddModelDialog
import com.yashvant.shardwave.ui.main.ModelItem
import com.yashvant.shardwave.ui.main.ModelStatus
import com.yashvant.shardwave.ui.theme.CupertinoFont
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import io.github.alexzhirkevich.cupertino.CupertinoButton
import io.github.alexzhirkevich.cupertino.CupertinoButtonDefaults
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.CupertinoSurface
import io.github.alexzhirkevich.cupertino.CupertinoText
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.PlusCircle
import io.github.alexzhirkevich.cupertino.theme.CupertinoColors
import io.github.alexzhirkevich.cupertino.theme.CupertinoTheme
import io.github.alexzhirkevich.cupertino.theme.Gray
import io.github.alexzhirkevich.cupertino.theme.systemGray6

@OptIn(ExperimentalCupertinoApi::class)
@Composable
fun CupertinoCatalogScreen(
    catalog: List<ModelItem>,
    selectedModel: ModelItem?,
    onSelectModel: (ModelItem) -> Unit,
    onStartDownload: (ModelItem) -> Unit,
    onAddModel: (name: String, url: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddModelDialog(
            onDismiss = { showAddDialog = false },
            onAddModel = onAddModel
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CupertinoText(
                    text = "AI Model Catalog",
                    style = CupertinoTheme.typography.largeTitle,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CupertinoFont
                )
                CupertinoText(
                    text = "P2P BitTorrent Swarm Distribution & FastCDC Delta Sync",
                    style = CupertinoTheme.typography.subhead,
                    color = CupertinoColors.Gray,
                    fontFamily = CupertinoFont
                )
            }
            CupertinoButton(
                onClick = { showAddDialog = true },
                colors = CupertinoButtonDefaults.plainButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CupertinoIcon(imageVector = CupertinoIcons.Outlined.PlusCircle, contentDescription = "Add")
                    Spacer(modifier = Modifier.padding(start = 4.dp))
                    CupertinoText("Add", fontFamily = CupertinoFont)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(catalog) { item ->
                ModelCard(
                    item = item,
                    isSelected = selectedModel?.id == item.id,
                    onSelect = { onSelectModel(item) },
                    onDownload = { onStartDownload(item) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalCupertinoApi::class)
@Composable
private fun ModelCard(
    item: ModelItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit
) {
    CupertinoSurface(
        onClick = onSelect,
        color = CupertinoColors.systemGray6,
        shape = CupertinoTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CupertinoText(
                    text = item.name,
                    style = CupertinoTheme.typography.title3,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CupertinoFont
                )
                Spacer(modifier = Modifier.height(2.dp))
                CupertinoText(
                    text = "Version ${item.version} • ${item.sizeMb} MB",
                    style = CupertinoTheme.typography.footnote,
                    color = CupertinoColors.Gray,
                    fontFamily = CupertinoFont
                )
            }

            when (item.status) {
                ModelStatus.READY -> {
                    CupertinoButton(
                        onClick = onSelect,
                        enabled = !isSelected,
                        colors = CupertinoButtonDefaults.plainButtonColors()
                    ) {
                        CupertinoText(
                            text = if (isSelected) "Active" else "Select",
                            fontFamily = CupertinoFont
                        )
                    }
                }
                ModelStatus.DOWNLOADING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CupertinoActivityIndicator()
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        CupertinoText(
                            text = "Syncing",
                            style = CupertinoTheme.typography.footnote,
                            fontFamily = CupertinoFont
                        )
                    }
                }
                ModelStatus.CATALOG -> {
                    CupertinoButton(
                        onClick = onDownload,
                        colors = CupertinoButtonDefaults.filledButtonColors()
                    ) {
                        CupertinoText(
                            text = "Download",
                            fontFamily = CupertinoFont
                        )
                    }
                }
            }
        }
    }
}
