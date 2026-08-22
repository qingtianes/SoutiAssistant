package com.dingding.souti.ui

import com.dingding.souti.model.Question
import com.dingding.souti.repository.QuestionBank
import com.dingding.souti.import.Importer
import com.dingding.souti.import.QuestionChunkParser

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dingding.souti.ui.theme.LocalGlass

@Composable
fun ImportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val bank = remember { QuestionBank(context) }
    var parsedQuestions by remember { mutableStateOf<List<Question>?>(null) }
    var sourceFileName by remember { mutableStateOf("") }
    var sourceModifiedAt by remember { mutableStateOf(0L) }
    var importMsg by remember { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var bankName by remember { mutableStateOf("") }
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
            sourceModifiedAt = try {
                context.contentResolver.query(
                    uri, arrayOf("date_modified"), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) * 1000
                    else 0L
                } ?: 0L
            } catch (_: Exception) { 0L }
            sourceFileName = cleanName
            parsing = true
            importMsg = ""
            importCoverage = 100
            importSourceLen = 0
            importParsedLen = 0
            Thread {
                val result = try {
                    Importer.parse(context, uri, cleanName)
                } catch (e: Throwable) {
                    Importer.ParseResult(
                        emptyList(),
                        error = "解析异常：${e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName}"
                    )
                }
                runOnUiThreadCompat(context) {
                    parsing = false
                    if (result.error != null) {
                        importMsg = result.error
                        return@runOnUiThreadCompat
                    }
                    if (result.chunks.isNotEmpty()) {
                        val importIdBase = System.currentTimeMillis()
                        val questions = result.chunks.mapIndexedNotNull { index, chunk ->
                            QuestionChunkParser.parse(chunk, cleanName, importIdBase + index)
                        }
                        if (questions.size != result.chunks.size) {
                            parsedQuestions = null
                            importMsg = "导入失败：有 ${result.chunks.size - questions.size} 道题无法拆分为有效题目，请检查文件格式。"
                            return@runOnUiThreadCompat
                        }
                        parsedQuestions = questions
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

    GlassBackground {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回", tint = glass.textPrimary) }
                Text("手动导入", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
            }
            Column(modifier = Modifier.padding(16.dp)) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("支持的格式", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = glass.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "✅ .txt / .docx / .pdf（文字版） / .xls\n✅ 三种排版都识别：有序号 / 无序号空行分隔 / 无序号选项对齐\n❌ 扫描版 PDF（无文本层）无法导入",
                        fontSize = 13.sp, color = glass.textPrimary
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { fileLauncher.launch(arrayOf("text/plain", "application/octet-stream", "application/pdf", "*/*")) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !parsing,
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = glass.primary, contentColor = glass.onPrimary)
                ) { Text("选择文件导入（txt/docx/pdf/xls）", fontSize = 15.sp) }
                if (parsing) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = glass.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("正在解析题库...", fontSize = 13.sp, color = glass.textSecondary)
                    }
                }
                if (importMsg.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(importMsg, fontSize = 13.sp, color = glass.textPrimary)
                    }
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
                    Text("文件名：$sourceFileName", fontSize = 11.sp, color = glass.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = bankName, onValueChange = { bankName = it }, singleLine = true, label = { Text("题库名") })
                    Spacer(Modifier.height(8.dp))
                    Text("已识别 ${parsedQuestions!!.size} 道题", fontSize = 12.sp, color = glass.primary)
                    if (importSourceLen > 0) {
                        Spacer(Modifier.height(6.dp))
                        val covColor = when {
                            importCoverage >= 90 -> glass.primary
                            importCoverage >= 70 -> Color(0xFFF57C00)
                            else -> Red
                        }
                        Text(
                            "字数对比：源 ${importSourceLen} / 解析 ${importParsedLen}（覆盖率 ${importCoverage}%）",
                            fontSize = 11.sp, color = covColor
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
                }) { Text("确定导入", color = glass.primary, fontWeight = FontWeight.Bold) }
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
