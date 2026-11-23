package com.example.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.cron.CronExpressionParser
import com.example.data.ActionChain
import com.example.data.CronJob

object CronScheduler {

    private const val CHAIN_REQUEST_OFFSET = 500_000

    fun scheduleJob(context: Context, job: CronJob) {
        if (!job.isEnabled) {
            cancelJob(context, job.id)
            return
        }
        val nextRun = CronExpressionParser.nextRunMillis(job.cronExpression) ?: return
        scheduleAlarmAt(context, nextRun, buildJobPendingIntent(context, job.id))
    }

    fun cancelJob(context: Context, jobId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildJobPendingIntent(context, jobId))
    }

    fun rescheduleAll(context: Context, jobs: List<CronJob>) {
        jobs.forEach { job ->
            if (job.isEnabled) scheduleJob(context, job) else cancelJob(context, job.id)
        }
    }

    fun scheduleChain(context: Context, chain: ActionChain) {
        if (!chain.isEnabled) {
            cancelChain(context, chain.id)
            return
        }
        val nextRun = CronExpressionParser.nextRunMillis(chain.cronExpression) ?: return
        scheduleAlarmAt(context, nextRun, buildChainPendingIntent(context, chain.id))
    }

    fun cancelChain(context: Context, chainId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildChainPendingIntent(context, chainId))
    }

    fun rescheduleAllChains(context: Context, chains: List<ActionChain>) {
        chains.forEach { chain ->
            if (chain.isEnabled) scheduleChain(context, chain) else cancelChain(context, chain.id)
        }
    }

    private fun scheduleAlarmAt(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canUseExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (canUseExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun buildJobPendingIntent(context: Context, jobId: Long): PendingIntent {
        val intent = Intent(context, CronAlarmReceiver::class.java).apply {
            action = CronAlarmReceiver.ACTION_TRIGGER_JOB
            putExtra(CronAlarmReceiver.EXTRA_JOB_ID, jobId)
        }
        return PendingIntent.getBroadcast(
            context,
            jobId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildChainPendingIntent(context: Context, chainId: Long): PendingIntent {
        val intent = Intent(context, CronAlarmReceiver::class.java).apply {
            action = CronAlarmReceiver.ACTION_TRIGGER_CHAIN
            putExtra(CronAlarmReceiver.EXTRA_CHAIN_ID, chainId)
        }
        return PendingIntent.getBroadcast(
            context,
            CHAIN_REQUEST_OFFSET + chainId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
