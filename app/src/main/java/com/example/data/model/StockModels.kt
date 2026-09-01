package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Taiwan Stock market individual stock quote
 */
data class StockQuote(
    val symbol: String,               // e.g. "2330"
    val name: String,                 // e.g. "台積電"
    val category: String,             // e.g. "半導體", "AI伺服器", "航運"
    val currentPrice: Double,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val previousClose: Double,
    val change: Double,
    val changePercent: Double,
    val volume: Long,                 // Total shares / lots (張)
    val totalAmount: Double,          // In NTD Hundreds of Millions (億元)
    val peRatio: Double = 18.5,       // 本益比
    val yieldRate: Double = 3.8,      // 殖利率 %
    val pbRatio: Double = 2.4,        // 股價淨值比
    val roe: Double = 24.5,           // 股東權益報酬率 %
    val grossMargin: Double = 53.2,   // 毛利率 %
    val operatingMargin: Double = 42.1,// 營業利益率 %
    val netMargin: Double = 38.5,     // 稅後純益率 %
    val eps: Double = 38.5,           // 每股盈餘 (近四季)
    val revenueGrowthMom: Double = 5.2, // 月營收 MoM %
    val revenueGrowthYoy: Double = 28.4,// 月營收 YoY %
    val isWatchlisted: Boolean = true,
    val kLineHistory: List<KLinePoint> = emptyList(),
    val chips: InstitutionalChips? = null
)

/**
 * Taiwan Market Indices (加權指數、櫃買指數等)
 */
data class IndexQuote(
    val symbol: String,               // "^TWII", "^TWOII", "^TWELEC"
    val name: String,                 // "加權指數", "櫃買指數", "電子類指數"
    val current: Double,
    val change: Double,
    val changePercent: Double,
    val high: Double,
    val low: Double,
    val volumeAmount: Double          // 預估/成交金額 (億元)
)

/**
 * Institutional Chips & Margin/Short data (法人買賣超與資券籌碼)
 */
data class InstitutionalChips(
    val symbol: String,
    val foreignBuySell: Long,         // 外資買賣超 (張)
    val trustBuySell: Long,           // 投信買賣超 (張)
    val dealerBuySell: Long,          // 自營商買賣超 (張)
    val foreignHoldPercent: Double,   // 外資持股率 %
    val trustConsecutiveBuyDays: Int, // 投信連買天數
    val marginBalance: Long,          // 融資餘額 (張)
    val marginChange: Long,           // 融資增減 (張, +為增 -為減)
    val shortBalance: Long,           // 融券餘額 (張)
    val shortChange: Long,            // 融券增減 (張)
    val marginShortRatio: Double,     // 券償比 % (融券餘額/融資餘額)
    val chipRating: String = "籌碼集中偏多" // 籌碼評價
) {
    val totalInstitutional: Long
        get() = foreignBuySell + trustBuySell + dealerBuySell
}

/**
 * Candlestick K-Line Point
 */
data class KLinePoint(
    val timestamp: Long,
    val dateStr: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    // Moving Averages
    var ma5: Double? = null,
    var ma10: Double? = null,
    var ma20: Double? = null,
    var ma60: Double? = null,
    // Volume MA
    var vma5: Double? = null,
    var vma20: Double? = null,
    // MACD
    var dif: Double? = null,
    var macd: Double? = null,
    var osc: Double? = null,
    // KD
    var k: Double? = null,
    var d: Double? = null
)

/**
 * Fibonacci Retracement & Extension Levels
 */
