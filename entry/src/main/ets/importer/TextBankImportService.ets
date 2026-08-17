import { fileIo, picker } from '@kit.CoreFileKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { common } from '@kit.AbilityKit';
import { util } from '@kit.ArkTS';

/**
 * 用户选择的 TXT 题库文件。
 * content 只在内存中返回给调用方，不写入日志。
 */
export class SelectedTextBank {
  uri: string;
  name: string;
  content: string;
  modifiedAt: number;

  constructor(uri: string, name: string, content: string, modifiedAt: number) {
    this.uri = uri;
    this.name = name;
    this.content = content;
    this.modifiedAt = modifiedAt;
  }
}

/** 负责让用户选择并读取一个 TXT 题库文件。 */
export class TextBankImportService {
  private static readonly MAX_FILE_BYTES: number = 2 * 1024 * 1024;
  private static readonly TXT_SUFFIX_FILTER: Array<string> = ['TXT|.txt'];

  /**
   * 打开系统文件选择器并读取一个 TXT 文件。
   * 用户取消选择时返回 undefined；其他错误继续抛给页面处理。
   */
  static async pickOneTxt(context: common.UIAbilityContext): Promise<SelectedTextBank | undefined> {
    const documentPicker: picker.DocumentViewPicker = new picker.DocumentViewPicker(context);
    let selectedUris: Array<string>;

    try {
      selectedUris = await documentPicker.select({
        fileSuffixFilters: TextBankImportService.TXT_SUFFIX_FILTER,
        maxSelectNumber: 1
      });
    } catch (error) {
      if (TextBankImportService.isUserCancellation(error)) {
        return undefined;
      }
      throw new Error('打开文件选择器失败，请稍后重试。');
    }

    if (selectedUris.length === 0 || selectedUris[0].trim().length === 0) {
      return undefined;
    }

    const uri: string = selectedUris[0];
    let selectedFile: fileIo.File | undefined = undefined;
    try {
      // Picker 返回的是 URI，不应直接按应用沙箱路径调用 readText。
      selectedFile = await fileIo.open(uri, fileIo.OpenMode.READ_ONLY);
    } catch (error) {
      throw new Error('无法打开所选 TXT 文件，请确认文件仍可访问。');
    }

    try {
      const name = selectedFile.name.trim().length > 0 ?
        selectedFile.name : TextBankImportService.extractFileName(uri);
      if (name.includes('.') && !TextBankImportService.isTxtFile(name)) {
        throw new Error('只能选择 .txt 格式的题库文件。');
      }

      let fileStat: fileIo.Stat;
      try {
        fileStat = await fileIo.stat(selectedFile.fd);
      } catch (error) {
        throw new Error('无法读取所选 TXT 文件的信息，请确认文件仍可访问。');
      }

      if (fileStat.size > TextBankImportService.MAX_FILE_BYTES) {
        throw new Error('TXT 题库文件不能超过 2 MB。');
      }

      let content: string;
      try {
        const bytes: ArrayBuffer = new ArrayBuffer(fileStat.size);
        const bytesRead: number = await fileIo.read(selectedFile.fd, bytes);
        if (bytesRead !== fileStat.size) {
          throw new Error(`题库文件读取不完整：预期 ${fileStat.size} 字节，实际 ${bytesRead} 字节。`);
        }
        const decoder: util.TextDecoder = new util.TextDecoder('utf-8', { fatal: true });
        content = decoder.decode(new Uint8Array(bytes));
      } catch (error) {
        throw new Error('读取 TXT 题库失败，请确认文件编码为 UTF-8 且文件仍可访问。');
      }

      if (content.trim().length === 0) {
        throw new Error('所选 TXT 题库内容为空，请选择包含题目的文件。');
      }

      return new SelectedTextBank(
        uri,
        name,
        content,
        fileStat.mtime * 1000
      );
    } finally {
      await fileIo.close(selectedFile);
    }
  }

  private static isTxtFile(name: string): boolean {
    return name.toLowerCase().endsWith('.txt');
  }

  private static extractFileName(uri: string): string {
    const withoutQuery: string = uri.split(/[?#]/)[0];
    const lastSlash: number = Math.max(withoutQuery.lastIndexOf('/'), withoutQuery.lastIndexOf('\\'));
    const encodedName: string = lastSlash >= 0 ? withoutQuery.substring(lastSlash + 1) : withoutQuery;
    if (encodedName.length === 0) {
      throw new Error('无法识别所选文件名。');
    }

    try {
      const decodedName: string = decodeURIComponent(encodedName);
      if (decodedName.length === 0 || decodedName === '.' || decodedName === '..') {
        throw new Error('无法识别所选文件名。');
      }
      return decodedName;
    } catch (error) {
      throw new Error('所选文件名格式无效。');
    }
  }

  private static isUserCancellation(error: Object): boolean {
    const businessError: BusinessError = error as BusinessError;
    const message: string = businessError.message ?? '';
    const normalizedMessage: string = message.toLowerCase();
    return normalizedMessage.includes('cancel') || message.includes('取消');
  }
}
