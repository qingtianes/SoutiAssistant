package com.dingding.souti.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

interface StandbyUiCallbacks {
    fun isScanning(): Boolean
    fun hasProjection(): Boolean
    fun switchToSearch()
    fun toggleOcr()
    fun minimize()
    fun bindResize(resizeHandle: View, recognizeArea: View)
}

data class StandbyUiViews(
    val rootView: View,
    val statusDot: View,
    val statusText: TextView,
    val topSwitch: TextView,
    val closeBtn: TextView,
    val redBorder: View,
    val statusView: TextView
)

object StandbyUiBuilder {
    private const val R_ID_RECOGNIZE = 0x7F010001

    fun build(
        context: Context,
        root: ViewGroup,
        dp: (Int) -> Int,
        callbacks: StandbyUiCallbacks,
        recognizeWidthPx: Int? = null,
        recognizeHeightPx: Int? = null
    ): StandbyUiViews {
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        val statusDot = View(context).apply {
            setBackgroundResource(android.R.drawable.presence_online)
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
        }
        val scanning = callbacks.isScanning()
        val statusText = TextView(context).apply {
            text = if (scanning) "扫描中" else "扫描停止"
            setTextColor(if (scanning) Color.parseColor("#1D9E75") else Color.parseColor("#E24B4A"))
            textSize = 11f
            setPadding(dp(2), 0, dp(4), 0)
        }
        topBar.addView(statusDot)
        topBar.addView(statusText)

        val manualBtn = TextView(context).apply {
            text = "🔍"
            setTextColor(Color.parseColor("#1D9E75"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener { callbacks.switchToSearch() }
        }
        manualBtn.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(4) }
        topBar.addView(manualBtn)
        topBar.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 0.6f)
        })

        val topBtn = TextView(context).apply {
            text = when {
                scanning -> "⏸暂停"
                !callbacks.hasProjection() -> "🔓授权并扫描"
                else -> "▶开始"
            }
            setTextColor(Color.parseColor(if (scanning) "#E24B4A" else "#1D9E75"))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(4), dp(2), dp(4), dp(2))
            setOnClickListener { callbacks.toggleOcr() }
        }
        topBar.addView(topBtn)

        val closeBtn = TextView(context).apply {
            text = "—"
            setTextColor(Color.parseColor("#E24B4A"))
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(4), dp(0), dp(4), dp(0))
            includeFontPadding = false
            setOnClickListener { callbacks.minimize() }
        }
        topBar.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        })
        topBar.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28))
        topBar.addView(closeBtn)

        val recognizeArea = FrameLayout(context).apply {
            id = R_ID_RECOGNIZE
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#11FFFFFF"))
                setStroke(dp(2), Color.parseColor("#1D9E75"))
                cornerRadius = dp(10).toFloat()
            }
        }
        recognizeArea.layoutParams = LinearLayout.LayoutParams(
            recognizeWidthPx ?: dp(352),
            recognizeHeightPx ?: dp(150)
        ).apply { leftMargin = dp(4) }

        val ocrStatusText = TextView(context).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#888888"))
            setPadding(dp(8), dp(2), dp(8), dp(2))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val resizeHandle = TextView(context).apply {
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
        ).apply { rightMargin = dp(2); bottomMargin = dp(2) }
        recognizeArea.addView(resizeHandle)
        callbacks.bindResize(resizeHandle, recognizeArea)

        val redBorder = View(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), Color.parseColor("#FF1744"))
                cornerRadius = dp(8).toFloat()
            }
        }
        redBorder.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        recognizeArea.addView(redBorder)
        redBorder.visibility = View.GONE

        // 主浮窗只保留：标题栏、绿框识别区、OCR 状态栏。
        outer.addView(topBar)
        outer.addView(recognizeArea)
        outer.addView(ocrStatusText)

        root.addView(outer)
        return StandbyUiViews(
            outer,
            statusDot,
            statusText,
            topBtn,
            closeBtn,
            redBorder,
            ocrStatusText
        )
    }
}
