package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ResearchNoteDao {
    @Query("SELECT * FROM research_notes ORDER BY isPinned DESC, createdAt DESC")
    fun getAllNotes(): Flow<List<ResearchNoteEntity>>

    @Query("SELECT * FROM research_notes WHERE notebookType = :type ORDER BY isPinned DESC, createdAt DESC")
    fun getNotesByType(type: String): Flow<List<ResearchNoteEntity>>

    @Query("SELECT * FROM research_notes WHERE targetSymbol = :symbol ORDER BY createdAt DESC")
    fun getNotesBySymbol(symbol: String): Flow<List<ResearchNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: ResearchNoteEntity): Long

    @Update
    suspend fun updateNote(note: ResearchNoteEntity)

    @Delete
    suspend fun deleteNote(note: ResearchNoteEntity)

    @Query("DELETE FROM research_notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM research_notes")
    suspend fun clearAllNotes()

    @Query("SELECT COUNT(*) FROM research_notes")
    suspend fun getCount(): Int
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alert_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<AlertRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AlertRuleEntity): Long

    @Delete
    suspend fun deleteRule(rule: AlertRuleEntity)

    @Query("SELECT * FROM alert_records ORDER BY timestamp DESC LIMIT 100")
    fun getAllRecords(): Flow<List<AlertRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AlertRecordEntity): Long

    @Query("UPDATE alert_records SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

    @Query("DELETE FROM alert_records")
    suspend fun clearAllRecords()
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist_stocks ORDER BY addedAt ASC")
    fun getAllWatchlist(): Flow<List<WatchlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStock(stock: WatchlistEntity)

    @Delete
    suspend fun deleteStock(stock: WatchlistEntity)

    @Query("DELETE FROM watchlist_stocks WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)

    @Query("SELECT COUNT(*) FROM watchlist_stocks")
    suspend fun getCount(): Int
}

@Dao
interface CustomAnalystDao {
    @Query("SELECT * FROM custom_analysts ORDER BY isBuiltIn DESC, createdAt ASC")
    fun getAllAnalysts(): Flow<List<CustomAnalystEntity>>

    @Query("SELECT * FROM custom_analysts WHERE id = :id")
    suspend fun getAnalystById(id: Long): CustomAnalystEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalyst(analyst: CustomAnalystEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysts(analysts: List<CustomAnalystEntity>)

    @Update
    suspend fun updateAnalyst(analyst: CustomAnalystEntity)

    @Delete
    suspend fun deleteAnalyst(analyst: CustomAnalystEntity)

    @Query("DELETE FROM custom_analysts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM custom_analysts")
    suspend fun getCount(): Int
}

@Dao
interface PromptHistoryDao {
    @Query("SELECT * FROM prompt_history WHERE analystId = :analystId ORDER BY version DESC")
    fun getHistoryForAnalyst(analystId: Long): Flow<List<PromptHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PromptHistoryEntity): Long

    @Query("DELETE FROM prompt_history WHERE analystId = :analystId")
    suspend fun deleteHistoryForAnalyst(analystId: Long)
}

@Dao
interface WorkflowExecutionDao {
    @Query("SELECT * FROM workflow_executions ORDER BY createdAt DESC")
    fun getAllExecutions(): Flow<List<WorkflowExecutionRecordEntity>>

    @Query("SELECT * FROM workflow_executions ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestExecution(): WorkflowExecutionRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecution(record: WorkflowExecutionRecordEntity): Long

    @Query("DELETE FROM workflow_executions")
    suspend fun clearAll()
}
