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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
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
import com.example.data.ChainStep
import com.example.data.StepCondition
import com.example.ui.ChainEditorDraft
import com.example.ui.components.MonoBadge
import com.example.ui.components.ResendCard
import com.example.ui.components.ResendOutlineButton
import com.example.ui.components.ResendPrimaryButton
import com.example.ui.components.ResendSwitch
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanInfo
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.UUID

private val chainQuickSchedules = listOf(
    "15m" to "*/15 * * * *",
    "Hourly" to "0 * * * *",
    "Daily 02:00" to "0 2 * * *",
    "Weekdays 09:00" to "0 9 * * 1-5"
)

private val stepDelays = listOf(0, 1, 2, 5, 10)

@Composable
fun ChainEditorSheet(
    draft: ChainEditorDraft,
    onUpdateDraft: ((ChainEditorDraft) -> ChainEditorDraft) -> Unit,
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
                                .testTag("chain_editor_close_button")
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close chain editor")
                        }
                        Text(
                            text = if (draft.existingId == null) "New Action Chain" else "Customize Action Chain",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    ResendPrimaryButton(
                        text = "Save Chain",
                        icon = Icons.Outlined.Check,
                        onClick = onSave,
                        testTag = "chain_editor_save_button"
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Metadata & Schedule
                    ResendCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Pipeline Identity & Schedule", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = { v -> onUpdateDraft { it.copy(name = v) } },
                            label = { Text("Chain Name") },
                            placeholder = { Text("e.g. Clean -> Build -> Notify") },
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chain_name_input")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draft.description,
                            onValueChange = { v -> onUpdateDraft { it.copy(description = v) } },
                            label = { Text("Description") },
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            chainQuickSchedules.forEach { (label, expr) ->
                                ChainChip(
                                    label = label,
                                    selected = draft.cronExpression.trim() == expr,
                                    onClick = { onUpdateDraft { it.copy(cronExpression = expr) } }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draft.cronExpression,
                            onValueChange = { v -> onUpdateDraft { it.copy(cronExpression = v) } },
                            label = { Text("Cron Expression") },
                            singleLine = true,
                            isError = !cronValid,
                            textStyle = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            shape = RoundedCornerShape(6.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = humanSchedule,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (cronValid) EmeraldPrimary else CrimsonError
                        )
                    }

                    // Ordered Action Steps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Action Steps (${draft.steps.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        ResendOutlineButton(
                            text = "Add Step",
                            icon = Icons.Outlined.Add,
                            onClick = {
                                onUpdateDraft { state ->
                                    val nextNum = state.steps.size + 1
                                    state.copy(
                                        steps = state.steps + ChainStep(
                                            id = UUID.randomUUID().toString(),
                                            title = "Step $nextNum",
                                            command = "echo \"Running step $nextNum\"",
                                            delaySeconds = 0,
                                            condition = StepCondition.ON_SUCCESS
                                        )
                                    )
                                }
                            },
                            testTag = "chain_add_step_button"
                        )
                    }

                    draft.steps.forEachIndexed { index, step ->
                        ChainStepEditorCard(
                            index = index,
                            total = draft.steps.size,
                            step = step,
                            fieldColors = fieldColors,
                            onUpdate = { updated ->
                                onUpdateDraft { state ->
                                    val mutable = state.steps.toMutableList()
                                    mutable[index] = updated
                                    state.copy(steps = mutable)
                                }
                            },
                            onMoveUp = {
                                if (index > 0) {
                                    onUpdateDraft { state ->
                                        val mutable = state.steps.toMutableList()
                                        val item = mutable.removeAt(index)
                                        mutable.add(index - 1, item)
                                        state.copy(steps = mutable)
                                    }
                                }
                            },
                            onMoveDown = {
                                if (index < draft.steps.lastIndex) {
                                    onUpdateDraft { state ->
                                        val mutable = state.steps.toMutableList()
                                        val item = mutable.removeAt(index)
                                        mutable.add(index + 1, item)
                                        state.copy(steps = mutable)
                                    }
                                }
                            },
                            onRemove = {
                                if (draft.steps.size > 1) {
                                    onUpdateDraft { state ->
                                        state.copy(steps = state.steps.filterIndexed { i, _ -> i != index })
                                    }
                                }
                            }
                        )
                    }

                    // Error Handling & Alerts
                    ResendCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Flow Control & Alerts", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Stop Chain on Error", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Halts remaining steps immediately if a command exits non-zero (except 'On Failure' hooks)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ResendSwitch(
                                checked = draft.stopOnError,
                                onCheckedChange = { v -> onUpdateDraft { it.copy(stopOnError = v) } }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AlertMode.entries.forEach { mode ->
                                ChainChip(
                                    label = mode.shortLabel,
                                    selected = draft.alertMode == mode,
                                    onClick = { onUpdateDraft { it.copy(alertMode = mode) } }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Respect Do Not Disturb", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Silences alarms and TTS during System DND or Quiet Hours",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ResendSwitch(
                                checked = draft.respectDnd,
                                onCheckedChange = { v -> onUpdateDraft { it.copy(respectDnd = v) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChainStepEditorCard(
    index: Int,
    total: Int,
    step: ChainStep,
    fieldColors: androidx.compose.material3.TextFieldColors,
    onUpdate: (ChainStep) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    ResendCard(
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chain_step_card_$index")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonoBadge(
                text = "STEP ${index + 1}",
                leadingDotColor = CyanInfo,
                textColor = CyanInfo
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move step up", modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < total - 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move step down", modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onRemove,
                    enabled = total > 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove step", modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = step.title,
            onValueChange = { onUpdate(step.copy(title = it)) },
            label = { Text("Step Title") },
            singleLine = true,
            shape = RoundedCornerShape(6.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = step.command,
            onValueChange = { onUpdate(step.copy(command = it)) },
            label = { Text("Bash / Termux:API Command") },
            minLines = 2,
            maxLines = 5,
            textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            shape = RoundedCornerShape(6.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "RUN CONDITION",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StepCondition.entries.forEach { cond ->
                ChainChip(
                    label = cond.shortLabel,
                    selected = step.condition == cond,
                    onClick = { onUpdate(step.copy(condition = cond)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "PRE-STEP DELAY",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            stepDelays.forEach { sec ->
                ChainChip(
                    label = "${sec}s",
                    selected = step.delaySeconds == sec,
                    onClick = { onUpdate(step.copy(delaySeconds = sec)) }
                )
            }
        }
    }
}

@Composable
private fun ChainChip(
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
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
