package com.example.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.CronDatabase
import com.example.data.CronRepository
import com.example.data.JobRunStatus
import com.example.data.SettingsPreferences
import com.example.security.CryptoVault
import com.example.termux.AlertDispatcher
import com.example.termux.DispatchResult
import com.example.termux.TermuxBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CronAlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TRIGGER_JOB = "com.example.ACTION_TRIGGER_CRON_JOB"
        const val ACTION_TRIGGER_CHAIN = "com.example.ACTION_TRIGGER_ACTION_CHAIN"
        const val EXTRA_JOB_ID = "extra_job_id"
        const val EXTRA_CHAIN_ID = "extra_chain_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getLongExtra(EXTRA_JOB_ID, -1L)
        val chainId = intent.getLongExtra(EXTRA_CHAIN_ID, -1L)
        if (jobId <= 0L && chainId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = CronRepository(
                    CronDatabase.getInstance(context).cronDao(),
                    CryptoVault()
                )
                val settings = SettingsPreferences(context).getSnapshot()

                if (chainId > 0L) {
                    val chain = repo.getChainById(chainId) ?: return@launch
                    if (!chain.isEnabled) return@launch
                    CronScheduler.scheduleChain(context, chain)
                    when (val result = TermuxBridge.dispatchChain(context, chain)) {
                        is DispatchResult.Dispatched -> {
                            val alertSummary = AlertDispatcher.dispatchAlert(
                                context = context,
                                jobName = "Chain: ${chain.name}",
                                alertMode = chain.alertMode,
                                settings = settings,
                                jobRespectsDnd = chain.respectDnd,
                                isFailure = false,
                                detailMessage = "Scheduled ${chain.steps.size}-step chain dispatched to Termux"
                            )
                            repo.recordChainExecution(
                                chainId = chain.id,
                                chainName = chain.name,
                                exitCode = 0,
                                durationMs = 1L,
                                status = JobRunStatus.DISPATCHED,
                                outputSnippet = "Dispatched ${chain.steps.size}-step chain to Termux",
                                alertBehavior = alertSummary
                            )
                        }
                        else -> {
                            val msg = dispatchErrorText(result)
                            val alertSummary = AlertDispatcher.dispatchAlert(
                                context = context,
                                jobName = "Chain: ${chain.name}",
                                alertMode = chain.alertMode,
                                settings = settings,
                                jobRespectsDnd = chain.respectDnd,
                                isFailure = true,
                                detailMessage = msg
                            )
                            repo.recordChainExecution(
                                chainId = chain.id,
                                chainName = chain.name,
                                exitCode = 1,
                                durationMs = 1L,
                                status = JobRunStatus.FAILED,
                                outputSnippet = msg,
                                alertBehavior = alertSummary
                            )
                        }
                    }
                    return@launch
                }

                val job = repo.getJobById(jobId) ?: return@launch
                if (!job.isEnabled) return@launch
                CronScheduler.scheduleJob(context, job)

                when (val result = TermuxBridge.dispatchJob(context, job)) {
                    is DispatchResult.Dispatched -> {
                        val alertSummary = AlertDispatcher.dispatchAlert(
                            context = context,
                            jobName = job.name,
                            alertMode = job.alertMode,
                            settings = settings,
                            jobRespectsDnd = job.respectDnd,
                            isFailure = false,
                            detailMessage = "Scheduled task dispatched to Termux"
                        )
                        repo.recordExecution(
                            jobId = job.id,
                            jobName = job.name,
                            exitCode = 0,
                            durationMs = 1L,
                            status = JobRunStatus.DISPATCHED,
                            outputSnippet = "Dispatched to Termux RunCommandService",
                            alertBehavior = alertSummary,
                            isEncrypted = job.isEncrypted
                        )
                    }
                    else -> {
                        val msg = dispatchErrorText(result)
                        val alertSummary = AlertDispatcher.dispatchAlert(
                            context = context,
                            jobName = job.name,
                            alertMode = job.alertMode,
                            settings = settings,
                            jobRespectsDnd = job.respectDnd,
                            isFailure = true,
                            detailMessage = msg
                        )
                        repo.recordExecution(
                            jobId = job.id,
                            jobName = job.name,
                            exitCode = 1,
                            durationMs = 1L,
                            status = JobRunStatus.FAILED,
                            outputSnippet = msg,
                            alertBehavior = alertSummary,
                            isEncrypted = job.isEncrypted
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun dispatchErrorText(result: DispatchResult): String = when (result) {
        DispatchResult.TermuxNotInstalled -> "Termux app not installed"
        DispatchResult.PermissionDenied -> "RUN_COMMAND permission not granted"
        is DispatchResult.BlockedOrFailed -> result.reason
        is DispatchResult.Dispatched -> "Dispatched"
    }
}
