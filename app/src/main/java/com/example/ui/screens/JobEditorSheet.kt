package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.cron.CronExpressionParser
import com.example.data.AlertMode
import com.example.data.JobCategory
import com.example.ui.JobEditorDraft
import com.example.ui.components.MonoBadge
import com.example.ui.components.ResendCard
import com.example.ui.components.ResendPrimaryButton
import com.example.ui.components.ResendSwitch
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily

private val quickSchedules = listOf(
    "15m" to "*/15 * * * *",
    "Hourly" to "0 * * * *",
    "Daily 02:00" to "0 2 * * *",
    "Weekdays 09:00" to "0 9 * * 1-5",
    "Weekly Sun" to "0 3 * * 0"
)

private val apiSnippets = listOf(
    "Battery JSON" to "\ntermux-battery-status",
    "Toast" to "\ntermux-toast \"CronDeck task done\"",
    "Termux Notify" to "\ntermux-notification --title \"CronDeck\" --content \"Task finished\"",
    "TTS Speak" to "\ntermux-tts-speak \"Automation completed\""
)

@Composable
fun JobEditorSheet(
    draft: JobEditorDraft,
    onUpdateDraft: ((JobEditorDraft) -> JobEditorDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val cronValid = remember(draft.cronExpression) {
        CronExpressionParser.isValid(draft.cronExpression)
    }
    val humanSchedule = remember(draft.cronExpression) {
        CronExpressionParser.describeHuman(draft.cronExpression)
    }
    val nextCountdown = remember(draft.cronExpression, cronValid) {
        if (!cronValid) "Invalid"
        else CronExpressionParser.formatRelativeCountdown(
            CronExpressionParser.nextRunMillis(draft.cronExpression)
        )
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.background,
        unfocusedContainerColor = MaterialTheme.colorScheme.background
    )

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
                // Top Bar
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
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("editor_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Close editor"
                            )
                        }
                        Text(
                            text = if (draft.existingId == null) "New Automation" else "Edit Automation",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    ResendPrimaryButton(
                        text = "Save Job",
                        icon = Icons.Outlined.Check,
                        onClick = onSave,
                        testTag = "editor_save_button"
                    )
                }

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Identity
                    ResendCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Task Identity", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = { v -> onUpdateDraft { it.copy(name = v) } },
                            label = { Text("Job Name") },
                            placeholder = { Text("e.g. Nightly Workspace Backup") },
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_name_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = draft.description,
                            onValueChange = { v -> onUpdateDraft { it.copy(description = v) } },
                            label = { Text("Description") },
                            placeholder = { Text("Optional summary of what this script automates") },
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "CATEGORY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            JobCategory.entries.forEach { cat ->
                                SelectableChip(
                                    label = cat.label,
                                    selected = draft.category == cat,
                                    onClick = { onUpdateDraft { it.copy(category = cat) } }
                                )
                            }
                        }
                    }

                    // Schedule Builder
                    ResendCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cron Schedule", style = MaterialTheme.typography.titleMedium)
                            MonoBadge(
                                text = nextCountdown,
                                leadingDotColor = if (cronValid) EmeraldPrimary else CrimsonError,
                                textColor = if (cronValid) EmeraldPrimary else CrimsonError
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickSchedules.forEach { (label, expr) ->
                                SelectableChip(
                                    label = label,
                                    selected = draft.cronExpression.trim() == expr,
                                    onClick = { onUpdateDraft { it.copy(cronExpression = expr) } }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = draft.cronExpression,
                            onValueChange = { v -> onUpdateDraft { it.copy(cronExpression = v) } },
                            label = { Text("5-Field Cron Expression (min hour dom month dow)") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            shape = RoundedCornerShape(6.dp),
                            colors = fieldColors,
                            isError = !cronValid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_cron_input")
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = humanSchedule,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (cronValid) EmeraldPrimary else CrimsonError
                        )
                    }

                    // Script Body
                    ResendCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Bash Script & Termux:API", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            apiSnippets.forEach { (label, snippet) ->
                                SelectableChip(
                                    label = "+ $label",
                                    selected = false,
                                    onClick = {
                                        onUpdateDraft { it.copy(scriptContent = it.scriptContent.trimEnd() + snippet) }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = draft.scriptContent,
                            onValueChange = { v -> onUpdateDraft { it.copy(scriptContent = v) } },
                            label = { Text("Shell Commands") },
                            minLines = 6,
                            maxLines = 12,
                            textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            shape = RoundedCornerShape(6.dp),
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_script_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = draft.workingDirectory,
                            onValueChange = { v -> onUpdateDraft { it.copy(workingDirectory = v) } },
                            label = { Text("Working Directory") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            shape = RoundedCornerShape(6.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Alerts, DND & Encryption
                    ResendCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Alerts, DND & Privacy", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "COMPLETION ALERT MODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AlertMode.entries.forEach { mode ->
                                SelectableChip(
                                    label = mode.shortLabel,
                                    selected = draft.alertMode == mode,
                                    onClick = { onUpdateDraft { it.copy(alertMode = mode) } }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        EditorSwitchRow(
                            title = "Respect Do Not Disturb & Quiet Hours",
                            subtitle = "Automatically mute alarms and TTS while DND is active",
                            checked = draft.respectDnd,
                            onCheckedChange = { v -> onUpdateDraft { it.copy(respectDnd = v) } }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        EditorSwitchRow(
                            title = "Run in Background Session",
                            subtitle = "Executes quietly via Termux RunCommandService without foreground terminal window",
                            checked = draft.runInBackground,
                            onCheckedChange = { v -> onUpdateDraft { it.copy(runInBackground = v) } }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        EditorSwitchRow(
                            title = "Encrypt Script (AES-256-GCM)",
                            subtitle = "Protects API keys, paths, and commands inside Android Keystore vault",
                            checked = draft.isEncrypted,
                            onCheckedChange = { v -> onUpdateDraft { it.copy(isEncrypted = v) } },
                            leadingIcon = Icons.Outlined.Lock
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.background
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EditorSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ResendSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
