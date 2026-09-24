package com.yashvant.shardwave.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yashvant.shardwave.ui.theme.CupertinoFont
import io.github.alexzhirkevich.cupertino.CupertinoButton
import io.github.alexzhirkevich.cupertino.CupertinoButtonDefaults
import io.github.alexzhirkevich.cupertino.CupertinoSurface
import io.github.alexzhirkevich.cupertino.CupertinoText
import io.github.alexzhirkevich.cupertino.CupertinoTextField
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi
import io.github.alexzhirkevich.cupertino.theme.CupertinoColors
import io.github.alexzhirkevich.cupertino.theme.CupertinoTheme
import io.github.alexzhirkevich.cupertino.theme.Gray
import io.github.alexzhirkevich.cupertino.theme.systemGray6

@OptIn(ExperimentalCupertinoApi::class)
@Composable
fun AddModelDialog(
    onDismiss: () -> Unit,
    onAddModel: (name: String, url: String) -> Unit
) {
    var modelName by remember { mutableStateOf("") }
    var downloadUrl by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        CupertinoSurface(
            color = CupertinoColors.systemGray6,
            shape = CupertinoTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                CupertinoText(
                    text = "Add Custom GGUF Model",
                    style = CupertinoTheme.typography.title2,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CupertinoFont
                )
                Spacer(modifier = Modifier.height(4.dp))
                CupertinoText(
                    text = "Enter a name and direct HuggingFace GGUF URL, HTTPS link, or magnet URI.",
                    style = CupertinoTheme.typography.footnote,
                    color = CupertinoColors.Gray,
                    fontFamily = CupertinoFont
                )
                Spacer(modifier = Modifier.height(16.dp))

                CupertinoText(
                    text = "Model Name",
                    style = CupertinoTheme.typography.caption1,
                    fontWeight = FontWeight.SemiBold,
                    color = CupertinoColors.Gray,
                    fontFamily = CupertinoFont
                )
                Spacer(modifier = Modifier.height(4.dp))
                CupertinoTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    placeholder = { CupertinoText("e.g. Qwen2.5 0.5B Instruct", color = CupertinoColors.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                CupertinoText(
                    text = "GGUF Download URL / Magnet",
                    style = CupertinoTheme.typography.caption1,
                    fontWeight = FontWeight.SemiBold,
                    color = CupertinoColors.Gray,
                    fontFamily = CupertinoFont
                )
                Spacer(modifier = Modifier.height(4.dp))
                CupertinoTextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it },
                    placeholder = { CupertinoText("https://huggingface.co/.../model.gguf", color = CupertinoColors.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CupertinoButton(
                        onClick = onDismiss,
                        colors = CupertinoButtonDefaults.plainButtonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        CupertinoText("Cancel", fontFamily = CupertinoFont)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    CupertinoButton(
                        onClick = {
                            if (modelName.isNotBlank() && downloadUrl.isNotBlank()) {
                                onAddModel(modelName.trim(), downloadUrl.trim())
                                onDismiss()
                            }
                        },
                        enabled = modelName.isNotBlank() && downloadUrl.isNotBlank(),
                        colors = CupertinoButtonDefaults.filledButtonColors(),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        CupertinoText("Add & Download", fontFamily = CupertinoFont)
                    }
                }
            }
        }
    }
}
