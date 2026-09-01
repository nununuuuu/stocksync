package com.example.data.repository

import android.content.Context
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.RemoteMarketDataSource
import com.example.domain.calculator.FibonacciCalculator
import com.example.domain.calculator.TechnicalCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin
import kotlin.random.Random

class StockRepository(
    private val db: AppDatabase,
    private val scope: CoroutineScope
) {
    private val noteDao = db.researchNoteDao()
    private val alertDao = db.alertDao()
    private val watchlistDao = db.watchlistDao()
    private val customAnalystDao = db.customAnalystDao()
    private val promptHistoryDao = db.promptHistoryDao()
    private val workflowExecutionDao = db.workflowExecutionDao()
    private val remoteDataSource = RemoteMarketDataSource()

    val toolEngine = com.example.domain.ai.AgentToolCallingEngine(
        stocksProvider = { _stocksState.value },
        indicesProvider = { _indicesState.value },
        newsProvider = { _newsState.value },
        noteDao = noteDao
    )

    val workflowEngine = com.example.domain.ai.MultiAgentWorkflowEngine(
        toolEngine = toolEngine,
        workflowDao = workflowExecutionDao
    )

    private val _stocksState = MutableStateFlow<List<StockQuote>>(emptyList())
    val stocksState: StateFlow<List<StockQuote>> = _stocksState.asStateFlow()

    private val _indicesState = MutableStateFlow<List<IndexQuote>>(emptyList())
    val indicesState: StateFlow<List<IndexQuote>> = _indicesState.asStateFlow()

    private val _newsState = MutableStateFlow<List<MarketNews>>(emptyList())
    val newsState: StateFlow<List<MarketNews>> = _newsState.asStateFlow()

    private val _selectedStockSymbol = MutableStateFlow("2330")
    val selectedStockSymbol: StateFlow<String> = _selectedStockSymbol.asStateFlow()

    private val _latestAlertEvent = MutableSharedFlow<IntradayAlert>(extraBufferCapacity = 50)
    val latestAlertEvent: SharedFlow<IntradayAlert> = _latestAlertEvent.asSharedFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow("今日 13:30 (即時收盤)")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    val allNotes: Flow<List<ResearchNoteEntity>> = noteDao.getAllNotes()
    val alertRecords: Flow<List<AlertRecordEntity>> = alertDao.getAllRecords()
    val alertRules: Flow<List<AlertRuleEntity>> = alertDao.getAllRules()
    val customAnalysts: Flow<List<CustomAnalystEntity>> = customAnalystDao.getAllAnalysts()
    val workflowExecutions: Flow<List<WorkflowExecutionRecordEntity>> = workflowExecutionDao.getAllExecutions()
    val workflowState = workflowEngine.executionState
    val workflowStepLogs = workflowEngine.stepLogs

    fun getPromptHistory(analystId: Long): Flow<List<PromptHistoryEntity>> {
        return promptHistoryDao.getHistoryForAnalyst(analystId)
    }

    suspend fun updateAnalystPromptWithVersion(analyst: CustomAnalystEntity, newPrompt: String, changeLog: String) {
        val nextVer = analyst.promptVersion + 1
        promptHistoryDao.insertHistory(
            PromptHistoryEntity(
                analystId = analyst.id,
                version = analyst.promptVersion,
                systemPrompt = analyst.systemPrompt,
                changeLog = "舊版本 v${analyst.promptVersion}"
            )
        )
        val updated = analyst.copy(systemPrompt = newPrompt, promptVersion = nextVer)
        customAnalystDao.updateAnalyst(updated)
    }

    suspend fun restoreAnalystPrompt(analyst: CustomAnalystEntity, history: PromptHistoryEntity) {
        val nextVer = analyst.promptVersion + 1
        promptHistoryDao.insertHistory(
            PromptHistoryEntity(
                analystId = analyst.id,
                version = analyst.promptVersion,
                systemPrompt = analyst.systemPrompt,
                changeLog = "還原前備份 v${analyst.promptVersion}"
            )
        )
        val updated = analyst.copy(systemPrompt = history.systemPrompt, promptVersion = nextVer)
        customAnalystDao.updateAnalyst(updated)
    }

    suspend fun runFullWorkflowPipeline(analysts: List<CustomAnalystEntity>, onProgress: (String) -> Unit): WorkflowExecutionRecordEntity? {
        return workflowEngine.runFullDepartmentPipeline(analysts, onProgress)
    }

    fun cancelWorkflowPipeline() {
        workflowEngine.cancelWorkflow()
    }

    init {
        initializeInitialData()
    }

    private fun initializeInitialData() {
        scope.launch(Dispatchers.IO) {
            val initialStocks = createDefaultStockQuotes()
            _stocksState.value = initialStocks

            _indicesState.value = listOf(
                IndexQuote("^TWII", "加權指數", 22858.60, 185.32, 0.82, 22910.50, 22720.10, 3865.4),
                IndexQuote("^TWOII", "櫃買指數", 268.45, 1.95, 0.73, 269.10, 266.80, 892.6),
                IndexQuote("^TWELEC", "電子類指數", 1215.80, 14.20, 1.18, 1220.40, 1205.10, 2650.8),
                IndexQuote("^TWSHIP", "航運類指數", 188.60, 2.80, 1.51, 189.50, 185.20, 320.5),
                IndexQuote("^TWFIN", "金融保險", 2045.20, -5.30, -0.26, 2056.00, 2040.10, 195.2)
            )

            _newsState.value = createDefaultNews()

            // Seed default analysts if empty
            if (customAnalystDao.getCount() == 0) {
                seedDefaultAnalysts()
            }

            // Seed initial notes if empty
            if (noteDao.getCount() == 0) {
                seedInitialNotes()
            }

            // Initial remote background sync with TWSE / TPEx / Yahoo Finance
            syncLiveMarketData()
        }
    }

    /**
     * Fetch and synchronize real-time market quotes and fundamentals from TWSE / TPEx OpenAPI & Yahoo Finance
     */
    suspend fun syncLiveMarketData() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        try {
            // 1. Sync Market Indices (^TWII 加權指數, ^TWOII 櫃買指數) from Yahoo Finance
            val currentIndices = _indicesState.value
            val updatedIndices = currentIndices.map { index ->
                if (index.symbol.startsWith("^")) {
                    remoteDataSource.fetchYahooIndex(index.symbol, index.name, index)
                } else {
                    index
                }
            }
            _indicesState.value = updatedIndices

            // 2. Fetch TWSE Fundamentals (BWIBBU_ALL) and TPEx Quotes
            val twseFundamentals = remoteDataSource.fetchTwseFundamentals()
            val tpexQuotes = remoteDataSource.fetchTpexQuotes()

            // 3. Sync tracked stocks from Yahoo Finance & OpenAPI
            val currentStocks = _stocksState.value
            val updatedStocks = currentStocks.map { stock ->
                var updated = stock

                // Fetch real-time price & K-line from Yahoo Finance
                val remoteQuote = remoteDataSource.fetchYahooChartData(
                    symbol = stock.symbol,
                    name = stock.name,
                    category = stock.category,
                    existingQuote = stock
                )
                if (remoteQuote != null) {
                    updated = remoteQuote
                }

                // Enrich with TWSE BWIBBU (PE / Yield / PB)
                val bwibbu = twseFundamentals[stock.symbol]
                if (bwibbu != null) {
                    val pe = bwibbu.peRatio?.toDoubleOrNull()
                    val yield = bwibbu.dividendYield?.toDoubleOrNull()
                    val pb = bwibbu.pbRatio?.toDoubleOrNull()
                    updated = updated.copy(
                        peRatio = if (pe != null && pe > 0) pe else updated.peRatio,
                        yieldRate = if (yield != null && yield > 0) yield else updated.yieldRate,
                        pbRatio = if (pb != null && pb > 0) pb else updated.pbRatio
                    )
                }

                // Enrich TPEx data if applicable
                val tpex = tpexQuotes[stock.symbol]
                if (tpex != null) {
                    val close = tpex.close?.toDoubleOrNull()
                    val change = tpex.change?.toDoubleOrNull()
                    if (close != null && close > 0) {
                        val prev = close - (change ?: 0.0)
                        val changePct = if (prev > 0) ((change ?: 0.0) / prev) * 100.0 else 0.0
                        updated = updated.copy(
                            currentPrice = close,
                            change = change ?: updated.change,
                            changePercent = changePct
                        )
                    }
                }
                updated
            }

            _stocksState.value = updatedStocks
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.TAIWAN).format(Date())
            _lastSyncTime.value = "今日 $timeStr (TWSE/TPEx/Yahoo同步)"
        } catch (e: Exception) {
            android.util.Log.e("StockRepository", "Sync failed: ${e.message}")
        } finally {
            _isSyncing.value = false
        }
    }

    fun selectStock(symbol: String) {
        _selectedStockSymbol.value = symbol
    }

    val selectedStock: StateFlow<StockQuote?> = combine(stocksState, selectedStockSymbol) { list, sym ->
        list.find { it.symbol == sym } ?: list.firstOrNull()
    }.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun saveResearchNote(note: ResearchNoteEntity): Long {
        return noteDao.insertNote(note)
    }

    suspend fun deleteNote(id: Long) {
        noteDao.deleteById(id)
    }

    suspend fun resetBenchmarkResearchNotes() {
        noteDao.clearAllNotes()
        seedInitialNotes()
    }

    suspend fun saveCustomAnalyst(analyst: CustomAnalystEntity): Long {
        return customAnalystDao.insertAnalyst(analyst)
    }

    suspend fun updateCustomAnalyst(analyst: CustomAnalystEntity) {
        customAnalystDao.updateAnalyst(analyst)
    }

    suspend fun deleteCustomAnalyst(id: Long) {
        customAnalystDao.deleteById(id)
    }

    suspend fun resetDefaultAnalysts() {
        val current = customAnalystDao.getCount()
        if (current > 0) {
            // Delete all and re-seed defaults
            seedDefaultAnalysts(forceReset = true)
        }
    }

    suspend fun addAlertRule(symbol: String, name: String, type: String, price: Double) {
        alertDao.insertRule(
            AlertRuleEntity(
                stockSymbol = symbol,
                stockName = name,
                alertType = type,
                targetPrice = price
            )
        )
    }

    suspend fun deleteAlertRule(rule: AlertRuleEntity) {
        alertDao.deleteRule(rule)
    }

    suspend fun toggleWatchlist(symbol: String) {
        val currentList = _stocksState.value
        val target = currentList.find { it.symbol == symbol } ?: return
        val newStatus = !target.isWatchlisted
        _stocksState.value = currentList.map {
            if (it.symbol == symbol) it.copy(isWatchlisted = newStatus) else it
        }
        if (newStatus) {
            watchlistDao.insertStock(WatchlistEntity(target.symbol, target.name, target.category))
        } else {
            watchlistDao.deleteBySymbol(symbol)
        }
    }

    /**
     * AI Stock Universe Recommendation & Screening Engine
     * Evaluates all stocks across the Taiwan universe based on analyst's custom prompt & specialization
     */
    fun scanAndRecommendStocks(
        analyst: CustomAnalystEntity,
        sectorFilter: String? = null
    ): List<AIStockRecommendation> {
        val allStocks = _stocksState.value
        val filteredList = if (!sectorFilter.isNullOrBlank() && sectorFilter != "全部族群") {
            allStocks.filter { it.category.contains(sectorFilter) || sectorFilter.contains(it.category) }
        } else {
            allStocks
        }

        val newsList = _newsState.value

        return filteredList.mapNotNull { stock ->
            val fib = FibonacciCalculator.calculate(stock.kLineHistory)
            val techSignal = TechnicalCalculator.getSignalSummary(stock.kLineHistory, stock.currentPrice)
            val chips = stock.chips ?: generateChipsData(stock.symbol)

            // Multi-factor quantitative scoring
            var score = 60

            // 1. Technical factors
            if (techSignal.isBullishMaAlignment) score += 12
            if (techSignal.isKdGoldenCross) score += 8
            if (techSignal.isMacdBullish) score += 6
            if (techSignal.isHeavyVolumeLongRed) score += 8
            if (stock.changePercent > 0) score += 4

            // 2. Chips factors
            if (chips.trustConsecutiveBuyDays >= 3) score += 10
            if (chips.foreignBuySell > 1000) score += 8
            if (chips.totalInstitutional > 1500) score += 8
            if (chips.marginChange < 0 && chips.shortChange > 0) score += 10 // 資減券增軋空

            // 3. Fundamental factors
            if (stock.peRatio < 18.0) score += 8
            if (stock.roe > 22.0) score += 7
            if (stock.yieldRate > 4.0) score += 8
            if (stock.revenueGrowthYoy > 20.0) score += 6

            // 4. News Catalyst
            val matchingNews = newsList.filter { it.relatedSymbols.contains(stock.symbol) }
            val hasNewsCatalyst = matchingNews.isNotEmpty()
            if (hasNewsCatalyst) score += 8

            // 5. Custom Analyst Style Bias
            val style = analyst.analysisStyle
            val spec = analyst.specialization
            if (style.contains("動能") || spec.contains("突破") || spec.contains("爆發")) {
                if (techSignal.isHeavyVolumeLongRed || stock.changePercent > 2.5) score += 10
            } else if (style.contains("價值") || style.contains("存股") || spec.contains("基本面")) {
                if (stock.peRatio < 16.0 && stock.yieldRate > 4.5) score += 12
            } else if (style.contains("籌碼") || spec.contains("法人") || spec.contains("外資")) {
                if (chips.trustConsecutiveBuyDays >= 4 || chips.totalInstitutional > 2500) score += 14
            } else if (style.contains("當沖") || spec.contains("5/10/20") || spec.contains("均線")) {
                if (stock.volume > 20000 && (techSignal.isKdGoldenCross || stock.changePercent > 1.5)) score += 10
            }

            // Cap score between 65 and 99
            val finalScore = minOf(99, maxOf(65, score))

            // 3-Way Resonance check: News + Chips + Technical
            val isChipsStrong = chips.trustConsecutiveBuyDays >= 2 || chips.totalInstitutional > 800
            val isTechStrong = techSignal.isBullishMaAlignment || techSignal.isKdGoldenCross || techSignal.isHeavyVolumeLongRed
            val threeWayResonance = hasNewsCatalyst && isChipsStrong && isTechStrong

            // Entry, target, and stop loss calculation via Fibonacci
            val entryPrice = if (stock.currentPrice > fib.level0_382 && fib.level0_382 > 0) fib.level0_382 else stock.currentPrice
            val targetPrice = if (fib.ext1_618 > stock.currentPrice) fib.ext1_618 else stock.currentPrice * 1.18
            val stopLossPrice = if (fib.level0_618 < stock.currentPrice && fib.level0_618 > 0) fib.level0_618 else stock.currentPrice * 0.93

            val risk = maxOf(1.0, entryPrice - stopLossPrice)
            val reward = maxOf(1.0, targetPrice - entryPrice)
            val rrRatio = Math.round((reward / risk) * 10.0) / 10.0

            val recType = when {
                threeWayResonance -> "🔥 強烈買進 (三面同向共振)"
                finalScore >= 90 -> "🚀 突破加碼 (多頭主升段)"
                finalScore >= 82 -> "📈 拉回布局 (斐波黃金支撐)"
                stock.yieldRate > 4.5 && stock.peRatio < 16 -> "💰 價值存股 (高殖利率低估)"
                else -> "⚡ 動能短線 (均線進出)"
            }

            val catalystText = if (matchingNews.isNotEmpty()) {
                matchingNews.first().title.take(35) + "..."
            } else {
                "${stock.category}族群資金匯聚，營收YoY ${String.format("%+.1f", stock.revenueGrowthYoy)}% 成長強勁"
            }

            val rationale = buildString {
                append("【${analyst.name}・評級 ${finalScore}分】")
                if (threeWayResonance) {
                    append("消息面題材、外資投信籌碼鎖碼、技術面均線KD形成完美三向共振！")
                } else if (techSignal.isBullishMaAlignment) {
                    append("日K線 5/10/20 均線呈多頭排列，技術面維持強勢上升軌道。")
                } else {
                    append("基本面 EPS ${stock.eps}元，PE ${stock.peRatio}倍，具備優異風險報酬比。")
                }
                append(" 投信連買 ${chips.trustConsecutiveBuyDays} 天，外資持股 ${chips.foreignHoldPercent}%。")
                append("建議回測 Fib 38.2% (NT$ ${String.format("%.1f", entryPrice)}) 建立部位，目標上看 Fib 161.8% (NT$ ${String.format("%.1f", targetPrice)})。")
            }

            AIStockRecommendation(
                stockSymbol = stock.symbol,
                stockName = stock.name,
                category = stock.category,
                currentPrice = stock.currentPrice,
                changePercent = stock.changePercent,
                analystId = analyst.id,
                analystName = analyst.name,
                score = finalScore,
                recommendationType = recType,
                rationale = rationale,
                threeWayResonance = threeWayResonance,
                entryPrice = Math.round(entryPrice * 10.0) / 10.0,
                targetPrice = Math.round(targetPrice * 10.0) / 10.0,
                stopLossPrice = Math.round(stopLossPrice * 10.0) / 10.0,
                riskRewardRatio = rrRatio,
                peRatio = stock.peRatio,
                yieldRate = stock.yieldRate,
                institutionalNet = chips.totalInstitutional,
                trustConsecutiveDays = chips.trustConsecutiveBuyDays,
                chipsRating = chips.chipRating,
                technicalSignal = techSignal.trendDescription,
                catalyst = catalystText
            )
        }.sortedByDescending { it.score }
    }

    suspend fun addCustomStock(symbol: String, name: String, category: String, price: Double) {
        val klines = generateKLineData(symbol, price, 60)
        val chips = generateChipsData(symbol)
        val newStock = StockQuote(
            symbol = symbol,
            name = name,
            category = category,
            currentPrice = price,
            openPrice = price * 0.995,
            highPrice = price * 1.015,
            lowPrice = price * 0.99,
            previousClose = price * 0.992,
            change = price - (price * 0.992),
            changePercent = (price - (price * 0.992)) / (price * 0.992) * 100,
            volume = 12500,
            totalAmount = price * 12500 / 10000.0,
            kLineHistory = klines,
            chips = chips,
            isWatchlisted = true
        )
        _stocksState.value = _stocksState.value + newStock
        watchlistDao.insertStock(WatchlistEntity(symbol, name, category))
        _selectedStockSymbol.value = symbol
    }

    /**
     * Trigger simulated intraday price tick & alert detection
     */
    fun simulateMarketTick() {
        val currentList = _stocksState.value
        val updated = currentList.map { stock ->
            val deltaPct = (Random.nextDouble() - 0.48) * 0.6 // Slight upward bias
            val newPrice = Math.round((stock.currentPrice * (1 + deltaPct / 100)) * 10.0) / 10.0
            val newChange = newPrice - stock.previousClose
            val newChangePct = (newChange / stock.previousClose) * 100
            val newHigh = maxOf(stock.highPrice, newPrice)
            val newLow = minOf(stock.lowPrice, newPrice)
            val newVol = stock.volume + Random.nextInt(10, 150)

            val updatedStock = stock.copy(
                currentPrice = newPrice,
                change = newChange,
                changePercent = newChangePct,
                highPrice = newHigh,
                lowPrice = newLow,
                volume = newVol
            )

            // Check alerts
            checkStockAlerts(updatedStock)

            updatedStock
        }
        _stocksState.value = updated
    }

    private fun checkStockAlerts(stock: StockQuote) {
        val fib = FibonacciCalculator.calculate(stock.kLineHistory)
        val nearestFib = FibonacciCalculator.findNearestFibLevel(stock.currentPrice, fib, 0.4)

        if (nearestFib != null && Random.nextInt(100) < 5) {
            val alert = IntradayAlert(
                stockSymbol = stock.symbol,
                stockName = stock.name,
                alertType = AlertType.FIBONACCI_KEY_LEVEL,
                message = "【${stock.name}】觸及 $nearestFib，當前報價 NT$ ${stock.currentPrice}",
                price = stock.currentPrice,
                isHighPriority = true
            )
            triggerAlert(alert)
        }

        if (stock.changePercent >= 4.5 && stock.volume > 25000 && Random.nextInt(100) < 6) {
            val alert = IntradayAlert(
                stockSymbol = stock.symbol,
                stockName = stock.name,
                alertType = AlertType.HEAVY_VOLUME_SURGE,
                message = "【${stock.name}】帶量長紅暴漲 ${String.format("%+.2f", stock.changePercent)}%，成交量暴增至 ${stock.volume} 張！",
                price = stock.currentPrice,
                isHighPriority = true
            )
            triggerAlert(alert)
        }
    }

    fun triggerAlert(alert: IntradayAlert) {
        scope.launch(Dispatchers.IO) {
            _latestAlertEvent.emit(alert)
            alertDao.insertRecord(
                AlertRecordEntity(
                    stockSymbol = alert.stockSymbol,
                    stockName = alert.stockName,
                    alertType = alert.alertType.label,
                    message = alert.message,
                    price = alert.price,
                    timestamp = alert.timestamp
                )
            )
        }
    }

    private fun createDefaultStockQuotes(): List<StockQuote> {
        val defs = listOf(
            // 半導體與晶圓製造
            Triple("2330", "台積電", "半導體") to 1045.0,
            Triple("2303", "聯電", "半導體") to 52.5,
            Triple("3711", "日月光投控", "半導體封測") to 162.0,
            Triple("2449", "京元電子", "半導體測試") to 128.5,
            Triple("6770", "力積電", "半導體") to 22.8,

            // IC設計與ASIC/IP
            Triple("2454", "聯發科", "IC設計") to 1320.0,
            Triple("3661", "世芯-KY", "ASIC矽智財") to 2890.0,
            Triple("3443", "創意", "ASIC矽智財") to 1420.0,
            Triple("3034", "聯詠", "驅動IC設計") to 540.0,
            Triple("2379", "瑞昱", "網通音訊IC") to 510.0,
            Triple("8299", "群聯", "快閃記憶體控制") to 560.0,
            Triple("3035", "智原", "ASIC矽智財") to 295.0,

            // AI伺服器/ODM/品牌電子
            Triple("2317", "鴻海", "AI伺服器/代工") to 218.5,
            Triple("2382", "廣達", "AI伺服器") to 312.0,
            Triple("3231", "緯創", "AI伺服器") to 118.0,
            Triple("6669", "緯穎", "AI雲端伺服器") to 2350.0,
            Triple("2356", "英業達", "AI伺服器") to 54.2,
            Triple("2376", "技嘉", "AI主機板顯卡") to 285.0,
            Triple("2357", "華碩", "AI PC/主機板") to 560.0,
            Triple("2301", "光寶科", "電源/伺服器機櫃") to 112.5,

            // AI水冷散熱與機構件
            Triple("3017", "奇鋐", "AI水冷散熱") to 675.0,
            Triple("3324", "雙鴻", "AI水冷散熱") to 735.0,
            Triple("2308", "台達電", "綠能電源/散熱") to 415.0,
            Triple("3653", "健策", "均熱片/散熱") to 1280.0,
            Triple("2421", "建準", "伺服器散熱風扇") to 128.0,

            // 網通光通訊與PCB/ABF載板
            Triple("2345", "智邦", "網通/交換器") to 580.0,
            Triple("3037", "欣興", "ABF載板") to 185.5,
            Triple("6274", "台燿", "高頻銅箔基板") to 178.0,
            Triple("2368", "金像電", "伺服器PCB") to 232.0,
            Triple("8069", "元太", "電子紙/物聯網") to 270.0,

            // 重電綠能與儲能政策題材
            Triple("1519", "華城", "重電/變壓器") to 680.0,
            Triple("1503", "士電", "重電/綠能") to 235.0,
            Triple("1513", "中興電", "重電/GIS開關") to 182.0,
            Triple("1514", "亞力", "重電/配電盤") to 132.0,
            Triple("6806", "森崴能源", "綠能風電") to 145.0,

            // 航運海運與航空物流
            Triple("2603", "長榮", "航運海運") to 215.0,
            Triple("2609", "陽明", "航運海運") to 72.5,
            Triple("2615", "萬海", "航運海運") to 88.0,
            Triple("2618", "長榮航", "航空物流") to 37.8,
            Triple("2610", "華航", "航空客貨運") to 23.5,

            // 金融金控 (高殖利率價值)
            Triple("2881", "富邦金", "金融金控") to 92.4,
            Triple("2882", "國泰金", "金融金控") to 68.5,
            Triple("2891", "中信金", "金融金控") to 38.2,
            Triple("2886", "兆豐金", "官股金控") to 40.5,
            Triple("2884", "玉山金", "金融金控") to 29.8,

            // 生技醫療與特化
            Triple("6446", "藥華藥", "生技新藥") to 630.0,
            Triple("1795", "美時", "生技製藥") to 290.0,
            Triple("6472", "保瑞", "生技CDMO") to 760.0,

            // 傳產龍頭
            Triple("2002", "中鋼", "鋼鐵傳產") to 22.8,
            Triple("1605", "華新", "電線電纜") to 34.2,

            // 核心與高股息 ETF
            Triple("0050", "元大台灣50", "ETF權值") to 198.5,
            Triple("0056", "元大高股息", "高股息ETF") to 38.5,
            Triple("00878", "國泰永續高股息", "高股息ETF") to 23.15,
            Triple("00919", "群益台灣精選高息", "高股息ETF") to 24.20,
            Triple("00929", "復華台灣科技優息", "科技高息ETF") to 19.85,
            Triple("00940", "元大台灣價值高息", "月配息ETF") to 9.65
        )

        return defs.map { (info, basePrice) ->
            val (sym, name, cat) = info
            val prevClose = basePrice * (1 - (Random.nextDouble() - 0.4) * 0.03)
            val change = basePrice - prevClose
            val changePct = (change / prevClose) * 100
            val klines = generateKLineData(sym, basePrice, 60)
            val chips = generateChipsData(sym)

            StockQuote(
                symbol = sym,
                name = name,
                category = cat,
                currentPrice = Math.round(basePrice * 10.0) / 10.0,
                openPrice = Math.round((basePrice * 0.995) * 10.0) / 10.0,
                highPrice = Math.round((basePrice * 1.02) * 10.0) / 10.0,
                lowPrice = Math.round((basePrice * 0.99) * 10.0) / 10.0,
                previousClose = Math.round(prevClose * 10.0) / 10.0,
                change = Math.round(change * 10.0) / 10.0,
                changePercent = Math.round(changePct * 100.0) / 100.0,
                volume = Random.nextLong(8000, 85000),
                totalAmount = basePrice * Random.nextInt(150, 450) / 10.0,
                peRatio = Math.round(Random.nextDouble(12.0, 32.0) * 10.0) / 10.0,
                yieldRate = Math.round(Random.nextDouble(2.2, 8.5) * 10.0) / 10.0,
                pbRatio = Math.round(Random.nextDouble(1.2, 5.2) * 10.0) / 10.0,
                roe = Math.round(Random.nextDouble(12.0, 36.0) * 10.0) / 10.0,
                grossMargin = Math.round(Random.nextDouble(18.0, 58.0) * 10.0) / 10.0,
                operatingMargin = Math.round(Random.nextDouble(10.0, 44.0) * 10.0) / 10.0,
                netMargin = Math.round(Random.nextDouble(8.0, 38.0) * 10.0) / 10.0,
                eps = Math.round(Random.nextDouble(3.0, 52.0) * 10.0) / 10.0,
                revenueGrowthMom = Math.round(Random.nextDouble(-3.0, 15.0) * 10.0) / 10.0,
                revenueGrowthYoy = Math.round(Random.nextDouble(8.0, 48.0) * 10.0) / 10.0,
                isWatchlisted = true,
                kLineHistory = klines,
                chips = chips
            )
        }
    }

    private fun generateKLineData(symbol: String, basePrice: Double, days: Int): List<KLinePoint> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val list = mutableListOf<KLinePoint>()
        var price = basePrice * 0.82
        val dateFormat = SimpleDateFormat("MM/dd", Locale.TAIWAN)

        for (i in 0 until days) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            // Skip weekends
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) continue

            val wave = sin(i * 0.2) * (basePrice * 0.02)
            val noise = (Random.nextDouble() - 0.45) * (basePrice * 0.035)
            val open = price
            price = maxOf(10.0, open + wave + noise)
            val high = maxOf(open, price) + Random.nextDouble(0.0, basePrice * 0.015)
            val low = minOf(open, price) - Random.nextDouble(0.0, basePrice * 0.015)
            val vol = Random.nextLong(5000, 65000)

            list.add(
                KLinePoint(
                    timestamp = cal.timeInMillis,
                    dateStr = dateFormat.format(cal.time),
                    open = Math.round(open * 10.0) / 10.0,
                    high = Math.round(high * 10.0) / 10.0,
                    low = Math.round(low * 10.0) / 10.0,
                    close = Math.round(price * 10.0) / 10.0,
                    volume = vol
                )
            )
        }

        // Align the last price closely to basePrice
        if (list.isNotEmpty()) {
            val last = list.last()
            list[list.size - 1] = last.copy(
                close = basePrice,
                high = maxOf(last.high, basePrice * 1.01),
                low = minOf(last.low, basePrice * 0.99)
            )
        }

        return TechnicalCalculator.enrichIndicators(list)
    }

    private fun generateChipsData(symbol: String): InstitutionalChips {
        val foreign = Random.nextLong(-2500, 8500)
        val trust = Random.nextLong(200, 3800)
        val dealer = Random.nextLong(-800, 1500)
        val trustDays = Random.nextInt(2, 12)
        val marginBal = Random.nextLong(12000, 48000)
        val marginChg = Random.nextLong(-1200, 1800)
        val shortBal = Random.nextLong(1500, 12000)
        val shortChg = Random.nextLong(-300, 950)
        val ratio = (shortBal.toDouble() / marginBal.toDouble()) * 100.0

        val rating = when {
            trustDays >= 5 && foreign > 2000 -> "外資投信雙認養 (主力極度偏多)"
            trustDays >= 3 -> "投信連續買超鎖碼 (作帳行情)"
            marginChg < 0 && shortChg > 0 -> "資減券增 (強烈軋空蓄勢)"
            foreign < -1500 -> "外資調節賣壓重"
            else -> "法人溫和換手"
        }

        return InstitutionalChips(
            symbol = symbol,
            foreignBuySell = foreign,
            trustBuySell = trust,
            dealerBuySell = dealer,
            foreignHoldPercent = Math.round(Random.nextDouble(25.0, 78.0) * 10.0) / 10.0,
            trustConsecutiveBuyDays = trustDays,
            marginBalance = marginBal,
            marginChange = marginChg,
            shortBalance = shortBal,
            shortChange = shortChg,
            marginShortRatio = Math.round(ratio * 100.0) / 100.0,
            chipRating = rating
        )
    }

    private fun createDefaultNews(): List<MarketNews> {
        val now = System.currentTimeMillis()
        return listOf(
            MarketNews(
                id = "n1",
                title = "輝達次世代 AI 晶片全面放量，台積電 3nm 與 CoWoS 產能滿載至 2026",
                summary = "國際大廠擴大下單，台積電先進封裝擴產速度超乎預期，外資重申加碼並上調目標價。",
                source = "經濟日報",
                category = "科技/財報",
                timestamp = now - 15 * 60 * 1000,
                timeAgo = "15分鐘前",
                sentiment = "重大利多",
                impactRating = "重大利多 (+5)",
                relatedSymbols = listOf("2330", "2382", "3037", "3661")
            ),
            MarketNews(
                id = "n2",
                title = "鴻海 GB200 伺服器第四季迎出貨高峰，毛利率可望逐季攀升",
                summary = "鴻海董事長表示 AI 伺服器需求強勁，北美大客戶大型資料中心訂單能見度已達明年上半年。",
                source = "工商時報",
                category = "科技/財報",
                timestamp = now - 45 * 60 * 1000,
                timeAgo = "45分鐘前",
                sentiment = "利多",
                impactRating = "利多 (+4)",
                relatedSymbols = listOf("2317", "2382", "3231", "3017")
            ),
            MarketNews(
                id = "n3",
                title = "美聯儲釋出降息寬鬆訊號，外資熱錢湧入亞洲，新台幣升值台股多頭續攻",
                summary = "FOMC 會議紀錄顯示通膨可控，降息循環啟動有助於高成長科技股與高殖利率標的估值修復。",
                source = "路透社",
                category = "國際美股",
                timestamp = now - 120 * 60 * 1000,
                timeAgo = "2小時前",
                sentiment = "利多",
                impactRating = "宏觀偏多 (+3)",
                relatedSymbols = listOf("0050", "2881", "2330")
            ),
            MarketNews(
                id = "n4",
                title = "紅海地緣局勢再度緊繃，貨櫃運價指數 SCFI 連三漲，長榮第四季獲利續看旺",
                summary = "航運繞道好望角常態化，船舶供給持續吃緊，海運業者表示長約換約價優於預期。",
                source = "中央社",
                category = "政策法說",
                timestamp = now - 240 * 60 * 1000,
                timeAgo = "4小時前",
                sentiment = "利多",
                impactRating = "波段題材 (+3)",
                relatedSymbols = listOf("2603")
            )
        )
    }

    private suspend fun seedInitialNotes() {
        val todayStr = "2026/08/24"

        // 1. 盤後研究筆記範本 1: 消息面雷達
        val note1 = ResearchNoteEntity(
            notebookType = "AFTER_HOURS",
            title = "2026/08/24 消息面驗證｜成熟製程供不應求撐聯電、SCFI連4漲撐航運、記憶體週賣後分歧",
            targetSymbol = "NEWS",
            targetName = "消息面驗證",
            summary = "聯電與航運「籌碼＋消息」雙強最可信；力積電與記憶體「週賣/日買、價漲法人賣」分歧屬獲利了結；仁寶缺近7天強催化，屬擴散而非主軸。大盤量縮破季線，短線由輝達08/26財報定調。",
            content = """
# 消息面雷達 · 2026/08/24 盤後驗證 · NEWS VERIFICATION
## 2026/08/24 消息面驗證｜成熟製程供不應求撐聯電、SCFI連4漲撐航運、記憶體週賣後分歧

> **核心摘要**：承接籌碼選股 08/24 與盤後重挫 08/24，以 7 天時效窗（tbs:qdr:w）逐則核實發布日期，交叉即時報價（13:30）與 SCFI/財報消息，判斷籌碼是否「有消息撐」。

---

### 📊 關鍵數據快報
- **2303 聯電**：NT$ 123.5 (+6.01%) ｜ 投信 +14,763 張＋融資減肥；消息：稼動率 85%→90% + 報價漲勢延續至 2027
- **SCFI 最新運價**：3,409 點 ｜ 連 4 漲 (+1.62%)，美西 / 美東續強、歐線回跌
- **航運近 1 月表現**：萬海 +43%、陽明 +26%、長榮 +24% ｜ Q2 獲利 V 轉
- **記憶體週賣超**：力積電 -19.5 萬張 ｜ 三大法人週賣，08/24 日內急回補分歧

---

### 💡 一句話結論
> **聯電與航運「籌碼＋消息」雙強最可信；力積電與記憶體「週賣/日買、價漲法人賣」分歧屬獲利了結；仁寶缺近 7 天強催化，屬擴散而非主軸。** 大盤量縮破季線，短線由 **輝達 08/26 財報** 定調，量能需回 7,500 億以上才有續航。

---

### 一、第一梯驗證：籌碼轉佳是否「有消息撐」

#### 1. 【★ 強支撐 · 雙驗證】2303 聯電 — 成熟製程供不應求硬消息 (NT$ 123.5, +6.01%, 量 19.3 萬張)
- **消息面**：經濟日報 08/24 指出 AI 伺服器與先進封裝占用 8 吋 / 12 吋成熟製程，供需收緊、**報價漲勢可望延續至 2027**。
- **營運面**：Q2 稼動率 85% → 毛利率 32.5% (+2.3pp)，Q3 上看 90% 以上、晶圓出貨季增高個位數，資本支出上調至 20 億美元。
- **籌碼呼應**：投信 +14,763 張爆量（前日僅 +505 張）＋ 三大法人同買 19,101 張 ＋ 融資 -2,453 張減肥 ＝ 法人認養非散戶追。
- **盤勢意義**：8/24 一度觸 128 元漲停、成交值市場第 2，資金避險由高位 AI 轉進低位成熟製程之「外溢行情」。

#### 2. 【分歧 · 需確認續航】6770 力積電 — 題材強但週線遭提款 (NT$ 70.8, +5.2%, 量 16 萬張)
- **題材面**：DRAM 報價調升 ＋ AI/HPC 帶動 ＋ 3D AI Foundry/WoW ＋ Intel EMIB 先進封裝認證，7 月營收年增逾七成、連數月站三年高檔。
- **矛盾點**：本週三大法人賣超 19.5 萬張（與旺宏合計 24 萬張 / 195 億），與 08/24 單日外資 +38,095 張 ＋ 融券軋空 1,945→45 張急回補形成**週賣日買分歧**。
- **解讀**：屬跌深籌碼反轉＋軋空，非趨勢翻多確認；需觀察外資是否連二日回補、月營收是否續站高檔。

#### 3. 【弱支撐 · 擴散性買盤】2324 仁寶 — 缺近 7 天強催化 (NT$ 41.6, 量 13.5 萬張)
- **近 7 天搜尋**：無主流媒體強催化（僅論壇 AI 伺服器占比討論），法人近 5 日合計 -1,863 張，外資單日 +28,426 張翻多缺乏硬新聞對應。
- **籌碼定位**：與聯電同屬「低位電子」獲投信點火擴散，受惠 AI 伺服器組裝但非製程漲價主軸。
- **操作建言**：消息落後籌碼，宜視為跟漲擴散，追價力道弱於聯電，需等法說或投信連二日加碼確認。

#### 4. 【強支撐 · 財報＋運價雙驗證】2603 長榮 — 航運內最乾淨 (NT$ 250.5, +1.83%, 三法人同買)
- **運價面**：SCFI 08/21 連 4 漲至 3,409.63 點 (+1.62%)，美西 6,765 / 美東 9,700 續強，僅歐 / 地中海回跌。
- **財報面**：Q1→Q2 V 轉：長榮 Q2 EPS 7.41（季增 93%）、陽明 1.64（年增 485%）、萬海 4.11（年增 972%），三雄全創今年新高。
- **籌碼面**：外資 +9,343 / 投信 +12 / 自營 +451 ＝ 三法人同買唯一且融資 -497 減肥，對比陽明/萬海自營調節＋融券暴增更乾淨。

---

### 二、第三梯驗證：記憶體轉弱是「報價反轉」還是「獲利了結」？
- **2408 南亞科 (501元, -3.09%)**：外資 -11,488 張翻空，投信 +887 杯水車薪。消息面 DRAM 合約價季增 10-15%，現貨持平未反轉，屬上週大漲後的籌碼獲利了結與輝達財報前避險。
- **2344 華邦電 (177元, +0.28%)**：外資 -4,145 張翻空，價漲法人賣形成背離，分歧在籌碼而非報價。
- **2317 鴻海 / 2330 台積電**：外資提款權值屬輝達 08/26 財報前的防禦性調節，無個股特定利空。
> **關鍵判斷**：記憶體不是報價反轉，是「漲多後的籌碼獲利了結 ＋ 財報前避險」，需等外資賣壓收斂至千張內 ＋ 融資續減肥才算止跌。

---

### 三、航運爆量拆解：為何陽明 / 萬海最噴、長榮最穩？
- **SCFI 結構決定彈性**：長榮長程線＋船隊規模大，獲利彈性佳；陽明長程佔 76% 彈性最大，故融資融券同暴增；萬海亞洲線佔 55%，Q2 獲利年增 972% 基期低故月漲 43% 最猛。
- **籌碼過熱三訊號**：外資連三買但遞減、自營商開始調節、融資融券同暴增。結論：**有基本面（獲利V轉＋SCFI連漲）但短線量價透支，屬「有題材的過熱」**，不宜追高。

---

### 四、大盤消息面：為何量縮破 45,000？
| 情境 | 消息面驅動 | 行情佐證 | 對盤勢影響 |
|---|---|---|---|
| **成熟製程外溢** | AI 先進製程滿載外溢至成熟製程，供需收緊至 2027 | 聯電 123.5 爆量 19.3 萬、力積電 70.8 爆量 16 萬 | 資金避險新去處，少數個股受惠 |
| **航運避險** | SCFI 連 4 漲 ＋ Q2 獲利 V 轉，8 月漲幅 3.5 倍於前 7 月 | 陽明 64.5、萬海 124.5、長榮 250.5 全創新高 | 吸金但透支動能，追價風險升高 |
| **記憶體調節** | 合約價仍揚但法人一週倒貨 24 萬張 | 南亞科 501 (-3.09%)、華邦電 177 背離 | 權值與記憶體拖累指數 |
| **財報前觀望** | 輝達 8/26 財報 ＋ PCE 通膨數據 | 量縮 6,294 億創低、台積電 2375、鴻海 243.5 | 量縮盤整，財報前防禦為宜 |

---

### 五、交叉驗證總表：籌碼 vs 消息面
| 梯隊 | 個股 | 籌碼結論 | 消息面 (7天內) | 一致性 | 操作意涵 |
|---|---|---|---|---|---|
| **第一梯 轉佳** | **2303 聯電** | 三大同買＋投信爆買＋融資減肥 最乾淨 | 供不應求＋稼動 90%＋漲價至 2027 硬消息 | **高度一致 ✓** | 首選，拉回量縮承接 |
| **第一梯** | **6770 力積電** | 外資爆買＋軋空 97% 翻多最猛 | DRAM 漲價＋7月營收年增 7 成，但週賣 19.5 萬張 | **分歧 △** | 需確認連二日回補，否則視軋空反彈 |
| **第一梯** | **2324 仁寶** | 外資翻多＋投信回頭 | 近 7 天無強催化，僅論壇 AI 伺服器臆測 | **消息落後 △** | 擴散跟漲，不追高 |
| **第一梯** | **2603 長榮** | 三法人同買＋融資減肥 航運最穩 | SCFI 連 4 漲 ＋ Q2 EPS 7.41 V 轉 | **高度一致 ✓** | 航運內首選，補漲抗跌 |
| **第二梯 過熱** | **2609/2615** | 外資遞減＋自營調節＋融資券暴增 | SCFI 撐但爆量 8~16 萬張透支 | **有題材但過熱 ⚠️** | 不追高，設移動停利 |
| **第三梯 轉弱** | **2408/2344** | 外資大賣＋價漲背離 | 合約價仍揚 10-15%，現貨持平未反轉 | **籌碼先行 ✕** | 避開，等賣壓收斂 |
| **第三梯** | **2317/2330** | 外資連賣 / 翻空 | 輝達財報前觀望，無個股利空 | **事件驅動 ✕** | 等財報後再評估 |

---

### 六、明日 08/25 觀察重點
1. **輝達 8/26 財報**：AI 權值（台積電、鴻海、聯發科、緯穎、台光電）波動放大，財報前不宜重押權值。
2. **聯電續航**：投信是否連二日買 ＋ 稼動率 / 漲價消息是否延燒至世界先進等成熟製程。
3. **航運量價**：是否爆量不漲 / 長上影 / 自營續調節 / 融券再增 ＝ 過熱三訊號齊發即短線高點。
4. **記憶體止跌**：外資賣超收斂至千張內 ＋ 融資續減肥 ＋ 不再破底。
5. **外資方向**：是否連二賣且賣超仍鎖定權值/記憶體 ＝ 提款未完；若翻買則單日調節結束。
            """.trimIndent(),
            rating = "聯電・航運 強支撐 / 記憶體分歧",
            entryPrice = 120.0,
            targetPrice = 139.0,
            stopLossPrice = 115.5,
            keyFibLevel = "回撤50%: 110.7 / 擴展161.8%: 139.0",
            tags = "消息面驗證,成熟製程,SCFI運價,記憶體分歧,輝達財報",
            author = "消息面雷達",
            dateStr = todayStr,
            isPinned = true
        )

        // 2. 盤後研究筆記範本 2: 籌碼選股分析師
        val note2 = ResearchNoteEntity(
            notebookType = "AFTER_HOURS",
            title = "2026/08/24 籌碼選股分析｜外資一日翻空提款、投信逆勢加碼 聯電＆力積電籌碼最強",
            targetSymbol = "CHIPS",
            targetName = "法人籌碼選股",
            summary = "外資提款鎖定權值＋記憶體，投信逆勢點火低位電子。籌碼最強是 2303 聯電（三法人同步大買+融資減肥）與 6770 力積電（軋空97%）；航運短線過熱；記憶體轉弱宜避。",
            content = """
# 籌碼選股 · 2026/08/24 盤後 · CHIPS ANALYSIS
## 2026/08/24 籌碼選股分析｜外資一日翻空提款、投信逆勢加碼 聯電＆力積電籌碼最強

> **核心摘要**：加權 -461 點失守季線、三大法人由 +331 億翻至 -163 億（外資 -157.5 億領賣）· 本篇逐檔拆解法人買賣超與融資融券，篩出籌碼轉佳／轉弱標的。

---

### 📊 法人籌碼快速總覽
- **外資買賣超**：-157.5 億元 (08/21 +283.0 億 → 一日翻空 440 億)
- **投信買賣超**：+37.05 億元 (唯一站買方 · 連二日擴大)
- **外資買超王**：6770 力積電 (+38,095 張 / 單日第一)
- **投信買超王**：2303 聯電 (+14,763 張 / 單日爆量)

---

### 💡 一句話結論
> **外資提款鎖定權值＋記憶體，投信逆勢點火低位電子。** 籌碼最強是 **2303 聯電（三大法人同步 +19,101 張、投信狂買 14,763 張＋融資減肥）** 與 **6770 力積電（外資 +38,095 張＋融券軋空 1,945→45 張）**；**航運續強但自營調節＋融券暴增、短線過熱**；**2408 南亞科／2344 華邦電／2317 鴻海** 遭外資提款、籌碼轉弱宜避。

---

### 一、大盤籌碼總覽 · 一日由大買轉大賣 495 億
| 法人 | 08/24 買賣超 | 08/21 對比 | 方向變化 | 解讀 |
|---|---|---|---|---|
| **外資及陸資** | **-157.51 億** | +283.05 億 | 買→大賣 | 一日翻空 440 億，提款最重 |
| **投信** | **+37.05 億** | +21.01 億 | 續買擴大 | 唯一站買方，聚焦低位電子與載板 |
| **自營商** | **-43.32 億** | +29.23 億 | 買→賣 | 自行＋避險同步轉賣 |
| **合計** | **-163.78 億** | **+331.06 億** | **由買轉賣 495 億** | 搭配量縮至 6,294 億＝追價缺席 |

---

### 二、法人買賣超焦點排行（08/24）
| 代號名稱 | 外資買賣超 (股) | 投信買賣超 (股) | 自營買賣超 (股) | 三大法人合計 (股) | 股價表現 |
|---|---|---|---|---|---|
| **6770 力積電** | **+38,095,597** | 0 | +2,008,109 | **+40,103,706** | 外資爆量翻多第 1 |
| **2609 陽明** | **+30,651,418** | +27,000 | -1,473,541 | **+29,204,877** | +6.97% 64.5 爆量 16.8 萬張 |
| **2324 仁寶** | **+28,426,511** | +958,000 | +87,480 | **+29,471,991** | 低位電子接棒 |
| **2303 聯電** | **+2,257,407** | **+14,763,022** | +2,080,891 | **+19,101,320** | +6.01% 123.5 投信爆買 |
| **2603 長榮** | **+9,343,268** | +12,801 | +451,307 | **+9,807,376** | +1.83% 250.5 法人最穩 |
| **2615 萬海** | **+11,518,471** | +12,000 | -481,769 | **+11,048,702** | +9.69% 124.5 爆量 8.8 萬張 |
| **2408 南亞科** | **-11,488,857** | +887,791 | -548,541 | **-11,149,607** | -3.09% 501 外資提款 |
| **2317 鴻海** | **-7,545,249** | +20,390 | -108,077 | **-7,632,936** | -1.22% 243.5 連三賣擴大 |
| **2330 台積電** | **-1,620,725** | +157,389 | -23,431 | **-1,486,767** | -1.45% 2,375 權值拖累 |
| **2344 華邦電** | **-4,145,723** | +17,432 | -662,832 | **-4,791,123** | +0.28% 177 價漲法人賣背離 |

---

### 三、精選個股籌碼診斷

#### 1. 籌碼轉佳 — 可續追蹤
- **【首選 · 最乾淨】2303 聯電 (+19,101 張)**：外資 +2,257 / 投信 +14,763 / 自營 +2,080。三大法人同買，融資減肥 -2,453 張，法人認養續航機率高。
- **【軋空翻多】6770 力積電 (+40,103 張)**：外資一日大買 38,095 張，融券單日減 1,900 張（軋空 97%），融資不增，籌碼由空轉多乾淨。
- **【低位接棒】2324 仁寶 (+29,471 張)**：外資一日翻多 28,426 張，投信回頭買 958 張，低位階獲買盤青睞。
- **【航運最穩】2603 長榮 (+9,807 張)**：三法人同買，融資 -497 減肥，落後補漲＋法人認養。

#### 2. 籌碼過熱 — 陽明 / 萬海
- 外資連三買但逐日遞減，自營商逆勢調節，融資融券同暴增，短線爆量透支動能，不宜追高。

#### 3. 籌碼轉弱 — 避雷或等止跌
- **2408 南亞科**：外資單日大賣 -11,488 張，提款第一線。
- **2344 華邦電**：外資 -4,145 張，價微漲但法人賣，籌碼背離。
- **2317 鴻海**：外資 -7,545 張，連三日賣超擴大。
- **2330 台積電**：外資 -1,620 張翻空，貢獻大盤 -250 點。

---

### 四、融資融券與散戶動態
| 個股 | 融資餘額 | 增減 | 融券餘額 | 增減 | 籌碼解讀 |
|---|---|---|---|---|---|
| **2609 陽明** | 37,454 張 | **+3,828 張** | 2,776 張 | **+897 張** | 資券同暴增＝散戶追價＋空單並存，過熱 |
| **2615 萬海** | 17,159 張 | +641 張 | 2,823 張 | **+1,541 張** | 融券暴增 120%＝軋空與反手空並存 |
| **2303 聯電** | 152,200 張 | **-2,453 張** | 2,499 張 | -353 張 | 融資減肥＝法人接走非散戶追 |
| **6770 力積電** | 143,986 張 | -760 張 | 45 張 | **-1,900 張** | 融券軋空 97%＝空單回補完畢 |

> **籌碼心法**：融資減肥＋法人大買＝最強組合（聯電、力積電）；融資暴增＋股價爆量＝過熱警訊（陽明、萬海）。

---

### 五、綜合選股建議 · 三梯隊
1. **第一梯（籌碼轉佳）**：2303 聯電 ｜ 6770 力積電 ｜ 2324 仁寶 ｜ 2603 長榮（拉回量縮承接）
2. **第二梯（過熱觀察）**：2609 陽明 ｜ 2615 萬海（不追高，設移動停利）
3. **第三梯（籌碼轉弱）**：2408 南亞科 ｜ 2344 華邦電 ｜ 2317 鴻海 ｜ 2330 台積電（避開或等止跌）
            """.trimIndent(),
            rating = "聯電・力積電 籌碼最強",
            entryPrice = 120.5,
            targetPrice = 131.0,
            stopLossPrice = 115.5,
            keyFibLevel = "支撐: 121.0 / 壓力: 131.0",
            tags = "籌碼選股,法人買賣超,資券變化,融券軋空,聯電,力積電",
            author = "籌碼選股分析師",
            dateStr = todayStr,
            isPinned = true
        )

        // 3. 盤後研究筆記範本 3: 盤後研究員
        val note3 = ResearchNoteEntity(
            notebookType = "AFTER_HOURS",
            title = "2026/08/24 盤後市場分析｜量縮重挫失守45,000 三大法人轉賣163億 航運逆勢強",
            targetSymbol = "^TWII",
            targetName = "加權指數",
            summary = "加權重挫462點失守季線與45,000，成交量急縮至6,294億創逾四月新低。三大法人轉賣163.78億。航運三雄爆量逆勢、權值分歧，下檔以44,500與半年線為首要防守。",
            content = """
# 盤後研究 · 2026/08/24 · TAIPEI 16:30
## 2026/08/24 盤後市場分析｜量縮重挫失守45,000 三大法人轉賣163億 航運逆勢強

> **核心摘要**：加權重挫 462 點失守季線與 45,000、成交量急縮至 6,294 億創逾四月新低 · 三大法人由買轉賣合計 -163.78 億（外資 -157.5 億領賣）· 航運三雄爆量逆勢、權值分歧。

---

### 📊 盤後核心數據
- **加權指數 TAIEX**：44,762.32 點 (-461.97 點 / -1.02%)，失守 45,000 與季線
- **櫃買指數 OTC**：約 386 點 (-0.3%)，中小型相對抗跌
- **上市成交額**：6,294 億元，創逾 4 個月新低（量縮價跌）
- **三大法人合計**：-163.78 億元 (外資 -157.5 億 / 投信 +37.0 億)

---

### 💡 一句話重點
> 上週五（08/21）量縮反彈站回 45,224 後，**今日量能再縮逾 900 億、價跌量縮破季線**，屬「反彈一日行情」後的快速回檔。外資由 +283 億翻為 -157.5 億、投信孤軍買超 37 億，籌碼面轉為**外資提款、內資撐 OTC**；盤面僅航運與少數電子逆勢，追價力道全面熄火，下檔以 44,500 與半年線為首要防守。

---

### 一、指數表現與走勢解讀
| 指數 | 08/24 收盤 | 漲跌點數 | 漲跌幅 | 成交額 / 備註 |
|---|---|---|---|---|
| **加權指數 TAIEX** | **44,762.32** | **-461.97** | **-1.02%** | 6,294.27 億元 · 跌破 5 日線與季線 |
| 加權 (08/21 對比) | 45,224.29 | +290.55 | +0.65% | 前一日量縮反彈、今日全數回吐 |
| **櫃買指數 OTC** | 約 386 點 | 約 -1.16 | -0.3% | 跌幅僅大盤 1/3，相對有撐 |

- **走勢解讀**：開盤小漲 16 點至 45,240 後一路走低，收在當日相對低點長黑。量能創逾四月新低，顯示追價買盤縮手；技術面一舉跌破 5 日線與季線（約 44,933）。
- **關鍵點位**：上壓 45,224 與 45,500～46,000 套牢區；支撐先看 44,500 整數與半年線，若破恐回探 44,000。

---

### 二、三大法人籌碼 · 由買轉賣
| 法人 | 買賣超金額 | 方向 | 08/21 對比 | 備註 |
|---|---|---|---|---|
| **外資及陸資** | **-157.51 億** | 賣超 | +283.05 億 | 提款最重、連買一日即翻賣 |
| **投信** | **+37.05 億** | 買超 | +21.01 億 | 唯一站買方、孤軍撐盤 |
| **自營商** | **-43.32 億** | 賣超 | +29.23 億 | 自行+避險同步轉賣 |
| **合計** | **-163.78 億** | 賣超 | +331.06 億 | 單日由大買轉大賣約 495 億 |

---

### 三、市場重點與族群分化
1. **最強族群（航運三雄）**：資金避險轉進，2615 萬海 (+9.69%, 124.5元 爆量8.8萬張)、2609 陽明 (+6.97%, 64.5元 爆量16.8萬張)、2603 長榮 (+1.83%, 250.5元)。
2. **弱勢拖累（權值與記憶體）**：2330 台積電 (-35元 至 2,375元, -1.45%) 貢獻大盤約 -250 點；2408 南亞科 (-3.09% 至 501元)。
3. **逆勢亮點（低位階電子）**：2454 聯發科 (+1.76% 至 3,765元)、2303 聯電 (+6.01% 至 123.5元)、光寶科漲停 287 元。

---

### 四、國際股市與下週定調
- **美股週五反彈**：道瓊 +0.98% 創高，但那指與標普週線仍收黑，屬技術性反彈。
- **關鍵風向球**：**輝達 NVIDIA 8/26 盤後公布 FY Q2 財報** 與 **美國 7 月 PCE 通膨數據**，財報前 AI 供應鏈波動將持續放大。

---

### 五、明日 08/25 觀察重點
1. **量能是否止縮**：反彈需量增至 7,500～8,000 億以上才有續航，否則定義為量縮弱勢整理。
2. **法人是否續賣**：外資是否連二日賣超，航運爆量後需防隔日獲利了結賣壓。
3. **台積電與季線爭奪**：台積電 2,375 元與加權季線（約 44,933）為多空分水嶺。
4. **輝達財報前震盪**：短線以 44,500 與 44,583 低點為防守，破則下探 44,000。
5. **族群輪動**：追蹤航運續航 vs 記憶體止跌訊號。
            """.trimIndent(),
            rating = "量縮弱勢整理 (防守44,500)",
            tags = "大盤指數,盤後分析,季線保衛,成交量縮,航運逆勢",
            author = "盤後研究員",
            dateStr = todayStr,
            isPinned = true
        )

        // 4. 策略研究筆記範本 4: 策略分析師 (雙軌決策中樞)
        val note4 = ResearchNoteEntity(
            notebookType = "STRATEGY",
            title = "2026/08/24 策略定調｜聯電/長榮雙強領航、航運過熱不追、記憶體避風、輝達財報前控管",
            targetSymbol = "STRATEGY",
            targetName = "雙軌決策中樞",
            summary = "承接08/24消息面驗證分級，雙軌定調：首選2303聯電、次選2603長榮；6770列觀察；2609/2615過熱不追；2408/2344籌碼分歧全避；2324仁寶缺催化不進場。兩週內只做三同向。",
            content = """
# 策略研究 · 2026/08/24 雙軌定調 · STRATEGIC DECISION HUB
## 聯電 / 長榮雙強領航・航運過熱不追・記憶體避風・輝達財報前風控

> **核心摘要**：承接 08/24 消息面驗證分級，以「消息＋籌碼＋技術」三同向才出手。**首選 2303 聯電、次選 2603 長榮**；6770 觀察、2609/2615 與 2408/2344 全避。

---

### 🏆 雙軌核心標的矩陣
- **【首選】2303 聯電 (NT$ 123.5 跳空突破)**：投信 +14,763 張 · 炸量 19.3 萬張 · 三同向 ★★★
- **【次選】2603 長榮 (NT$ 250.5 SCFI連4漲)**：量 2.1 萬張續強 · 趨勢多頭 · 三同向 ★★★
- **【過熱不追】2609 陽明 64.5 / 2615 萬海 124.5**：月漲 43% 爆量長上影 · 當沖過熱 · 僅防守

---

### 📋 執行摘要
承接 08/24 驗證分級，雙軌定調：**首選 2303 聯電、次選 2603 長榮；6770 列觀察；2609/2615 過熱不追；2408/2344 籌碼分歧全避；2324 仁寶缺催化不進場。** 兩週內只做「消息＋籌碼＋技術」三同向；大盤量縮 6,294 億＋外資單日提款 157 億＋輝達 08/26 財報前，總水位 5 成內、航運不加碼、破線即退。

---

### 一、三面向同向度總表
| 分級 | 標的 | 同向度 | 策略動作 |
|---|---|---|---|
| **高度一致** | **2303 聯電** | **★★★ 三同向** | **拉回承接主戰場** |
| **高度一致** | **2603 長榮** | **★★★ 三同向** | **趨勢續抱** |
| **分歧** | **6770 力積電 70.8** | **★★ 觀察** | 站穩 68.5 才追 |
| **過熱** | **2609 陽明 / 2615 萬海** | **★ 過熱** | 不追、破 5 日線出 |
| **籌碼兌現** | **2408 南亞科 / 2344 華邦電** | **☆ 背離** | 全避、反彈減碼 |
| **缺催化** | **2324 仁寶 41.6** | **☆ 無驅動** | 不進場 |

---

### 二、選股金字塔
- **🥇 第一階：立即執行 (資金各 25% 為限，僅此二檔主動建倉)**
  - **2303 聯電**：NT$ 120 - 121 承接
  - **2603 長榮**：NT$ 240 - 245 承接
- **🥈 第二階：條件單 (未觸條件不碰，避免替分歧買單)**
  - **6770 力積電**：收盤 > 68.5 ＋ 量 > 16 萬張才進
  - **2324 仁寶**：僅作為 AI 擴散備選

---

### 三、軌道一｜長期存股
- **唯一存股標的：2303 聯電**
  - **基本面邏輯**：成熟製程結構性缺口＋稼動率 90%＋漲價到 2027，投信中長線認養。
  - **執行作法**：定期定額＋回撤加碼（Fib 0.5 約 110.7 加一倍、Fib 0.618 約 107.7 再加），跌破 90 元月線停扣。
  - *(註：長榮屬景氣循環股不納入存股，僅作波段)*

---

### 四、軌道二｜兩週爆發 進出場錨點
#### 1. 2303 聯電 (現價 123.5)
- **進場錨點**：回測 120-121 量縮收腳；或帶量過 124 追
- **停損防守**：收破 115.5 缺口全退
- **停利目標**：128 (第一目標) / 131 (Fib 127.2%) / 139 (Fib 161.8%) 分批獲利
- **斐波回撤加碼位**：113.7 / 110.7 / 107.7

#### 2. 2603 長榮 (現價 250.5)
- **進場錨點**：拉回 240-245 承接；過 254 ＋ 量 2.5 萬加碼
- **停損防守**：收破 235
- **停利目標**：260 / 275 / 300
- **失效條件**：SCFI 轉跌或破 20 日線全出

#### 3. 6770 力積電 (現價 70.8 觀察)
- **觸發條件**：站穩 68.5 ＋ 量增才進，目標 72 / 75，停損 65.5

#### 4. 不做清單
- **2609 / 2615**：不追、破 60.3 / 113.5 出清
- **2408 / 2344**：全避、反彈減碼

---

### 五、技術綜合判讀
- **均線排列**：2303 / 2603 為 **5 > 10 > 20 多頭排列、股價 > 20 日線** 多方控盤；2408 / 2344 跌破 5/10 日轉弱。
- **K 線與量能**：雙強「跳空＋長紅＋量增」為有效突破；陽明 / 萬海「爆量＋長上影」為假突破高風險。
- **關鍵支撐壓力**：2303 壓 124 / 131、撐 121 / 115.5；2603 壓 254 / 260、撐 245 / 235。
- **斐波那契（近60日低-高）**：
  - **2303 (98→123.5)**：回撤 113.7 / 110.7 / 107.7、擴展 131 / 139。
  - **2603 (180→254)**：回撤 226 / 217 / 208、擴展 274 / 300。
  *(回撤位即為低接加碼位、擴展位即為目標停利位)*

---

### 六、風控守則｜輝達 08/26 財報前
1. **總水位 5 成內**：8/25-8/26 不盲目加碼，財報後量能表態再加至 7 成；電子與航運不壓單邊。
2. **航運過熱嚴控**：2609 / 2615 不新建倉、持有半倉、破 5 日線即全撤，絕不融資追航運。
3. **停損紀律執行**：收盤破支撐即退，單筆虧損 2%、單日總虧損 4% 即收手。
4. **大盤濾網機制**：量 < 6,500 億且指數 < 45,000 時新單減半、僅做雙強；量回 7,000 億＋站回 45,000 才恢復。

---

### 七、本週行動清單
- **8/25 開盤掛單**：2303 掛 120.5-121 承接、2603 掛 242-245 承接；6770 設 68.6 條件單。
- **持股管理**：2609 / 2615 設 60.3 / 113.5 移動停利；2408 / 2344 反彈至 51 / 18 減碼。
- **每日檢核指標**：SCFI 運價走勢、三大法人買賣超、5/10/20 日均線是否續多、成交量能是否重回 7,500 億。

> **結論**：雙強領航、只做同向；過熱與分歧全避；財報前活下來，財報後再放大。
            """.trimIndent(),
            rating = "★★★ 三面向同向共振 (雙強領航)",
            entryPrice = 120.5,
            targetPrice = 139.0,
            stopLossPrice = 115.5,
            keyFibLevel = "回撤50%: 110.7 / 擴展161.8%: 139.0",
            tags = "雙軌決策,長期存股,兩週爆發,三面向共振,選股金字塔,斐波那契",
            author = "策略分析師 (雙軌中樞)",
            dateStr = todayStr,
            isPinned = true
        )

        noteDao.insertNote(note1)
        noteDao.insertNote(note2)
        noteDao.insertNote(note3)
        noteDao.insertNote(note4)
    }

    private suspend fun seedDefaultAnalysts(forceReset: Boolean = false) {
        if (forceReset) {
            val list = customAnalystDao.getAllAnalysts().firstOrNull() ?: emptyList()
            list.forEach { customAnalystDao.deleteAnalyst(it) }
        }

        val defaultList = listOf(
            CustomAnalystEntity(
                name = "盤後研究員",
                roleTitle = "宏觀大盤與產業總體分析師",
                avatarIcon = "ANALYTICS",
                themeColorHex = 0xFF38BDF8,
                specialization = "每日收盤加權指數多空結構、法人籌碼歸屬與明日盤勢指引",
                systemPrompt = "你是一位資深台灣股市盤後研究總監，擅長宏觀大盤指數拆解、外資與投信法人動向分析、主流產業族群剖析以及明日台股關鍵多空分水嶺操作指引。",
                analysisStyle = "宏觀大盤與族群輪動",
                notebookType = "AFTER_HOURS",
                provider = "Google Gemini",
                modelId = "gemini-2.5-flash",
                modelDisplayName = "Gemini 2.5 Flash",
                upstreamRoleKey = "NONE",
                allowedTools = "get_market_summary,get_stock_quote,get_stock_history,write_note",
                scheduleCron = "16:00 (盤後每日)",
                isEnabled = true,
                isBuiltIn = true,
                defaultRoleKey = "AFTER_HOURS",
                promptVersion = 1
            ),
            CustomAnalystEntity(
                name = "籌碼選股分析師",
                roleTitle = "外資投信主力雷達總監",
                avatarIcon = "ACCOUNT_BALANCE",
                themeColorHex = 0xFF06B6D4,
                specialization = "三大法人買賣超、投信連買作帳、主力鎖碼度與資減券增軋空訊號",
                systemPrompt = "你是一位精通台股籌碼面的王牌分析師，專精分析外資、投信、自營商買賣超、持股比例及融資融券變化，評估主力鎖碼與散戶籌碼沉澱度，將股票分為A(明顯轉強)、B(觀察)、C(中性)、D(轉弱)、E(過熱高風險)。",
                analysisStyle = "籌碼跟單與軋空",
                notebookType = "DAY_TRADING",
                provider = "Google Gemini",
                modelId = "gemini-2.5-flash",
                modelDisplayName = "Gemini 2.5 Flash",
                upstreamRoleKey = "AFTER_HOURS",
                allowedTools = "read_note,get_institutional,get_margin,write_note",
                scheduleCron = "16:05 (盤後每日)",
                isEnabled = true,
                isBuiltIn = true,
                defaultRoleKey = "CHIPS_SCREENER",
                promptVersion = 1
            ),
            CustomAnalystEntity(
                name = "消息面雷達",
                roleTitle = "國際財經情報官 (7日時效窗)",
                avatarIcon = "RSS_FEED",
                themeColorHex = 0xFFF97316,
                specialization = "即時重大財經新聞、美股連動、法說政策與產業催化劑評估",
                systemPrompt = "你是一位國際財經新聞與台股重大事件情報官，嚴格採計最近7天公信消息，核實發布日期與來源，驗證籌碼異常背後是否存在事件催化，確認市場是否已反映並與法人籌碼同向。",
                analysisStyle = "重大新聞與催化劑評估",
                notebookType = "AFTER_HOURS",
                provider = "Google Gemini",
                modelId = "gemini-2.5-flash",
                modelDisplayName = "Gemini 2.5 Flash",
                upstreamRoleKey = "CHIPS_SCREENER",
                allowedTools = "read_note,search_recent_news,write_note",
                scheduleCron = "16:10 (盤後每日)",
                isEnabled = true,
                isBuiltIn = true,
                defaultRoleKey = "NEWS_RADAR",
                promptVersion = 1
            ),
            CustomAnalystEntity(
                name = "策略分析師 (雙軌中樞)",
                roleTitle = "首席策略總監・雙軌決策長",
                avatarIcon = "HUB",
                themeColorHex = 0xFFA855F7,
                specialization = "【軌道一】長期價值存股＋【軌道二】兩周內消息/籌碼/技術三面同向動態爆發",
                systemPrompt = "你是一位身兼多空對沖與頂級避險基金的投資策略總監，負責執行【雙軌決策中樞】：軌道一（長期存股安全邊際買點）與軌道二（兩周內中短期動態爆發：消息面＋籌碼面＋技術面全面綜合判讀，三面向同向才出手，嚴設斐波那契停利停損點、選股金字塔與明日三情境劇本）。",
                analysisStyle = "三面共振動態爆發",
                notebookType = "STRATEGY",
                provider = "Google Gemini",
                modelId = "gemini-2.5-flash",
                modelDisplayName = "Gemini 2.5 Flash",
                upstreamRoleKey = "NEWS_RADAR",
                allowedTools = "read_note,calculate_ma,calculate_macd,calculate_kd,calculate_fibonacci,calculate_support_resistance,write_note",
                scheduleCron = "16:15 (盤後每日)",
                isEnabled = true,
                isBuiltIn = true,
                defaultRoleKey = "STRATEGY_HUB",
                promptVersion = 1
            ),
            CustomAnalystEntity(
                name = "當沖策略顧問",
                roleTitle = "短線極速操盤手・均線斐波教練",
                avatarIcon = "TIMELINE",
                themeColorHex = 0xFFF59E0B,
                specialization = "5/10/20日均線定進出、20/60日均線定趨勢，交叉驗證斐波那契關鍵價位",
                systemPrompt = "你是一位頂尖台股當沖與短線操盤教練，專門使用 5/10/20 日均線定進出、20/60 均線定趨勢、斐波那契關鍵回撤與擴展點位（38.2%、61.8%、127.2%、161.8%），制定精準當沖進出場與風控點位。",
                analysisStyle = "極短線當沖/均線進出",
                notebookType = "DAY_TRADING",
                provider = "Google Gemini",
                modelId = "gemini-2.5-flash",
                modelDisplayName = "Gemini 2.5 Flash",
                upstreamRoleKey = "STRATEGY_HUB",
                allowedTools = "read_note,calculate_ma,calculate_support_resistance,calculate_kd,write_note",
                scheduleCron = "08:30 (開盤前每日)",
                isEnabled = true,
                isBuiltIn = true,
                defaultRoleKey = "DAY_TRADING",
                promptVersion = 1
            ),
            CustomAnalystEntity(
                name = "個股研究員",
                roleTitle = "巴菲特價值存股分析師",
                avatarIcon = "SAVINGS",
                themeColorHex = 0xFF10B981,
                specialization = "獲利三率、ROE、營收成長、本益比河流圖與便宜價/合理價/昂貴價模型",
                systemPrompt = "你是一位專精於台股價值投資、巴菲特存股法則與財務報表深度剖析的資深分析師。擅長計算合理價、便宜價、昂貴價及評估長期護城河與定期定額安全邊際買點。",
                analysisStyle = "價值投資與存股護城河",
                notebookType = "FUNDAMENTAL",
                provider = "Google Gemini",
                modelId = "gemini-2.5-flash",
                modelDisplayName = "Gemini 2.5 Flash",
                upstreamRoleKey = "STRATEGY_HUB",
                allowedTools = "get_financials,get_stock_quote,write_note",
                scheduleCron = "16:30 (盤後每日)",
                isEnabled = true,
                isBuiltIn = true,
                defaultRoleKey = "FUNDAMENTAL",
                promptVersion = 1
            )
        )

        customAnalystDao.insertAnalysts(defaultList)
    }
}
