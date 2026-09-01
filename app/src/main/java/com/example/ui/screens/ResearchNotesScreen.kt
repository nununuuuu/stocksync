package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import com.example.data.local.ResearchNoteEntity
import com.example.data.model.NotebookType
import com.example.ui.MainAppViewModel
import com.example.ui.theme.*

@Composable
fun ResearchNotesScreen(
    viewModel: MainAppViewModel,
    modifier: Modifier = Modifier
) {
    val allNotes by viewModel.allNotes.collectAsState()
    var selectedNotebook by remember { mutableStateOf<NotebookType?>(null) } // null for ALL
    var searchQuery by remember { mutableStateOf("") }
    var selectedNoteForDetail by remember { mutableStateOf<ResearchNoteEntity?>(null) }
    var showCreateNoteDialog by remember { mutableStateOf(false) }

    val filteredNotes = remember(allNotes, selectedNotebook, searchQuery) {
        allNotes.filter { note ->
            val matchNotebook = selectedNotebook == null || note.notebookType == selectedNotebook?.name
            val matchSearch = searchQuery.isBlank() ||
                    note.title.contains(searchQuery, ignoreCase = true) ||
                    note.summary.contains(searchQuery, ignoreCase = true) ||
                    note.content.contains(searchQuery, ignoreCase = true) ||
                    (note.targetSymbol?.contains(searchQuery, ignoreCase = true) == true) ||
                    (note.targetName?.contains(searchQuery, ignoreCase = true) == true)

            matchNotebook && matchSearch
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateNoteDialog = true },
                containerColor = StockPrimary,
                contentColor = Color.Black,
                modifier = Modifier.testTag("fab_create_custom_note")
            ) {
                Icon(Icons.Default.Edit, contentDescription = "撰寫筆記")
            }
        },
        containerColor = Color.Transparent,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("research_notes_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header & Search
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("專業研究筆記庫", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            Text("前3篇為盤後研究筆記 · 第4篇為雙軌策略研究筆記", fontSize = 11.sp, color = TextMutedDark)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = { viewModel.resetBenchmarkNotes() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = StockPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("重載4篇範本", fontSize = 11.sp, color = StockPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_search_notes"),
                        placeholder = { Text("搜尋研報標題、目標股或關鍵字", fontSize = 13.sp, color = TextMutedDark) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondaryDark) },
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

            // Notebook Category Filter Tabs
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedNotebook == null,
                            onClick = { selectedNotebook = null },
                            label = { Text("全部筆記", fontSize = 12.sp) }
                        )
                    }
                    items(NotebookType.values()) { nb ->
                        val isSelected = selectedNotebook == nb
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedNotebook = nb },
                            label = { Text(nb.title, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Notes List
            if (filteredNotes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(StockSurfaceDark, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("筆記庫中尚無符合之研究報告", color = TextSecondaryDark, fontSize = 13.sp)
                    }
                }
            } else {
                items(filteredNotes, key = { it.id }) { note ->
                    ResearchNoteCard(
                        note = note,
                        onClick = { selectedNoteForDetail = note },
                        onDelete = { viewModel.deleteNote(note.id) }
                    )
                }
            }

            // Official Market Research Citation Footer Card
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
                            Text("研報資料來源與合規聲明", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StockPrimary)
                        }
                        Text(
                            text = "資料來源：臺灣證券交易所「三大法人買賣金額統計表」、鉅亨網／經濟日報／鏡週刊／Setn 盤後彙整、臺灣證券交易所加權與櫃買指數 (https://openapi.twse.com.tw/ & https://www.tpex.org.tw/openapi/)、CMoney／WantGoo 法人統計、Yahoo Finance (yfinance) 即時報價（13:30）。",
                            fontSize = 10.sp,
                            color = TextMutedDark,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }

    // Full Note Detail Dialog
    if (selectedNoteForDetail != null) {
        val note = selectedNoteForDetail!!
        AlertDialog(
            onDismissRequest = { selectedNoteForDetail = null },
            title = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = StockSurfaceVariantDark,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                note.author,
                                fontSize = 11.sp,
                                color = StockPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(note.dateStr, fontSize = 11.sp, color = TextMutedDark)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(note.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxHeight(0.75f)) {
                    item {
                        if (note.targetPrice != null || note.stopLossPrice != null || note.keyFibLevel != null) {
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = StockCardDark),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    note.entryPrice?.let { Text("🎯 建議買點: NT$ ${String.format("%.1f", it)}", fontSize = 12.sp, color = TextPrimaryDark) }
                                    note.targetPrice?.let { Text("🚀 目標停利: NT$ ${String.format("%.1f", it)}", fontSize = 12.sp, color = StockUpRed, fontWeight = FontWeight.Bold) }
                                    note.stopLossPrice?.let { Text("🛑 防守停損: NT$ ${String.format("%.1f", it)}", fontSize = 12.sp, color = StockDownGreen, fontWeight = FontWeight.Bold) }
                                    note.keyFibLevel?.let { Text("📐 斐波那契關鍵位: $it", fontSize = 11.sp, color = Fib618Color) }
                                }
                            }
                        }

                        MarkdownDocumentViewer(note.content)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedNoteForDetail = null }) {
                    Text("關閉", color = StockPrimary)
                }
            },
            containerColor = StockSurfaceDark
        )
    }

    // Create Manual Note Dialog
    if (showCreateNoteDialog) {
        var noteTitle by remember { mutableStateOf("") }
        var targetSym by remember { mutableStateOf("") }
        var noteSummary by remember { mutableStateOf("") }
        var noteContent by remember { mutableStateOf("") }
        var noteCategory by remember { mutableStateOf("STRATEGY") }

        AlertDialog(
            onDismissRequest = { showCreateNoteDialog = false },
            title = { Text("撰寫自訂研究筆記", fontWeight = FontWeight.Bold, color = TextPrimaryDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("筆記標題") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = targetSym,
                        onValueChange = { targetSym = it },
                        label = { Text("標的代號 (選填 如 2330)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = noteSummary,
                        onValueChange = { noteSummary = it },
                        label = { Text("核心重點摘要") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("筆記內容與決策分析") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        maxLines = 6
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteTitle.isNotBlank() && noteContent.isNotBlank()) {
                            viewModel.createCustomNote(
                                type = noteCategory,
                                title = noteTitle.trim(),
                                symbol = targetSym.trim(),
                                summary = noteSummary.ifBlank { noteTitle },
                                content = noteContent
                            )
                            showCreateNoteDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StockPrimary)
                ) {
                    Text("存入筆記庫", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateNoteDialog = false }) {
                    Text("取消", color = TextSecondaryDark)
                }
            },
            containerColor = StockSurfaceDark
        )
    }
}

@Composable
private fun ResearchNoteCard(
    note: ResearchNoteEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("note_item_${note.id}"),
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = StockSurfaceVariantDark,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            note.author,
                            fontSize = 11.sp,
                            color = StockPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (note.targetSymbol != null) {
                        Text(
                            "${note.targetSymbol} ${note.targetName ?: ""}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = StockTertiary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(note.dateStr, fontSize = 11.sp, color = TextMutedDark)
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "刪除", tint = TextMutedDark, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(note.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
            Spacer(modifier = Modifier.height(4.dp))
            Text(note.summary, fontSize = 12.sp, color = TextSecondaryDark, lineHeight = 17.sp, maxLines = 2)

            if (note.rating != null || note.targetPrice != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    note.rating?.let {
                        Text("評估：$it", fontSize = 11.sp, color = StockSecondary, fontWeight = FontWeight.SemiBold)
                    }
                    note.targetPrice?.let {
                        Text("目標價: NT$ ${String.format("%.1f", it)}", fontSize = 11.sp, color = StockUpRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownDocumentViewer(markdownText: String) {
    val lines = markdownText.lines()
    var i = 0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# ").trim(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StockPrimary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    i++
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## ").trim(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                    i++
                }
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### ").trim(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = StockSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    i++
                }
                line.startsWith("#### ") -> {
                    Text(
                        text = line.removePrefix("#### ").trim(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StockTertiary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    i++
                }
                line.startsWith("> ") -> {
                    Surface(
                        color = StockCardDark,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, StockPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = line.removePrefix("> ").trim().replace("**", ""),
                            fontSize = 12.sp,
                            color = TextPrimaryDark,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    i++
                }
                line.startsWith("---") -> {
                    HorizontalDivider(color = StockBorderDark, modifier = Modifier.padding(vertical = 4.dp))
                    i++
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("•", fontSize = 13.sp, color = StockPrimary, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = line.substring(2).trim().replace("**", ""),
                            fontSize = 12.sp,
                            color = TextPrimaryDark,
                            lineHeight = 18.sp
                        )
                    }
                    i++
                }
                line.startsWith("|") && line.endsWith("|") -> {
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                        val currentTableLine = lines[i].trim()
                        if (!currentTableLine.contains("---")) {
                            tableLines.add(currentTableLine)
                        }
                        i++
                    }
                    if (tableLines.isNotEmpty()) {
                        TableCard(tableLines)
                    }
                }
                line.isBlank() -> {
                    i++
                }
                else -> {
                    Text(
                        text = line.replace("**", ""),
                        fontSize = 12.sp,
                        color = TextPrimaryDark,
                        lineHeight = 18.sp
                    )
                    i++
                }
            }
        }
    }
}

@Composable
private fun TableCard(rows: List<String>) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = StockCardDark),
        border = BorderStroke(1.dp, StockBorderDark),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEachIndexed { index, rowStr ->
                val cells = rowStr.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                if (cells.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (index == 0) StockSurfaceVariantDark else Color.Transparent, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        cells.forEach { cell ->
                            Text(
                                text = cell.replace("**", ""),
                                fontSize = if (index == 0) 11.sp else 10.sp,
                                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (index == 0) StockPrimary else TextPrimaryDark,
                                modifier = Modifier.weight(1f, fill = false).padding(end = 4.dp)
                            )
                        }
                    }
                    if (index == 0 && rows.size > 1) {
                        HorizontalDivider(color = StockBorderDark, thickness = 1.dp)
                    }
                }
            }
        }
    }
}
