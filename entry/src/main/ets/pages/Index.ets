/**
 * 搜题助手 - 图片识别与本地题库练习入口。
 *
 * 主路径由用户主动选择图片触发，不依赖悬浮窗或后台采集。
 */
import { common } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import promptAction from '@ohos.promptAction';
import { FloatWindowManager } from '../common/FloatWindowManager';
import { QuestionBank, SearchResult } from '../model/QuestionBank';
import { ImagePickerService } from '../picker/ImagePickerService';
import { OcrService } from '../ocr/OcrService';

@Entry
@Component
struct Index {
  @State selectedImageUri: string = '';
  @State ocrText: string = '';
  @State ocrStatus: string = '请选择一张包含文字的图片';
  @State bankStatus: string = '正在读取本地题库…';
  @State supportedLanguageText: string = '';
  @State isRecognizing: boolean = false;
  @State isSearching: boolean = false;
  @State searchResults: SearchResult[] = [];
  @State floatOn: boolean = false;

  private readonly questionBank: QuestionBank = new QuestionBank();

  aboutToAppear(): void {
    this.floatOn = FloatWindowManager.isShowing();
    this.refreshBankStatus();
  }

  private async refreshBankStatus(): Promise<void> {
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const banks = await this.questionBank.loadBanks(context);
      const activeIds = await this.questionBank.getActiveBankIds(context);
      this.bankStatus = `本地题库 ${banks.length} 个，已启用 ${activeIds.length} 个`;
    } catch (err) {
      this.bankStatus = `题库读取失败：${this.describeError(err)}`;
    }
  }

  private async chooseImage(): Promise<void> {
    try {
      const uri = await ImagePickerService.pickOneImage();
      if (uri.length === 0) {
        this.ocrStatus = '未选择图片';
        return;
      }
      this.selectedImageUri = uri;
      this.ocrText = '';
      this.searchResults = [];
      this.ocrStatus = '图片已选择，点击“开始识别”';
    } catch (err) {
      this.ocrStatus = `选择图片失败：${this.describeError(err)}`;
    }
  }

  private async recognizeSelectedImage(): Promise<void> {
    if (this.selectedImageUri.length === 0) {
      promptAction.showToast({ message: '请先选择图片' });
      return;
    }
    if (this.isRecognizing) {
      return;
    }

    this.isRecognizing = true;
    this.ocrStatus = '正在识别图片文字…';
    this.searchResults = [];
    try {
      const languages = await OcrService.getSupportedLanguages();
      this.supportedLanguageText = languages.length > 0 ?
        `设备 OCR 支持 ${languages.length} 种语言` : '设备未返回 OCR 语言列表';
      const text = await OcrService.recognizeImage(this.selectedImageUri);
      this.ocrText = text;
      if (text.length === 0) {
        this.ocrStatus = '识别完成，但图片中没有可用文字';
        return;
      }
      this.ocrStatus = `识别完成，共 ${text.length} 个字符`;
      await this.searchCurrentText();
    } catch (err) {
      this.ocrStatus = `OCR 失败：${this.describeError(err)}`;
    } finally {
      this.isRecognizing = false;
    }
  }

  private async searchCurrentText(): Promise<void> {
    const query = this.ocrText.trim();
    if (query.length === 0) {
      promptAction.showToast({ message: '请先识别或输入题目文字' });
      return;
    }
    if (this.isSearching) {
      return;
    }

    this.isSearching = true;
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const activeIds = await this.questionBank.getActiveBankIds(context);
      if (activeIds.length === 0) {
        this.searchResults = [];
        this.bankStatus = '尚未启用本地题库，OCR 文字已保留，可先核对和复制';
        return;
      }
      this.searchResults = await this.questionBank.search(context, query, 5);
      this.bankStatus = this.searchResults.length > 0 ?
        `找到 ${this.searchResults.length} 条本地题库匹配` : '本地题库中没有找到匹配题目';
    } catch (err) {
      this.searchResults = [];
      this.bankStatus = `题库搜索失败：${this.describeError(err)}`;
    } finally {
      this.isSearching = false;
    }
  }

  private clearImageAndResult(): void {
    this.selectedImageUri = '';
    this.ocrText = '';
    this.searchResults = [];
    this.supportedLanguageText = '';
    this.ocrStatus = '请选择一张包含文字的图片';
    this.refreshBankStatus();
  }

  private async showFloat(): Promise<void> {
    const context = getContext(this) as common.UIAbilityContext;
    const ok = await FloatWindowManager.createFloatWindow(context);
    if (ok) {
      this.floatOn = true;
      promptAction.showToast({ message: '悬浮窗已开启' });
    } else {
      promptAction.showToast({ message: '开启失败：当前设备或签名可能没有悬浮窗权限' });
    }
  }

  private async hideFloat(): Promise<void> {
    await FloatWindowManager.destroyFloatWindow();
    this.floatOn = false;
    promptAction.showToast({ message: '悬浮窗已关闭' });
  }

  private describeError(err: Object): string {
    const error = err as BusinessError;
    if (error.message !== undefined && error.message.length > 0) {
      return error.message;
    }
    return JSON.stringify(err);
  }

  @Builder
  private resultCard(result: SearchResult, index: number) {
    Column({ space: 6 }) {
      Row() {
        Text(`匹配 ${index + 1}`)
          .fontSize(12)
          .fontWeight(FontWeight.Bold)
          .fontColor('#1D9E75')
        Blank()
        Text(`${result.bankName} · ${result.score} 分`)
          .fontSize(11)
          .fontColor('#777777')
      }
      .width('100%')

      Text(result.question.stem)
        .width('100%')
        .fontSize(15)
        .fontColor('#222222')
        .lineHeight(22)

      ForEach(result.question.options, (option: string) => {
        Text(option)
          .width('100%')
          .fontSize(13)
          .fontColor('#555555')
      })

      if (result.question.answer.length > 0) {
        Text(`参考答案：${result.question.answer}`)
          .width('100%')
          .fontSize(14)
          .fontWeight(FontWeight.Bold)
          .fontColor('#1D9E75')
      }
    }
    .width('100%')
    .padding(14)
    .borderRadius(12)
    .backgroundColor(Color.White)
    .alignItems(HorizontalAlign.Start)
  }

  build() {
    Column() {
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

      Scroll() {
        Column({ space: 14 }) {
          Column({ space: 8 }) {
            Text('图片文字识别')
              .width('100%')
              .fontSize(18)
              .fontWeight(FontWeight.Bold)
              .fontColor('#222222')
            Text('主动选择题目图片，识别后可修改文字，再搜索本地题库。图片和 OCR 原文不会写入日志。')
              .width('100%')
              .fontSize(13)
              .fontColor('#666666')
              .lineHeight(20)
          }
          .width('92%')
          .padding(16)
          .borderRadius(12)
          .backgroundColor(Color.White)
          .alignItems(HorizontalAlign.Start)

          if (this.selectedImageUri.length > 0) {
            Image(this.selectedImageUri)
              .width('92%')
              .height(220)
              .objectFit(ImageFit.Contain)
              .borderRadius(12)
              .backgroundColor('#EEEEEE')
          }

          Row({ space: 10 }) {
            Button(this.selectedImageUri.length > 0 ? '重新选择' : '选择图片')
              .layoutWeight(1)
              .height(44)
              .backgroundColor('#1D9E75')
              .onClick(() => this.chooseImage())
            Button(this.isRecognizing ? '识别中…' : '开始识别')
              .layoutWeight(1)
              .height(44)
              .enabled(!this.isRecognizing && this.selectedImageUri.length > 0)
              .backgroundColor('#2F6FED')
              .onClick(() => this.recognizeSelectedImage())
          }
          .width('92%')

          Column({ space: 5 }) {
            Text(this.ocrStatus)
              .width('100%')
              .fontSize(13)
              .fontColor(this.ocrStatus.startsWith('OCR 失败') ? '#C83B3B' : '#444444')
            if (this.supportedLanguageText.length > 0) {
              Text(this.supportedLanguageText)
                .width('100%')
                .fontSize(11)
                .fontColor('#888888')
            }
          }
          .width('92%')
          .padding(12)
          .borderRadius(10)
          .backgroundColor('#F1F1F1')
          .alignItems(HorizontalAlign.Start)

          TextArea({ text: this.ocrText, placeholder: '识别出的文字会显示在这里，也可以手动输入或修改' })
            .width('92%')
            .height(150)
            .fontSize(14)
            .backgroundColor(Color.White)
            .borderRadius(12)
            .onChange((value: string) => {
              this.ocrText = value;
            })

          Row({ space: 10 }) {
            Button(this.isSearching ? '搜索中…' : '搜索本地题库')
              .layoutWeight(1)
              .height(44)
              .enabled(!this.isSearching && this.ocrText.trim().length > 0)
              .backgroundColor('#1D9E75')
              .onClick(() => this.searchCurrentText())
            Button('清除')
              .width(90)
              .height(44)
              .backgroundColor('#777777')
              .onClick(() => this.clearImageAndResult())
          }
          .width('92%')

          Text(this.bankStatus)
            .width('92%')
            .fontSize(13)
            .fontColor('#555555')
            .padding(12)
            .borderRadius(10)
            .backgroundColor('#EAF6F1')

          if (this.searchResults.length > 0) {
            Column({ space: 10 }) {
              ForEach(this.searchResults, (result: SearchResult, index: number) => {
                this.resultCard(result, index)
              }, (result: SearchResult) => result.question.id.toString())
            }
            .width('92%')
          }

          Column({ space: 10 }) {
            Text('实验性悬浮窗')
              .width('100%')
              .fontSize(15)
              .fontWeight(FontWeight.Bold)
              .fontColor('#333333')
            Text('该功能依赖特殊系统权限，不影响上面的图片 OCR 主流程。')
              .width('100%')
              .fontSize(12)
              .fontColor('#777777')
            Button(this.floatOn ? '关闭悬浮窗' : '启动悬浮窗')
              .width('100%')
              .height(42)
              .backgroundColor(this.floatOn ? '#E24B4A' : '#777777')
              .onClick(() => {
                if (this.floatOn) {
                  this.hideFloat();
                } else {
                  this.showFloat();
                }
              })
          }
          .width('92%')
          .padding(14)
          .margin({ bottom: 30 })
          .borderRadius(12)
          .backgroundColor('#F3F3F3')
          .alignItems(HorizontalAlign.Start)
        }
        .width('100%')
        .padding({ top: 14 })
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
