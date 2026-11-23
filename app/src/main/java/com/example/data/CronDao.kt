package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CronDao {
    @Query("SELECT * FROM cron_jobs ORDER BY isEnabled DESC, updatedAt DESC")
    fun observeAllJobs(): Flow<List<CronJobEntity>>

    @Query("SELECT * FROM cron_jobs WHERE isEnabled = 1")
    suspend fun getEnabledJobs(): List<CronJobEntity>

    @Query("SELECT * FROM cron_jobs")
    suspend fun getAllJobsOnce(): List<CronJobEntity>

    @Query("SELECT * FROM cron_jobs WHERE id = :id LIMIT 1")
    suspend fun getJobById(id: Long): CronJobEntity?

    @Query("SELECT COUNT(*) FROM cron_jobs")
    suspend fun getJobCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: CronJobEntity): Long

    @Update
    suspend fun updateJob(job: CronJobEntity)

    @Query("UPDATE cron_jobs SET isEnabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setJobEnabled(id: Long, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE cron_jobs SET isEnabled = :enabled, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun setJobsEnabled(ids: List<Long>, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query(
        "UPDATE cron_jobs SET lastRunAt = :runAt, lastExitCode = :exitCode, " +
            "lastDurationMs = :durationMs, lastStatus = :status, updatedAt = :runAt WHERE id = :id"
    )
    suspend fun updateJobRunResult(
        id: Long,
        runAt: Long,
        exitCode: Int?,
        durationMs: Long?,
        status: JobRunStatus
    )

    @Query("DELETE FROM cron_jobs WHERE id = :id")
    suspend fun deleteJobById(id: Long)

    @Query("DELETE FROM cron_jobs WHERE id IN (:ids)")
    suspend fun deleteJobsByIds(ids: List<Long>)

    // Execution Logs
    @Query("SELECT * FROM execution_logs ORDER BY executedAt DESC LIMIT 120")
    fun observeRecentLogs(): Flow<List<ExecutionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ExecutionLogEntity): Long

    @Query("DELETE FROM execution_logs")
    suspend fun clearLogs()

    // Action Chains
    @Query("SELECT * FROM action_chains ORDER BY isEnabled DESC, updatedAt DESC")
    fun observeAllChains(): Flow<List<ActionChainEntity>>

    @Query("SELECT * FROM action_chains WHERE id = :id LIMIT 1")
    suspend fun getChainById(id: Long): ActionChainEntity?

    @Query("SELECT * FROM action_chains WHERE isEnabled = 1")
    suspend fun getEnabledChains(): List<ActionChainEntity>

    @Query("SELECT COUNT(*) FROM action_chains")
    suspend fun getChainCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChain(chain: ActionChainEntity): Long

    @Update
    suspend fun updateChain(chain: ActionChainEntity)

    @Query("UPDATE action_chains SET isEnabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setChainEnabled(id: Long, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query(
        "UPDATE action_chains SET lastRunAt = :runAt, lastExitCode = :exitCode, " +
            "lastStatus = :status, updatedAt = :runAt WHERE id = :id"
    )
    suspend fun updateChainRunResult(
        id: Long,
        runAt: Long,
        exitCode: Int?,
        status: JobRunStatus
    )

    @Query("DELETE FROM action_chains WHERE id = :id")
    suspend fun deleteChainById(id: Long)
}
