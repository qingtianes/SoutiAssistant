package com.dingding.souti

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import android.view.WindowManager
/**
 * 悬浮窗服务：在任意应用上方显示可拖动的搜题悬浮窗
 *
 * 双模式：
 *  - 待机模式：识别框（透明边框） + 状态 + 关闭 + 拖拽手柄 + 搜题按钮
 *  - 搜题模式：题干输入框 + 搜索按钮 + 结果列表（题干/选项/答案）
 */
class FloatWindowService : Service() {

    companion object {
        private const val CHANNEL_ID = "souti_float"
        private const val NOTIFICATION_ID = 1
        const val ACTION_AUTH_OCR = "com.dingding.souti.AUTH_OCR"
        const val ACTION_START_SCAN = "com.dingding.souti.START_SCAN"
        const val ACTION_STOP_SCAN = "com.dingding.souti.STOP_SCAN"
        const val ACTION_AUTH_RESULT = "com.dingding.souti.AUTH_RESULT"
        /** ★ 切换红色边框（OCR 范围调试用） */
        const val ACTION_TOGGLE_DEBUG = "com.dingding.souti.TOGGLE_DEBUG"
        /** ★ 重新显示浮窗（Service 已在跑，只是 root 被隐藏） */
        const val ACTION_SHOW_WINDOW = "com.dingding.souti.SHOW_WINDOW"
        /** ★ 内部停止（Android 14+ 前台服务必须用 stopForeground 才能真的停） */
        const val ACTION_STOP_SELF = "com.dingding.souti.STOP_SELF"
        /** ★ 读屏模式启动（全屏自动识别，输出到独立答案小窗） */
        const val ACTION_SCREEN_READ_START = "com.dingding.souti.SCREEN_READ_START"
        /** ★ 读屏模式停止 */
        const val ACTION_SCREEN_READ_STOP = "com.dingding.souti.SCREEN_READ_STOP"
        /** ★ 绿框 View 的稳定 ID（用 getLocationOnScreen 获取真实屏幕坐标） */
        private const val R_ID_RECOGNIZE = 0x7F010001
    }

    private lateinit var windowManager: WindowManager
    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
    /** ★ 顶栏最小化按钮引用（用于 minimizeToDot 时计算屏幕坐标和尺寸） */
    private var closeBtnRef: View? = null
    /** ★ 最小化圆点窗口（独立 40x40 小窗口，最可靠方案） */
    private var minimizedDot: View? = null
    private var minimizedDotParams: WindowManager.LayoutParams? = null
    private var isMinimized: Boolean = false
    private val bank by lazy { QuestionBank(this) }

