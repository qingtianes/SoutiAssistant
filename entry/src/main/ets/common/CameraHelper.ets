/**
 * 摄像头辅助类：封装鸿蒙 Camera Kit
 * 功能：申请权限、打开后置摄像头预览、定时拍照保存到应用沙箱
 */
import { camera } from '@kit.CameraKit';
import { image } from '@kit.ImageKit';
import { abilityAccessCtrl, common } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { hilog } from '@kit.PerformanceAnalysisKit';

const TAG = 'CameraHelper';
const DOMAIN = 0x0000;

export class CameraHelper {
  private cameraManager: camera.CameraManager;
  private cameraInput: camera.CameraInput | null = null;
  private previewOutput: camera.PreviewOutput | null = null;
  private photoOutput: camera.PhotoOutput | null = null;
  private session: camera.PhotoSession | null = null;
  private receiver: image.ImageReceiver | null = null;
  private context: common.UIAbilityContext;
  // 拍照锁：上一张没处理完不拍下一张
  private busy: boolean = false;
  private timer: number = -1;
  /** 拍照回调（外部拿文件路径处理，比如 OCR） */
  onPhotoTaken: ((path: string) => void) | null = null;

  constructor(context: common.UIAbilityContext) {
    this.context = context;
    this.cameraManager = camera.getCameraManager(context);
  }

  /** 申请相机权限（用户手动授权） */
  static async requestCameraPermission(context: common.UIAbilityContext): Promise<boolean> {
    const atManager = abilityAccessCtrl.createAtManager();
    try {
      const res = await atManager.requestPermissionsFromUser(context, ['ohos.permission.CAMERA']);
      return res.authResults.every((r: number) => r === 0);
    } catch (err) {
      const e = err as BusinessError;
      hilog.error(DOMAIN, TAG, 'request permission failed: %{public}s', JSON.stringify(e));
      return false;
    }
  }

  /**
   * 初始化相机并开始预览
   * @param surfaceId XComponent 的 surface id
   */
  async initAndStartPreview(surfaceId: string): Promise<boolean> {
    try {
      // 1. 选择后置摄像头
      const cameras = this.cameraManager.getSupportedCameras();
      const back = cameras.find((c) => c.cameraPosition === camera.CameraPosition.CAMERA_POSITION_BACK);
      if (back === undefined) {
        hilog.error(DOMAIN, TAG, 'no back camera');
        return false;
      }
      // 2. 创建并打开相机输入
      this.cameraInput = this.cameraManager.createCameraInput(back);
      await this.cameraInput.open();
      // 3. 创建照片会话
      this.session = this.cameraManager.createSession(camera.SceneMode.NORMAL_PHOTO) as camera.PhotoSession;
      this.session.beginConfig();
      this.session.addInput(this.cameraInput);
      // 4. 预览输出（绑定到 XComponent surface）
      const outputCap = this.cameraManager.getSupportedOutputCapability(back, camera.SceneMode.NORMAL_PHOTO);
      const previewProfile = outputCap.previewProfiles[0];
      this.previewOutput = this.cameraManager.createPreviewOutput(previewProfile, surfaceId);
      this.session.addOutput(this.previewOutput);
      // 5. 拍照输出（用 ImageReceiver 接收 JPEG，写文件）
      await this.setupPhotoOutput(outputCap);
      // 6. 提交并启动
      await this.session.commitConfig();
      await this.session.start();
      hilog.info(DOMAIN, TAG, 'camera started');
      return true;
    } catch (err) {
      const e = err as BusinessError;
      hilog.error(DOMAIN, TAG, 'init camera failed: %{public}s', JSON.stringify(e));
      return false;
    }
  }

  /** 设置拍照输出：ImageReceiver 接收 JPEG → 写沙箱文件 */
  private async setupPhotoOutput(outputCap: camera.CameraOutputCapability): Promise<void> {
    const photoProfile = outputCap.photoProfiles[0];
    this.receiver = image.createImageReceiver(
      photoProfile.size.width,
      photoProfile.size.height,
      image.ImageFormat.JPEG,
      2
    );
    const recvSurfaceId = await this.receiver.getReceivingSurfaceId();
    this.photoOutput = this.cameraManager.createPhotoOutput(photoProfile, recvSurfaceId);

    // 接收照片回调
    this.receiver.on('imageArrival', () => {
      this.handleImage();
    });
  }

  /** 处理接收到的照片：写文件并回调 */
  private async handleImage(): Promise<void> {
    if (this.receiver === null) {
      return;
    }
    try {
      const img = await this.receiver.readNextImage();
      const component = await img.getComponent(image.ComponentType.JPEG);
      const buffer = component.byteBuffer;
      // 写入沙箱文件
      const filePath = `${this.context.filesDir}/shot_${Date.now()}.jpg`;
      const file = fs.openSync(filePath, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE);
      fs.writeSync(file.fd, buffer);
      fs.closeSync(file.fd);
      img.release();
      // 回调外部
      if (this.onPhotoTaken !== null) {
        this.onPhotoTaken(filePath);
      }
    } catch (err) {
      const e = err as BusinessError;
      hilog.error(DOMAIN, TAG, 'handle image failed: %{public}s', JSON.stringify(e));
    } finally {
      this.busy = false;
    }
  }

  /** 开始定时拍照（默认 2 秒一张） */
  startAutoCapture(intervalMs: number = 2000): void {
    if (this.timer !== -1) {
      return;
    }
    this.timer = setInterval(() => {
      if (this.photoOutput === null || this.busy) {
        return;
      }
      this.busy = true;
      try {
        this.photoOutput.capture();
      } catch (err) {
        this.busy = false;
        const e = err as BusinessError;
        hilog.error(DOMAIN, TAG, 'capture failed: %{public}s', JSON.stringify(e));
      }
    }, intervalMs);
  }

  /** 停止定时拍照 */
  stopAutoCapture(): void {
    if (this.timer !== -1) {
      clearInterval(this.timer);
      this.timer = -1;
    }
  }

  /** 释放相机 */
  async release(): Promise<void> {
    this.stopAutoCapture();
    try {
      if (this.session !== null) {
        await this.session.stop();
        await this.session.release();
      }
      if (this.cameraInput !== null) {
        await this.cameraInput.close();
      }
      if (this.receiver !== null) {
        await this.receiver.release();
      }
    } catch (err) {
      const e = err as BusinessError;
      hilog.error(DOMAIN, TAG, 'release camera failed: %{public}s', JSON.stringify(e));
    }
    this.session = null;
    this.cameraInput = null;
    this.previewOutput = null;
    this.photoOutput = null;
    this.receiver = null;
  }
}
