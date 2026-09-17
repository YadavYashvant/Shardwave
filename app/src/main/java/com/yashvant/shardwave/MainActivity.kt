package com.yashvant.shardwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yashvant.shardwave.ui.main.MainViewModel
import com.yashvant.shardwave.ui.screens.CupertinoCatalogScreen
import com.yashvant.shardwave.ui.screens.CupertinoInferenceScreen
import com.yashvant.shardwave.ui.screens.CupertinoTransferScreen
import com.yashvant.shardwave.ui.theme.CupertinoFont
import com.yashvant.shardwave.ui.theme.ShardwaveCupertinoTheme
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.CupertinoNavigationBar
import io.github.alexzhirkevich.cupertino.CupertinoNavigationBarItem
import io.github.alexzhirkevich.cupertino.CupertinoScaffold
import io.github.alexzhirkevich.cupertino.CupertinoText
import io.github.alexzhirkevich.cupertino.CupertinoTopAppBar
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.ArrowDownCircle
import io.github.alexzhirkevich.cupertino.icons.outlined.Cpu
import io.github.alexzhirkevich.cupertino.icons.outlined.Folder

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShardwaveCupertinoTheme {
                ShardwaveApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalCupertinoApi::class)
@Composable
fun ShardwaveApp(viewModel: MainViewModel) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val catalog by viewModel.catalog.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val swarmStats by viewModel.swarmStats.collectAsState()
    val inferenceOutput by viewModel.inferenceOutput.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()

    CupertinoScaffold(
        topBar = {
            CupertinoTopAppBar(
                title = { CupertinoText("Shardwave P2P", fontFamily = CupertinoFont) },
                isTranslucent = true
            )
        },
        bottomBar = {
            CupertinoNavigationBar(isTranslucent = true) {
                CupertinoNavigationBarItem(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    icon = { CupertinoIcon(imageVector = CupertinoIcons.Outlined.Folder, contentDescription = "Catalog") },
                    label = { CupertinoText("Catalog", fontFamily = CupertinoFont) }
                )
                CupertinoNavigationBarItem(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    icon = { CupertinoIcon(imageVector = CupertinoIcons.Outlined.ArrowDownCircle, contentDescription = "Transfer") },
                    label = { CupertinoText("Transfer", fontFamily = CupertinoFont) }
                )
                CupertinoNavigationBarItem(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    icon = { CupertinoIcon(imageVector = CupertinoIcons.Outlined.Cpu, contentDescription = "Inference") },
                    label = { CupertinoText("Inference", fontFamily = CupertinoFont) }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTabIndex) {
                0 -> CupertinoCatalogScreen(
                    catalog = catalog,
                    selectedModel = selectedModel,
                    onSelectModel = { viewModel.selectModel(it) },
                    onStartDownload = {
                        viewModel.startDownload(it)
                        selectedTabIndex = 1
                    },
                    onAddModel = { name, url ->
                        viewModel.addCustomModel(name, url)
                    }
                )
                1 -> CupertinoTransferScreen(
                    stats = swarmStats
                )
                2 -> CupertinoInferenceScreen(
                    selectedModel = selectedModel,
                    output = inferenceOutput,
                    isGenerating = isGenerating,
                    onRunInference = { viewModel.runInference(it) }
                )
            }
        }
    }
}
