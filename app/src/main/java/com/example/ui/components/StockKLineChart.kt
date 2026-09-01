package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FibonacciLevels
import com.example.data.model.KLinePoint
import com.example.domain.calculator.FibonacciCalculator
import com.example.ui.theme.*
import kotlin.math.max
import kotlin.math.min

enum class SubChartType(val label: String) {
    VOLUME("量能 (VOL)"),
    MACD("MACD指標"),
    KD("KD指標")
}

@Composable
fun StockKLineChart(
    kLines: List<KLinePoint>,
    modifier: Modifier = Modifier,
    initialFibLevels: FibonacciLevels? = null
) {
    if (kLines.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(StockSurfaceVariantDark, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("載入技術圖表中...", color = TextSecondaryDark)
        }
        return
    }

    var showMA by remember { mutableStateOf(true) }
    var showFibonacci by remember { mutableStateOf(true) }
    var showSupportResistance by remember { mutableStateOf(true) }
    var subChart by remember { mutableStateOf(SubChartType.VOLUME) }
    var touchIndex by remember { mutableStateOf<Int?>(null) }

    val fibLevels = remember(kLines) {
        initialFibLevels ?: FibonacciCalculator.calculate(kLines)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("kline_chart_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Chart Control Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = showMA,
                        onClick = { showMA = !showMA },
                        label = { Text("均線", fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = showFibonacci,
                        onClick = { showFibonacci = !showFibonacci },
                        label = { Text("斐波那契", fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = showSupportResistance,
                        onClick = { showSupportResistance = !showSupportResistance },
                        label = { Text("支撐壓力", fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }

                // Sub chart selector
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SubChartType.values().forEach { type ->
                        SuggestionChip(
                            onClick = { subChart = type },
                            label = {
                                Text(
                                    type.label.take(4),
                                    fontSize = 10.sp,
                                    fontWeight = if (subChart == type) FontWeight.Bold else FontWeight.Normal,
                                    color = if (subChart == type) StockPrimary else TextSecondaryDark
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (subChart == type) StockSurfaceVariantDark else Color.Transparent
                            ),
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }
            }

            // Indicator Header Info
            val activePoint = if (touchIndex != null && touchIndex!! in kLines.indices) {
                kLines[touchIndex!!]
            } else {
                kLines.last()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("日期: ${activePoint.dateStr}", fontSize = 11.sp, color = TextPrimaryDark, fontWeight = FontWeight.SemiBold)
                    Text("開: ${activePoint.open}", fontSize = 11.sp, color = TextSecondaryDark)
                    Text("高: ${activePoint.high}", fontSize = 11.sp, color = StockUpRed)
                    Text("低: ${activePoint.low}", fontSize = 11.sp, color = StockDownGreen)
                    Text("收: ${activePoint.close}", fontSize = 11.sp, color = if (activePoint.close >= activePoint.open) StockUpRed else StockDownGreen, fontWeight = FontWeight.Bold)
                }
            }

            if (showMA) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    activePoint.ma5?.let { Text("MA5: ${String.format("%.1f", it)}", fontSize = 10.sp, color = MA5Color) }
                    activePoint.ma10?.let { Text("MA10: ${String.format("%.1f", it)}", fontSize = 10.sp, color = MA10Color) }
                    activePoint.ma20?.let { Text("MA20: ${String.format("%.1f", it)}", fontSize = 10.sp, color = MA20Color) }
                    activePoint.ma60?.let { Text("MA60: ${String.format("%.1f", it)}", fontSize = 10.sp, color = MA60Color) }
                }
            }

            // Main Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(Color(0xFF0D1322), RoundedCornerShape(8.dp))
                    .pointerInput(kLines) {
                        detectTapGestures(
                            onPress = { offset ->
                                val count = kLines.size
                                val itemWidth = size.width / count
                                val idx = (offset.x / itemWidth).toInt().coerceIn(0, count - 1)
                                touchIndex = idx
                            },
                            onTap = { touchIndex = null }
                        )
                    }
                    .pointerInput(kLines) {
                        detectDragGestures(
                            onDrag = { change, _ ->
                                val count = kLines.size
                                val itemWidth = size.width / count
                                val idx = (change.position.x / itemWidth).toInt().coerceIn(0, count - 1)
                                touchIndex = idx
                            },
                            onDragEnd = { /* keep last or clear on tap */ }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val mainHeight = size.height * 0.68f
                    val subHeight = size.height * 0.28f
                    val subTop = size.height * 0.72f
                    val count = kLines.size
                    if (count < 2) return@Canvas

                    val candleWidth = (size.width / count) * 0.65f
                    val stepX = size.width / count

                    // Find min/max for main chart
                    var minPrice = kLines.minOf { it.low } * 0.985
                    var maxPrice = kLines.maxOf { it.high } * 1.015

                    if (showFibonacci) {
                        minPrice = min(minPrice, fibLevels.level1_000 * 0.98)
                        maxPrice = max(maxPrice, fibLevels.ext1_272 * 1.02)
                    }

                    val priceRange = max(1.0, maxPrice - minPrice)

                    fun priceToY(price: Double): Float {
                        return (mainHeight - ((price - minPrice) / priceRange * mainHeight)).toFloat()
                    }

                    // 1. Draw Grid Lines
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = (mainHeight / gridLines) * i
                        drawLine(
                            color = Color(0x1FFFFFFF),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                        val p = maxPrice - (priceRange / gridLines) * i
                        drawContext.canvas.nativeCanvas.drawText(
                            String.format("%.1f", p),
                            size.width - 48f,
                            y - 4f,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.GRAY
                                textSize = 22f
                            }
                        )
                    }

                    // 2. Draw Support & Resistance Lines
                    if (showSupportResistance) {
                        val rLineY = priceToY(fibLevels.resistance1)
                        val sLineY = priceToY(fibLevels.support1)

                        // Resistance R1 (Red dashed)
                        drawLine(
                            color = StockUpRed.copy(alpha = 0.8f),
                            start = Offset(0f, rLineY),
                            end = Offset(size.width, rLineY),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "壓力 R1: ${String.format("%.1f", fibLevels.resistance1)}",
                            8f,
                            rLineY - 4f,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor("#EF4444")
                                textSize = 20f
                            }
                        )

                        // Support S1 (Green dashed)
                        drawLine(
                            color = StockDownGreen.copy(alpha = 0.8f),
                            start = Offset(0f, sLineY),
                            end = Offset(size.width, sLineY),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "支撐 S1: ${String.format("%.1f", fibLevels.support1)}",
                            8f,
                            sLineY + 22f,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor("#10B981")
                                textSize = 20f
                            }
                        )
                    }

                    // 3. Draw Fibonacci Lines
                    if (showFibonacci) {
                        val fibs = listOf(
                            "Fib 0.0% (頂)" to (fibLevels.level0_0 to Fib0Color),
                            "Fib 23.6%" to (fibLevels.level0_236 to Fib236Color),
                            "Fib 38.2% (關鍵支撐)" to (fibLevels.level0_382 to Fib382Color),
                            "Fib 50.0% (中線)" to (fibLevels.level0_500 to Fib500Color),
                            "Fib 61.8% (黃金比例)" to (fibLevels.level0_618 to Fib618Color),
                            "Fib 100% (底)" to (fibLevels.level1_000 to Fib100Color),
                            "擴展 127.2% (目標一)" to (fibLevels.ext1_272 to FibExtColor)
                        )

                        fibs.forEach { (label, data) ->
                            val (levelPrice, color) = data
                            val y = priceToY(levelPrice)
                            if (y in 0f..mainHeight) {
                                drawLine(
                                    color = color.copy(alpha = 0.65f),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    "$label ${String.format("%.1f", levelPrice)}",
                                    size.width * 0.35f,
                                    y - 3f,
                                    android.graphics.Paint().apply {
                                        this.color = android.graphics.Color.argb(
                                            (color.alpha * 255).toInt(),
                                            (color.red * 255).toInt(),
                                            (color.green * 255).toInt(),
                                            (color.blue * 255).toInt()
                                        )
                                        textSize = 18f
                                    }
                                )
                            }
                        }
                    }

                    // 4. Draw Candlesticks
                    kLines.forEachIndexed { index, point ->
                        val centerX = index * stepX + stepX / 2f
                        val isBullish = point.close >= point.open
                        val color = if (isBullish) StockUpRed else StockDownGreen

                        val highY = priceToY(point.high)
                        val lowY = priceToY(point.low)
                        val openY = priceToY(point.open)
                        val closeY = priceToY(point.close)

                        // Wick line
                        drawLine(
                            color = color,
                            start = Offset(centerX, highY),
                            end = Offset(centerX, lowY),
                            strokeWidth = 2f
                        )

                        // Body rect
                        val bodyTop = min(openY, closeY)
                        val bodyHeight = max(2f, kotlin.math.abs(closeY - openY))

                        drawRect(
                            color = color,
                            topLeft = Offset(centerX - candleWidth / 2f, bodyTop),
                            size = Size(candleWidth, bodyHeight)
                        )
                    }

                    // 5. Draw Moving Averages
                    if (showMA) {
                        fun drawMALine(getter: (KLinePoint) -> Double?, color: Color) {
                            val path = Path()
                            var started = false
                            kLines.forEachIndexed { index, point ->
                                val ma = getter(point)
                                if (ma != null) {
                                    val x = index * stepX + stepX / 2f
                                    val y = priceToY(ma)
                                    if (!started) {
                                        path.moveTo(x, y)
                                        started = true
                                    } else {
                                        path.lineTo(x, y)
                                    }
                                }
                            }
                            if (started) {
                                drawPath(path = path, color = color, style = Stroke(width = 2.5f))
                            }
                        }

                        drawMALine({ it.ma5 }, MA5Color)
                        drawMALine({ it.ma10 }, MA10Color)
                        drawMALine({ it.ma20 }, MA20Color)
                        drawMALine({ it.ma60 }, MA60Color)
                    }

                    // 6. Draw Sub-Charts (Volume / MACD / KD)
                    when (subChart) {
                        SubChartType.VOLUME -> {
                            val maxVol = max(1L, kLines.maxOf { it.volume })
                            kLines.forEachIndexed { index, point ->
                                val centerX = index * stepX + stepX / 2f
                                val isBullish = point.close >= point.open
                                val color = if (isBullish) StockUpRed.copy(alpha = 0.8f) else StockDownGreen.copy(alpha = 0.8f)
                                val barHeight = (point.volume.toFloat() / maxVol.toFloat()) * subHeight
                                val barTop = size.height - barHeight

                                drawRect(
                                    color = color,
                                    topLeft = Offset(centerX - candleWidth / 2f, barTop),
                                    size = Size(candleWidth, barHeight)
                                )
                            }
                        }
                        SubChartType.MACD -> {
                            var maxOsc = 0.1
                            kLines.forEach { p ->
                                val o = kotlin.math.abs(p.osc ?: 0.0)
                                if (o > maxOsc) maxOsc = o
                            }
                            val zeroY = subTop + subHeight / 2f
                            drawLine(
                                color = Color.Gray,
                                start = Offset(0f, zeroY),
                                end = Offset(size.width, zeroY),
                                strokeWidth = 1f
                            )

                            kLines.forEachIndexed { index, point ->
                                val centerX = index * stepX + stepX / 2f
                                val osc = point.osc ?: 0.0
                                val barHeight = (kotlin.math.abs(osc) / maxOsc * (subHeight / 2f)).toFloat()
                                val color = if (osc >= 0) StockUpRed else StockDownGreen
                                val barTop = if (osc >= 0) zeroY - barHeight else zeroY

                                drawRect(
                                    color = color,
                                    topLeft = Offset(centerX - candleWidth / 2f, barTop),
                                    size = Size(candleWidth, max(2f, barHeight))
                                )
                            }
                        }
                        SubChartType.KD -> {
                            val pathK = Path()
                            val pathD = Path()
                            var started = false

                            kLines.forEachIndexed { index, point ->
                                val k = (point.k ?: 50.0).toFloat().coerceIn(0f, 100f)
                                val d = (point.d ?: 50.0).toFloat().coerceIn(0f, 100f)
                                val x = index * stepX + stepX / 2f
                                val yK = subTop + subHeight - (k / 100f * subHeight)
                                val yD = subTop + subHeight - (d / 100f * subHeight)

                                if (!started) {
                                    pathK.moveTo(x, yK)
                                    pathD.moveTo(x, yD)
                                    started = true
                                } else {
                                    pathK.lineTo(x, yK)
                                    pathD.lineTo(x, yD)
                                }
                            }
                            drawPath(pathK, color = MA5Color, style = Stroke(width = 2.5f))
                            drawPath(pathD, color = MA10Color, style = Stroke(width = 2.5f))

                            // 80 and 20 overbought/oversold levels
                            val y80 = subTop + subHeight * 0.2f
                            val y20 = subTop + subHeight * 0.8f
                            drawLine(Color(0x33EF4444), Offset(0f, y80), Offset(size.width, y80), 1f)
                            drawLine(Color(0x3310B981), Offset(0f, y20), Offset(size.width, y20), 1f)
                        }
                    }

                    // 7. Touch Crosshair & Tooltip Overlay
                    if (touchIndex != null && touchIndex!! in kLines.indices) {
                        val selPoint = kLines[touchIndex!!]
                        val selX = touchIndex!! * stepX + stepX / 2f
                        val selY = priceToY(selPoint.close)

                        // Vertical & Horizontal crosshair lines
                        drawLine(
                            color = Color.White.copy(alpha = 0.7f),
                            start = Offset(selX, 0f),
                            end = Offset(selX, size.height),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.7f),
                            start = Offset(0f, selY),
                            end = Offset(size.width, selY),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        // Focus marker circle
                        drawCircle(
                            color = StockPrimary,
                            radius = 5f,
                            center = Offset(selX, selY)
                        )
                    }
                }
            }

            // Legend Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("紅色: 上漲", fontSize = 10.sp, color = StockUpRed)
                    Text("綠色: 下跌", fontSize = 10.sp, color = StockDownGreen)
                    if (showFibonacci) {
                        Text("黃金分割: 0.618 關鍵支撐", fontSize = 10.sp, color = Fib618Color)
                    }
                }
                Text("點擊或滑動線圖查看各K棒數值", fontSize = 10.sp, color = TextMutedDark)
            }
        }
    }
}
