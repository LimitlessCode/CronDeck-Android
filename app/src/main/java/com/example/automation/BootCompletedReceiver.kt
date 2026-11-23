package com.example.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.CronDatabase
import com.example.data.CronRepository
import com.example.security.CryptoVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = CronRepository(
                    CronDatabase.getInstance(context).cronDao(),
                    CryptoVault()
                )
                CronScheduler.rescheduleAll(context, repo.getEnabledJobsHydrated())
                CronScheduler.rescheduleAllChains(context, repo.getEnabledChains())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
