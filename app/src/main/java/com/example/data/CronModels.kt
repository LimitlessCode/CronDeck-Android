package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class AlertMode(val label: String, val shortLabel: String) {
    SILENT("Silent Log Only", "Silent"),
    NOTIFICATION("Standard Notification", "Notify"),
    ALARM("Alarm Chime", "Alarm"),
    TTS("Spoken TTS Alert", "TTS"),
    ALARM_AND_TTS("Alarm + Spoken TTS", "Alarm+TTS")
}

enum class JobRunStatus(val label: String) {
    IDLE("Ready"),
    DISPATCHED("Dispatched"),
    SUCCESS("Completed"),
    FAILED("Failed"),
    DND_MUTED("DND Muted")
}

enum class JobCategory(val label: String) {
    SYNC("Sync"),
    BACKUP("Backup"),
    SYSTEM("System"),
    NETWORK("Network"),
    BATTERY("Battery"),
    CUSTOM("Custom")
}

@Entity(
    tableName = "cron_jobs",
    indices = [Index(value = ["isEnabled"]), Index(value = ["category"])]
)
data class CronJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val cronExpression: String,
    val scriptContent: String,
    val encryptedScript: String? = null,
    val encryptionIv: String? = null,
    val workingDirectory: String = "/data/data/com.termux/files/home",
    val isEnabled: Boolean = true,
    val runInBackground: Boolean = true,
    val isEncrypted: Boolean = false,
    val category: JobCategory = JobCategory.CUSTOM,
    val alertMode: AlertMode = AlertMode.NOTIFICATION,
    val respectDnd: Boolean = true,
    val lastRunAt: Long? = null,
    val lastExitCode: Int? = null,
    val lastDurationMs: Long? = null,
    val lastStatus: JobRunStatus = JobRunStatus.IDLE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "execution_logs",
    indices = [Index(value = ["jobId"]), Index(value = ["executedAt"])]
)
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val jobName: String,
    val executedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val exitCode: Int = 0,
    val status: JobRunStatus,
    val outputSnippet: String,
    val alertBehavior: String
)

/**
 * Domain model with decrypted script hydrated in memory on demand.
 */
data class CronJob(
    val entity: CronJobEntity,
    val decryptedScript: String,
    val decryptionError: Boolean = false
) {
    val id: Long get() = entity.id
    val name: String get() = entity.name
    val description: String get() = entity.description
    val cronExpression: String get() = entity.cronExpression
    val workingDirectory: String get() = entity.workingDirectory
    val isEnabled: Boolean get() = entity.isEnabled
    val runInBackground: Boolean get() = entity.runInBackground
    val isEncrypted: Boolean get() = entity.isEncrypted
    val category: JobCategory get() = entity.category
    val alertMode: AlertMode get() = entity.alertMode
    val respectDnd: Boolean get() = entity.respectDnd
    val lastRunAt: Long? get() = entity.lastRunAt
    val lastExitCode: Int? get() = entity.lastExitCode
    val lastDurationMs: Long? get() = entity.lastDurationMs
    val lastStatus: JobRunStatus get() = entity.lastStatus
}

class CronTypeConverters {
    @TypeConverter
    fun fromAlertMode(value: AlertMode): String = value.name

    @TypeConverter
    fun toAlertMode(value: String): AlertMode =
        runCatching { AlertMode.valueOf(value) }.getOrDefault(AlertMode.NOTIFICATION)

    @TypeConverter
    fun fromJobRunStatus(value: JobRunStatus): String = value.name

    @TypeConverter
    fun toJobRunStatus(value: String): JobRunStatus =
        runCatching { JobRunStatus.valueOf(value) }.getOrDefault(JobRunStatus.IDLE)

    @TypeConverter
    fun fromJobCategory(value: JobCategory): String = value.name

    @TypeConverter
    fun toJobCategory(value: String): JobCategory =
        runCatching { JobCategory.valueOf(value) }.getOrDefault(JobCategory.CUSTOM)
}
