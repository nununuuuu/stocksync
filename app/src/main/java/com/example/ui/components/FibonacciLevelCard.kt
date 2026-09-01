package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FibonacciLevels
import com.example.ui.theme.*

@Composable
fun FibonacciLevelCard(
    fib: FibonacciLevels,
    currentPrice: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = "斐波那契關鍵位",
                        tint = Fib618Color,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("斐波那契回撤與擴展目標位", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                }

                Surface(
                    color = StockSurfaceVariantDark,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "黃金分割 0.618 關鍵",
                        fontSize = 11.sp,
                        color = Fib618Color,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Retracement Levels
            Text("【波段回撤位】(支撐/回檔防守分析)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryDark)
            Spacer(modifier = Modifier.height(8.dp))

            val retracements = listOf(
                Triple("0.0% (波段高點壓力)", fib.level0_0, Fib0Color),
                Triple("23.6% (強勢回檔位)", fib.level0_236, Fib236Color),
                Triple("38.2% (關鍵支撐位)", fib.level0_382, Fib382Color),
                Triple("50.0% (多空中線平衡)", fib.level0_500, Fib500Color),
                Triple("61.8% (黃金分割強支撐)", fib.level0_618, Fib618Color),
                Triple("78.6% (極限防守位)", fib.level0_786, Fib786Color),
                Triple("100.0% (起漲波段底)", fib.level1_000, Fib100Color)
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                retracements.forEach { (label, price, color) ->
                    FibLevelRow(
                        label = label,
                        levelPrice = price,
                        currentPrice = currentPrice,
                        color = color
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = StockBorderDark, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Extension / Projection Targets
            Text("【波段擴展目標位】(突破後波段停利目標)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryDark)
            Spacer(modifier = Modifier.height(8.dp))

            val extensions = listOf(
                Triple("127.2% 擴展 (第一衝刺目標)", fib.ext1_272, FibExtColor),
                Triple("161.8% 擴展 (黃金擴展主升段)", fib.ext1_618, StockTertiary),
                Triple("200.0% 擴展 (波段翻倍目標)", fib.ext2_000, StockPrimary),
                Triple("261.8% 擴展 (超強延伸目標)", fib.ext2_618, StockSecondary)
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                extensions.forEach { (label, price, color) ->
                    FibLevelRow(
                        label = label,
                        levelPrice = price,
                        currentPrice = currentPrice,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun FibLevelRow(
    label: String,
    levelPrice: Double,
    currentPrice: Double,
    color: Color
) {
    val isNear = kotlin.math.abs(currentPrice - levelPrice) / levelPrice * 100 <= 1.2

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isNear) Color(0x3038BDF8) else StockCardDark,
        border = if (isNear) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockPrimary)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
                Text(label, fontSize = 12.sp, color = if (isNear) TextPrimaryDark else TextSecondaryDark, fontWeight = if (isNear) FontWeight.Bold else FontWeight.Normal)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isNear) {
                    Surface(
                        color = StockPrimary,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("現價附近", fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
                Text(
                    "NT$ ${String.format("%.1f", levelPrice)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}
