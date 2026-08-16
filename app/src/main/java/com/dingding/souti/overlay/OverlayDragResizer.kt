package com.dingding.souti.overlay

import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

object OverlayDragResizer {
    fun bindScreenRead(
        win: View,
        titleBar: View,
        resizeHandle: View,
        p: WindowManager.LayoutParams,
        windowManager: WindowManager,
        dp: (Int) -> Int
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
                        p.x = initX + dx
                        p.y = initY + dy
                        try { windowManager.updateViewLayout(win, p) } catch (_: Exception) {}
                    }
                }
                return true
            }
        })
        resizeHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0
            private var initY = 0
            private var initW = 0
            private var initH = 0
            private var startTX = 0f
            private var startTY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = p.x
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
                        try { windowManager.updateViewLayout(win, p) } catch (_: Exception) {}
                    }
                }
                return true
            }
        })
    }
}
