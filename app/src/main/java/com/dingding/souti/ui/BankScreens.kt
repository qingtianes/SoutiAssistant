package com.dingding.souti.ui

import com.dingding.souti.repository.QuestionBank

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dingding.souti.ui.theme.LocalGlass

@Composable
fun OverviewScreen(onBack: () -> Unit, onOpenBank: (Long) -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlass.current
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

    GlassBackground {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回", tint = glass.textPrimary) }
                Text("题库总览", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary, modifier = Modifier.weight(1f))
                if (banks.isNotEmpty()) {
                    val activeCount = banks.count { activeIds.contains(it.id.toString()) }
                    TextButton(onClick = onBack) {
                        Text("确认($activeCount)", color = glass.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
            Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("手动导入", selected = selectedTab == 0) { selectedTab = 0 }
                GlassChip("AI 导入", selected = selectedTab == 1) { selectedTab = 1 }
            }
            if (banks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (selectedTab == 0) "暂无手动导入的题库\n请先导入题库" else "暂无 AI 导入的题库",
                        fontSize = 16.sp, color = glass.textSecondary, textAlign = TextAlign.Center
                    )
                }
            } else {
                Spacer(Modifier.height(10.dp))
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text("勾选需要搜题的题库 → 点右上角「确认」→ 启动浮窗后只搜这些题库", fontSize = 12.sp, color = glass.primary)
                }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    items(banks, key = { it.id }) { b ->
                        val isActive = activeIds.contains(b.id.toString())
                        GlassCard(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            onClick = { onOpenBank(b.id) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isActive,
                                    onCheckedChange = { bank.toggleActive(b.id); refreshKey++ },
                                    colors = CheckboxDefaults.colors(checkedColor = glass.primary)
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                                    Text(b.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    Text("题目数：${bank.questionCount(b.id)}", fontSize = 11.sp, color = glass.textSecondary)
                                    Text("导入时间：${b.formattedTime()}", fontSize = 11.sp, color = glass.textSecondary)
                                }
                                TextButton(onClick = {
                                    val qs = bank.loadQuestions(b.id)
                                    val sb = StringBuilder()
                                    sb.appendLine("搜题助手 - 题库：${b.name}")
                                    sb.appendLine("题目数：${qs.size}")
                                    sb.appendLine("---")
                                    qs.forEach { q ->
                                        sb.appendLine(q.stem.trim())
                                        q.options.forEachIndexed { i, opt -> sb.appendLine("${('A' + i)}. $opt") }
                                        if (q.answer.isNotEmpty()) sb.appendLine("答案:${q.answer}")
                                        sb.appendLine("")
                                    }
                                    val cacheDir = java.io.File(context.cacheDir, "shared_banks")
                                    cacheDir.mkdirs()
                                    val safeName = b.name.replace(Regex("[^\\w\\u4e00-\\u9fff]"), "_")
                                    val outFile = java.io.File(cacheDir, "${safeName}.txt")
                                    outFile.writeText(sb.toString(), Charsets.UTF_8)
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "分享题库（txt文件）"))
                                }) { Text("分享", color = glass.primary, fontSize = 12.sp) }
                                TextButton(onClick = { bank.deleteBank(b.id); refreshKey++ }) { Text("删除", color = Red, fontSize = 12.sp) }
                            }
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
    val glass = LocalGlass.current
    val bank = remember { QuestionBank(context) }
    var refreshKey by remember { mutableStateOf(0) }
    val questions = remember(refreshKey) { bank.loadQuestions(bankId) }
    val bankInfo = remember(refreshKey) { bank.loadBanks().firstOrNull { it.id == bankId } }

    GlassBackground {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回", tint = glass.textPrimary) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(bankInfo?.name ?: "题库", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${questions.size} 题", fontSize = 12.sp, color = glass.textSecondary)
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(questions, key = { it.id }) { q ->
                    GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text(q.stem, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = glass.textPrimary)
                        if (q.options.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            q.options.forEach { opt ->
                                Text(opt, fontSize = 13.sp, color = glass.textPrimary, modifier = Modifier.padding(start = 4.dp, top = 1.dp))
                            }
                        }
                        if (q.answer.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text("答案: ${q.answer}", fontSize = 13.sp, color = glass.primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = { bank.deleteQuestion(q.id); refreshKey++ },
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("删除", color = Red, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    val glass = LocalGlass.current
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = glass.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun MenuCard(title: String, subtitle: String, onClick: () -> Unit) {
    val glass = LocalGlass.current
    GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = glass.textSecondary)
            }
            Text("›", fontSize = 24.sp, color = glass.textSecondary)
        }
    }
}

@Composable
fun RowScope.BankIcon(emoji: String, title: String, enabled: Boolean, onClick: () -> Unit) {
    val glass = LocalGlass.current
    GlassCard(
        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        onClick = { if (enabled) onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.height(4.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (enabled) glass.textPrimary else glass.textSecondary)
        }
    }
}
