package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InstitutionalChips
import com.example.ui.theme.*

@Composable
fun ChipsDetailCard(
    chips: InstitutionalChips?,
    modifier: Modifier = Modifier
) {
    if (chips == null) return

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
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = "法人籌碼",
                        tint = StockPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("三大法人與資券籌碼分析", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                }

                Surface(
                    color = StockSurfaceVariantDark,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        chips.chipRating,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StockTertiary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Institutional Buy/Sell grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChipMetricBox(
                    title = "外資買賣超",
                    value = formatLots(chips.foreignBuySell),
                    subtext = "持股率 ${chips.foreignHoldPercent}%",
                    isPositive = chips.foreignBuySell >= 0,
                    modifier = Modifier.weight(1f)
                )
                ChipMetricBox(
                    title = "投信買賣超",
                    value = formatLots(chips.trustBuySell),
                    subtext = "連買 ${chips.trustConsecutiveBuyDays} 天",
                    isPositive = chips.trustBuySell >= 0,
                    highlight = chips.trustConsecutiveBuyDays >= 3,
                    modifier = Modifier.weight(1f)
                )
                ChipMetricBox(
                    title = "自營商",
                    value = formatLots(chips.dealerBuySell),
                    subtext = "短線避險",
                    isPositive = chips.dealerBuySell >= 0,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Total Institutional
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(StockSurfaceVariantDark)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("三大法人單日淨買賣超合計", fontSize = 13.sp, color = TextSecondaryDark)
                Text(
                    formatLots(chips.totalInstitutional),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (chips.totalInstitutional >= 0) StockUpRed else StockDownGreen
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = StockBorderDark, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Margin and Short Selling (融資融券)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("融資融券散戶動態", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                Text("券償比: ${String.format("%.2f", chips.marginShortRatio)}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StockSecondary)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MarginBox(
                    title = "融資餘額 / 增減",
                    balance = "${chips.marginBalance} 張",
                    change = formatLotsWithSign(chips.marginChange),
                    isPositive = chips.marginChange >= 0,
                    note = if (chips.marginChange < 0) "散戶退場 (籌碼沉澱)" else "散戶追價",
                    modifier = Modifier.weight(1f)
                )
                MarginBox(
                    title = "融券餘額 / 增減",
                    balance = "${chips.shortBalance} 張",
                    change = formatLotsWithSign(chips.shortChange),
                    isPositive = chips.shortChange >= 0,
                    note = if (chips.shortChange > 0 && chips.marginShortRatio > 20.0) "⚡ 軋空動能增強" else "避險/看空",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChipMetricBox(
    title: String,
    value: String,
    subtext: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (highlight) Color(0x30F59E0B) else StockCardDark,
        border = if (highlight) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockTertiary)) else null
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontSize = 12.sp, color = TextSecondaryDark)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPositive) StockUpRed else StockDownGreen
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtext, fontSize = 10.sp, color = if (highlight) StockTertiary else TextMutedDark)
        }
    }
}

@Composable
private fun MarginBox(
    title: String,
    balance: String,
    change: String,
    isPositive: Boolean,
    note: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = StockCardDark
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontSize = 12.sp, color = TextSecondaryDark)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(balance, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                Text(
                    change,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPositive) StockUpRed else StockDownGreen
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(note, fontSize = 10.sp, color = TextMutedDark)
        }
    }
}

private fun formatLots(lots: Long): String {
    return if (lots >= 0) "+$lots 張" else "$lots 張"
}

private fun formatLotsWithSign(lots: Long): String {
    return if (lots >= 0) "+$lots 張" else "$lots 張"
}
