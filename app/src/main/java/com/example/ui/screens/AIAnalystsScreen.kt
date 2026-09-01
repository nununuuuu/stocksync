package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.CustomAnalystEntity
import com.example.data.local.PromptHistoryEntity
import com.example.data.local.ResearchNoteEntity
import com.example.data.local.WorkflowExecutionRecordEntity
import com.example.data.model.AIStockRecommendation
import com.example.data.model.StockQuote
import com.example.domain.ai.WorkflowExecutionState
import com.example.domain.ai.WorkflowStepLog
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*

@Composable
fun AIAnalystsScreen(
    viewModel: MainAppViewModel,
    onViewNotes: () -> Unit,
    onNavigateToChart: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val stocks by viewModel.stocks.collectAsState()
    val selectedStock by viewModel.selectedStock.collectAsState()
    val customAnalysts by viewModel.customAnalysts.collectAsState()
    val aiRecommendations by viewModel.aiRecommendations.collectAsState()
    val isScanningMarket by viewModel.isScanningMarket.collectAsState()
    val isGenerating by viewModel.isAiGenerating.collectAsState()
    val genMessage by viewModel.generationMessage.collectAsState()
    val workflowExecutions by viewModel.workflowExecutions.collectAsState()
    val workflowState by viewModel.workflowState.collectAsState()
    val workflowStepLogs by viewModel.workflowStepLogs.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: 部門協作流水線, 1: 6名AI員工與Prompt版本, 2: 全台股推薦, 3: 指定個股研報
    var selectedAnalystForScreening by remember { mutableStateOf<CustomAnalystEntity?>(null) }
    var selectedSectorFilter by remember { mutableStateOf("全部族群") }

    // Dialog States
    var showAddAnalystDialog by remember { mutableStateOf(false) }
    var editingAnalyst by remember { mutableStateOf<CustomAnalystEntity?>(null) }
    var viewingPromptHistoryAnalyst by remember { mutableStateOf<CustomAnalystEntity?>(null) }
    var deletingAnalystId by remember { mutableStateOf<Long?>(null) }
    var currentReportNote by remember { mutableStateOf<ResearchNoteEntity?>(null) }
    var viewingHtmlReport by remember { mutableStateOf<String?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successToastMessage by remember { mutableStateOf<String?>(null) }

    val activeAnalyst = selectedAnalystForScreening ?: customAnalysts.firstOrNull()
    val activeStock = selectedStock ?: stocks.firstOrNull()

    val sectors = listOf(
        "全部族群", "半導體", "IC設計", "AI伺服器", "AI水冷散熱",
        "網通/交換器", "重電/綠能", "航運海運", "金融金控", "高股息ETF"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("ai_analysts_screen")
    ) {
        // Tab Navigation Header
        Surface(
            color = StockSurfaceDark,
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark))
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = StockPrimary, modifier = Modifier.size(24.dp))
                        Column {
                            Text("台股 AI 投資研究部門", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            Text("6名AI員工協作・數據工具・交接流・HTML研報", fontSize = 11.sp, color = TextSecondaryDark)
                        }
                    }

                    Button(
                        onClick = onViewNotes,
                        colors = ButtonDefaults.buttonColors(containerColor = StockSurfaceVariantDark),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = StockPrimary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("筆記庫", fontSize = 12.sp, color = StockPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = StockPrimary,
                    divider = { HorizontalDivider(color = StockBorderDark) },
                    edgePadding = 12.dp
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("部門協作流水線", fontSize = 13.sp, fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Engineering, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("6名AI員工・Prompt訂製 (${customAnalysts.size})", fontSize = 13.sp, fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Recommend, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("全台股選股推薦", fontSize = 13.sp, fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 3,
                        onClick = { selectedTabIndex = 3 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Article, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("指定個股研報", fontSize = 13.sp, fontWeight = if (selectedTabIndex == 3) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                }
            }
        }

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTabIndex) {
                0 -> {
                    // Tab 0: Department Workflow Cockpit & Reports
                    DepartmentWorkflowCockpitView(
                        workflowState = workflowState,
                        stepLogs = workflowStepLogs,
                        executions = workflowExecutions,
                        onRunPipeline = {
                            viewModel.runFullDepartmentPipeline { record ->
                                if (record != null) {
                                    successToastMessage = "全體 AI 員工協作研究完成！已生成 HTML/PDF 總研報。"
                                }
                            }
                        },
                        onCancelPipeline = { viewModel.cancelWorkflowPipeline() },
                        onViewHtmlReport = { html -> viewingHtmlReport = html },
                        onViewNotes = onViewNotes
                    )
                }
                1 -> {
                    // Tab 1: 6 AI Employees & Prompt Versioning
                    CustomAnalystsManagementView(
                        analysts = customAnalysts,
                        onAddNew = { showAddAnalystDialog = true },
                        onEdit = { editingAnalyst = it },
                        onViewHistory = { viewingPromptHistoryAnalyst = it },
                        onDelete = { deletingAnalystId = it.id },
                        onResetDefaults = { viewModel.resetDefaultAnalysts() },
                        onTestRun = { analyst ->
                            selectedAnalystForScreening = analyst
                            selectedTabIndex = 2
                            viewModel.runUniverseStockScan(analyst, selectedSectorFilter)
                        }
                    )
                }
                2 -> {
                    // Tab 2: Whole Universe AI Stock Recommendations
                    WholeMarketScreeningView(
                        stocks = stocks,
                        customAnalysts = customAnalysts,
                        aiRecommendations = aiRecommendations,
                        isScanning = isScanningMarket,
                        genMessage = genMessage,
                        selectedAnalyst = activeAnalyst,
                        selectedSector = selectedSectorFilter,
                        sectors = sectors,
                        onSelectAnalyst = { selectedAnalystForScreening = it },
                        onSelectSector = { selectedSectorFilter = it },
                        onStartScan = { analyst, sector ->
                            viewModel.runUniverseStockScan(analyst, sector)
                        },
                        onSaveRecToNotes = { rec ->
                            viewModel.saveRecommendationAsNote(rec) { note ->
                                successToastMessage = "已將【${rec.stockSymbol} ${rec.stockName}】推薦研報寫入研究筆記庫！"
                            }
                        },
                        onSelectStock = { sym ->
                            viewModel.selectStock(sym)
                            onNavigateToChart(sym)
                        }
                    )
                }
                3 -> {
                    // Tab 3: Single Target Deep Report
                    SingleTargetDeepReportView(
                        stocks = stocks,
                        activeStock = activeStock,
                        customAnalysts = customAnalysts,
                        isGenerating = isGenerating,
                        genMessage = genMessage,
                        currentReportNote = currentReportNote,
                        onSelectStock = { viewModel.selectStock(it) },
                        onRunReport = { analyst, stock ->
                            viewModel.runCustomAnalystAnalysis(analyst, stock) { note ->
                                currentReportNote = note
                                showSuccessDialog = true
                            }
                        }
                    )
                }
            }
        }
    }

    // View HTML/PDF Report Dialog (Interactive In-App Viewer)
    viewingHtmlReport?.let { html ->
        HtmlReportViewerDialog(
            htmlContent = html,
            onDismiss = { viewingHtmlReport = null }
        )
    }

    // View Prompt History Dialog
    viewingPromptHistoryAnalyst?.let { targetAnalyst ->
        PromptHistoryDialog(
            analyst = targetAnalyst,
            viewModel = viewModel,
            onDismiss = { viewingPromptHistoryAnalyst = null },
            onRestore = { history ->
                viewModel.restoreAnalystPrompt(targetAnalyst, history)
                viewingPromptHistoryAnalyst = null
                successToastMessage = "已將【${targetAnalyst.name}】還原至 Prompt 版本 v${history.version}！"
            }
        )
    }

    // Add New AI Analyst Dialog
    if (showAddAnalystDialog) {
        AnalystEditDialog(
            analyst = null,
            onDismiss = { showAddAnalystDialog = false },
            onSave = { name, roleTitle, avatar, colorHex, spec, prompt, style, noteType, provider, modelId, upstream, tools, cron ->
                viewModel.addCustomAnalyst(
                    name = name,
                    roleTitle = roleTitle,
                    avatarIcon = avatar,
                    themeColorHex = colorHex,
                    specialization = spec,
                    systemPrompt = prompt,
                    analysisStyle = style,
                    notebookType = noteType
                )
                showAddAnalystDialog = false
                successToastMessage = "已成功聘任新 AI 員工【$name】！"
            }
        )
    }

    // Edit Existing AI Analyst Dialog
    editingAnalyst?.let { targetAnalyst ->
        AnalystEditDialog(
            analyst = targetAnalyst,
            onDismiss = { editingAnalyst = null },
            onSave = { name, roleTitle, avatar, colorHex, spec, prompt, style, noteType, provider, modelId, upstream, tools, cron ->
                viewModel.updateAnalystPrompt(
                    targetAnalyst.copy(
                        name = name,
                        roleTitle = roleTitle,
                        avatarIcon = avatar,
                        themeColorHex = colorHex,
                        specialization = spec,
                        analysisStyle = style,
                        notebookType = noteType,
                        provider = provider,
                        modelId = modelId,
                        modelDisplayName = if (modelId.contains("flash")) "Gemini 2.5 Flash" else modelId,
                        upstreamRoleKey = upstream,
                        allowedTools = tools,
                        scheduleCron = cron
                    ),
                    newPrompt = prompt,
                    changeLog = "更新 Prompt 與設定"
                )
                editingAnalyst = null
                successToastMessage = "已更新【$name】的專業 Prompt 與分析參數（版本升級）！"
            }
        )
    }

    // Delete Confirmation Dialog
    deletingAnalystId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingAnalystId = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = StockBearish)
                    Text("解僱 / 刪除自訂 AI 員工", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                }
            },
            text = {
                Text("確定要解僱此 AI 員工嗎？刪除後自訂的 Prompt 與專業設定將被移除。", fontSize = 13.sp, color = TextSecondaryDark)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomAnalyst(id)
                        deletingAnalystId = null
                        successToastMessage = "已成功刪除該自訂 AI 員工。"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StockBearish)
                ) {
                    Text("確認刪除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAnalystId = null }) {
                    Text("取消", color = TextSecondaryDark)
                }
            },
            containerColor = StockSurfaceDark
        )
    }

    // Success SnackBar / Dialog
    if (showSuccessDialog && currentReportNote != null) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StockPrimary)
                    Text("研究報告產出成功", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                }
            },
            text = {
                Text(
                    "【${currentReportNote?.title}】已成功生成並自動寫入研究筆記庫！包含完整斐波那契目標價與防守停損點。",
                    fontSize = 13.sp,
                    color = TextSecondaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = StockPrimary)
                ) {
                    Text("查看研報", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSuccessDialog = false
                    onViewNotes()
                }) {
                    Text("前往筆記庫", color = TextSecondaryDark)
                }
            },
            containerColor = StockSurfaceDark
        )
    }

    // Toast Message
    successToastMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2800)
            successToastMessage = null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                color = StockPrimary,
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Text(msg, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 0: Department Workflow Cockpit & Interactive HTML Reports
// -------------------------------------------------------------
@Composable
fun DepartmentWorkflowCockpitView(
    workflowState: WorkflowExecutionState,
    stepLogs: List<WorkflowStepLog>,
    executions: List<WorkflowExecutionRecordEntity>,
    onRunPipeline: () -> Unit,
    onCancelPipeline: () -> Unit,
    onViewHtmlReport: (String) -> Unit,
    onViewNotes: () -> Unit
) {
    val isRunning = workflowState is WorkflowExecutionState.Running
    val latestExecution = executions.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Action Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(StockPrimary, StockSecondary)))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(StockPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AccountTree, contentDescription = null, tint = StockPrimary, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("AI 投研部門自動協作流水線", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                                Text("每日 16:00 盤後排程・6 名員工依序交接", fontSize = 11.sp, color = TextSecondaryDark)
                            }
                        }

                        Surface(
                            color = StockSurfaceVariantDark,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "TWSE / TPEx 官方數據",
                                fontSize = 10.sp,
                                color = StockPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "點擊下方按鈕將依序調度：\n① 盤後研究員 (大盤指數/成交量) → ② 籌碼選股分析師 (三大法人/A~E分級) → ③ 消息面雷達 (7日時效窗催化) → ④ 策略分析師 (雙軌中樞/斐波那契) → ⑤ 當沖/個股研究員 → ⑥ 總編輯 (HTML/PDF 總報告)。",
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onRunPipeline,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_run_department_pipeline"),
                            colors = ButtonDefaults.buttonColors(containerColor = StockPrimary),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isRunning
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("AI 部門協作中...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("一鍵啟動全體 AI 協作研究", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        if (isRunning) {
                            OutlinedButton(
                                onClick = onCancelPipeline,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StockBearish),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("中斷", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Live Step Progress Display
        if (stepLogs.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🚀 協作交接執行即時看板", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StockPrimary)
                        Spacer(modifier = Modifier.height(10.dp))

                        stepLogs.forEachIndexed { index, step ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (step.status) {
                                                "COMPLETED" -> StockBullish
                                                "RUNNING" -> StockPrimary
                                                "FAILED" -> StockBearish
                                                else -> StockSurfaceVariantDark
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (step.status == "RUNNING") {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                                    } else if (step.status == "COMPLETED") {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                    } else {
                                        Text("${step.step}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Step ${step.step}：${step.agentName}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                                        Surface(color = StockSurfaceVariantDark, shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                when (step.status) {
                                                    "COMPLETED" -> "完成 ✅"
                                                    "RUNNING" -> "執行中..."
                                                    else -> "等待交接"
                                                },
                                                fontSize = 9.sp,
                                                color = if (step.status == "COMPLETED") StockBullish else StockPrimary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text("調用工具：${step.toolsCalled.joinToString(", ")}", fontSize = 10.sp, color = TextSecondaryDark)
                                    if (step.outputNoteTitle != null) {
                                        Text("產出：${step.outputNoteTitle}", fontSize = 10.sp, color = StockSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            if (index < stepLogs.size - 1) {
                                HorizontalDivider(color = StockBorderDark.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        // Latest Full Research Report Preview & Actions
        latestExecution?.let { exec ->
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBullish))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = StockBullish, modifier = Modifier.size(20.dp))
                                Text("最新盤後投研總報告", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            }
                            Surface(color = StockBullish.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "已封存 (${exec.executionDate})",
                                    fontSize = 11.sp,
                                    color = StockBullish,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(exec.summary, fontSize = 13.sp, color = TextPrimaryDark, lineHeight = 19.sp)

                        Spacer(modifier = Modifier.height(12.dp))

                        // Scenario Previews
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = StockSurfaceVariantDark
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("🔮 明日三大情境劇本精要：", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StockPrimary)
                                Text("• ${exec.scenarioA}", fontSize = 11.sp, color = TextSecondaryDark, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("• ${exec.scenarioB}", fontSize = 11.sp, color = TextSecondaryDark, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("• ${exec.scenarioC}", fontSize = 11.sp, color = TextSecondaryDark, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onViewHtmlReport(exec.htmlReportContent) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = StockPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Html, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("瀏覽響應式 HTML 研報", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = onViewNotes,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StockPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("前往筆記庫檢視", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Interactive HTML Report Viewer Dialog (Android WebView)
// -------------------------------------------------------------
@Composable
fun HtmlReportViewerDialog(
    htmlContent: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Article, contentDescription = null, tint = StockPrimary)
                    Text("台股 AI 投資研究部門 · 總研報", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "關閉", tint = TextSecondaryDark)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                setBackgroundColor(android.graphics.Color.parseColor("#090d16"))
                                webViewClient = WebViewClient()
                                loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💡 支援放大縮小與夜間高對比排版", fontSize = 11.sp, color = TextSecondaryDark)
                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/html"
                                putExtra(Intent.EXTRA_SUBJECT, "台股 AI 投資研究部門 · 每日盤後綜合總研報")
                                putExtra(Intent.EXTRA_TEXT, htmlContent)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "分享或匯出研究報告"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StockSurfaceVariantDark),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = StockPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("分享 / PDF 匯出", fontSize = 11.sp, color = StockPrimary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = StockPrimary)
            ) {
                Text("關閉研報", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = StockSurfaceDark
    )
}

// -------------------------------------------------------------
// TAB 1: Custom AI Employees & Prompt Builder Management View
// -------------------------------------------------------------
@Composable
fun CustomAnalystsManagementView(
    analysts: List<CustomAnalystEntity>,
    onAddNew: () -> Unit,
    onEdit: (CustomAnalystEntity) -> Unit,
    onViewHistory: (CustomAnalystEntity) -> Unit,
    onDelete: (CustomAnalystEntity) -> Unit,
    onResetDefaults: () -> Unit,
    onTestRun: (CustomAnalystEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Management Banner & Add Action
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null, tint = StockPrimary, modifier = Modifier.size(22.dp))
                            Text("AI 分析師員工與 Prompt 管理中樞", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        }

                        Button(
                            onClick = onAddNew,
                            colors = ButtonDefaults.buttonColors(containerColor = StockPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("聘任新員工", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "您可以自由修改每位 AI 員工的【LLM 模型】、【上游交接角色】、【允許調用之官方數據工具】、【排程】與【Prompt 版本】。每次修改 Prompt 將自動建立歷史版本，支援隨時一鍵還原！",
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onResetDefaults) {
                            Icon(Icons.Default.Restore, contentDescription = null, tint = TextSecondaryDark, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("重置為預設 6 大研究員團隊", fontSize = 11.sp, color = TextSecondaryDark)
                        }
                    }
                }
            }
        }

        // Analyst Cards List
        items(analysts) { analyst ->
            val color = Color(analyst.themeColorHex)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark)),
                modifier = Modifier.testTag("analyst_card_${analyst.id}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header: Avatar, Name, Role, Built-in badge & Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(color.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (analyst.avatarIcon) {
                                        "HUB" -> Icons.Default.Hub
                                        "TIMELINE" -> Icons.Default.Timeline
                                        "ACCOUNT_BALANCE" -> Icons.Default.AccountBalance
                                        "SAVINGS" -> Icons.Default.Savings
                                        "ANALYTICS" -> Icons.Default.Analytics
                                        "RSS_FEED" -> Icons.Default.RssFeed
                                        "ROCKET" -> Icons.Default.RocketLaunch
                                        "LIGHTBULB" -> Icons.Default.Lightbulb
                                        else -> Icons.Default.Psychology
                                    },
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(analyst.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                                    Surface(color = StockSurfaceVariantDark, shape = RoundedCornerShape(4.dp)) {
                                        Text("v${analyst.promptVersion}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StockPrimary, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                    }
                                    if (analyst.isBuiltIn) {
                                        Surface(color = StockSurfaceVariantDark, shape = RoundedCornerShape(4.dp)) {
                                            Text("核心團隊", fontSize = 9.sp, color = color, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(analyst.roleTitle, fontSize = 11.sp, color = TextSecondaryDark)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = { onViewHistory(analyst) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.History, contentDescription = "版本歷史", tint = StockSecondary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onEdit(analyst) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "編輯 Prompt", tint = StockPrimary, modifier = Modifier.size(18.dp))
                            }
                            if (!analyst.isBuiltIn) {
                                IconButton(onClick = { onDelete(analyst) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "刪除", tint = StockBearish, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Model & Upstream tags
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(color = StockSurfaceVariantDark, shape = RoundedCornerShape(4.dp)) {
                            Text("🤖 ${analyst.provider}: ${analyst.modelDisplayName}", fontSize = 10.sp, color = StockPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Surface(color = StockSurfaceVariantDark, shape = RoundedCornerShape(4.dp)) {
                            Text("🔗 上游交接: ${analyst.upstreamRoleKey}", fontSize = 10.sp, color = TextSecondaryDark, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Surface(color = StockSurfaceVariantDark, shape = RoundedCornerShape(4.dp)) {
                            Text("⏰ ${analyst.scheduleCron}", fontSize = 10.sp, color = StockSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "專長描述：${analyst.specialization}",
                        fontSize = 12.sp,
                        color = TextPrimaryDark,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Allowed Tools Tag
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = StockSurfaceVariantDark.copy(alpha = 0.4f)
                    ) {
                        Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🛠️ 數據工具：", fontSize = 10.sp, color = TextSecondaryDark)
                            Text(analyst.allowedTools, fontSize = 10.sp, color = StockPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // System Prompt Box
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = StockSurfaceVariantDark.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Terminal, contentDescription = null, tint = StockPrimary, modifier = Modifier.size(14.dp))
                                    Text("客製 Prompt (v${analyst.promptVersion})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StockPrimary)
                                }
                                Text("點擊右上筆編輯", fontSize = 10.sp, color = TextSecondaryDark)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                analyst.systemPrompt,
                                fontSize = 11.sp,
                                color = TextSecondaryDark,
                                lineHeight = 16.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Test Run Button
                    Button(
                        onClick = { onTestRun(analyst) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("指派【${analyst.name}】執行全台股選股推薦", fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Prompt History Dialog
// -------------------------------------------------------------
@Composable
fun PromptHistoryDialog(
    analyst: CustomAnalystEntity,
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit,
    onRestore: (PromptHistoryEntity) -> Unit
) {
    val histories by viewModel.getPromptHistory(analyst.id).collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.History, contentDescription = null, tint = StockPrimary)
                Text("【${analyst.name}】Prompt 版本歷史", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                Text(
                    "目前使用中版本：v${analyst.promptVersion}。您可以隨時一鍵還原到過去的任何版本：",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (histories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("尚無過去修訂版本記錄（目前為初始 v1 版）", fontSize = 12.sp, color = TextSecondaryDark)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(histories) { item ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = StockSurfaceVariantDark)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("版本 v${item.version} (${item.changeLog})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StockPrimary)
                                        Button(
                                            onClick = { onRestore(item) },
                                            colors = ButtonDefaults.buttonColors(containerColor = StockPrimary),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("還原此版", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        item.systemPrompt,
                                        fontSize = 11.sp,
                                        color = TextSecondaryDark,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉", color = TextSecondaryDark)
            }
        },
        containerColor = StockSurfaceDark
    )
}

// -------------------------------------------------------------
// TAB 2: Whole Universe AI Stock Screening & Recommendation View
// -------------------------------------------------------------
@Composable
fun WholeMarketScreeningView(
    stocks: List<StockQuote>,
    customAnalysts: List<CustomAnalystEntity>,
    aiRecommendations: List<AIStockRecommendation>,
    isScanning: Boolean,
    genMessage: String?,
    selectedAnalyst: CustomAnalystEntity?,
    selectedSector: String,
    sectors: List<String>,
    onSelectAnalyst: (CustomAnalystEntity) -> Unit,
    onSelectSector: (String) -> Unit,
    onStartScan: (CustomAnalystEntity, String) -> Unit,
    onSaveRecToNotes: (AIStockRecommendation) -> Unit,
    onSelectStock: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // AI Scanner Control Card
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.TravelExplore, contentDescription = null, tint = StockPrimary, modifier = Modifier.size(20.dp))
                            Text("全台股標的池 AI 智慧掃描引擎", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        }
                        Surface(
                            color = StockSurfaceVariantDark,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "共 ${stocks.size} 檔即時標的",
                                fontSize = 11.sp,
                                color = StockPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "由指定 AI 員工執行全市場量化模型：技術面 (5/10/20均線+KD+量能)、籌碼面 (外資投信連買+資券軋空)、基本面 (PE+ROE+殖利率) 與斐波那契支撐壓力評分，推薦最具爆發力與安全邊際之個股。",
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Select Analyst Chips
                    Text("1. 選擇主導評估的 AI 員工 / 策略顧問：", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(customAnalysts) { analyst ->
                            val isSel = analyst.id == selectedAnalyst?.id
                            val color = Color(analyst.themeColorHex)
                            FilterChip(
                                selected = isSel,
                                onClick = { onSelectAnalyst(analyst) },
                                label = { Text(analyst.name, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = color.copy(alpha = 0.25f),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Select Sector Chips
                    Text("2. 選擇產業族群範圍：", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sectors) { sector ->
                            val isSel = sector == selectedSector
                            FilterChip(
                                selected = isSel,
                                onClick = { onSelectSector(sector) },
                                label = { Text(sector, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Button
                    Button(
                        onClick = {
                            selectedAnalyst?.let { onStartScan(it, selectedSector) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("btn_start_market_scan"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = selectedAnalyst?.themeColorHex?.let { Color(it) } ?: StockPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isScanning && selectedAnalyst != null
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI 量化掃描運算中...", color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "由【${selectedAnalyst?.name ?: "AI顧問"}】啟動全台股推薦篩選",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (isScanning && genMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(genMessage ?: "", fontSize = 12.sp, color = StockPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Recommendations List
        if (aiRecommendations.isEmpty() && !isScanning) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Troubleshoot, contentDescription = null, tint = TextSecondaryDark, modifier = Modifier.size(48.dp))
                        Text("尚未執行全市場選股", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        Text(
                            "點擊上方「啟動 AI 全台股選股推薦」按鈕，AI 員工將依照其專屬客製 Prompt，從半導體、AI伺服器、重電綠能等各產業中篩選出最優質標的！",
                            fontSize = 12.sp,
                            color = TextSecondaryDark,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            items(aiRecommendations) { rec ->
                StockRecommendationCard(
                    rec = rec,
                    onSaveToNote = { onSaveRecToNotes(rec) },
                    onNavigateToChart = { onSelectStock(rec.stockSymbol) }
                )
            }
        }
    }
}

@Composable
fun StockRecommendationCard(
    rec: AIStockRecommendation,
    onSaveToNote: () -> Unit,
    onNavigateToChart: () -> Unit
) {
    val isBullish = rec.changePercent >= 0
    val trendColor = if (isBullish) StockBullish else StockBearish

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rec_card_${rec.stockSymbol}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
        border = if (rec.threeWayResonance) {
            CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(StockBullish, StockPrimary)))
        } else {
            CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark))
        }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Symbol, Name, Category & Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = StockSurfaceVariantDark,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            rec.stockSymbol,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = StockPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(rec.stockName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)

                    Surface(
                        color = StockSurfaceVariantDark,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            rec.category,
                            fontSize = 10.sp,
                            color = TextSecondaryDark,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // AI Score Badge
                Surface(
                    color = if (rec.score >= 90) StockBullish.copy(alpha = 0.2f) else StockPrimary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(if (rec.score >= 90) StockBullish else StockPrimary))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = if (rec.score >= 90) StockBullish else StockPrimary, modifier = Modifier.size(14.dp))
                        Text(
                            "AI評分 ${rec.score}分",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (rec.score >= 90) StockBullish else StockPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Price & Recommendation Type Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "NT$ ${String.format("%.1f", rec.currentPrice)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = trendColor
                    )
                    Text(
                        "${if (isBullish) "+" else ""}${String.format("%.2f", rec.changePercent)}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = trendColor
                    )
                }

                Surface(
                    color = if (rec.threeWayResonance) Color(0xFFDC2626) else StockSurfaceVariantDark,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        rec.recommendationType,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (rec.threeWayResonance) Color.White else StockPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Trading Strategy Matrix: Entry, TP, SL, R:R
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = StockSurfaceVariantDark
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("建議進場點", fontSize = 10.sp, color = TextSecondaryDark)
                        Text("NT$ ${rec.entryPrice}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                    }
                    Column {
                        Text("目標價 (TP)", fontSize = 10.sp, color = TextSecondaryDark)
                        Text("NT$ ${rec.targetPrice}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StockBullish)
                    }
                    Column {
                        Text("防守停損 (SL)", fontSize = 10.sp, color = TextSecondaryDark)
                        Text("NT$ ${rec.stopLossPrice}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StockBearish)
                    }
                    Column {
                        Text("風報比 (R:R)", fontSize = 10.sp, color = TextSecondaryDark)
                        Text("${rec.riskRewardRatio} : 1", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StockPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Key Factors Bar: Chips & PE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = StockSurfaceVariantDark.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "法人淨超: ${rec.institutionalNet}張 (投信連買${rec.trustConsecutiveDays}天)",
                        fontSize = 10.sp,
                        color = StockPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = StockSurfaceVariantDark.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "PE: ${rec.peRatio}x / 殖利率: ${rec.yieldRate}%",
                        fontSize = 10.sp,
                        color = TextSecondaryDark,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // AI Rationale
            Text(
                rec.rationale,
                fontSize = 12.sp,
                color = TextSecondaryDark,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToChart,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StockPrimary),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(Icons.Default.ShowChart, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("查看K線圖", fontSize = 12.sp)
                }

                Button(
                    onClick = onSaveToNote,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StockPrimary),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("存入研究筆記", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: Single Target Deep Report View
// -------------------------------------------------------------
@Composable
fun SingleTargetDeepReportView(
    stocks: List<StockQuote>,
    activeStock: StockQuote?,
    customAnalysts: List<CustomAnalystEntity>,
    isGenerating: Boolean,
    genMessage: String?,
    currentReportNote: ResearchNoteEntity?,
    onSelectStock: (String) -> Unit,
    onRunReport: (CustomAnalystEntity, StockQuote) -> Unit
) {
    var selectedAnalyst by remember { mutableStateOf<CustomAnalystEntity?>(null) }
    val currentAnalyst = selectedAnalyst ?: customAnalysts.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Stock and Analyst Selector Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockBorderDark))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("指定特定個股產出深度研報", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "由您選擇的 AI 員工結合即時報價、法人籌碼、日K均線/KD/MACD 及斐波那契點位，生成完整專業研究報告並自動寫入筆記庫。",
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Target Stock Selector
                    Text("1. 選擇分析標的：", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(stocks) { stock ->
                            val isSel = stock.symbol == activeStock?.symbol
                            FilterChip(
                                selected = isSel,
                                onClick = { onSelectStock(stock.symbol) },
                                label = { Text("${stock.symbol} ${stock.name}", fontSize = 11.sp) },
                                leadingIcon = if (isSel) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Analyst Selector
                    Text("2. 選擇負責研究員：", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(customAnalysts) { analyst ->
                            val isSel = analyst.id == currentAnalyst?.id
                            val color = Color(analyst.themeColorHex)
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedAnalyst = analyst },
                                label = { Text(analyst.name, fontSize = 11.sp) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Button
                    Button(
                        onClick = {
                            if (currentAnalyst != null && activeStock != null) {
                                onRunReport(currentAnalyst, activeStock)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_generate_target_report"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = currentAnalyst?.themeColorHex?.let { Color(it) } ?: StockPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isGenerating && activeStock != null && currentAnalyst != null
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI 深度分析運算中...", color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "由【${currentAnalyst?.name}】產出 ${activeStock?.symbol} ${activeStock?.name} 深度研報",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (isGenerating && genMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(genMessage ?: "", fontSize = 12.sp, color = StockPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Live Report Preview
        if (currentReportNote != null) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StockSurfaceDark),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StockPrimary))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("最新研究報告產出", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = StockPrimary)
                            Surface(
                                color = StockSurfaceVariantDark,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "已存入筆記庫",
                                    fontSize = 11.sp,
                                    color = StockPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(currentReportNote.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(currentReportNote.summary, fontSize = 13.sp, color = TextSecondaryDark, lineHeight = 18.sp)

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = StockBorderDark)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            currentReportNote.content,
                            fontSize = 13.sp,
                            color = TextPrimaryDark,
                            lineHeight = 21.sp
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Add / Edit AI Analyst Modal Dialog
// -------------------------------------------------------------
@Composable
fun AnalystEditDialog(
    analyst: CustomAnalystEntity?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        roleTitle: String,
        avatarIcon: String,
        themeColorHex: Long,
        specialization: String,
        systemPrompt: String,
        analysisStyle: String,
        notebookType: String,
        provider: String,
        modelId: String,
        upstreamRoleKey: String,
        allowedTools: String,
        scheduleCron: String
    ) -> Unit
) {
    val isEditing = analyst != null
    var name by remember { mutableStateOf(analyst?.name ?: "") }
    var roleTitle by remember { mutableStateOf(analyst?.roleTitle ?: "") }
    var avatarIcon by remember { mutableStateOf(analyst?.avatarIcon ?: "HUB") }
    var themeColorHex by remember { mutableLongStateOf(analyst?.themeColorHex ?: 0xFFA855F7) }
    var specialization by remember { mutableStateOf(analyst?.specialization ?: "") }
    var systemPrompt by remember {
        mutableStateOf(
            analyst?.systemPrompt
                ?: "你是一位頂尖台股資深操盤手與分析專家。請根據提供之日K技術指標（5/10/20日均線、MACD、KD）、三大法人買賣超與斐波那契點位，進行多空綜合研判，給出精準進場價、停利目標價與嚴格防守停損價。"
        )
    }
    var analysisStyle by remember { mutableStateOf(analyst?.analysisStyle ?: "多面向綜合研判") }
    var notebookType by remember { mutableStateOf(analyst?.notebookType ?: "STRATEGY") }
    var provider by remember { mutableStateOf(analyst?.provider ?: "Google Gemini") }
    var modelId by remember { mutableStateOf(analyst?.modelId ?: "gemini-2.5-flash") }
    var upstreamRoleKey by remember { mutableStateOf(analyst?.upstreamRoleKey ?: "NONE") }
    var allowedTools by remember { mutableStateOf(analyst?.allowedTools ?: "get_market_summary,get_stock_quote,write_note") }
    var scheduleCron by remember { mutableStateOf(analyst?.scheduleCron ?: "16:00 (盤後每日)") }

    val modelOptions = listOf(
        "gemini-2.5-flash" to "Gemini 2.5 Flash (極速版)",
        "gemini-2.5-pro" to "Gemini 2.5 Pro (深度推理)",
        "claude-3-5-sonnet" to "Claude 3.5 Sonnet",
        "gpt-4o" to "GPT-4o",
        "deepseek-r1" to "DeepSeek R1 (開源)"
    )

    val colorOptions = listOf(
        0xFFA855F7 to "紫色 (策略)",
        0xFFF59E0B to "琥珀 (當沖)",
        0xFF06B6D4 to "青色 (籌碼)",
        0xFF10B981 to "綠色 (價值)",
        0xFF38BDF8 to "天藍 (盤後)",
        0xFFF97316 to "橙色 (消息)",
        0xFFEC4899 to "粉紅 (爆發)"
    )

    val noteTypeOptions = listOf(
        "STRATEGY" to "雙軌策略筆記",
        "DAY_TRADING" to "當沖短線筆記",
        "FUNDAMENTAL" to "個股基本面筆記",
        "AFTER_HOURS" to "盤後研究筆記"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (isEditing) Icons.Default.Edit else Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = StockPrimary
                )
                Text(
                    if (isEditing) "訂製【${analyst?.name}】的 Prompt 與設定" else "聘任新 AI 員工 (自訂 Prompt)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Name & Role
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("AI 員工名稱 (如: 飆股動能雷達)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_analyst_name"),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = roleTitle,
                        onValueChange = { roleTitle = it },
                        label = { Text("職稱 / 頭銜 (如: 短線爆發力分析官)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_analyst_role"),
                        singleLine = true
                    )
                }

                // Model Selection
                item {
                    Text("底層大語言模型 (LLM Model)：", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(modelOptions) { (id, label) ->
                            val isSel = id == modelId
                            FilterChip(
                                selected = isSel,
                                onClick = { modelId = id },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Upstream Handover & Schedule
                item {
                    OutlinedTextField(
                        value = upstreamRoleKey,
                        onValueChange = { upstreamRoleKey = it },
                        label = { Text("上游交接角色 Key (如: AFTER_HOURS, CHIPS_SCREENER, NONE)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = allowedTools,
                        onValueChange = { allowedTools = it },
                        label = { Text("允許調用之官方數據工具 (以逗號分隔)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = scheduleCron,
                        onValueChange = { scheduleCron = it },
                        label = { Text("每日執行排程 (如: 16:00 盤後每日)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Color Selection
                item {
                    Text("代表主題色：", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(colorOptions) { (hex, _) ->
                            val isSel = hex == themeColorHex
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .clickable { themeColorHex = hex }
                                    .border(
                                        width = if (isSel) 3.dp else 0.dp,
                                        color = if (isSel) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSel) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Specialization
                item {
                    OutlinedTextField(
                        value = specialization,
                        onValueChange = { specialization = it },
                        label = { Text("專精研究領域 (如: 5/10/20均線向上、投信連買)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_analyst_spec"),
                        maxLines = 2
                    )
                }

                // Custom System Prompt (The most important section)
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("核心系統 Prompt 指令 (System Prompt)：", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StockPrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("此 Prompt 將作為大模型的核心推理邏輯，儲存時將自動遞增版本號並備份舊版。", fontSize = 11.sp, color = TextSecondaryDark)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .testTag("input_analyst_prompt"),
                            maxLines = 10,
                            placeholder = { Text("請輸入客製化 Prompt...") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            name,
                            roleTitle.ifBlank { "專業分析師" },
                            avatarIcon,
                            themeColorHex,
                            specialization.ifBlank { "全市場多因子量化評估" },
                            systemPrompt.ifBlank { "你是一位頂尖台股分析師。" },
                            analysisStyle.ifBlank { "綜合量化分析" },
                            notebookType,
                            provider,
                            modelId,
                            upstreamRoleKey,
                            allowedTools,
                            scheduleCron
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = StockPrimary),
                enabled = name.isNotBlank()
            ) {
                Text(if (isEditing) "儲存 Prompt 與參數 (升版)" else "立即聘任", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondaryDark)
            }
        },
        containerColor = StockSurfaceDark
    )
}
