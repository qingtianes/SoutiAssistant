import { photoAccessHelper } from '@kit.MediaLibraryKit';

export class ImagePickerService {
  static async pickOneImage(): Promise<string> {
    const picker: photoAccessHelper.PhotoViewPicker = new photoAccessHelper.PhotoViewPicker();
    const result: photoAccessHelper.PhotoSelectResult = await picker.select({
      MIMEType: photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE,
      maxSelectNumber: 1
    });
    const uris: Array<string> = result.photoUris;
    return uris.length > 0 ? uris[0] : '';
  }
}
