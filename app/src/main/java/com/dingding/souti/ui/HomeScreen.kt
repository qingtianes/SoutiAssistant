package com.dingding.souti.ui

import com.dingding.souti.repository.QuestionBank
import com.dingding.souti.ocr.OcrBridge
import com.dingding.souti.overlay.FloatWindowService

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dingding.souti.ui.theme.SoutiAssistantTheme
import com.dingding.souti.ui.theme.SoutiThemeController
import com.dingding.souti.ui.theme.SoutiThemeMode
import com.dingding.souti.ui.theme.LocalGlass

@Composable
fun App() {
    val context = LocalContext.current
    var themeMode by remember { mutableStateOf(SoutiThemeController.mode(context)) }
    val dark = when (themeMode) {
        SoutiThemeMode.LIGHT -> false
        SoutiThemeMode.DARK -> true
        SoutiThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    SoutiAssistantTheme(darkTheme = dark) {
        var screen by remember { mutableStateOf("home") }
        var openBankId by remember { mutableStateOf<Long?>(null) }
        val toggleTheme = {
            val newMode = if (dark) SoutiThemeMode.LIGHT else SoutiThemeMode.DARK
            themeMode = newMode
            SoutiThemeController.setMode(context, newMode)
        }
        when (screen) {
            "home" -> HomeScreen(
                onNavigate = { screen = it },
                isDark = dark,
                onToggleTheme = toggleTheme
            )
            "import" -> ImportScreen(onBack = { screen = "home" })
            "overview" -> OverviewScreen(onBack = { screen = "home" }, onOpenBank = { id -> openBankId = id; screen = "bank" })
            "bank" -> openBankId?.let { BankDetailScreen(bankId = it, onBack = { screen = "overview" }) }
            "scan" -> ScanScreen(onBack = { screen = "home" })
            "settings" -> SettingsScreen(onBack = { screen = "home" })
            "help" -> UsageGuideScreen(onBack = { screen = "home" })
        }
    }
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit, isDark: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var floatRunning by remember { mutableStateOf(false) }
    var screenReadRunning by remember { mutableStateOf(false) }
    var floatError by remember { mutableStateOf("") }
    // ★ 轮询 Service 真实状态（用 ActivityManager 校验自家服务，Android 14+ 允许）
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("souti_state", android.content.Context.MODE_PRIVATE)
        while (true) {
            val actuallyRunning = isServiceRunning(context, FloatWindowService::class.java)
            val mode = OcrBridge.currentMode
            val newFloatRunning = actuallyRunning && mode == OcrBridge.MODE_FLOAT_WINDOW
            val newScreenReadRunning = actuallyRunning && mode == OcrBridge.MODE_SCREEN_READ
            if (floatRunning != newFloatRunning) floatRunning = newFloatRunning
            if (screenReadRunning != newScreenReadRunning) screenReadRunning = newScreenReadRunning
            val prefsRunning = prefs.getBoolean("service_running", false)
            if (actuallyRunning != prefsRunning) {
                prefs.edit().putBoolean("service_running", actuallyRunning).apply()
            }
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

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("搜题助手", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                    Text("浮窗 · 读屏 · 摄像头 · 本地题库", fontSize = 13.sp, color = glass.textSecondary)
                }
                ThemeToggle(isDark = isDark, onToggle = onToggleTheme)
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { onNavigate("help") },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(glass.primary)
                ) {
                    Icon(Icons.Filled.Info, contentDescription = "使用说明", tint = glass.onPrimary)
                }
            }
            Spacer(Modifier.height(16.dp))
            GlassSectionTitle("题库")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassBankTile("📁", "智能导入") { onNavigate("import") }
                GlassBankTile("📚", "题库总览") { onNavigate("overview") }
            }
            GlassSectionTitle("快捷搜题")
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("浮窗搜题", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                    Spacer(Modifier.weight(1f))
                    val dotColor = if (floatRunning) glass.primary else if (hasOverlayPermission) glass.textSecondary else Red
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when { floatRunning -> "运行中"; hasOverlayPermission -> "未开启"; else -> "未授权" },
                        fontSize = 12.sp, color = glass.textSecondary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("绿框实时识别 · 单题答案输出", fontSize = 12.sp, color = glass.textSecondary)
                if (activeCount == 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("⚠ 未勾选任何题库（去题库总览勾选后再启动）", fontSize = 11.sp, color = Red)
                }
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
                                val stopIntent = Intent(context, FloatWindowService::class.java).apply {
                                    action = FloatWindowService.ACTION_STOP_SELF
                                }
                                OcrBridge.cancelAuthRequest()
                                context.startService(stopIntent)
                                floatRunning = false
                            }
                            else -> {
                                context.startForegroundService(Intent(context, FloatWindowService::class.java).apply {
                                    action = FloatWindowService.ACTION_START_SCAN
                                })
                                floatRunning = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (floatRunning) Red else glass.primary, contentColor = glass.onPrimary)
                ) {
                    Text(when { !hasOverlayPermission -> "授权悬浮窗权限"; floatRunning -> "关闭服务"; else -> "启动服务" }, fontSize = 15.sp)
                }
            }
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                onClick = {
                    if (!Settings.canDrawOverlays(context)) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    } else if (screenReadRunning) {
                        android.widget.Toast.makeText(context, "读屏搜题运行中：小窗右上角 ✕ 关闭", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        val svcIntent = Intent(context, FloatWindowService::class.java).apply {
                            action = FloatWindowService.ACTION_SCREEN_READ_START
                        }
                        context.startService(svcIntent)
                        android.widget.Toast.makeText(context, "读屏搜题已启动：小窗右上角 ✕ 关闭", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("读屏搜题", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                        Spacer(Modifier.height(3.dp))
                        Text(if (screenReadRunning) "运行中：右上角小窗 ✕ 关闭" else "全屏识别 · 多题答案输出", fontSize = 12.sp, color = glass.textSecondary)
                    }
                    if (screenReadRunning) Box(Modifier.size(8.dp).clip(CircleShape).background(glass.primary)) else Text("›", fontSize = 24.sp, color = glass.textSecondary)
                }
            }
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                onClick = { onNavigate("scan") }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("扫描搜题", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                        Spacer(Modifier.height(3.dp))
                        Text("摄像头实时扫描 · 取景框识别", fontSize = 12.sp, color = glass.textSecondary)
                    }
                    Text("›", fontSize = 24.sp, color = glass.textSecondary)
                }
            }
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("AI 搜题", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = glass.textSecondary)
                    Spacer(Modifier.weight(1f))
                    Text("待开发", fontSize = 12.sp, color = glass.textSecondary)
                }
            }
            GlassSectionTitle("设置")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("settings") }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("设置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                        Spacer(Modifier.height(3.dp))
                        Text("权限 / 识别匹配 / 浮窗显示 / 扫描 / 关于", fontSize = 12.sp, color = glass.textSecondary)
                    }
                    Text("›", fontSize = 24.sp, color = glass.textSecondary)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 玻璃拟态题库入口块（图标 + 标题）。 */
@Composable
private fun RowScope.GlassBankTile(emoji: String, title: String, onClick: () -> Unit) {
    val glass = LocalGlass.current
    GlassCard(
        modifier = Modifier.weight(1f).padding(bottom = 8.dp),
        onClick = onClick
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.height(6.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
        }
    }
}
