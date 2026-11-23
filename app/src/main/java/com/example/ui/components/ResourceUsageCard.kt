package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Memory
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.telemetry.ResourceSample
import com.example.ui.theme.CyanInfo
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Recharts-inspired dual-series real-time CPU & RAM line chart rendered
 * natively on Jetpack Compose Canvas for zero-overhead performance.
 */
@Composable
fun ResourceUsageCard(
    samples: List<ResourceSample>,
    activeJobsCount: Int,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }
    var inspectedIndex by remember { mutableStateOf<Int?>(null) }

    val latest = samples.lastOrNull()
    val inspected = inspectedIndex?.let { idx -> samples.getOrNull(idx) } ?: latest

    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceBg = MaterialTheme.colorScheme.background

    ResendCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("resource_usage_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = "Resource Usage",
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Resource Usage",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MonoBadge(
                    text = if (activeJobsCount > 0) "$activeJobsCount ACTIVE" else "IDLE",
                    leadingDotColor = if (activeJobsCount > 0) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textColor = if (activeJobsCount > 0) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Collapse resource chart" else "Expand resource chart",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Live Legend & Readout Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendReadout(
                color = EmeraldPrimary,
                label = "CPU",
                value = inspected?.let { String.format(Locale.US, "%.1f%%", it.cpuPercent) } ?: "--",
                modifier = Modifier.weight(1f)
            )
            LegendReadout(
                color = CyanInfo,
                label = "RAM",
                value = inspected?.let {
                    String.format(
                        Locale.US,
                        "%.0f%% (%dMB)",
                        it.ramPercent,
                        it.usedRamMb
                    )
                } ?: "--",
                modifier = Modifier.weight(1.4f)
            )
            if (inspected != null) {
                val timeStr = remember(inspected.timestampMillis) {
                    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(inspected.timestampMillis))
                }
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .background(surfaceBg, RoundedCornerShape(4.dp))
                        .border(1.dp, outlineColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    if (samples.size < 2) {
                        Box(
                            modifier = Modifier.matchParentSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sampling live CPU & RAM telemetry...",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val gridColor = MaterialTheme.colorScheme.outline
                        val crosshairColor = MaterialTheme.colorScheme.outlineVariant

                        Canvas(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(samples.size) {
                                    detectTapGestures { offset ->
                                        val stepX = size.width.toFloat() / (samples.size - 1).coerceAtLeast(1)
                                        val idx = (offset.x / stepX).roundToInt().coerceIn(0, samples.lastIndex)
                                        inspectedIndex = if (inspectedIndex == idx) null else idx
                                    }
                                }
                        ) {
                            val w = size.width
                            val h = size.height
                            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                            // Horizontal CartesianGrid lines (0%, 25%, 50%, 75%, 100%)
                            for (step in 0..4) {
                                val y = h * (step / 4f)
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = if (step in 1..3) dash else null
                                )
                            }

                            val count = samples.size
                            val dx = w / (count - 1).coerceAtLeast(1)

                            fun yForPct(pct: Float): Float {
                                val clamped = pct.coerceIn(0f, 100f)
                                return h - (clamped / 100f) * h
                            }

                            // Build RAM path & fill
                            val ramLine = Path()
                            val ramFill = Path()
                            samples.forEachIndexed { idx, s ->
                                val x = idx * dx
                                val y = yForPct(s.ramPercent)
                                if (idx == 0) {
                                    ramLine.moveTo(x, y)
                                    ramFill.moveTo(x, h)
                                    ramFill.lineTo(x, y)
                                } else {
                                    ramLine.lineTo(x, y)
                                    ramFill.lineTo(x, y)
                                }
                                if (idx == samples.lastIndex) {
                                    ramFill.lineTo(x, h)
                                    ramFill.close()
                                }
                            }
                            drawPath(
                                path = ramFill,
                                brush = Brush.verticalGradient(
                                    colors = listOf(CyanInfo.copy(alpha = 0.22f), Color.Transparent)
                                )
                            )
                            drawPath(
                                path = ramLine,
                                color = CyanInfo,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            // Build CPU path & fill
                            val cpuLine = Path()
                            val cpuFill = Path()
                            samples.forEachIndexed { idx, s ->
                                val x = idx * dx
                                val y = yForPct(s.cpuPercent)
                                if (idx == 0) {
                                    cpuLine.moveTo(x, y)
                                    cpuFill.moveTo(x, h)
                                    cpuFill.lineTo(x, y)
                                } else {
                                    cpuLine.lineTo(x, y)
                                    cpuFill.lineTo(x, y)
                                }
                                if (idx == samples.lastIndex) {
                                    cpuFill.lineTo(x, h)
                                    cpuFill.close()
                                }
                            }
                            drawPath(
                                path = cpuFill,
                                brush = Brush.verticalGradient(
                                    colors = listOf(EmeraldPrimary.copy(alpha = 0.26f), Color.Transparent)
                                )
                            )
                            drawPath(
                                path = cpuLine,
                                color = EmeraldPrimary,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            // Active / Inspected crosshair + dots
                            val focusIdx = (inspectedIndex ?: samples.lastIndex).coerceIn(0, samples.lastIndex)
                            val focusSample = samples[focusIdx]
                            val focusX = focusIdx * dx
                            drawLine(
                                color = crosshairColor,
                                start = Offset(focusX, 0f),
                                end = Offset(focusX, h),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawCircle(
                                color = CyanInfo,
                                radius = 4.dp.toPx(),
                                center = Offset(focusX, yForPct(focusSample.ramPercent))
                            )
                            drawCircle(
                                color = EmeraldPrimary,
                                radius = 4.dp.toPx(),
                                center = Offset(focusX, yForPct(focusSample.cpuPercent))
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tap chart to inspect sample · 0–100% scale",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (latest != null) {
                        Text(
                            text = String.format(Locale.US, "%.1f GB RAM", latest.totalRamMb / 1024f),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendReadout(
    color: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = JetBrainsMonoFontFamily,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
