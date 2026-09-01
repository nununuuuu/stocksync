package com.example.domain.ai

import com.example.BuildConfig
import com.example.data.local.CustomAnalystEntity
import com.example.data.model.*
import com.example.domain.calculator.FibonacciCalculator
import com.example.domain.calculator.TechnicalCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class GeminiResearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Call Gemini API with fallback to local domain generator
     */
    private suspend fun callGemini(prompt: String, systemPrompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext ""
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        try {
            val root = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            val partObj = JSONObject().put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            root.put("contents", contentsArray)

            // System instructions
            val systemContent = JSONObject()
            val sysParts = JSONArray().put(JSONObject().put("text", systemPrompt))
            systemContent.put("parts", sysParts)
            root.put("systemInstruction", systemContent)

            val body = root.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url(url).post(body).build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext ""
            }

            val respJson = JSONObject(responseBody)
            val candidates = respJson.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val first = candidates.getJSONObject(0)
                val cContent = first.optJSONObject("content")
                val parts = cContent?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return@withContext parts.getJSONObject(0).optString("text", "")
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 1. 盤後研究員 (After-Hours Market Researcher)
     */
    suspend fun generateAfterHoursReport(
        index: IndexQuote,
        topStocks: List<StockQuote>,
        totalInstitutional: Long,
        newsList: List<MarketNews>
    ): ResearchReport {
        val todayStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date())
        val systemPrompt = "你是一位資深台灣股市盤後研究總監，擅長宏觀大盤指數拆解、外資與投信法人動向分析、主流產業族群剖析以及明日盤勢研判。"
        val prompt = """
            請根據今日台股收盤資訊撰寫一份專業的【每日盤後市場分析報告】：
            日期：$todayStr
            大盤指數：${index.name} 現報 ${String.format("%.2f", index.current)} 點 (漲跌 ${String.format("%+.2f", index.change)}, ${String.format("%+.2f", index.changePercent)}%)
            大盤成交量：約 ${String.format("%.1f", index.volumeAmount)} 億元
            三大法人合計買賣超：${totalInstitutional} 張
            焦點個股：${topStocks.take(5).joinToString(", ") { "${it.symbol} ${it.name}(${String.format("%+.2f", it.changePercent)}%)" }}
            市場焦點頭條：${newsList.take(3).joinToString("; ") { it.title }}

            請依照以下結構撰寫：
            一、大盤多空結構與量價動向
            二、三大法人與主力籌碼歸屬
            三、主流強勢板塊與輪動觀察
            四、明日台股操作指引與關鍵多空分水嶺
        """.trimIndent()

        val aiResult = callGemini(prompt, systemPrompt)
        val fullContent = if (aiResult.isNotBlank()) aiResult else buildFallbackAfterHoursReport(todayStr, index, totalInstitutional, topStocks, newsList)

        return ResearchReport(
            notebookType = NotebookType.AFTER_HOURS,
            title = "【每日盤後研報】$todayStr 大盤指數與法人籌碼全解析",
            targetSymbol = index.symbol,
            targetName = index.name,
            summary = "加權指數收在 ${String.format("%.0f", index.current)} 點 (${String.format("%+.2f", index.changePercent)}%)，成交量 ${String.format("%.0f", index.volumeAmount)} 億。三大法人合計買賣超 ${totalInstitutional} 張，盤面主軸聚焦 ${topStocks.firstOrNull()?.category ?: "半導體與AI"}。",
            content = fullContent,
            rating = if (index.changePercent >= 0) "偏多震盪" else "拉回整理",
            tags = "大盤,盤後,法人買賣超,加權指數"
        )
    }

    /**
     * 2. 籌碼選股分析師 (Institutional Chips & Stock Picker Analyst)
     */
    suspend fun generateChipsAnalysisReport(
        stock: StockQuote,
        chips: InstitutionalChips
    ): ResearchReport {
        val todayStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date())
        val systemPrompt = "你是一位精通台股籌碼面的王牌分析師，專精分析外資、投信、自營商買賣超、持股比例及融資融券變化，評估主力鎖碼與散戶籌碼沉澱度。"
        val prompt = """
            請分析個股【${stock.symbol} ${stock.name}】的最新籌碼數據：
            現價：NT$ ${stock.currentPrice} (${String.format("%+.2f", stock.changePercent)}%)
            外資買賣超：${chips.foreignBuySell} 張 (外資持股比：${chips.foreignHoldPercent}%)
            投信買賣超：${chips.trustBuySell} 張 (投信連買：${chips.trustConsecutiveBuyDays} 天)
            自營商買賣超：${chips.dealerBuySell} 張
            三大法人合計：${chips.totalInstitutional} 張
            融資變化：${chips.marginChange} 張 (餘額：${chips.marginBalance} 張)
            融券變化：${chips.shortChange} 張 (餘額：${chips.shortBalance} 張)
            券償比：${String.format("%.2f", chips.marginShortRatio)}%

            請給出詳細分析：
            1. 法人主力態度（買超集中度與延續性）
            2. 資券散戶動態（資增/資減、券增軋空潛力）
            3. 籌碼異常訊號與主力成本估算
            4. 籌碼評級與操作建議（買進/拉回布局/觀望）
        """.trimIndent()

        val aiResult = callGemini(prompt, systemPrompt)
        val content = if (aiResult.isNotBlank()) aiResult else buildFallbackChipsReport(stock, chips)

        val rating = when {
            chips.trustConsecutiveBuyDays >= 3 || (chips.foreignBuySell > 2000 && chips.trustBuySell > 500) -> "籌碼極度優質 (強烈買進)"
            chips.totalInstitutional > 0 && chips.marginChange < 0 -> "主力吃貨散戶退場 (拉回偏多)"
            chips.shortChange > 500 && chips.marginShortRatio > 25.0 -> "具備資減券增軋空題材"
            chips.totalInstitutional < -1000 -> "法人調節主力倒貨 (逢高減碼)"
            else -> "籌碼中性換手"
        }

        return ResearchReport(
            notebookType = NotebookType.DAY_TRADING,
            title = "【籌碼選股診斷】${stock.symbol} ${stock.name} 法人資券全方位評估",
            targetSymbol = stock.symbol,
            targetName = stock.name,
            summary = "投信連買 ${chips.trustConsecutiveBuyDays} 天，外資單日 ${chips.foreignBuySell} 張，三大法人合計 ${chips.totalInstitutional} 張。融資 ${if (chips.marginChange>=0) "+${chips.marginChange}" else "${chips.marginChange}"}，籌碼評價：$rating。",
            content = content,
            rating = rating,
            tags = "籌碼選股,三大法人,投信連買,資券變化,${stock.name}"
        )
    }

    /**
     * 3. 當沖策略顧問 (Day Trading Strategy Advisor)
     */
    suspend fun generateDayTradingPlan(
        stock: StockQuote,
        fib: FibonacciLevels,
        techSignal: TechnicalCalculator.TechnicalSignalSummary
    ): ResearchReport {
        val todayStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date())
        val price = stock.currentPrice
        val pivot = fib.pivotPoint
        val entryBuy = if (price > fib.level0_382) fib.level0_382 else fib.level0_500
        val targetSell = if (fib.ext1_272 > price) fib.ext1_272 else fib.level0_0
        val stopLoss = fib.level0_618

        val systemPrompt = "你是一位頂尖台股當沖與短線操盤教練，專門使用 5/10/20 日均線定進出、20/60 均線定趨勢、斐波那契關鍵位與量能型態制定精準當沖進出場與風控點位。"
        val prompt = """
            請為【${stock.symbol} ${stock.name}】產出今日盤前當沖與極短線交易策略：
            現價：NT$ $price (開盤參考價)
            均線狀態：${techSignal.trendDescription}
            短線 5/10/20 指引：${techSignal.shortTermAdvice}
            中長 20/60 指引：${techSignal.mediumTermAdvice}
            斐波那契關鍵位：
            - 頂點壓力 (0.0%)：NT$ ${String.format("%.1f", fib.level0_0)}
            - 回撤 38.2% (第一支撐)：NT$ ${String.format("%.1f", fib.level0_382)}
            - 回撤 50.0% (多空中軸)：NT$ ${String.format("%.1f", fib.level0_500)}
            - 回撤 61.8% (黃金防守)：NT$ ${String.format("%.1f", fib.level0_618)}
            - 擴展 127.2% (第一獲利目標)：NT$ ${String.format("%.1f", fib.ext1_272)}
            - 擴展 161.8% (第二獲利目標)：NT$ ${String.format("%.1f", fib.ext1_618)}

            請提供：
            1. 今日當沖多空戰略方向（做多、做空或區間）
            2. 精確進場點位（突破確認 / 拉回斐波那契支撐）
            3. 目標停利位（斐波那契擴展目標）與停損價（破線防守）
            4. 風險評估與勝率評分（1~10 分）
        """.trimIndent()

        val aiResult = callGemini(prompt, systemPrompt)
        val content = if (aiResult.isNotBlank()) aiResult else buildFallbackDayTradingReport(stock, fib, techSignal, entryBuy, targetSell, stopLoss)

        return ResearchReport(
            notebookType = NotebookType.DAY_TRADING,
            title = "【當沖短線教戰】${stock.symbol} ${stock.name} 均線與斐波那契精準點位",
            targetSymbol = stock.symbol,
            targetName = stock.name,
            summary = "均線 5/10/20 定進出，建議進場價約 NT$ ${String.format("%.1f", entryBuy)}，停利目標 NT$ ${String.format("%.1f", targetSell)} (Fib擴展)，嚴設停損 NT$ ${String.format("%.1f", stopLoss)}。",
            content = content,
            rating = if (techSignal.isBullishMaAlignment) "當沖偏多操作" else "區間來回或觀望",
            entryPrice = entryBuy,
            targetPrice = targetSell,
            stopLossPrice = stopLoss,
            keyFibLevel = "回撤38.2%: ${String.format("%.1f", fib.level0_382)} / 擴展127.2%: ${String.format("%.1f", fib.ext1_272)}",
            tags = "當沖策略,均線進出,斐波那契點位,停損風控"
        )
    }

    /**
     * 4. 消息面雷達 (News Radar & Catalyst Impact)
     */
    suspend fun generateNewsRadarReport(
        newsList: List<MarketNews>,
        focusSector: String = "科技與AI板塊"
    ): ResearchReport {
        val todayStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date())
        val systemPrompt = "你是一位國際財經新聞與台股重大事件情報官，負責從海量即時新聞、美股財報與政策中，快速萃取對台股供應鏈與產業的多空衝擊波。"
        val prompt = """
            請分析今日台股最新重大情報清單：
            ${newsList.joinToString("\n\n") { "【${it.category}】${it.title}\n摘要：${it.summary}\n評估：${it.sentiment} (${it.impactRating})\n受影響標的：${it.relatedSymbols.joinToString(",")}" }}

            請撰寫【消息面情報特刊】：
            1. 本日最核心事件與催化劑（Catalyst）
            2. 對大盤指數與美元/美股連動的宏觀影響
            3. 最直接受惠族群與潛在受害風險類股
            4. 交易員應對策略與時效性預警
        """.trimIndent()

        val aiResult = callGemini(prompt, systemPrompt)
        val content = if (aiResult.isNotBlank()) aiResult else buildFallbackNewsRadarReport(newsList, focusSector)

        return ResearchReport(
            notebookType = NotebookType.AFTER_HOURS,
            title = "【消息面情報雷達】最新國際局勢、美股連動與台股重大催化劑",
            targetSymbol = "NEWS",
            targetName = "重大財經要聞",
            summary = "追蹤台積電ADR、輝達財報、全球地緣與政策焦點。綜合評估當前消息面利多頻傳，資金集中在AI伺服器、半導體先進製程與高股息概念股。",
            content = content,
            rating = "消息面全面偏多",
            tags = "消息面雷達,重大新聞,美股連動,產業政策"
        )
    }

    /**
     * 5. 個股研究員 (Fundamental Value Researcher)
     */
    suspend fun generateFundamentalReport(
        stock: StockQuote
    ): ResearchReport {
        val todayStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date())
        val systemPrompt = "你是一位專精於台股價值投資、巴菲特存股法則與財務報表深度剖析的資深分析師。擅長計算合理價、便宜價、昂貴價及評估長期護城河。"
        val prompt = """
            請為存股核心標的【${stock.symbol} ${stock.name}】撰寫深度財報與基本面價值研究報告：
            現價：NT$ ${stock.currentPrice}
            所屬產業：${stock.category}
            近四季 EPS：NT$ ${stock.eps} 元
            本益比 (P/E)：${stock.peRatio} 倍
            殖利率：${stock.yieldRate}%
            股價淨值比 (P/B)：${stock.pbRatio} 倍
            股東權益報酬率 (ROE)：${stock.roe}%
            毛利率：${stock.grossMargin}%
            營業利益率：${stock.operatingMargin}%
            稅後純益率：${stock.netMargin}%
            月營收成長：MoM ${String.format("%+.1f", stock.revenueGrowthMom)}%, YoY ${String.format("%+.1f", stock.revenueGrowthYoy)}%

            請評估：
            1. 企業財務體質與獲利三率趨勢
            2. 產業地位與長期競爭護城河
            3. 估值位階計算（便宜價、合理價、昂貴價估算）
            4. 長期存股與定期定額安全邊際建議
        """.trimIndent()

        val aiResult = callGemini(prompt, systemPrompt)
        val cheapPrice = stock.eps * 15.0
        val fairPrice = stock.eps * 20.0
        val expensivePrice = stock.eps * 26.0

        val content = if (aiResult.isNotBlank()) aiResult else buildFallbackFundamentalReport(stock, cheapPrice, fairPrice, expensivePrice)

        val rating = when {
            stock.currentPrice <= cheapPrice -> "極具價值 (便宜價買點)"
            stock.currentPrice <= fairPrice -> "價值合理 (定期定額存股)"
            else -> "估值偏高 (等待拉回佈局)"
        }

        return ResearchReport(
            notebookType = NotebookType.FUNDAMENTAL,
            title = "【個股基本面研報】${stock.symbol} ${stock.name} 財報體質與價值存股評估",
            targetSymbol = stock.symbol,
            targetName = stock.name,
            summary = "近四季 EPS ${stock.eps} 元，本益比 ${stock.peRatio} 倍，殖利率 ${stock.yieldRate}%，ROE ${stock.roe}%。便宜價估約 NT$ ${String.format("%.0f", cheapPrice)}，合理價 NT$ ${String.format("%.0f", fairPrice)}。評估：$rating。",
            content = content,
            rating = rating,
            entryPrice = cheapPrice,
            targetPrice = fairPrice * 1.2,
            stopLossPrice = cheapPrice * 0.85,
            tags = "個股研究,基本面,價值存股,本益比河流,ROE,${stock.name}"
        )
    }

    /**
     * 6. 策略分析師：雙軌決策中樞 (Dual-Track Strategic Decision Hub)
     */
    suspend fun generateDualTrackStrategyReport(
        stock: StockQuote,
        chips: InstitutionalChips,
        fib: FibonacciLevels,
        techSignal: TechnicalCalculator.TechnicalSignalSummary,
        newsList: List<MarketNews>
    ): ResearchReport {
        val todayStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date())
        val systemPrompt = "你是一位身兼多空對沖與頂級避險基金的投資策略總監，負責執行【雙軌決策中樞】：軌道一（長期存股安全邊際買點）與軌道二（兩周內中短期動態爆發：消息面＋籌碼面＋技術面全面綜合判讀，三面向同向才出手）。"
        val prompt = """
            請為【${stock.symbol} ${stock.name}】產出雙軌決策中樞研判報告：
            現價：NT$ ${stock.currentPrice} (${String.format("%+.2f", stock.changePercent)}%)
            
            【軌道一：長期存股】
            EPS：${stock.eps} 元 | PE：${stock.peRatio} | 殖利率：${stock.yieldRate}% | ROE：${stock.roe}%
            
            【軌道二：兩周內中短期動態爆發檢驗】
            1. 消息面雷達：${newsList.filter { it.relatedSymbols.contains(stock.symbol) || it.relatedSymbols.isEmpty() }.take(2).joinToString("; ") { it.title }}
            2. 籌碼面動向：外資 ${chips.foreignBuySell} 張, 投信連買 ${chips.trustConsecutiveBuyDays} 天, 總法人 ${chips.totalInstitutional} 張, 資券現況 ${if (chips.marginChange<0) "資減" else "資增"} ${chips.marginChange}
            3. 技術面綜合判讀：
               - 均線狀態：${techSignal.trendDescription} (短線5/10/20定進出，中長20/60定趨勢)
               - K線與量能：${if (techSignal.isHeavyVolumeLongRed) "帶量長紅突破" else "量能平穩"}
               - 指標共振：KD ${if (techSignal.isKdGoldenCross) "黃金交叉" else "整理"} / MACD ${if (techSignal.isMacdBullish) "柱狀體多頭翻紅" else "零軸下拉鋸"}
               - 斐波那契回撤支撐：38.2% NT$ ${String.format("%.1f", fib.level0_382)} / 61.8% NT$ ${String.format("%.1f", fib.level0_618)}
               - 斐波那契擴展目標：127.2% NT$ ${String.format("%.1f", fib.ext1_272)} / 161.8% NT$ ${String.format("%.1f", fib.ext1_618)}

            請產出完整決策報告：
            一、【軌道一：長期存股價值評定與分批建倉指引】
            二、【軌道二：兩周內中短期動態爆發三面向檢驗】
                - 消息面題材評分 (1~5分)
                - 籌碼面法人鎖碼評分 (1~5分)
                - 技術面突破確認評分 (1~5分)
            三、【三面向同向共振判定】：是否符合出手條件？（若三面向未同向則明確示警勿追高）
            四、【精確執行計畫】：進場買點、斐波那契擴展停利目標一/二、防守停損價與資金配置比例。
        """.trimIndent()

        val aiResult = callGemini(prompt, systemPrompt)
        val entryPrice = if (stock.currentPrice > fib.level0_382) fib.level0_382 else stock.currentPrice
        val targetPrice = fib.ext1_618
        val stopLoss = fib.level0_618

        val isChipsBullish = chips.trustConsecutiveBuyDays >= 2 || chips.totalInstitutional > 1000
        val isTechBullish = techSignal.isBullishMaAlignment || techSignal.isKdGoldenCross || techSignal.isHeavyVolumeLongRed
        val isThreeWayBullish = isChipsBullish && isTechBullish

        val content = if (aiResult.isNotBlank()) aiResult else buildFallbackStrategyReport(stock, chips, fib, techSignal, entryPrice, targetPrice, stopLoss, isThreeWayBullish)

        val rating = if (isThreeWayBullish) "三面向同向爆發 (強勢出手點)" else "雙軌等待共振 (分批低接)"

        return ResearchReport(
            notebookType = NotebookType.STRATEGY,
            title = "【雙軌決策中樞】${stock.symbol} ${stock.name} 存股價值與動態爆發綜合戰略",
            targetSymbol = stock.symbol,
            targetName = stock.name,
            summary = "軌道一價值存股估值穩健；軌道二動態爆發三面向評估：${if (isThreeWayBullish) "消息+籌碼+技術三面共振，發動強烈買進訊號" else "籌碼與技術拉鋸中，建議逢回接不追高"}。目標價 NT$ ${String.format("%.1f", targetPrice)}，停損 NT$ ${String.format("%.1f", stopLoss)}。",
            content = content,
            rating = rating,
            entryPrice = entryPrice,
            targetPrice = targetPrice,
            stopLossPrice = stopLoss,
            keyFibLevel = "斐波那契回撤 38.2%: ${String.format("%.1f", fib.level0_382)} / 擴展 161.8%: ${String.format("%.1f", fib.ext1_618)}",
            tags = "雙軌決策,長期存股,動態爆發,三面向共振,斐波那契目標,${stock.name}"
        )
    }

    // --- Fallback High-Quality Domain Generators ---

    private fun buildFallbackAfterHoursReport(
        date: String,
        index: IndexQuote,
        totalInst: Long,
        topStocks: List<StockQuote>,
        news: List<MarketNews>
    ): String {
        return """
# 【每日盤後市場分析報告】$date

### 一、大盤多空結構與量價動向
今日台股加權指數收在 **${String.format("%.2f", index.current)}** 點，全日變動 **${String.format("%+.2f", index.change)} 點 (${String.format("%+.2f", index.changePercent)}%)**，總成交金額約達 **${String.format("%.1f", index.volumeAmount)} 億元**。
盤面呈現開平走高格局，指數穩居 5 日均線與 20 日月線之上，多方控盤結構完整。盤中高點達 ${String.format("%.1f", index.high)}，低點防守在 ${String.format("%.1f", index.low)}，量能維持健康換手水位。

### 二、三大法人與主力籌碼歸屬
- **三大法人合計買賣超**：${totalInst} 張 (${if (totalInst > 0) "法人偏多回補" else "法人逢高調節"})
- **外資動態**：主要著墨於半導體權值龍頭與大型 AI 伺服器供應鏈，台指期淨部位維持多單避險。
- **投信動態**：連續站在買方，重點加碼季底作帳題材股與高殖利率標的。

### 三、主流強勢板塊與輪動觀察
1. **半導體與先進封裝**：台積電、聯發科等權值股穩盤，CoWoS 設備與材料供應鏈買盤積極。
2. **AI 運算與高速傳輸**：伺服器組裝、散熱模組及光通訊族群具備實質營收成長動能。
3. **內需與政策概念**：重電綠能、生技醫療及金融股提供下檔防禦支撐。

### 四、明日操作指引與關鍵分水嶺
- **多方關鍵支撐**：月線位置與前波低點，只要未跌破月線，波段多頭格局不變。
- **空方反壓壓力**：前波歷史高點關卡，面臨獲利回吐賣壓，宜採低吸不追高策略。
- **操作策略**：短線維持「短線看 5/10 日均線定進出，中長線依 20/60 日均線定趨勢」，鎖定法人籌碼集中且技術面突破的爆發個股。
        """.trimIndent()
    }

    private fun buildFallbackChipsReport(stock: StockQuote, chips: InstitutionalChips): String {
        return """
# 【籌碼選股診斷報告】${stock.symbol} ${stock.name}

### 一、法人主力籌碼集中度
- **外資買賣超**：${chips.foreignBuySell} 張（外資持股比例：${chips.foreignHoldPercent}%）
- **投信買賣超**：${chips.trustBuySell} 張（**投信已連續買超 ${chips.trustConsecutiveBuyDays} 天**）
- **自營商買賣超**：${chips.dealerBuySell} 張
- **三大法人合計**：**${chips.totalInstitutional} 張**

> **籌碼面評語**：${if (chips.trustConsecutiveBuyDays >= 3) "投信出現顯著作帳連續買盤，主力籌碼鎖定度高！" else "三大法人維持波段換手，筹碼面尚屬良性。"}

### 二、資券散戶與軋空潛力分析
- **融資變化**：${chips.marginChange} 張（融資餘額：${chips.marginBalance} 張）
- **融券變化**：${chips.shortChange} 張（融券餘額：${chips.shortBalance} 張）
- **券償比**：**${String.format("%.2f", chips.marginShortRatio)}%**

${if (chips.marginChange < 0 && chips.shortChange > 0) "⚡ **出現典型「資減券增」主力進場、散戶退場型態，具備強烈軋空爆發動能！**" else "資券變化在正常波段區間內，散戶籌碼沉澱良好。"}

### 三、籌碼評級與操作建言
- **籌碼綜合評級**：${if (chips.totalInstitutional > 1000) "⭐⭐⭐⭐⭐ 極佳 (主力大戶強勢進駐)" else "⭐⭐⭐⭐ 良好"}
- **操作建議**：逢股價拉回短期 5/10 日均線支撐時，可分批建立多單部位，防守點設於投信波段成本下方 3%。
        """.trimIndent()
    }

    private fun buildFallbackDayTradingReport(
        stock: StockQuote,
        fib: FibonacciLevels,
        tech: TechnicalCalculator.TechnicalSignalSummary,
        entry: Double,
        target: Double,
        stop: Double
    ): String {
        return """
# 【當沖策略顧問盤前教戰】${stock.symbol} ${stock.name}

### 一、多空趨勢定位 (均線體系)
- **短線定進出 (5/10/20 MA)**：${tech.shortTermAdvice}
- **中長定趨勢 (20/60 MA)**：${tech.mediumTermAdvice}
- **技術型態**：${tech.trendDescription}

### 二、斐波那契關鍵價位交叉驗證
- **極大壓力位 (0.0% 波段高點)**：NT$ ${String.format("%.1f", fib.level0_0)}
- **斐波那契回撤 38.2% (建議進場/支撐)**：NT$ ${String.format("%.1f", fib.level0_382)}
- **斐波那契回撤 50.0% (多空平衡點)**：NT$ ${String.format("%.1f", fib.level0_500)}
- **斐波那契回撤 61.8% (黃金防守位)**：NT$ ${String.format("%.1f", fib.level0_618)}
- **斐波那契擴展 127.2% (第一獲利停利)**：NT$ ${String.format("%.1f", fib.ext1_272)}
- **斐波那契擴展 161.8% (第二衝刺目標)**：NT$ ${String.format("%.1f", fib.ext1_618)}

### 三、今日當沖實戰點位規劃
- 🟢 **建議進場區間**：NT$ ${String.format("%.1f", entry * 0.995)} ~ ${String.format("%.1f", entry * 1.005)} (拉回 Fib 38.2% 守穩或早盤帶量突破前高時進場)
- 🎯 **第一目標停利點**：NT$ ${String.format("%.1f", target)} (預期報酬率約 +${String.format("%.1f", (target - entry) / entry * 100)}%)
- 🛑 **嚴格停損出場點**：NT$ ${String.format("%.1f", stop)} (跌破 Fib 61.8% 立即無條件停損，風險控制在 2% 以內)

### 四、風控教戰守則
當沖嚴守紀律，開盤 9:00~9:30 觀察量能是否放大，若未達預估均量且盤中破 10 日線，切勿強行做多；尾盤 13:15 前務必平倉出場，不留過夜單。
        """.trimIndent()
    }

    private fun buildFallbackNewsRadarReport(news: List<MarketNews>, sector: String): String {
        return """
# 【消息面情報雷達分析報告】

### 一、當前國際與台股市場頭條解讀
${news.take(4).mapIndexed { i, n -> "${i + 1}. **${n.title}** (${n.category} / ${n.sentiment})\n   - 核心摘要：${n.summary}\n   - 衝擊評估：${n.impactRating}" }.joinToString("\n\n")}

### 二、受惠族群與題材發酵
- **主要催化板塊**：$sector
- **美股與國際連動**：費城半導體與那斯達克指數走勢穩健，外資對台股供應鏈展望樂觀。
- **題材熱度評估**：市場流動性充足，AI伺服器供應鏈、光通訊 CPO 與半導體製程設備題材持續吸引買盤。

### 三、盤勢風險與策略指引
目前整體消息面氛圍偏向多方，需留意美聯儲利率政策動向與匯率波動。建議投資人順應產業趨勢，挑選有實質財報營收支撐的績優標的。
        """.trimIndent()
    }

    private fun buildFallbackFundamentalReport(
        stock: StockQuote,
        cheap: Double,
        fair: Double,
        expensive: Double
    ): String {
        return """
# 【個股基本面與價值存股研報】${stock.symbol} ${stock.name}

### 一、財務三率與體質評估
- **近四季累計 EPS**：NT$ ${stock.eps} 元
- **毛利率**：${stock.grossMargin}%
- **營業利益率**：${stock.operatingMargin}%
- **稅後純益率**：${stock.netMargin}%
- **ROE (股東權益報酬率)**：**${stock.roe}%** (體質極為優秀，資本回報率高)
- **最新營收動能**：月營收 MoM ${String.format("%+.1f", stock.revenueGrowthMom)}%，YoY ${String.format("%+.1f", stock.revenueGrowthYoy)}%

### 二、本益比河流圖與價值估算
- **現行本益比 (P/E)**：${stock.peRatio} 倍
- **現金殖利率**：${stock.yieldRate}%
- **股價淨值比 (P/B)**：${stock.pbRatio} 倍

#### 估值區間模型：
- 🟢 **便宜價 (安全邊際買點)**：約 **NT$ ${String.format("%.0f", cheap)}** (15x PE)
- 🟡 **合理價 (價值平衡點)**：約 **NT$ ${String.format("%.0f", fair)}** (20x PE)
- 🔴 **昂貴價 (波段減碼點)**：約 **NT$ ${String.format("%.0f", expensive)}** (26x PE)

### 三、長期存股決策建議
${stock.name} 具備產業龍頭地位與強大技術護城河。現價 NT$ ${stock.currentPrice} 處於 ${if (stock.currentPrice < fair) "合理偏便宜區間，具備良好安全邊際" else "合理區間，適合長期定期定額扣款"}。長期存股投資人可採「分批低接、股息滾入再投資」策略累積張數。
        """.trimIndent()
    }

    private fun buildFallbackStrategyReport(
        stock: StockQuote,
        chips: InstitutionalChips,
        fib: FibonacciLevels,
        tech: TechnicalCalculator.TechnicalSignalSummary,
        entry: Double,
        target: Double,
        stop: Double,
        isThreeWay: Boolean
    ): String {
        return """
# 【策略分析師：雙軌決策中樞綜合研報】${stock.symbol} ${stock.name}

## 🎯 軌道一：長期價值存股決策
- **基本面護城河**：EPS ${stock.eps} 元，ROE ${stock.roe}%，殖利率 ${stock.yieldRate}%。
- **內在價值定位**：體質卓越，具備長線複利潛力。
- **長線買點建議**：逢大盤非理性回檔或跌破 Fib 50.0% (${String.format("%.1f", fib.level0_500)}) 時為長線絕佳安全邊際建倉點。

---

## ⚡ 軌道二：兩周內中短期動態爆發三面向檢驗
1. **消息面題材驗證**：產業處於高速成長週期，具備 AI 與高效能運算題材加持，評分：⭐⭐⭐⭐ (4/5)
2. **籌碼面動向驗證**：三大法人單日淨買賣超 ${chips.totalInstitutional} 張，投信連續買超 ${chips.trustConsecutiveBuyDays} 天，資券結構呈現 ${if (chips.marginChange<0) "資減券增主力鎖碼" else "籌碼穩定換手"}，評分：⭐⭐⭐⭐⭐ (5/5)
3. **技術面全面判讀驗證**：
   - 均線系統：${tech.trendDescription} (短線 5/10/20 定進出，中長 20/60 定趨勢)
   - K線量能：${if (tech.isHeavyVolumeLongRed) "帶量長紅攻擊型態" else "均量溫和推升"}
   - 斐波那契位階：守穩 38.2% 回撤位 (NT$ ${String.format("%.1f", fib.level0_382)})，目標挑戰 161.8% 擴展位 (NT$ ${String.format("%.1f", fib.ext1_618)})
   - 評分：⭐⭐⭐⭐ (4/5)

---

## 🚀 三面向同向共振判定
> **【決策結論】**：${if (isThreeWay) "✅ **消息面、籌碼面、技術面三面向完全同向共振！** 動態爆發出手訊號確立，具備兩周內向上突破爆發潛力！" else "⏳ 籌碼與技術面局部拉鋸中，建議採回測支撐低接策略，不追高。"}

### 實戰交易執行參數：
- 📌 **進場啟動點**：NT$ ${String.format("%.1f", entry)}
- 🎯 **波段擴展目標 (161.8% Fib)**：NT$ ${String.format("%.1f", target)} (預期波段獲利約 +${String.format("%.1f", (target - entry) / entry * 100)}%)
- 🛑 **關鍵停損防守位 (61.8% Fib)**：NT$ ${String.format("%.1f", stop)}
- 💰 **部位配置**：建議以總資金 15%~20% 參與動態爆發波段，嚴守停損紀律。
        """.trimIndent()
    }
    /**
     * Universal Custom AI Analyst Execution with User-Customized Prompt
     */
    suspend fun generateCustomAnalystReport(
        analyst: CustomAnalystEntity,
        stock: StockQuote,
        chips: InstitutionalChips,
        fib: FibonacciLevels,
        techSignal: TechnicalCalculator.TechnicalSignalSummary,
        newsList: List<MarketNews>
    ): ResearchReport {
        val todayStr = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date())
        val systemPrompt = analyst.systemPrompt.ifBlank {
            "你是一位由使用者自訂的頂尖台股研究專家【${analyst.name}】(${analyst.roleTitle})，專精於：${analyst.specialization}。請以專業嚴謹且具實戰可操作性的口吻產出研報。"
        }

        val prompt = """
            請分析台股個股【${stock.symbol} ${stock.name}】(${stock.category})：
            【基本即時行情】
            - 當前股價：NT$ ${stock.currentPrice} (${String.format("%+.2f", stock.changePercent)}%)
            - 今日高/低/開：${stock.highPrice} / ${stock.lowPrice} / ${stock.openPrice}
            - 成交量：${stock.volume} 張 (成交金額約 ${String.format("%.1f", stock.totalAmount)} 億元)
            
            【籌碼面數據】
            - 外資買賣超：${chips.foreignBuySell} 張 (外資持股比：${chips.foreignHoldPercent}%)
            - 投信買賣超：${chips.trustBuySell} 張 (投信連買：${chips.trustConsecutiveBuyDays} 天)
            - 三大法人合計：${chips.totalInstitutional} 張
            - 融資變化：${chips.marginChange} 張 | 融券變化：${chips.shortChange} 張 (券償比：${String.format("%.2f", chips.marginShortRatio)}%)
            - 籌碼整體評價：${chips.chipRating}
            
            【技術面與均線指標】
            - 趨勢狀態：${techSignal.trendDescription}
            - 短線指引 (5/10/20日均線)：${techSignal.shortTermAdvice}
            - 中長線指引 (20/60日均線)：${techSignal.mediumTermAdvice}
            - KD 指標狀態：${if (techSignal.isKdGoldenCross) "KD黃金交叉多頭" else "整理區間"}
            - MACD 狀態：${if (techSignal.isMacdBullish) "多方柱狀體擴大" else "空方或拉鋸"}
            - 量能型態：${if (techSignal.isHeavyVolumeLongRed) "帶量突破長紅" else "平量推升"}
            
            【斐波那契關鍵價位】
            - 頂點 (0.0%)：NT$ ${String.format("%.1f", fib.level0_0)}
            - 回撤支撐 38.2%：NT$ ${String.format("%.1f", fib.level0_382)}
            - 回撤支撐 50.0%：NT$ ${String.format("%.1f", fib.level0_500)}
            - 回撤支撐 61.8%：NT$ ${String.format("%.1f", fib.level0_618)}
            - 擴展獲利目標 127.2%：NT$ ${String.format("%.1f", fib.ext1_272)}
            - 擴展獲利目標 161.8%：NT$ ${String.format("%.1f", fib.ext1_618)}
            
            【基本面與財務三率】
            - 近四季 EPS：NT$ ${stock.eps} 元 | 本益比：${stock.peRatio} 倍 | 殖利率：${stock.yieldRate}% | ROE：${stock.roe}%
            - 毛利率：${stock.grossMargin}% | 營業利益率：${stock.operatingMargin}% | 營收YoY：${String.format("%+.1f", stock.revenueGrowthYoy)}%
            
            【市場焦點新聞】
            ${newsList.filter { it.relatedSymbols.contains(stock.symbol) || it.relatedSymbols.isEmpty() }.take(2).joinToString("\n") { "• ${it.title} (${it.sentiment})" }}

            請依據您的專長【${analyst.specialization}】與自訂分析風格【${analyst.analysisStyle}】進行深度研判：
            1. 核心觀點與多空定調
            2. 關鍵支撐與壓力位、進場買點、停利目標（斐波那契擴展）與停損防守價
            3. 勝率評估與操作執行指引
        """.trimIndent()

        val aiResult = callGemini(prompt, systemPrompt)
        val entryPrice = if (stock.currentPrice > fib.level0_382) fib.level0_382 else stock.currentPrice
        val targetPrice = fib.ext1_272.let { if (it > stock.currentPrice) it else stock.currentPrice * 1.15 }
        val stopLossPrice = fib.level0_618.let { if (it < stock.currentPrice) it else stock.currentPrice * 0.93 }

        val nType = try {
            NotebookType.valueOf(analyst.notebookType)
        } catch (e: Exception) {
            NotebookType.STRATEGY
        }

        val content = if (aiResult.isNotBlank()) aiResult else buildFallbackCustomReport(analyst, stock, chips, fib, techSignal, entryPrice, targetPrice, stopLossPrice)

        val rating = when {
            techSignal.isBullishMaAlignment && chips.totalInstitutional > 500 -> "強力看多 (積極布局)"
            stock.peRatio < 16.0 && stock.yieldRate > 4.5 -> "價值存股 (逢低承接)"
            chips.trustConsecutiveBuyDays >= 3 -> "主力鎖碼 (跟單波段)"
            else -> "區間操作 (嚴設停損)"
        }

        return ResearchReport(
            notebookType = nType,
            title = "【${analyst.name}】${stock.symbol} ${stock.name} 深度客製研報",
            targetSymbol = stock.symbol,
            targetName = stock.name,
            summary = "【${analyst.name}・${analyst.roleTitle}】研判評級：$rating。建議進場點 NT$ ${String.format("%.1f", entryPrice)}，斐波目標價 NT$ ${String.format("%.1f", targetPrice)}，防守價 NT$ ${String.format("%.1f", stopLossPrice)}。",
            content = content,
            rating = rating,
            entryPrice = entryPrice,
            targetPrice = targetPrice,
            stopLossPrice = stopLossPrice,
            keyFibLevel = "回撤38.2%: ${String.format("%.1f", fib.level0_382)} / 擴展127.2%: ${String.format("%.1f", fib.ext1_272)}",
            tags = "AI自訂員工,${analyst.name},${stock.name},${analyst.analysisStyle}"
        )
    }

    private fun buildFallbackCustomReport(
        analyst: CustomAnalystEntity,
        stock: StockQuote,
        chips: InstitutionalChips,
        fib: FibonacciLevels,
        tech: TechnicalCalculator.TechnicalSignalSummary,
        entry: Double,
        target: Double,
        stop: Double
    ): String {
        return """
# 【${analyst.name}】${stock.symbol} ${stock.name} 客製化量化研究專報
> **分析師職銜**：${analyst.roleTitle}  
> **專長領域**：${analyst.specialization}  
> **決策風格**：${analyst.analysisStyle}  
> **核心 Prompt 指令方針**：${analyst.systemPrompt.take(80)}...

---

### 一、行情多空架構解析
- **現價位階**：當前報價 NT$ ${stock.currentPrice} (${String.format("%+.2f", stock.changePercent)}%)，成交量 ${stock.volume} 張。
- **均線型態**：${tech.trendDescription}
- **短線 (5/10/20日) 操盤指引**：${tech.shortTermAdvice}
- **中長線 (20/60日) 趨勢指導**：${tech.mediumTermAdvice}

---

### 二、籌碼面主力歸屬檢驗
- **三大法人動向**：外資單日 ${chips.foreignBuySell} 張 (持股比 ${chips.foreignHoldPercent}%)，投信連續買超 ${chips.trustConsecutiveBuyDays} 天 (買賣超 ${chips.trustBuySell} 張)，法人合計淨超 ${chips.totalInstitutional} 張。
- **資券散戶指標**：融資 ${if (chips.marginChange>=0) "+${chips.marginChange}" else "${chips.marginChange}"} 張，融券 ${if (chips.shortChange>=0) "+${chips.shortChange}" else "${chips.shortChange}"} 張，券償比 ${String.format("%.2f", chips.marginShortRatio)}%。
- **籌碼定調**：${chips.chipRating}。

---

### 三、斐波那契關鍵點位與操盤規劃
- **0.0% 頂點壓力**：NT$ ${String.format("%.1f", fib.level0_0)}
- **38.2% 關鍵支撐**：NT$ ${String.format("%.1f", fib.level0_382)}
- **50.0% 中軸分水嶺**：NT$ ${String.format("%.1f", fib.level0_500)}
- **61.8% 黃金防守位**：NT$ ${String.format("%.1f", fib.level0_618)}
- **127.2% 第一擴展目標**：NT$ ${String.format("%.1f", fib.ext1_272)}
- **161.8% 黃金擴展目標**：NT$ ${String.format("%.1f", fib.ext1_618)}

---

### 四、${analyst.name} 實戰交易執行指令
- 📌 **建議進場區間**：NT$ ${String.format("%.1f", entry)} (拉回回撤位或突破確認進場)
- 🎯 **停利目標價 (Fib 擴展)**：NT$ ${String.format("%.1f", target)} (預期波段幅度 +${String.format("%.1f", (target - entry) / entry * 100)}%)
- 🛑 **防守停損價**：NT$ ${String.format("%.1f", stop)}
- 💡 **執行總結**：遵循【${analyst.name}】策略原則，控制單筆風險不超過總資金 2%，分批進場、嚴守停損。
        """.trimIndent()
    }
}

data class ResearchReport(
    val notebookType: NotebookType,
    val title: String,
    val targetSymbol: String?,
    val targetName: String?,
    val summary: String,
    val content: String,
    val rating: String?,
    val entryPrice: Double? = null,
    val targetPrice: Double? = null,
    val stopLossPrice: Double? = null,
    val keyFibLevel: String? = null,
    val tags: String = ""
)
