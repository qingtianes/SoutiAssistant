package com.dingding.souti

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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

// 主题色
val Green = Color(0xFF1D9E75)
val Red = Color(0xFFE24B4A)

class MainActivity : ComponentActivity() {

    val ocrHelper by lazy { OcrHelper(this) }

    // 截屏授权回调：授权结果直接传给 FloatWindowService（Service 创建 MediaProjection，Activity 不持有）
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            // ★ 把 resultCode + data 传给 Service，由 Service getMediaProjection（Android 14 必须前台服务创建）
            val svcIntent = Intent(this, FloatWindowService::class.java).apply {
                action = FloatWindowService.ACTION_AUTH_RESULT
                putExtra("result_code", result.resultCode)
                putExtra("result_data", result.data!!)
            }
            startService(svcIntent)
            finish()  // 透明 Activity 立即关闭
        } else {
            finish()  // 用户取消授权
        }
    }

    /** 给 FloatWindowService 调用：发起 OCR 授权请求 */
    fun startOcrRequest(rect: Rect, continuous: Boolean = false) {
        OcrBridge.pendingRect = rect
        OcrBridge.continuous = continuous
        ocrHelper.startOcr(rect, projectionLauncher)
    }

    /** 停止持续 OCR（让浮窗关闭时调用） */
    fun stopOcrContinuous() {
        OcrBridge.isRunning = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isOcrRequest = intent?.getBooleanExtra("ocr_request", false) == true
        if (isOcrRequest) {
            // ★ OCR 模式：透明背景，不显示主页内容（避免遮挡题库页面让 OCR 截到主页）
            setContent { /* 空 - 透明背景 */ }
        } else {
            // 普通启动：显示搜题助手主页
            setContent {
                SoutiAssistantTheme {
                    App()
                }
            }
        }
        handleOcrIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val isOcrRequest = intent.getBooleanExtra("ocr_request", false)
        if (isOcrRequest) {
            // OCR 模式：切到透明背景（不显示主页内容）
            setContent { /* 空 - 透明背景 */ }
        }
        handleOcrIntent(intent)
    }

    private fun handleOcrIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("ocr_request", false) == true) {
            val rect = Rect(
                intent.getIntExtra("rect_left", 0),
                intent.getIntExtra("rect_top", 0),
                intent.getIntExtra("rect_right", 1),
                intent.getIntExtra("rect_bottom", 1)
            )
            val continuous = intent.getBooleanExtra("ocr_continuous", false)
            startOcrRequest(rect, continuous)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ocrHelper.destroy()
    }
}

