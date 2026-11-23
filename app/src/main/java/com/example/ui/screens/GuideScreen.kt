package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.automation.BatteryOptimizationStatus
import com.example.cron.CronExpressionParser
import com.example.termux.TermuxEnvironmentStatus
import com.example.ui.components.BatteryOptimizationCard
import com.example.ui.components.MonoBadge
import com.example.ui.components.ResendCard
import com.example.ui.components.ResendOutlineButton
import com.example.ui.components.ResendPrimaryButton
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CyanInfo
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val playgroundExamples = listOf(
    "*/15 * * * *",
    "0 * * * *",
    "30 2 * * *",
    "0 9 * * 1-5",
    "0 3 * * 0"
)

@Composable
fun GuideScreen(
    termuxStatus: TermuxEnvironmentStatus,
    batteryStatus: BatteryOptimizationStatus,
    onRefreshEnvironment: () -> Unit,
    onOpenTermux: () -> Boolean,
    onCopyFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var playgroundCron by remember { mutableStateOf("*/15 * * * *") }
    val breakdown = remember(playgroundCron) { CronExpressionParser.breakdown(playgroundCron) }
    val nextRuns = remember(playgroundCron) {
        val formatter = SimpleDateFormat("EEE, MMM d · HH:mm", Locale.US)
        CronExpressionParser.nextRunsFormatted(playgroundCron, count = 3).map { formatter.format(Date(it)) }
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
            // Hero Banner
            item(key = "guide_hero") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_cron_banner),
                        contentDescription = "CronDeck Automation Architecture Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x6609090B),
                                        Color(0xE609090B)
                                    )
                                )
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column {
                            MonoBadge(
                                text = "BEGINNER TUTORIAL",
                                leadingDotColor = EmeraldPrimary,
                                textColor = EmeraldPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Termux Automation Quickstart",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color(0xFFFAFAFA)
                            )
                            Text(
                                text = "Connect Termux, configure external app commands, and master cron schedules in 3 minutes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA1A1AA)
                            )
                        }
                    }
                }
            }

            // Live Termux Diagnostics Card
            item(key = "termux_diagnostics") {
                ResendCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Environment Check",
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(
                            onClick = onRefreshEnvironment,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Re-check Termux packages",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    DiagnosticRow(
                        label = "Termux App (com.termux)",
                        ready = termuxStatus.isTermuxInstalled,
                        detail = termuxStatus.termuxVersionName ?: "Install from F-Droid or GitHub Releases"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    DiagnosticRow(
                        label = "Termux:API Add-on (com.termux.api)",
                        ready = termuxStatus.isTermuxApiInstalled,
                        detail = if (termuxStatus.isTermuxApiInstalled) "Installed" else "Required for battery, clipboard & Termux sensors"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    DiagnosticRow(
                        label = "RUN_COMMAND Permission",
                        ready = termuxStatus.hasRunCommandPermission,
                        detail = if (termuxStatus.hasRunCommandPermission) "Granted" else "Grant in Android App Info -> Permissions"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (termuxStatus.isTermuxInstalled) {
                        ResendPrimaryButton(
                            text = "Launch Termux",
                            icon = Icons.Outlined.OpenInNew,
                            onClick = {
                                if (!onOpenTermux()) {
                                    onCopyFeedback("Unable to open Termux directly")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "launch_termux_button"
                        )
                    } else {
                        ResendOutlineButton(
                            text = "Re-scan Installed Packages",
                            icon = Icons.Outlined.Refresh,
                            onClick = onRefreshEnvironment,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Battery Optimization Check
            item(key = "guide_battery_check") {
                BatteryOptimizationCard(
                    status = batteryStatus,
                    onRefresh = onRefreshEnvironment,
                    onFeedback = onCopyFeedback
                )
            }

            // Step 1
            item(key = "step_1") {
                CommandStepCard(
                    stepNumber = "01",
                    title = "Enable External App Execution",
                    explanation = "Termux blocks third-party apps from running scripts by default. Run this one-line setup command inside Termux to allow CronDeck:",
                    command = "mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings",
                    onCopy = { cmd ->
                        clipboard.setText(AnnotatedString(cmd))
                        onCopyFeedback("Copied allow-external-apps command")
                    }
                )
            }

            // Step 2
            item(key = "step_2") {
                CommandStepCard(
                    stepNumber = "02",
                    title = "Install Termux:API & Cron Daemon",
                    explanation = "Install cronie, termux-services, and termux-api so your scripts can query battery, Wi-Fi, notifications, and sync with crond:",
                    command = "pkg update -y && pkg install -y cronie termux-services termux-api && sv-enable crond",
                    onCopy = { cmd ->
                        clipboard.setText(AnnotatedString(cmd))
                        onCopyFeedback("Copied Termux package setup command")
                    }
                )
            }

            // Step 3: Interactive Cron Syntax Playground
            item(key = "cron_playground") {
                ResendCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MonoBadge(text = "03", textColor = EmeraldPrimary)
                        Text(
                            text = "Interactive Cron Playground",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap any schedule preset below to see how the 5 cron fields map to real execution times:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        playgroundExamples.forEach { expr ->
                            val selected = expr == playgroundCron
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.background
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                    .clickable { playgroundCron = expr }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                                    .testTag("playground_expr_${expr.hashCode()}")
                            ) {
                                Text(
                                    text = expr,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = CronExpressionParser.describeHuman(playgroundCron),
                        style = MaterialTheme.typography.titleSmall,
                        color = EmeraldPrimary
                    )

                    if (breakdown != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CronFieldBox("MIN", breakdown.minute, Modifier.weight(1f))
                            CronFieldBox("HOUR", breakdown.hour, Modifier.weight(1f))
                            CronFieldBox("DAY", breakdown.dayOfMonth, Modifier.weight(1f))
                            CronFieldBox("MON", breakdown.month, Modifier.weight(1f))
                            CronFieldBox("DOW", breakdown.dayOfWeek, Modifier.weight(1f))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "NEXT 3 TRIGGER TIMES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    nextRuns.forEachIndexed { idx, formatted ->
                        Text(
                            text = "${idx + 1}. $formatted",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = CyanInfo,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    ready: Boolean,
    detail: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
            contentDescription = if (ready) "Ready" else "Needs attention",
            tint = if (ready) EmeraldPrimary else AmberWarn,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommandStepCard(
    stepNumber: String,
    title: String,
    explanation: String,
    command: String,
    onCopy: (String) -> Unit
) {
    ResendCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MonoBadge(text = stepNumber, textColor = EmeraldPrimary)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = command,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = { onCopy(command) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy command",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CronFieldBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = JetBrainsMonoFontFamily,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
