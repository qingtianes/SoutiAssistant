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
            showFloatWindow()
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
        val r = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
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
        }

        // ★ 顶栏（绿框外）：●扫描中 + 🔍搜题 + 授权并启动 + ✕
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)  // 完全透明
            setPadding(dp(2), dp(2), dp(2), dp(2))  // 缩小 padding
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
        // ★ 左侧弹性空间（让授权按钮居中）
        topBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        })
        // 授权/扫描按钮（混合态：未授权=授权，已授权未扫描=开始，已扫描=暂停）
        val topBtn = TextView(this).apply {
            text = when {
                continuousScanning -> "⏸暂停"
                OcrBridge.mediaProjection == null -> "🔓授权"
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
        }
        // 顶部 padding（极简：topSpace=0，绿框紧贴顶栏下沿，视觉上 0 间距）
        val topSpace = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(0))
        }
        container.addView(topSpace)

        // ★ 绿框识别区（纯识别区，里面只有 ◢ 拖拽手柄，其他都没有！）
        val recognizeArea = FrameLayout(this).apply {
            id = R_ID_RECOGNIZE  // ★ 给个稳定 ID（captureAndProcessOnce 用 getLocationOnScreen 获取真实坐标）
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#11FFFFFF"))  // 极淡白底（方便用户看到绿框边界）
                setStroke(dp(2), Color.parseColor("#1D9E75"))
                cornerRadius = dp(10).toFloat()
            }
        }
        recognizeArea.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(150)
        ).apply {
            leftMargin = dp(4)
            rightMargin = dp(4)
        }
        container.addView(recognizeArea)

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
            addView(resultsContainer)
        }
        // ★ 输出显示框默认高度 dp(180)，renderScanResults 后根据实际内容自适应调高
        //    没/少内容时浮窗变小省屏，多内容时保持 180dp 上限 + ScrollView 内部滚动
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
    private val serviceRecognizer by lazy {
        TextRecognition.getClient(
            com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
        )
    }
    private var serviceImageReader: android.media.ImageReader? = null
    private var serviceVirtualDisplay: android.hardware.display.VirtualDisplay? = null

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

    /** 渲染实时扫描结果到结果区 */
    private fun renderScanResults(results: List<SearchResult>) {
        val resultContainer = ocrResultContainer ?: return
        resultContainer.removeAllViews()
        // ★ 滚到顶部（让用户看到最新最佳匹配）
        ocrResultScroll?.post { ocrResultScroll?.scrollTo(0, 0) }
        // ★ 始终先显示 OCR 识别到的原文（让用户知道 OCR 真的在识别、识别到了什么）
        if (ocrRawText.isNotBlank()) {
            resultContainer.addView(TextView(this).apply {
                text = "[OCR 识别] ${ocrRawText.take(120).replace("\n", " ")}${if (ocrRawText.length > 120) "..." else ""}"
                textSize = 10f
                setTextColor(Color.parseColor("#888888"))
                setPadding(dp(4), dp(2), dp(4), dp(4))
            })
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
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(4)
                lp.gravity = Gravity.CENTER_HORIZONTAL  // 居中
                layoutParams = lp
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
            // 上限 180dp（多匹配时滚动），下限 60dp（无内容也保留可见区域）
            val desired = contentH.coerceIn(dp(60), dp(180))
            val olp = ocrScroll.layoutParams as? LinearLayout.LayoutParams ?: return@post
            if (olp.height != desired) {
                olp.height = desired
                ocrScroll.layoutParams = olp
            }
            // 浮窗总高同步：topBar(28) + topSpace(0) + 绿框 + 输出框（自适应后高度）
            val greenH = ocrRecognizeHeight
            val targetH = dp(28) + dp(0) + greenH + desired
            if (p.height != targetH) {
                p.height = targetH
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
                    val newW = (initBoxW + dx).coerceIn(dp(100), dp(360))  // 宽度 100-360dp
                    val newH = (initBoxH + dy).coerceIn(dp(20), dp(400))   // 高度 20-400dp（20dp = 单行字高度）
                    // ★ recognizeArea 是 LinearLayout 子 View，layoutParams 是 LinearLayout.LayoutParams
                    val lp = dragArea.layoutParams as LinearLayout.LayoutParams
                    lp.width = newW
                    lp.height = newH
                    dragArea.layoutParams = lp
                    ocrRecognizeHeight = newH  // 同步给字段（OCR 用）
                    // ★ 浮窗总高动态同步：topBar(28) + topSpace(0) + 绿框 + 输出框(180)
                    //     否则绿框变小后 container 内 LinearLayout 末尾会留白（content 总高 < container 高）
                    val targetH = dp(28) + dp(0) + newH + dp(180)
                    if (p.height != targetH) {
                        p.height = targetH
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
        scanHandler.removeCallbacksAndMessages(null)
        OcrBridge.isRunning = false
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
                    startContinuousScan()
                }
            }
            ACTION_STOP_SCAN -> {
                stopContinuousScan()
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
                if (this.root == null && params != null) {
                    val r = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
                    this.root = r
                    buildStandbyUi(r)
                    val searchUi = buildSearchUi(r)
                    searchUi.visibility = View.GONE
                    try {
                        windowManager.addView(r, params)
                    } catch (_: Exception) {}
                }
            }
            ACTION_STOP_SELF -> {
                // ★ Android 14+ 前台服务必须在 stopService 之前 stopForeground（否则 onDestroy 不触发）
                Log.d("FloatWindow", "ACTION_STOP_SELF 收到")
                stopContinuousScan()
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
                            // 用户停止录屏时，自动停止 OCR 扫描
                            stopContinuousScan()
                            ocrTopSwitch?.text = if (OcrBridge.mediaProjection == null) "🔓授权并扫描" else "🔄开始扫描"
                            statusDot?.let { d -> statusText?.let { t -> updateStatusUi(d, t, scanning = false) } }
                        }
                    }, serviceOcrHandler)
                    OcrBridge.mediaProjection = projection
                    Log.d("FloatWindow", "Service 创建 MediaProjection 成功，自动开始扫描")
                    startContinuousScan()
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

    /** 最小化：隐藏完整浮窗 → 显示 40x40 绿底圆点（可拖动） */
    private fun minimizeToDot() {
        if (isMinimized) return
        stopContinuousScan()  // 暂停扫描（保留 MediaProjection）
        // 1. 移除完整浮窗
        val r = root
        if (r != null) {
            try { windowManager.removeView(r) } catch (_: Exception) {}
            root = null
        }
        // 2. 创建 40x40 圆点窗口
        val dot = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#1D9E75"))
            // 圆角
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1D9E75"))
                cornerRadius = dp(20).toFloat()
            }
        }
        val dotText = TextView(this).apply {
            text = "+"
            setTextColor(Color.WHITE)
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        dot.addView(dotText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        val dotP = WindowManager.LayoutParams(
            dp(40), dp(40),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(40)
            y = dp(200)
        }
        dot.setOnClickListener { restoreFromDot() }  // 点击恢复
        try {
            windowManager.addView(dot, dotP)
        } catch (e: Exception) {
            Log.e("FloatWindow", "创建圆点失败", e)
            return
        }
        minimizedDot = dot
        minimizedDotParams = dotP
        isMinimized = true
        Log.d("FloatWindow", "最小化：显示 40x40 圆点")
    }

    /** 恢复：移除圆点 → 重建完整浮窗 */
    private fun restoreFromDot() {
        if (!isMinimized) return
        // 1. 移除圆点
        minimizedDot?.let { d ->
            try { windowManager.removeView(d) } catch (_: Exception) {}
        }
        minimizedDot = null
        // 2. 重建完整浮窗（沿用原 params 位置）
        val p = params ?: run { isMinimized = false; return }
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