@Composable
fun App() {
    SoutiAssistantTheme {
        var screen by remember { mutableStateOf("home") }
        var openBankId by remember { mutableStateOf<Long?>(null) }
        when (screen) {
            "home" -> HomeScreen(onNavigate = { screen = it })
            "import" -> ImportScreen(onBack = { screen = "home" })
            "overview" -> OverviewScreen(onBack = { screen = "home" }, onOpenBank = { id -> openBankId = id; screen = "bank" })
            "bank" -> openBankId?.let { BankDetailScreen(bankId = it, onBack = { screen = "overview" }) }
        }
    }
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var floatRunning by remember { mutableStateOf(false) }
    // ★ 轮询 Service 状态（FloatWindowService 启动/停止时写 SharedPreferences）
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("souti_state", android.content.Context.MODE_PRIVATE)
        while (true) {
            floatRunning = prefs.getBoolean("service_running", false)
            kotlinx.coroutines.delay(500)  // 500ms 轮询
        }
    }
    var activeCount by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val bank = remember { QuestionBank(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                activeCount = bank.getActiveBankIds().size
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F7F7)).verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp)) {
        Text("搜题助手", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Green)
        Text("悬浮窗识别 + 自定义题库搜索", fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(20.dp))
        SectionTitle("搜题方式")
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("浮窗搜题", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222222))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (floatRunning) Green else if (hasOverlayPermission) Color(0xFFCCCCCC) else Red))
                    Spacer(Modifier.width(4.dp))
                    Text(when { floatRunning -> "运行中"; hasOverlayPermission -> "未开启"; else -> "未授权" }, fontSize = 12.sp, color = Color.Gray)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (activeCount == 0) "⚠ 未勾选任何题库（去题库总览勾选后再启动）" else "✓ 已勾选 $activeCount 个题库用于搜题",
                    fontSize = 11.sp, color = if (activeCount == 0) Red else Green
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        when {
                            !hasOverlayPermission -> context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                            floatRunning -> {
                                // ★ 完全关闭服务：stopService + 清 OcrBridge（下次启动需重新授权）
                                context.stopService(Intent(context, FloatWindowService::class.java))
                                OcrBridge.mediaProjection = null
                                floatRunning = false
                            }
                            else -> {
                                // ★ 启动服务（首次或完全关闭后启动）
                                context.startForegroundService(Intent(context, FloatWindowService::class.java))
                                floatRunning = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (floatRunning) Red else Green)
                ) { Text(when { !hasOverlayPermission -> "授权悬浮窗权限"; floatRunning -> "关闭服务（需重新授权）"; else -> "启动服务" }, fontSize = 15.sp) }
            }
        }
        MenuCard("读屏搜题", "录屏实时检测全屏题目（OCR 明早实现）") {}
        MenuCard("扫描搜题", "摄像头实时扫描出题（待开发）") {}
        MenuCard("AI 搜题", "在线大模型搜题（待开发）") {}
        Spacer(Modifier.height(16.dp))
        SectionTitle("题库管理")
        MenuCard("AI 导入", "喂文档，AI 自动解析题干/选项/答案（待开发）") {}
        MenuCard("手动导入", "选 .txt 文件，自定义解析") { onNavigate("import") }
        MenuCard("题库总览", "查看已导入题库") { onNavigate("overview") }
        Spacer(Modifier.height(16.dp))
        SectionTitle("设置")
        MenuCard("权限管理", "悬浮窗 / 截屏 / 摄像头 / 通知") {}
        MenuCard("OCR 引擎", "中文 ML Kit 离线识别（明早集成）") {}
        // ★ 调试开关：控制浮窗红色边框（OCR 截屏范围）显示
        val context = LocalContext.current
        var showDebugBorder by remember { mutableStateOf(false) }
        MenuCard(
            title = "显示 OCR 识别范围",
            subtitle = if (showDebugBorder) "开启：浮窗红色边框显示 OCR 真实截屏范围" else "关闭：浮窗干净无调试元素",
            onClick = {
                showDebugBorder = !showDebugBorder
                val intent = Intent(context, FloatWindowService::class.java).apply {
                    action = FloatWindowService.ACTION_TOGGLE_DEBUG
                }
                try { context.startService(intent) } catch (_: Exception) {}
            }
        )
        MenuCard("关于", "搜题助手 v1.0") {}
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun ImportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val bank = remember { QuestionBank(context) }
    var parsedQuestions by remember { mutableStateOf<List<Question>?>(null) }
    var sourceFileName by remember { mutableStateOf("") }
    var importMsg by remember { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var bankName by remember { mutableStateOf("") }

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
            sourceFileName = cleanName
            parsing = true
            importMsg = ""
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
                    Text("✅ .txt / .docx / .pdf（文字版）\n✅ 三种排版都识别：有序号 / 无序号空行分隔 / 无序号选项对齐\n❌ 扫描版 PDF（无文本层）无法导入\n❌ .xls 请用电脑端转换工具转 txt 后导入", fontSize = 13.sp, color = Color(0xFF333333))
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { fileLauncher.launch(arrayOf("text/plain", "application/octet-stream", "application/pdf", "*/*")) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !parsing,
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) { Text("选择文件导入（.txt/.docx/.pdf）", fontSize = 15.sp) }
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
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = bankName.trim().ifEmpty { "未命名题库" }
                    bank.importBank(name = name, sourceFile = sourceFileName, type = "manual", questions = parsedQuestions!!)
                    importMsg = "导入成功！\n题库名：$name\n题目数：${parsedQuestions!!.size}\n（源文件 ${if (sourceFileName.endsWith(".xls") || sourceFileName.endsWith(".xlsx")) "请用电脑工具转换" else "已自动转换"}）"
                    parsedQuestions = null
                    showNameDialog = false
                }) { Text("确定导入", color = Green, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = false; parsedQuestions = null }) { Text("取消", color = Red) } }
        )
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
                                Text("${bank.questionCount(b.id)} 题 · ${b.formattedTime()}", fontSize = 12.sp, color = Color.Gray)
                            }
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
