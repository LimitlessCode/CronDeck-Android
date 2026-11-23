package com.example.cron

import java.util.Calendar
import java.util.Locale

data class CronFieldBreakdown(
    val minute: String,
    val hour: String,
    val dayOfMonth: String,
    val month: String,
    val dayOfWeek: String
)

data class ParsedCrontabEntry(
    val name: String,
    val cronExpression: String,
    val command: String,
    val isEnabled: Boolean
)

/**
 * Fast, zero-dependency 5-field standard cron parser, plain-English translator,
 * next-run calculator, and Termux crontab importer/exporter.
 */
object CronExpressionParser {

    private val dayNames = mapOf(
        0 to "Sun", 1 to "Mon", 2 to "Tue", 3 to "Wed",
        4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun"
    )

    fun normalizeMacro(expr: String): String {
        return when (expr.trim().lowercase(Locale.US)) {
            "@hourly" -> "0 * * * *"
            "@daily", "@midnight" -> "0 0 * * *"
            "@weekly" -> "0 0 * * 0"
            "@monthly" -> "0 0 1 * *"
            "@yearly", "@annually" -> "0 0 1 1 *"
            else -> expr.trim().replace(Regex("\\s+"), " ")
        }
    }

    fun breakdown(expr: String): CronFieldBreakdown? {
        val parts = normalizeMacro(expr).split(" ")
        if (parts.size != 5) return null
        return CronFieldBreakdown(
            minute = parts[0],
            hour = parts[1],
            dayOfMonth = parts[2],
            month = parts[3],
            dayOfWeek = parts[4]
        )
    }

    fun isValid(expr: String): Boolean {
        val parts = normalizeMacro(expr).split(" ")
        if (parts.size != 5) return false
        return parseField(parts[0], 0, 59) != null &&
            parseField(parts[1], 0, 23) != null &&
            parseField(parts[2], 1, 31) != null &&
            parseField(parts[3], 1, 12) != null &&
            parseField(parts[4], 0, 7) != null
    }

    fun describeHuman(expr: String): String {
        val normalized = normalizeMacro(expr)
        val parts = normalized.split(" ")
        if (parts.size != 5 || !isValid(normalized)) {
            return "Invalid cron syntax (need 5 fields)"
        }
        val (min, hour, dom, month, dow) = parts

        if (normalized == "* * * * *") return "Every minute"
        if (min.startsWith("*/") && hour == "*" && dom == "*" && month == "*" && dow == "*") {
            val step = min.removePrefix("*/").toIntOrNull()
            if (step != null && step > 0) {
                return if (step == 1) "Every minute" else "Every $step minutes"
            }
        }
        if (min == "0" && hour.startsWith("*/") && dom == "*" && month == "*" && dow == "*") {
            val step = hour.removePrefix("*/").toIntOrNull()
            if (step != null && step > 0) {
                return if (step == 1) "Every hour" else "Every $step hours"
            }
        }
        if (min.toIntOrNull() != null && hour == "*" && dom == "*" && month == "*" && dow == "*") {
            val m = min.toInt()
            return String.format(Locale.US, "Hourly at :%02d", m)
        }

        val minVal = min.toIntOrNull()
        val hourVal = hour.toIntOrNull()
        if (minVal != null && hourVal != null && month == "*") {
            val timeStr = String.format(Locale.US, "%02d:%02d", hourVal, minVal)
            if (dom == "*" && dow == "*") return "Daily at $timeStr"
            if (dom == "*" && dow == "1-5") return "Weekdays at $timeStr"
            if (dom == "*" && dow == "0,6") return "Weekends at $timeStr"
            if (dom == "*" && dow.toIntOrNull() != null) {
                val dayLabel = dayNames[dow.toInt()] ?: "Day $dow"
                return "Every $dayLabel at $timeStr"
            }
            if (dom.toIntOrNull() != null && dow == "*") {
                return "Monthly on day $dom at $timeStr"
            }
        }

        return "Custom ($normalized)"
    }

    /**
     * Fast forward scan for the next matching timestamp within 366 days.
     */
    fun nextRunMillis(expr: String, fromMillis: Long = System.currentTimeMillis()): Long? {
        val parts = normalizeMacro(expr).split(" ")
        if (parts.size != 5) return null

        val minutes = parseField(parts[0], 0, 59) ?: return null
        val hours = parseField(parts[1], 0, 23) ?: return null
        val doms = parseField(parts[2], 1, 31) ?: return null
        val months = parseField(parts[3], 1, 12) ?: return null
        val dowsRaw = parseField(parts[4], 0, 7) ?: return null
        val dows = dowsRaw.map { if (it == 7) 0 else it }.toSet()

        val cal = Calendar.getInstance().apply {
            timeInMillis = fromMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 1)
        }

        val domWildcard = parts[2] == "*"
        val dowWildcard = parts[4] == "*"

