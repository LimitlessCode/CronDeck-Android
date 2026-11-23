package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CronJob
import com.example.data.ExecutionLogEntity
import com.example.data.JobRunStatus
import com.example.ui.components.MonoBadge
import com.example.ui.components.ResendCard
import com.example.ui.components.ResendOutlineButton
import com.example.ui.components.statusColor
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogFilter(val label: String) {
    ALL("All"),
    FAILED("Failed"),
    COMPLETED("Success"),
    DND_MUTED("DND Muted")
}

@Composable
fun JobExecutionLogsScreen(
    logs: List<ExecutionLogEntity>,
    jobs: List<CronJob>,
    onRetryJob: (CronJob) -> Unit,
    onClearLogs: () -> Unit,
    onCopiedNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var selectedFilter by remember { mutableStateOf(LogFilter.ALL) }

    val jobsById = remember(jobs) { jobs.associateBy { it.id } }

    val failedCount by remember(logs) {
        derivedStateOf { logs.count { it.status == JobRunStatus.FAILED || it.exitCode != 0 } }
    }
    val successCount by remember(logs) {
        derivedStateOf {
            logs.count {
                (it.status == JobRunStatus.SUCCESS || it.status == JobRunStatus.DISPATCHED) && it.exitCode == 0
            }
        }
    }

    val filteredLogs by remember(logs, selectedFilter) {
        derivedStateOf {
            when (selectedFilter) {
                LogFilter.ALL -> logs
                LogFilter.FAILED -> logs.filter { it.status == JobRunStatus.FAILED || it.exitCode != 0 }
                LogFilter.COMPLETED -> logs.filter {
                    (it.status == JobRunStatus.SUCCESS || it.status == JobRunStatus.DISPATCHED) && it.exitCode == 0
                }
                LogFilter.DND_MUTED -> logs.filter { it.status == JobRunStatus.DND_MUTED }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .testTag("execution_logs_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "logs_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Job Execution Logs",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Inspect run timestamps, exit codes, and output to troubleshoot automations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (logs.isNotEmpty()) {
                        ResendOutlineButton(
                            text = "Clear",
                            icon = Icons.Outlined.DeleteSweep,
                            onClick = onClearLogs,
                            testTag = "clear_execution_logs_button"
                        )
                    }
                }
            }

            item(key = "logs_summary") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LogStatTile(
                        label = "TOTAL RUNS",
                        value = logs.size.toString(),
                        accentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    LogStatTile(
                        label = "SUCCEEDED",
                        value = successCount.toString(),
                        accentColor = EmeraldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    LogStatTile(
                        label = "FAILED",
                        value = failedCount.toString(),
                        accentColor = if (failedCount > 0) CrimsonError else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "logs_filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LogFilter.entries.forEach { filter ->
                        val selected = selectedFilter == filter
                        val shape = RoundedCornerShape(4.dp)
                        Box(
                            modifier = Modifier
                                .clip(shape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                                .clickable { selectedFilter = filter }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                .testTag("log_filter_${filter.name.lowercase()}")
                        ) {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (filteredLogs.isEmpty()) {
                item(key = "logs_empty") {
                    ResendCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = if (logs.isEmpty()) "No job runs recorded yet" else "No logs match this filter",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Run any job from the Jobs tab or wait for a scheduled trigger to view timestamps and exit codes here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(items = filteredLogs, key = { it.id }) { log ->
                    ExecutionHistoryItem(
                        log = log,
                        linkedJob = jobsById[log.jobId],
                        onRetryJob = onRetryJob,
                        onCopyLog = { formatted ->
                            clipboard.setText(AnnotatedString(formatted))
                            onCopiedNotice("Copied log entry to clipboard")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LogStatTile(
    label: String,
    value: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = JetBrainsMonoFontFamily,
                color = accentColor
            )
        }
    }
}

@Composable
private fun ExecutionHistoryItem(
    log: ExecutionLogEntity,
    linkedJob: CronJob?,
    onRetryJob: (CronJob) -> Unit,
    onCopyLog: (String) -> Unit
) {
    val formattedTimestamp = remember(log.executedAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(log.executedAt))
    }
    val isFailure = log.status == JobRunStatus.FAILED || log.exitCode != 0
    val exitColor = if (log.exitCode == 0) EmeraldPrimary else CrimsonError

    ResendCard(
        borderColor = if (isFailure) CrimsonError.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_item_${log.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = log.jobName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MonoBadge(
                    text = "EXIT ${log.exitCode}",
                    leadingDotColor = exitColor,
                    textColor = exitColor
                )
                MonoBadge(
                    text = log.status.label.uppercase(Locale.US),
                    textColor = statusColor(log.status)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$formattedTimestamp · ${log.durationMs}ms",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = log.alertBehavior,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = log.outputSnippet,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = JetBrainsMonoFontFamily,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(10.dp)
        )

        if (isFailure) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AmberWarn.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                    .border(1.dp, AmberWarn.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Troubleshooting hint",
                    tint = AmberWarn,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = troubleshootHintForExitCode(log.exitCode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val export = "[${formattedTimestamp}] ${log.jobName} | EXIT ${log.exitCode} (${log.status.label})\n${log.outputSnippet}"
                    onCopyLog(export)
                },
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .testTag("copy_log_${log.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy execution log",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (linkedJob != null && isFailure) {
                Spacer(modifier = Modifier.size(8.dp))
                ResendOutlineButton(
                    text = "Retry",
                    icon = Icons.Outlined.PlayArrow,
                    onClick = { onRetryJob(linkedJob) },
                    testTag = "retry_log_job_${log.id}"
                )
            }
        }
    }
}

private fun troubleshootHintForExitCode(exitCode: Int): String {
    return when (exitCode) {
        127 -> "Exit 127: Command or Termux binary not found. Verify Termux is installed and packages are in \$PATH."
        126 -> "Exit 126: Permission denied executing script. Check file permissions or allow-external-apps=true."
        137 -> "Exit 137: Process killed (SIGKILL). Disable Android Battery Optimization for Termux and CronDeck."
        2 -> "Exit 2: Shell syntax error or failing health check threshold inside script."
        else -> "Exit $exitCode: Non-zero termination. Review stderr output above and verify working directory."
    }
}
