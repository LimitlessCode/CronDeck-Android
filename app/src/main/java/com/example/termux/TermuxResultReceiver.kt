package com.example.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AlertMode
import com.example.data.CronDatabase
import com.example.data.CronRepository
import com.example.data.JobRunStatus
import com.example.data.SettingsPreferences
import com.example.security.CryptoVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TermuxResultReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_COMMAND_RESULT = "com.example.ACTION_TERMUX_COMMAND_RESULT"
        const val EXTRA_JOB_ID = "extra_job_id"
        const val EXTRA_CHAIN_ID = "extra_chain_id"
        const val EXTRA_JOB_NAME = "extra_job_name"
        const val EXTRA_DISPATCHED_AT = "extra_dispatched_at"
        const val EXTRA_IS_ENCRYPTED = "extra_is_encrypted"
        const val EXTRA_ALERT_MODE = "extra_alert_mode"
        const val EXTRA_RESPECT_DND = "extra_respect_dnd"
        const val EXTRA_IS_CRONTAB_SYNC = "extra_is_crontab_sync"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val resultBundle = intent.getBundleExtra("result")
        val stdout = resultBundle?.getString("stdout") ?: ""
        val stderr = resultBundle?.getString("stderr") ?: ""
        val exitCode = resultBundle?.getInt("exitCode", 0) ?: 0
        val errCode = resultBundle?.getInt("err", -1) ?: -1
        val errMsg = resultBundle?.getString("errmsg") ?: ""

        val isCrontabSync = intent.getBooleanExtra(EXTRA_IS_CRONTAB_SYNC, false)
        if (isCrontabSync) {
            TermuxSyncEvents.emitCrontabResponse(
                CrontabSyncPayload(
                    rawCrontab = stdout,
                    stderr = if (errMsg.isNotBlank()) errMsg else stderr,
                    exitCode = if (errCode > 0) errCode else exitCode
                )
            )
            return
        }

        val jobId = intent.getLongExtra(EXTRA_JOB_ID, -1L)
        val chainId = intent.getLongExtra(EXTRA_CHAIN_ID, -1L)
        if (jobId <= 0L && chainId <= 0L) return
        val jobName = intent.getStringExtra(EXTRA_JOB_NAME)
            ?: if (chainId > 0L) "Chain #$chainId" else "Task #$jobId"
        val dispatchedAt = intent.getLongExtra(EXTRA_DISPATCHED_AT, System.currentTimeMillis())
        val isEncrypted = intent.getBooleanExtra(EXTRA_IS_ENCRYPTED, false)
        val alertMode = runCatching {
            AlertMode.valueOf(intent.getStringExtra(EXTRA_ALERT_MODE) ?: AlertMode.NOTIFICATION.name)
        }.getOrDefault(AlertMode.NOTIFICATION)
        val respectDnd = intent.getBooleanExtra(EXTRA_RESPECT_DND, true)

        val finalExit = if (errCode > 0) errCode else exitCode
        val isFailure = finalExit != 0
        val durationMs = (System.currentTimeMillis() - dispatchedAt).coerceAtLeast(1L)
        val combinedOutput = buildString {
            if (stdout.isNotBlank()) append(stdout.trim())
            if (stderr.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("ERR: ").append(stderr.trim())
            }
            if (errMsg.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(errMsg)
            }
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsPreferences(context).getSnapshot()
                val alertSummary = AlertDispatcher.dispatchAlert(
                    context = context,
                    jobName = jobName,
                    alertMode = alertMode,
                    settings = settings,
                    jobRespectsDnd = respectDnd,
                    isFailure = isFailure,
                    detailMessage = if (isFailure) "Exit code $finalExit" else "Completed in ${durationMs}ms"
                )
                val status = when {
                    alertSummary.startsWith("Muted by") && isFailure -> JobRunStatus.FAILED
                    alertSummary.startsWith("Muted by") -> JobRunStatus.DND_MUTED
                    isFailure -> JobRunStatus.FAILED
                    else -> JobRunStatus.SUCCESS
                }
                val repo = CronRepository(
                    CronDatabase.getInstance(context).cronDao(),
                    CryptoVault()
                )
                if (chainId > 0L) {
                    repo.recordChainExecution(
                        chainId = chainId,
                        chainName = jobName,
                        exitCode = finalExit,
                        durationMs = durationMs,
                        status = status,
                        outputSnippet = combinedOutput,
                        alertBehavior = alertSummary
                    )
                } else {
                    repo.recordExecution(
                        jobId = jobId,
                        jobName = jobName,
                        exitCode = finalExit,
                        durationMs = durationMs,
                        status = status,
                        outputSnippet = combinedOutput,
                        alertBehavior = alertSummary,
                        isEncrypted = isEncrypted
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
