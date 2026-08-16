package com.dingding.souti.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dingding.souti.ui.theme.LocalGlass
import com.dingding.souti.ui.theme.SoutiThemeMode

/**
 * 玻璃拟态背景：品牌渐变 + 两团柔光光斑（对应图标深蓝黑玻璃 + 青绿发光）。
 */
@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    val glass = LocalGlass.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(glass.bgTop, glass.bgMid, glass.bgBottom)))
    ) {
        Box(
            Modifier
                .size(240.dp)
                .offset(x = (-60).dp, y = (-70).dp)
                .background(Brush.radialGradient(listOf(glass.glowA, Color.Transparent)), CircleShape)
        )
        Box(
            Modifier
                .size(220.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 50.dp)
                .background(Brush.radialGradient(listOf(glass.glowB, Color.Transparent)), CircleShape)
        )
        content()
    }
}

/**
 * 玻璃拟态卡片：半透明 + 顶部高光边 + 细边框（模拟毛玻璃，Compose 不支持 backdrop blur）。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = LocalGlass.current
    val base = modifier
        .clip(shape)
        .background(
            Brush.verticalGradient(
                0f to glass.surfaceHighlight,
                0.12f to glass.surface,
                1f to glass.surface
            )
        )
        .border(1.dp, glass.surfaceBorder, shape)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(modifier = clickable.padding(14.dp), content = content)
}

/**
 * 日/月主题切换按钮（浅色 ↔ 深色）。isDark 决定当前显示太阳还是月亮。
 */
@Composable
fun ThemeToggle(isDark: Boolean, onToggle: () -> Unit) {
    val glass = LocalGlass.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(glass.surface)
            .border(1.dp, glass.surfaceBorder, CircleShape)
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isDark) "🌙" else "☀️",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 玻璃拟态的分组标题（浅色小字 + 字距）。 */
@Composable
fun GlassSectionTitle(text: String) {
    val glass = LocalGlass.current
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = glass.textSecondary,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
    )
}

/** 玻璃拟态选项 chip（替代 Material FilterChip）。 */
@Composable
fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) glass.primary else glass.surface)
            .border(1.dp, if (selected) Color.Transparent else glass.surfaceBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) glass.onPrimary else glass.textPrimary
        )
    }
}

/** 玻璃拟态开关（替代 Material Switch）。 */
@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val glass = LocalGlass.current
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) glass.primary else glass.textSecondary.copy(alpha = 0.3f))
            .clickable { onChange(!checked) }
    ) {
        Box(
            Modifier
                .size(20.dp)
                .offset(x = if (checked) 21.dp else 3.dp, y = 3.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
