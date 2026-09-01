package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.NotebookType

@Entity(tableName = "research_notes")
data class ResearchNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val notebookType: String,      // AFTER_HOURS, DAY_TRADING, FUNDAMENTAL, STRATEGY
    val title: String,
    val targetSymbol: String? = null,
    val targetName: String? = null,
    val summary: String,
    val content: String,
    val rating: String? = null,    // e.g. "強力買進", "拉回布局", "觀望"
    val entryPrice: Double? = null,
    val targetPrice: Double? = null,
    val stopLossPrice: Double? = null,
    val keyFibLevel: String? = null,
    val tags: String = "",         // Comma-separated tags
    val author: String = "AI 策略研究員",
    val dateStr: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

@Entity(tableName = "alert_rules")
data class AlertRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val stockSymbol: String,
    val stockName: String,
    val alertType: String,
    val targetPrice: Double,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "alert_records")
data class AlertRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val stockSymbol: String,
    val stockName: String,
    val alertType: String,
    val message: String,
    val price: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "watchlist_stocks")
data class WatchlistEntity(
    @PrimaryKey
    val symbol: String,
    val name: String,
    val category: String,
    val customNote: String = "",
    val isFavorite: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_analysts")
data class CustomAnalystEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val roleTitle: String,
    val avatarIcon: String = "HUB",
    val themeColorHex: Long = 0xFF38BDF8,
    val specialization: String,
    val systemPrompt: String,
    val analysisStyle: String = "綜合平衡",
    val notebookType: String = "STRATEGY",
    val provider: String = "Google Gemini",
    val modelId: String = "gemini-2.5-flash",
    val modelDisplayName: String = "Gemini 2.5 Flash",
    val upstreamRoleKey: String = "NONE",
    val allowedTools: String = "ALL",
    val scheduleCron: String = "16:00 (盤後每日)",
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val defaultRoleKey: String? = null,
    val promptVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "prompt_history")
data class PromptHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val analystId: Long,
    val version: Int,
    val systemPrompt: String,
    val changeLog: String = "修訂 Prompt",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "workflow_executions")
data class WorkflowExecutionRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val executionDate: String,
    val status: String = "SUCCESS", // SUCCESS, RUNNING, FAILED, CANCELLED
    val totalDurationMs: Long = 0L,
    val summary: String,
    val scenarioA: String = "",
    val scenarioB: String = "",
    val scenarioC: String = "",
    val htmlReportContent: String = "",
    val structuredJson: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
