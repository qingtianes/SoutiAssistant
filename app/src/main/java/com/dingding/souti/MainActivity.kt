package com.dingding.souti

import com.dingding.souti.import.Importer

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

// 主题色
val Green = Color(0xFF1D9E75)
val Red = Color(0xFFE24B4A)

class MainActivity : ComponentActivity() {

    private companion object {
        const val STATE_AUTH_REQUEST_ID = "state_auth_request_id"
    }

    val ocrHelper by lazy { OcrHelper(this) }
    private var pendingAuthRequestId: Long = 0

    // 截屏授权回调：授权结果直接传给 FloatWindowService（Service 创建 MediaProjection，Activity 不持有）
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val requestId = pendingAuthRequestId
        if (!OcrBridge.isAuthRequestActive(requestId)) {
            pendingAuthRequestId = 0
            finish()
            return@registerForActivityResult
        }
        if (result.resultCode == RESULT_OK && result.data != null) {
            // 把本次授权会话一并交给 Service；关闭后的迟到结果会被拒绝。
            val svcIntent = Intent(this, FloatWindowService::class.java).apply {
                action = FloatWindowService.ACTION_AUTH_RESULT
                putExtra(FloatWindowService.EXTRA_AUTH_REQUEST_ID, requestId)
                putExtra("result_code", result.resultCode)
                putExtra("result_data", result.data!!)
            }
            startService(svcIntent)
            pendingAuthRequestId = 0
            finish()
        } else {
            OcrBridge.cancelAuthRequest(requestId)
            pendingAuthRequestId = 0
            finish()
        }
    }

    /** 给 FloatWindowService 调用：发起 OCR 授权请求 */
    fun startOcrRequest(rect: Rect, continuous: Boolean = false, screenRead: Boolean = false) {
        if (pendingAuthRequestId > 0 && OcrBridge.isAuthRequestActive(pendingAuthRequestId)) {
            Log.w("MainActivity", "已有录屏授权请求进行中，忽略重复请求")
            return
        }
        OcrBridge.pendingRect = rect
        OcrBridge.continuous = continuous
        OcrBridge.screenRead = screenRead
        pendingAuthRequestId = OcrBridge.beginAuthRequest()
        ocrHelper.startOcr(rect, projectionLauncher)
    }

    /** 停止持续 OCR（让浮窗关闭时调用） */
    fun stopOcrContinuous() {
        OcrBridge.isRunning = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAuthRequestId = savedInstanceState?.getLong(STATE_AUTH_REQUEST_ID, 0) ?: 0
        if (pendingAuthRequestId > 0 && !OcrBridge.isAuthRequestActive(pendingAuthRequestId)) {
            // 进程重建后内存中的授权会话已经丢失；不要自动叠加第二个系统授权窗口。
            pendingAuthRequestId = 0
            finish()
            return
        }
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
            val screenRead = intent.getBooleanExtra("ocr_screen_read", false)
            startOcrRequest(rect, continuous, screenRead)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(STATE_AUTH_REQUEST_ID, pendingAuthRequestId)
        super.onSaveInstanceState(outState)
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
    var screenReadRunning by remember { mutableStateOf(false) }
    var floatError by remember { mutableStateOf("") }  // ★ Service 启动失败信息
    // ★ 轮询 Service 真实状态（用 ActivityManager 校验自家服务，Android 14+ 允许）
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("souti_state", android.content.Context.MODE_PRIVATE)
        while (true) {
            // Android 14+ 允许自家服务的 getRunningServices（被禁的只是其他应用）
            val actuallyRunning = isServiceRunning(context, FloatWindowService::class.java)
            // ★ 根据 OcrBridge.currentMode 区分两种模式（避免两种模式互相误显示状态）
            val mode = OcrBridge.currentMode
            val newFloatRunning = actuallyRunning && mode == OcrBridge.MODE_FLOAT_WINDOW
            val newScreenReadRunning = actuallyRunning && mode == OcrBridge.MODE_SCREEN_READ
            if (floatRunning != newFloatRunning) floatRunning = newFloatRunning
            if (screenReadRunning != newScreenReadRunning) screenReadRunning = newScreenReadRunning
            // 同步真实状态到 prefs（防止进程 kill 后 prefs 漂移）
            val prefsRunning = prefs.getBoolean("service_running", false)
            if (actuallyRunning != prefsRunning) {
                prefs.edit().putBoolean("service_running", actuallyRunning).apply()
            }
            // 同步错误信息（Service 启动成功时它会自动清空）
            val err = prefs.getString("last_error", "") ?: ""
            if (err != floatError) {
                floatError = err
            }
            kotlinx.coroutines.delay(500)
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
                // ★ Service 启动失败时显示错误
                if (floatError.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text("⚠ 启动失败：$floatError", fontSize = 10.sp, color = Red)
                }
                Spacer(Modifier.height(12.dp))
Button(
                    onClick = {
                        when {
                            !hasOverlayPermission -> context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                            floatRunning -> {
                                // ★ 完全关闭服务：发 ACTION_STOP_SELF（Android 14+ 前台服务必须先 stopForeground）
                                val stopIntent = Intent(context, FloatWindowService::class.java).apply {
                                    action = FloatWindowService.ACTION_STOP_SELF
                                }
                                OcrBridge.cancelAuthRequest()
                                context.startService(stopIntent)
                                floatRunning = false
                            }
                            else -> {
                                // ★ 启动服务（首次或完全关闭后启动）—— 带 ACTION_START_SCAN 让 Service 立即显示主浮窗
                                //    之前只用 startForegroundService 不传 action，导致 Service 只做 onCreate 初始化
                                //    不显示主浮窗（onCreate 已不再自动 showFloatWindow）→ 浮窗"打不开"
                                context.startForegroundService(Intent(context, FloatWindowService::class.java).apply {
                                    action = FloatWindowService.ACTION_START_SCAN
                                })
                                floatRunning = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (floatRunning) Red else Green)
                ) { Text(when { !hasOverlayPermission -> "授权悬浮窗权限"; floatRunning -> "关闭服务（需重新授权）"; else -> "启动服务" }, fontSize = 15.sp) }
            }
        }
        MenuCard(
            title = "读屏搜题",
            subtitle = if (screenReadRunning) "运行中：右上角小窗 ✕ 关闭"
                       else "全屏自动识别，答案小窗输出（不挡作答）"
        ) {
            // ★ 读屏模式：需要悬浮窗权限 + MediaProjection 授权
            if (!Settings.canDrawOverlays(context)) {
                // 没有悬浮窗权限 → 引导授权
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            } else {
                // 有悬浮窗权限 → 发读屏启动（无 MediaProjection 时 Service 内部引导授权）
                val svcIntent = Intent(context, FloatWindowService::class.java).apply {
                    action = FloatWindowService.ACTION_SCREEN_READ_START
                }
                context.startService(svcIntent)
                // 给个提示（如果正在读屏，提示怎么停）
                android.widget.Toast.makeText(
                    context,
                    "读屏搜题已启动：小窗右上角 ✕ 关闭",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        MenuCard("扫描搜题", "摄像头实时扫描出题（待开发）") {}
        MenuCard("AI 搜题", "在线大模型搜题（待开发）") {}
        Spacer(Modifier.height(16.dp))
        SectionTitle("题库管理")
        // ★ 横向 4 图标网格（智能导入 / 题库总览 / 公共题库 / 远程导入）
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            BankIcon("📁", "智能导入", enabled = true) { onNavigate("import") }
            BankIcon("📚", "题库总览", enabled = true) { onNavigate("overview") }
            BankIcon("🌐", "公共题库", enabled = false) {}
            BankIcon("🔗", "远程导入", enabled = false) {}
        }
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
private fun isServiceRunning(context: android.content.Context, serviceClass: Class<*>): Boolean {
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
