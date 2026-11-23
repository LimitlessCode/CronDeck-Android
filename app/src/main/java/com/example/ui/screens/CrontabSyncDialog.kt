package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.ui.CrontabDialogState
import com.example.ui.components.ResendCard
import com.example.ui.components.ResendOutlineButton
import com.example.ui.components.ResendPrimaryButton
import com.example.ui.theme.CyanInfo
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun CrontabSyncDialog(
    state: CrontabDialogState,
    onImportTextChange: (String) -> Unit,
    onFetchLiveCrontab: () -> Unit,
    onImportEntries: () -> Unit,
    onPushCrontabToTermux: () -> Unit,
    onCopiedNotice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val clipboard = LocalClipboardManager.current

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close crontab sync")
                        }
                        Text(
                            text = "Termux Crontab Sync",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Import Section
                    ResendCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Import from Termux (crontab -l)", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Pull existing cron entries directly from Termux or paste your crontab lines below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (state.liveSyncNote != null) {
                            Text(
                                text = state.liveSyncNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = CyanInfo,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        ResendOutlineButton(
                            text = "Pull Live from Termux",
                            icon = Icons.Outlined.Sync,
                            onClick = onFetchLiveCrontab,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "pull_live_crontab_button"
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = state.importText,
                            onValueChange = onImportTextChange,
                            label = { Text("Crontab Lines") },
                            placeholder = { Text("*/15 * * * * ~/scripts/sync.sh") },
                            minLines = 4,
                            maxLines = 8,
                            textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            shape = RoundedCornerShape(6.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.background,
                                unfocusedContainerColor = MaterialTheme.colorScheme.background
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("crontab_import_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ResendPrimaryButton(
                            text = "Import Parsed Jobs",
                            icon = Icons.Outlined.Download,
                            onClick = onImportEntries,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "import_crontab_button"
                        )
                    }

                    // Export / Push Section
                    ResendCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Export / Sync to Termux crond", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Push your CronDeck jobs directly into Termux's crontab or copy the generated file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = state.exportedText,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                .padding(10.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ResendOutlineButton(
                                text = "Copy",
                                icon = Icons.Outlined.ContentCopy,
                                onClick = {
                                    clipboard.setText(AnnotatedString(state.exportedText))
                                    onCopiedNotice("Copied crontab to clipboard")
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ResendPrimaryButton(
                                text = "Push to Termux",
                                icon = Icons.Outlined.Upload,
                                onClick = onPushCrontabToTermux,
                                modifier = Modifier.weight(1f),
                                testTag = "push_crontab_button"
                            )
                        }
                    }
                }
            }
        }
    }
}
