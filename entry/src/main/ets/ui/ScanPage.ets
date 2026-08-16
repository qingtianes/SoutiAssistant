import { common } from '@kit.AbilityKit';
import { router } from '@kit.ArkUI';
import { BusinessError } from '@kit.BasicServicesKit';
import promptAction from '@ohos.promptAction';
import { CameraHelper } from '../camera/CameraHelper';
import { OcrService } from '../ocr/OcrService';
import { QuestionBank } from '../service/QuestionBank';
import { SearchResult } from '../model/QuestionBankModels';

@Entry
@Component
struct ScanPage {
  @State surfaceId: string = '';
  @State hasPermission: boolean = false;
  @State cameraReady: boolean = false;
  @State paused: boolean = false;
  @State ocrText: string = '';
  @State status: string = '正在准备摄像头…';
  @State results: SearchResult[] = [];
  @State busy: boolean = false;

  private camera: CameraHelper | null = null;
  private readonly questionBank: QuestionBank = new QuestionBank();

  aboutToAppear(): void {
    this.requestPermission();
  }

  async requestPermission(): Promise<void> {
    const context = getContext(this) as common.UIAbilityContext;
    this.hasPermission = await CameraHelper.requestCameraPermission(context);
    this.status = this.hasPermission ? '摄像头已授权，准备预览…' : '未获得摄像头权限';
  }

  onXComponentLoad(context: object): void {
    const xc = context as Record<string, Object>;
    this.surfaceId = xc['surfaceId'] as string;
    this.startCamera();
  }

  async startCamera(): Promise<void> {
    if (this.surfaceId.length === 0 || !this.hasPermission || this.cameraReady) {
      return;
    }
    const ctx = getContext(this) as common.UIAbilityContext;
    this.camera = new CameraHelper(ctx);
    this.camera.onPhotoTaken = (path: string) => this.handlePhoto(path);
    const ok = await this.camera.initAndStartPreview(this.surfaceId);
    if (ok) {
      this.cameraReady = true;
      this.status = '摄像头已启动，正在实时识别…';
      this.camera.startAutoCapture(1500);
    } else {
      this.status = '摄像头启动失败，请检查权限或设备支持';
    }
  }

  async handlePhoto(path: string): Promise<void> {
    if (this.paused || this.busy) {
      return;
    }
    this.busy = true;
    try {
      const text = await OcrService.recognizeImage(path);
      if (this.paused) {
        return;
      }
      this.ocrText = text;
      this.status = text.length > 0 ? `识别到 ${text.length} 字，正在搜索…` : '未识别到文字';
      if (text.trim().length === 0) {
        return;
      }
      const ctx = getContext(this) as common.UIAbilityContext;
      const found = await this.questionBank.search(ctx, text.trim(), 5);
      if (!this.paused) {
        this.results = found;
        this.status = found.length > 0 ? `找到 ${found.length} 条匹配` : '未找到匹配题目';
      }
    } catch (err) {
      const e = err as BusinessError;
      this.status = `识别失败：${e.message ?? JSON.stringify(err)}`;
    } finally {
      this.busy = false;
    }
  }

  togglePause(): void {
    this.paused = !this.paused;
    if (this.paused) {
      this.camera?.stopAutoCapture();
      this.status = '已暂停，结果已锁定';
    } else {
      this.camera?.startAutoCapture(1500);
      this.status = '已继续，正在实时识别…';
    }
  }

  releaseCamera(): void {
    this.camera?.stopAutoCapture();
    this.camera?.release();
    this.cameraReady = false;
  }

  aboutToDisappear(): void {
    this.releaseCamera();
  }

  @Builder
  private resultCard(result: SearchResult, index: number) {
    Column({ space: 5 }) {
      Text(`匹配 ${index + 1} · ${result.bankName} · ${result.score} 分`)
        .fontSize(11)
        .fontColor('#777777')
      Text(result.question.stem)
        .width('100%')
        .fontSize(14)
        .fontColor('#222222')
        .lineHeight(21)
      if (result.question.answer.length > 0) {
        Text(`参考答案：${result.question.answer}`)
          .width('100%')
          .fontSize(13)
          .fontWeight(FontWeight.Bold)
          .fontColor('#1D9E75')
      }
    }
    .width('100%')
    .padding(12)
    .borderRadius(10)
    .backgroundColor(Color.White)
    .alignItems(HorizontalAlign.Start)
  }

  build() {
    Column() {
      Row() {
        Text('扫描搜题')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor(Color.White)
        Blank()
        Button('返回')
          .height(32)
          .fontSize(13)
          .backgroundColor('#1D9E75')
          .onClick(() => {
            this.releaseCamera();
            router.back();
          })
      }
      .width('100%')
      .height(56)
      .padding({ left: 16, right: 16 })
      .alignItems(VerticalAlign.Center)
      .backgroundColor('#1D9E75')

      Stack({ alignContent: Alignment.TopStart }) {
        XComponent({ id: 'scanCameraSurface', type: 'surface', libraryname: '' })
          .width('100%')
          .height(320)
          .onLoad((context: object) => this.onXComponentLoad(context))

        Column() {
          Text(this.status)
            .fontSize(13)
            .fontColor('#FFFFFF')
            .padding(8)
            .borderRadius(8)
            .backgroundColor('#88000000')
        }
        .margin(12)
      }
      .width('100%')
      .height(320)
      .backgroundColor(Color.Black)

      Row({ space: 10 }) {
        Button(this.paused ? '继续' : '暂停')
          .layoutWeight(1)
          .height(40)
          .backgroundColor(this.paused ? '#1D9E75' : '#E24B4A')
          .onClick(() => this.togglePause())
        Button('重新授权')
          .layoutWeight(1)
          .height(40)
          .backgroundColor('#777777')
          .onClick(() => this.requestPermission())
      }
      .width('92%')
      .margin({ top: 12 })

      Text('识别文字')
        .width('92%')
        .fontSize(14)
        .fontWeight(FontWeight.Bold)
        .fontColor('#222222')
        .margin({ top: 10 })

      Text(this.ocrText.length > 0 ? this.ocrText : '将题目对准摄像头，自动识别并搜索')
        .width('92%')
        .fontSize(12)
        .fontColor(this.ocrText.length > 0 ? '#333333' : '#888888')
        .padding(10)
        .borderRadius(8)
        .backgroundColor('#F1F1F1')

      Scroll() {
        Column({ space: 8 }) {
          ForEach(this.results, (result: SearchResult, index: number) => {
            this.resultCard(result, index)
          }, (result: SearchResult) => `${result.question.bankId}:${result.question.id}`)
        }
        .width('92%')
        .padding({ top: 10, bottom: 20 })
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
