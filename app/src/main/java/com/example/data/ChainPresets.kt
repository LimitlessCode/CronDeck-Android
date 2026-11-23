package com.example.data

data class ChainPreset(
    val id: String,
    val title: String,
    val summary: String,
    val defaultCron: String,
    val stopOnError: Boolean,
    val recommendedAlert: AlertMode,
    val steps: List<ChainStep>
)

object ChainPresets {
    val all: List<ChainPreset> = listOf(
        ChainPreset(
            id = "chain_git_test_notify",
            title = "Git Sync -> Verify -> Notify",
            summary = "Pulls latest git changes, runs quick repo check, and notifies on success or speaks an alert if rebase fails.",
            defaultCron = "0 * * * *",
            stopOnError = false,
            recommendedAlert = AlertMode.TTS,
            steps = listOf(
                ChainStep(
                    id = "s1",
                    title = "Pull Git Repositories",
                    command = "for d in \"\$HOME/projects\"/*/.git; do [ -d \"\$d\" ] && git -C \"\$(dirname \"\$d\")\" pull --rebase; done",
                    delaySeconds = 0,
                    condition = StepCondition.ALWAYS
                ),
                ChainStep(
                    id = "s2",
                    title = "Verify Repo Cleanliness",
                    command = "echo \"Repos verified at \$(date '+%H:%M')\"",
                    delaySeconds = 1,
                    condition = StepCondition.ON_SUCCESS
                ),
                ChainStep(
                    id = "s3",
                    title = "Failure Recovery Alert",
                    command = "termux-notification --title \"Git Chain Warning\" --content \"Check merge conflicts in ~/projects\"",
                    delaySeconds = 0,
                    condition = StepCondition.ON_FAILURE
                )
            )
        ),
        ChainPreset(
            id = "chain_clean_backup_verify",
            title = "Clean -> Archive -> Verify Backup",
            summary = "Purges stale temporary files before archiving ~/projects and verifying the resulting .tar.gz integrity.",
            defaultCron = "0 2 * * *",
            stopOnError = true,
            recommendedAlert = AlertMode.ALARM_AND_TTS,
            steps = listOf(
                ChainStep(
                    id = "b1",
                    title = "Purge Temp & Log Caches",
                    command = "find \"\$PREFIX/tmp\" -type f -mtime +2 -delete 2>/dev/null || true",
                    delaySeconds = 0,
                    condition = StepCondition.ALWAYS
                ),
                ChainStep(
                    id = "b2",
                    title = "Create Compressed Tarball",
                    command = "mkdir -p \"\$HOME/backups\" \"\$HOME/projects\" && tar -czf \"\$HOME/backups/latest.tar.gz\" -C \"\$HOME\" projects",
                    delaySeconds = 1,
                    condition = StepCondition.ON_SUCCESS
                ),
                ChainStep(
                    id = "b3",
                    title = "Verify Archive Integrity",
                    command = "tar -tzf \"\$HOME/backups/latest.tar.gz\" >/dev/null && echo \"Archive verified OK\"",
                    delaySeconds = 1,
                    condition = StepCondition.ON_SUCCESS
                )
            )
        ),
        ChainPreset(
            id = "chain_battery_guard_sync",
            title = "Battery Check -> Conditional Sync",
            summary = "Queries Termux:API battery level first and only runs heavy sync tasks when battery is above 25% or charging.",
            defaultCron = "*/30 * * * *",
            stopOnError = true,
            recommendedAlert = AlertMode.NOTIFICATION,
            steps = listOf(
                ChainStep(
                    id = "bat1",
                    title = "Check Battery via Termux:API",
                    command = "PCT=\$(termux-battery-status | grep -o '\"percentage\": [0-9]*' | grep -o '[0-9]*'); echo \"Battery=\$PCT%\"; [ \"\${PCT:-100}\" -ge 25 ]",
                    delaySeconds = 0,
                    condition = StepCondition.ALWAYS
                ),
                ChainStep(
                    id = "bat2",
                    title = "Run Heavy Sync Workload",
                    command = "echo \"Power sufficient; running background sync...\" && mkdir -p \"\$HOME/sync\"",
                    delaySeconds = 2,
                    condition = StepCondition.ON_SUCCESS
                ),
                ChainStep(
                    id = "bat3",
                    title = "Skip Notice on Low Power",
                    command = "termux-toast \"Skipped heavy sync due to low battery\"",
                    delaySeconds = 0,
                    condition = StepCondition.ON_FAILURE
                )
            )
        ),
        ChainPreset(
            id = "chain_health_diag_alarm",
            title = "Health Ping -> Capture Trace -> Alert",
            summary = "Monitors endpoint HTTP 200 status and captures verbose curl diagnostics if the service goes down.",
            defaultCron = "*/10 * * * *",
            stopOnError = false,
            recommendedAlert = AlertMode.ALARM,
            steps = listOf(
                ChainStep(
                    id = "h1",
                    title = "HTTP 200 Health Check",
                    command = "CODE=\$(curl -s -o /dev/null -w '%{http_code}' --max-time 6 https://example.com/health); [ \"\$CODE\" = \"200\" ]",
                    delaySeconds = 0,
                    condition = StepCondition.ALWAYS
                ),
                ChainStep(
                    id = "h2",
                    title = "Capture Failure Diagnostics",
                    command = "curl -IsS --max-time 5 https://example.com/health | head -n 5 >> \"\$HOME/health_errors.log\"",
                    delaySeconds = 0,
                    condition = StepCondition.ON_FAILURE
                )
            )
        )
    )

    val starterChains: List<ActionChainEntity>
        get() {
            val now = System.currentTimeMillis()
            val first = all[0]
            val second = all[1]
            return listOf(
                ActionChainEntity(
                    name = first.title,
                    description = first.summary,
                    cronExpression = first.defaultCron,
                    isEnabled = true,
                    stopOnError = first.stopOnError,
                    stepsJson = ChainStepSerializer.encode(first.steps),
                    alertMode = first.recommendedAlert,
                    respectDnd = true,
                    createdAt = now - 150_000,
                    updatedAt = now - 150_000
                ),
                ActionChainEntity(
                    name = second.title,
                    description = second.summary,
                    cronExpression = second.defaultCron,
                    isEnabled = false,
                    stopOnError = second.stopOnError,
                    stepsJson = ChainStepSerializer.encode(second.steps),
                    alertMode = second.recommendedAlert,
                    respectDnd = true,
                    createdAt = now - 100_000,
                    updatedAt = now - 100_000
                )
            )
        }
}
