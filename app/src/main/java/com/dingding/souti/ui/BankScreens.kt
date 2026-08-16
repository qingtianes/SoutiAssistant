package com.dingding.souti.ui

import com.dingding.souti.model.Bank
import com.dingding.souti.model.Question
import com.dingding.souti.repository.QuestionBank
import com.dingding.souti.import.Importer
import com.dingding.souti.ocr.OcrBridge
import com.dingding.souti.ocr.OcrHelper
import com.dingding.souti.overlay.FloatWindowService

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dingding.souti.ui.theme.SoutiAssistantTheme

@Composable
fun OverviewScreen(onBack: () -> Unit, onOpenBank: (Long) -> Unit) {
    val context = LocalContext.current
    val bank = remember { QuestionBank(context) }
    var selectedTab by remember { mutableStateOf(0) }
    var refreshKey by remember { mutableStateOf(0) }
    val activeIds = remember(refreshKey) { bank.getActiveBankIds() }
    val banks by remember(refreshKey, selectedTab) {
        derivedStateOf {
            val type = if (selectedTab == 0) "manual" else "ai"
            bank.loadBanksByType(type)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F7F7)).statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("题库总览", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Green, modifier = Modifier.weight(1f))
            if (banks.isNotEmpty()) {
                val activeCount = banks.count { activeIds.contains(it.id.toString()) }
                TextButton(onClick = onBack) {
                    Text("确认($activeCount)", color = Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("手动导入") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("AI 导入") })
        }
        if (banks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (selectedTab == 0) "暂无手动导入的题库\n请先导入题库" else "暂无 AI 导入的题库", fontSize = 16.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), color = Color(0xFFFFF3E0), shape = RoundedCornerShape(8.dp)) {
                Text("勾选需要搜题的题库 → 点右上角「确认」→ 启动浮窗后只搜这些题库", fontSize = 12.sp, color = Color(0xFFE65100), modifier = Modifier.padding(12.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(banks, key = { it.id }) { b ->
                    val isActive = activeIds.contains(b.id.toString())
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onOpenBank(b.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isActive) Color(0xFFE8F5E9) else Color.White)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isActive, onCheckedChange = { bank.toggleActive(b.id); refreshKey++ })
                            Column(modifier = Modifier.weight(1f).padding(8.dp)) {
                                Text(b.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222222), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text("题目数：${bank.questionCount(b.id)}", fontSize = 11.sp, color = Color.Gray)
                                Text("导入时间：${b.formattedTime()}", fontSize = 11.sp, color = Color.Gray)
                                if (b.sourceFile.isNotEmpty()) Text("源文件：${b.sourceFile}", fontSize = 10.sp, color = Color(0xFFAAAAAA))
                            }
                            // ★ 分享按钮：调用系统分享 Intent（微信/邮箱/蓝牙等）
                            TextButton(onClick = {
                                // ★ 分享：导出为暂时 txt 文件给 FileProvider，系统选择面板选应用
                                val qs = bank.loadQuestions(b.id)
                                val sb = StringBuilder()
                                sb.appendLine("搜题助手 - 题库：${b.name}")
                                sb.appendLine("题目数：${qs.size}")
                                sb.appendLine("---")
                                qs.forEach { q ->
                                    sb.appendLine(q.stem.trim())
                                    q.options.forEachIndexed { i, opt ->
                                        sb.appendLine("${('A' + i)}. $opt")
                                    }
                                    if (q.answer.isNotEmpty()) sb.appendLine("答案:${q.answer}")
                                    sb.appendLine("")
                                }
                                val cacheDir = java.io.File(context.cacheDir, "shared_banks")
                                cacheDir.mkdirs()
                                val safeName = b.name.replace(Regex("[^\\w\\u4e00-\\u9fff]"), "_")
                                val outFile = java.io.File(cacheDir, "${safeName}.txt")
                                outFile.writeText(sb.toString(), Charsets.UTF_8)
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    outFile
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "分享题库（txt文件）"))
                            }) { Text("分享", color = Green, fontSize = 12.sp) }
                            TextButton(onClick = { bank.deleteBank(b.id); refreshKey++ }) { Text("删除", color = Red, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BankDetailScreen(bankId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val bank = remember { QuestionBank(context) }
    var refreshKey by remember { mutableStateOf(0) }
    val questions = remember(refreshKey) { bank.loadQuestions(bankId) }
    val bankInfo = remember(refreshKey) { bank.loadBanks().firstOrNull { it.id == bankId } }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F7F7)).statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Column(modifier = Modifier.weight(1f)) {
                Text(bankInfo?.name ?: "题库", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Green, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${questions.size} 题", fontSize = 12.sp, color = Color.Gray)
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(questions, key = { it.id }) { q ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(q.stem, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF222222))
                        if (q.options.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            q.options.forEach { opt ->
                                Text(opt, fontSize = 13.sp, color = Color(0xFF333333), modifier = Modifier.padding(start = 4.dp, top = 1.dp))
                            }
                        }
                        if (q.answer.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text("答案: ${q.answer}", fontSize = 13.sp, color = Green, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { bank.deleteQuestion(q.id); refreshKey++ }, modifier = Modifier.align(Alignment.End)) {
                            Text("删除", color = Red, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun MenuCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222222))
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
            Text("›", fontSize = 24.sp, color = Color.Gray)
        }
    }
}

/** ★ 题库管理横向图标（4 格网格，未开发功能灰色不可点） */
@Composable
fun RowScope.BankIcon(emoji: String, title: String, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (enabled) Color(0xFFE8F5E9) else Color(0xFFF0F0F0)
    val fg = if (enabled) Color(0xFF222222) else Color(0xFFAAAAAA)
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 28.sp)
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}
