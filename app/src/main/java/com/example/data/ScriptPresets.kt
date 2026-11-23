package com.example.data

data class ScriptPreset(
    val id: String,
    val title: String,
    val summary: String,
    val category: JobCategory,
    val defaultCron: String,
    val usesTermuxApi: Boolean,
    val recommendedAlert: AlertMode,
    val scriptBody: String,
    val defaultWorkDir: String = "/data/data/com.termux/files/home"
)

object ScriptPresets {
    val all: List<ScriptPreset> = listOf(
        ScriptPreset(
            id = "preset_git_sync",
            title = "Git Repo Auto-Sync",
            summary = "Fetches and rebases all git repositories inside ~/projects cleanly.",
            category = JobCategory.SYNC,
            defaultCron = "*/30 * * * *",
            usesTermuxApi = false,
            recommendedAlert = AlertMode.NOTIFICATION,
            scriptBody = """
                #!/data/data/com.termux/files/usr/bin/bash
                set -e
                TARGET_DIR="${'$'}HOME/projects"
                mkdir -p "${'$'}TARGET_DIR"
                for repo in "${'$'}TARGET_DIR"/*/.git; do
                  [ -d "${'$'}repo" ] || continue
                  dir="${'$'}(dirname "${'$'}repo")"
                  echo "Syncing ${'$'}dir..."
                  git -C "${'$'}dir" pull --rebase --autostash
                done
                echo "Git sync finished at ${'$'}(date '+%H:%M:%S')"
            """.trimIndent()
        ),
        ScriptPreset(
            id = "preset_battery_guard",
            title = "Low Battery Alert Guard",
            summary = "Reads device battery level via Termux:API and fires a spoken or notification warning under 20%.",
            category = JobCategory.BATTERY,
            defaultCron = "*/15 * * * *",
            usesTermuxApi = true,
            recommendedAlert = AlertMode.TTS,
            scriptBody = """
                #!/data/data/com.termux/files/usr/bin/bash
                STATUS=${'$'}(termux-battery-status)
                PCT=${'$'}(echo "${'$'}STATUS" | grep -o '"percentage": [0-9]*' | grep -o '[0-9]*')
                PLUG=${'$'}(echo "${'$'}STATUS" | grep -o '"plugged": "[^"]*"' | cut -d'"' -f4)
                echo "Battery: ${'$'}PCT% (${'$'}PLUG)"
                if [ -n "${'$'}PCT" ] && [ "${'$'}PCT" -lt 20 ] && [ "${'$'}PLUG" = "UNPLUGGED" ]; then
                  termux-notification --title "Low Battery (${'$'}PCT%)" --content "Connect charger soon."
                  exit 1
                fi
            """.trimIndent()
        ),
        ScriptPreset(
            id = "preset_workspace_backup",
            title = "Workspace Tarball Backup",
            summary = "Archives ~/projects into a compressed dated snapshot and retains the newest 5 backups.",
            category = JobCategory.BACKUP,
            defaultCron = "0 2 * * *",
            usesTermuxApi = false,
            recommendedAlert = AlertMode.ALARM_AND_TTS,
            scriptBody = """
                #!/data/data/com.termux/files/usr/bin/bash
                set -e
                BACKUP_DIR="${'$'}HOME/backups"
                mkdir -p "${'$'}BACKUP_DIR" "${'$'}HOME/projects"
                STAMP=${'$'}(date '+%Y%m%d_%H%M')
                OUT="${'$'}BACKUP_DIR/projects_${'$'}STAMP.tar.gz"
                tar -czf "${'$'}OUT" -C "${'$'}HOME" projects
                ls -1t "${'$'}BACKUP_DIR"/projects_*.tar.gz | tail -n +6 | xargs -r rm -f
                echo "Backup created: ${'$'}OUT"
            """.trimIndent()
        ),
        ScriptPreset(
            id = "preset_cache_sweeper",
            title = "Storage & Cache Sweeper",
            summary = "Cleans Termux apt package cache, stale temp files, and logs older than 7 days.",
            category = JobCategory.SYSTEM,
            defaultCron = "0 3 * * 0",
            usesTermuxApi = false,
            recommendedAlert = AlertMode.NOTIFICATION,
            scriptBody = """
                #!/data/data/com.termux/files/usr/bin/bash
                apt-get clean || true
                find "${'$'}PREFIX/tmp" -type f -mtime +2 -delete 2>/dev/null || true
                find "${'$'}HOME" -maxdepth 2 -name "*.log" -mtime +7 -delete 2>/dev/null || true
                df -h "${'$'}HOME" | tail -n 1
            """.trimIndent()
        ),
        ScriptPreset(
            id = "preset_endpoint_ping",
            title = "Server Health Ping",
            summary = "Checks HTTP status and response latency of a critical endpoint with automatic failure alerts.",
            category = JobCategory.NETWORK,
            defaultCron = "*/10 * * * *",
            usesTermuxApi = true,
            recommendedAlert = AlertMode.ALARM,
            scriptBody = """
                #!/data/data/com.termux/files/usr/bin/bash
                URL="https://example.com/health"
                RES=${'$'}(curl -s -o /dev/null -w "%{http_code} %{time_total}s" --max-time 8 "${'$'}URL")
                CODE=${'$'}(echo "${'$'}RES" | awk '{print ${'$'}1}')
                echo "Health ${'$'}URL -> ${'$'}RES"
                if [ "${'$'}CODE" != "200" ]; then
                  termux-toast "Endpoint alert: HTTP ${'$'}CODE"
                  exit 2
                fi
            """.trimIndent()
        ),
        ScriptPreset(
            id = "preset_clipboard_note",
            title = "Clipboard Markdown Archiver",
            summary = "Captures your current clipboard via Termux:API and appends timestamped snippets to daily notes.",
            category = JobCategory.CUSTOM,
            defaultCron = "0 */2 * * *",
            usesTermuxApi = true,
            recommendedAlert = AlertMode.SILENT,
            scriptBody = """
                #!/data/data/com.termux/files/usr/bin/bash
                CLIP=${'$'}(termux-clipboard-get)
                [ -z "${'$'}CLIP" ] && exit 0
                NOTES_DIR="${'$'}HOME/notes"
                mkdir -p "${'$'}NOTES_DIR"
                FILE="${'$'}NOTES_DIR/${'$'}(date '+%Y-%m-%d').md"
                printf "\n## %s\n%s\n" "${'$'}(date '+%H:%M')" "${'$'}CLIP" >> "${'$'}FILE"
                echo "Archived clipboard entry to ${'$'}FILE"
            """.trimIndent()
        )
    )

