package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.CronDeckApp
import com.example.automation.BatteryOptimizationHelper
import com.example.automation.BatteryOptimizationStatus
import com.example.automation.CronScheduler
import com.example.cron.CronExpressionParser
import com.example.data.ActionChain
import com.example.data.AlertMode
import com.example.data.AppSettings
import com.example.data.ChainPreset
import com.example.data.ChainStep
import com.example.data.CronJob
import com.example.data.CronRepository
import com.example.data.ExecutionLogEntity
import com.example.data.JobCategory
import com.example.data.JobRunStatus
import com.example.data.ScriptPreset
import com.example.data.SettingsPreferences
import com.example.data.StepCondition
import com.example.telemetry.ResourceMonitor
import com.example.telemetry.ResourceSample
import com.example.termux.AlertDispatcher
import com.example.termux.DispatchResult
import com.example.termux.DndStatus
import com.example.termux.TermuxBridge
import com.example.termux.TermuxEnvironmentStatus
import com.example.termux.TermuxSyncEvents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NavTab(val label: String) {
    JOBS("Jobs"),
    LOGS("Logs"),
    PRESETS("Presets"),
    GUIDE("Guide"),
    SETTINGS("Settings")
}

enum class JobsSubTab(val label: String) {
    CRON_JOBS("Cron Jobs"),
    ACTIVITY_LOGS("Activity Logs")
}

enum class JobsViewMode(val label: String) {
    CRON_JOBS("Cron Jobs"),
    ACTION_CHAINS("Action Chains")
}

enum class PresetsViewMode(val label: String) {
    SINGLE_SCRIPTS("Script Presets"),
    CHAIN_PRESETS("Action Chains")
}

enum class JobFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    PAUSED("Paused"),
    ENCRYPTED("Encrypted")
}

data class JobEditorDraft(
    val existingId: Long? = null,
    val name: String = "",
    val description: String = "",
    val cronExpression: String = "*/15 * * * *",
    val scriptContent: String = "#!/data/data/com.termux/files/usr/bin/bash\necho \"Hello from CronDeck at $(date)\"",
    val workingDirectory: String = "/data/data/com.termux/files/home",
    val isEnabled: Boolean = true,
    val runInBackground: Boolean = true,
    val isEncrypted: Boolean = false,
    val category: JobCategory = JobCategory.CUSTOM,
    val alertMode: AlertMode = AlertMode.NOTIFICATION,
    val respectDnd: Boolean = true
)

data class CrontabDialogState(
    val importText: String = "",
    val exportedText: String = "",
    val liveSyncNote: String? = null
)

data class ChainEditorDraft(
    val existingId: Long? = null,
    val name: String = "",
    val description: String = "",
    val cronExpression: String = "0 * * * *",
    val isEnabled: Boolean = true,
    val stopOnError: Boolean = true,
    val steps: List<ChainStep> = listOf(
        ChainStep(
            title = "Step 1: Prepare",
            command = "echo \"Starting chain at $(date)\"",
            delaySeconds = 0,
            condition = StepCondition.ALWAYS
        ),
        ChainStep(
            title = "Step 2: Execute",
            command = "echo \"Running action step\"",
            delaySeconds = 1,
            condition = StepCondition.ON_SUCCESS
        )
    ),
    val alertMode: AlertMode = AlertMode.NOTIFICATION,
    val respectDnd: Boolean = true
)

