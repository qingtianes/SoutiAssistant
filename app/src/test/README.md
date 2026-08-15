# Android 纯逻辑测试基线

## 范围

本目录用于测试不依赖 Android 真机的核心逻辑：

- `Importer` 的题目边界、选项识别与分块规则；
- `Importer.parse()` 使用的文件格式识别规则；
- `Importer.ParseResult` 的覆盖率与低覆盖判断；
- `OcrBridge` 录屏授权请求的生成、取消和单次消费规则；
- `Bank`、`Question`、`SearchResult` 的格式化和数据保持行为；
- 遗留的 `QuestionBank.parseTxt()` 解析行为。

## 运行方式

1. 让 `JAVA_HOME` 指向 JDK 21。
2. 如需节省系统盘空间，可让 `GRADLE_USER_HOME` 指向任意非系统盘目录。
3. 在项目根目录运行：

       .\gradlew.bat --no-daemon :app:testDebugUnitTest

Linux 或 macOS 环境可运行：

       ./gradlew --no-daemon :app:testDebugUnitTest

项目使用正式的 JUnit 4.13.2 测试依赖，不需要测试目录内的兼容类或临时初始化脚本。

## 重要说明

- `LegacyQuestionBankParseTxtTest` 只保护尚未移除的遗留解析器，不代表真实导入链路。
- 当前真实文件导入入口是 `Importer.parse()`。
- `.xls` 可以进入现有表格解析流程；`.xlsx` 目前尚未支持，会明确提示先另存为 `.xls`。
- 这些测试首先用于锁定现有行为，不表示所有旧行为都已经完成优化。
