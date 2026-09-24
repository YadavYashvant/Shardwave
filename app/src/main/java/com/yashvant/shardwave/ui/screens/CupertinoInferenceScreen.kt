package com.yashvant.shardwave.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.yashvant.shardwave.ui.main.ChatMessage
import com.yashvant.shardwave.ui.main.MessageSender
import com.yashvant.shardwave.ui.main.ModelItem
import com.yashvant.shardwave.ui.theme.CupertinoFont
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import io.github.alexzhirkevich.cupertino.CupertinoButton
import io.github.alexzhirkevich.cupertino.CupertinoButtonDefaults
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.CupertinoSurface
import io.github.alexzhirkevich.cupertino.CupertinoText
import io.github.alexzhirkevich.cupertino.CupertinoTextField
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.Paperplane
import io.github.alexzhirkevich.cupertino.icons.outlined.Trash
import io.github.alexzhirkevich.cupertino.theme.*

@OptIn(ExperimentalCupertinoApi::class, ExperimentalLayoutApi::class)
@Composable
fun CupertinoInferenceScreen(
    selectedModel: ModelItem?,
    chatMessages: List<ChatMessage>,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    var promptInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun triggerSend() {
        if (promptInput.isNotBlank() && !isGenerating && selectedModel != null) {
            val textToSend = promptInput.trim()
            promptInput = ""
            onSendMessage(textToSend)
        }
    }

    // Auto scroll to latest token
    LaunchedEffect(chatMessages.size, chatMessages.lastOrNull()?.text) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .imePadding()
    ) {
        // Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CupertinoText(
                    text = "On-Device LLM Chat",
                    style = CupertinoTheme.typography.largeTitle,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CupertinoFont
                )
                CupertinoText(
                    text = "Active: ${selectedModel?.name ?: "No model selected"}",
                    style = CupertinoTheme.typography.subhead,
                    color = CupertinoColors.Gray,
                    fontFamily = CupertinoFont
                )
            }

            if (chatMessages.isNotEmpty()) {
                CupertinoButton(
                    onClick = onClearChat,
                    colors = CupertinoButtonDefaults.plainButtonColors()
                ) {
                    CupertinoIcon(imageVector = CupertinoIcons.Outlined.Trash, contentDescription = "Clear Chat")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Chat Message List
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (chatMessages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CupertinoText(
                        text = "No Messages Yet",
                        style = CupertinoTheme.typography.title3,
                        fontWeight = FontWeight.SemiBold,
                        color = CupertinoColors.Gray,
                        fontFamily = CupertinoFont
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    CupertinoText(
                        text = "Ask anything to run on-device GGUF inference via llama.cpp",
                        style = CupertinoTheme.typography.footnote,
                        color = CupertinoColors.Gray,
                        fontFamily = CupertinoFont
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Quick suggestion chips
                    FlowRow(
                        horizontalArrangement = Arrangement.Center,
                        maxItemsInEachRow = 2
                    ) {
                        SuggestionChip(text = "Explain FastCDC chunking") {
                            promptInput = it
                            triggerSend()
                        }
                        SuggestionChip(text = "Who is Donald Trump?") {
                            promptInput = it
                            triggerSend()
                        }
                        SuggestionChip(text = "Tell me about Android devices") {
                            promptInput = it
                            triggerSend()
                        }
                        SuggestionChip(text = "Write a Kotlin flow example") {
                            promptInput = it
                            triggerSend()
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(chatMessages, key = { it.id }) { msg ->
                        ChatBubble(message = msg)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom Prompt Input Bar with Always-Visible Send Button
        CupertinoSurface(
            color = CupertinoColors.systemGray6,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CupertinoTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = { CupertinoText("Ask anything...", color = CupertinoColors.Gray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { triggerSend() }),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                val canSend = promptInput.isNotBlank() && !isGenerating && selectedModel != null

                CupertinoSurface(
                    onClick = { triggerSend() },
                    color = if (canSend) CupertinoColors.systemBlue else CupertinoColors.systemGray4,
                    shape = CircleShape,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isGenerating) {
                            CupertinoActivityIndicator()
                        } else {
                            CupertinoIcon(
                                imageVector = CupertinoIcons.Outlined.Paperplane,
                                contentDescription = "Send",
                                tint = if (canSend) Color.White else CupertinoColors.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    onClick: (String) -> Unit
) {
    CupertinoSurface(
        onClick = { onClick(text) },
        color = CupertinoColors.systemGray5,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(4.dp)
    ) {
        CupertinoText(
            text = text,
            style = CupertinoTheme.typography.caption1,
            fontFamily = CupertinoFont,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (isUser) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp))
                    .background(CupertinoColors.systemBlue)
                    .padding(12.dp)
            ) {
                CupertinoText(
                    text = message.text,
                    style = CupertinoTheme.typography.body,
                    color = Color.White,
                    fontFamily = CupertinoFont
                )
            }
        } else {
            CupertinoSurface(
                color = CupertinoColors.systemGray6,
                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (message.text.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CupertinoActivityIndicator()
                            Spacer(modifier = Modifier.width(8.dp))
                            CupertinoText(
                                text = "Thinking...",
                                style = CupertinoTheme.typography.body,
                                color = CupertinoColors.Gray,
                                fontFamily = CupertinoFont
                            )
                        }
                    } else {
                        CupertinoText(
                            text = message.text,
                            style = CupertinoTheme.typography.body,
                            fontFamily = CupertinoFont
                        )
                    }
                }
            }
        }
    }
}