    val starterJobs: List<CronJobEntity>
        get() {
            val now = System.currentTimeMillis()
            val git = all[0]
            val battery = all[1]
            val backup = all[2]
            return listOf(
                CronJobEntity(
                    name = git.title,
                    description = git.summary,
                    cronExpression = git.defaultCron,
                    scriptContent = git.scriptBody,
                    workingDirectory = git.defaultWorkDir,
                    isEnabled = true,
                    runInBackground = true,
                    isEncrypted = false,
                    category = git.category,
                    alertMode = git.recommendedAlert,
                    respectDnd = true,
                    createdAt = now - 300_000,
                    updatedAt = now - 300_000
                ),
                CronJobEntity(
                    name = battery.title,
                    description = battery.summary,
                    cronExpression = battery.defaultCron,
                    scriptContent = battery.scriptBody,
                    workingDirectory = battery.defaultWorkDir,
                    isEnabled = true,
                    runInBackground = true,
                    isEncrypted = false,
                    category = battery.category,
                    alertMode = battery.recommendedAlert,
                    respectDnd = true,
                    createdAt = now - 200_000,
                    updatedAt = now - 200_000
                ),
                CronJobEntity(
                    name = backup.title,
                    description = backup.summary,
                    cronExpression = backup.defaultCron,
                    scriptContent = backup.scriptBody,
                    workingDirectory = backup.defaultWorkDir,
                    isEnabled = false,
                    runInBackground = true,
                    isEncrypted = false,
                    category = backup.category,
                    alertMode = backup.recommendedAlert,
                    respectDnd = true,
                    createdAt = now - 100_000,
                    updatedAt = now - 100_000
                )
            )
        }
}
