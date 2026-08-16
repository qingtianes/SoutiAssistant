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
fun ImportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val bank = remember { QuestionBank(context) }
    var parsedQuestions by remember { mutableStateOf<List<Question>?>(null) }
    var sourceFileName by remember { mutableStateOf("") }
    var sourceModifiedAt by remember { mutableStateOf(0L) }  // ★ 源文件修改时间
    var importMsg by remember { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var bankName by remember { mutableStateOf("") }
    // ★ 字数验证状态
    var importCoverage by remember { mutableStateOf(100) }
    var importSourceLen by remember { mutableStateOf(0) }
    var importParsedLen by remember { mutableStateOf(0) }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val fileName = try {
                context.contentResolver.query(
                    uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: uri.lastPathSegment ?: "未知题库"
            } catch (_: Exception) { uri.lastPathSegment ?: "未知题库" }
            val cleanName = fileName.substringAfterLast('/').substringAfterLast("%2F").replace("%20", " ")
            // ★ 读取源文件最后修改时间（DocumentProvider 标准字段 "date_modified"，兼容所有 API）
            // 直接给外层 state 赋值（不要局部 val 同名覆盖）
            sourceModifiedAt = try {
                context.contentResolver.query(
                    uri, arrayOf("date_modified"), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) * 1000  // 秒 → 毫秒
                    else 0L
                } ?: 0L
            } catch (_: Exception) { 0L }
            sourceFileName = cleanName
            parsing = true
            importMsg = ""
            importCoverage = 100
            importSourceLen = 0
            importParsedLen = 0
            // 后台线程解析（大文件不阻塞 UI）
            Thread {
                val result = try {
                    Importer.parse(context, uri, cleanName)
                } catch (e: Exception) {
                    Importer.ParseResult(emptyList(), error = "解析异常：${e.message}")
                }
                runOnUiThreadCompat(context) {
                    parsing = false
                    if (result.error != null) {
                        importMsg = result.error
                        return@runOnUiThreadCompat
                    }
                    if (result.chunks.isNotEmpty()) {
                        // 切块模式：每块整段作为题干（题干+选项+答案原样保留）
                        val questions = result.chunks.map { chunk ->
                            Question(
                                id = System.currentTimeMillis() + (0..999).random(),
                                bankId = 0,
                                stem = chunk,
                                options = emptyList(),
                                answer = "",
                                source = cleanName
                            )
                        }
                        parsedQuestions = questions
                        // ★ 字数验证：覆盖率低时直接拒绝导入
                        val cov = result.coverage()
                        if (result.sourceLength > 0 && cov < 60) {
                            parsedQuestions = null
                            importMsg = "导入验证失败：字数覆盖率仅 $cov%\n源文件 ${result.sourceLength} 字，解析出 ${result.parsedLength} 字\n大量题目可能未被识别，请人工检查源文件后重新导入。"
                            return@runOnUiThreadCompat
                        }
                        importCoverage = cov
                        importSourceLen = result.sourceLength
                        importParsedLen = result.parsedLength
                        val defaultName = cleanName.substringBeforeLast(".")
                        bankName = if (defaultName.isNotBlank() && defaultName != cleanName) defaultName else cleanName
                        showNameDialog = true
                    } else {
                        importMsg = "解析失败：未识别到题目"
                    }
                }
            }.start()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F7F7)).statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("手动导入", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Green)
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("支持的格式", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6A1B9A))
                    Spacer(Modifier.height(8.dp))
                    Text("✅ .txt / .docx / .pdf（文字版） / .xls\n✅ 三种排版都识别：有序号 / 无序号空行分隔 / 无序号选项对齐\n❌ 扫描版 PDF（无文本层）无法导入", fontSize = 13.sp, color = Color(0xFF333333))
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { fileLauncher.launch(arrayOf("text/plain", "application/octet-stream", "application/pdf", "*/*")) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !parsing,
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) { Text("选择文件导入（txt/docx/pdf/xls）", fontSize = 15.sp) }
            if (parsing) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Green)
                    Spacer(Modifier.width(12.dp))
                    Text("正在解析题库...", fontSize = 13.sp, color = Color(0xFF666666))
                }
            }
            if (importMsg.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                    Text(importMsg, modifier = Modifier.padding(12.dp), fontSize = 13.sp, color = Color(0xFF1B5E20))
                }
            }
        }
    }

    if (showNameDialog && parsedQuestions != null) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("为题库起个名字") },
            text = {
                Column {
                    Text("文件名：$sourceFileName", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = bankName, onValueChange = { bankName = it }, singleLine = true, label = { Text("题库名") })
                    Spacer(Modifier.height(8.dp))
                    Text("已识别 ${parsedQuestions!!.size} 道题", fontSize = 12.sp, color = Green)
                    // ★ 字数验证显示
                    if (importSourceLen > 0) {
                        Spacer(Modifier.height(6.dp))
                        val covColor = when {
                            importCoverage >= 90 -> Green
                            importCoverage >= 70 -> Color(0xFFF57C00) // 橙
                            else -> Red
                        }
                        Text(
                            "字数对比：源 ${importSourceLen} / 解析 ${importParsedLen}（覆盖率 ${importCoverage}%）",
                            fontSize = 11.sp,
                            color = covColor
                        )
                        if (importCoverage < 90) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (importCoverage >= 70) "⚠️ 少量差异（可能是章节标题/格式被忽略，可接受）"
                                else "⚠️ 字数差距较大！可能有题目未被识别，建议检查源文件",
                                fontSize = 10.sp,
                                color = if (importCoverage >= 70) Color(0xFFF57C00) else Red
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = bankName.trim().ifEmpty { "未命名题库" }
                    bank.importBank(name = name, sourceFile = sourceFileName, sourceModifiedAt = sourceModifiedAt, type = "manual", questions = parsedQuestions!!)
                    importMsg = "导入成功！\n题库名：$name\n题目数：${parsedQuestions!!.size}"
                    parsedQuestions = null
                    showNameDialog = false
                }) { Text("确定导入", color = Green, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = false; parsedQuestions = null }) { Text("取消", color = Red) } }
        )
    }
}

/** 主动校验 Service 真实状态（Android 14+ 限制 getRunningServices，必须 try-catch） */
fun isServiceRunning(context: android.content.Context, serviceClass: Class<*>): Boolean {
    return try {
        val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        manager?.getRunningServices(Int.MAX_VALUE)
            ?.any { it.service.className == serviceClass.name } ?: false
    } catch (e: Throwable) {
        // Android 14+ 普通应用不能 getRunningServices（SecurityException），返回 false
        false
    }
}

/** 在 Compose 外跑 UI 线程（导入解析用的 Thread 里） */
private fun runOnUiThreadCompat(context: android.content.Context, block: () -> Unit) {
    if (context is android.app.Activity) {
        context.runOnUiThread(block)
    } else {
        block()
    }
}
