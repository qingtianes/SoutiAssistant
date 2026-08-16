package com.dingding.souti.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dingding.souti.repository.QuestionBank

interface SearchUiCallbacks {
    fun switchToStandby()
    fun stopEverything()
}

object SearchUiBuilder {
    fun build(
        context: Context,
        root: ViewGroup,
        bank: QuestionBank,
        dp: (Int) -> Int,
        callbacks: SearchUiCallbacks
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        container.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )

        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backBtn = TextView(context).apply {
            text = "← 返回"
            setTextColor(Color.parseColor("#1D9E75"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), dp(4), dp(8), dp(4))
            setOnClickListener { callbacks.switchToStandby() }
        }
        val title = TextView(context).apply {
            text = "搜题"
            setTextColor(Color.parseColor("#1D9E75"))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn2 = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#E24B4A"))
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { callbacks.stopEverything() }
        }
        topBar.addView(backBtn)
        topBar.addView(title)
        topBar.addView(closeBtn2)
        container.addView(topBar)

        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            layoutParams = lp
        }
        val input = EditText(context).apply {
            hint = "输入题干关键词"
            setHintTextColor(Color.parseColor("#888888"))
            setTextColor(Color.BLACK)
            textSize = 13f
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
        }
        val searchGo = Button(context).apply {
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

        val activeHint = TextView(context).apply {
            val count = bank.getActiveBankIds().size
            text = if (count == 0) "⚠ 未勾选任何题库，去题库总览勾选" else "✓ 已勾选 $count 个题库"
            textSize = 11f
            setTextColor(if (count == 0) Color.parseColor("#E24B4A") else Color.parseColor("#1D9E75"))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(4)
            layoutParams = lp
        }
        container.addView(activeHint)

        val resultScroll = ScrollView(context).apply {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            lp.topMargin = dp(6)
            layoutParams = lp
            setBackgroundColor(Color.TRANSPARENT)
        }
        val resultContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        resultScroll.addView(resultContainer)

        val placeholder = TextView(context).apply {
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
                resultContainer.addView(TextView(context).apply {
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
                    val card = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundColor(Color.parseColor("#F5F5F5"))
                        setPadding(dp(10), dp(8), dp(10), dp(8))
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.bottomMargin = dp(6)
                        layoutParams = lp
                    }
                    card.addView(TextView(context).apply {
                        text = sr.question.stem
                        textSize = 12f
                        setTextColor(Color.parseColor("#222222"))
                        maxLines = 3
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    sr.question.options.forEach { opt ->
                        card.addView(TextView(context).apply {
                            text = opt
                            textSize = 11f
                            setTextColor(Color.parseColor("#444444"))
                            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            lp.leftMargin = dp(4)
                            layoutParams = lp
                        })
                    }
                    if (sr.question.answer.isNotBlank()) {
                        card.addView(TextView(context).apply {
                            text = "答案：${sr.question.answer}"
                            textSize = 13f
                            setTypeface(typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor("#1D9E75"))
                            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            lp.topMargin = dp(4)
                            layoutParams = lp
                        })
                    }
                    card.addView(TextView(context).apply {
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
}
