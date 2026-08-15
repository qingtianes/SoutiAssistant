import { fileIo, picker } from '@kit.CoreFileKit';

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
  private static readonly MAX_FILE_BYTES: number = 10 * 1024 * 1024;
  private static readonly TXT_SUFFIX_FILTER: Array<string> = ['TXT|.txt'];

  /**
   * 打开系统文件选择器并读取一个 TXT 文件。
   * 用户取消选择时返回 undefined；其他错误继续抛给页面处理。
   */
  static async pickOneTxt(): Promise<SelectedTextBank | undefined> {
    const documentPicker: picker.DocumentViewPicker = new picker.DocumentViewPicker();
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
    const name: string = TextBankImportService.extractFileName(uri);
    if (!TextBankImportService.isTxtFile(name)) {
      throw new Error('只能选择 .txt 格式的题库文件。');
    }

    let fileStat: fileIo.Stat;
    try {
      fileStat = await fileIo.stat(uri);
    } catch (error) {
      throw new Error('无法读取所选 TXT 文件的信息，请确认文件仍可访问。');
    }

    if (fileStat.size > TextBankImportService.MAX_FILE_BYTES) {
      throw new Error('TXT 题库文件不能超过 10 MB。');
    }

    let content: string;
    try {
      content = await fileIo.readText(uri, { encoding: 'utf-8' });
    } catch (error) {
      throw new Error('读取 TXT 题库失败，请确认文件编码为 UTF-8 且文件仍可访问。');
    }

    if (content.trim().length === 0) {
      throw new Error('所选 TXT 题库内容为空，请选择包含题目的文件。');
    }

    // 文件在选择后可能被替换，读取后再做一次字符数保护，避免绕过大小限制。
    if (content.length > TextBankImportService.MAX_FILE_BYTES) {
      throw new Error('TXT 题库文件不能超过 10 MB。');
    }

    return new SelectedTextBank(
      uri,
      name,
      content,
      fileStat.mtime * 1000
    );
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
    if (businessError.code === 201) {
      return true;
    }
    const message: string = businessError.message ?? '';
    const normalizedMessage: string = message.toLowerCase();
    return normalizedMessage.includes('cancel') || message.includes('取消');
  }
}
