package com.example.domain.calculator

import com.example.data.model.KLinePoint
import kotlin.math.max
import kotlin.math.min

object TechnicalCalculator {

    /**
     * Compute all technical indicators (MA5/10/20/60, VMA, MACD, KD) on the K-line list
     */
    fun enrichIndicators(points: List<KLinePoint>): List<KLinePoint> {
        if (points.isEmpty()) return emptyList()

        val result = points.map { it.copy() }

        // 1. Moving Averages (MA5, MA10, MA20, MA60)
        for (i in result.indices) {
            if (i >= 4) {
                result[i].ma5 = result.subList(i - 4, i + 1).map { it.close }.average()
            }
            if (i >= 9) {
                result[i].ma10 = result.subList(i - 9, i + 1).map { it.close }.average()
            }
            if (i >= 19) {
                result[i].ma20 = result.subList(i - 19, i + 1).map { it.close }.average()
            }
            if (i >= 59) {
                result[i].ma60 = result.subList(i - 59, i + 1).map { it.close }.average()
            }

            // Volume MA
            if (i >= 4) {
                result[i].vma5 = result.subList(i - 4, i + 1).map { it.volume.toDouble() }.average()
            }
            if (i >= 19) {
                result[i].vma20 = result.subList(i - 19, i + 1).map { it.volume.toDouble() }.average()
            }
        }

        // 2. MACD (EMA12, EMA26, DIF, MACD 9-day EMA, OSC)
        var ema12 = result.first().close
        var ema26 = result.first().close
        var macdSignal = 0.0

        val k12 = 2.0 / (12 + 1)
        val k26 = 2.0 / (26 + 1)
        val k9 = 2.0 / (9 + 1)

        for (i in result.indices) {
            val close = result[i].close
            ema12 = close * k12 + ema12 * (1 - k12)
            ema26 = close * k26 + ema26 * (1 - k26)
            val dif = ema12 - ema26
            result[i].dif = dif

            macdSignal = if (i == 0) dif else dif * k9 + macdSignal * (1 - k9)
            result[i].macd = macdSignal
            result[i].osc = dif - macdSignal
        }

        // 3. KD (9-day Stochastic: RSV = (Close - MinLow9) / (MaxHigh9 - MinLow9) * 100)
        var prevK = 50.0
        var prevD = 50.0

        for (i in result.indices) {
            val startIdx = max(0, i - 8)
            val sub = result.subList(startIdx, i + 1)
            var highest = Double.MIN_VALUE
            var lowest = Double.MAX_VALUE

            for (p in sub) {
                if (p.high > highest) highest = p.high
                if (p.low < lowest) lowest = p.low
            }

            val rsv = if (highest > lowest) {
                (result[i].close - lowest) / (highest - lowest) * 100.0
            } else {
                50.0
            }

            val currentK = (2.0 / 3.0) * prevK + (1.0 / 3.0) * rsv
            val currentD = (2.0 / 3.0) * prevD + (1.0 / 3.0) * currentK

            result[i].k = currentK
            result[i].d = currentD

            prevK = currentK
            prevD = currentD
        }

        return result
    }

    /**
     * Technical Signal Summary
     */
    data class TechnicalSignalSummary(
        val isBullishMaAlignment: Boolean, // 5>10>20>60 多頭排列
        val isKdGoldenCross: Boolean,      // KD 黃金交叉
        val isKdDeathCross: Boolean,
        val isMacdBullish: Boolean,        // MACD DIF > 0 & OSC > 0
        val isHeavyVolumeLongRed: Boolean, // 帶量長紅
        val trendDescription: String,
        val shortTermAdvice: String,       // 5/10/20 定進出
        val mediumTermAdvice: String       // 20/60 定趨勢
    )

    fun getSignalSummary(points: List<KLinePoint>, currentPrice: Double = 0.0): TechnicalSignalSummary {
        return evaluateSignals(points)
    }

    fun evaluateSignals(points: List<KLinePoint>): TechnicalSignalSummary {
        if (points.size < 5) {
            return TechnicalSignalSummary(
                isBullishMaAlignment = false,
                isKdGoldenCross = false,
                isKdDeathCross = false,
                isMacdBullish = false,
                isHeavyVolumeLongRed = false,
                trendDescription = "資料收集中",
                shortTermAdvice = "觀望等待型態成形",
                mediumTermAdvice = "多看少做"
            )
        }

        val last = points.last()
        val prev = points[points.size - 2]

        val ma5 = last.ma5 ?: last.close
        val ma10 = last.ma10 ?: last.close
        val ma20 = last.ma20 ?: last.close
        val ma60 = last.ma60 ?: last.close

        val isBullishMaAlignment = ma5 >= ma10 && ma10 >= ma20 && ma20 >= ma60
        val isKdGoldenCross = (prev.k ?: 50.0) <= (prev.d ?: 50.0) && (last.k ?: 50.0) > (last.d ?: 50.0)
        val isKdDeathCross = (prev.k ?: 50.0) >= (prev.d ?: 50.0) && (last.k ?: 50.0) < (last.d ?: 50.0)
        val isMacdBullish = (last.dif ?: 0.0) > 0 && (last.osc ?: 0.0) > 0

        // 帶量長紅: 實體陽線 > 2.5% 且成交量 > 1.5倍 5日均量
        val vma5 = last.vma5 ?: last.volume.toDouble()
        val priceGain = (last.close - last.open) / last.open * 100.0
        val isHeavyVolumeLongRed = priceGain >= 2.5 && last.volume >= vma5 * 1.5

        val trendDesc = when {
            isBullishMaAlignment && isMacdBullish -> "多頭強烈發散 (均線全面多頭排列，動能強勁)"
            isBullishMaAlignment -> "偏多格局 (站穩短期與月季均線)"
            last.close < ma20 && ma20 < ma60 -> "空頭整理格局 (均線空頭排列，跌破月線防守)"
            else -> "震盪整理洗盤 (在月線與季線間拉鋸)"
        }

        val shortAdvice = when {
            last.close > ma5 && ma5 > ma10 -> "短線 5/10 均線向上：沿 5 日線偏多操作，跌破 10 日線減碼"
            last.close < ma5 && last.close > ma20 -> "短線跌破 5 日線：注意獲利了結或等待回測 10/20 日線支撐"
            else -> "短線跌破 20 日月線：嚴格執行停損停利，暫停當沖多單"
        }

        val mediumAdvice = when {
            ma20 > ma60 && last.close > ma20 -> "中長線 20/60 均線黃金向上：中長線大多頭趨勢確立，波段持有"
            ma20 < ma60 && last.close < ma20 -> "中長線 20/60 均線死亡交叉：中長趨勢向下，逢反彈降低持股"
            else -> "中長線 20/60 糾結：趨勢方向待確認，等待量能表態突破"
        }

        return TechnicalSignalSummary(
            isBullishMaAlignment = isBullishMaAlignment,
            isKdGoldenCross = isKdGoldenCross,
            isKdDeathCross = isKdDeathCross,
            isMacdBullish = isMacdBullish,
            isHeavyVolumeLongRed = isHeavyVolumeLongRed,
            trendDescription = trendDesc,
            shortTermAdvice = shortAdvice,
            mediumTermAdvice = mediumAdvice
        )
    }
}
