/**
 * 搜题助手 - 主控制台（AGC 申请通过后用）
 * 功能：控制悬浮窗 / 摄像头识别模式
 */
import { FloatWindowManager } from '../common/FloatWindowManager';
import { common } from '@kit.AbilityKit';
import promptAction from '@ohos.promptAction';

@Entry
@Component
struct Index {
  @State floatOn: boolean = false;
  @State mode: string = '悬浮窗'; // 当前模式提示

  aboutToAppear() {
    this.floatOn = FloatWindowManager.isShowing();
  }

  async showFloat() {
    const ctx = getContext(this) as common.UIAbilityContext;
    const ok = await FloatWindowManager.createFloatWindow(ctx);
    if (ok) {
      this.floatOn = true;
      promptAction.showToast({ message: '悬浮窗已开启，切到答题界面' });
    } else {
      promptAction.showToast({ message: '开启失败：检查 SYSTEM_FLOAT_WINDOW 权限' });
    }
  }

  async hideFloat() {
    await FloatWindowManager.destroyFloatWindow();
    this.floatOn = false;
    promptAction.showToast({ message: '悬浮窗已关闭' });
  }

  build() {
    Column() {
      // 顶部栏
      Row() {
        Text('搜题助手')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor(Color.White)
      }
      .width('100%')
      .height(56)
      .justifyContent(FlexAlign.Center)
      .backgroundColor('#1D9E75')

      // 状态
      Row() {
        Circle()
          .width(12)
          .height(12)
          .fill(this.floatOn ? '#1D9E75' : '#CCCCCC')
        Text(this.floatOn ? '悬浮窗运行中' : '悬浮窗未开启')
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor('#222222')
          .margin({ left: 10 })
      }
      .width('90%')
      .padding(16)
      .margin({ top: 16 })
      .borderRadius(12)
      .backgroundColor('#F1F1F1')

      // 说明
      Column() {
        Text('使用方式')
          .fontSize(15)
          .fontWeight(FontWeight.Bold)
          .fontColor('#222222')
          .alignSelf(ItemAlign.Start)
        Text('1. AGC 申请 SYSTEM_FLOAT_WINDOW + trial debug profile\n2. DevEco 自动签名后即可安装\n3. 启动悬浮窗，切到答题页面，自动识别题目')
          .fontSize(13)
          .fontColor('#666666')
          .lineHeight(22)
          .margin({ top: 8 })
          .alignSelf(ItemAlign.Start)
      }
      .width('90%')
      .padding(16)
      .margin({ top: 12 })
      .borderRadius(12)
      .backgroundColor('#FFFFFF')
      .alignItems(HorizontalAlign.Start)

      Blank()

      // 控制按钮
      Column({ space: 12 }) {
        Button(this.floatOn ? '关闭悬浮窗' : '启动悬浮窗')
          .width('80%')
          .height(48)
          .fontSize(16)
          .backgroundColor(this.floatOn ? '#E24B4A' : '#1D9E75')
          .onClick(() => {
            if (this.floatOn) {
              this.hideFloat();
            } else {
              this.showFloat();
            }
          })
        Text('AGC 申请通过、DevEco 自动签名后即可用')
          .fontSize(11)
          .fontColor('#999999')
      }
      .width('100%')
      .margin({ bottom: 48 })
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F7F7F7')
  }
}
