package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.automation.BatteryOptimizationHelper
import com.example.automation.BatteryOptimizationStatus
import com.example.termux.TermuxBridge
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldPrimary

@Composable
fun BatteryOptimizationCard(
    status: BatteryOptimizationStatus,
    onRefresh: () -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasWarning = status.hasRestrictionWarning
    val accentColor = if (hasWarning) AmberWarn else EmeraldPrimary

    ResendCard(
        borderColor = if (hasWarning) AmberWarn.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline,
        modifier = modifier
            .fillMaxWidth()
            .testTag("battery_optimization_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (hasWarning) Icons.Outlined.BatteryAlert else Icons.Outlined.BatteryChargingFull,
                    contentDescription = "Battery optimization status",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (hasWarning) "Battery Optimization Warning" else "Background Power Unrestricted",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("refresh_battery_status_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Re-check battery optimization",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (hasWarning) {
                "Android background power restrictions can delay cron alarms or terminate Termux scripts. Disable battery optimization for reliable execution."
            } else {
                "Both CronDeck and Termux background power policies are unrestricted for reliable scheduled tasks."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MonoBadge(
                text = if (status.isCronDeckRestricted) "CRONDECK: RESTRICTED" else "CRONDECK: UNRESTRICTED",
                leadingDotColor = if (status.isCronDeckRestricted) AmberWarn else EmeraldPrimary,
                textColor = if (status.isCronDeckRestricted) AmberWarn else EmeraldPrimary
            )
            val termuxText = when {
                !status.isTermuxInstalled -> "TERMUX: NOT INSTALLED"
                status.isTermuxRestricted -> "TERMUX: RESTRICTED"
                else -> "TERMUX: UNRESTRICTED"
            }
            val termuxColor = when {
                !status.isTermuxInstalled -> MaterialTheme.colorScheme.onSurfaceVariant
                status.isTermuxRestricted -> AmberWarn
                else -> EmeraldPrimary
            }
            MonoBadge(
                text = termuxText,
                leadingDotColor = termuxColor,
                textColor = termuxColor
            )
        }

        if (hasWarning) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResendPrimaryButton(
                    text = "Battery Settings",
                    icon = Icons.Outlined.Settings,
                    onClick = {
                        if (!BatteryOptimizationHelper.openBatteryOptimizationSettings(context)) {
                            onFeedback("Open Android Settings -> Apps -> Battery -> Unrestricted")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "open_battery_settings_button"
                )
                if (status.isTermuxRestricted) {
                    ResendOutlineButton(
                        text = "Termux Info",
                        icon = Icons.Outlined.OpenInNew,
                        onClick = {
                            if (!BatteryOptimizationHelper.openAppBatteryInfo(context, TermuxBridge.TERMUX_PACKAGE)) {
                                onFeedback("Could not open Termux App Info")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "open_termux_battery_info_button"
                    )
                } else if (status.isCronDeckRestricted) {
                    ResendOutlineButton(
                        text = "App Info",
                        icon = Icons.Outlined.OpenInNew,
                        onClick = {
                            if (!BatteryOptimizationHelper.openAppBatteryInfo(context, context.packageName)) {
                                onFeedback("Could not open CronDeck App Info")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "open_crondeck_battery_info_button"
                    )
                }
            }
        }
    }
}
