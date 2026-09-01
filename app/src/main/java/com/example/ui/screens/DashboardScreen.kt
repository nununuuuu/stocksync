package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.IndexQuote
import com.example.data.model.StockQuote
import com.example.domain.calculator.FibonacciCalculator
import com.example.ui.MainAppViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    viewModel: MainAppViewModel,
    onNavigateToAI: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stocks by viewModel.stocks.collectAsState()
    val indices by viewModel.indices.collectAsState()
    val selectedStock by viewModel.selectedStock.collectAsState()
    val isGenerating by viewModel.isAiGenerating.collectAsState()
    val genMessage by viewModel.generationMessage.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()

    var showReportDialog by remember { mutableStateOf(false) }
    var latestGenReport by remember { mutableStateOf<String?>(null) }
    var latestGenTitle by remember { mutableStateOf("") }

    val activeStock = selectedStock ?: stocks.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0. Live Data Source & Sync Status Header
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = StockPrimary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StockPrimary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                "TWSE/TPEx & Yahoo",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StockPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Column {
                            Text("即時行情與公開資料連線", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            Text(lastSyncTime, fontSize = 10.sp, color = TextMutedDark)
                        }
                    }

                    FilledTonalButton(
                        onClick = { viewModel.refreshMarketData() },
                        enabled = !isSyncing,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = StockPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("同步中...", fontSize = 11.sp, color = StockPrimary)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = StockPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("聯網同步", fontSize = 11.sp, color = StockPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 1. Taiwan Market Indices Carousel
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.ShowChart, contentDescription = null, tint = StockPrimary, modifier = Modifier.size(18.dp))
                        Text("台股即時大盤指數", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                    }
                    Text("自動即時更新", fontSize = 11.sp, color = TextMutedDark)
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(indices) { index ->
                        IndexCard(index = index)
                    }
                }
            }
        }

        // 2. Stock Quick Selector Horizontal Bar
        item {
            Column {
                Text("熱門個股與自選", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(stocks) { stock ->
                        val isSelected = stock.symbol == activeStock?.symbol
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) StockPrimary else StockSurfaceDark,
                            border = if (!isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark)) else null,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.selectStock(stock.symbol) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    stock.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else TextPrimaryDark
                                )
                                Text(
                                    "${if (stock.change >= 0) "+" else ""}${String.format("%.1f", stock.changePercent)}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.Black else if (stock.change >= 0) StockUpRed else StockDownGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        if (activeStock != null) {
            // 3. Active Stock Summary Banner
            item {
                StockSummaryCard(
                    stock = activeStock,
                    onToggleWatchlist = { viewModel.toggleWatchlist(activeStock.symbol) }
                )
            }

            // 4. Interactive Candlestick K-Line Canvas with Fibonacci & Support/Resistance
            item {
                StockKLineChart(
                    kLines = activeStock.kLineHistory,
                    initialFibLevels = FibonacciCalculator.calculate(activeStock.kLineHistory)
                )
            }

            // 5. Quick AI Dual-Track & Day Trading Actions
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = StockTertiary)
                                Text("AI 策略研究快速生成", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            }
                            TextButton(onClick = onNavigateToAI) {
                                Text("更多AI顧問", color = StockPrimary, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    viewModel.runDualTrackStrategyHub(activeStock) { note ->
                                        latestGenTitle = note.title
                                        latestGenReport = note.content
                                        showReportDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_dual_track_strategy"),
                                colors = ButtonDefaults.buttonColors(containerColor = StockPrimary),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isGenerating
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("雙軌決策中樞", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    viewModel.runDayTradingAdvisor(activeStock) { note ->
                                        latestGenTitle = note.title
                                        latestGenReport = note.content
                                        showReportDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_day_trading_plan"),
                                colors = ButtonDefaults.buttonColors(containerColor = StockSurfaceVariantDark),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isGenerating
                            ) {
                                Icon(Icons.Default.Timeline, contentDescription = null, tint = StockTertiary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("當沖教戰點位", color = StockTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (isGenerating && genMessage != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = StockPrimary, strokeWidth = 2.dp)
                                Text(genMessage ?: "", fontSize = 12.sp, color = StockPrimary)
                            }
                        }
                    }
                }
            }

            // 6. Institutional Chips & Margin/Short details
            item {
                ChipsDetailCard(chips = activeStock.chips)
            }

            // 7. Fibonacci Retracement & Expansion table
            item {
                val fib = FibonacciCalculator.calculate(activeStock.kLineHistory)
                FibonacciLevelCard(
                    fib = fib,
                    currentPrice = activeStock.currentPrice
                )
            }

            // 8. Official Market Data Source Citation (TWSE / TPEx / Yahoo Finance)
            item {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = StockSurfaceDark.copy(alpha = 0.7f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark)),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = StockPrimary, modifier = Modifier.size(14.dp))
                            Text("資料來源與合規聲明", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StockPrimary)
                        }
                        Text(
                            text = "資料來源：臺灣證券交易所「三大法人買賣金額統計表」、鉅亨網／經濟日報／鏡週刊／Setn 盤後彙整、臺灣證券交易所加權與櫃買指數 (https://openapi.twse.com.tw/ & https://www.tpex.org.tw/openapi/)、CMoney／WantGoo 法人統計、Yahoo Finance (yfinance) / market_data 即時報價（13:30）。",
                            fontSize = 10.sp,
                            color = TextMutedDark,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }

    // Report View Dialog
    if (showReportDialog && latestGenReport != null) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = {
                Text(latestGenTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxHeight(0.7f)) {
                    item {
                        Text(
                            latestGenReport ?: "",
                            fontSize = 13.sp,
                            color = TextPrimaryDark,
                            lineHeight = 20.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("已自動存入研究筆記", color = StockPrimary)
                }
            },
            containerColor = StockSurfaceDark
        )
    }
}

@Composable
private fun IndexCard(index: IndexQuote) {
    val isUp = index.change >= 0
    val changeColor = if (isUp) StockUpRed else StockDownGreen

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark)),
        modifier = Modifier.width(145.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(index.name, fontSize = 12.sp, color = TextSecondaryDark, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                String.format("%.2f", index.current),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = changeColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${if (isUp) "+" else ""}${String.format("%.2f", index.change)}",
                    fontSize = 11.sp,
                    color = changeColor
                )
                Text(
                    "${if (isUp) "+" else ""}${String.format("%.2f", index.changePercent)}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = changeColor
                )
            }
        }
    }
}
