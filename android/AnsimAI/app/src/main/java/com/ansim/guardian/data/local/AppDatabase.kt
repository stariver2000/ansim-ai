package com.ansim.guardian.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [ScamCaseEntity::class, AlertLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scamCaseDao(): ScamCaseDao
    abstract fun alertLogDao(): AlertLogDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v1 → v2: alert_log 테이블 추가 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS alert_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        riskLevel TEXT NOT NULL,
                        riskEmoji TEXT NOT NULL,
                        category TEXT NOT NULL,
                        sourceLabel TEXT NOT NULL,
                        contentPreview TEXT NOT NULL,
                        detectionMethod TEXT NOT NULL,
                        reason TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ansim_guard.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                instance?.scamCaseDao()?.insertAll(ScamCaseSeedData.all())
                            }
                        }
                    })
                    .build()
                    .also { instance = it }
            }
        }
    }
}