data class CronDeckUiState(
    val jobs: List<CronJob> = emptyList(),
    val chains: List<ActionChain> = emptyList(),
    val logs: List<ExecutionLogEntity> = emptyList(),
    val resourceSamples: List<ResourceSample> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val termuxStatus: TermuxEnvironmentStatus = TermuxEnvironmentStatus(
        isTermuxInstalled = false,
        isTermuxApiInstalled = false,
        hasRunCommandPermission = false,
        termuxVersionName = null
    ),
    val dndStatus: DndStatus = DndStatus(
        systemDndActive = false,
        systemFilterLabel = "Off",
        quietHoursActive = false,
        shouldSuppressAudioAlerts = false,
        reason = "Allowed"
    ),
    val batteryStatus: BatteryOptimizationStatus = BatteryOptimizationStatus(
        isCronDeckIgnoringOptimizations = true,
        isCronDeckBackgroundRestricted = false,
        isTermuxInstalled = false,
        isTermuxIgnoringOptimizations = null
    ),
    val selectedTab: NavTab = NavTab.JOBS,
    val jobsSubTab: JobsSubTab = JobsSubTab.CRON_JOBS,
    val jobsViewMode: JobsViewMode = JobsViewMode.CRON_JOBS,
    val presetsViewMode: PresetsViewMode = PresetsViewMode.SINGLE_SCRIPTS,
    val searchQuery: String = "",
    val activeFilter: JobFilter = JobFilter.ALL,
    val isSelectionMode: Boolean = false,
    val selectedJobIds: Set<Long> = emptySet(),
    val editorDraft: JobEditorDraft? = null,
    val chainEditorDraft: ChainEditorDraft? = null,
    val crontabDialog: CrontabDialogState? = null,
    val revealedJobIds: Set<Long> = emptySet(),
    val snackbarMessage: String? = null
)

private data class LocalNavState(
    val selectedTab: NavTab = NavTab.JOBS,
    val jobsSubTab: JobsSubTab = JobsSubTab.CRON_JOBS,
    val jobsViewMode: JobsViewMode = JobsViewMode.CRON_JOBS,
    val presetsViewMode: PresetsViewMode = PresetsViewMode.SINGLE_SCRIPTS,
    val searchQuery: String = "",
    val activeFilter: JobFilter = JobFilter.ALL,
    val isSelectionMode: Boolean = false,
    val selectedJobIds: Set<Long> = emptySet(),
    val editorDraft: JobEditorDraft? = null,
    val chainEditorDraft: ChainEditorDraft? = null,
    val crontabDialog: CrontabDialogState? = null,
    val revealedJobIds: Set<Long> = emptySet(),
    val snackbarMessage: String? = null
)

class CronDeckViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CronDeckApp
    private val repository: CronRepository = app.repository
    private val settingsPrefs: SettingsPreferences = app.settingsPreferences
    private val resourceMonitor = ResourceMonitor(app)

    private val localState = MutableStateFlow(LocalNavState())
    private val envState = MutableStateFlow(TermuxBridge.detectEnvironment(app))
    private val batteryState = MutableStateFlow(BatteryOptimizationHelper.checkStatus(app))
    private val dndState = MutableStateFlow(
        AlertDispatcher.evaluateDnd(app, AppSettings(), jobRespectsDnd = true, isFailure = false)
    )

    @Volatile
    private var latestActiveTasksCount: Int = 0

    private val resourceSamplesFlow = resourceMonitor.observeSamples(
        intervalMs = 1500L,
        maxHistory = 24,
        activeJobsCountProvider = { latestActiveTasksCount }
    )

    val uiState: StateFlow<CronDeckUiState> = combine(
        repository.jobsFlow,
        repository.chainsFlow,
        repository.logsFlow,
        settingsPrefs.settingsFlow,
        localState,
        envState,
        batteryState,
        resourceSamplesFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val jobs = values[0] as List<CronJob>
        @Suppress("UNCHECKED_CAST")
        val chains = values[1] as List<ActionChain>
        @Suppress("UNCHECKED_CAST")
        val logs = values[2] as List<ExecutionLogEntity>
        val settings = values[3] as AppSettings
        val nav = values[4] as LocalNavState
        val env = values[5] as TermuxEnvironmentStatus
        val battery = values[6] as BatteryOptimizationStatus
        @Suppress("UNCHECKED_CAST")
        val samples = values[7] as List<ResourceSample>

        latestActiveTasksCount = jobs.count { it.isEnabled } + chains.count { it.isEnabled }
        val dnd = AlertDispatcher.evaluateDnd(app, settings, jobRespectsDnd = true, isFailure = false)

        CronDeckUiState(
            jobs = jobs,
            chains = chains,
            logs = logs,
            resourceSamples = samples,
            settings = settings,
            termuxStatus = env,
            dndStatus = dnd,
            batteryStatus = battery,
            selectedTab = nav.selectedTab,
            jobsSubTab = nav.jobsSubTab,
            jobsViewMode = nav.jobsViewMode,
            presetsViewMode = nav.presetsViewMode,
            searchQuery = nav.searchQuery,
            activeFilter = nav.activeFilter,
            isSelectionMode = nav.isSelectionMode,
            selectedJobIds = nav.selectedJobIds,
            editorDraft = nav.editorDraft,
            chainEditorDraft = nav.chainEditorDraft,
            crontabDialog = nav.crontabDialog,
            revealedJobIds = nav.revealedJobIds,
            snackbarMessage = nav.snackbarMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = CronDeckUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureStarterDataSeeded()
            CronScheduler.rescheduleAll(app, repository.getEnabledJobsHydrated())
            CronScheduler.rescheduleAllChains(app, repository.getEnabledChains())
            refreshEnvironmentStatus()
        }
        viewModelScope.launch {
            TermuxSyncEvents.crontabResponses.collect { payload ->
                val current = localState.value.crontabDialog
                val note = if (payload.exitCode == 0) {
                    if (payload.rawCrontab.isBlank()) {
                        "Termux crontab is currently empty. Paste entries below or push CronDeck jobs."
                    } else {
                        "Synced live crontab from Termux (${payload.rawCrontab.lines().size} lines)."
                    }
                } else {
                    "Termux returned exit ${payload.exitCode}: ${payload.stderr}"
                }
                if (current != null) {
                    localState.update {
                        it.copy(
                            crontabDialog = current.copy(
                                importText = payload.rawCrontab.ifBlank { current.importText },
                                liveSyncNote = note
                            )
                        )
                    }
                } else {
                    showSnackbar(note)
                }
            }
        }
    }

    fun refreshEnvironmentStatus() {
        envState.value = TermuxBridge.detectEnvironment(app)
        batteryState.value = BatteryOptimizationHelper.checkStatus(app)
        viewModelScope.launch {
            val settings = settingsPrefs.getSnapshot()
            dndState.value = AlertDispatcher.evaluateDnd(app, settings, jobRespectsDnd = true, isFailure = false)
        }
    }

    fun selectTab(tab: NavTab) {
        localState.update { it.copy(selectedTab = tab) }
        refreshEnvironmentStatus()
    }

    fun selectJobsSubTab(subTab: JobsSubTab) {
        localState.update { it.copy(jobsSubTab = subTab) }
    }

    fun updateSearchQuery(query: String) {
        localState.update { it.copy(searchQuery = query) }
    }

    fun selectFilter(filter: JobFilter) {
        localState.update { it.copy(activeFilter = filter) }
    }

    fun toggleScriptMaskReveal(jobId: Long) {
        localState.update { state ->
            val next = if (jobId in state.revealedJobIds) {
                state.revealedJobIds - jobId
            } else {
                state.revealedJobIds + jobId
            }
            state.copy(revealedJobIds = next)
        }
    }

    fun openNewJobEditor() {
        val defaults = uiState.value.settings
        localState.update {
            it.copy(
                editorDraft = JobEditorDraft(
                    isEncrypted = defaults.encryptByDefault,
                    alertMode = if (defaults.defaultTtsEnabled) AlertMode.TTS else AlertMode.NOTIFICATION,
                    respectDnd = defaults.respectSystemDnd
                )
            )
        }
    }

    fun openEditJobEditor(job: CronJob) {
        localState.update {
            it.copy(
                editorDraft = JobEditorDraft(
                    existingId = job.id,
                    name = job.name,
                    description = job.description,
                    cronExpression = job.cronExpression,
                    scriptContent = job.decryptedScript,
                    workingDirectory = job.workingDirectory,
                    isEnabled = job.isEnabled,
                    runInBackground = job.runInBackground,
                    isEncrypted = job.isEncrypted,
                    category = job.category,
                    alertMode = job.alertMode,
                    respectDnd = job.respectDnd
                )
            )
        }
    }

    fun openPresetInEditor(preset: ScriptPreset) {
        val defaults = uiState.value.settings
        localState.update {
            it.copy(
                editorDraft = JobEditorDraft(
                    name = preset.title,
                    description = preset.summary,
                    cronExpression = preset.defaultCron,
                    scriptContent = preset.scriptBody,
                    workingDirectory = preset.defaultWorkDir,
                    isEnabled = true,
                    runInBackground = true,
                    isEncrypted = defaults.encryptByDefault,
                    category = preset.category,
                    alertMode = preset.recommendedAlert,
                    respectDnd = defaults.respectSystemDnd
                )
            )
        }
    }

    fun quickAddPreset(preset: ScriptPreset) {
        viewModelScope.launch {
            val defaults = settingsPrefs.getSnapshot()
            val newId = repository.saveJob(
                existingId = null,
                name = preset.title,
                description = preset.summary,
                cronExpression = preset.defaultCron,
                plainScript = preset.scriptBody,
                workingDirectory = preset.defaultWorkDir,
                isEnabled = true,
                runInBackground = true,
                isEncrypted = defaults.encryptByDefault,
                category = preset.category,
                alertMode = preset.recommendedAlert,
                respectDnd = defaults.respectSystemDnd
            )
            repository.getJobById(newId)?.let { CronScheduler.scheduleJob(app, it) }
            showSnackbar("Added '${preset.title}' to active jobs")
        }
    }

    fun updateEditorDraft(transform: (JobEditorDraft) -> JobEditorDraft) {
        localState.update { state ->
            val draft = state.editorDraft ?: return@update state
            state.copy(editorDraft = transform(draft))
        }
    }

    fun closeEditor() {
        localState.update { it.copy(editorDraft = null) }
    }

    fun saveEditorDraft() {
        val draft = localState.value.editorDraft ?: return
        if (draft.name.isBlank()) {
            showSnackbar("Please enter a task name")
            return
        }
        if (!CronExpressionParser.isValid(draft.cronExpression)) {
            showSnackbar("Invalid cron expression — use 5 fields (e.g. */15 * * * *)")
            return
        }
        if (draft.scriptContent.isBlank()) {
            showSnackbar("Script content cannot be empty")
            return
        }
        viewModelScope.launch {
            val id = repository.saveJob(
                existingId = draft.existingId,
                name = draft.name,
                description = draft.description,
                cronExpression = draft.cronExpression,
                plainScript = draft.scriptContent,
                workingDirectory = draft.workingDirectory,
                isEnabled = draft.isEnabled,
                runInBackground = draft.runInBackground,
                isEncrypted = draft.isEncrypted,
                category = draft.category,
                alertMode = draft.alertMode,
                respectDnd = draft.respectDnd
            )
            val saved = repository.getJobById(id)
            if (saved != null) {
                CronScheduler.scheduleJob(app, saved)
            }
            localState.update { it.copy(editorDraft = null) }
            showSnackbar(if (draft.existingId == null) "Created '${draft.name}'" else "Saved '${draft.name}'")
        }
    }

    fun toggleJob(job: CronJob, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleJobEnabled(job.id, enabled)
            val updated = repository.getJobById(job.id)
            if (updated != null) {
                CronScheduler.scheduleJob(app, updated)
            }
            showSnackbar(if (enabled) "Enabled '${job.name}'" else "Paused '${job.name}'")
        }
    }

    fun toggleJobEncryption(job: CronJob) {
        viewModelScope.launch {
            val next = !job.isEncrypted
            repository.toggleJobEncryption(job.id, next)
            showSnackbar(
                if (next) "Encrypted '${job.name}' with AES-256-GCM"
                else "Decrypted '${job.name}' to standard storage"
            )
        }
    }

    fun deleteJob(job: CronJob) {
        viewModelScope.launch {
            CronScheduler.cancelJob(app, job.id)
            repository.deleteJob(job.id)
            showSnackbar("Deleted '${job.name}'")
        }
    }

    fun runJobNow(job: CronJob) {
        viewModelScope.launch {
            val settings = settingsPrefs.getSnapshot()
            when (val result = TermuxBridge.dispatchJob(app, job)) {
                is DispatchResult.Dispatched -> {
                    val alertBehavior = AlertDispatcher.dispatchAlert(
                        context = app,
                        jobName = job.name,
                        alertMode = job.alertMode,
                        settings = settings,
                        jobRespectsDnd = job.respectDnd,
                        isFailure = false,
                        detailMessage = "Dispatched to Termux RunCommandService"
                    )
                    repository.recordExecution(
                        jobId = job.id,
                        jobName = job.name,
                        exitCode = 0,
                        durationMs = 1L,
                        status = JobRunStatus.DISPATCHED,
                        outputSnippet = "Command dispatched to Termux (${job.workingDirectory})",
                        alertBehavior = alertBehavior,
                        isEncrypted = job.isEncrypted
                    )
                    showSnackbar("Dispatched '${job.name}' to Termux")
                }
                DispatchResult.TermuxNotInstalled -> {
                    val alertBehavior = AlertDispatcher.dispatchAlert(
                        context = app,
                        jobName = job.name,
                        alertMode = job.alertMode,
                        settings = settings,
                        jobRespectsDnd = job.respectDnd,
                        isFailure = true,
                        detailMessage = "Termux package not installed on device"
                    )
                    repository.recordExecution(
                        jobId = job.id,
                        jobName = job.name,
                        exitCode = 127,
                        durationMs = 1L,
                        status = JobRunStatus.FAILED,
                        outputSnippet = "com.termux is not installed. See Guide tab to install Termux & Termux:API.",
                        alertBehavior = alertBehavior,
                        isEncrypted = job.isEncrypted
                    )
                    showSnackbar("Termux not installed — open Guide tab for setup")
                }
                DispatchResult.PermissionDenied -> {
                    showSnackbar("Grant Termux RUN_COMMAND permission in Android Settings")
                }
                is DispatchResult.BlockedOrFailed -> {
                    repository.recordExecution(
                        jobId = job.id,
                        jobName = job.name,
                        exitCode = 1,
                        durationMs = 1L,
                        status = JobRunStatus.FAILED,
                        outputSnippet = result.reason,
                        alertBehavior = "Execution blocked",
                        isEncrypted = job.isEncrypted
                    )
                    showSnackbar(result.reason)
                }
            }
        }
    }

    fun openCrontabSyncDialog() {
        viewModelScope.launch {
            val jobs = repository.getAllJobsHydrated()
            val exported = TermuxBridge.buildCrontabDocument(jobs)
            localState.update {
                it.copy(
                    crontabDialog = CrontabDialogState(
                        importText = "",
                        exportedText = exported,
                        liveSyncNote = null
                    )
                )
            }
        }
    }

    fun updateCrontabImportText(text: String) {
        localState.update { state ->
            val dlg = state.crontabDialog ?: return@update state
            state.copy(crontabDialog = dlg.copy(importText = text))
        }
    }

    fun closeCrontabDialog() {
        localState.update { it.copy(crontabDialog = null) }
    }

    fun fetchCrontabFromTermux() {
        when (val res = TermuxBridge.requestCrontabList(app)) {
            is DispatchResult.Dispatched -> {
                localState.update { state ->
                    val dlg = state.crontabDialog ?: return@update state
                    state.copy(crontabDialog = dlg.copy(liveSyncNote = "Querying Termux crontab -l..."))
                }
            }
            DispatchResult.TermuxNotInstalled -> showSnackbar("Termux not installed — paste crontab output manually below")
            DispatchResult.PermissionDenied -> showSnackbar("Termux RUN_COMMAND permission required")
            is DispatchResult.BlockedOrFailed -> showSnackbar(res.reason)
        }
    }

    fun pushCrontabToTermux() {
        viewModelScope.launch {
            val jobs = repository.getAllJobsHydrated()
            when (val res = TermuxBridge.pushCrontab(app, jobs)) {
                is DispatchResult.Dispatched -> showSnackbar("Synced ${jobs.size} jobs to Termux crontab")
                DispatchResult.TermuxNotInstalled -> showSnackbar("Termux not installed — copy the crontab text to import later")
                DispatchResult.PermissionDenied -> showSnackbar("Termux RUN_COMMAND permission required")
                is DispatchResult.BlockedOrFailed -> showSnackbar(res.reason)
            }
        }
    }

    fun importParsedCrontabEntries() {
        val raw = localState.value.crontabDialog?.importText.orEmpty()
        val parsed = CronExpressionParser.parseCrontabText(raw)
        if (parsed.isEmpty()) {
            showSnackbar("No valid 5-field cron entries detected")
            return
        }
        viewModelScope.launch {
            val defaults = settingsPrefs.getSnapshot()
            parsed.forEach { entry ->
                val id = repository.saveJob(
                    existingId = null,
                    name = entry.name,
                    description = "Imported from Termux crontab",
                    cronExpression = entry.cronExpression,
                    plainScript = "#!/data/data/com.termux/files/usr/bin/bash\n${entry.command}",
                    workingDirectory = "/data/data/com.termux/files/home",
                    isEnabled = entry.isEnabled,
                    runInBackground = true,
                    isEncrypted = defaults.encryptByDefault,
                    category = JobCategory.CUSTOM,
                    alertMode = if (defaults.defaultTtsEnabled) AlertMode.TTS else AlertMode.NOTIFICATION,
                    respectDnd = defaults.respectSystemDnd
                )
                repository.getJobById(id)?.let { CronScheduler.scheduleJob(app, it) }
            }
            localState.update { it.copy(crontabDialog = null) }
            showSnackbar("Imported ${parsed.size} cron job(s)")
        }
    }

    fun testAlert(mode: AlertMode, simulateFailure: Boolean = false) {
        viewModelScope.launch {
            val settings = settingsPrefs.getSnapshot()
            val result = AlertDispatcher.dispatchAlert(
                context = app,
                jobName = "CronDeck Diagnostic",
                alertMode = mode,
                settings = settings,
                jobRespectsDnd = true,
                isFailure = simulateFailure,
                detailMessage = if (simulateFailure) "Simulated critical failure alert" else "Alert test executed successfully"
            )
            showSnackbar("Alert test: $result")
        }
    }

    fun openTermux(): Boolean = TermuxBridge.openTermuxApp(app)

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsPrefs.setDarkMode(enabled) }
    }

    fun setRespectSystemDnd(enabled: Boolean) {
        viewModelScope.launch {
            settingsPrefs.setRespectSystemDnd(enabled)
            refreshEnvironmentStatus()
        }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsPrefs.setQuietHoursEnabled(enabled)
            refreshEnvironmentStatus()
        }
    }

    fun setQuietWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        viewModelScope.launch {
            settingsPrefs.setQuietWindow(startHour, startMinute, endHour, endMinute)
            refreshEnvironmentStatus()
        }
    }

    fun setCriticalBypassQuiet(enabled: Boolean) {
        viewModelScope.launch { settingsPrefs.setCriticalBypassQuiet(enabled) }
    }

    fun setEncryptByDefault(enabled: Boolean) {
        viewModelScope.launch { settingsPrefs.setEncryptByDefault(enabled) }
    }

    fun setMaskEncryptedPreviews(enabled: Boolean) {
        viewModelScope.launch { settingsPrefs.setMaskEncryptedPreviews(enabled) }
    }

    fun setDefaultTtsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsPrefs.setDefaultTtsEnabled(enabled) }
    }

    fun encryptAllExistingJobs() {
        viewModelScope.launch {
            val count = repository.encryptAllUnencrypted()
            showSnackbar(
                if (count > 0) "Encrypted $count job(s) with AES-256-GCM"
                else "All jobs are already encrypted"
            )
        }
    }

    fun dismissBeginnerBanner() {
        viewModelScope.launch { settingsPrefs.dismissBeginnerBanner() }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            showSnackbar("Cleared execution history")
        }
    }

    // View Modes & Bulk Selection
    fun selectJobsViewMode(mode: JobsViewMode) {
        localState.update { it.copy(jobsViewMode = mode, isSelectionMode = false, selectedJobIds = emptySet()) }
    }

    fun selectPresetsViewMode(mode: PresetsViewMode) {
        localState.update { it.copy(presetsViewMode = mode) }
    }

    fun toggleSelectionMode() {
        localState.update { state ->
            val next = !state.isSelectionMode
            state.copy(
                isSelectionMode = next,
                selectedJobIds = if (next) state.selectedJobIds else emptySet()
            )
        }
    }

    fun toggleJobSelected(jobId: Long) {
        localState.update { state ->
            val next = if (jobId in state.selectedJobIds) {
                state.selectedJobIds - jobId
            } else {
                state.selectedJobIds + jobId
            }
            state.copy(isSelectionMode = true, selectedJobIds = next)
        }
    }

    fun selectAllJobs(visibleIds: List<Long>) {
        localState.update { state ->
            state.copy(isSelectionMode = true, selectedJobIds = visibleIds.toSet())
        }
    }

    fun clearJobSelection() {
        localState.update { it.copy(isSelectionMode = false, selectedJobIds = emptySet()) }
    }

    fun bulkEnableSelected() {
        val ids = localState.value.selectedJobIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.setJobsEnabledBulk(ids, true)
            ids.forEach { id ->
                repository.getJobById(id)?.let { CronScheduler.scheduleJob(app, it) }
            }
            localState.update { it.copy(isSelectionMode = false, selectedJobIds = emptySet()) }
            showSnackbar("Enabled ${ids.size} job(s)")
        }
    }

    fun bulkDisableSelected() {
        val ids = localState.value.selectedJobIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.setJobsEnabledBulk(ids, false)
            ids.forEach { id -> CronScheduler.cancelJob(app, id) }
            localState.update { it.copy(isSelectionMode = false, selectedJobIds = emptySet()) }
            showSnackbar("Disabled ${ids.size} job(s)")
        }
    }

    fun bulkDeleteSelected() {
        val ids = localState.value.selectedJobIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id -> CronScheduler.cancelJob(app, id) }
            repository.deleteJobsBulk(ids)
            localState.update { it.copy(isSelectionMode = false, selectedJobIds = emptySet()) }
            showSnackbar("Deleted ${ids.size} job(s)")
        }
    }

    // Action Chains
    fun openNewChainEditor() {
        val defaults = uiState.value.settings
        localState.update {
            it.copy(
                chainEditorDraft = ChainEditorDraft(
                    alertMode = if (defaults.defaultTtsEnabled) AlertMode.TTS else AlertMode.NOTIFICATION,
                    respectDnd = defaults.respectSystemDnd
                )
            )
        }
    }

    fun openEditChainEditor(chain: ActionChain) {
        localState.update {
            it.copy(
                chainEditorDraft = ChainEditorDraft(
                    existingId = chain.id,
                    name = chain.name,
                    description = chain.description,
                    cronExpression = chain.cronExpression,
                    isEnabled = chain.isEnabled,
                    stopOnError = chain.stopOnError,
                    steps = chain.steps,
                    alertMode = chain.alertMode,
                    respectDnd = chain.respectDnd
                )
            )
        }
    }

    fun openChainPresetInEditor(preset: ChainPreset) {
        val defaults = uiState.value.settings
        localState.update {
            it.copy(
                chainEditorDraft = ChainEditorDraft(
                    name = preset.title,
                    description = preset.summary,
                    cronExpression = preset.defaultCron,
                    isEnabled = true,
                    stopOnError = preset.stopOnError,
                    steps = preset.steps,
                    alertMode = preset.recommendedAlert,
                    respectDnd = defaults.respectSystemDnd
                )
            )
        }
    }

    fun quickAddChainPreset(preset: ChainPreset) {
        viewModelScope.launch {
            val defaults = settingsPrefs.getSnapshot()
            val id = repository.saveChain(
                existingId = null,
                name = preset.title,
                description = preset.summary,
                cronExpression = preset.defaultCron,
                isEnabled = true,
                stopOnError = preset.stopOnError,
                steps = preset.steps,
                alertMode = preset.recommendedAlert,
                respectDnd = defaults.respectSystemDnd
            )
            repository.getChainById(id)?.let { CronScheduler.scheduleChain(app, it) }
            showSnackbar("Added chain '${preset.title}'")
        }
    }

    fun updateChainEditorDraft(transform: (ChainEditorDraft) -> ChainEditorDraft) {
        localState.update { state ->
            val draft = state.chainEditorDraft ?: return@update state
            state.copy(chainEditorDraft = transform(draft))
        }
    }

    fun closeChainEditor() {
        localState.update { it.copy(chainEditorDraft = null) }
    }

    fun saveChainEditorDraft() {
        val draft = localState.value.chainEditorDraft ?: return
        if (draft.name.isBlank()) {
            showSnackbar("Please enter a chain name")
            return
        }
        if (!CronExpressionParser.isValid(draft.cronExpression)) {
            showSnackbar("Invalid cron expression")
            return
        }
        if (draft.steps.isEmpty() || draft.steps.any { it.command.isBlank() }) {
            showSnackbar("Each step needs a command")
            return
        }
        viewModelScope.launch {
            val id = repository.saveChain(
                existingId = draft.existingId,
                name = draft.name,
                description = draft.description,
                cronExpression = draft.cronExpression,
                isEnabled = draft.isEnabled,
                stopOnError = draft.stopOnError,
                steps = draft.steps,
                alertMode = draft.alertMode,
                respectDnd = draft.respectDnd
            )
            repository.getChainById(id)?.let { CronScheduler.scheduleChain(app, it) }
            localState.update { it.copy(chainEditorDraft = null) }
            showSnackbar("Saved chain '${draft.name}'")
        }
    }

    fun toggleChain(chain: ActionChain, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleChainEnabled(chain.id, enabled)
            repository.getChainById(chain.id)?.let { CronScheduler.scheduleChain(app, it) }
            showSnackbar(if (enabled) "Enabled chain '${chain.name}'" else "Paused chain '${chain.name}'")
        }
    }

    fun deleteChain(chain: ActionChain) {
        viewModelScope.launch {
            CronScheduler.cancelChain(app, chain.id)
            repository.deleteChain(chain.id)
            showSnackbar("Deleted chain '${chain.name}'")
        }
    }

    fun runChainNow(chain: ActionChain) {
        viewModelScope.launch {
            val settings = settingsPrefs.getSnapshot()
            when (val res = TermuxBridge.dispatchChain(app, chain)) {
                is DispatchResult.Dispatched -> {
                    val alertBehavior = AlertDispatcher.dispatchAlert(
                        context = app,
                        jobName = "Chain: ${chain.name}",
                        alertMode = chain.alertMode,
                        settings = settings,
                        jobRespectsDnd = chain.respectDnd,
                        isFailure = false,
                        detailMessage = "Dispatched ${chain.steps.size}-step action chain"
                    )
                    repository.recordChainExecution(
                        chainId = chain.id,
                        chainName = chain.name,
                        exitCode = 0,
                        durationMs = 1L,
                        status = JobRunStatus.DISPATCHED,
                        outputSnippet = "Dispatched ${chain.steps.size}-step pipeline to Termux",
                        alertBehavior = alertBehavior
                    )
                    showSnackbar("Dispatched chain '${chain.name}'")
                }
                DispatchResult.TermuxNotInstalled -> {
                    val alertBehavior = AlertDispatcher.dispatchAlert(
                        context = app,
                        jobName = "Chain: ${chain.name}",
                        alertMode = chain.alertMode,
                        settings = settings,
                        jobRespectsDnd = chain.respectDnd,
                        isFailure = true,
                        detailMessage = "Termux package not installed"
                    )
                    repository.recordChainExecution(
                        chainId = chain.id,
                        chainName = chain.name,
                        exitCode = 127,
                        durationMs = 1L,
                        status = JobRunStatus.FAILED,
                        outputSnippet = "com.termux is not installed. See Guide tab to install Termux & Termux:API.",
                        alertBehavior = alertBehavior
                    )
                    showSnackbar("Termux not installed — open Guide tab for setup")
                }
                DispatchResult.PermissionDenied -> showSnackbar("Grant Termux RUN_COMMAND permission in Android Settings")
                is DispatchResult.BlockedOrFailed -> {
                    repository.recordChainExecution(
                        chainId = chain.id,
                        chainName = chain.name,
                        exitCode = 1,
                        durationMs = 1L,
                        status = JobRunStatus.FAILED,
                        outputSnippet = res.reason,
                        alertBehavior = "Execution blocked"
                    )
                    showSnackbar(res.reason)
                }
            }
        }
    }

    fun showSnackbar(message: String) {
        localState.update { it.copy(snackbarMessage = message) }
    }

    fun consumeSnackbar() {
        localState.update { it.copy(snackbarMessage = null) }
    }
}
