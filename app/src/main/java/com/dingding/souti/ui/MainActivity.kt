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
