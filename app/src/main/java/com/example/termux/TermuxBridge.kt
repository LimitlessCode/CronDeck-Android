package com.example.termux

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.ActionChain
import com.example.data.CronJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class TermuxEnvironmentStatus(
    val isTermuxInstalled: Boolean,
    val isTermuxApiInstalled: Boolean,
    val hasRunCommandPermission: Boolean,
    val termuxVersionName: String?
) {
    val isReady: Boolean get() = isTermuxInstalled && hasRunCommandPermission
}

sealed class DispatchResult {
    data class Dispatched(val timestamp: Long) : DispatchResult()
    data object TermuxNotInstalled : DispatchResult()
    data object PermissionDenied : DispatchResult()
    data class BlockedOrFailed(val reason: String) : DispatchResult()
}

data class CrontabSyncPayload(
    val rawCrontab: String,
    val stderr: String,
    val exitCode: Int
)

object TermuxSyncEvents {
    private val _crontabResponses = MutableSharedFlow<CrontabSyncPayload>(extraBufferCapacity = 4)
    val crontabResponses: SharedFlow<CrontabSyncPayload> = _crontabResponses.asSharedFlow()

    fun emitCrontabResponse(payload: CrontabSyncPayload) {
        _crontabResponses.tryEmit(payload)
    }
}

object TermuxBridge {
    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_API_PACKAGE = "com.termux.api"
    const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"

    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    private const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"
    private const val TERMUX_HOME = "/data/data/com.termux/files/home"

    fun detectEnvironment(context: Context): TermuxEnvironmentStatus {
        val pm = context.packageManager
        val termuxInfo = getPackageInfoCompat(pm, TERMUX_PACKAGE)
        val termuxApiInfo = getPackageInfoCompat(pm, TERMUX_API_PACKAGE)
        val hasPerm = context.checkSelfPermission(PERMISSION_RUN_COMMAND) ==
            PackageManager.PERMISSION_GRANTED

        return TermuxEnvironmentStatus(
            isTermuxInstalled = termuxInfo != null,
            isTermuxApiInstalled = termuxApiInfo != null,
            hasRunCommandPermission = hasPerm,
            termuxVersionName = termuxInfo?.versionName
        )
    }

    fun dispatchJob(context: Context, job: CronJob): DispatchResult {
        val env = detectEnvironment(context)
        if (!env.isTermuxInstalled) return DispatchResult.TermuxNotInstalled
        if (!env.hasRunCommandPermission) return DispatchResult.PermissionDenied

        val resultIntent = Intent(context, TermuxResultReceiver::class.java).apply {
            action = TermuxResultReceiver.ACTION_COMMAND_RESULT
            putExtra(TermuxResultReceiver.EXTRA_JOB_ID, job.id)
            putExtra(TermuxResultReceiver.EXTRA_JOB_NAME, job.name)
            putExtra(TermuxResultReceiver.EXTRA_DISPATCHED_AT, System.currentTimeMillis())
            putExtra(TermuxResultReceiver.EXTRA_IS_ENCRYPTED, job.isEncrypted)
            putExtra(TermuxResultReceiver.EXTRA_ALERT_MODE, job.alertMode.name)
            putExtra(TermuxResultReceiver.EXTRA_RESPECT_DND, job.respectDnd)
            putExtra(TermuxResultReceiver.EXTRA_IS_CRONTAB_SYNC, false)
        }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (job.id.toInt() and 0x7FFF) + 1000,
            resultIntent,
            pendingFlags
        )

