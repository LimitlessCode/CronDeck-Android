package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.AlertMode
import com.example.ui.CronDeckUiState
import com.example.ui.components.BatteryOptimizationCard
import com.example.ui.components.MonoBadge
import com.example.ui.components.ResendCard
import com.example.ui.components.ResendOutlineButton
import com.example.ui.components.ResendPrimaryButton
import com.example.ui.components.ResendSwitch
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CyanInfo
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

@Composable
fun SettingsScreen(
    state: CronDeckUiState,
    onSetDarkMode: (Boolean) -> Unit,
    onSetRespectSystemDnd: (Boolean) -> Unit,
    onSetQuietHoursEnabled: (Boolean) -> Unit,
    onSetQuietWindow: (Int, Int, Int, Int) -> Unit,
    onSetCriticalBypassQuiet: (Boolean) -> Unit,
    onSetDefaultTts: (Boolean) -> Unit,
    onSetEncryptByDefault: (Boolean) -> Unit,
    onSetMaskEncrypted: (Boolean) -> Unit,
    onEncryptAllJobs: () -> Unit,
    onTestAlert: (AlertMode, Boolean) -> Unit,
    onRefreshStatus: () -> Unit,
    onShowFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    val settings = state.settings
    val dnd = state.dndStatus

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
            item(key = "settings_header") {
                Column {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Configure DND-aware TTS/alarm alerts, AES-256-GCM encryption defaults, and theme.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Battery Optimization & Background Power
            item(key = "battery_power_section") {
                BatteryOptimizationCard(
                    status = state.batteryStatus,
                    onRefresh = onRefreshStatus,
                    onFeedback = onShowFeedback
                )
            }

            // Notifications, Alarms & TTS
            item(key = "alerts_section") {
                ResendCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RecordVoiceOver,
                            contentDescription = null,
                            tint = CyanInfo,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Completion Alerts & TTS",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                        ResendPrimaryButton(
                            text = "Allow Notification Alerts",
                            icon = Icons.Outlined.NotificationsActive,
                            onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "grant_notifications_button"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    SettingSwitchRow(
                        title = "Use Spoken TTS by Default",
                        subtitle = "New jobs default to Text-to-Speech completion alerts",
                        checked = settings.defaultTtsEnabled,
                        onCheckedChange = onSetDefaultTts,
                        testTag = "setting_default_tts"
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "TEST REAL-TIME ALERTS (RESPECTS DND)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ResendOutlineButton(
                            text = "Notify",
                            onClick = { onTestAlert(AlertMode.NOTIFICATION, false) },
                            modifier = Modifier.weight(1f),
                            testTag = "test_notify_button"
                        )
                        ResendOutlineButton(
                            text = "TTS",
                            onClick = { onTestAlert(AlertMode.TTS, false) },
                            modifier = Modifier.weight(1f),
                            testTag = "test_tts_button"
                        )
                        ResendOutlineButton(
                            text = "Alarm",
                            onClick = { onTestAlert(AlertMode.ALARM, false) },
                            modifier = Modifier.weight(1f),
                            testTag = "test_alarm_button"
                        )
                    }
                }
            }

            // Do Not Disturb & Quiet Hours
            item(key = "dnd_section") {
                ResendCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DoNotDisturbOn,
                                contentDescription = null,
                                tint = AmberWarn,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Do Not Disturb & Quiet Hours",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        MonoBadge(
                            text = if (dnd.systemDndActive) "SYS DND ON" else "SYS DND OFF",
                            leadingDotColor = if (dnd.systemDndActive) AmberWarn else EmeraldPrimary,
                            textColor = if (dnd.systemDndActive) AmberWarn else EmeraldPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    SettingSwitchRow(
                        title = "Respect System Do Not Disturb",
                        subtitle = "Current OS filter: ${dnd.systemFilterLabel}. Silences alarms & TTS when active.",
                        checked = settings.respectSystemDnd,
                        onCheckedChange = {
                            onSetRespectSystemDnd(it)
                            onRefreshStatus()
                        },
                        testTag = "setting_respect_sys_dnd"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingSwitchRow(
                        title = "Custom Quiet Hours",
                        subtitle = "Suppress audio/TTS alerts during your scheduled sleep or focus window",
                        checked = settings.quietHoursEnabled,
                        onCheckedChange = onSetQuietHoursEnabled,
                        testTag = "setting_quiet_hours"
                    )

                    if (settings.quietHoursEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HourStepperBox(
                                label = "QUIET START",
                                hour = settings.quietStartHour,
                                minute = settings.quietStartMinute,
                                onStepHour = { delta ->
                                    val next = (settings.quietStartHour + delta + 24) % 24
                                    onSetQuietWindow(
                                        next,
                                        settings.quietStartMinute,
                                        settings.quietEndHour,
                                        settings.quietEndMinute
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            HourStepperBox(
                                label = "QUIET END",
                                hour = settings.quietEndHour,
                                minute = settings.quietEndMinute,
                                onStepHour = { delta ->
                                    val next = (settings.quietEndHour + delta + 24) % 24
                                    onSetQuietWindow(
                                        settings.quietStartHour,
                                        settings.quietStartMinute,
                                        next,
                                        settings.quietEndMinute
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingSwitchRow(
                            title = "Critical Error Bypass (Quiet Hours)",
                            subtitle = "Allow non-zero exit code alarms during Custom Quiet Hours (still obeys System DND)",
                            checked = settings.allowCriticalBypassQuietHours,
                            onCheckedChange = onSetCriticalBypassQuiet,
                            testTag = "setting_critical_bypass"
                        )
                    }
                }
            }

            // Privacy & Encryption Vault
            item(key = "vault_section") {
                ResendCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Privacy & Encryption Vault",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        MonoBadge(text = "AES-256-GCM", textColor = EmeraldPrimary)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingSwitchRow(
                        title = "Encrypt New Jobs by Default",
                        subtitle = "Encrypts script payloads at rest via Android Keystore",
                        checked = settings.encryptByDefault,
                        onCheckedChange = onSetEncryptByDefault,
                        testTag = "setting_encrypt_default"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingSwitchRow(
                        title = "Mask Encrypted Scripts in List",
                        subtitle = "Hides sensitive tokens or commands until tapped to reveal",
                        checked = settings.maskEncryptedPreviews,
                        onCheckedChange = onSetMaskEncrypted,
                        testTag = "setting_mask_encrypted"
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    ResendOutlineButton(
                        text = "Encrypt All Unencrypted Jobs",
                        icon = Icons.Outlined.Lock,
                        onClick = onEncryptAllJobs,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "encrypt_all_button"
                    )
                }
            }

            // Appearance
            item(key = "appearance_section") {
                ResendCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingSwitchRow(
                        title = "Obsidian Dark Mode",
                        subtitle = "High-contrast Resend dark UI (recommended for terminal workflows)",
                        checked = settings.darkMode,
                        onCheckedChange = onSetDarkMode,
                        testTag = "setting_dark_mode"
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ResendSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun HourStepperBox(
    label: String,
    hour: Int,
    minute: Int,
    onStepHour: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .clickable { onStepHour(-1) },
                contentAlignment = Alignment.Center
            ) {
                Text("-", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = String.format(Locale.US, "%02d:%02d", hour, minute),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .clickable { onStepHour(1) },
                contentAlignment = Alignment.Center
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