        // Bounded scan: up to 366 days of minutes (~527k steps, jumping days/hours when mismatched)
        var guard = 0
        while (guard < 400_000) {
            guard++
            val month = cal.get(Calendar.MONTH) + 1
            if (month !in months) {
                cal.add(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                continue
            }

            val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // Sunday=0..Saturday=6
            val dayMatches = when {
                domWildcard && dowWildcard -> true
                domWildcard -> dayOfWeek in dows
                dowWildcard -> dayOfMonth in doms
                else -> (dayOfMonth in doms) || (dayOfWeek in dows)
            }
            if (!dayMatches) {
                cal.add(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                continue
            }

            val hour = cal.get(Calendar.HOUR_OF_DAY)
            if (hour !in hours) {
                cal.add(Calendar.HOUR_OF_DAY, 1)
                cal.set(Calendar.MINUTE, 0)
                continue
            }

            val minute = cal.get(Calendar.MINUTE)
            if (minute !in minutes) {
                cal.add(Calendar.MINUTE, 1)
                continue
            }

            return cal.timeInMillis
        }
        return null
    }

    fun nextRunsFormatted(expr: String, count: Int = 3, fromMillis: Long = System.currentTimeMillis()): List<Long> {
        val results = mutableListOf<Long>()
        var cursor = fromMillis
        repeat(count) {
            val next = nextRunMillis(expr, cursor) ?: return results
            results.add(next)
            cursor = next
        }
        return results
    }

    fun formatRelativeCountdown(targetMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String {
        if (targetMillis == null) return "Paused"
        val diff = targetMillis - nowMillis
        if (diff <= 0L) return "Due now"
        val totalMinutes = (diff / 60_000L).toInt().coerceAtLeast(1)
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val mins = totalMinutes % 60
        return when {
            days > 0 -> "in ${days}d ${hours}h"
            hours > 0 -> "in ${hours}h ${mins}m"
            else -> "in ${mins}m"
        }
    }

    fun parseCrontabText(rawCrontab: String): List<ParsedCrontabEntry> {
        val entries = mutableListOf<ParsedCrontabEntry>()
        var pendingName: String? = null
        rawCrontab.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach

            if (trimmed.startsWith("#")) {
                val nameMatch = Regex("^#\\s*CRONDECK_NAME:\\s*(.+)$", RegexOption.IGNORE_CASE).find(trimmed)
                if (nameMatch != null) {
                    pendingName = nameMatch.groupValues[1].trim()
                    return@forEach
                }
                val disabledMatch = Regex("^#\\s*(?:DISABLED\\s+)?((?:@\\w+)|(?:\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+\\S+))\\s+(.+)$")
                    .find(trimmed)
                if (disabledMatch != null) {
                    val expr = disabledMatch.groupValues[1]
                    val cmd = disabledMatch.groupValues[2].trim()
                    if (isValid(expr)) {
                        entries.add(
                            ParsedCrontabEntry(
                                name = pendingName ?: deriveNameFromCommand(cmd),
                                cronExpression = normalizeMacro(expr),
                                command = cmd,
                                isEnabled = false
                            )
                        )
                        pendingName = null
                    }
                }
                return@forEach
            }

            val activeMatch = Regex("^((?:@\\w+)|(?:\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+\\S+))\\s+(.+)$")
                .find(trimmed)
            if (activeMatch != null) {
                val expr = activeMatch.groupValues[1]
                val cmd = activeMatch.groupValues[2].trim()
                if (isValid(expr)) {
                    entries.add(
                        ParsedCrontabEntry(
                            name = pendingName ?: deriveNameFromCommand(cmd),
                            cronExpression = normalizeMacro(expr),
                            command = cmd,
                            isEnabled = true
                        )
                    )
                    pendingName = null
                }
            }
        }
        return entries
    }

    private fun deriveNameFromCommand(command: String): String {
        val token = command.trim().split(Regex("\\s+")).firstOrNull()?.substringAfterLast('/') ?: "Imported"
        return "${token.replaceFirstChar { it.uppercase() }} Task"
    }

    private fun parseField(field: String, min: Int, max: Int): Set<Int>? {
        if (field.isBlank()) return null
        val result = mutableSetOf<Int>()
        for (part in field.split(",")) {
            val stepSplit = part.split("/")
            if (stepSplit.size > 2) return null
            val step = if (stepSplit.size == 2) {
                stepSplit[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            } else 1

            val rangeExpr = stepSplit[0]
            when {
                rangeExpr == "*" -> {
                    for (v in min..max step step) result.add(v)
                }
                rangeExpr.contains("-") -> {
                    val bounds = rangeExpr.split("-")
                    if (bounds.size != 2) return null
                    val start = bounds[0].toIntOrNull() ?: return null
                    val end = bounds[1].toIntOrNull() ?: return null
                    if (start < min || end > max || start > end) return null
                    for (v in start..end step step) result.add(v)
                }
                else -> {
                    val value = rangeExpr.toIntOrNull() ?: return null
                    if (value < min || value > max) return null
                    if (stepSplit.size == 2) {
                        for (v in value..max step step) result.add(v)
                    } else {
                        result.add(value)
                    }
                }
            }
        }
        return result.takeIf { it.isNotEmpty() }
    }
}
