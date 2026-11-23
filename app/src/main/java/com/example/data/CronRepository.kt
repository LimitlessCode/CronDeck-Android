package com.example.data

import com.example.cron.CronExpressionParser
import com.example.security.CryptoVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CronRepository(
    private val dao: CronDao,
    private val cryptoVault: CryptoVault
) {
    companion object {
        const val ENCRYPTED_PLACEHOLDER = "[AES-256-GCM ENCRYPTED PAYLOAD]"
    }

    val jobsFlow: Flow<List<CronJob>> = dao.observeAllJobs().map { entities ->
        withContext(Dispatchers.Default) {
            entities.map { hydrateJob(it) }
        }
    }

    val logsFlow: Flow<List<ExecutionLogEntity>> = dao.observeRecentLogs()

    val chainsFlow: Flow<List<ActionChain>> = dao.observeAllChains().map { entities ->
        withContext(Dispatchers.Default) {
            entities.map { entity ->
                ActionChain(
                    entity = entity,
                    steps = ChainStepSerializer.decode(entity.stepsJson)
                )
            }
        }
    }

    suspend fun ensureStarterDataSeeded() = withContext(Dispatchers.IO) {
        if (dao.getJobCount() == 0) {
            ScriptPresets.starterJobs.forEach { dao.insertJob(it) }
        }
        if (dao.getChainCount() == 0) {
            ChainPresets.starterChains.forEach { dao.insertChain(it) }
        }
    }

    suspend fun getAllJobsHydrated(): List<CronJob> = withContext(Dispatchers.IO) {
        dao.getAllJobsOnce().map { hydrateJob(it) }
    }

    suspend fun getEnabledJobsHydrated(): List<CronJob> = withContext(Dispatchers.IO) {
        dao.getEnabledJobs().map { hydrateJob(it) }
    }

    suspend fun getJobById(id: Long): CronJob? = withContext(Dispatchers.IO) {
        dao.getJobById(id)?.let { hydrateJob(it) }
    }

    suspend fun saveJob(
        existingId: Long?,
        name: String,
        description: String,
        cronExpression: String,
        plainScript: String,
        workingDirectory: String,
        isEnabled: Boolean,
        runInBackground: Boolean,
        isEncrypted: Boolean,
        category: JobCategory,
        alertMode: AlertMode,
        respectDnd: Boolean
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val normalizedCron = CronExpressionParser.normalizeMacro(cronExpression)
        val existing = existingId?.let { dao.getJobById(it) }

        val (storedPlain, encCipher, encIv) = if (isEncrypted) {
            val payload = cryptoVault.encrypt(plainScript)
            Triple(ENCRYPTED_PLACEHOLDER, payload.ciphertextBase64, payload.ivBase64)
        } else {
            Triple(plainScript, null, null)
        }

        val entity = CronJobEntity(
            id = existing?.id ?: 0,
            name = name.trim(),
            description = description.trim(),
            cronExpression = normalizedCron,
            scriptContent = storedPlain,
            encryptedScript = encCipher,
            encryptionIv = encIv,
            workingDirectory = workingDirectory.trim().ifEmpty { "/data/data/com.termux/files/home" },
            isEnabled = isEnabled,
            runInBackground = runInBackground,
            isEncrypted = isEncrypted,
            category = category,
            alertMode = alertMode,
            respectDnd = respectDnd,
            lastRunAt = existing?.lastRunAt,
            lastExitCode = existing?.lastExitCode,
            lastDurationMs = existing?.lastDurationMs,
            lastStatus = existing?.lastStatus ?: JobRunStatus.IDLE,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )

        if (existing != null) {
            dao.updateJob(entity)
            entity.id
        } else {
            dao.insertJob(entity)
        }
    }

    suspend fun toggleJobEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.setJobEnabled(id, enabled)
    }

    suspend fun setJobsEnabledBulk(ids: List<Long>, enabled: Boolean) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) {
            dao.setJobsEnabled(ids, enabled)
        }
    }

    suspend fun deleteJobsBulk(ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) {
            dao.deleteJobsByIds(ids)
        }
    }

    suspend fun toggleJobEncryption(id: Long, encrypt: Boolean) = withContext(Dispatchers.IO) {
        val entity = dao.getJobById(id) ?: return@withContext
        val hydrated = hydrateJob(entity)
        if (hydrated.decryptionError) return@withContext
        saveJob(
            existingId = entity.id,
            name = entity.name,
            description = entity.description,
            cronExpression = entity.cronExpression,
            plainScript = hydrated.decryptedScript,
            workingDirectory = entity.workingDirectory,
            isEnabled = entity.isEnabled,
            runInBackground = entity.runInBackground,
            isEncrypted = encrypt,
            category = entity.category,
            alertMode = entity.alertMode,
            respectDnd = entity.respectDnd
        )
    }

    suspend fun encryptAllUnencrypted(): Int = withContext(Dispatchers.IO) {
        val all = dao.getAllJobsOnce()
        var count = 0
        all.filter { !it.isEncrypted }.forEach { entity ->
            toggleJobEncryption(entity.id, true)
            count++
        }
        count
    }

    suspend fun deleteJob(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteJobById(id)
    }

    // Action Chains
    suspend fun getChainById(id: Long): ActionChain? = withContext(Dispatchers.IO) {
        dao.getChainById(id)?.let { entity ->
            ActionChain(entity = entity, steps = ChainStepSerializer.decode(entity.stepsJson))
        }
    }

    suspend fun getEnabledChains(): List<ActionChain> = withContext(Dispatchers.IO) {
        dao.getEnabledChains().map { entity ->
            ActionChain(entity = entity, steps = ChainStepSerializer.decode(entity.stepsJson))
        }
    }

    suspend fun saveChain(
        existingId: Long?,
        name: String,
        description: String,
        cronExpression: String,
        isEnabled: Boolean,
        stopOnError: Boolean,
        steps: List<ChainStep>,
        alertMode: AlertMode,
        respectDnd: Boolean
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val normalizedCron = CronExpressionParser.normalizeMacro(cronExpression)
        val existing = existingId?.let { dao.getChainById(it) }
        val entity = ActionChainEntity(
            id = existing?.id ?: 0L,
            name = name.trim(),
            description = description.trim(),
            cronExpression = normalizedCron,
            isEnabled = isEnabled,
            stopOnError = stopOnError,
            stepsJson = ChainStepSerializer.encode(steps),
            alertMode = alertMode,
            respectDnd = respectDnd,
            lastRunAt = existing?.lastRunAt,
            lastExitCode = existing?.lastExitCode,
            lastStatus = existing?.lastStatus ?: JobRunStatus.IDLE,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        if (existing != null) {
            dao.updateChain(entity)
            entity.id
        } else {
            dao.insertChain(entity)
        }
    }

    suspend fun toggleChainEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.setChainEnabled(id, enabled)
    }

    suspend fun deleteChain(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteChainById(id)
    }

    suspend fun recordChainExecution(
        chainId: Long,
        chainName: String,
        exitCode: Int,
        durationMs: Long,
        status: JobRunStatus,
        outputSnippet: String,
        alertBehavior: String
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.updateChainRunResult(
            id = chainId,
            runAt = now,
            exitCode = exitCode,
            status = status
        )
        dao.insertLog(
            ExecutionLogEntity(
                jobId = -chainId,
                jobName = "Chain: $chainName",
                executedAt = now,
                durationMs = durationMs,
                exitCode = exitCode,
                status = status,
                outputSnippet = outputSnippet.take(400).ifBlank { "(no output)" },
                alertBehavior = alertBehavior
            )
        )
    }

    suspend fun recordExecution(
        jobId: Long,
        jobName: String,
        exitCode: Int,
        durationMs: Long,
        status: JobRunStatus,
        outputSnippet: String,
        alertBehavior: String,
        isEncrypted: Boolean
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.updateJobRunResult(
            id = jobId,
            runAt = now,
            exitCode = exitCode,
            durationMs = durationMs,
            status = status
        )
        val safeOutput = if (isEncrypted) {
            "[Output redacted: encrypted job]"
        } else {
            outputSnippet.take(400).ifBlank { "(no output)" }
        }
        dao.insertLog(
            ExecutionLogEntity(
                jobId = jobId,
                jobName = jobName,
                executedAt = now,
                durationMs = durationMs,
                exitCode = exitCode,
                status = status,
                outputSnippet = safeOutput,
                alertBehavior = alertBehavior
            )
        )
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        dao.clearLogs()
    }

    private fun hydrateJob(entity: CronJobEntity): CronJob {
        if (!entity.isEncrypted || entity.encryptedScript == null || entity.encryptionIv == null) {
            return CronJob(entity = entity, decryptedScript = entity.scriptContent)
        }
        val result = cryptoVault.decrypt(entity.encryptedScript, entity.encryptionIv)
        return result.fold(
            onSuccess = { plain -> CronJob(entity = entity, decryptedScript = plain) },
            onFailure = {
                CronJob(
                    entity = entity,
                    decryptedScript = "# Unable to decrypt payload with current device keystore",
                    decryptionError = true
                )
            }
        )
    }
}