data class FibonacciLevels(
    val swingHigh: Double,
    val swingLow: Double,
    val isUptrend: Boolean = true,
    // Retracement Levels (回撤位)
    val level0_0: Double = swingHigh,       // 0.0% (頂點)
    val level0_236: Double = 0.0,           // 23.6%
    val level0_382: Double = 0.0,           // 38.2% (關鍵支撐)
    val level0_500: Double = 0.0,           // 50.0% (中線支撐)
    val level0_618: Double = 0.0,           // 61.8% (黃金分割關鍵位)
    val level0_786: Double = 0.0,           // 78.6% (深幅回撤)
    val level1_000: Double = swingLow,      // 100.0% (起漲點底點)
    // Extension Levels (擴展目標位)
    val ext1_272: Double = 0.0,             // 127.2% 擴展目標一
    val ext1_618: Double = 0.0,             // 161.8% 黃金擴展目標二
    val ext2_000: Double = 0.0,             // 200.0% 翻倍擴展目標三
    val ext2_618: Double = 0.0,             // 261.8% 強勢延伸目標四
    // Key Support & Resistance
    val resistance1: Double = 0.0,
    val resistance2: Double = 0.0,
    val support1: Double = 0.0,
    val support2: Double = 0.0,
    val pivotPoint: Double = 0.0
)

/**
 * Real-time Intraday Warning Alert
 */
data class IntradayAlert(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val stockSymbol: String,
    val stockName: String,
    val alertType: AlertType,
    val message: String,
    val price: Double,
    val isHighPriority: Boolean = false,
    val dateFormatted: String = SimpleDateFormat("HH:mm:ss", Locale.TAIWAN).format(Date())
)

enum class AlertType(val label: String) {
    BREAKOUT_RESISTANCE("突破關鍵壓力"),
    BREAKDOWN_SUPPORT("跌破關鍵支撐"),
    FIBONACCI_KEY_LEVEL("觸及斐波那契關鍵位"),
    HEAVY_VOLUME_SURGE("帶量長紅突破"),
    KD_GOLDEN_CROSS("KD黃金交叉"),
    MACD_BULL_CROSS("MACD翻紅突破"),
    INSTITUTIONAL_BUY_SURGE("法人大量買超"),
    MARGIN_SHORT_SQUEEZE("資減券增軋空訊號")
}

/**
 * Market News & Event
 */
data class MarketNews(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val category: String, // "科技/財報", "政策法說", "國際美股", "大盤焦點"
    val timestamp: Long,
    val timeAgo: String,
    val sentiment: String, // "利多", "利空", "中立"
    val impactRating: String, // "重大利多 (+5)", "中性偏多 (+2)"
    val relatedSymbols: List<String> = emptyList()
)

/**
 * Research Notebook Category
 */
enum class NotebookType(val title: String, val subtitle: String, val badgeColor: Long) {
    AFTER_HOURS("盤後研究筆記", "每日收盤市場動向與大盤剖析", 0xFF38BDF8),
    DAY_TRADING("當沖策略筆記", "盤前短線標的、5/10/20均線與進出場價位", 0xFFF59E0B),
    FUNDAMENTAL("個股研究筆記", "存股價值評估、財報體質與合理買點", 0xFF10B981),
    STRATEGY("策略研究筆記", "雙軌決策中樞：長期存股與動態爆發", 0xFFA855F7)
}

/**
 * AI Stock Screener Recommendation Result
 */
data class AIStockRecommendation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val stockSymbol: String,
    val stockName: String,
    val category: String,
    val currentPrice: Double,
    val changePercent: Double,
    val analystId: Long = 0L,
    val analystName: String,
    val score: Int,                         // 0-100 score
    val recommendationType: String,         // e.g. "強烈買進 (三面共振)", "波段布局", "突破加碼", "價值存股"
    val rationale: String,                  // AI 核心論點
    val threeWayResonance: Boolean = false, // 消息+籌碼+技術三面共振
    val entryPrice: Double,
    val targetPrice: Double,                // Fibonacci extension target
    val stopLossPrice: Double,              // Fibonacci defense level
    val riskRewardRatio: Double = 2.5,
    val peRatio: Double = 18.0,
    val yieldRate: Double = 3.5,
    val institutionalNet: Long = 0L,
    val trustConsecutiveDays: Int = 0,
    val chipsRating: String = "籌碼偏多",
    val technicalSignal: String = "多頭排列",
    val catalyst: String = "AI產業需求旺盛",
    val generatedAt: Long = System.currentTimeMillis()
)