    // ★★★ 读屏模式（全屏自动识别）状态 ★★★
    /** 读屏模式开关（开：全屏截屏 → 滚动防抖 → OCR → 多题匹配 → 独立答案小窗） */
    private var screenReadActive: Boolean = false
    /** 读屏模式独立答案小窗（半透明、可拖动、可缩放，不挡作答） */
    private var screenReadWindow: View? = null
    private var screenReadParams: WindowManager.LayoutParams? = null
    /** 读屏模式答案列表容器（小窗内的 ScrollView 内容区） */
    private var screenReadContainer: LinearLayout? = null
    /** 读屏模式滚动检测：上一帧缩略图（已废弃：滚动检测在中文整屏场景太敏感反误判，去掉了） */
    private var lastFrameThumb: Bitmap? = null
    /** ★ 上次 OCR 文字（去重：文字未变不重复渲染） */
    private var lastScreenReadText: String = ""
    /** ★ 上次 OCR 时间戳（最小间隔，避免 ML Kit 跑太频繁） */
    private var lastScreenReadTime: Long = 0L
    /** ★ OCR 互斥锁（process 在跑时不触发新一轮，避免异步回调 mySeq 永远被丢弃） */
    private var screenReadOcrInProgress: Boolean = false
    /** ★ OCR 状态行（"识别中"/"已识别"/"未识别到"） */
    private var screenReadStatusText: TextView? = null
    /** ★ OCR 原文预览（截短显示，2 行） */
    private var screenReadOcrPreview: TextView? = null
    /** 读屏模式扫描循环 Runnable */
    private var screenReadRunnable: Runnable? = null
    /** ★ 读屏模式扫描循环 tick 计数（用于诊断"runnable 是否真的在跑"） */
    private var captureScreenTick: Int = 0
    /** 读屏小窗最小化后的圆点（📖） */
    private var minimizedScreenReadDot: View? = null
    /** 读屏小窗最小化时保存的位置/尺寸（恢复用） */
    private var screenReadSavedRect: IntArray = intArrayOf(0, 0, 0, 0)
    /** ★ 启动读屏模式时主浮窗 UI 是否在显示（用于停止读屏时决定是否恢复主浮窗 UI） */
    private var floatWasRunningBeforeScreenRead: Boolean = false
    /** ★ 启动读屏模式时浮窗 OCR 扫描是否在跑（停止读屏时只在之前扫描在跑才重启扫描） */
    private var floatScanningBeforeScreenRead: Boolean = false
    // ★★★ 读屏搜题专用 OCR 资源（与浮窗搜题完全独立，绝不共享） ★★★
    /** 读屏专用 TextRecognizer（不与浮窗的 serviceRecognizer 共享，避坑并发卡死） */
    private var screenReadRecognizer: com.google.mlkit.vision.text.TextRecognizer? = null
    /** 读屏专用 ImageReader（不与浮窗的 ocrImageReader 共享） */
    private var screenReadImageReader: android.media.ImageReader? = null
    /** 读屏专用 VirtualDisplay（不与浮窗的 ocrVirtualDisplay 共享） */
    private var screenReadVirtualDisplay: android.hardware.display.VirtualDisplay? = null
    /** 读屏专用 Handler（OCR 异步回调，不与浮窗共享） */
    private val screenReadOcrHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            ocrRecognizeHeight = dp(150)  // 默认 150dp（约 1 道题高）
            // ★ 启动成功：清空之前的错误信息（防止上次失败残留）
            getSharedPreferences("souti_state", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("service_running", true)
                .putString("last_error", "")
                .apply()
            createNotificationChannel()
            // ★ OCR 截屏必须用 mediaProjection 类型（之前 specialUse 抛 SecurityException）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            // ★ 不再在 onCreate 自动 showFloatWindow（之前会导致点读屏搜题时主浮窗"闪一下"才被移除）
            //    改为：ACTION_START_SCAN 处理时按需显示主浮窗（用户主动启动浮窗搜题时才显示）
        } catch (e: Throwable) {
            Log.e("FloatWindow", "Service 启动失败", e)
            // ★ 崩溃时清理 prefs + 写错误信息到 prefs，让主页能显示
            try {
                getSharedPreferences("souti_state", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("service_running", false)
                    .putString("last_error", e.message ?: e.javaClass.simpleName)
                    .apply()
            } catch (_: Throwable) {}
            stopSelf()
            return
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "搜题悬浮窗", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("搜题助手运行中")
            .setContentText("悬浮窗已开启")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showFloatWindow() {
        val r = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            // clipChildren 保持默认 true：浮窗内容严格限制在浮窗边界内（模块4不侵入其他区域）
        }
        root = r

        // ★ 浮窗默认高度精确 = topBar(28) + topSpace(0) + 绿框(150) + 输出框(180) = 358dp
        //    resize 绿框时由 bindResizeAndDrag 动态同步调 p.height，公式 = 28 + 0 + greenH + 180
        //    renderScanResults 后由 updateFloatHeightAfterRender 调 p.height = 28 + 0 + greenH + contentH
        // ★ 浮窗默认宽度减小到 dp(360) = 1080px（在 1280px 屏幕里还有 200px 余量，不容易超出）
        val p = WindowManager.LayoutParams(
            dp(360), dp(358),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,  // ★ 允许浮窗超出屏幕边界
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(40)
            y = dp(200)
        }
        params = p

        // 待机模式 UI（OCR 扫描 + 手动搜题备用 全在这里）
        buildStandbyUi(r)
        // 搜题模式 UI（纯手动搜题备用）
        val searchUi = buildSearchUi(r)
        searchUi.visibility = View.GONE

        windowManager.addView(r, p)
    }

    /** 待机模式：绿框识别区（顶部）+ OCR 结果区（底部，紧贴）
     *  OCR 范围 = 绿框区域（可拖动，识别框跟随）
     */
    private fun buildStandbyUi(root: ViewGroup): View {
        // ★ 外层 LinearLayout VERTICAL（自然垂直排列：顶栏 + 容器，容器 weight=1 占满剩余 → 浮窗无底部透明空白）
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            // clipChildren 保持默认 true：子 View（container）不能绘制出 outer 边界 → 模块4 不会侵入模块1/2/3
        }

        // ★ 顶栏（绿框外）：●扫描中 + 🔍搜题 + 授权并启动 + ✕
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)  // 完全透明
            setPadding(dp(8), dp(2), dp(8), dp(2))  // 左右 8dp 缩进：内容更聚到中间，不贴绿框边缘
        }
        // ●扫描中
        val statusDot = View(this).apply {
            setBackgroundResource(android.R.drawable.presence_online)
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
        }
        val statusText = TextView(this).apply {
            text = if (continuousScanning) "扫描中" else "扫描停止"
            setTextColor(if (continuousScanning) Color.parseColor("#1D9E75") else Color.parseColor("#E24B4A"))
            textSize = 11f
            setPadding(dp(2), 0, dp(4), 0)
        }
        this.statusDot = statusDot
        this.statusText = statusText
        topBar.addView(statusDot)
        topBar.addView(statusText)
        // 🔍搜题按钮（手动搜题备用）
        val manualBtn = TextView(this).apply {
            text = "🔍"
            setTextColor(Color.parseColor("#1D9E75"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener { switchToSearchMode() }
        }
        val manualLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(4)
        }
        manualBtn.layoutParams = manualLp
        topBar.addView(manualBtn)
        // ★ 左侧弹性空间（让授权按钮视觉居中）
        //    weight=0.6f 偏小：补偿左侧 "●扫描 + 状态文字 + 🔍" 占空间多，让授权向左移视觉居中
        //    （之前 1.6f 方向反了——左弹性更大反而把授权推右）
        topBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 0.6f)
        })
        // 授权/扫描按钮（混合态：未授权=授权，已授权未扫描=开始，已扫描=暂停）
        val topBtn = TextView(this).apply {
            text = when {
                continuousScanning -> "⏸暂停"
                OcrBridge.mediaProjection == null -> "🔓授权并扫描"
                else -> "▶开始"
            }
            setTextColor(Color.parseColor(if (continuousScanning) "#E24B4A" else "#1D9E75"))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(Color.TRANSPARENT)  // 透明底
            setPadding(dp(4), dp(2), dp(4), dp(2))
            setOnClickListener { toggleOcrFromStandby() }
        }
        ocrTopSwitch = topBtn
        topBar.addView(topBtn)
        // — 按钮：最小化（隐藏完整浮窗 → 显示独立 36dp 透明 "+" 圆点窗口）
        val closeBtn = TextView(this).apply {
            text = "—"  // em-dash 最小化
            setTextColor(Color.parseColor("#E24B4A"))  // 红色
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(Color.TRANSPARENT)  // ★ 透明底（不再绿底大白块）
            setPadding(dp(4), dp(0), dp(4), dp(0))
            includeFontPadding = false
            setOnClickListener {
                this@FloatWindowService.minimizeToDot()
            }
        }
        closeBtnRef = closeBtn  // 存引用，minimizeToDot 用它定位屏幕坐标
        // closeBtn WRAP_CONTENT（不强制 40x40dp，跟其他按钮同大小）
        // ★ 右侧弹性空间（让授权按钮居中，closeBtn 靠右）
        //    weight=1f 偏小：补偿右侧 "—" 单按钮占空间少（与左侧 weight=1.6f 对应）
        topBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        })
        // ★ 顶栏加到外层（LinearLayout 子 View：显式固定高度 dp(28)，不再 WRAP_CONTENT）
        //    原因：浮窗总高公式 = topBar + topSpace + 绿框 + 输出框，topBar 固定才能精确无留白
        topBar.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(28)
        )
        outer.addView(topBar)
        // 将 closeBtn 追加到 topBar 尾部（放右侧）
        topBar.addView(closeBtn)

        // ★ 内层 LinearLayout（垂直）：绿框 + 结果区 紧贴排列（在顶栏下方）
        //    设 layoutParams = MATCH_PARENT, weight=1：撑满 outer 剩余高度
        //    → 浮窗底部再无透明空白区（之前 outer 是 FrameLayout + container WRAP_CONTENT 时底部漏出 ~34dp 透明区）
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            // clipChildren 保持默认 true：子 View（绿框/ScrollView）不能绘制出 container 边界 → 模块4 只在自己区域显示
        }
        // 顶部 padding（极简：topSpace=0，绿框紧贴顶栏下沿，视觉上 0 间距）
        val topSpace = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(0))
        }
        container.addView(topSpace)

        // ★ 绿框识别区：初始固定 dp(352) 宽（=浮窗宽 360 - 左右 margin 4*2）
        //    resize 时 lp.width = newW 可超浮窗宽度（受 root.clipChildren=false 支持）
        //    不能 WRAP_CONTENT：redBorder 子 View 是 MATCH_PARENT，WRAP_CONTENT 父下 MATCH_PARENT 失效→ 绿框初始 0dp 宽
        val recognizeArea = FrameLayout(this).apply {
            id = R_ID_RECOGNIZE  // ★ 给个稳定 ID（captureAndProcessOnce 用 getLocationOnScreen 获取真实坐标）
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#11FFFFFF"))  // 极淡白底（方便用户看到绿框边界）
                setStroke(dp(2), Color.parseColor("#1D9E75"))
                cornerRadius = dp(10).toFloat()
            }
        }
        recognizeArea.layoutParams = LinearLayout.LayoutParams(
            dp(352), dp(150)
        ).apply {
            leftMargin = dp(4)
        }
        container.addView(recognizeArea)

        // ★ 模块3：OCR 识别状态显示（绿框下方固定一行，不随匹配结果滚动）
        //    识别到的原文实时显示在这里；无内容时隐藏不占空间
        val ocrStatusText = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#888888"))
            setPadding(dp(8), dp(2), dp(8), dp(2))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE  // 无 OCR 内容时隐藏
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(ocrStatusText)
        ocrStatusTextView = ocrStatusText

        // ◢ 拖拽手柄（绿框内最右下，唯一在绿框内的元素）
        val resizeHandle = TextView(this).apply {
            text = "◢"
            setTextColor(Color.parseColor("#1D9E75"))
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#22FFFFFF"))
            setPadding(dp(4), 0, dp(4), 0)
        }
        resizeHandle.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END
        ).apply {
            rightMargin = dp(2)
            bottomMargin = dp(2)
        }
        recognizeArea.addView(resizeHandle)
        bindResizeAndDrag(resizeHandle, recognizeArea)

        // ★ 红色边框：放在 recognizeArea 内部（绿框内），完全等于绿框大小（不缩进）
        // OCR 截屏范围 = 绿框 = 红色边框（红色边框只是视觉提示）
        val redBorder = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), Color.parseColor("#FF1744"))  // 红色
                cornerRadius = dp(8).toFloat()
            }
        }
        redBorder.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )  // 不设 margin = 和绿框一样大
        recognizeArea.addView(redBorder)
        redBorderView = redBorder
        // ★ 默认隐藏红色边框（用户在主页调试开关里开启才显示）
        redBorder.visibility = View.GONE

        // ★ 结果区（绿框下方，紧贴）
        val resultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.parseColor("#22000000"))
            // ★ 显式裁剪：卡片内容只能绘制在本容器内（绝不溢出到 ScrollView/绿框/顶栏）
            clipChildren = true
            // ★ 显式 MATCH_PARENT 宽（不被 ScrollView 默认 WRAP_CONTENT 干扰）
            //    卡片 layoutParams.width = MATCH_PARENT 才能正确跟随 resultContainer 宽度
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            val hint = TextView(this@FloatWindowService).apply {
                text = "（OCR 结果将在这里显示）"
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            addView(hint)
        }
        val ocrResults = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            // ★ 强制裁剪：防止卡片内容绘制溢出 ScrollView 边界覆盖到顶栏/绿框
            clipChildren = true
            addView(resultsContainer)
        }
        // ★ 输出显示框固定 dp(180)：renderScanResults 后不再动态改 ScrollView 高（避免动态测量导致卡片重叠）
        //    没/少内容时浮窗变小省屏通过 updateFloatHeightAfterRender 调浮窗总高实现，不影响 ScrollView
        //    内容超出时 ScrollView 内部 180dp 范围内滚动
        ocrResults.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(180)
        )
        container.addView(ocrResults)
        ocrResultContainer = resultsContainer
        ocrResultScroll = ocrResults

        outer.addView(container)
        root.addView(outer)
        return outer
    }

    private fun TextView.lpTopEnd() {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        )
    }
    private fun View.lpBottomStart() {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.START
        )
    }
    private fun TextView.lpBottomEnd() {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END
        )
    }

    /** 搜题模式 */
    private fun buildSearchUi(root: ViewGroup): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        container.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )

        // 顶部条
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backBtn = TextView(this).apply {
            text = "← 返回"
            setTextColor(Color.parseColor("#1D9E75"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), dp(4), dp(8), dp(4)
            )
            setOnClickListener { switchToStandbyMode() }
        }
        val title = TextView(this).apply {
            text = "搜题"
            setTextColor(Color.parseColor("#1D9E75"))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn2 = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#E24B4A"))
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { stopSelf() }
        }
        // ★ 搜题模式顶栏：返回 + 标题 + ✕（OCR 扫描开关在主页，不在这里）
        topBar.addView(backBtn)
        topBar.addView(title)
        topBar.addView(closeBtn2)
        container.addView(topBar)

        // 输入行
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            layoutParams = lp
        }
        val input = EditText(this).apply {
            hint = "输入题干关键词"
            setHintTextColor(Color.parseColor("#888888"))
            setTextColor(Color.BLACK)
            textSize = 13f
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(dp(8), dp(6), dp(8), dp(6)
            )
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
        }
        val searchGo = Button(this).apply {
            text = "搜"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1D9E75"))
            textSize = 13f
            val lp = LinearLayout.LayoutParams(dp(56), dp(36))
            lp.leftMargin = dp(6)
            layoutParams = lp
        }
        inputRow.addView(input)
        inputRow.addView(searchGo)
        container.addView(inputRow)

        // 激活题库提示
        val activeHint = TextView(this).apply {
            val count = bank.getActiveBankIds().size
            text = if (count == 0) "⚠ 未勾选任何题库，去题库总览勾选" else "✓ 已勾选 $count 个题库"
            textSize = 11f
            setTextColor(if (count == 0) Color.parseColor("#E24B4A") else Color.parseColor("#1D9E75"))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(4)
            layoutParams = lp
        }
        container.addView(activeHint)

        // 结果区域
        val resultScroll = ScrollView(this).apply {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            lp.topMargin = dp(6)
            layoutParams = lp
            setBackgroundColor(Color.TRANSPARENT)
        }
        val resultContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        resultScroll.addView(resultContainer)

        val placeholder = TextView(this).apply {
            text = "输入题干关键词搜索\n如：丁二烯 溶剂"
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            setLineSpacing(2f, 1.2f)
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(30)
            layoutParams = lp
        }
        resultContainer.addView(placeholder)
        container.addView(resultScroll)

        searchGo.setOnClickListener {
            val q = input.text.toString().trim()
            if (q.isEmpty()) return@setOnClickListener
            resultContainer.removeAllViews()
            val results = bank.search(q, limit = 5)
            if (results.isEmpty()) {
                resultContainer.addView(TextView(this).apply {
                    text = if (bank.getActiveBankIds().isEmpty()) "未勾选题库" else "未找到匹配题目"
                    textSize = 13f
                    setTextColor(Color.parseColor("#888888"))
                    gravity = Gravity.CENTER
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.topMargin = dp(30)
                    layoutParams = lp
                })
            } else {
                results.forEach { sr ->
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundColor(Color.parseColor("#F5F5F5"))
                        setPadding(dp(10), dp(8), dp(10), dp(8))
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.bottomMargin = dp(6)
                        layoutParams = lp
                    }
                    card.addView(TextView(this).apply {
                        text = sr.question.stem
                        textSize = 12f
                        setTextColor(Color.parseColor("#222222"))
                        maxLines = 3
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    sr.question.options.forEach { opt ->
                        card.addView(TextView(this).apply {
                            text = opt
                            textSize = 11f
                            setTextColor(Color.parseColor("#444444"))
                            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            lp.leftMargin = dp(4)
                            layoutParams = lp
                        })
                    }
                    if (sr.question.answer.isNotBlank()) {
                        card.addView(TextView(this).apply {
                            text = "答案：${sr.question.answer}"
                            textSize = 13f
                            setTypeface(typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor("#1D9E75"))
                            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            lp.topMargin = dp(4)
                            layoutParams = lp
                        })
                    }
                    card.addView(TextView(this).apply {
                        text = "来源：${sr.bankName} · 相关度 ${sr.score}"
                        textSize = 10f
                        setTextColor(Color.parseColor("#AAAAAA"))
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.topMargin = dp(2)
                        layoutParams = lp
                    })
                    resultContainer.addView(card)
                }
            }
        }
        input.setOnEditorActionListener { _, _, _ -> searchGo.performClick(); true }

        root.addView(container)
        return container
    }

    /** 持续 OCR 模式的轮询（持续显示结果，不超时） */
    private fun startContinuousPolling(input: EditText, searchBtn: Button, hint: TextView) {
        val pollHandler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("ocr_prefs", Context.MODE_PRIVATE)
                val ts = prefs.getLong("ocr_timestamp", 0)
                if (ts > lastOcrTimestamp) {
                    val text = prefs.getString("ocr_result", "") ?: ""
                    if (text.isNotBlank() && text != "区域太小" && text != "截屏失败") {
                        // 自动填入并搜索
                        if (input.text.toString() != text) {
                            input.setText(text)
                            searchBtn.performClick()
                            hint.text = "✓ 已识别：${text.take(30)}${if (text.length > 30) "..." else ""}"
                            hint.setTextColor(Color.parseColor("#1D9E75"))
                        }
                    }
                    lastOcrTimestamp = ts
                }
                // 持续模式：每 1 秒轮询
                pollHandler.postDelayed(this, 1000)
            }
        }
        pollHandler.post(runnable)
    }

    /** 轮询 OCR 结果（每 500ms 检查一次，最多等 15 秒） */
    private val ocrPollHandler = Handler(Looper.getMainLooper())
    private var ocrPollCount = 0
    private var lastOcrTimestamp = 0L

    /**
     * Service 直接 OCR（复用已授权的 MediaProjection 句柄，不弹授权）
     */
    private val serviceOcrHandler = Handler(Looper.getMainLooper())
    // ★ 改成 var lateinit：每次读屏模式启动时 dispose 旧的、new 新的（避免之前模式残留状态导致 "Failed to run text recognizer"）
    private var serviceRecognizer: com.google.mlkit.vision.text.TextRecognizer =
        TextRecognition.getClient(
            com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
        )
    private var serviceImageReader: android.media.ImageReader? = null
    private var serviceVirtualDisplay: android.hardware.display.VirtualDisplay? = null

    /** 重新创建 ML Kit 识别器（读屏模式启动时调用，清除之前模式残留状态） */
    private fun recreateServiceRecognizer() {
        try { serviceRecognizer.close() } catch (_: Exception) {}
        serviceRecognizer = TextRecognition.getClient(
            com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
        )
    }

    private fun startOcrInService(input: EditText, searchBtn: Button, hint: TextView, rect: android.graphics.Rect) {
        val projection = OcrBridge.mediaProjection ?: return
        if (serviceImageReader != null) {
            hint.text = "⚠ OCR 正在识别中…"
            return
        }
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val density = metrics.densityDpi
        serviceImageReader = android.media.ImageReader.newInstance(w, h, android.graphics.PixelFormat.RGBA_8888, 2)
        try {
            serviceVirtualDisplay = projection.createVirtualDisplay(
                "ocr_svc", w, h, density,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                serviceImageReader!!.surface, null, serviceOcrHandler
            )
        } catch (e: Exception) {
            hint.text = "⚠ 截屏启动失败：${e.message}"
            return
        }
        serviceOcrHandler.postDelayed({
            val image = try { serviceImageReader?.acquireLatestImage() } catch (_: Exception) { null }
            if (image == null) {
                hint.text = "⚠ 截屏未就绪"
                cleanupServiceOcr()
                return@postDelayed
            }
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width
                val bitmap = android.graphics.Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride, image.height,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()
                val cropL = rect.left.coerceIn(0, w - 1)
                val cropT = rect.top.coerceIn(0, h - 1)
                val cropW = (rect.right - cropL).coerceAtLeast(1)
                val cropH = (rect.bottom - cropT).coerceAtLeast(1)
                if (cropW < 20 || cropH < 20) {
                    hint.text = "⚠ 识别区域太小"
                    return@postDelayed
                }
                val cropped = android.graphics.Bitmap.createBitmap(bitmap, cropL, cropT, cropW, cropH)
                val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(cropped, 0)
                serviceRecognizer.process(inputImage)
                    .addOnSuccessListener { result ->
                        val text = result.text.replace("\n", " ").trim()
                        val prefs = getSharedPreferences("ocr_prefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString("ocr_result", text)
                            .putLong("ocr_timestamp", System.currentTimeMillis())
                            .apply()
                        if (text.isNotBlank()) {
                            input.setText(text)
                            searchBtn.performClick()
                            hint.text = "✓ OCR 完成：${text.take(30)}"
                            hint.setTextColor(Color.parseColor("#1D9E75"))
                        }
                    }
                    .addOnFailureListener { e ->
                        hint.text = "⚠ OCR 识别失败：${e.message}"
                    }
            } catch (e: Exception) {
                hint.text = "⚠ 截屏失败：${e.message}"
            } finally {
                cleanupServiceOcr()
            }
        }, 500)
    }

    private fun cleanupServiceOcr() {
        try { serviceVirtualDisplay?.release() } catch (_: Exception) {}
        try { serviceImageReader?.close() } catch (_: Exception) {}
        serviceVirtualDisplay = null
        serviceImageReader = null
    }

    // ============ 🔄 实时连续扫描模式 ============
    private var continuousScanning = false
    private val scanHandler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null
    private val authPollHandler = Handler(Looper.getMainLooper())
    /** 待机模式 OCR 结果容器（OCR 扫描时显示结果） */
    private var ocrResultContainer: LinearLayout? = null
    /** ScrollView 包裹结果区，用于渲染后自动滚到顶部 */
    private var ocrResultScroll: ScrollView? = null
    /** ★ 模块3：OCR 识别状态 TextView（独立显示识别到的文字，不随匹配结果滚动） */
    private var ocrStatusTextView: TextView? = null
    /** ★ 红色调试框（显示 OCR 实际识别范围） */
    private var redBorderView: View? = null
    /** OCR 顶部开关按钮引用 */
    private var ocrTopSwitch: TextView? = null
    /** 状态栏点 + 文字（用于切换扫描中/停止状态） */
    private var statusDot: View? = null
    private var statusText: TextView? = null
    /** 最近一次 OCR 识别的原始文本（fallback 显示） */
    private var ocrRawText: String = ""
    /** 绿框的实际像素高度（用户拖拽时变化）。注意：不能在字段初始化器里调 dp()，此时 Context 还未就绪 */
    private var ocrRecognizeHeight: Int = 0  // 默认值在 onCreate 里设置
    /** ★ OCR 序列号：每次启动 OCR 递增，用于丢弃旧异步回调（防止 OCR 错位渲染） */
    @Volatile private var ocrSeq: Int = 0
    /** ★ 长期 VirtualDisplay（保持运行，避免频繁创建触发 MediaProjection 保护） */
    private var ocrVirtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var ocrImageReader: android.media.ImageReader? = null

    /** 启动实时扫描：每 2 秒截屏绿框区域 → OCR → 分段搜题 → 渲染到结果容器
     *  ★ 完全独立于手动搜题
     */
    private fun startContinuousScan() {
        if (OcrBridge.mediaProjection == null) {
            Log.w("FloatWindow", "startContinuousScan: mediaProjection 为 null！OCR 无法启动")
            return
        }
        if (continuousScanning) return
        continuousScanning = true
        // ★ 标记当前模式为浮窗搜题（让主页轮询能区分显示）
        OcrBridge.currentMode = OcrBridge.MODE_FLOAT_WINDOW
        Log.d("FloatWindow", "startContinuousScan: 启动 OCR 循环（每 2 秒，长期 VirtualDisplay）")
        // ★ 创建一次长期 VirtualDisplay + ImageReader（避免每 2 秒创建/释放触发 MediaProjection stop）
        if (ocrVirtualDisplay == null) {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val w = metrics.widthPixels
            val h = metrics.heightPixels
            val density = metrics.densityDpi
            try {
                val reader = android.media.ImageReader.newInstance(w, h, android.graphics.PixelFormat.RGBA_8888, 2)
                val vd = OcrBridge.mediaProjection!!.createVirtualDisplay(
                    "ocr_scan", w, h, density,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface, null, serviceOcrHandler
                )
                ocrImageReader = reader
                ocrVirtualDisplay = vd
                Log.d("FloatWindow", "创建长期 VirtualDisplay 成功: ${w}x${h}@${density}dpi")
            } catch (e: Exception) {
                Log.e("FloatWindow", "创建 VirtualDisplay 失败: ${e.message}", e)
                continuousScanning = false
                return
            }
        }
        scanRunnable = object : Runnable {
            override fun run() {
                if (!continuousScanning) return
                Log.d("FloatWindow", "OCR 循环 tick (每 2 秒)")
                captureAndProcessOnce()
                scanHandler.postDelayed(this, 2000)
            }
        }
        scanRunnable?.let { scanHandler.post(it) }
    }

    private fun stopContinuousScan() {
        continuousScanning = false
        scanHandler.removeCallbacksAndMessages(null)
        authPollHandler.removeCallbacksAndMessages(null)
        // ★ 保留 VirtualDisplay + MediaProjection token（用户点"继续"时复用）
        // 只在 ACTION_STOP_SELF / MediaProjection.onStop 时真正释放
        Log.d("FloatWindow", "stopContinuousScan: 暂停扫描（保留 MediaProjection）")
        // ★ 注意：这里**不重置** currentMode（保留"浮窗搜题"标识，mainActivity 主页能看到）
        //    真正的 NONE 状态只在 stopScreenRead 或 Service 销毁时设置
    }

    // ═══════════════════ 读屏模式（全屏自动识别） ═══════════════════
    // 与浮窗搜题共享 MediaProjection / VirtualDisplay / ImageReader / OCR 识别器
    // 区别：识别区域 = 全屏（不裁剪绿框）；滚动防抖；多题切分；输出到独立答案小窗

    /** 启动读屏模式：隐藏主浮窗 + 创建答案小窗 + 开始全屏扫描循环 */
    private fun startScreenRead() {
        if (OcrBridge.mediaProjection == null) {
            Log.w("FloatWindow", "startScreenRead: mediaProjection 为 null！无法启动")
            return
        }
        if (screenReadActive) return
        screenReadActive = true
        // ★ 标记当前模式为读屏（让主页轮询能区分显示）
        OcrBridge.currentMode = OcrBridge.MODE_SCREEN_READ
        Log.d("FloatWindow", "startScreenRead: 读屏模式启动")
        // 1. ★ 记录主浮窗 UI 是否在显示 + 扫描是否在跑（停止读屏时据此恢复）
        floatWasRunningBeforeScreenRead = (root != null)
        floatScanningBeforeScreenRead = continuousScanning
        // 2. 若浮窗绿框扫描在跑，先停（避免两个扫描循环抢同一个 recognizer）
        if (continuousScanning) stopContinuousScan()
        // 3. ★ 隐藏主浮窗（root 置 null，停止读屏时 restoreFloatWindow 能正确重建）
        if (root != null) {
            try { windowManager.removeView(root) } catch (_: Exception) {}
            root = null  // ★ P1 修复：置 null，否则 restoreFloatWindow 的 if(root!=null) return 会短路
            Log.d("FloatWindow", "读屏模式：主浮窗已隐藏")
        }
        // ★★★ 4. 创建读屏专用资源（完全独立于浮窗搜题，绝不共享 recognizer/ImageReader/VirtualDisplay）★★★
        ensureScreenReadResources()
        // 5. 创建独立答案小窗
        buildScreenReadWindow()
        // 6. 启动读屏扫描循环
        screenReadRunnable = object : Runnable {
            override fun run() {
                if (!screenReadActive) return
                captureScreenReadFrame()
                scanHandler.postDelayed(this, 1500)
            }
        }
        scanHandler.post(screenReadRunnable!!)
    }

    /** 创建读屏专用资源（独立 recognizer/ImageReader/VirtualDisplay） */
    private fun ensureScreenReadResources() {
        if (screenReadRecognizer == null) {
            screenReadRecognizer = TextRecognition.getClient(
                com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
            )
            Log.d("FloatWindow", "读屏: 创建专用 recognizer")
        }
        if (screenReadImageReader == null || screenReadVirtualDisplay == null) {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val w = metrics.widthPixels
            val h = metrics.heightPixels
            val density = metrics.densityDpi
            try {
                val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
                val vd = OcrBridge.mediaProjection!!.createVirtualDisplay(
                    "screen_read_ocr", w, h, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface, null, screenReadOcrHandler
                )
                screenReadImageReader = reader
                screenReadVirtualDisplay = vd
                Log.d("FloatWindow", "读屏: 创建专用 VirtualDisplay ${w}x${h}@${density}dpi")
            } catch (e: Exception) {
                Log.e("FloatWindow", "读屏: 创建 VirtualDisplay 失败: ${e.message}", e)
            }
        }
    }

    /** 释放读屏专用资源（关闭读屏搜题时调用） */
    private fun releaseScreenReadResources() {
        try { screenReadRecognizer?.close() } catch (_: Exception) {}
        screenReadRecognizer = null
        try { screenReadVirtualDisplay?.release() } catch (_: Exception) {}
        screenReadVirtualDisplay = null
        try { screenReadImageReader?.close() } catch (_: Exception) {}
        screenReadImageReader = null
        Log.d("FloatWindow", "读屏: 释放专用资源")
    }

    /** ★★ 释放浮窗的 VirtualDisplay + ImageReader（读屏启动时调用，MediaProjection 只允许一个 VD 收帧） */
    private fun releaseFloatWindowOcrResources() {
        try { ocrVirtualDisplay?.release() } catch (_: Exception) {}
        ocrVirtualDisplay = null
        try { ocrImageReader?.close() } catch (_: Exception) {}
        ocrImageReader = null
        Log.d("FloatWindow", "读屏: 已释放浮窗 VirtualDisplay/ImageReader（读屏 VD 独占收帧）")
    }

    /** 停止读屏模式：停循环 + 移除小窗 + 释放帧缓存 + 恢复主浮窗 */
    private fun stopScreenRead() {
        if (!screenReadActive) return
        screenReadActive = false
        // ★ 重置模式标记（让主页能正确显示"未开启"状态）
        if (OcrBridge.currentMode == OcrBridge.MODE_SCREEN_READ) {
            OcrBridge.currentMode = OcrBridge.MODE_NONE
        }
        screenReadOcrInProgress = false
        Log.d("FloatWindow", "stopScreenRead: 读屏模式停止")
        screenReadRunnable?.let { scanHandler.removeCallbacks(it) }
        screenReadRunnable = null
        captureScreenTick = 0
        lastFrameThumb?.recycle()
        lastFrameThumb = null
        // ★★ 释放读屏专用资源（与浮窗完全隔离）★★★
        releaseScreenReadResources()
        // ★ 重置文字缓存和时间戳（下次启动 OCR 时重新识别）
        lastScreenReadText = ""
        lastScreenReadTime = 0L
        // 移除答案小窗
        val w = screenReadWindow
        if (w != null) {
            try { windowManager.removeView(w) } catch (_: Exception) {}
            screenReadWindow = null
        }
        screenReadParams = null
        screenReadContainer = null
        // ★ 移除最小化圆点
        minimizedScreenReadDot?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        minimizedScreenReadDot = null
        // ★ 恢复主浮窗（如果启动读屏前主浮窗 UI 在显示）
        if (floatWasRunningBeforeScreenRead) {
            restoreFloatWindow()
            floatWasRunningBeforeScreenRead = false
            // ★ P2-2 修复：只在"启动读屏前扫描在跑"时才重启扫描
            if (floatScanningBeforeScreenRead && OcrBridge.mediaProjection != null && !continuousScanning) {
                startContinuousScan()
            }
            floatScanningBeforeScreenRead = false
        }
    }

    /** 恢复主浮窗（提取自 ACTION_SHOW_WINDOW 逻辑，启动/停止读屏时复用） */
    private fun restoreFloatWindow() {
        if (root != null) return  // 已存在，不重复添加
        val p = params ?: return
        val r = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        root = r
        buildStandbyUi(r)
        val searchUi = buildSearchUi(r)
        searchUi.visibility = View.GONE
        try {
            windowManager.addView(r, p)
            Log.d("FloatWindow", "主浮窗已恢复（读屏模式结束）")
        } catch (e: Exception) {
            Log.e("FloatWindow", "主浮窗恢复失败: ${e.message}")
        }
    }

    /** 启动读屏扫描循环（暂停/恢复用） */
    private fun startScreenReadLoop() {
        if (!screenReadActive) screenReadActive = true
        if (OcrBridge.mediaProjection == null) return
        ensureScreenReadResources()  // ★ P2-1 修复：暂停/继续要用读屏专用资源，不是浮窗的 ensureScanResources
        screenReadRunnable = object : Runnable {
            override fun run() {
                if (!screenReadActive) return
                captureScreenReadFrame()
                scanHandler.postDelayed(this, 1500)
            }
        }
        scanHandler.post(screenReadRunnable!!)
    }

    /** 停止读屏扫描循环（暂停用，保留小窗） */
    private fun stopScreenReadLoop() {
        screenReadActive = false
        screenReadRunnable?.let { scanHandler.removeCallbacks(it) }
        screenReadRunnable = null
        lastFrameThumb?.recycle()
        lastFrameThumb = null
        // ★ 重置文字缓存和时间戳（下次启动 OCR 时重新识别）
        lastScreenReadText = ""
        lastScreenReadTime = 0L
    }

    /** 确保长期 VirtualDisplay + ImageReader 存在（浮窗模式已创建则复用） */
    private fun ensureScanResources() {
        if (ocrVirtualDisplay != null && ocrImageReader != null) return
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val density = metrics.densityDpi
        try {
            val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            val vd = OcrBridge.mediaProjection!!.createVirtualDisplay(
                "ocr_scan", w, h, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, serviceOcrHandler
            )
            ocrImageReader = reader
            ocrVirtualDisplay = vd
            Log.d("FloatWindow", "ensureScanResources: 创建 ${w}x${h}@${density}dpi")
        } catch (e: Exception) {
            Log.e("FloatWindow", "ensureScanResources 失败: ${e.message}", e)
        }
    }

    /**
     * 读屏模式单帧处理：
     * 1. acquireLatestImage 拿全屏帧
     * 2. 缩略图 → 与上一帧比较 diff（滚动检测）
     * 3. diff 大（滚动中）→ 冻结输出；连续稳定 ≥2 帧 → 全屏 OCR
     */
    private fun captureScreenReadFrame() {
        // ★ 累积 tick 计数（每次调用都更新状态栏，确认 runnable 在跑）
        captureScreenTick++
        Log.d("FloatWindow", "读屏 tick #$captureScreenTick（screenReadActive=$screenReadActive, reader=${screenReadImageReader != null}）")
        screenReadStatusText?.text = "💓 第${captureScreenTick}次 tick..."
        // ★ 用读屏专用 ImageReader（不与浮窗共享）
        val reader = screenReadImageReader ?: run {
            Log.w("FloatWindow", "读屏: screenReadImageReader == null")
            screenReadStatusText?.text = "⚠ 读屏 VirtualDisplay 未创建"
            return
        }
        val recognizer = screenReadRecognizer ?: run {
            Log.w("FloatWindow", "读屏: screenReadRecognizer == null")
            screenReadStatusText?.text = "⚠ 读屏 Recognizer 未初始化"
            return
        }
        // ★★★ P0 根因修复：删除 mySeq = ++ocrSeq ★★★
        //    之前每次 tick 都 ++ocrSeq，而大图 OCR 耗时 > 1.5s（tick 间隔），
        //    OCR 完成时 mySeq != ocrSeq（被下一 tick 覆盖）→ 结果 100% 被丢弃！
        //    读屏已有 screenReadOcrInProgress 互斥锁 + 时间间隔，同一时刻只有一个 OCR 在跑，
        //    根本不需要 seq 丢弃机制。删除后每次 OCR 结果都能正常处理。
        val image = try { reader.acquireLatestImage() } catch (e: Exception) {
            Log.e("FloatWindow", "读屏 acquireLatestImage 异常: ${e.message}")
            null
        }
        if (image == null) {
            Log.w("FloatWindow", "读屏: acquireLatestImage == null（ImageReader 没新帧）")
            screenReadStatusText?.text = "🔄 等待截屏帧..."
            return
        }
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val full = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride, image.height,
                Bitmap.Config.ARGB_8888
            )
            full.copyPixelsFromBuffer(buffer)
            image.close()
            // ★ 诊断：保存原始截屏到 cacheDir + 主动 push 到 sdcard（让 adb pull 能立刻拿到）
            try {
                val ts = System.currentTimeMillis() / 100
                val dumpFile = java.io.File(cacheDir, "ocr_dump.png")
                full.compress(android.graphics.Bitmap.CompressFormat.PNG, 80, java.io.FileOutputStream(dumpFile))
                // ★ 主动 push 到 sdcard（避免 run-as 权限问题）
                val sdFile = java.io.File("/sdcard/ocr_dump_${ts % 100000}.png")
                full.compress(android.graphics.Bitmap.CompressFormat.PNG, 80, java.io.FileOutputStream(sdFile))
                Log.d("FloatWindow", "读屏: dump saved ${sdFile.absolutePath} (${full.width}x${full.height})")
            } catch (e: Exception) {
                Log.e("FloatWindow", "读屏: dump 保存失败: ${e.message}")
            }
            // ★ 关键：截屏把答案小窗自己也截进去了！涂白小窗区域
            val win = screenReadWindow
            if (win != null) {
                val loc = IntArray(2)
                win.getLocationOnScreen(loc)
                val left = loc[0].coerceAtLeast(0)
                val top = loc[1].coerceAtLeast(0)
                val right = (loc[0] + win.width).coerceAtMost(full.width)
                val bottom = (loc[1] + win.height).coerceAtMost(full.height)
                if (right > left && bottom > top) {
                    val canvas = android.graphics.Canvas(full)
                    val whitePaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
                    canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), whitePaint)
                    Log.d("FloatWindow", "读屏: 已涂白小窗区域 [$left,$top,$right,$bottom]")
                }
            }
            // ★ 滚动检测（仅做日志，不阻塞 OCR）
            val thumb = Bitmap.createScaledBitmap(full, 32, 64, false)
            val diffRatio = computeFrameDiff(thumb, lastFrameThumb)
            lastFrameThumb?.recycle()
            lastFrameThumb = thumb
            // ★ 状态栏实时显示进度（让用户直观看到 OCR 链路走到哪步）
            screenReadStatusText?.text = "🔄 截屏 diff=${(diffRatio * 100).toInt()}%"
            // 时间间隔控制 + OCR 互斥（避免异步回调 mySeq 永远被丢弃）
            val now = System.currentTimeMillis()
            if (now - lastScreenReadTime < 1500 || screenReadOcrInProgress) {
                full.recycle()
                return
            }
            lastScreenReadTime = now
            screenReadStatusText?.text = "🔄 OCR 中... diff=${(diffRatio * 100).toInt()}%"
            Log.d("FloatWindow", "读屏: diff=$diffRatio → OCR (${full.width}x${full.height})")
            // ★★★ 关键修复：裁剪中央文字密集区（去掉状态栏和导航栏）后缩放到 720 宽 ★★★
            //    浮窗 OCR 证明：ML Kit 中文识别能跑（WebView 内容也能识别）→ 限制是 bitmap 尺寸不能太大
            //    全屏 1280x2856 → ML Kit 中文识别静默失败（不报错但返回空文字）→ 用户看到"OCR 中...但没输出"
            //    裁剪中央 75%（去掉顶 120px 状态栏 + 底 200px 导航栏）→ 缩放到 720 宽 → 文字占比更高 → ML Kit 能识别
            val statusBarH = 120
            val navBarH = 200
            val cropY0 = statusBarH
            val cropY1 = full.height - navBarH
            val cropped = Bitmap.createBitmap(full, 0, cropY0, full.width, cropY1 - cropY0)
            // ★ 自适应反色：检测图片平均亮度，深色背景（黑底白字）自动反色成白底黑字
            val avgBrightness = computeAverageBrightness(cropped)
            if (avgBrightness < 100) {
                Log.d("FloatWindow", "读屏: 检测到深色背景(avg=$avgBrightness)，执行反色")
                invertBitmap(cropped)
            }
            // ★ 缩放到 720 宽（浮窗 OCR 验证 ML Kit 接受此尺寸），高度按比例
            val targetWidth = 720
            val scale = targetWidth.toFloat() / cropped.width
            val ocrBitmap = Bitmap.createScaledBitmap(
                cropped,
                targetWidth,
                (cropped.height * scale).toInt(),
                true
            )
            cropped.recycle()
            full.recycle()  // full 已裁剪+缩放，释放
            val inputImage = InputImage.fromBitmap(ocrBitmap, 0)
            // ★ OCR 互斥锁：process 调用前加锁，避免新一轮 process 抢占后 mySeq 永远丢弃
            screenReadOcrInProgress = true
            Log.d("FloatWindow", "读屏: process() 调用 ocrBitmap=${ocrBitmap.width}x${ocrBitmap.height}")
            // ★★ 关键：使用读屏专用 recognizer（不与浮窗共享）★★★
            kotlin.runCatching {
                recognizer.process(inputImage)
                    .addOnSuccessListener { result ->
                        Log.d("FloatWindow", "读屏: addOnSuccessListener 触发 text.length=${result.text.length}")
                        screenReadOcrInProgress = false
                        // ★★★ P0 根因修复：删除 if (mySeq != ocrSeq) 丢弃检查 ★★★
                        // ★ P3-1 修复：空文本也走 processScreenReadText（让状态栏显示"未识别到文字"）
                        if (result.text.isNotEmpty() && result.text == lastScreenReadText) return@addOnSuccessListener
                        lastScreenReadText = result.text
                        screenReadOcrHandler.post { processScreenReadText(result.text) }
                        // ★ P2-3 修复：OCR 完成后回收 bitmap（InputImage.fromBitmap 不持有所有权）
                        try { ocrBitmap.recycle() } catch (_: Exception) {}
                    }
                    .addOnFailureListener { e ->
                        Log.w("FloatWindow", "读屏: addOnFailureListener 触发 ${e.javaClass.simpleName}: ${e.message}")
                        screenReadOcrInProgress = false
                        Log.e("FloatWindow", "读屏 OCR 失败: ${e.javaClass.simpleName}: ${e.message}", e)
                        val causeClass = e.cause?.javaClass?.simpleName ?: "无"
                        val diagText = "${e.javaClass.simpleName}: ${e.message?.take(30) ?: "?"}\ncause: $causeClass\nOCR输入: ${ocrBitmap.width}x${ocrBitmap.height}"
                        screenReadOcrHandler.post {
                            screenReadStatusText?.text = "✕ OCR 失败"
                            screenReadOcrPreview?.text = diagText.take(200)
                            android.widget.Toast.makeText(this@FloatWindowService, "OCR失败: ${e.javaClass.simpleName}", android.widget.Toast.LENGTH_LONG).show()
                        }
                        try { ocrBitmap.recycle() } catch (_: Exception) {}
                    }
            }.onFailure { syncEx ->
                Log.e("FloatWindow", "读屏 process() 同步异常: ${syncEx.javaClass.simpleName}: ${syncEx.message}", syncEx)
                screenReadOcrInProgress = false
                screenReadOcrHandler.post {
                    screenReadStatusText?.text = "✕ OCR 同步异常"
                    screenReadOcrPreview?.text = "同步异常: ${syncEx.javaClass.simpleName}: ${syncEx.message?.take(50) ?: "?"}"
                    android.widget.Toast.makeText(this@FloatWindowService, "OCR同步异常: ${syncEx.message}", android.widget.Toast.LENGTH_LONG).show()
                }
                try { ocrBitmap.recycle() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("FloatWindow", "读屏截屏失败: ${e.message}")
            screenReadStatusText?.text = "✕ 截屏失败: ${e.message?.take(30) ?: "未知"}"
        }
    }

    /** 计算两帧缩略图 diff 比例（0-1）：逐像素比较 RGB 亮度差 */
    private fun computeFrameDiff(a: Bitmap, b: Bitmap?): Float {
        if (b == null || a.width != b.width || a.height != b.height) return 1f
        var diffPixels = 0
        val w = a.width
        val h = a.height
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pa = a.getPixel(x, y)
                val pb = b.getPixel(x, y)
                val dr = Math.abs((pa shr 16 and 0xFF) - (pb shr 16 and 0xFF))
                val dg = Math.abs((pa shr 8 and 0xFF) - (pb shr 8 and 0xFF))
                val db = Math.abs((pa and 0xFF) - (pb and 0xFF))
                if (dr + dg + db > 90) diffPixels++
            }
        }
        return diffPixels.toFloat() / (w * h)
    }

    /** 计算 bitmap 平均亮度（0-255）：采样 1/8 像素加速 */
    private fun computeAverageBrightness(bmp: Bitmap): Int {
        var sum = 0L
        var count = 0
        val step = 8
        for (y in 0 until bmp.height step step) {
            for (x in 0 until bmp.width step step) {
                val p = bmp.getPixel(x, y)
                sum += (p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)
                count += 3
            }
        }
        return if (count == 0) 255 else (sum / count).toInt()
    }

    /** 反色：白↔黑、浅↔深（深底浅字 → 白底黑字，让 ML Kit 识别率恢复） */
    private fun invertBitmap(bmp: Bitmap) {
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (i in pixels.indices) {
            val a = pixels[i] shr 24 and 0xFF
            val r = 255 - (pixels[i] shr 16 and 0xFF)
            val g = 255 - (pixels[i] shr 8 and 0xFF)
            val b = 255 - (pixels[i] and 0xFF)
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    }

    /**
     * 读屏模式独立答案小窗：
     * - 半透明深色底（不刺眼、不挡作答）
     * - 顶部标题栏：读屏搜题 + 暂停/继续 + 最小化 + 关闭
     * - 下方 ScrollView：多题答案列表（B方案全列表）
     * - 可拖动（标题栏拖拽）、可缩放（右下角 ◢）
     */
    private fun buildScreenReadWindow() {
        if (screenReadWindow != null) return
        val win = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#44000000"))  // 26% 不透明黑（背景几乎透明，屏幕内容清晰可见）
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))  // 1dp 白色细边框（微弱轮廓，避免完全看不见）
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        // ── 标题栏（可拖拽移动整窗）──
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleBar.addView(TextView(this).apply {
            text = "读屏搜题"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#FFFFFF"))
        })
        titleBar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
        // 暂停/继续按钮
        val pauseBtn = TextView(this).apply {
            text = "⏸"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener {
                if (screenReadActive) {
                    stopScreenReadLoop()
                    text = "▶"
                } else {
                    startScreenReadLoop()
                    text = "⏸"
                }
            }
        }
        titleBar.addView(pauseBtn)
        // 最小化（缩成小圆点，类似老板键）
        val minBtn = TextView(this).apply {
            text = "—"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener { minimizeScreenReadWindow() }
        }
        titleBar.addView(minBtn)
        // 关闭
        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 12f
            setTextColor(Color.parseColor("#E24B4A"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener { stopScreenRead() }
        }
        titleBar.addView(closeBtn)
        win.addView(titleBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)))
        // ── ★ OCR 状态栏（识别中/已识别/未识别）+ 原文预览（2 行截短）──
        val statusText = TextView(this).apply {
            text = "🔄 等待首次识别..."
            textSize = 11f
            setTextColor(Color.parseColor("#FAC775"))  // 黄：识别中
            setPadding(dp(4), dp(2), dp(4), dp(0))
        }
        val ocrPreview = TextView(this).apply {
            text = ""
            textSize = 9f
            setTextColor(Color.parseColor("#CCCCCC"))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(4), dp(0), dp(4), dp(4))
        }
        val statusBar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusBar.addView(statusText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        statusBar.addView(ocrPreview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        win.addView(statusBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        // ── 结果区 ScrollView（多题答案列表）──
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(TextView(this).apply {
            text = "（匹配答案将在这里显示）"
            textSize = 11f
            setTextColor(Color.parseColor("#CCCCCC"))  // 透明底用浅灰更清晰
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), dp(4), dp(10))
        })
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            addView(container)
        }
        win.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        // ── 右下角缩放手柄 ◢ ──
        val resizeHandle = TextView(this).apply {
            text = "◢"
            textSize = 10f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
        }
        val handleLp = LinearLayout.LayoutParams(dp(24), dp(18))
        handleLp.gravity = Gravity.END
        win.addView(resizeHandle, handleLp)

        // ── 窗口参数：右上角靠边，260x360dp ──
        val p = WindowManager.LayoutParams(
            dp(260), dp(360),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            // ★ 统一用 TOP|END（右上角锚定）：p.x=距右边缘距离，p.y=距顶边缘距离
            //    之前 BOTTOM|END 导致拖动 p.y 方向反了 + 恢复函数用 TOP|END 不一致
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = dp(80)
        }
        try {
            windowManager.addView(win, p)
        } catch (e: Exception) {
            Log.e("FloatWindow", "读屏小窗创建失败: ${e.message}")
            return
        }
        screenReadWindow = win
        screenReadParams = p
        screenReadContainer = container
        screenReadStatusText = statusText  // ★ OCR 状态行引用
        screenReadOcrPreview = ocrPreview  // ★ OCR 原文预览引用
        bindScreenReadDragAndResize(win, titleBar, resizeHandle, p)
        Log.d("FloatWindow", "读屏小窗已创建 260x360dp")
    }

    /** 读屏小窗：标题栏拖拽移动 + ◢ 缩放 */
    private fun bindScreenReadDragAndResize(
        win: View, titleBar: View, resizeHandle: View, p: WindowManager.LayoutParams
    ) {
        titleBar.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0
            private var initY = 0
            private var startTX = 0f
            private var startTY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = p.x
                        initY = p.y
                        startTX = event.rawX
                        startTY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - startTX).toInt()
                        val dy = (event.rawY - startTY).toInt()
                        // ★ gravity = TOP|END 下：p.x 是相对右边缘的距离，所以手指向右 dx>0 应让 p.x 减少（窗口向右移）
                        p.x = initX - dx
                        p.y = initY + dy
                        try { windowManager.updateViewLayout(win, p) } catch (_: Exception) {}
                    }
                }
                return true
            }
        })
        resizeHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0   // ★ DOWN 时记录 p.x（修复：之前漏声明，引用 titleBar 类的 initX 报 unresolved）
            private var initY = 0   // ★ DOWN 时记录 p.y
            private var initW = 0
            private var initH = 0
            private var startTX = 0f
            private var startTY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = p.x   // ★ 记录初始 x
                        initY = p.y
                        initW = p.width
                        initH = p.height
                        startTX = event.rawX
                        startTY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - startTX).toInt()
                        val dy = (event.rawY - startTY).toInt()
                        val newW = (initW + dx).coerceIn(dp(180), dp(500))
                        val newH = (initH + dy).coerceIn(dp(240), dp(700))
                        p.width = newW
                        p.height = newH
                        // ★★★ 正确 resize（TOP|END 右上角锚定）★★★
                        //    窗口右上角（p.x, p.y）固定不动，只改 width/height
                        //    拖 ◢ 向右下 dx>0,dy>0 → 窗口右下扩（◢ 跟手指）
                        //    拖 ◢ 向左上 dx<0,dy<0 → 窗口右下缩
                        //    p.x 和 p.y 不变！
                        try { windowManager.updateViewLayout(win, p) } catch (_: Exception) {}
                    }
                }
                return true
            }
        })
    }

    /** 读屏小窗最小化：缩成屏幕边缘小圆点（可点恢复），不打扰作答 */
    private fun minimizeScreenReadWindow() {
        val w = screenReadWindow ?: return
        val p = screenReadParams ?: return
        // 记住位置（恢复时用）
        screenReadSavedRect = intArrayOf(p.x, p.y, p.width, p.height)
        try { windowManager.removeView(w) } catch (_: Exception) {}
        screenReadWindow = null
        screenReadParams = null
        // 创建 36dp 圆点
        val dot = TextView(this).apply {
            text = "📖"
            textSize = 16f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E61D9E75"))
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener {
                // 恢复小窗
                try { windowManager.removeView(this) } catch (_: Exception) {}
                restoreScreenReadWindow()
            }
        }
        val dotP = WindowManager.LayoutParams(
            dp(36), dp(36),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = screenReadSavedRect[1]
        }
        try {
            windowManager.addView(dot, dotP)
            minimizedScreenReadDot = dot
        } catch (_: Exception) {}
    }

    /** 从最小化圆点恢复读屏小窗 */
    private fun restoreScreenReadWindow() {
        // 移除圆点
        minimizedScreenReadDot?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        minimizedScreenReadDot = null
        val savedX = screenReadSavedRect[0]
        val savedY = screenReadSavedRect[1]
        val savedW = screenReadSavedRect[2]
        val savedH = screenReadSavedRect[3]
        // 重建小窗（用 saved 参数）
        val win = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#44000000"))  // 26% 不透明黑
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleBar.addView(TextView(this).apply {
            text = "读屏搜题"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        titleBar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
        val pauseBtn = TextView(this).apply {
            text = if (screenReadActive) "⏸" else "▶"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener {
                if (screenReadActive) {
                    stopScreenReadLoop()
                    text = "▶"
                } else {
                    startScreenReadLoop()
                    text = "⏸"
                }
            }
        }
        titleBar.addView(pauseBtn)
        val minBtn = TextView(this).apply {
            text = "—"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener { minimizeScreenReadWindow() }
        }
        titleBar.addView(minBtn)
        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 12f
            setTextColor(Color.parseColor("#E24B4A"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener { stopScreenRead() }
        }
        titleBar.addView(closeBtn)
        win.addView(titleBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)))
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(TextView(this).apply {
            text = "正在识别屏幕..."
            textSize = 11f
            setTextColor(Color.parseColor("#CCCCCC"))  // 透明底用浅灰更清晰
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), dp(4), dp(10))
        })
        val scroll = ScrollView(this).apply { addView(container) }
        win.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val resizeHandle = TextView(this).apply {
            text = "◢"
            textSize = 10f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
        }
        val handleLp = LinearLayout.LayoutParams(dp(24), dp(18))
        handleLp.gravity = Gravity.END
        win.addView(resizeHandle, handleLp)
        val p = WindowManager.LayoutParams(
            savedW, savedH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = savedX
            y = savedY
        }
        try {
            windowManager.addView(win, p)
        } catch (e: Exception) {
            Log.e("FloatWindow", "读屏恢复失败: ${e.message}")
            return
        }
        screenReadWindow = win
        screenReadParams = p
        screenReadContainer = container
        bindScreenReadDragAndResize(win, titleBar, resizeHandle, p)
    }

    /** 浮窗待机模式下 OCR 开关点击（暂停/恢复三态）：
     *  - 扫描中 → 暂停（保留 MediaProjection）
     *  - 已授权未扫描 → 恢复扫描
     *  - 未授权 → 授权+自动扫描
     */
    private fun toggleOcrFromStandby() {
        val btn = ocrTopSwitch ?: run {
            Log.e("FloatWindow", "toggleOcrFromStandby: ocrTopSwitch 为 null!")
            return
        }
        val dot = statusDot ?: return
        val text = statusText ?: return
        Log.d("FloatWindow", "toggleOcrFromStandby 被点击: scanning=$continuousScanning hasProjection=${OcrBridge.mediaProjection != null}")
        if (continuousScanning) {
            // 扫描中 → 暂停（保留 MediaProjection token，下次点继续扫描）
            stopContinuousScan()
            btn.text = "▶继续"
            btn.isEnabled = true
            updateStatusUi(dot, text, scanning = false)
            return
        }
        if (OcrBridge.mediaProjection == null) {
            // 未授权：触发授权，授权成功后自动开始扫描
            btn.text = "⏳授权中…"
            btn.isEnabled = false
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("ocr_request", true)
                putExtra("ocr_continuous", true)
            }
            startActivity(intent)
            authPollHandler.postDelayed(object : Runnable {
                override fun run() {
                    if (OcrBridge.mediaProjection != null) {
                        Log.d("FloatWindow", "授权成功，自动开始扫描")
                        startContinuousScan()
                        btn.text = "⏸暂停"
                        updateStatusUi(dot, text, scanning = true)
                        authPollHandler.postDelayed({
                            btn.isEnabled = true
                        }, 1000)
                    } else {
                        authPollHandler.postDelayed(this, 500)
                    }
                }
            }, 500)
        } else {
            // 已授权但未扫描 → 恢复扫描（不需要重新授权）
            startContinuousScan()
            btn.text = "⏸暂停"
            updateStatusUi(dot, text, scanning = true)
            btn.isEnabled = false
            authPollHandler.postDelayed({
                btn.isEnabled = true
            }, 1500)
        }
    }

    /** 更新状态栏（扫描中/停止，绿色/红色） */
    private fun updateStatusUi(dot: View, text: TextView, scanning: Boolean) {
        text.text = if (scanning) "扫描中" else "扫描停止"
        text.setTextColor(if (scanning) Color.parseColor("#1D9E75") else Color.parseColor("#E24B4A"))
        dot.setBackgroundResource(android.R.drawable.presence_online)
    }

    /** 截屏一次 + OCR + 分段搜题 + 更新结果
     *  OCR 范围 = 浮窗位置矩形（截屏后用白色遮盖浮窗本身，避免 OCR 识别到浮窗文字）
     */
    private fun captureAndProcessOnce() {
        // ★ 分配本次 OCR 序列号（旧的异步回调会被丢弃）
        val mySeq = ++ocrSeq
        val projection = OcrBridge.mediaProjection ?: run {
            Log.w("FloatWindow", "OCR: projection 为 null，跳过")
            return
        }
        // ★ 使用长期 ImageReader（startContinuousScan 时创建一次，不再每 2 秒创建/释放）
        val reader = ocrImageReader ?: run {
            Log.w("FloatWindow", "OCR: ocrImageReader 为 null，跳过")
            return
        }
        val p = params ?: return
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        // ★ 用 getLocationOnScreen 获取 recognizeArea 真实屏幕坐标（不依赖手动计算）
        val recogView = root?.findViewById<View>(R_ID_RECOGNIZE) ?: return
        val loc = IntArray(2)
        recogView.getLocationOnScreen(loc)
        // ★ 不 clip！绿色框可能在屏幕外（浮窗拖到边界），但 OCR 截屏必须 = 绿色框实际位置
        val rectL = loc[0]
        val rectT = loc[1]
        val rectR = rectL + recogView.width
        val rectB = rectT + recogView.height
        // ★ 同步更新 ocrRecognizeHeight（用户拖 ◢ 时用）
        ocrRecognizeHeight = recogView.height
        Log.d("FloatWindow", "DEBUG: recogView loc=[$rectL,$rectT] size=${recogView.width}x${recogView.height} (params=[${p.x},${p.y}])")
        if (rectR - rectL < 50 || rectB - rectT < 50) return
        // ★ 红色边框已经在 buildStandbyUi 里作为 recognizeArea 子 View 添加了
        // （永远在绿框内，自动跟随绿框大小+位置）
        serviceOcrHandler.postDelayed({
            Log.d("FloatWindow", "OCR 截屏尝试: rect=[${rectL},${rectT},${rectR},${rectB}]")
            val image = try { reader.acquireLatestImage() } catch (e: Exception) {
                Log.e("FloatWindow", "acquireLatestImage 异常: ${e.message}")
                null
            }
            Log.d("FloatWindow", "OCR 截屏: image=${image != null}")
            if (image == null) return@postDelayed
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width
                val fullBitmap = android.graphics.Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride, image.height,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                fullBitmap.copyPixelsFromBuffer(buffer)
                image.close()
                val cropW = rectR - rectL
                val cropH = rectB - rectT
                val cropped = android.graphics.Bitmap.createBitmap(fullBitmap, rectL, rectT, cropW, cropH)
                fullBitmap.recycle()
                // ★ 绿框内只有 ◢ 拖拽手柄，不需要涂白（用户已重新设计 UI：所有按钮在绿框外）
                val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(cropped, 0)
                serviceRecognizer.process(inputImage)
                    .addOnSuccessListener { result ->
                        // ★ 丢弃旧 OCR 回调（防错位渲染 = 用户感觉"两个程序同时跑"）
                        if (mySeq != ocrSeq) {
                            Log.d("FloatWindow", "OCR 回调过期(mySeq=$mySeq, currentSeq=$ocrSeq)，丢弃")
                            return@addOnSuccessListener
                        }
                        Log.d("FloatWindow", "OCR 成功(${result.text.length}字符): ${result.text.take(150).replace("\n", " ")}")
                        processOcrText(result.text)
                    }
                    .addOnFailureListener { e ->
                        if (mySeq != ocrSeq) return@addOnFailureListener  // 过期回调也丢弃
                        Log.e("FloatWindow", "OCR 失败: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e("FloatWindow", "截屏失败: ${e.message}")
            }
            // ★ 不再释放 VirtualDisplay/ImageReader（长期运行）
        }, 800)
    }

    /**
     * OCR 全文 → 按题目分段 → 每段单独搜题 → 更新结果区
     * OCR 出来的可能是整屏多道题，先按"答案"切分，每段提取题干关键词搜题
     */
    private fun processOcrText(text: String) {
        if (text.isBlank()) return
        val cleaned = text.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        Log.d("FloatWindow", "processOcrText: cleaned.len=${cleaned.length}")
        // 按"答案"切分：每段 = 题干+选项+答案
        val segments = cleaned.split(Regex("答案\\s*[:：]"))
        // ★ 用括号前的题干做查询词（不含选项字符，关键词短，匹配更精准）
        val queries = mutableListOf<String>()
        segments.forEach { seg ->
            val segTrim = seg.trim()
            if (segTrim.length < 4) return@forEach
            // 找括号：取"("或"（"之前的所有文字作为题干
            val parenIdx = maxOf(segTrim.lastIndexOf('('), segTrim.lastIndexOf('（'))
            val stem = if (parenIdx > 3) segTrim.substring(0, parenIdx).trim() else segTrim.take(20)
            if (stem.isNotBlank()) queries.add(stem)
        }
        Log.d("FloatWindow", "提取 ${queries.size} 个查询词: $queries")
        // 每段搜题，合并结果
        val merged = LinkedHashMap<Long, Pair<SearchResult, Int>>()
        queries.forEach { q ->
            if (q.length < 3) return@forEach
            val results = bank.search(q, limit = 5)
            results.forEach { r ->
                val old = merged[r.question.id]
                if (old == null || r.score > old.second) {
                    merged[r.question.id] = r to r.score
                }
            }
        }
        ocrRawText = cleaned
        // ★ 按相关度排序 + 限制最多 3 条候选（用户要求"如不确定就继续输出次结果"）
        val sorted = merged.values.sortedByDescending { it.second }.take(5).map { it.first }
        Log.d("FloatWindow", "搜题结果: ${merged.size} 条唯一匹配，显示前 ${sorted.size}")
        renderScanResults(sorted)
    }

    // ═══════════════ 读屏模式：多题切分 + 匹配渲染 ═══════════════

    /**
     * 读屏模式 OCR 文本处理（B 方案：全列表输出）：
     * - 文本 ≤ 300 字 → 单题：直接 LCS 匹配，输出单张卡片
     * - 文本 > 300 字 → 多题：按题号+选项结构切分 N 题，每题独立匹配，按屏幕顺序输出列表
     * - 无匹配时小窗内显示提示
     */
    private fun processScreenReadText(text: String) {
        Log.d("FloatWindow", "processScreenReadText 收到: text.length=${text.length} '${text.take(60)}'")
        val container = screenReadContainer ?: return
        // ★ 同步更新 OCR 状态栏（让用户知道 OCR 是否在工作）
        screenReadStatusText?.let { st ->
            if (text.isBlank()) {
                st.text = "⚠ 未识别到文字"
                st.setTextColor(Color.parseColor("#FFCCCC"))  // 浅红
            } else {
                st.text = "✓ 已识别 ${text.length} 字"
                st.setTextColor(Color.parseColor("#9FE1CB"))  // 浅绿
            }
        }
        // ★ OCR 原文预览（2 行截短）
        screenReadOcrPreview?.let { pv ->
            pv.text = text.take(150).replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        }
        if (text.isBlank()) {
            // OCR 成功但没文字（屏幕纯色/低对比）→ 提示用户，便于区分"OCR 在跑 vs 没跑"
            container.removeAllViews()
            container.addView(TextView(this).apply {
                this.text = "（屏幕无文字内容）"
                textSize = 11f
                setTextColor(Color.parseColor("#CCCCCC"))
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(16), dp(4), dp(16))
            })
            return
        }
        val cleaned = text.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        Log.d("FloatWindow", "读屏 OCR(${cleaned.length}字): ${cleaned.take(80)}...")
        container.removeAllViews()
        if (cleaned.length <= 300) {
            // ── 单题模式：整段作为查询词 ──
            val results = bank.search(cleaned, limit = 5)
            Log.d("FloatWindow", "读屏单题匹配: ${results.size} 条")
            renderScreenReadResults(container, results, isMulti = false)
        } else {
            // ── 多题模式：按结构切分 ──
            val questions = splitScreenReadQuestions(cleaned)
            Log.d("FloatWindow", "读屏多题切分: ${questions.size} 段")
            if (questions.size <= 1) {
                // 切分失败（无题号结构）→ 仍按单题处理
                val results = bank.search(cleaned, limit = 5)
                renderScreenReadResults(container, results, isMulti = false)
                return
            }
            // 每段独立匹配，按屏幕顺序收集
            val allResults = mutableListOf<List<SearchResult>>()
            questions.forEach { seg ->
                val r = bank.search(seg, limit = 3)
                allResults.add(r)
            }
            renderScreenReadMulti(container, questions, allResults)
        }
    }

    /**
     * 多题切分：按 "数字. " 题号 + 选项结构把长文本切成 N 个候选题目
     * 规则：
     * 1. 找所有 "数字." / "数字、" 开头位置（题号锚点）
     * 2. 题号之间 = 一道题
     * 3. 若题号太少（<2）→ 退化单题（整段）
     */
    private fun splitScreenReadQuestions(text: String): List<String> {
        // 匹配 "1. " "2、" "3．" 等题号模式（OCR 常见变体）
        val numRe = Regex("(?<![\\d])(\\d{1,3})[.、．]\\s*")
        val matches = numRe.findAll(text)
        val starts = matches.map { it.range.first }.toList()
        if (starts.size < 2) return listOf(text)
        val segments = mutableListOf<String>()
        for (i in starts.indices) {
            val s = starts[i]
            val e = if (i + 1 < starts.size) starts[i + 1] else text.length
            val seg = text.substring(s, e).trim()
            if (seg.length >= 8) segments.add(seg)
        }
        return segments
    }

    /** 渲染多题列表（每题一张卡，按屏幕顺序） */
    private fun renderScreenReadMulti(
        container: LinearLayout, questions: List<String>, resultsList: List<List<SearchResult>>
    ) {
        // 头部提示：识别到 N 题（在透明窗口底上用浅绿）
        container.addView(TextView(this).apply {
            text = "📋 识别到 ${questions.size} 道题（滚动切换后自动更新）"
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#FFFFFF"))  // 白字更醒目
            setPadding(dp(4), dp(2), dp(4), dp(6))
        })
        questions.forEachIndexed { idx, seg ->
            val results = resultsList[idx]
            val title = "第 ${idx + 1} 题"
            if (results.isEmpty()) {
                container.addView(TextView(this).apply {
                    text = "$title 未匹配"
                    textSize = 11f
                    setTextColor(Color.parseColor("#CCCCCC"))  // 透明底用浅灰更清晰
                    setPadding(dp(4), dp(4), dp(4), dp(2))
                })
                return@forEachIndexed
            }
            val best = results[0]
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    // ★ 卡片半透明白底：透明窗口底上卡片更醒目
                    setColor(Color.parseColor("#E6FFFFFF"))
                    cornerRadius = dp(8).toFloat()
                }
                setPadding(dp(8), dp(6), dp(8), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
            }
            card.addView(TextView(this).apply {
                text = title
                textSize = 10f
                setTextColor(Color.parseColor("#1D9E75"))  // 白底上用深绿更清晰
                setTypeface(typeface, Typeface.BOLD)
            })
            card.addView(TextView(this).apply {
                text = best.question.stem.take(40) + if (best.question.stem.length > 40) "..." else ""
                textSize = 10f
                setTextColor(Color.parseColor("#222222"))  // 白底上用深灰更清晰
                maxLines = 2
            })
            if (best.question.answer.isNotBlank()) {
                card.addView(TextView(this).apply {
                    text = "✔ ${best.question.answer}"
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#1D9E75"))  // 答案保持深绿
                })
            }
            card.addView(TextView(this).apply {
                text = "相关度 ${best.score}"
                textSize = 9f
                setTextColor(Color.parseColor("#666666"))  // 白底上用中灰
            })
            container.addView(card)
        }
    }

    /** 渲染读屏单题结果（样式与浮窗卡片一致，白底深字更清晰） */
    private fun renderScreenReadResults(
        container: LinearLayout, results: List<SearchResult>, isMulti: Boolean
    ) {
        if (results.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "未匹配到题库题目"
                textSize = 11f
                setTextColor(Color.parseColor("#CCCCCC"))  // 透明底用浅灰更清晰
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(16), dp(4), dp(16))
            })
            return
        }
        results.forEachIndexed { idx, sr ->
            val isBest = idx == 0
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(if (isBest) Color.parseColor("#E8F5E9") else Color.parseColor("#F5F5F5"))
                    cornerRadius = dp(8).toFloat()
                }
                setPadding(dp(8), dp(6), dp(8), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
            }
            card.addView(TextView(this).apply {
                text = (if (isBest) "🎯 " else "${idx + 1}. ") + sr.question.stem
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#222222"))
                if (sr.question.options.isNotEmpty() || sr.question.answer.isNotEmpty()) {
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
            })
            sr.question.options.forEach { opt ->
                card.addView(TextView(this).apply {
                    text = opt
                    textSize = 10f
                    setTextColor(Color.parseColor("#555555"))
                    setPadding(dp(2), dp(0), dp(2), dp(0))
                })
            }
            if (sr.question.answer.isNotBlank()) {
                card.addView(TextView(this).apply {
                    text = "✔ ${sr.question.answer}"
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#1D9E75"))
                })
            }
            card.addView(TextView(this).apply {
                text = "来源：${sr.bankName} · 相关度 ${sr.score}"
                textSize = 9f
                setTextColor(Color.parseColor("#AAAAAA"))
            })
            container.addView(card)
        }
    }

    /** 渲染实时扫描结果到结果区 */
    private fun renderScanResults(results: List<SearchResult>) {
        val resultContainer = ocrResultContainer ?: return
        resultContainer.removeAllViews()
        // ★ 滚到顶部（让用户看到最新最佳匹配）
        ocrResultScroll?.post { ocrResultScroll?.scrollTo(0, 0) }
        // ★ 模块3：OCR 识别状态实时显示（独立模块在绿框下方，不随匹配结果滚动）
        ocrStatusTextView?.let { st ->
            if (ocrRawText.isNotBlank()) {
                st.text = "[OCR 识别] ${ocrRawText.take(120).replace("\n", " ")}${if (ocrRawText.length > 120) "..." else ""}"
                st.visibility = View.VISIBLE
            } else {
                st.text = ""
                st.visibility = View.GONE
            }
        }
        if (results.isEmpty()) {
            resultContainer.addView(TextView(this).apply {
                text = "未匹配到题库题目"
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                gravity = Gravity.CENTER
            })
            // ★ 没匹配：浮窗总高根据内容自适应（无结果时缩到最小）
            updateFloatHeightAfterRender()
            return
        }
// 显示最佳候选（前 5 条）
        results.forEachIndexed { idx, sr ->
            val isBest = idx == 0
            // ★ 结果卡片：不透明背景（清晰可读）+ WRAP_CONTENT 居中
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(
                    if (isBest) Color.parseColor("#E8F5E9")  // 最佳=浅绿
                    else Color.parseColor("#F5F5F5")          // 次候选=浅灰
                )
                setPadding(dp(10), dp(6), dp(10), dp(6))
                // ★ 卡片 MATCH_PARENT 宽（与 resultContainer 一致），子 View MATCH_PARENT 嵌套 MATCH_PARENT 父不再异常
                //    删掉 lp.gravity = Gravity.CENTER_HORIZONTAL（旧写法在多次扫描 + 滚动时偶尔导致卡片视觉重叠）
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(4)
                }
            }
            card.addView(TextView(this).apply {
                text = (if (isBest) "🎯 " else "${idx + 1}. ") + sr.question.stem
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#222222"))
                if (sr.question.options.isEmpty() && sr.question.answer.isEmpty()) {
                    // ★ 切块模式：整块原样显示（题干+选项+答案都在里面），不截断
                } else {
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
            })
            // ★ 显示选项（考试 APP 会改选项顺序，必须让用户看到选项对照）
            sr.question.options.forEach { opt ->
                card.addView(TextView(this).apply {
                    text = opt
                    textSize = 10f
                    setTextColor(Color.parseColor("#555555"))
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.leftMargin = dp(4)
                    lp.topMargin = dp(1)
                    layoutParams = lp
                })
            }
            if (sr.question.answer.isNotBlank()) {
                card.addView(TextView(this).apply {
                    text = "✔ ${sr.question.answer}"
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#1D9E75"))
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.topMargin = dp(4)
                    layoutParams = lp
                })
            }
            card.addView(TextView(this).apply {
                text = "来源：${sr.bankName} · 相关度 ${sr.score}"
                textSize = 10f
                setTextColor(Color.parseColor("#AAAAAA"))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(2)
                layoutParams = lp
            })
            resultContainer.addView(card)
        }
        // ★ 有匹配：浮窗总高根据内容自适应（上限 180dp）
        updateFloatHeightAfterRender()
    }

    /**
     * 根据 OCR 结果容器内容高度自适应调 ScrollView 高度（上限 180dp，下限 60dp）
     * 同时调 WindowManager 浮窗总高 = topBar(30) + topSpace(36) + 绿框 + 输出框
     * → 没/少内容时浮窗缩到最小省屏，多内容时 180dp 上限 + 内部滚动
     */
    private fun updateFloatHeightAfterRender() {
        val ocrScroll = ocrResultScroll ?: return
        val contentRoot = root ?: return
        val p = params ?: return
        ocrScroll.post {
            val container = ocrResultContainer ?: return@post
            // 等布局完成后再测量（post 到下一帧）
            val contentH = if (container.height > 0) container.height else container.measuredHeight
            // ★ 关键修复：ScrollView 高 = min(desired, container 剩余)，保证不溢出 container（避免卡片侵入上方区域）
            val desired = contentH.coerceIn(dp(60), dp(180))
            val greenH = ocrRecognizeHeight
            // 模块3（OCR 状态）高度：可见才占空间
            val ocrStatusH = if (ocrStatusTextView?.visibility == View.VISIBLE) (ocrStatusTextView?.height ?: 0) else 0
            val containerRemaining = p.height - dp(28) - dp(0) - greenH - ocrStatusH
            val scrollH = minOf(desired, containerRemaining).coerceAtLeast(dp(60))
            val olp = ocrScroll.layoutParams as? LinearLayout.LayoutParams ?: return@post
            if (olp.height != scrollH) {
                olp.height = scrollH
                ocrScroll.layoutParams = olp
            }
            // 浮窗总高同步 = topBar(28) + topSpace(0) + 绿框 + 模块3 + ScrollView(scrollH)
            val actualH = dp(28) + dp(0) + greenH + ocrStatusH + scrollH
            if (p.height != actualH) {
                p.height = actualH
                windowManager.updateViewLayout(contentRoot, p)
            }
        }
    }

    private fun startOcrPolling(input: EditText, searchBtn: Button, hint: TextView) {
        // 记住当前时间戳作为基线（OCR 完成后会更新为更新时间）
        val prefs = getSharedPreferences("ocr_prefs", Context.MODE_PRIVATE)
        lastOcrTimestamp = prefs.getLong("ocr_timestamp", 0)
        ocrPollCount = 0
        ocrPollHandler.post(object : Runnable {
            override fun run() {
                ocrPollCount++
                val cur = getSharedPreferences("ocr_prefs", Context.MODE_PRIVATE)
                    .getLong("ocr_timestamp", 0)
                if (cur > lastOcrTimestamp) {
                    // OCR 完成
                    val text = getSharedPreferences("ocr_prefs", Context.MODE_PRIVATE)
                        .getString("ocr_result", "") ?: ""
                    if (text.isNotBlank() && text != "区域太小" && text != "截屏失败") {
                        input.setText(text)
                        hint.text = "✓ OCR 完成：${text.take(20)}…"
                        hint.setTextColor(Color.parseColor("#1D9E75"))
                        // 自动触发搜索
                        searchBtn.performClick()
                    } else {
                        hint.text = "⚠ OCR 未识别到文字"
                        hint.setTextColor(Color.parseColor("#E24B4A"))
                    }
                    return
                }
                if (ocrPollCount >= 30) {
                    // 15 秒超时
                    hint.text = "⚠ OCR 超时"
                    hint.setTextColor(Color.parseColor("#E24B4A"))
                    return
                }
                ocrPollHandler.postDelayed(this, 500)
            }
        })
    }

    private fun switchToSearchMode() {
        val r = root as? ViewGroup ?: return
        r.getChildAt(0).visibility = View.GONE
        r.getChildAt(1).visibility = View.VISIBLE
        // 搜题模式需要接收输入
        params?.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        params?.height = dp(460)
        windowManager.updateViewLayout(r, params)
    }

    private fun switchToStandbyMode() {
        val r = root as? ViewGroup ?: return
        r.getChildAt(0).visibility = View.VISIBLE
        r.getChildAt(1).visibility = View.GONE
        params?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        // ★ 恢复浮窗原始高度 358dp（之前 searchMode 改成 460dp 没恢复）
        params?.height = dp(358)
        windowManager.updateViewLayout(r, params)
    }

    private fun bindResizeAndDrag(resizeHandle: View, dragArea: View) {
        val p = params ?: return
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        dragArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = p.x; initialY = p.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = initialX + (event.rawX - initialTouchX).toInt()
                    p.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(root, p)
                    true
                }
                else -> false
            }
        }
        var initBoxW = dragArea.width
        var initBoxH = dragArea.height
        var initTX = 0f
        var initTY = 0f
        resizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // ★ 对角线拖动：dx 改宽度，dy 改高度
                    initBoxW = dragArea.width
                    initBoxH = dragArea.height
                    initTX = event.rawX; initTY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // ★ ◢ 对角线拖动：dx 改宽度，dy 改高度
                    val dx = (event.rawX - initTX).toInt()
                    val dy = (event.rawY - initTY).toInt()
                    val newW = (initBoxW + dx).coerceIn(dp(100), dp(720))  // 宽度 100-720dp（可超出浮窗宽度）
                    val newH = (initBoxH + dy).coerceIn(dp(20), dp(400))   // 高度 20-400dp（20dp = 单行字高度）
                    // ★ recognizeArea 是 LinearLayout 子 View，layoutParams 是 LinearLayout.LayoutParams
                    val lp = dragArea.layoutParams as LinearLayout.LayoutParams
                    lp.width = newW
                    lp.height = newH
                    dragArea.layoutParams = lp
                    ocrRecognizeHeight = newH  // 同步给字段（OCR 用）
                    // ★ 浮窗总高动态同步：topBar(28) + topSpace(0) + 绿框 + 模块3 + 输出框(180)
                    //     否则绿框变小后 container 内 LinearLayout 末尾会留白（content 总高 < container 高）
                    val ocrStatusH = if (ocrStatusTextView?.visibility == View.VISIBLE) (ocrStatusTextView?.height ?: 0) else 0
                    val targetH = dp(28) + dp(0) + newH + ocrStatusH + dp(180)
                    // ★ 浮窗 width 同步：绿框超出浮窗默认宽时浮窗跟着变（让绿框覆盖桌面 OCR 更多内容）
                    val targetW = maxOf(dp(360), newW + dp(8))
                    if (p.height != targetH || p.width != targetW) {
                        p.height = targetH
                        p.width = targetW
                        windowManager.updateViewLayout(root, p)
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        continuousScanning = false
        screenReadActive = false
        OcrBridge.currentMode = OcrBridge.MODE_NONE
        scanHandler.removeCallbacksAndMessages(null)
        OcrBridge.isRunning = false
        // ★ 读屏专用资源清理
        releaseScreenReadResources()
        // ★ 读屏小窗/圆点清理（防止窗口泄漏）
        screenReadWindow?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            screenReadWindow = null
        }
        minimizedScreenReadDot?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            minimizedScreenReadDot = null
        }
        lastFrameThumb?.recycle()
        lastFrameThumb = null
        // ★ 写 SharedPreferences 让主页轮询到
        getSharedPreferences("souti_state", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("service_running", false).apply()
        root?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            root = null
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    /** 切换红色边框（OCR 范围调试用） */
    fun setDebugBorderVisible(visible: Boolean) {
        redBorderView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** 外部触发：主页发 action 控制 OCR */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_AUTH_OCR -> {
                // 启动 MainActivity 透明授权
                val authIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("ocr_request", true)
                    putExtra("ocr_continuous", true)
                }
                startActivity(authIntent)
            }
            ACTION_START_SCAN -> {
                if (OcrBridge.mediaProjection == null) {
                    // 没授权：先去授权
                    val authIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("ocr_request", true)
                        putExtra("ocr_continuous", true)
                    }
                    startActivity(authIntent)
                } else {
                    // ★ 已授权：按需显示主浮窗（onCreate 不再自动显示，避免读屏模式被影响）
                    if (root == null) showFloatWindow()
                    startContinuousScan()
                }
            }
            ACTION_STOP_SCAN -> {
                stopContinuousScan()
            }
            ACTION_SCREEN_READ_START -> {
                if (OcrBridge.mediaProjection == null) {
                    // 没授权：先去授权（复用浮窗授权流程，标记读屏模式）
                    val authIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("ocr_request", true)
                        putExtra("ocr_continuous", true)
                        putExtra("ocr_screen_read", true)
                    }
                    startActivity(authIntent)
                } else {
                    startScreenRead()
                }
            }
            ACTION_SCREEN_READ_STOP -> {
                stopScreenRead()
            }
            ACTION_TOGGLE_DEBUG -> {
                // ★ 切换红框可见性（toggle：每次点都反转）
                val cur = redBorderView?.visibility == View.VISIBLE
                setDebugBorderVisible(!cur)
            }
            ACTION_SHOW_WINDOW -> {
                // ★ Service 已在跑，root 被隐藏了，重新添加浮窗到 WindowManager
                // 先恢复圆点状态（如果最小化中）
                if (isMinimized) {
                    restoreFromDot()
                    return START_STICKY
                }
                restoreFloatWindow()
            }
            ACTION_STOP_SELF -> {
                Log.d("FloatWindow", "ACTION_STOP_SELF 收到")
                stopContinuousScan()
                stopScreenRead()
                releaseScreenReadResources()
                OcrBridge.currentMode = OcrBridge.MODE_NONE
                // 释放 MediaProjection
                try {
                    OcrBridge.mediaProjection?.stop()
                } catch (_: Exception) {}
                OcrBridge.mediaProjection = null
                try {
                    if (android.os.Build.VERSION.SDK_INT >= 24) {
                        stopForeground(android.app.Service.STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                } catch (_: Exception) {}
                stopSelf()
                // 写 prefs 即时通知主页（不靠 onDestroy）
                try {
                    getSharedPreferences("souti_state", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("service_running", false).apply()
                } catch (_: Exception) {}
            }            ACTION_AUTH_RESULT -> {
                Log.d("FloatWindow", "ACTION_AUTH_RESULT 收到")
                try {
                    val resultCode = intent.getIntExtra("result_code", 0)
                    val data: Intent? = if (android.os.Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra("result_data", Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra("result_data")
                    }
                    if (data == null) {
                        Log.e("FloatWindow", "授权 data 为 null")
                        writeErrorAndRollbackUi("授权失败：data 为 null")
                        return START_STICKY
                    }
                    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                    val projection = mpm.getMediaProjection(resultCode, data)
                    if (projection == null) {
                        Log.e("FloatWindow", "getMediaProjection 返回 null")
                        writeErrorAndRollbackUi("getMediaProjection 失败（Token 过期？）")
                        return START_STICKY
                    }
                    // ★ Android 14 强制要求：createVirtualDisplay 前必须注册 Callback
                    projection.registerCallback(object : android.media.projection.MediaProjection.Callback() {
                        override fun onStop() {
                            Log.d("FloatWindow", "MediaProjection onStop")
                            stopContinuousScan()
                            stopScreenRead()
                            OcrBridge.currentMode = OcrBridge.MODE_NONE
                            ocrTopSwitch?.text = if (OcrBridge.mediaProjection == null) "🔓授权并扫描" else "🔄开始扫描"
                            statusDot?.let { d -> statusText?.let { t -> updateStatusUi(d, t, scanning = false) } }
                        }
                    }, serviceOcrHandler)
                    OcrBridge.mediaProjection = projection
                    // ★ 读屏模式：授权成功后启动全屏读屏扫描（而非绿框扫描）
                    if (OcrBridge.screenRead) {
                        Log.d("FloatWindow", "Service 创建 MediaProjection 成功（读屏模式），自动开始读屏扫描")
                        OcrBridge.screenRead = false  // 一次性标记，消费掉
                        // ★ 立即移除主浮窗（避免"闪一下"：授权完成瞬间若主浮窗已存在需立刻隐藏）
                        root?.let {
                            try { windowManager.removeView(it) } catch (_: Exception) {}
                            Log.d("FloatWindow", "读屏模式：授权完成立即隐藏主浮窗")
                        }
                        startScreenRead()
                        return START_STICKY
                    }
                    Log.d("FloatWindow", "Service 创建 MediaProjection 成功，自动开始扫描")
                    startContinuousScan()
                    // ★ 按需显示主浮窗（首次授权后用户点浮窗搜题要看到主浮窗）
                    if (root == null) showFloatWindow()
                    // ★ 启动成功才改 UI 文字；如果 scan 没启动成功，OCR 失败时回滚
                    if (continuousScanning) {
                        ocrTopSwitch?.text = "⏹停止扫描"
                        statusDot?.let { d -> statusText?.let { t -> updateStatusUi(d, t, scanning = true) } }
                    } else {
                        Log.e("FloatWindow", "startContinuousScan 内部失败，回滚 UI")
                        writeErrorAndRollbackUi("OCR 启动失败（VirtualDisplay 创建失败）")
                    }
                } catch (e: Throwable) {
                    Log.e("FloatWindow", "Service 创建 MediaProjection 失败", e)
                    writeErrorAndRollbackUi("授权失败: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
        return START_STICKY
    }

    /** 写错误到 prefs + 回滚 UI 按钮 */
    private fun writeErrorAndRollbackUi(errorMsg: String) {
        Log.e("FloatWindow", "OCR 授权失败: $errorMsg")
        try {
            getSharedPreferences("souti_state", android.content.Context.MODE_PRIVATE)
                .edit().putString("last_error", errorMsg).apply()
        } catch (_: Throwable) {}
        try {
            ocrTopSwitch?.text = "🔓授权并扫描"
            ocrTopSwitch?.isEnabled = true
            statusDot?.let { d -> statusText?.let { t -> updateStatusUi(d, t, scanning = false) } }
        } catch (_: Throwable) {}
    }

    // ============ ★ 最小化（独立 40x40 圆点窗口，最可靠） ============

    /** 最小化：隐藏完整浮窗 → 显示 28x28 红色 + 按钮（无绿圈、可拖动、出现在 — 消失的位置） */
    private fun minimizeToDot() {
        if (isMinimized) return
        stopContinuousScan()  // 暂停扫描（保留 MediaProjection）
        // ★ 先算 — 按钮的屏幕中心位置（+ 号初始位置 = — 消失的位置；root.removeView 之后 View 已 detach，拿不到坐标）
        val closeBtn = closeBtnRef
        var xCenter = 0
        var yCenter = 0
        if (closeBtn != null && closeBtn.isAttachedToWindow) {
            val loc = IntArray(2)
            closeBtn.getLocationOnScreen(loc)
            xCenter = loc[0] + closeBtn.width / 2
            yCenter = loc[1] + closeBtn.height / 2
        } else {
            val p = params
            if (p != null) {
                xCenter = p.x + p.width - dp(20)
                yCenter = p.y + dp(14)
            }
        }
        // 1. 移除完整浮窗
        val r = root
        if (r != null) {
            try { windowManager.removeView(r) } catch (_: Exception) {}
            root = null
        }
        // 2. 创建 28x28 + 按钮窗口（无绿圈、红色 + 文字、背景完全透明）
        val dotSize = dp(28)
        val dot = TextView(this).apply {
            text = "+"
            setTextColor(Color.parseColor("#E24B4A"))  // 红色（与 — 按钮同色）
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            // ★ 完全透明背景（不要任何白底/色底）
            setBackgroundColor(Color.TRANSPARENT)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        val dotP = WindowManager.LayoutParams(
            dotSize, dotSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 居中对齐到 — 消失的位置
            // ★ 微调：+ 号（22f）baseline 视觉中心略低于 —（16f），dot 上移 dp(3) 补偿
            x = if (xCenter > 0) xCenter - dotSize / 2 else dp(40)
            y = if (yCenter > 0) yCenter - dotSize / 2 - dp(3) else dp(200)
        }
        // ★ 可拖动
        var initX = 0
        var initY = 0
        var initTouchX = 0f
        var initTouchY = 0f
        dot.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = dotP.x
                    initY = dotP.y
                    initTouchX = event.rawX
                    initTouchY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    dotP.x = initX + (event.rawX - initTouchX).toInt()
                    dotP.y = initY + (event.rawY - initTouchY).toInt()
                    windowManager.updateViewLayout(dot, dotP)
                }
            }
            false  // 不消费，让 setOnClickListener 也能触发
        }
        dot.setOnClickListener { restoreFromDot() }  // 点击恢复
        try {
            windowManager.addView(dot, dotP)
        } catch (e: Exception) {
            Log.e("FloatWindow", "创建 + 按钮失败", e)
            return
        }
        minimizedDot = dot
        minimizedDotParams = dotP
        isMinimized = true
        Log.d("FloatWindow", "最小化：显示 28x28 + 按钮（无绿圈）")
    }

    /** 恢复：移除圆点 → 重建完整浮窗 */
    private fun restoreFromDot() {
        if (!isMinimized) return
        // 1. 移除圆点
        minimizedDot?.let { d ->
            try { windowManager.removeView(d) } catch (_: Exception) {}
        }
        minimizedDot = null
        // 2. 重建完整浮窗（沿用原 params 位置 + 重算 height 避免 resize 残留）
        val p = params ?: run { isMinimized = false; return }
        // ★ 重算 height：minimize 时 params.height 保留了 resize 后的尺寸（小绿框时 height 偏小）
        //    restore 时按当前公式（topBar 28 + topSpace 0 + 当前绿框高 + OCR 180）重算，避免 OCR ScrollView 被裁
        val greenH = ocrRecognizeHeight
        val ocrStatusH = if (ocrStatusTextView?.visibility == View.VISIBLE) (ocrStatusTextView?.height ?: 0) else 0
        p.height = dp(28) + dp(0) + greenH + ocrStatusH + dp(180)
        val r = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        root = r
        buildStandbyUi(r)
        val searchUi = buildSearchUi(r)
        searchUi.visibility = View.GONE
        try {
            windowManager.addView(r, p)
        } catch (e: Exception) {
            Log.e("FloatWindow", "恢复浮窗失败", e)
        }
        isMinimized = false
        Log.d("FloatWindow", "恢复：重建完整浮窗")
    }
}
