package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cron.CronExpressionParser
import com.example.data.ActionChain
import com.example.data.CronJob
import com.example.data.JobRunStatus
import com.example.ui.CronDeckUiState
import com.example.ui.JobFilter
import com.example.ui.JobsViewMode
import com.example.ui.components.BatteryOptimizationCard
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.MonoBadge
import com.example.ui.components.ResendCard
import com.example.ui.components.ResendOutlineButton
import com.example.ui.components.ResendPrimaryButton
import com.example.ui.components.ResendSwitch
import com.example.ui.components.ResourceUsageCard
import com.example.ui.components.alertModeIcon
import com.example.ui.components.statusColor
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanInfo
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

@Composable
fun JobsDashboardScreen(
    state: CronDeckUiState,
    onSelectViewMode: (JobsViewMode) -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectFilter: (JobFilter) -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleJobSelected: (Long) -> Unit,
    onSelectAllJobs: (List<Long>) -> Unit,
    onClearJobSelection: () -> Unit,
    onBulkEnable: () -> Unit,
    onBulkDisable: () -> Unit,
    onBulkDelete: () -> Unit,
    onToggleJob: (CronJob, Boolean) -> Unit,
    onRunJobNow: (CronJob) -> Unit,
    onEditJob: (CronJob) -> Unit,
    onDeleteJob: (CronJob) -> Unit,
    onToggleMaskReveal: (Long) -> Unit,
    onCreateNewJob: () -> Unit,
    onCreateNewChain: () -> Unit,
    onToggleChain: (ActionChain, Boolean) -> Unit,
    onRunChainNow: (ActionChain) -> Unit,
    onEditChain: (ActionChain) -> Unit,
    onDeleteChain: (ActionChain) -> Unit,
    onOpenCrontabSync: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenLogs: () -> Unit,
    onDismissBanner: () -> Unit,
    onRefreshEnvironment: () -> Unit,
    onShowFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    val activeJobsCount by remember(state.jobs) {
        derivedStateOf { state.jobs.count { it.isEnabled } }
    }
    val activeChainsCount by remember(state.chains) {
        derivedStateOf { state.chains.count { it.isEnabled } }
    }
    val encryptedCount by remember(state.jobs) {
        derivedStateOf { state.jobs.count { it.isEncrypted } }
    }
    val nextRunCountdown by remember(state.jobs, state.chains) {
        derivedStateOf {
            val now = System.currentTimeMillis()
            val earliestJob = state.jobs
                .filter { it.isEnabled }
                .mapNotNull { CronExpressionParser.nextRunMillis(it.cronExpression, now) }
            val earliestChain = state.chains
                .filter { it.isEnabled }
                .mapNotNull { CronExpressionParser.nextRunMillis(it.cronExpression, now) }
            val earliest = (earliestJob + earliestChain).minOrNull()
            CronExpressionParser.formatRelativeCountdown(earliest, now)
        }
    }
    val filteredJobs by remember(state.jobs, state.searchQuery, state.activeFilter) {
        derivedStateOf {
            val q = state.searchQuery.trim().lowercase(Locale.US)
            state.jobs.filter { job ->
                val matchesFilter = when (state.activeFilter) {
                    JobFilter.ALL -> true
                    JobFilter.ACTIVE -> job.isEnabled
                    JobFilter.PAUSED -> !job.isEnabled
                    JobFilter.ENCRYPTED -> job.isEncrypted
                }
                val matchesQuery = q.isEmpty() ||
                    job.name.lowercase(Locale.US).contains(q) ||
                    job.cronExpression.contains(q) ||
                    job.description.lowercase(Locale.US).contains(q)
                matchesFilter && matchesQuery
            }
        }
    }

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete Selected Jobs?") },
            text = {
                Text(
                    "Permanently delete ${state.selectedJobIds.size} selected cron job(s) and cancel their schedules?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkDeleteConfirm = false
                        onBulkDelete()
                    }
                ) {
                    Text("Delete", color = CrimsonError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Resend-style Header
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CronDeck",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            MonoBadge(
                                text = if (state.termuxStatus.isReady) "TERMUX READY" else "TERMUX SETUP",
                                leadingDotColor = if (state.termuxStatus.isReady) EmeraldPrimary else AmberWarn,
                                textColor = if (state.termuxStatus.isReady) EmeraldPrimary else AmberWarn
                            )
                            if (state.dndStatus.shouldSuppressAudioAlerts) {
                                MonoBadge(
                                    text = "DND QUIET",
                                    leadingDotColor = AmberWarn,
                                    textColor = AmberWarn
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onOpenCrontabSync,
                            modifier = Modifier
                                .size(48.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                .testTag("crontab_sync_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = "Sync Termux crontab",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (state.jobsViewMode == JobsViewMode.CRON_JOBS) {
                            ResendPrimaryButton(
                                text = "New Job",
                                icon = Icons.Outlined.Add,
                                onClick = onCreateNewJob,
                                testTag = "new_job_button"
                            )
                        } else {
                            ResendPrimaryButton(
                                text = "New Chain",
                                icon = Icons.Outlined.Add,
                                onClick = onCreateNewChain,
                                testTag = "new_chain_button"
                            )
                        }
                    }
                }
            }

            // Telemetry Metric Strip
            item(key = "telemetry") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        label = "ACTIVE",
                        value = "${activeJobsCount + activeChainsCount}/${state.jobs.size + state.chains.size}",
                        accentColor = EmeraldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "NEXT RUN",
                        value = nextRunCountdown,
                        accentColor = CyanInfo,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "VAULT",
                        value = "$encryptedCount ENC",
                        accentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Real-Time Resource Usage Line Chart Component
            item(key = "resource_usage") {
                ResourceUsageCard(
                    samples = state.resourceSamples,
                    activeJobsCount = activeJobsCount + activeChainsCount
                )
            }

            // Beginner Onboarding Banner (dismissible)
            item(key = "beginner_banner") {
                AnimatedVisibility(visible = !state.settings.beginnerBannerDismissed) {
                    ResendCard(
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "New to Termux Cron?",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Toggle starter tasks below, build Action Chains, or follow the 3-step Termux setup guide.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = onDismissBanner,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Dismiss guide banner",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        ResendOutlineButton(
                            text = "Open Beginner Guide",
                            icon = Icons.Outlined.MenuBook,
                            onClick = onOpenGuide,
                            testTag = "open_guide_banner_button"
                        )
                    }
                }
            }

            // Battery Optimization Check Warning
            if (state.batteryStatus.hasRestrictionWarning) {
                item(key = "battery_warning") {
                    BatteryOptimizationCard(
                        status = state.batteryStatus,
                        onRefresh = onRefreshEnvironment,
                        onFeedback = onShowFeedback
                    )
                }
            }

            // Quick Log Summary Shortcut Row
            if (state.logs.isNotEmpty()) {
                item(key = "logs_shortcut") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenLogs)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("dashboard_open_logs_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val failedCount = state.logs.count {
                            it.status == JobRunStatus.FAILED || it.exitCode != 0
                        }
                        Text(
                            text = if (failedCount > 0) {
                                "${state.logs.size} execution logs · $failedCount failed"
                            } else {
                                "${state.logs.size} execution logs · View run history"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (failedCount > 0) AmberWarn else MaterialTheme.colorScheme.onSurface
                        )
                        MonoBadge(
                            text = "LOGS ->",
                            textColor = CyanInfo
                        )
                    }
                }
            }

            // Segmented Switcher: Cron Jobs | Action Chains
            item(key = "view_mode_switcher") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    JobsViewMode.entries.forEach { mode ->
                        val selected = state.jobsViewMode == mode
                        val count = if (mode == JobsViewMode.CRON_JOBS) state.jobs.size else state.chains.size
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable { onSelectViewMode(mode) }
                                .testTag("jobs_mode_${mode.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${mode.label} ($count)",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (state.jobsViewMode == JobsViewMode.CRON_JOBS) {
                // Search, Filter & Multi-Select Toggle
                item(key = "search_filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search jobs",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (state.searchQuery.isEmpty()) {
                                        Text(
                                            text = "Filter tasks...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    BasicTextField(
                                        value = state.searchQuery,
                                        onValueChange = onSearchChange,
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("jobs_search_input")
                                    )
                                }
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onSearchChange("") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = onToggleSelectionMode,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (state.isSelectionMode) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                    .testTag("bulk_select_mode_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Checklist,
                                    contentDescription = "Bulk select jobs",
                                    tint = if (state.isSelectionMode) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            JobFilter.entries.forEach { filter ->
                                val selected = state.activeFilter == filter
                                val shape = RoundedCornerShape(4.dp)
                                Box(
                                    modifier = Modifier
                                        .clip(shape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.surfaceVariant
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) MaterialTheme.colorScheme.outlineVariant
                                            else MaterialTheme.colorScheme.outline,
                                            shape
                                        )
                                        .clickable { onSelectFilter(filter) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                        .testTag("filter_${filter.name.lowercase()}")
                                ) {
                                    Text(
                                        text = filter.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Bulk Action Toolbar
                if (state.isSelectionMode || state.selectedJobIds.isNotEmpty()) {
                    item(key = "bulk_action_toolbar") {
                        BulkActionToolbar(
                            selectedCount = state.selectedJobIds.size,
                            totalVisibleCount = filteredJobs.size,
                            onSelectAll = { onSelectAllJobs(filteredJobs.map { it.id }) },
                            onClearSelection = onClearJobSelection,
                            onEnableAll = onBulkEnable,
                            onDisableAll = onBulkDisable,
                            onDeleteSelected = { showBulkDeleteConfirm = true }
                        )
                    }
                }

                if (filteredJobs.isEmpty()) {
                    item(key = "empty_jobs") {
                        ResendCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "No matching automation tasks",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Create a custom cron job or pick a customizable starter from Presets.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(items = filteredJobs, key = { it.id }) { job ->
                        val isSelected = job.id in state.selectedJobIds
                        CronJobCard(
                            job = job,
                            maskEncrypted = state.settings.maskEncryptedPreviews && job.id !in state.revealedJobIds,
                            isSelectionMode = state.isSelectionMode,
                            isSelected = isSelected,
                            onSelectToggle = { onToggleJobSelected(job.id) },
                            onLongPress = { onToggleJobSelected(job.id) },
                            onToggle = { enabled -> onToggleJob(job, enabled) },
                            onRunNow = { onRunJobNow(job) },
                            onEdit = { onEditJob(job) },
                            onDelete = { onDeleteJob(job) },
                            onToggleMaskReveal = { onToggleMaskReveal(job.id) }
                        )
                    }
                }
            } else {
                // Action Chains List
                if (state.chains.isEmpty()) {
                    item(key = "empty_chains") {
                        ResendCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountTree,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text("No Action Chains yet", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Link sequential commands with conditional triggers or add one from Chain Presets.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ResendPrimaryButton(
                                    text = "Create Action Chain",
                                    icon = Icons.Outlined.Add,
                                    onClick = onCreateNewChain
                                )
                            }
                        }
                    }
                } else {
                    items(items = state.chains, key = { "chain_${it.id}" }) { chain ->
                        ActionChainCard(
                            chain = chain,
                            onToggle = { enabled -> onToggleChain(chain, enabled) },
                            onRunNow = { onRunChainNow(chain) },
                            onEdit = { onEditChain(chain) },
                            onDelete = { onDeleteChain(chain) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkActionToolbar(
    selectedCount: Int,
    totalVisibleCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    ResendCard(
        borderColor = CyanInfo.copy(alpha = 0.6f),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bulk_action_toolbar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MonoBadge(
                    text = "$selectedCount SELECTED",
                    leadingDotColor = CyanInfo,
                    textColor = CyanInfo
                )
                if (selectedCount < totalVisibleCount) {
                    Text(
                        text = "Select All",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onSelectAll)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("bulk_select_all_button")
                    )
                }
            }
            IconButton(
                onClick = onClearSelection,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("bulk_clear_selection_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Exit bulk selection",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ResendOutlineButton(
                text = "Enable All",
                icon = Icons.Outlined.CheckCircleOutline,
                onClick = onEnableAll,
                modifier = Modifier.weight(1f),
                testTag = "bulk_enable_all_button"
            )
            ResendOutlineButton(
                text = "Disable All",
                icon = Icons.Outlined.HighlightOff,
                onClick = onDisableAll,
                modifier = Modifier.weight(1f),
                testTag = "bulk_disable_all_button"
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = CrimsonError.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = selectedCount > 0, onClick = onDeleteSelected)
                .testTag("bulk_delete_selected_button")
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = CrimsonError,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Delete Selected ($selectedCount)",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrimsonError
                )
            }
        }
    }
}

@Composable
private fun MetricBox(
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
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CronJobCard(
    job: CronJob,
    maskEncrypted: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onLongPress: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleMaskReveal: () -> Unit
) {
    val humanSchedule = remember(job.cronExpression) {
        CronExpressionParser.describeHuman(job.cronExpression)
    }
    val countdown = remember(job.cronExpression, job.isEnabled) {
        if (!job.isEnabled) "Paused"
        else CronExpressionParser.formatRelativeCountdown(
            CronExpressionParser.nextRunMillis(job.cronExpression)
        )
    }
    val scriptPreview = remember(job.decryptedScript, job.isEncrypted, maskEncrypted) {
        if (job.isEncrypted && maskEncrypted) {
            "••••••••  AES-256-GCM encrypted script"
        } else {
            job.decryptedScript
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("#!") }
                ?: job.decryptedScript.take(80)
        }
    }

    val shape = RoundedCornerShape(6.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = { if (isSelectionMode) onSelectToggle() else onEdit() },
                onLongClick = onLongPress
            )
            .testTag("job_card_${job.id}"),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isSelected -> CyanInfo
                job.isEnabled -> MaterialTheme.colorScheme.outlineVariant
                else -> MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = CyanInfo,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("job_checkbox_${job.id}")
                    )
                } else {
                    CategoryIconBadge(category = job.category)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = job.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (job.isEncrypted) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "Encrypted script",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = "$humanSchedule · $countdown",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (job.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                ResendSwitch(
                    checked = job.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("job_toggle_${job.id}")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonoBadge(
                    text = job.cronExpression,
                    leadingDotColor = if (job.isEnabled) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                MonoBadge(
                    text = job.alertMode.shortLabel,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (job.respectDnd) {
                    MonoBadge(
                        text = "DND",
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (job.lastStatus != JobRunStatus.IDLE) {
                    MonoBadge(
                        text = job.lastStatus.label.uppercase(Locale.US),
                        leadingDotColor = statusColor(job.lastStatus),
                        textColor = statusColor(job.lastStatus)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$ $scriptPreview",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (job.isEncrypted) {
                    IconButton(
                        onClick = onToggleMaskReveal,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (maskEncrypted) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (maskEncrypted) "Reveal encrypted command" else "Hide encrypted command",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = alertModeIcon(job.alertMode),
                        contentDescription = job.alertMode.label,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    if (job.respectDnd) {
                        Icon(
                            imageVector = Icons.Outlined.DoNotDisturbOn,
                            contentDescription = "Respects Do Not Disturb",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            .testTag("delete_job_${job.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete job",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            .testTag("edit_job_${job.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit job",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    ResendOutlineButton(
                        text = "Run",
                        icon = Icons.Outlined.PlayArrow,
                        onClick = onRunNow,
                        testTag = "run_job_${job.id}"
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionChainCard(
    chain: ActionChain,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val humanSchedule = remember(chain.cronExpression) {
        CronExpressionParser.describeHuman(chain.cronExpression)
    }
    ResendCard(
        borderColor = if (chain.isEnabled) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outline,
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chain_card_${chain.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountTree,
                    contentDescription = "Action Chain",
                    tint = CyanInfo,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chain.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$humanSchedule · ${chain.steps.size} steps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ResendSwitch(
                checked = chain.isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("chain_toggle_${chain.id}")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MonoBadge(
                text = chain.cronExpression,
                leadingDotColor = if (chain.isEnabled) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            MonoBadge(text = if (chain.stopOnError) "STOP ON ERR" else "CONTINUE")
            MonoBadge(text = chain.alertMode.shortLabel)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Sequential Step Flow Summary
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            chain.steps.forEachIndexed { idx, step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%02d", idx + 1),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = CyanInfo
                    )
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = step.condition.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(40.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete chain",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(40.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Customize chain",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            ResendOutlineButton(
                text = "Run Chain",
                icon = Icons.Outlined.PlayArrow,
                onClick = onRunNow,
                testTag = "run_chain_${chain.id}"
            )
        }
    }
}
