package com.example.telemetry

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

data class ResourceSample(
    val timestampMillis: Long,
    val cpuPercent: Float,
    val ramPercent: Float,
    val usedRamMb: Long,
    val totalRamMb: Long,
    val activeJobCount: Int
)

/**
 * Samples real device RAM via ActivityManager.MemoryInfo and CPU load via /proc/stat
 * (with Process.getElapsedCpuTime delta fallback when SELinux restricts /proc/stat).
 */
class ResourceMonitor(private val context: Context) {

    private var prevTotalCpuTicks: Long = -1L
    private var prevIdleCpuTicks: Long = -1L
    private var prevProcCpuMs: Long = -1L
    private var prevWallMs: Long = -1L

    fun observeSamples(
        intervalMs: Long = 1500L,
        maxHistory: Int = 24,
        activeJobsCountProvider: () -> Int
    ): Flow<List<ResourceSample>> = flow {
        val history = ArrayDeque<ResourceSample>(maxHistory)
        while (true) {
            val sample = captureSample(activeJobsCountProvider())
            if (history.size >= maxHistory) {
                history.removeFirst()
            }
            history.addLast(sample)
            emit(history.toList())
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    fun captureSample(activeJobCount: Int): ResourceSample {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(1L)
        val availMb = (memInfo.availMem / (1024L * 1024L)).coerceIn(0L, totalMb)
        val usedMb = (totalMb - availMb).coerceAtLeast(0L)
        val ramPct = ((usedMb.toFloat() / totalMb.toFloat()) * 100f).coerceIn(0f, 100f)

        val cpuPct = readSystemOrProcessCpuPercent()

        return ResourceSample(
            timestampMillis = System.currentTimeMillis(),
            cpuPercent = cpuPct,
            ramPercent = ramPct,
            usedRamMb = usedMb,
            totalRamMb = totalMb,
            activeJobCount = activeJobCount
        )
    }

    private fun readSystemOrProcessCpuPercent(): Float {
        // Attempt /proc/stat system-wide CPU delta first
        val procStatPct = tryReadProcStatPercent()
        if (procStatPct != null) return procStatPct

        // Fallback: process CPU time delta across available cores
        val nowWall = SystemClock.elapsedRealtime()
        val nowProcCpu = Process.getElapsedCpuTime()
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val pct = if (prevWallMs > 0L && nowWall > prevWallMs && prevProcCpuMs >= 0L) {
            val wallDelta = (nowWall - prevWallMs).toFloat()
            val cpuDelta = (nowProcCpu - prevProcCpuMs).coerceAtLeast(0L).toFloat()
            ((cpuDelta / (wallDelta * cores)) * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }
        prevWallMs = nowWall
        prevProcCpuMs = nowProcCpu
        return pct
    }

    private fun tryReadProcStatPercent(): Float? {
        return runCatching {
            val firstLine = File("/proc/stat").bufferedReader().use { it.readLine() } ?: return null
            if (!firstLine.startsWith("cpu ")) return null
            val parts = firstLine.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (parts.size < 4) return null
            val idle = parts[3] + (parts.getOrNull(4) ?: 0L)
            val total = parts.sum()
            val pct = if (prevTotalCpuTicks > 0L && total > prevTotalCpuTicks) {
                val totalDelta = (total - prevTotalCpuTicks).toFloat()
                val idleDelta = (idle - prevIdleCpuTicks).coerceAtLeast(0L).toFloat()
                (((totalDelta - idleDelta) / totalDelta) * 100f).coerceIn(0f, 100f)
            } else {
                null
            }
            prevTotalCpuTicks = total
            prevIdleCpuTicks = idle
            pct
        }.getOrNull()
    }
}
