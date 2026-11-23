package com.example.ui.screens

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
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cron.CronExpressionParser
import com.example.data.ChainPreset
import com.example.data.ChainPresets
import com.example.data.JobCategory
import com.example.data.ScriptPreset
import com.example.data.ScriptPresets
import com.example.ui.PresetsViewMode
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.MonoBadge
import com.example.ui.components.ResendCard
import com.example.ui.components.ResendOutlineButton
import com.example.ui.components.ResendPrimaryButton
import com.example.ui.theme.CyanInfo
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

@Composable
fun PresetsScreen(
    viewMode: PresetsViewMode,
    onSelectViewMode: (PresetsViewMode) -> Unit,
    onCustomizePreset: (ScriptPreset) -> Unit,
    onQuickAddPreset: (ScriptPreset) -> Unit,
    onCustomizeChainPreset: (ChainPreset) -> Unit,
    onQuickAddChainPreset: (ChainPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf<JobCategory?>(null) }
    val presets = remember(selectedCategory) {
        if (selectedCategory == null) ScriptPresets.all
        else ScriptPresets.all.filter { it.category == selectedCategory }
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
            item(key = "presets_header") {
                Column {
                    Text(
                        text = "Automation Presets",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Beginner-ready scripts and multi-step Action Chains. Customize schedules, step order, alerts, and encryption.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item(key = "presets_mode_switcher") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PresetsViewMode.entries.forEach { mode ->
                        val selected = viewMode == mode
                        val count = if (mode == PresetsViewMode.SINGLE_SCRIPTS) ScriptPresets.all.size else ChainPresets.all.size
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
                                .testTag("presets_mode_${mode.name.lowercase()}"),
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

            if (viewMode == PresetsViewMode.SINGLE_SCRIPTS) {
                item(key = "category_chips") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CategoryChip(
                            label = "All (${ScriptPresets.all.size})",
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            testTag = "preset_filter_all"
                        )
                        JobCategory.entries.forEach { cat ->
                            CategoryChip(
                                label = cat.label,
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                testTag = "preset_filter_${cat.name.lowercase()}"
                            )
                        }
                    }
                }

                items(items = presets, key = { it.id }) { preset ->
                    PresetCard(
                        preset = preset,
                        onCustomize = { onCustomizePreset(preset) },
                        onQuickAdd = { onQuickAddPreset(preset) }
                    )
                }
            } else {
                items(items = ChainPresets.all, key = { it.id }) { chainPreset ->
                    ChainPresetCard(
                        preset = chainPreset,
                        onCustomize = { onCustomizeChainPreset(chainPreset) },
                        onQuickAdd = { onQuickAddChainPreset(chainPreset) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PresetCard(
    preset: ScriptPreset,
    onCustomize: () -> Unit,
    onQuickAdd: () -> Unit
) {
    val scheduleText = remember(preset.defaultCron) {
        CronExpressionParser.describeHuman(preset.defaultCron)
    }
    ResendCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryIconBadge(category = preset.category)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = scheduleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (preset.usesTermuxApi) {
                MonoBadge(text = "TERMUX:API", textColor = CyanInfo)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = preset.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonoBadge(text = preset.defaultCron, leadingDotColor = EmeraldPrimary)
            MonoBadge(text = preset.recommendedAlert.shortLabel)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = preset.scriptBody,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = JetBrainsMonoFontFamily,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(10.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ResendOutlineButton(
                text = "Customize",
                icon = Icons.Outlined.Edit,
                onClick = onCustomize,
                modifier = Modifier.weight(1f),
                testTag = "customize_${preset.id}"
            )
            ResendPrimaryButton(
                text = "Quick Add",
                icon = Icons.Outlined.Add,
                onClick = onQuickAdd,
                modifier = Modifier.weight(1f),
                testTag = "quick_add_${preset.id}"
            )
        }
    }
}

@Composable
private fun ChainPresetCard(
    preset: ChainPreset,
    onCustomize: () -> Unit,
    onQuickAdd: () -> Unit
) {
    val scheduleText = remember(preset.defaultCron) {
        CronExpressionParser.describeHuman(preset.defaultCron)
    }
    ResendCard(modifier = Modifier.fillMaxWidth()) {
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
                    contentDescription = "Action Chain Preset",
                    tint = CyanInfo,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$scheduleText · ${preset.steps.size} steps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MonoBadge(text = "PIPELINE", textColor = CyanInfo)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = preset.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            preset.steps.forEachIndexed { idx, step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%02d", idx + 1),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = EmeraldPrimary
                    )
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
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

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ResendOutlineButton(
                text = "Customize",
                icon = Icons.Outlined.Edit,
                onClick = onCustomize,
                modifier = Modifier.weight(1f),
                testTag = "customize_${preset.id}"
            )
            ResendPrimaryButton(
                text = "Quick Add",
                icon = Icons.Outlined.Add,
                onClick = onQuickAdd,
                modifier = Modifier.weight(1f),
                testTag = "quick_add_${preset.id}"
            )
        }
    }
}
