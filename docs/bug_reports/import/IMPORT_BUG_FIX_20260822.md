# Android 题库导入 BUG 修复记录

日期：2026-08-22
状态：代码修复完成，本地验证通过，未 push；等待设备导入验收。

## 样例与结果

- TXT：`2026_SBS聚合岗理论题库_428_59_66_435__2028_8_17.txt`
  - 文件头声明 432 题。
  - 修复前切出约 443 块，额外块来自简答题答案中的 `2、/3、`。
  - 修复后自动化验证为 432 块。
- XLS：`2026-SBS聚合岗理论题库(428-59+66=435)-2028.8.17.xls`
  - 有效题干 435 条。
  - 修复前前 40 字去重会跳过 3 道相似但不同的题。
  - 修复后自动化验证为 435 块。
- PDF：`SBS装置操作工技师理论知识20220615.pdf`
  - 代码增加 PDFBox Android 资源初始化和资源安全关闭。
  - 因 PDFBox 中文字体映射需要 Android 运行环境，最终中文显示仍需模拟器/真机确认。

## 修改点

1. `BankChunker`：可靠空行题块中，空行优先于数字题号。
2. `XlsBankParser`：删除前 40 字误去重；兼容分号、中文分号、Tab 和换行选项。
3. `PdfBankParser`：初始化 `PDFBoxResourceLoader`，捕获错误并提供明确反馈。
4. `QuestionChunkParser`：把题干、选项、答案拆到正确字段。
5. `ImportScreen`：不再把整块文本全部放进 `Question.stem`；异常不会让加载状态永久卡住。

## 验证命令

```text
gradlew.bat testDebugUnitTest --no-daemon
gradlew.bat lintDebug --no-daemon
gradlew.bat assembleDebug --no-daemon
```

以上三项均已通过。Debug 包位于 `app/build/outputs/apk/debug/app-debug.apk`。
