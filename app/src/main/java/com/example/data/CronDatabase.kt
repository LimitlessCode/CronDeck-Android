package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CronJobEntity::class, ExecutionLogEntity::class, ActionChainEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(CronTypeConverters::class)
abstract class CronDatabase : RoomDatabase() {
    abstract fun cronDao(): CronDao

    companion object {
        @Volatile
        private var INSTANCE: CronDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `action_chains` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `cronExpression` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `stopOnError` INTEGER NOT NULL,
                        `stepsJson` TEXT NOT NULL,
                        `alertMode` TEXT NOT NULL,
                        `respectDnd` INTEGER NOT NULL,
                        `lastRunAt` INTEGER,
                        `lastExitCode` INTEGER,
                        `lastStatus` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_action_chains_isEnabled` ON `action_chains` (`isEnabled`)"
                )
            }
        }

        fun getInstance(context: Context): CronDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CronDatabase::class.java,
                    "crondeck_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
