package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ResearchNoteEntity::class,
        AlertRuleEntity::class,
        AlertRecordEntity::class,
        WatchlistEntity::class,
        CustomAnalystEntity::class,
        PromptHistoryEntity::class,
        WorkflowExecutionRecordEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun researchNoteDao(): ResearchNoteDao
    abstract fun alertDao(): AlertDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun customAnalystDao(): CustomAnalystDao
    abstract fun promptHistoryDao(): PromptHistoryDao
    abstract fun workflowExecutionDao(): WorkflowExecutionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tw_stock_research_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
