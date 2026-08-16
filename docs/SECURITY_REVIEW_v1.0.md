# 搜题助手 v1.0 安全 / 隐私审查

## 1. 权限最小化
- CAMERA：仅扫描搜题使用，使用前动态申请。✅
- SYSTEM_ALERT_WINDOW：浮窗/读屏必需。✅
- FOREGROUND_SERVICE + MEDIA_PROJECTION：录屏必需，前台服务声明正确。✅
- POST_NOTIFICATIONS：通知权限，设置页可跳系统通知设置。✅
- 无 INTERNET 权限：应用不联网，隐私声明“不上传”成立。✅

## 2. 数据本地化
- 题库与设置均存于 SharedPreferences（应用私有目录）。
- OCR、截屏、摄像头帧仅在内存处理，不写共享存储。
- `android:allowBackup="false"`：禁止系统把题库/设置备份到云端，确保“本地”语义。✅（本轮已改）

## 3. 敏感数据
- 无日志输出题目正文/答案到 Logcat 的明文敏感信息（现有日志仅调试状态）。
- 设置项不涉及个人身份信息。

## 4. 输入与文件
- FileProvider 仅用于分享题库，exported=false + grantUriPermissions 临时授权。✅
- 导入文件解析为本地数据，不执行外部代码。

## 5. 已知风险（可接受/记录）
- 鸿蒙卓易通录屏授权偶发不启动：已记录 KNOWN_ISSUES，不阻塞 1.0。
- 应用为本地离线工具，未做网络传输加密需求（无网络能力）。

## 结论
1.0 范围内未发现高危安全问题；关闭系统备份后与隐私说明一致。可进入用户验收。