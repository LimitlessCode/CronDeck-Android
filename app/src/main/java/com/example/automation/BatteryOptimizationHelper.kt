package com.example.automation

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.example.termux.TermuxBridge

data class BatteryOptimizationStatus(
    val isCronDeckIgnoringOptimizations: Boolean,
    val isCronDeckBackgroundRestricted: Boolean,
    val isTermuxInstalled: Boolean,
    val isTermuxIgnoringOptimizations: Boolean?
) {
    val isCronDeckRestricted: Boolean
        get() = !isCronDeckIgnoringOptimizations || isCronDeckBackgroundRestricted

    val isTermuxRestricted: Boolean
        get() = isTermuxInstalled && (isTermuxIgnoringOptimizations == false)

    val hasRestrictionWarning: Boolean
        get() = isCronDeckRestricted || isTermuxRestricted
}

object BatteryOptimizationHelper {

    fun checkStatus(context: Context): BatteryOptimizationStatus {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val cronDeckIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
        val cronDeckBgRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            am.isBackgroundRestricted
        } else {
            false
        }

        val termuxEnv = TermuxBridge.detectEnvironment(context)
        val termuxIgnoring = if (termuxEnv.isTermuxInstalled) {
            runCatching {
                pm.isIgnoringBatteryOptimizations(TermuxBridge.TERMUX_PACKAGE)
            }.getOrNull()
        } else {
            null
        }

        return BatteryOptimizationStatus(
            isCronDeckIgnoringOptimizations = cronDeckIgnoring,
            isCronDeckBackgroundRestricted = cronDeckBgRestricted,
            isTermuxInstalled = termuxEnv.isTermuxInstalled,
            isTermuxIgnoringOptimizations = termuxIgnoring
        )
    }

    fun openBatteryOptimizationSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            openAppBatteryInfo(context, context.packageName)
        }
    }

    fun openAppBatteryInfo(context: Context, packageName: String): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
