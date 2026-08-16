package com.dingding.souti.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.core.content.ContextCompat
import com.dingding.souti.repository.QuestionRepository
import com.dingding.souti.repository.SettingsStore
import com.dingding.souti.ui.theme.LocalGlass

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 结果回到系统授权页即可 */ }

    var showClearDialog by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "返回", tint = glass.textPrimary)
                }
                Text("设置", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
            }
            Spacer(Modifier.height(8.dp))

            GlassSectionTitle("权限管理")
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                PermissionRow("悬浮窗权限", Settings.canDrawOverlays(context)) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                }
                PermissionRow("摄像头权限", hasCamera(context)) {
                    cameraLauncher.launch(Manifest.permission.CAMERA)
                }
                PermissionRow("通知权限", hasNotification(context)) {
                    openNotificationSettings(context)
                }
                PermissionRow("录屏权限", true) {
                    Toast.makeText(context, "首次使用浮窗/读屏时，系统会弹出录屏授权", Toast.LENGTH_SHORT).show()
                }
            }

            GlassSectionTitle("识别与匹配")
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                ChoiceRow("最多显示结果", listOf("1", "3", "5", "10"), SettingsStore.resultLimit(context).toString()) {
                    SettingsStore.setResultLimit(context, it.toInt())
                }
                SliderRow("最低匹配分", SettingsStore.minScore(context), "${SettingsStore.minScore(context).toInt()} 分") {
                    SettingsStore.setMinScore(context, it)
                }
                ChoiceRow("识别速度", listOf("快", "标准", "省电"), speedLabel(SettingsStore.ocrSpeed(context))) {
                    SettingsStore.setOcrSpeed(context, speedKey(it))
                }
            }

            GlassSectionTitle("浮窗显示")
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                ChoiceRow("结果字号", listOf("小", "中", "大"), fontLabel(SettingsStore.fontScale(context))) {
                    SettingsStore.setFontScale(context, fontKey(it))
                }
                SwitchRow("显示匹配分与来源", "关闭后只显示题干和答案", SettingsStore.showMeta(context)) {
                    SettingsStore.setShowMeta(context, it)
                }
                ChoiceRow("绿框默认大小", listOf("小", "中", "大"), SettingsStore.frameSize(context)) {
                    SettingsStore.setFrameSize(context, it)
                }
            }

            GlassSectionTitle("扫描搜题")
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                ChoiceRow("默认缩放倍数", listOf("1.0x", "1.5x", "2.0x"), "${SettingsStore.scanZoom(context)}x") {
                    SettingsStore.setScanZoom(context, it.removeSuffix("x").toFloat())
                }
                ChoiceRow("取景框高度", listOf("单行", "双行"), viewfinderLabel(SettingsStore.viewfinderHeight(context))) {
                    SettingsStore.setViewfinderHeight(context, viewfinderKey(it))
                }
            }

            GlassSectionTitle("通用 / 关于")
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                InfoRow("当前版本", appVersion(context))
                InfoRow("更新日志", "查看 v1.0 新增与修复") { showChangelog = true }
                InfoRow("隐私说明", "OCR 完全本机处理，不上传任何内容") { showPrivacy = true }
                InfoRow("意见反馈", "前往 GitHub 提交问题") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/qingtianes/SoutiAssistant/issues")))
                }
                InfoRow("清空本地题库与设置", "删除已导入题库并恢复默认设置", danger = true) { showClearDialog = true }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("确认清空？") },
            text = { Text("将删除全部已导入题库，并把设置恢复为默认值。此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    QuestionRepository(context).clear()
                    SettingsStore.reset(context)
                    showClearDialog = false
                    Toast.makeText(context, "已清空本地题库与设置", Toast.LENGTH_SHORT).show()
                }) { Text("清空", color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }
    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text("更新日志（v1.0）") },
            text = { Text("新增：设置中心、扫描搜题暂停/继续、识别与匹配调节、浮窗显示调节。\n优化：扫描取景框、结果卡片透明化。") },
            confirmButton = { TextButton(onClick = { showChangelog = false }) { Text("知道了") } }
        )
    }
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("隐私说明") },
            text = { Text("本应用所有 OCR 识别均在本机离线完成；截屏与摄像头画面只在内存中处理，不上传到任何服务器。题库数据保存在手机本地。") },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("知道了") } }
        )
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp, color = glass.textPrimary, modifier = Modifier.weight(1f))
        Text(if (granted) "已授权" else "未授权", fontSize = 12.sp, color = if (granted) glass.primary else Red)
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val glass = LocalGlass.current
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, fontSize = 15.sp, color = glass.textPrimary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                GlassChip(option, selected = option == selected, onClick = { onSelect(option) })
            }
        }
    }
}

@Composable
private fun SliderRow(title: String, value: Float, valueText: String, onValue: (Float) -> Unit) {
    val glass = LocalGlass.current
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 15.sp, color = glass.textPrimary, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = 13.sp, color = glass.primary)
        }
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = glass.primary,
                activeTrackColor = glass.primary,
                inactiveTrackColor = glass.textSecondary.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val glass = LocalGlass.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = glass.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = glass.textSecondary)
        }
        GlassSwitch(checked = checked, onChange = onChecked)
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String, danger: Boolean = false, onClick: (() -> Unit)? = null) {
    val glass = LocalGlass.current
    val clickable = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Row(
        modifier = Modifier.fillMaxWidth().then(clickable).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = if (danger) Red else glass.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = glass.textSecondary)
        }
    }
}

private fun hasCamera(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun hasNotification(context: Context): Boolean =
    androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    context.startActivity(intent)
}

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
} catch (_: Exception) { "1.0" }

private fun speedKey(label: String) = when (label) { "快" -> "fast"; "省电" -> "slow"; else -> "normal" }
private fun speedLabel(key: String) = when (key) { "fast" -> "快"; "slow" -> "省电"; else -> "标准" }
private fun fontKey(label: String) = when (label) { "小" -> "small"; "大" -> "large"; else -> "medium" }
private fun fontLabel(key: String) = when (key) { "small" -> "小"; "large" -> "大"; else -> "中" }
private fun viewfinderKey(label: String) = if (label == "单行") "single" else "double"
private fun viewfinderLabel(key: String) = if (key == "single") "单行" else "双行"
