package com.dingding.souti.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UsageGuideScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "返回")
            }
            Text("使用说明", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Green)
        }
        Spacer(Modifier.height(12.dp))

        GuideCard("1. 题库导入", listOf(
            "首页“题库”→“智能导入”，选择 txt / docx / pdf / xls 文件。",
            "导入后进入“题库总览”，勾选要参与搜题的题库。",
        ))
        GuideCard("2. 浮窗搜题", listOf(
            "首页点“浮窗搜题”，按提示授权悬浮窗和录屏。",
            "屏幕出现绿色识别框，把框拖动/缩放到题目上。",
            "识别到题目后，答案显示在独立输出窗。",
            "输出窗标题栏可拖动位置，内容区可上下滑动。",
            "标题栏“—”可最小化浮窗。"
        ))
        GuideCard("3. 读屏搜题", listOf(
            "首页点“读屏搜题”，按提示授权悬浮窗和录屏。",
            "自动全屏识别题目，答案按顺序显示在右上角小窗。",
            "小窗可拖动/缩放，右上角 ✕ 关闭。"
        ))
        GuideCard("4. 扫描搜题", listOf(
            "首页点“扫描搜题”，按提示授权摄像头。",
            "把题目对准绿色取景框，只识别框内内容。",
            "双指捏合或右下角 +/− 按钮可缩放画面。",
            "下方显示答案；点“暂停”锁定结果，点“继续”恢复扫描。"
        ))
        GuideCard("5. 设置", listOf(
            "首页“设置”可管理权限、识别与匹配、浮窗显示、扫描搜题等。"
        ))
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun GuideCard(title: String, steps: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222222))
            Spacer(Modifier.height(8.dp))
            steps.forEachIndexed { i, step ->
                Text(
                    text = "${i + 1}. $step",
                    fontSize = 13.sp,
                    color = Color(0xFF444444),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
