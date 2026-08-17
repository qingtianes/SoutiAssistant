/**
 * 设置中心（对齐 Android SettingsScreen 五大分类骨架）。
 * 已实现项可用；平台权限相关项在能力落地前显示真实状态。
 */
import { common } from '@kit.AbilityKit';
import { router } from '@kit.ArkUI';
import { BusinessError } from '@kit.BasicServicesKit';
import promptAction from '@ohos.promptAction';
import { QuestionBank } from '../service/QuestionBank';

@Entry
@Component
struct SettingsPage {
  @State version: string = '0.1.0-harmony（完整复刻中）';
  @State busy: boolean = false;
  private readonly questionBank: QuestionBank = new QuestionBank();

  private goGuide(): void {
    router.pushUrl({ url: 'ui/UsageGuidePage' });
  }

  private showPrivacy(): void {
    AlertDialog.show({
      title: '隐私说明',
      message: 'OCR 与题库数据仅在本机处理，不上传；相册图片仅读取；摄像头扫描已冻结并后置于完整复刻阶段。',
      confirm: { value: '知道了', action: () => {} }
    });
  }

  private confirmClear(): void {
    AlertDialog.show({
      title: '确认清空？',
      message: '将删除全部已导入题库并恢复默认设置。此操作不可恢复。',
      primaryButton: { value: '清空', fontColor: '#C83B3B', action: () => this.clearAll() },
      secondaryButton: { value: '取消', action: () => {} }
    });
  }

  private async clearAll(): Promise<void> {
    if (this.busy) {
      return;
    }
    this.busy = true;
    try {
      const context = getContext(this) as common.UIAbilityContext;
      await this.questionBank.clear(context);
      promptAction.showToast({ message: '已清空本地题库与设置' });
    } catch (err) {
      const e = err as BusinessError;
      promptAction.showToast({ message: `清空失败：${e.message ?? ''}` });
    } finally {
      this.busy = false;
    }
  }

  @Builder
  private section(title: string) {
    Text(title)
      .width('100%')
      .fontSize(14)
      .fontWeight(FontWeight.Bold)
      .fontColor('#444444')
      .margin({ top: 8, bottom: 4 })
  }

  @Builder
  private item(name: string, state: string, action?: () => void, danger: boolean = false) {
    Row() {
      Text(name)
        .layoutWeight(1)
        .fontSize(15)
        .fontColor(danger ? '#C83B3B' : '#222222')
      Text(state)
        .fontSize(12)
        .fontColor(state === '已生效' ? '#1D9E75' : '#AAAAAA')
    }
    .width('100%')
    .padding(14)
    .borderRadius(12)
    .backgroundColor(Color.White)
    .onClick(action !== undefined ? action : () => {})
  }

  build() {
    Column() {
      Row() {
        Text('设置')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor(Color.White)
        Blank()
        Button('返回')
          .height(32)
          .fontSize(13)
          .backgroundColor('#1D9E75')
          .onClick(() => router.back())
      }
      .width('100%')
      .height(56)
      .padding({ left: 16, right: 16 })
      .backgroundColor('#1D9E75')

      Scroll() {
        Column({ space: 10 }) {
          this.section('权限管理')
          this.item('悬浮窗权限', '普通应用不可用', undefined, false)
          this.item('摄像头权限', '后置开发', undefined, false)
          this.item('通知权限', '待实现', undefined, false)
          this.item('录屏权限', '待真机验证', undefined, false)

          this.section('识别与匹配')
          this.item('最多显示结果', '待实现', undefined, false)
          this.item('最低匹配分', '待实现', undefined, false)
          this.item('识别速度', '待实现', undefined, false)

          this.section('浮窗显示')
          this.item('结果字号', '待实现', undefined, false)
          this.item('显示来源与相关度', '待实现', undefined, false)
          this.item('绿框默认大小', '待实现', undefined, false)

          this.section('扫描搜题')
          this.item('默认缩放', '后置开发', undefined, false)
          this.item('取景框高度', '后置开发', undefined, false)

          this.section('通用 / 关于')
          this.item('当前版本', this.version, undefined, false)
          this.item('使用说明', '可进入', () => this.goGuide(), false)
          this.item('隐私说明', '可查看', () => this.showPrivacy(), false)
          this.item('GitHub 意见反馈', '待配置', undefined, false)
          this.item('清空本地题库与设置', '危险操作', () => this.confirmClear(), true)
        }
        .width('92%')
        .padding({ top: 12, bottom: 30 })
      }
      .width('100%')
      .layoutWeight(1)
      .scrollBar(BarState.Auto)
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F7F7F7')
  }
}
