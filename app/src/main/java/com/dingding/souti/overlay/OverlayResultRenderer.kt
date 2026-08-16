package com.dingding.souti.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.widget.LinearLayout
import android.widget.TextView
import com.dingding.souti.model.SearchResult

/**
 * 结果卡片渲染器：把搜索结果画成统一的题目卡片。
 * 只负责 View 构建，不接触 Service 状态。
 */
object OverlayResultRenderer {

    private fun dp(context: Context, v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()

    fun buildScreenReadMultiCard(context: Context, title: String, sr: SearchResult): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6FFFFFF"))
                cornerRadius = dp(context, 8).toFloat()
            }
            setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 6) }
        }
        card.addView(TextView(context).apply {
            text = title
            textSize = 10f
            setTextColor(Color.parseColor("#1D9E75"))
            setTypeface(typeface, Typeface.BOLD)
        })
        card.addView(TextView(context).apply {
            text = sr.question.stem
            textSize = 10f
            setTextColor(Color.parseColor("#222222"))
            if (sr.question.options.isNotEmpty() || sr.question.answer.isNotEmpty()) {
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
            }
        })
        sr.question.options.forEach { opt ->
            card.addView(TextView(context).apply {
                text = opt
                textSize = 10f
                setTextColor(Color.parseColor("#555555"))
                setPadding(dp(context, 2), dp(context, 0), dp(context, 2), dp(context, 0))
            })
        }
        if (sr.question.answer.isNotBlank()) {
            card.addView(TextView(context).apply {
                text = "✔ ${sr.question.answer}"
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#1D9E75"))
            })
        }
        card.addView(TextView(context).apply {
            text = "来源：${sr.bankName} · 相关度 ${sr.score}"
            textSize = 9f
            setTextColor(Color.parseColor("#666666"))
        })
        return card
    }

    fun buildScreenReadSingleCard(context: Context, sr: SearchResult, idx: Int): LinearLayout {
        val isBest = idx == 0
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(if (isBest) Color.parseColor("#E8F5E9") else Color.parseColor("#F5F5F5"))
                cornerRadius = dp(context, 8).toFloat()
            }
            setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 6) }
        }
        card.addView(TextView(context).apply {
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
            card.addView(TextView(context).apply {
                text = opt
                textSize = 10f
                setTextColor(Color.parseColor("#555555"))
                setPadding(dp(context, 2), dp(context, 0), dp(context, 2), dp(context, 0))
            })
        }
        if (sr.question.answer.isNotBlank()) {
            card.addView(TextView(context).apply {
                text = "✔ ${sr.question.answer}"
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#1D9E75"))
            })
        }
        card.addView(TextView(context).apply {
            text = "来源：${sr.bankName} · 相关度 ${sr.score}"
            textSize = 9f
            setTextColor(Color.parseColor("#AAAAAA"))
        })
        return card
    }

    fun buildScanCard(context: Context, sr: SearchResult, idx: Int): LinearLayout {
        val isBest = idx == 0
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (isBest) Color.parseColor("#E8F5E9") else Color.parseColor("#F5F5F5"))
            setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 4) }
        }
        card.addView(TextView(context).apply {
            text = (if (isBest) "🎯 " else "${idx + 1}. ") + sr.question.stem
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#222222"))
            if (sr.question.options.isEmpty() && sr.question.answer.isEmpty()) {
                // 切块模式：整块原样显示
            } else {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
        })
        sr.question.options.forEach { opt ->
            card.addView(TextView(context).apply {
                text = opt
                textSize = 10f
                setTextColor(Color.parseColor("#555555"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = dp(context, 4)
                    topMargin = dp(context, 1)
                }
            })
        }
        if (sr.question.answer.isNotBlank()) {
            card.addView(TextView(context).apply {
                text = "✔ ${sr.question.answer}"
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#1D9E75"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(context, 4) }
            })
        }
        card.addView(TextView(context).apply {
            text = "来源：${sr.bankName} · 相关度 ${sr.score}"
            textSize = 10f
            setTextColor(Color.parseColor("#AAAAAA"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 2) }
        })
        return card
    }
}
