/**
 * 首页：对齐 Android HomeScreen 的信息架构。
 * 题库区 / 快捷搜题区 / 设置区；平台能力未就绪时展示真实状态而非伪装。
 */
import { common } from '@kit.AbilityKit';
import { router } from '@kit.ArkUI';
import preferences from '@ohos.data.preferences';
import { QuestionBank } from '../service/QuestionBank';

@Component
struct GlassCard {
  @Prop title: string = '';
  @Prop subtitle: string = '';
  @Prop statusText: string = '';
  @Prop statusDot: number = 1; // 0 green, 1 gray, 2 red
  @Prop showArrow: boolean = true;
  @Prop danger: boolean = false;
  onCardClick: () => void = () => {};

  build() {
    Column({ space: 6 }) {
      Row() {
        Text(this.title)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .fontColor(this.danger ? '#C83B3B' : '#222222')
        Blank()
        if (this.statusText.length > 0) {
          Circle()
            .width(8)
            .height(8)
            .fill(this.statusDot === 0 ? '#1D9E75' : (this.statusDot === 2 ? '#C83B3B' : '#BBBBBB'))
          Text(this.statusText)
            .fontSize(12)
            .fontColor('#888888')
            .margin({ left: 5 })
        } else if (this.showArrow) {
          Text('›')
            .fontSize(24)
            .fontColor('#BBBBBB')
        }
      }
      .width('100%')

      if (this.subtitle.length > 0) {
        Text(this.subtitle)
          .width('100%')
          .fontSize(12)
          .fontColor('#888888')
      }
    }
    .width('100%')
    .padding(14)
    .borderRadius(18)
    .backgroundColor(Color.White)
    .onClick(() => this.onCardClick())
  }
}

@Entry
@Component
struct HomePage {
  @State dark: boolean = false;
  @State bankSummary: string = '本地题库加载中…';
  private readonly questionBank: QuestionBank = new QuestionBank();

  aboutToAppear(): void {
    this.loadTheme();
    this.refreshBankSummary();
  }

  onPageShow(): void {
    this.refreshBankSummary();
  }

  private async loadTheme(): Promise<void> {
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const pref = await preferences.getPreferences(context, 'souti_settings');
      const value = await pref.get('theme_dark', false);
      this.dark = value as boolean;
    } catch (_) {
      this.dark = false;
    }
  }

  private async toggleTheme(): Promise<void> {
    this.dark = !this.dark;
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const pref = await preferences.getPreferences(context, 'souti_settings');
      await pref.put('theme_dark', this.dark);
      await pref.flush();
    } catch (_) {
    }
  }

  private async refreshBankSummary(): Promise<void> {
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const banks = await this.questionBank.loadBanks(context);
      const active = await this.questionBank.getActiveBankIds(context);
      this.bankSummary = `本地题库 ${banks.length} 个 · 已启用 ${active.length} 个`;
    } catch (_) {
      this.bankSummary = '';
    }
  }

  private go(url: string): void {
    router.pushUrl({ url: url });
  }

  build() {
    Column() {
      Scroll() {
        Column({ space: 12 }) {
          // 顶部
          Row() {
            Column({ space: 2 }) {
              Text('搜题助手')
                .fontSize(26)
                .fontWeight(FontWeight.Bold)
                .fontColor(this.dark ? '#FFFFFF' : '#222222')
              Text('浮窗 · 读屏 · 摄像头 · 本地题库')
                .fontSize(13)
                .fontColor('#888888')
            }
            .alignItems(HorizontalAlign.Start)
            .layoutWeight(1)

            Button(this.dark ? '☀' : '☾')
              .width(40)
              .height(40)
              .fontSize(18)
              .borderRadius(20)
              .backgroundColor('#FFFFFF')
              .fontColor('#333333')
              .onClick(() => this.toggleTheme())

            Text('ⓘ')
              .width(40)
              .height(40)
              .textAlign(TextAlign.Center)
              .fontSize(22)
              .borderRadius(20)
              .backgroundColor('#FFFFFF')
              .border({ width: 1, color: '#EEEEEE' })
              .onClick(() => this.go('ui/UsageGuidePage'))
          }
          .width('100%')

          // 题库区
          Text('题库')
            .width('100%')
            .fontSize(14)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.dark ? '#DDDDDD' : '#444444')
          Row({ space: 10 }) {
            Column({ space: 6 }) {
              Text('📁').fontSize(24)
              Text('智能导入').fontSize(13).fontWeight(FontWeight.Bold).fontColor('#222222')
            }
            .width('50%')
            .padding(14)
            .borderRadius(18)
            .backgroundColor(Color.White)
            .onClick(() => this.go('ui/ImportPage'))

            Column({ space: 6 }) {
              Text('📚').fontSize(24)
              Text('题库总览').fontSize(13).fontWeight(FontWeight.Bold).fontColor('#222222')
            }
            .width('50%')
            .padding(14)
            .borderRadius(18)
            .backgroundColor(Color.White)
            .onClick(() => this.go('ui/OverviewPage'))
          }
          .width('100%')

          // 快捷搜题区
          Text('快捷搜题')
            .width('100%')
            .fontSize(14)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.dark ? '#DDDDDD' : '#444444')
          GlassCard({
            title: '浮窗搜题',
            subtitle: '绿框实时识别 · 单题答案输出',
            statusText: this.bankSummary.length > 0 ? '平台能力待验证' : '',
            statusDot: 2,
            showArrow: false
          })
          GlassCard({
            title: '读屏搜题',
            subtitle: '全屏识别 · 多题答案输出',
            statusText: '待真机验证',
            statusDot: 1,
            showArrow: false
          })
          Button('扫描搜题（摄像头实时识别，后置开发）')
            .width('100%')
            .height(44)
            .enabled(false)
            .backgroundColor('#BDBDBD')
          GlassCard({
            title: 'AI 搜题',
            subtitle: '待开发',
            statusText: '',
            statusDot: 1,
            showArrow: false,
            danger: false
          })

          // 附加工具（Harmony 保留能力，非 Android 首页替代项）
          GlassCard({
            title: '图片与文字识别',
            subtitle: '选择题目图片 → 本机 OCR → 手动编辑 → 搜索本地题库',
            showArrow: true,
            onCardClick: () => this.go('ui/Index')
          })

          // 设置区
          Text('设置')
            .width('100%')
            .fontSize(14)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.dark ? '#DDDDDD' : '#444444')
          GlassCard({
            title: '设置',
            subtitle: '权限 / 识别匹配 / 浮窗显示 / 扫描 / 关于',
            showArrow: true,
            onCardClick: () => this.go('ui/SettingsPage')
          })
        }
        .width('100%')
        .padding({ left: 16, right: 16, top: 16, bottom: 40 })
      }
      .width('100%')
      .layoutWeight(1)
      .scrollBar(BarState.Auto)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.dark ? '#1A1A1A' : '#F7F7F7')
  }
}

