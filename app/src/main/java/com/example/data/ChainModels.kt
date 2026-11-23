package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class StepCondition(val label: String, val shortLabel: String) {
    ALWAYS("Always Run", "Always"),
    ON_SUCCESS("If Previous Succeeded", "On Success"),
    ON_FAILURE("If Previous Failed", "On Failure")
}

data class ChainStep(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val command: String,
    val delaySeconds: Int = 0,
    val condition: StepCondition = StepCondition.ALWAYS
)

@Entity(
    tableName = "action_chains",
    indices = [Index(value = ["isEnabled"])]
)
data class ActionChainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val cronExpression: String,
    val isEnabled: Boolean = true,
    val stopOnError: Boolean = true,
    val stepsJson: String,
    val alertMode: AlertMode = AlertMode.NOTIFICATION,
    val respectDnd: Boolean = true,
    val lastRunAt: Long? = null,
    val lastExitCode: Int? = null,
    val lastStatus: JobRunStatus = JobRunStatus.IDLE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ActionChain(
    val entity: ActionChainEntity,
    val steps: List<ChainStep>
) {
    val id: Long get() = entity.id
    val name: String get() = entity.name
    val description: String get() = entity.description
    val cronExpression: String get() = entity.cronExpression
    val isEnabled: Boolean get() = entity.isEnabled
    val stopOnError: Boolean get() = entity.stopOnError
    val alertMode: AlertMode get() = entity.alertMode
    val respectDnd: Boolean get() = entity.respectDnd
    val lastRunAt: Long? get() = entity.lastRunAt
    val lastExitCode: Int? get() = entity.lastExitCode
    val lastStatus: JobRunStatus get() = entity.lastStatus

    /**
     * Compiles ordered chain steps into a sequential bash script honoring
     * inter-step delays, condition gates, and stop-on-error semantics.
     */
    fun compilePipelineScript(): String {
        val sb = StringBuilder()
        sb.appendLine("#!/data/data/com.termux/files/usr/bin/bash")
        sb.appendLine("LAST_EXIT=0")
        sb.appendLine("CHAIN_EXIT=0")
        steps.forEachIndexed { index, step ->
            val n = index + 1
            sb.appendLine()
            sb.appendLine("# --- Step $n: ${step.title} ---")
            val bodyBlock = buildString {
                if (step.delaySeconds > 0) {
                    appendLine("  sleep ${step.delaySeconds}")
                }
                appendLine("  echo \"[Step $n] ${step.title.replace("\"", "'")}\"")
                appendLine("  (")
                step.command.lineSequence().forEach { line ->
                    appendLine("    $line")
                }
                appendLine("  )")
                appendLine("  LAST_EXIT=\$?")
                appendLine("  if [ \$LAST_EXIT -ne 0 ]; then CHAIN_EXIT=\$LAST_EXIT; fi")
                if (stopOnError && step.condition != StepCondition.ON_FAILURE) {
                    appendLine("  if [ \$LAST_EXIT -ne 0 ]; then echo \"Halted chain at Step $n (exit \$LAST_EXIT)\"; exit \$LAST_EXIT; fi")
                }
            }
            when (step.condition) {
                StepCondition.ALWAYS -> sb.append(bodyBlock.replace("\n  ", "\n").trimStart())
                StepCondition.ON_SUCCESS -> {
                    sb.appendLine("if [ \$LAST_EXIT -eq 0 ]; then")
                    sb.append(bodyBlock)
                    sb.appendLine("fi")
                }
                StepCondition.ON_FAILURE -> {
                    sb.appendLine("if [ \$LAST_EXIT -ne 0 ]; then")
                    sb.append(bodyBlock)
                    sb.appendLine("fi")
                }
            }
        }
        sb.appendLine("exit \$CHAIN_EXIT")
        return sb.toString()
    }
}

object ChainStepSerializer {
    fun encode(steps: List<ChainStep>): String {
        val arr = JSONArray()
        steps.forEach { step ->
            arr.put(
                JSONObject().apply {
                    put("id", step.id)
                    put("title", step.title)
                    put("command", step.command)
                    put("delaySeconds", step.delaySeconds)
                    put("condition", step.condition.name)
                }
            )
        }
        return arr.toString()
    }

    fun decode(json: String): List<ChainStep> {
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { idx ->
                val obj = arr.getJSONObject(idx)
                val condName = obj.optString("condition", StepCondition.ALWAYS.name)
                ChainStep(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title", "Step ${idx + 1}"),
                    command = obj.optString("command", "echo 'Step ${idx + 1}'"),
                    delaySeconds = obj.optInt("delaySeconds", 0).coerceAtLeast(0),
                    condition = runCatching { StepCondition.valueOf(condName) }
                        .getOrDefault(StepCondition.ALWAYS)
                )
            }
        }.getOrDefault(emptyList())
    }
}
