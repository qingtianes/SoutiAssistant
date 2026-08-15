package com.dingding.souti

import android.graphics.Rect
import android.media.projection.MediaProjection

/**
 * OCR 全局桥接器：MainActivity 拿到 MediaProjection 后存到这里 → FloatWindowService 持续使用
 * 同进程内 IPC（不能用跨进程）
 */
object OcrBridge {
    /** MainActivity 授权后写入；Service 启动 OCR 时读取 */
    var mediaProjection: MediaProjection? = null
    /** 持续模式开关 */
    var continuous: Boolean = false
    /** ★ 读屏模式请求标记（授权成功后启动读屏而非绿框扫描） */
    var screenRead: Boolean = false
    /** 浮窗区域（屏幕 px 坐标） */
    var pendingRect: Rect = Rect(0, 0, 1, 1)
    /** 是否正在运行 */
    var isRunning: Boolean = false

    private var authGeneration: Long = 0
    private var activeAuthRequestId: Long = 0

    @Synchronized
    fun beginAuthRequest(): Long {
        authGeneration += 1
        activeAuthRequestId = authGeneration
        return activeAuthRequestId
    }

    @Synchronized
    fun isAuthRequestActive(requestId: Long): Boolean =
        requestId > 0 && requestId == activeAuthRequestId

    @Synchronized
    fun consumeAuthRequest(requestId: Long): Boolean {
        if (!isAuthRequestActive(requestId)) return false
        activeAuthRequestId = 0
        return true
    }

    @Synchronized
    fun cancelAuthRequest(requestId: Long = 0) {
        if (requestId <= 0 || requestId == activeAuthRequestId) {
            activeAuthRequestId = 0
            authGeneration += 1
        }
    }
    /** ★ 当前运行模式（让 MainActivity 主页能区分显示浮窗搜题/读屏搜题状态） */
    var currentMode: String = MODE_NONE

    const val MODE_NONE = "none"
    const val MODE_FLOAT_WINDOW = "floatWindow"
    const val MODE_SCREEN_READ = "screenRead"
}
