import { image } from '@kit.ImageKit';
import { textRecognition } from '@kit.CoreVisionKit';

export class OcrService {
  static async getSupportedLanguages(): Promise<string[]> {
    try {
      const initialized: boolean = await textRecognition.init();
      if (!initialized) {
        throw new Error('Unable to initialize OCR service');
      }
      const languages: Array<string> = await textRecognition.getSupportedLanguages();
      return languages;
    } finally {
      await textRecognition.release();
    }
  }

  static async recognizeImage(uri: string): Promise<string> {
    const sourceUri: string = uri.trim();
    if (sourceUri.length === 0) {
      throw new Error('Image URI must not be empty');
    }

    let imageSource: image.ImageSource | undefined = undefined;
    let pixelMap: image.PixelMap | undefined = undefined;
    try {
      const initialized: boolean = await textRecognition.init();
      if (!initialized) {
        throw new Error('Unable to initialize OCR service');
      }

      imageSource = image.createImageSource(sourceUri);
      if (imageSource === undefined) {
        throw new Error('Unable to create image source');
      }

      pixelMap = await imageSource.createPixelMap();
      if (pixelMap === undefined) {
        throw new Error('Unable to create pixel map');
      }

      const result: textRecognition.TextRecognitionResult = await textRecognition.recognizeText({
        pixelMap: pixelMap
      });
      return result.value.trim();
    } finally {
      if (pixelMap !== undefined) {
        await pixelMap.release();
      }
      if (imageSource !== undefined) {
        await imageSource.release();
      }
      await textRecognition.release();
    }
  }
}