        return sendRunCommandIntent(
            context = context,
            bashScript = job.decryptedScript,
            workingDirectory = job.workingDirectory.ifBlank { TERMUX_HOME },
            background = job.runInBackground,
            pendingIntent = pendingIntent
        )
    }

    fun dispatchChain(context: Context, chain: ActionChain): DispatchResult {
        val env = detectEnvironment(context)
        if (!env.isTermuxInstalled) return DispatchResult.TermuxNotInstalled
        if (!env.hasRunCommandPermission) return DispatchResult.PermissionDenied

        val resultIntent = Intent(context, TermuxResultReceiver::class.java).apply {
            action = TermuxResultReceiver.ACTION_COMMAND_RESULT
            putExtra(TermuxResultReceiver.EXTRA_CHAIN_ID, chain.id)
            putExtra(TermuxResultReceiver.EXTRA_JOB_NAME, chain.name)
            putExtra(TermuxResultReceiver.EXTRA_DISPATCHED_AT, System.currentTimeMillis())
            putExtra(TermuxResultReceiver.EXTRA_IS_ENCRYPTED, false)
            putExtra(TermuxResultReceiver.EXTRA_ALERT_MODE, chain.alertMode.name)
            putExtra(TermuxResultReceiver.EXTRA_RESPECT_DND, chain.respectDnd)
            putExtra(TermuxResultReceiver.EXTRA_IS_CRONTAB_SYNC, false)
        }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (chain.id.toInt() and 0x7FFF) + 50000,
            resultIntent,
            pendingFlags
        )

        return sendRunCommandIntent(
            context = context,
            bashScript = chain.compilePipelineScript(),
            workingDirectory = TERMUX_HOME,
            background = true,
            pendingIntent = pendingIntent
        )
    }

    fun requestCrontabList(context: Context): DispatchResult {
        val env = detectEnvironment(context)
        if (!env.isTermuxInstalled) return DispatchResult.TermuxNotInstalled
        if (!env.hasRunCommandPermission) return DispatchResult.PermissionDenied

        val resultIntent = Intent(context, TermuxResultReceiver::class.java).apply {
            action = TermuxResultReceiver.ACTION_COMMAND_RESULT
            putExtra(TermuxResultReceiver.EXTRA_IS_CRONTAB_SYNC, true)
            putExtra(TermuxResultReceiver.EXTRA_DISPATCHED_AT, System.currentTimeMillis())
        }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9901,
            resultIntent,
            pendingFlags
        )

        return sendRunCommandIntent(
            context = context,
            bashScript = "crontab -l 2>/dev/null || true",
            workingDirectory = TERMUX_HOME,
            background = true,
            pendingIntent = pendingIntent
        )
    }

    fun pushCrontab(context: Context, jobs: List<CronJob>): DispatchResult {
        val env = detectEnvironment(context)
        if (!env.isTermuxInstalled) return DispatchResult.TermuxNotInstalled
        if (!env.hasRunCommandPermission) return DispatchResult.PermissionDenied

        val crontabBody = buildCrontabDocument(jobs)
        val escaped = crontabBody.replace("'", "'\"'\"'")
        val script = "printf '%s\\n' '$escaped' | crontab -"

        return sendRunCommandIntent(
            context = context,
            bashScript = script,
            workingDirectory = TERMUX_HOME,
            background = true,
            pendingIntent = null
        )
    }

    fun buildCrontabDocument(jobs: List<CronJob>): String {
        val sb = StringBuilder()
        sb.appendLine("# Managed by CronDeck for Termux")
        jobs.forEach { job ->
            sb.appendLine("# CRONDECK_NAME: ${job.name}")
            val singleLineCmd = if (job.isEncrypted) {
                "echo 'Encrypted CronDeck job (${job.name}) - trigger via CronDeck'"
            } else {
                job.decryptedScript
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#!") }
                    .joinToString(" && ")
            }
            val prefix = if (job.isEnabled) "" else "# DISABLED "
            sb.appendLine("$prefix${job.cronExpression} $singleLineCmd")
        }
        return sb.toString().trimEnd()
    }

    fun openTermuxApp(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    private fun sendRunCommandIntent(
        context: Context,
        bashScript: String,
        workingDirectory: String,
        background: Boolean,
        pendingIntent: PendingIntent?
    ): DispatchResult {
        val intent = Intent(ACTION_RUN_COMMAND).apply {
            component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-c", bashScript))
            putExtra(EXTRA_WORKDIR, workingDirectory)
            putExtra(EXTRA_BACKGROUND, background)
            putExtra(EXTRA_SESSION_ACTION, "0")
            if (pendingIntent != null) {
                putExtra(EXTRA_PENDING_INTENT, pendingIntent)
            }
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            DispatchResult.Dispatched(System.currentTimeMillis())
        } catch (sec: SecurityException) {
            DispatchResult.BlockedOrFailed(
                sec.message ?: "Enable allow-external-apps=true in ~/.termux/termux.properties"
            )
        } catch (ex: Exception) {
            DispatchResult.BlockedOrFailed(ex.message ?: "Failed to reach Termux RunCommandService")
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfoCompat(pm: PackageManager, pkg: String) = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageInfo(pkg, 0)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
