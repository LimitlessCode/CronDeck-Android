package com.example

import com.example.automation.BatteryOptimizationStatus
import com.example.cron.CronExpressionParser
import com.example.data.ActionChain
import com.example.data.ActionChainEntity
import com.example.data.AppSettings
import com.example.data.ChainPresets
import com.example.data.ChainStep
import com.example.data.StepCondition
import com.example.termux.AlertDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CronDeckUnitTests {

    @Test
    fun cronParser_validatesAndDescribesCommonExpressions() {
        assertTrue(CronExpressionParser.isValid("*/15 * * * *"))
        assertTrue(CronExpressionParser.isValid("0 9 * * 1-5"))
        assertTrue(CronExpressionParser.isValid("@daily"))
        assertFalse(CronExpressionParser.isValid("65 * * * *"))
        assertFalse(CronExpressionParser.isValid("* * *"))

        assertEquals("Every 15 minutes", CronExpressionParser.describeHuman("*/15 * * * *"))
        assertEquals("Weekdays at 09:00", CronExpressionParser.describeHuman("0 9 * * 1-5"))
        assertEquals("Daily at 00:00", CronExpressionParser.describeHuman("@daily"))
    }

    @Test
    fun cronParser_computesNextRunTimestamp() {
        val base = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 23, 10, 12, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val next = CronExpressionParser.nextRunMillis("*/15 * * * *", base)
        assertNotNull(next)
        val cal = Calendar.getInstance().apply { timeInMillis = next!! }
        assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, cal.get(Calendar.MINUTE))
    }

    @Test
    fun cronParser_parsesActiveAndDisabledCrontabEntries() {
        val raw = """
            # CRONDECK_NAME: Git Sync
            */30 * * * * bash ~/sync.sh
            # CRONDECK_NAME: Old Backup
            # DISABLED 0 2 * * * tar -czf ~/b.tar.gz ~/projects
        """.trimIndent()

        val entries = CronExpressionParser.parseCrontabText(raw)
        assertEquals(2, entries.size)
        assertEquals("Git Sync", entries[0].name)
        assertTrue(entries[0].isEnabled)
        assertEquals("Old Backup", entries[1].name)
        assertFalse(entries[1].isEnabled)
    }

    @Test
    fun quietHours_handlesOvernightWindowCorrectly() {
        val settings = AppSettings(
            quietHoursEnabled = true,
            quietStartHour = 22,
            quietStartMinute = 0,
            quietEndHour = 7,
            quietEndMinute = 0
        )

        val lateNight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 30)
        }.timeInMillis
        val earlyMorning = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 15)
        }.timeInMillis
        val midday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        assertTrue(AlertDispatcher.isWithinQuietHours(settings, lateNight))
        assertTrue(AlertDispatcher.isWithinQuietHours(settings, earlyMorning))
        assertFalse(AlertDispatcher.isWithinQuietHours(settings, midday))
    }

    @Test
    fun batteryStatus_flagsCronDeckOrTermuxRestrictions() {
        val unrestricted = BatteryOptimizationStatus(
            isCronDeckIgnoringOptimizations = true,
            isCronDeckBackgroundRestricted = false,
            isTermuxInstalled = true,
            isTermuxIgnoringOptimizations = true
        )
        assertFalse(unrestricted.hasRestrictionWarning)

        val termuxRestricted = unrestricted.copy(isTermuxIgnoringOptimizations = false)
        assertTrue(termuxRestricted.isTermuxRestricted)
        assertTrue(termuxRestricted.hasRestrictionWarning)

        val cronDeckRestricted = unrestricted.copy(isCronDeckIgnoringOptimizations = false)
        assertTrue(cronDeckRestricted.isCronDeckRestricted)
        assertTrue(cronDeckRestricted.hasRestrictionWarning)
    }

    @Test
    fun actionChain_compilesStepsDelaysAndConditionsToBash() {
        val steps = listOf(
            ChainStep(
                id = "s1",
                title = "Pull Repo",
                command = "cd ~/app && git pull",
                delaySeconds = 0,
                condition = StepCondition.ALWAYS
            ),
            ChainStep(
                id = "s2",
                title = "Run Tests",
                command = "pytest",
                delaySeconds = 5,
                condition = StepCondition.ON_SUCCESS
            ),
            ChainStep(
                id = "s3",
                title = "Notify Failure",
                command = "termux-toast 'Failed'",
                delaySeconds = 0,
                condition = StepCondition.ON_FAILURE
            )
        )
        val chain = ActionChain(
            entity = ActionChainEntity(
                id = 1L,
                name = "CI Pipeline",
                description = "Pull and test",
                cronExpression = "0 * * * *",
                stopOnError = false,
                stepsJson = "[]"
            ),
            steps = steps
        )

        val script = chain.compilePipelineScript()
        assertTrue(script.startsWith("#!/data/data/com.termux/files/usr/bin/bash"))
        assertTrue(script.contains("# --- Step 1: Pull Repo ---"))
        assertTrue(script.contains("cd ~/app && git pull"))
        assertTrue(script.contains("if [ \$LAST_EXIT -eq 0 ]; then"))
        assertTrue(script.contains("sleep 5"))
        assertTrue(script.contains("if [ \$LAST_EXIT -ne 0 ]; then"))
        assertTrue(script.contains("exit \$CHAIN_EXIT"))
        assertFalse(script.contains("Halted chain"))
    }

    @Test
    fun actionChain_stopOnErrorEmitsHaltGuard() {
        val chain = ActionChain(
            entity = ActionChainEntity(
                id = 2L,
                name = "Strict Sync",
                description = "Halt on failure",
                cronExpression = "*/30 * * * *",
                stopOnError = true,
                stepsJson = "[]"
            ),
            steps = listOf(
                ChainStep(
                    id = "s1",
                    title = "Step One",
                    command = "echo ok",
                    condition = StepCondition.ON_SUCCESS
                )
            )
        )
        val script = chain.compilePipelineScript()
        assertTrue(script.contains("Halted chain at Step 1"))
    }

    @Test
    fun chainPresets_haveValidCronAndNonEmptySteps() {
        assertTrue(ChainPresets.all.isNotEmpty())
        ChainPresets.all.forEach { preset ->
            assertTrue(
                "Invalid cron expression in preset ${preset.title}: ${preset.defaultCron}",
                CronExpressionParser.isValid(preset.defaultCron)
            )
            assertTrue("Preset ${preset.title} must have steps", preset.steps.isNotEmpty())
            preset.steps.forEach { step ->
                assertTrue(step.title.isNotBlank())
                assertTrue(step.command.isNotBlank())
            }
        }
    }
}
