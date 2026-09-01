package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ResearchNoteEntity
import com.example.data.model.*
import com.example.data.repository.StockRepository
import com.example.domain.ai.GeminiResearchService
import com.example.domain.calculator.FibonacciCalculator
import com.example.domain.calculator.TechnicalCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainAppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val repository = StockRepository(db, viewModelScope)
    private val aiService = GeminiResearchService()

    val stocks: StateFlow<List<StockQuote>> = repository.stocksState
    val indices: StateFlow<List<IndexQuote>> = repository.indicesState
    val news: StateFlow<List<MarketNews>> = repository.newsState
    val selectedStock: StateFlow<StockQuote?> = repository.selectedStock
    val allNotes: StateFlow<List<ResearchNoteEntity>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val alertRecords = repository.alertRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val alertRules = repository.alertRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val customAnalysts = repository.customAnalysts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestAlert = repository.latestAlertEvent
    val isSyncing = repository.isSyncing
    val lastSyncTime = repository.lastSyncTime

    // AI Generation Loading State
    private val _isAiGenerating = MutableStateFlow(false)
    val isAiGenerating: StateFlow<Boolean> = _isAiGenerating.asStateFlow()

    private val _generationMessage = MutableStateFlow<String?>(null)
    val generationMessage: StateFlow<String?> = _generationMessage.asStateFlow()

    // Multi-Agent Workflow State
    val workflowExecutions = repository.workflowExecutions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val workflowState = repository.workflowState
    val workflowStepLogs = repository.workflowStepLogs

    // Full Market Stock Screener & AI Recommendations State
    private val _aiRecommendations = MutableStateFlow<List<AIStockRecommendation>>(emptyList())
    val aiRecommendations: StateFlow<List<AIStockRecommendation>> = _aiRecommendations.asStateFlow()

    private val _isScanningMarket = MutableStateFlow(false)
    val isScanningMarket: StateFlow<Boolean> = _isScanningMarket.asStateFlow()

    private val _selectedAnalystForScan = MutableStateFlow<com.example.data.local.CustomAnalystEntity?>(null)
    val selectedAnalystForScan: StateFlow<com.example.data.local.CustomAnalystEntity?> = _selectedAnalystForScan.asStateFlow()

    fun runFullDepartmentPipeline(onComplete: (com.example.data.local.WorkflowExecutionRecordEntity?) -> Unit = {}) {
        viewModelScope.launch {
            val analysts = customAnalysts.value
            val result = repository.runFullWorkflowPipeline(analysts) { msg ->
                _generationMessage.value = msg
            }
            onComplete(result)
        }
    }

    fun cancelWorkflowPipeline() {
        repository.cancelWorkflowPipeline()
    }

    fun getPromptHistory(analystId: Long) = repository.getPromptHistory(analystId)

    fun updateAnalystPrompt(analyst: com.example.data.local.CustomAnalystEntity, newPrompt: String, changeLog: String = "修訂 Prompt") {
        viewModelScope.launch {
            repository.updateAnalystPromptWithVersion(analyst, newPrompt, changeLog)
        }
    }

    fun restoreAnalystPrompt(analyst: com.example.data.local.CustomAnalystEntity, history: com.example.data.local.PromptHistoryEntity) {
        viewModelScope.launch {
            repository.restoreAnalystPrompt(analyst, history)
        }
    }

    // Real-time market tick simulator (runs every 3.5 seconds)
    init {
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(3500)
                repository.simulateMarketTick()
            }
        }
    }

    fun selectStock(symbol: String) {
        repository.selectStock(symbol)
    }

    fun toggleWatchlist(symbol: String) {
        viewModelScope.launch {
            repository.toggleWatchlist(symbol)
        }
    }

    fun addCustomStock(symbol: String, name: String, category: String, price: Double) {
        viewModelScope.launch {
            repository.addCustomStock(symbol, name, category, price)
        }
    }

    fun refreshMarketData() {
        viewModelScope.launch {
            repository.syncLiveMarketData()
        }
    }

    // --- AI Analyst Generation Actions ---

    fun runAfterHoursAnalyst(onComplete: (ResearchNoteEntity) -> Unit = {}) {
        val index = indices.value.firstOrNull() ?: return
        val topStocks = stocks.value
        val newsList = news.value

        viewModelScope.launch {
            _isAiGenerating.value = true
            _generationMessage.value = "盤後研究員正在深度彙整大盤指數、法人買賣超與板塊動向..."
            try {
                val report = aiService.generateAfterHoursReport(
                    index = index,
                    topStocks = topStocks,
                    totalInstitutional = topStocks.mapNotNull { it.chips?.totalInstitutional }.sum(),
                    newsList = newsList
                )
                val entity = ResearchNoteEntity(
                    notebookType = "AFTER_HOURS",
                    title = report.title,
                    targetSymbol = report.targetSymbol,
                    targetName = report.targetName,
                    summary = report.summary,
                    content = report.content,
                    rating = report.rating,
                    tags = report.tags,
                    dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date()),
                    author = "AI 盤後研究員"
                )
                val id = repository.saveResearchNote(entity)
                onComplete(entity.copy(id = id))
            } finally {
                _isAiGenerating.value = false
                _generationMessage.value = null
            }
        }
    }

    fun runChipsAnalyst(stock: StockQuote, onComplete: (ResearchNoteEntity) -> Unit = {}) {
        val chips = stock.chips ?: return
        viewModelScope.launch {
            _isAiGenerating.value = true
            _generationMessage.value = "籌碼選股分析師正在分析 ${stock.symbol} ${stock.name} 之法人買賣超與資券..."
            try {
                val report = aiService.generateChipsAnalysisReport(stock, chips)
                val entity = ResearchNoteEntity(
                    notebookType = "DAY_TRADING",
                    title = report.title,
                    targetSymbol = stock.symbol,
                    targetName = stock.name,
                    summary = report.summary,
                    content = report.content,
                    rating = report.rating,
                    tags = report.tags,
                    dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date()),
                    author = "AI 籌碼選股分析師"
                )
                val id = repository.saveResearchNote(entity)
                onComplete(entity.copy(id = id))
            } finally {
                _isAiGenerating.value = false
                _generationMessage.value = null
            }
        }
    }

    fun runDayTradingAdvisor(stock: StockQuote, onComplete: (ResearchNoteEntity) -> Unit = {}) {
        val fib = FibonacciCalculator.calculate(stock.kLineHistory)
        val techSignal = TechnicalCalculator.evaluateSignals(stock.kLineHistory)

        viewModelScope.launch {
            _isAiGenerating.value = true
            _generationMessage.value = "當沖策略顧問正在計算 5/10/20 均線進出、斐波那契目標與停損價..."
            try {
                val report = aiService.generateDayTradingPlan(stock, fib, techSignal)
                val entity = ResearchNoteEntity(
                    notebookType = "DAY_TRADING",
                    title = report.title,
                    targetSymbol = stock.symbol,
                    targetName = stock.name,
                    summary = report.summary,
                    content = report.content,
                    rating = report.rating,
                    entryPrice = report.entryPrice,
                    targetPrice = report.targetPrice,
                    stopLossPrice = report.stopLossPrice,
                    keyFibLevel = report.keyFibLevel,
                    tags = report.tags,
                    dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date()),
                    author = "AI 當沖策略顧問"
                )
                val id = repository.saveResearchNote(entity)
                onComplete(entity.copy(id = id))
            } finally {
                _isAiGenerating.value = false
                _generationMessage.value = null
            }
        }
    }

    fun runNewsRadar(onComplete: (ResearchNoteEntity) -> Unit = {}) {
        val newsList = news.value
        viewModelScope.launch {
            _isAiGenerating.value = true
            _generationMessage.value = "消息面雷達正在掃描即時國際財經、政策與台股重大催化劑..."
            try {
                val report = aiService.generateNewsRadarReport(newsList)
                val entity = ResearchNoteEntity(
                    notebookType = "AFTER_HOURS",
                    title = report.title,
                    targetSymbol = "NEWS",
                    targetName = "重大財經消息",
                    summary = report.summary,
                    content = report.content,
                    rating = report.rating,
                    tags = report.tags,
                    dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date()),
                    author = "AI 消息面雷達"
                )
                val id = repository.saveResearchNote(entity)
                onComplete(entity.copy(id = id))
            } finally {
                _isAiGenerating.value = false
                _generationMessage.value = null
            }
        }
    }

    fun runFundamentalAnalyst(stock: StockQuote, onComplete: (ResearchNoteEntity) -> Unit = {}) {
        viewModelScope.launch {
            _isAiGenerating.value = true
            _generationMessage.value = "個股研究員正在深度評估 ${stock.symbol} ${stock.name} 財報三率、ROE 與存股合理價..."
            try {
                val report = aiService.generateFundamentalReport(stock)
                val entity = ResearchNoteEntity(
                    notebookType = "FUNDAMENTAL",
                    title = report.title,
                    targetSymbol = stock.symbol,
                    targetName = stock.name,
                    summary = report.summary,
                    content = report.content,
                    rating = report.rating,
                    entryPrice = report.entryPrice,
                    targetPrice = report.targetPrice,
                    stopLossPrice = report.stopLossPrice,
                    tags = report.tags,
                    dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date()),
                    author = "AI 個股研究員"
                )
                val id = repository.saveResearchNote(entity)
                onComplete(entity.copy(id = id))
            } finally {
                _isAiGenerating.value = false
                _generationMessage.value = null
            }
        }
    }

    fun runDualTrackStrategyHub(stock: StockQuote, onComplete: (ResearchNoteEntity) -> Unit = {}) {
        val chips = stock.chips ?: return
        val fib = FibonacciCalculator.calculate(stock.kLineHistory)
        val techSignal = TechnicalCalculator.evaluateSignals(stock.kLineHistory)
        val newsList = news.value

        viewModelScope.launch {
            _isAiGenerating.value = true
            _generationMessage.value = "策略分析師正在運行雙軌決策中樞（軌道一存股價值 + 軌道二動態爆發三面向共振）..."
            try {
                val report = aiService.generateDualTrackStrategyReport(stock, chips, fib, techSignal, newsList)
                val entity = ResearchNoteEntity(
                    notebookType = "STRATEGY",
                    title = report.title,
                    targetSymbol = stock.symbol,
                    targetName = stock.name,
                    summary = report.summary,
                    content = report.content,
                    rating = report.rating,
                    entryPrice = report.entryPrice,
                    targetPrice = report.targetPrice,
                    stopLossPrice = report.stopLossPrice,
                    keyFibLevel = report.keyFibLevel,
                    tags = report.tags,
                    dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date()),
                    author = "AI 策略分析師 (雙軌中樞)"
                )
                val id = repository.saveResearchNote(entity)
                onComplete(entity.copy(id = id))
            } finally {
                _isAiGenerating.value = false
                _generationMessage.value = null
            }
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }

    fun resetBenchmarkNotes() {
        viewModelScope.launch {
            repository.resetBenchmarkResearchNotes()
        }
    }

    fun createCustomNote(type: String, title: String, symbol: String, summary: String, content: String) {
        viewModelScope.launch {
            val entity = ResearchNoteEntity(
                notebookType = type,
                title = title,
                targetSymbol = symbol.ifBlank { null },
                targetName = stocks.value.find { it.symbol == symbol }?.name,
                summary = summary,
                content = content,
                dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date()),
                author = "使用者自訂筆記"
            )
            repository.saveResearchNote(entity)
        }
    }

    fun addAlertRule(symbol: String, name: String, type: String, price: Double) {
        viewModelScope.launch {
            repository.addAlertRule(symbol, name, type, price)
        }
    }

    fun deleteAlertRule(rule: com.example.data.local.AlertRuleEntity) {
        viewModelScope.launch {
            repository.deleteAlertRule(rule)
        }
    }

    fun testTriggerAlert(stock: StockQuote) {
        val alert = IntradayAlert(
            stockSymbol = stock.symbol,
            stockName = stock.name,
            alertType = AlertType.BREAKOUT_RESISTANCE,
            message = "【${stock.name}】盤中突破關鍵壓力位 NT$ ${stock.highPrice}，短線帶量向上！",
            price = stock.currentPrice,
            isHighPriority = true
        )
        repository.triggerAlert(alert)
    }

    // --- Custom AI Analyst Workforce Management ---

    fun addCustomAnalyst(
        name: String,
        roleTitle: String,
        avatarIcon: String,
        themeColorHex: Long,
        specialization: String,
        systemPrompt: String,
        analysisStyle: String,
        notebookType: String
    ) {
        viewModelScope.launch {
            val entity = com.example.data.local.CustomAnalystEntity(
                name = name,
                roleTitle = roleTitle,
                avatarIcon = avatarIcon,
                themeColorHex = themeColorHex,
                specialization = specialization,
                systemPrompt = systemPrompt,
                analysisStyle = analysisStyle,
                notebookType = notebookType,
                isBuiltIn = false
            )
            repository.saveCustomAnalyst(entity)
        }
    }

    fun updateCustomAnalyst(analyst: com.example.data.local.CustomAnalystEntity) {
        viewModelScope.launch {
            repository.updateCustomAnalyst(analyst)
        }
    }

    fun deleteCustomAnalyst(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomAnalyst(id)
        }
    }

    fun resetDefaultAnalysts() {
        viewModelScope.launch {
            repository.resetDefaultAnalysts()
        }
    }

    // --- Full Universe AI Stock Screening & Recommendation Action ---

    fun runUniverseStockScan(
        analyst: com.example.data.local.CustomAnalystEntity,
        sectorFilter: String? = null
    ) {
        _selectedAnalystForScan.value = analyst
        viewModelScope.launch {
            _isScanningMarket.value = true
            _generationMessage.value = "【${analyst.name}】正在掃描全台股市場，進行多因子量化與多空共振評估..."
            try {
                delay(600) // Realistic UI scanning animation
                val recs = repository.scanAndRecommendStocks(analyst, sectorFilter)
                _aiRecommendations.value = recs
            } finally {
                _isScanningMarket.value = false
                _generationMessage.value = null
            }
        }
    }

    // --- Run Custom / Universal AI Analyst on a specific stock ---

    fun runCustomAnalystAnalysis(
        analyst: com.example.data.local.CustomAnalystEntity,
        stock: StockQuote,
        onComplete: (ResearchNoteEntity) -> Unit = {}
    ) {
        val chips = stock.chips ?: return
        val fib = FibonacciCalculator.calculate(stock.kLineHistory)
        val techSignal = TechnicalCalculator.getSignalSummary(stock.kLineHistory, stock.currentPrice)
        val newsList = news.value

        viewModelScope.launch {
            _isAiGenerating.value = true
            _generationMessage.value = "【${analyst.name}】正在依據客製化 Prompt 深度研判 ${stock.symbol} ${stock.name}..."
            try {
                val report = aiService.generateCustomAnalystReport(
                    analyst = analyst,
                    stock = stock,
                    chips = chips,
                    fib = fib,
                    techSignal = techSignal,
                    newsList = newsList
                )
                val entity = ResearchNoteEntity(
                    notebookType = analyst.notebookType,
                    title = report.title,
                    targetSymbol = stock.symbol,
                    targetName = stock.name,
                    summary = report.summary,
                    content = report.content,
                    rating = report.rating,
                    entryPrice = report.entryPrice,
                    targetPrice = report.targetPrice,
                    stopLossPrice = report.stopLossPrice,
                    keyFibLevel = report.keyFibLevel,
                    tags = report.tags,
                    dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date()),
                    author = analyst.name
                )
                val id = repository.saveResearchNote(entity)
                onComplete(entity.copy(id = id))
            } finally {
                _isAiGenerating.value = false
                _generationMessage.value = null
            }
        }
    }

    fun saveRecommendationAsNote(rec: AIStockRecommendation, onComplete: (ResearchNoteEntity) -> Unit = {}) {
        viewModelScope.launch {
            val entity = ResearchNoteEntity(
                notebookType = "STRATEGY",
                title = "【AI選股推薦】${rec.stockSymbol} ${rec.stockName} (${rec.recommendationType})",
                targetSymbol = rec.stockSymbol,
                targetName = rec.stockName,
                summary = rec.rationale,
                content = """
# 【全台股 AI 智慧選股推薦研報】${rec.stockSymbol} ${rec.stockName}
> **推薦分析師**：${rec.analystName}  
> **綜合推薦評分**：${rec.score} 分 / 100 分  
> **選股評級**：${rec.recommendationType}  
> **三面向共振**：${if (rec.threeWayResonance) "✅ 消息+籌碼+技術三面完全共振！" else "籌碼與技術多方強勢"}  

---

### 🎯 實戰交易進出場參數
- 📌 **建議進場點位**：NT$ ${rec.entryPrice}
- 🚀 **斐波那契擴展目標價 (TP)**：NT$ ${rec.targetPrice} (預期幅度 +${String.format("%.1f", (rec.targetPrice - rec.entryPrice) / rec.entryPrice * 100)}%)
- 🛑 **防守停損價 (SL)**：NT$ ${rec.stopLossPrice}
- ⚖️ **風險報酬比**：${rec.riskRewardRatio} : 1

---

### 📊 關鍵量化指標清單
- **產業族群**：${rec.category}
- **本益比 (PE)**：${rec.peRatio} 倍 | **殖利率**：${rec.yieldRate}%
- **法人動態**：三大法人淨超 ${rec.institutionalNet} 張，投信連續買超 ${rec.trustConsecutiveDays} 天 (${rec.chipsRating})
- **技術信號**：${rec.technicalSignal}
- **重要催化劑**：${rec.catalyst}

---

### 💡 AI 核心論點
${rec.rationale}
                """.trimIndent(),
                rating = rec.recommendationType,
                entryPrice = rec.entryPrice,
                targetPrice = rec.targetPrice,
                stopLossPrice = rec.stopLossPrice,
                keyFibLevel = "進場: ${rec.entryPrice} / 目標: ${rec.targetPrice}",
                tags = "AI選股推薦,${rec.analystName},${rec.stockName},${rec.category}",
                dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date()),
                author = rec.analystName
            )
            val id = repository.saveResearchNote(entity)
            onComplete(entity.copy(id = id))
        }
    }
}
