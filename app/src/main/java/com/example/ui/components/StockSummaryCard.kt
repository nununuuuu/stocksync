package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StockQuote
import com.example.ui.theme.*

@Composable
fun StockSummaryCard(
    stock: StockQuote,
    onToggleWatchlist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUp = stock.change >= 0
    val changeColor = if (isUp) StockUpRed else StockDownGreen
    val changeBg = if (isUp) StockUpRedBg else StockDownGreenBg

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top: Symbol, Name, Category & Watchlist Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stock.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Text(
                        stock.symbol,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StockPrimary
                    )
                    Surface(
                        color = StockSurfaceVariantDark,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            stock.category,
                            fontSize = 11.sp,
                            color = TextSecondaryDark,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(onClick = onToggleWatchlist) {
                    Icon(
                        imageVector = if (stock.isWatchlisted) Icons.Default.Star else Icons.Outlined.StarOutline,
                        contentDescription = "自選股",
                        tint = if (stock.isWatchlisted) StockTertiary else TextSecondaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Price & Change Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        "NT$ ${String.format("%.1f", stock.currentPrice)}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = changeColor
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = changeBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isUp) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = changeColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "${if (isUp) "+" else ""}${String.format("%.1f", stock.change)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = changeColor
                            )
                            Text(
                                "(${if (isUp) "+" else ""}${String.format("%.2f", stock.changePercent)}%)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = changeColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Key Statistics: High, Low, Volume, Amount, PE, ROE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(StockCardDark)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(title = "開盤", value = "${stock.openPrice}")
                StatItem(title = "最高", value = "${stock.highPrice}", color = StockUpRed)
                StatItem(title = "最低", value = "${stock.lowPrice}", color = StockDownGreen)
                StatItem(title = "成交量", value = "${stock.volume}張")
                StatItem(title = "本益比", value = "${stock.peRatio}x")
                StatItem(title = "殖利率", value = "${stock.yieldRate}%", color = StockTertiary)
            }
        }
    }
}

@Composable
private fun StatItem(
    title: String,
    value: String,
    color: Color = TextPrimaryDark
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 11.sp, color = TextMutedDark)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
