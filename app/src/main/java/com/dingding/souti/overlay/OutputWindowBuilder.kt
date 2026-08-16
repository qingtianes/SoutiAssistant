package com.dingding.souti.overlay

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

data class OutputWindowViews(
    val rootView: ScrollView,
    val resultContainer: LinearLayout
)

/**
 * 独立输出悬浮窗的内容构建器。
 * 只负责创建 ScrollView + LinearLayout 结果容器，不接触 WindowManager 生命周期。
 */
object OutputWindowBuilder {

    fun build(context: Context, dp: (Int) -> Int): OutputWindowViews {
        val resultsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.parseColor("#22000000"))
            clipChildren = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(TextView(context).apply {
                text = "（OCR 结果将在这里显示）"
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }

        val ocrResults = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            clipChildren = true
            visibility = View.GONE
            addView(resultsContainer)
        }

        return OutputWindowViews(ocrResults, resultsContainer)
    }
}