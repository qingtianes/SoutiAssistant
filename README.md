# 搜题助手 · 鸿蒙版（Souti Assistant for HarmonyOS）

> 鸿蒙原生 ArkTS 版：用户主动选择图片 → 官方 OCR → 本地题库搜索答案。

## 当前版本

- **阶段**：本地题库闭环完成，未真机验证
- **当前分支**：`main`（接管工作已合并）

## 已完成

| 模块 | 说明 |
|---|---|
| 图片 OCR | PhotoViewPicker 选图 → Core Vision textRecognition |
| 题库管理 | TXT 导入、启用/停用、删除 |
| 智能匹配 | LCS 最长公共子串 + 完全包含打分 |
| 本地存储 | 应用私有 JSON，关闭系统备份 |

## 项目结构

```text
entry/src/main/ets/
├── model/       QuestionBankModels
├── repository/  QuestionRepository
├── service/     QuestionBank、QuestionMatcher
├── import/      TextBankImportService
├── ocr/         OcrService
├── picker/      ImagePickerService
├── overlay/     FloatWindowManager、FloatPage
├── camera/      CameraHelper（未接入）
└── ui/          Index
```

## 构建

- 构建前设置 `NODE_HOME`、`JAVA_HOME`、`DEVECO_SDK_HOME` 到 DevEco 目录
- 命令：`hvigorw.bat assembleHap --mode module -p product=default -p module=entry@default -p buildMode=debug --no-daemon`
- 当前：构建成功，生成未签名 HAP

## 待办

- 真机验证中文 OCR、相册 URI 读取
- DevEco 自动签名并安装
- 按安卓路线继续对齐读屏/扫描搜题

## 版本历史

- 接管前：工程骨架 + 悬浮窗 Demo + 题库模型
- 接管后：图片 OCR 搜索闭环、TXT 题库导入、模块边界对齐安卓
