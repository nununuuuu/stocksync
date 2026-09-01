package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.data.local.AlertRecordEntity
import com.example.data.local.AlertRuleEntity
import com.example.data.model.AlertType
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertsRadarScreen(
    viewModel: MainAppViewModel,
    modifier: Modifier = Modifier
) {
    val alertRecords by viewModel.alertRecords.collectAsState()
    val alertRules by viewModel.alertRules.collectAsState()
    val stocks by viewModel.stocks.collectAsState()
    val selectedStock by viewModel.selectedStock.collectAsState()

    var showAddRuleDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("alerts_radar_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Radar Status & Action Header
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = StockTertiary, modifier = Modifier.size(24.dp))
                            Text("盤中即時預警雷達", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        }

                        Surface(
                            color = Color(0x3010B981),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(StockDownGreen))
                                Text("即時監聽中", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StockDownGreen)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "即時偵測：突破/跌破關鍵價位、斐波那契黃金分割位、帶量長紅攻擊、KD黃金交叉及軋空訊號，自動觸發推播預警。",
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showAddRuleDialog = true },
                            modifier = Modifier.weight(1f).testTag("btn_create_alert_rule"),
                            colors = ButtonDefaults.buttonColors(containerColor = StockPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AddAlert, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("新增自訂預警", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        selectedStock?.let { stock ->
                            OutlinedButton(
                                onClick = { viewModel.testTriggerAlert(stock) },
                                modifier = Modifier.weight(1f).testTag("btn_test_alert_simulation"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Sensors, contentDescription = null, tint = StockTertiary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("模擬觸發推播", fontSize = 12.sp, color = StockTertiary)
                            }
                        }
                    }
                }
            }
        }

        // Active Alert Rules List
        item {
            Column {
                Text("已啟用的預警規則 (${alertRules.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                Spacer(modifier = Modifier.height(8.dp))

                if (alertRules.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = StockSurfaceDark
                    ) {
                        Text(
                            "目前系統已預設全自動偵測所有自選股的斐波那契關鍵位與量能暴增訊號。您也可點擊上方新增自訂價位。",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 12.sp,
                            color = TextMutedDark
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        alertRules.forEach { rule ->
                            RuleItemCard(rule = rule, onDelete = { viewModel.deleteAlertRule(rule) })
                        }
                    }
                }
            }
        }

        // Triggered Alert Records Feed
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("盤中即時警報紀錄", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                Text("${alertRecords.size} 則歷史訊號", fontSize = 11.sp, color = TextMutedDark)
            }
        }

        if (alertRecords.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(StockSurfaceDark, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("盤中運行中，等待訊號觸發...", color = TextSecondaryDark, fontSize = 13.sp)
                }
            }
        } else {
            items(alertRecords, key = { it.id }) { record ->
                AlertRecordCard(record = record)
            }
        }
    }

    // Add Alert Rule Dialog
    if (showAddRuleDialog) {
        var selectedSymbol by remember { mutableStateOf(stocks.firstOrNull()?.symbol ?: "2330") }
        var targetPriceInput by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(AlertType.BREAKOUT_RESISTANCE.label) }

        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text("新增盤中價格預警規則", fontWeight = FontWeight.Bold, color = TextPrimaryDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("選擇股票：", fontSize = 12.sp, color = TextSecondaryDark)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(stocks) { s ->
                            FilterChip(
                                selected = s.symbol == selectedSymbol,
                                onClick = { selectedSymbol = s.symbol },
                                label = { Text("${s.symbol} ${s.name}", fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = targetPriceInput,
                        onValueChange = { targetPriceInput = it },
                        label = { Text("目標觸發價位 (NT$)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = targetPriceInput.toDoubleOrNull() ?: 0.0
                        val stockName = stocks.find { it.symbol == selectedSymbol }?.name ?: ""
                        if (p > 0) {
                            viewModel.addAlertRule(selectedSymbol, stockName, selectedType, p)
                            showAddRuleDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StockPrimary)
                ) {
                    Text("設定預警", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) {
                    Text("取消", color = TextSecondaryDark)
                }
            },
            containerColor = StockSurfaceDark
        )
    }
}

@Composable
private fun RuleItemCard(
    rule: AlertRuleEntity,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = StockSurfaceDark,
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(rule.stockName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                    Text(rule.stockSymbol, fontSize = 12.sp, color = StockPrimary)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text("觸發價位: NT$ ${rule.targetPrice} (${rule.alertType})", fontSize = 12.sp, color = TextSecondaryDark)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "刪除", tint = TextMutedDark, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AlertRecordCard(record: AlertRecordEntity) {
    val dateStr = SimpleDateFormat("HH:mm:ss", Locale.TAIWAN).format(Date(record.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x30F59E0B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = StockTertiary, modifier = Modifier.size(18.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(record.stockName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        Text(record.alertType, fontSize = 11.sp, color = StockPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Text(dateStr, fontSize = 11.sp, color = TextMutedDark)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(record.message, fontSize = 12.sp, color = TextSecondaryDark, lineHeight = 17.sp)
            }
        }
    }
}
