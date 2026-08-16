package com.dingding.souti.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

interface ScreenReadWindowCallbacks {
    fun isActive(): Boolean
    fun togglePause(): Boolean
    fun minimize()
    fun close()
    fun bindDrag(win: View, titleBar: View, resizeHandle: View, p: WindowManager.LayoutParams)
}

data class ScreenReadWindowViews(
    val window: View,
    val params: WindowManager.LayoutParams,
    val container: LinearLayout,
    val statusText: TextView,
    val ocrPreview: TextView,
    val modeText: TextView
)

object ScreenReadWindowBuilder {
    fun build(
        context: Context,
        displayWidth: Int,
        displayHeight: Int,
        dp: (Int) -> Int,
        callbacks: ScreenReadWindowCallbacks
    ): ScreenReadWindowViews {
        val win = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#44000000"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }

        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleBar.addView(TextView(context).apply {
            text = "读屏搜题"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        val modeText = TextView(context).apply {
            text = ""
            textSize = 10f
            setTextColor(Color.parseColor("#9FE1CB"))
            setPadding(dp(6), dp(0), dp(4), dp(0))
        }
        titleBar.addView(modeText)
        titleBar.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
        val pauseBtn = TextView(context).apply {
            text = "⏸"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener {
                val active = callbacks.togglePause()
                text = if (active) "⏸" else "▶"
            }
        }
        titleBar.addView(pauseBtn)
        val minBtn = TextView(context).apply {
            text = "—"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener { callbacks.minimize() }
        }
        titleBar.addView(minBtn)
        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 12f
            setTextColor(Color.parseColor("#E24B4A"))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener { callbacks.close() }
        }
        titleBar.addView(closeBtn)
        win.addView(titleBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)))

        val statusText = TextView(context).apply {
            text = "🔄 等待首次识别..."
            textSize = 11f
            setTextColor(Color.parseColor("#FAC775"))
            setPadding(dp(4), dp(2), dp(4), dp(0))
        }
        val ocrPreview = TextView(context).apply {
            text = ""
            textSize = 9f
            setTextColor(Color.parseColor("#CCCCCC"))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(4), dp(0), dp(4), dp(4))
        }
        val statusBar = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        statusBar.addView(statusText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        statusBar.addView(ocrPreview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        win.addView(statusBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        container.addView(TextView(context).apply {
            text = "（匹配答案将在这里显示）"
            textSize = 11f
            setTextColor(Color.parseColor("#CCCCCC"))
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), dp(4), dp(10))
        })
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            addView(container)
        }
        win.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val resizeHandle = TextView(context).apply {
            text = "◢"
            textSize = 10f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
        }
        val handleLp = LinearLayout.LayoutParams(dp(24), dp(18))
        handleLp.gravity = Gravity.END
        win.addView(resizeHandle, handleLp)

        val p = WindowManager.LayoutParams(
            dp(260), dp(360),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (displayWidth - dp(260) - dp(8)).coerceAtLeast(0)
            y = (displayHeight - dp(360) - dp(100)).coerceAtLeast(0)
        }

        callbacks.bindDrag(win, titleBar, resizeHandle, p)
        return ScreenReadWindowViews(win, p, container, statusText, ocrPreview, modeText)
    }
}
