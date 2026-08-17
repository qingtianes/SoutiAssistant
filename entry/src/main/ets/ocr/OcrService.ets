import { image } from '@kit.ImageKit';
import { textRecognition } from '@kit.CoreVisionKit';

export class OcrService {
  private static async releaseRecognition(): Promise<void> {
    try {
      if (typeof textRecognition.release === 'function') {
        await textRecognition.release();
      }
    } catch (_) {
      // 模拟器可能没有 OCR 原生模块，清理异常不能覆盖真正错误。
    }
  }

  static async getSupportedLanguages(): Promise<string[]> {
    try {
      const initialized: boolean = await textRecognition.init();
      if (!initialized) {
        throw new Error('OCR init failed');
      }
      return await textRecognition.getSupportedLanguages();
    } catch (_) {
      throw new Error('当前模拟器/设备未安装系统文字识别能力，请在支持 OCR 的鸿蒙真机验证');
    } finally {
      await OcrService.releaseRecognition();
    }
  }

  static async recognizeImage(uri: string): Promise<string> {
    const sourceUri: string = uri.trim();
    if (sourceUri.length === 0) {
      throw new Error('图片地址不能为空');
    }

    let imageSource: image.ImageSource | undefined = undefined;
    let pixelMap: image.PixelMap | undefined = undefined;
    try {
      const initialized: boolean = await textRecognition.init();
      if (!initialized) {
        throw new Error('OCR init failed');
      }
      imageSource = image.createImageSource(sourceUri);
      if (imageSource === undefined) {
        throw new Error('image source failed');
      }
      pixelMap = await imageSource.createPixelMap();
      if (pixelMap === undefined) {
        throw new Error('pixel map failed');
      }
      if (typeof textRecognition.recognizeText !== 'function') {
        throw new Error('OCR API unavailable');
      }
      const result: textRecognition.TextRecognitionResult = await textRecognition.recognizeText({
        pixelMap: pixelMap
      });
      return result.value.trim();
    } catch (_) {
      throw new Error('当前模拟器/设备未安装系统文字识别能力，请在支持 OCR 的鸿蒙真机验证');
    } finally {
      try {
        if (pixelMap !== undefined && typeof pixelMap.release === 'function') {
          await pixelMap.release();
        }
      } catch (_) {
        // 忽略图片资源清理异常。
      }
      try {
        if (imageSource !== undefined && typeof imageSource.release === 'function') {
          await imageSource.release();
        }
      } catch (_) {
        // 忽略图片资源清理异常。
      }
      await OcrService.releaseRecognition();
    }
  }
}
