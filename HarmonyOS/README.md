# 搜题助手 · 鸿蒙版（Souti Assistant for HarmonyOS）

> 目标：以 `E:\SoutiAssistant` Android v1.0.2 为唯一产品标准，完整复刻结构、功能、交互、状态和 UI。当前处于 H0 复刻基线恢复阶段，尚非完整鸿蒙版，也不是正式发布版。

## 当前阶段：H0 基线恢复

鸿蒙旧代码曾经只实现了图片 OCR、TXT 题库和一个相机/静态悬浮窗原型。它们是历史部分工作，不代表最终产品范围。

权威目标和缺口以以下文件为准：

```text
E:\SoutiAssistant\HarmonyOS\docs\PARITY_MATRIX.md
E:\SoutiAssistant\HarmonyOS\docs\DECISIONS.md
E:\SoutiAssistant\HarmonyOS\docs\TASKS.md
E:\SoutiAssistant\HarmonyOS\docs\SESSION_HANDOFF.md
```

## Android 基准必须完整复刻的模块

- 首页和完整导航；
- 智能导入：TXT、DOCX、PDF、XLS（Android 明确不支持 XLSX）；
- 独立题库总览和题库详情；
- 图片 OCR；
- 浮窗搜题：绿框、屏幕采集、OCR、独立输出窗、暂停/拖动/滚动；
- 读屏搜题：全屏多题 OCR、分题、去重、顺序输出；
- 摄像头扫描搜题：最后开发；
- 完整设置中心、主题、使用说明和隐私状态。

## 当前已验证的历史能力

- TXT 系统文件选择器导入、解析、自动启用；
- 题库停用、重新启用、删除和重启持久化；
- 手动题干本地匹配和答案输出；
- 模拟器中相机 API 曾经启动，但 OCR 原生模块缺失；
- 普通模拟器拒绝系统悬浮窗权限。

这些证据不能替代完整复刻，也不能替代用户的真机最终验收。

## 当前安全冻结

摄像头扫描暂时不开发、不测试：

- 不自动申请摄像头权限；
- 不启动预览；
- 不自动拍照；
- 不调用电脑或模拟器摄像头。

之前的模拟器测试发现其摄像头映射到了宿主机摄像头；该事件已记录为隐私踩坑，只有用户明确授权后才恢复摄像头阶段。

## 构建

```powershell
$env:DEVECO_SDK_HOME='E:\Huawei\DevEco Studio\sdk'
$env:JAVA_TOOL_OPTIONS='-Xms128m -Xmx1536m'
$env:JAVA_HOME='E:\Huawei\DevEco Studio\jbr'
$env:PATH='E:\Huawei\DevEco Studio\jbr\bin;E:\Huawei\DevEco Studio\tools\node;' + $env:PATH
& 'E:\Huawei\DevEco Studio\tools\hvigor\bin\hvigorw.bat' --no-daemon assembleHap --mode module -p product=default
```

发布模式同样必须通过构建，但签名和最终发布要等用户真机验收后再决定。

## 规则

- Android v1.0.2 是唯一产品基线；
- 不用截图和坐标点击循环替代自动化验证；
- 每个重要节点更新 Markdown、README、CHANGELOG，构建/验证通过后 commit 并 push `main`；
- 最终真机验收由用户完成；
- 当前不 push，直到 H0 文档和矩阵审查完成。
