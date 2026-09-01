package com.example.domain.calculator

import com.example.data.model.FibonacciLevels
import com.example.data.model.KLinePoint
import kotlin.math.max
import kotlin.math.min

object FibonacciCalculator {

    /**
     * Compute Fibonacci Retracement and Extension levels from a list of K-line points
     */
    fun calculate(kLines: List<KLinePoint>, lookback: Int = 60): FibonacciLevels {
        if (kLines.isEmpty()) {
            return FibonacciLevels(0.0, 0.0)
        }

        val slice = if (kLines.size > lookback) kLines.takeLast(lookback) else kLines
        var maxHigh = Double.MIN_VALUE
        var minLow = Double.MAX_VALUE
        var maxIndex = 0
        var minIndex = 0

        slice.forEachIndexed { index, point ->
            if (point.high > maxHigh) {
                maxHigh = point.high
                maxIndex = index
            }
            if (point.low < minLow) {
                minLow = point.low
                minIndex = index
            }
        }

        val isUptrend = minIndex <= maxIndex
        val range = maxHigh - minLow

        val level0 = maxHigh
        val level236 = maxHigh - 0.236 * range
        val level382 = maxHigh - 0.382 * range
        val level500 = maxHigh - 0.500 * range
        val level618 = maxHigh - 0.618 * range
        val level786 = maxHigh - 0.786 * range
        val level100 = minLow

        // Extensions
        val ext1272 = minLow + 1.272 * range
        val ext1618 = minLow + 1.618 * range
        val ext2000 = minLow + 2.000 * range
        val ext2618 = minLow + 2.618 * range

        // Classic Pivot Points
        val last = kLines.last()
        val pivot = (last.high + last.low + last.close) / 3.0
        val r1 = 2 * pivot - last.low
        val s1 = 2 * pivot - last.high
        val r2 = pivot + (last.high - last.low)
        val s2 = pivot - (last.high - last.low)

        return FibonacciLevels(
            swingHigh = maxHigh,
            swingLow = minLow,
            isUptrend = isUptrend,
            level0_0 = level0,
            level0_236 = level236,
            level0_382 = level382,
            level0_500 = level500,
            level0_618 = level618,
            level0_786 = level786,
            level1_000 = level100,
            ext1_272 = ext1272,
            ext1_618 = ext1618,
            ext2_000 = ext2000,
            ext2_618 = ext2618,
            resistance1 = max(r1, level236),
            resistance2 = max(r2, level0),
            support1 = min(s1, level382),
            support2 = min(s2, level618),
            pivotPoint = pivot
        )
    }

    /**
     * Check if a given price is near a critical Fibonacci level (within tolerance %)
     */
    fun findNearestFibLevel(price: Double, fib: FibonacciLevels, tolerancePct: Double = 1.0): String? {
        val levels = listOf(
            "回撤 0.0% (高點反壓)" to fib.level0_0,
            "回撤 23.6% (強勢回檔位)" to fib.level0_236,
            "回撤 38.2% (關鍵支撐位)" to fib.level0_382,
            "回撤 50.0% (多空平衡中軸)" to fib.level0_500,
            "回撤 61.8% (黃金分割強支撐)" to fib.level0_618,
            "回撤 78.6% (極限防守位)" to fib.level0_786,
            "回撤 100% (波段起漲底)" to fib.level1_000,
            "擴展 127.2% (突破目標一)" to fib.ext1_272,
            "擴展 161.8% (黃金擴展目標二)" to fib.ext1_618,
            "擴展 200.0% (波段翻倍目標三)" to fib.ext2_000,
            "擴展 261.8% (超強狂牛目標四)" to fib.ext2_618
        )

        for ((name, levelPrice) in levels) {
            val diffPct = kotlin.math.abs(price - levelPrice) / levelPrice * 100
            if (diffPct <= tolerancePct) {
                return "$name (約 NT$ ${String.format("%.1f", levelPrice)})"
            }
        }
        return null
    }
}
