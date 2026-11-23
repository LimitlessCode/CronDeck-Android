package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.CronDeckViewModel
import com.example.ui.NavTab
import com.example.ui.screens.ChainEditorSheet
import com.example.ui.screens.CrontabSyncDialog
import com.example.ui.screens.GuideScreen
import com.example.ui.screens.JobEditorSheet
import com.example.ui.screens.JobExecutionLogsScreen
import com.example.ui.screens.JobsDashboardScreen
import com.example.ui.screens.PresetsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CronDeckTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CronDeckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(state.snackbarMessage) {
                val msg = state.snackbarMessage
                if (msg != null) {
                    snackbarHostState.showSnackbar(msg)
                    viewModel.consumeSnackbar()
                }
            }

            CronDeckTheme(darkTheme = state.settings.darkMode) {
                if (state.selectedTab != NavTab.JOBS &&
                    state.editorDraft == null &&
                    state.chainEditorDraft == null &&
                    state.crontabDialog == null
                ) {
                    BackHandler { viewModel.selectTab(NavTab.JOBS) }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        snackbarHost = {
                            SnackbarHost(hostState = snackbarHostState) { data ->
                                Snackbar(
                                    snackbarData = data,
                                    shape = RoundedCornerShape(6.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        bottomBar = {
                            CronDeckBottomBar(
                                selectedTab = state.selectedTab,
                                onSelectTab = viewModel::selectTab
                            )
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (state.selectedTab) {
                                NavTab.JOBS -> JobsDashboardScreen(
                                    state = state,
                                    onSelectViewMode = viewModel::selectJobsViewMode,
                                    onSearchChange = viewModel::updateSearchQuery,
                                    onSelectFilter = viewModel::selectFilter,
                                    onToggleSelectionMode = viewModel::toggleSelectionMode,
                                    onToggleJobSelected = viewModel::toggleJobSelected,
                                    onSelectAllJobs = viewModel::selectAllJobs,
                                    onClearJobSelection = viewModel::clearJobSelection,
                                    onBulkEnable = viewModel::bulkEnableSelected,
                                    onBulkDisable = viewModel::bulkDisableSelected,
                                    onBulkDelete = viewModel::bulkDeleteSelected,
                                    onToggleJob = viewModel::toggleJob,
                                    onRunJobNow = viewModel::runJobNow,
                                    onEditJob = viewModel::openEditJobEditor,
                                    onDeleteJob = viewModel::deleteJob,
                                    onToggleMaskReveal = viewModel::toggleScriptMaskReveal,
                                    onCreateNewJob = viewModel::openNewJobEditor,
                                    onCreateNewChain = viewModel::openNewChainEditor,
                                    onToggleChain = viewModel::toggleChain,
                                    onRunChainNow = viewModel::runChainNow,
                                    onEditChain = viewModel::openEditChainEditor,
                                    onDeleteChain = viewModel::deleteChain,
                                    onOpenCrontabSync = viewModel::openCrontabSyncDialog,
                                    onOpenGuide = { viewModel.selectTab(NavTab.GUIDE) },
                                    onOpenLogs = { viewModel.selectTab(NavTab.LOGS) },
                                    onDismissBanner = viewModel::dismissBeginnerBanner,
                                    onRefreshEnvironment = viewModel::refreshEnvironmentStatus,
                                    onShowFeedback = viewModel::showSnackbar
                                )
                                NavTab.LOGS -> JobExecutionLogsScreen(
                                    logs = state.logs,
                                    jobs = state.jobs,
                                    onRetryJob = viewModel::runJobNow,
                                    onClearLogs = viewModel::clearLogs,
                                    onCopiedNotice = viewModel::showSnackbar
                                )
                                NavTab.PRESETS -> PresetsScreen(
                                    viewMode = state.presetsViewMode,
                                    onSelectViewMode = viewModel::selectPresetsViewMode,
                                    onCustomizePreset = viewModel::openPresetInEditor,
                                    onQuickAddPreset = viewModel::quickAddPreset,
                                    onCustomizeChainPreset = viewModel::openChainPresetInEditor,
                                    onQuickAddChainPreset = viewModel::quickAddChainPreset
                                )
                                NavTab.GUIDE -> GuideScreen(
                                    termuxStatus = state.termuxStatus,
                                    batteryStatus = state.batteryStatus,
                                    onRefreshEnvironment = viewModel::refreshEnvironmentStatus,
                                    onOpenTermux = viewModel::openTermux,
                                    onCopyFeedback = viewModel::showSnackbar
                                )
                                NavTab.SETTINGS -> SettingsScreen(
                                    state = state,
                                    onSetDarkMode = viewModel::setDarkMode,
                                    onSetRespectSystemDnd = viewModel::setRespectSystemDnd,
                                    onSetQuietHoursEnabled = viewModel::setQuietHoursEnabled,
                                    onSetQuietWindow = viewModel::setQuietWindow,
                                    onSetCriticalBypassQuiet = viewModel::setCriticalBypassQuiet,
                                    onSetDefaultTts = viewModel::setDefaultTtsEnabled,
                                    onSetEncryptByDefault = viewModel::setEncryptByDefault,
                                    onSetMaskEncrypted = viewModel::setMaskEncryptedPreviews,
                                    onEncryptAllJobs = viewModel::encryptAllExistingJobs,
                                    onTestAlert = viewModel::testAlert,
                                    onRefreshStatus = viewModel::refreshEnvironmentStatus,
                                    onShowFeedback = viewModel::showSnackbar
                                )
                            }
                        }
                    }

                    val draft = state.editorDraft
                    if (draft != null) {
                        JobEditorSheet(
                            draft = draft,
                            onUpdateDraft = viewModel::updateEditorDraft,
                            onSave = viewModel::saveEditorDraft,
                            onDismiss = viewModel::closeEditor
                        )
                    }

                    val chainDraft = state.chainEditorDraft
                    if (chainDraft != null) {
                        ChainEditorSheet(
                            draft = chainDraft,
                            onUpdateDraft = viewModel::updateChainEditorDraft,
                            onSave = viewModel::saveChainEditorDraft,
                            onDismiss = viewModel::closeChainEditor
                        )
                    }

                    val crontabDlg = state.crontabDialog
                    if (crontabDlg != null) {
                        CrontabSyncDialog(
                            state = crontabDlg,
                            onImportTextChange = viewModel::updateCrontabImportText,
                            onFetchLiveCrontab = viewModel::fetchCrontabFromTermux,
                            onImportEntries = viewModel::importParsedCrontabEntries,
                            onPushCrontabToTermux = viewModel::pushCrontabToTermux,
                            onCopiedNotice = viewModel::showSnackbar,
                            onDismiss = viewModel::closeCrontabDialog
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshEnvironmentStatus()
    }
}

@Composable
private fun CronDeckBottomBar(
    selectedTab: NavTab,
    onSelectTab: (NavTab) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars,
            modifier = Modifier.height(80.dp)
        ) {
            NavTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val icon = when (tab) {
                    NavTab.JOBS -> if (selected) Icons.Filled.Terminal else Icons.Outlined.Terminal
                    NavTab.LOGS -> if (selected) Icons.Filled.ReceiptLong else Icons.Outlined.ReceiptLong
                    NavTab.PRESETS -> if (selected) Icons.Filled.AutoAwesomeMosaic else Icons.Outlined.AutoAwesomeMosaic
                    NavTab.GUIDE -> if (selected) Icons.Filled.MenuBook else Icons.Outlined.MenuBook
                    NavTab.SETTINGS -> if (selected) Icons.Filled.Tune else Icons.Outlined.Tune
                }
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.label
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                )
            }
        }
    }
}
