package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
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
import com.example.data.model.StockQuote
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*

enum class ChipFilter(val label: String) {
    ALL("全部標的"),
    WATCHLIST("我的自選"),
    TRUST_CONSECUTIVE("投信連買作帳"),
    FOREIGN_BUY("外資主力加碼"),
    SHORT_SQUEEZE("資減券增 (軋空)"),
    HEAVY_VOLUME("帶量長紅突破"),
    VALUE_INVESTING("價值存股 (低PE/高殖利率)")
}

@Composable
fun WatchlistChipsScreen(
    viewModel: MainAppViewModel,
    onSelectStockAndGoDashboard: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val stocks by viewModel.stocks.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    var currentFilter by remember { mutableStateOf(ChipFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddStockDialog by remember { mutableStateOf(false) }

    val filteredStocks = remember(stocks, currentFilter, searchQuery) {
        stocks.filter { stock ->
            val matchQuery = searchQuery.isBlank() ||
                    stock.name.contains(searchQuery, ignoreCase = true) ||
                    stock.symbol.contains(searchQuery, ignoreCase = true) ||
                    stock.category.contains(searchQuery, ignoreCase = true)

            val matchFilter = when (currentFilter) {
                ChipFilter.ALL -> true
                ChipFilter.WATCHLIST -> stock.isWatchlisted
                ChipFilter.TRUST_CONSECUTIVE -> (stock.chips?.trustConsecutiveBuyDays ?: 0) >= 3
                ChipFilter.FOREIGN_BUY -> (stock.chips?.foreignBuySell ?: 0) > 1500
                ChipFilter.SHORT_SQUEEZE -> (stock.chips?.marginChange ?: 0) < 0 && (stock.chips?.shortChange ?: 0) > 0
                ChipFilter.HEAVY_VOLUME -> stock.changePercent >= 2.0 && stock.volume > 20000
                ChipFilter.VALUE_INVESTING -> stock.peRatio <= 18.0 && stock.yieldRate >= 4.0
            }

            matchQuery && matchFilter
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddStockDialog = true },
                containerColor = StockPrimary,
                contentColor = Color.Black,
                modifier = Modifier.testTag("fab_add_custom_stock")
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增個股")
            }
        },
        containerColor = Color.Transparent,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("watchlist_chips_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Search Bar & Stats Header
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("籌碼選股與自選清單", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            Text(lastSyncTime, fontSize = 10.sp, color = TextMutedDark)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { viewModel.refreshMarketData() },
                                enabled = !isSyncing,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = StockPrimary, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp), tint = StockPrimary)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("同步", fontSize = 11.sp, color = StockPrimary)
                                }
                            }
                            Text("共 ${filteredStocks.size} 檔", fontSize = 13.sp, color = StockPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_search_stock"),
                        placeholder = { Text("搜尋股票代號、名稱或產業 (如 2330, 台積電, AI)", fontSize = 13.sp, color = TextMutedDark) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondaryDark) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清除", tint = TextSecondaryDark)
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = StockSurfaceDark,
                            unfocusedContainerColor = StockSurfaceDark,
                            focusedBorderColor = StockPrimary,
                            unfocusedBorderColor = StockBorderDark,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        )
                    )
                }
            }

            // Screener Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ChipFilter.values()) { filter ->
                        val isSelected = filter == currentFilter
                        FilterChip(
                            selected = isSelected,
                            onClick = { currentFilter = filter },
                            label = { Text(filter.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StockPrimary,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
            }

            // Stock Screener Cards List
            if (filteredStocks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(StockSurfaceDark, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("無符合篩選條件之標的", color = TextSecondaryDark, fontSize = 14.sp)
                    }
                }
            } else {
                items(filteredStocks, key = { it.symbol }) { stock ->
                    StockScreenerCard(
                        stock = stock,
                        onSelect = {
                            viewModel.selectStock(stock.symbol)
                            onSelectStockAndGoDashboard(stock.symbol)
                        },
                        onToggleWatchlist = { viewModel.toggleWatchlist(stock.symbol) }
                    )
                }
            }

            // Data Source Citation Footer Card
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

    // Add Custom Stock Dialog
    if (showAddStockDialog) {
        var symbolInput by remember { mutableStateOf("") }
        var nameInput by remember { mutableStateOf("") }
        var categoryInput by remember { mutableStateOf("") }
        var priceInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddStockDialog = false },
            title = { Text("新增自選與監控個股", fontWeight = FontWeight.Bold, color = TextPrimaryDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = symbolInput,
                        onValueChange = { symbolInput = it },
                        label = { Text("股票代號 (如 2609)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("股票名稱 (如 陽明)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = categoryInput,
                        onValueChange = { categoryInput = it },
                        label = { Text("產業類別 (如 航運業)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("當前參考股價 (如 72.5)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val price = priceInput.toDoubleOrNull() ?: 100.0
                        if (symbolInput.isNotBlank() && nameInput.isNotBlank()) {
                            viewModel.addCustomStock(
                                symbol = symbolInput.trim(),
                                name = nameInput.trim(),
                                category = categoryInput.ifBlank { "一般產業" },
                                price = price
                            )
                            showAddStockDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StockPrimary)
                ) {
                    Text("新增入庫", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddStockDialog = false }) {
                    Text("取消", color = TextSecondaryDark)
                }
            },
            containerColor = StockSurfaceDark
        )
    }
}

@Composable
private fun StockScreenerCard(
    stock: StockQuote,
    onSelect: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    val isUp = stock.change >= 0
    val changeColor = if (isUp) StockUpRed else StockDownGreen
    val chips = stock.chips

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .testTag("stock_item_${stock.symbol}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row 1: Name, Symbol, Category & Watchlist
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stock.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                    Text(stock.symbol, fontSize = 13.sp, color = StockPrimary, fontWeight = FontWeight.SemiBold)
                    Surface(
                        color = StockSurfaceVariantDark,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(stock.category, fontSize = 10.sp, color = TextSecondaryDark, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "NT$ ${String.format("%.1f", stock.currentPrice)}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = changeColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (isUp) StockUpRedBg else StockDownGreenBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "${if (isUp) "+" else ""}${String.format("%.2f", stock.changePercent)}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = changeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    IconButton(onClick = onToggleWatchlist, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (stock.isWatchlisted) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = if (stock.isWatchlisted) StockTertiary else TextSecondaryDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Chips Snapshot & Valuation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(StockCardDark)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (chips != null) {
                    Text(
                        "法人買賣超: ${if (chips.totalInstitutional >= 0) "+${chips.totalInstitutional}" else "${chips.totalInstitutional}"}張",
                        fontSize = 11.sp,
                        color = if (chips.totalInstitutional >= 0) StockUpRed else StockDownGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("投信連買: ${chips.trustConsecutiveBuyDays}天", fontSize = 11.sp, color = StockTertiary)
                    Text("券償比: ${String.format("%.1f", chips.marginShortRatio)}%", fontSize = 11.sp, color = TextSecondaryDark)
                }
                Text("PE: ${stock.peRatio}x", fontSize = 11.sp, color = TextMutedDark)
            }
        }
    }
}
