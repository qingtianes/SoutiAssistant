/**
 * 悬浮窗内容页：显示在系统悬浮窗里
 * 功能（MVP）：整体可拖动、显示识别区域框、状态栏
 */
import { FloatWindowManager } from './FloatWindowManager';

@Entry
@Component
struct FloatPage {
  // 拖动状态
  private dragStartX: number = 0;
  private dragStartY: number = 0;
  private winStartX: number = 0;
  private winStartY: number = 0;
  // 识别状态（MVP 先固定文字，后续接 OCR 后实时更新）
  @State statusText: string = '待机中';

  /** 关闭悬浮窗 */
  closeFloat() {
    FloatWindowManager.destroyFloatWindow();
  }

  build() {
    Column() {
      // ===== 顶部标题栏 =====
      Row() {
        Text('搜题助手')
          .fontSize(14)
          .fontWeight(FontWeight.Bold)
          .fontColor(Color.White)
        Blank()
        Text('✕')
          .fontSize(14)
          .fontColor(Color.White)
          .padding({ left: 8, right: 8 })
          .onClick(() => {
            this.closeFloat();
          })
      }
      .width('100%')
      .height(32)
      .padding({ left: 12, right: 6 })
      .backgroundColor('#1D9E75')

      // ===== 题目识别区域（虚线框） =====
      Column() {
        Text('题目识别区域')
          .fontSize(12)
          .fontColor('#888888')
      }
      .width('88%')
      .layoutWeight(1)
      .justifyContent(FlexAlign.Center)
      .border({
        width: 1,
        color: '#1D9E75',
        style: BorderStyle.Dashed
      })
      .borderRadius(8)
      .margin({ top: 8, bottom: 8 })

      // ===== 状态栏 =====
      Row() {
        Circle()
          .width(8)
          .height(8)
          .fill('#1D9E75')
        Text(this.statusText)
          .fontSize(12)
          .fontColor('#666666')
          .margin({ left: 6 })
        Blank()
        Text('可拖动')
          .fontSize(10)
          .fontColor('#BBBBBB')
      }
      .width('88%')
      .margin({ bottom: 8 })
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Color.White)
    .borderRadius(12)
    .clip(true)
    // 整个窗口可拖动：按下记录起点，移动时更新窗口位置
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.dragStartX = event.touches[0].windowX;
        this.dragStartY = event.touches[0].windowY;
        const pos = FloatWindowManager.getPosition();
        this.winStartX = pos.x;
        this.winStartY = pos.y;
      } else if (event.type === TouchType.Move && event.touches.length > 0) {
        const dx = event.touches[0].windowX - this.dragStartX;
        const dy = event.touches[0].windowY - this.dragStartY;
        FloatWindowManager.moveTo(this.winStartX + dx, this.winStartY + dy);
      }
    })
  }
}
