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
fun App() {
    SoutiAssistantTheme {
        var screen by remember { mutableStateOf("home") }
        var openBankId by remember { mutableStateOf<Long?>(null) }
        when (screen) {
            "home" -> HomeScreen(onNavigate = { screen = it })
            "import" -> ImportScreen(onBack = { screen = "home" })
            "overview" -> OverviewScreen(onBack = { screen = "home" }, onOpenBank = { id -> openBankId = id; screen = "bank" })
            "bank" -> openBankId?.let { BankDetailScreen(bankId = it, onBack = { screen = "overview" }) }
            "scan" -> ScanScreen(onBack = { screen = "home" })
            "settings" -> SettingsScreen(onBack = { screen = "home" })
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
        MenuCard("扫描搜题", "摄像头实时扫描出题") { onNavigate("scan") }
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
        MenuCard("设置", "权限 / 识别匹配 / 浮窗显示 / 扫描 / 关于") { onNavigate("settings") }
        Spacer(Modifier.height(32.dp))
    }
}
