package com.ansim.guardian.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [ScamCaseEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scamCaseDao(): ScamCaseDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ansim_guard.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // DB 생성 시 초기 사기 사례 데이터 삽입
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
