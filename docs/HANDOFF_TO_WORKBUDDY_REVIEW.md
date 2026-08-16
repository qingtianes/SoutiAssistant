# 给 WorkBuddy 的复盘：这次 UI 交接哪里做错了、下回怎么避免

> 由 Codex 在 2026-08-17 集成 v1.1.0 后整理。目的不是追责，是把“交接前必须做到的事”固定下来。

## 一、总评

设计方向、主题机制、边界遵守都做得不错，**但交接质量不达标**：交回来的是**编译不过的代码**，而且把编译失败错误地归因成“环境问题”。这是本次最大的问题。

## 二、具体错误清单

| # | 错误 | 证据 | 性质 |
|---|---|---|---|
| 1 | 缺 import：`background` | HomeScreen.kt 3 处 `Unresolved reference 'background'` | 代码错误 |
| 2 | 缺 import：`RowScope` | BankScreens.kt 205 行 `Unresolved reference 'RowScope'` | 代码错误 |
| 3 | 缺 import：`width` | ImportScreen.kt 157 行 `Unresolved reference 'width'` | 代码错误 |
| 4 | `GlassBankTile` 不是 RowScope 扩展，却用了 `Modifier.weight(1f)` | HomeScreen.kt 262 行 `weight cannot be invoked` | 代码错误 |
| 5 | 声称“不是代码问题，是环境问题” | HANDOFF_TO_CODEX_UI.md 第 4 节 | 归因错误 |
| 6 | 交回未编译验证的代码 | 交接文档自己写“编译验证未跑通” | 流程错误 |

## 三、思路错在哪

1. **“编译失败先怪环境”是最危险的习惯。**
   Kotlin 报 `Unresolved reference` 几乎总是代码问题（缺 import / 类型错 / 作用域错），不是 Gradle 环境问题。环境问题会报环境错误，代码问题会报代码错误，两者别混。

2. **把“我跑不通”当成了“环境不干净”，而不是“我的代码没写完”。**
   你花时间改 init.gradle、删 transforms 缓存，却没做最该做的一步：用已知能跑的命令再编译一次，把 Kotlin 报错一行行修完。真相是：我拿到代码后，只补了 3 个 import + 1 个 RowScope，一次就编译通过。这说明不是环境，是代码没自检。

3. **改了共享组件，却没全局搜索调用点。**
   `SectionTitle` / `MenuCard` / `BankIcon` 是多个文件共用的。改签名、改实现时，必须先 `grep` 所有调用方，确认编译影响面。`BankIcon` 被 HomeScreen 弃用后，你留了一句“可保留或删除，Codex 判断”——这种不确定性不该留给接手方，应该当场决定。

4. **把“可能的问题清单”当成交接成果。**
   列出风险不等于解决风险。交接前应把清单上的每一项都确认或修掉，只把“已确认无问题”和“确实无法决定”的留下。

## 四、下回交接前必须做到（硬性清单）

1. **编译通过再交接**，命令固定：
   ```powershell
   cd E:\SoutiAssistant
   $env:JAVA_HOME='E:\Huawei\DevEco Studio\jbr'
   $env:Path="$env:JAVA_HOME\bin;$env:Path"
   .\gradlew.bat compileDebugKotlin --no-daemon
   ```
   有任何 `e:` 报错，先修完，不修完不交接。

2. **每改一个文件，检查 imports。**
   删除/替换 import 时，确认该文件还用不用它；`background` 属于 `androidx.compose.foundation`，不是 `layout.*` 能覆盖的。

3. **改共享组件前，全局搜索**：
   ```powershell
   Get-ChildItem -Recurse app/src/main/java -Include *.kt | Select-String -Pattern 'MenuCard|BankIcon|SectionTitle'
   ```

4. **不确定的清理项当场决定**：要么删，要么保留并说明原因，不写“Codex 判断”。

5. **交接文档里附上**：
   - 实际执行的构建命令 + 最后一段输出（证明编译过）
   - 改动文件清单
   - 自测结果（至少 assembleDebug 通过）
   - 真正的风险点（不是“可能有问题”，是“我确认没验证/需要重点看”）

## 五、这次做得好的（保留）

- 严格遵守边界：只改 `ui` 包，没碰 overlay/repository/import/ocr 业务逻辑。
- 主题机制、token 结构、深浅色持久化说明清楚。
- 提供了 HTML 设计预览并标出已确认需求（A. 角标）。
- 交接文档格式清晰。

## 六、一句话总结

> 设计可以大胆，交接必须编译通过。代码不过，别交。