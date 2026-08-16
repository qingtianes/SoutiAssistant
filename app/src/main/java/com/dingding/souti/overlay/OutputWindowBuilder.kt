package com.dingding.souti.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

data class OutputWindowViews(
    val rootView: ViewGroup,
    val titleBar: TextView,
    val scrollView: ScrollView,
    val resultContainer: LinearLayout,
    val titleBarHeightPx: Int
)

/**
 * 独立输出悬浮窗的内容构建器。
 * 结构：标题栏（常驻，用于拖动整窗） + ScrollView 内容区（只负责上下滚动）。
 * 不接触 WindowManager 生命周期。
 */
object OutputWindowBuilder {

    fun build(context: Context, dp: (Int) -> Int): OutputWindowViews {
        val titleBarHeightPx = dp(22)

        val resultsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.parseColor("#22000000"))
            clipChildren = true
            addView(TextView(context).apply {
                text = "暂无输出"
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            })
        }

        val contentScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            clipChildren = true
            isFillViewport = false
            addView(
                resultsContainer,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val titleBar = TextView(context).apply {
            text = "搜题结果"
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#CC1D9E75"))
            includeFontPadding = false
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = true
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                titleBar,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    titleBarHeightPx
                )
            )
            addView(
                contentScroll,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }

        return OutputWindowViews(root, titleBar, contentScroll, resultsContainer, titleBarHeightPx)
    }
}
