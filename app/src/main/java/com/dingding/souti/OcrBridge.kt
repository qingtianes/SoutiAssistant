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
    /** 浮窗区域（屏幕 px 坐标） */
    var pendingRect: Rect = Rect(0, 0, 1, 1)
    /** 是否正在运行 */
    var isRunning: Boolean = false
}
