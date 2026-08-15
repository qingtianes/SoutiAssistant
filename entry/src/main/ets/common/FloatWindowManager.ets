/**
 * 悬浮窗管理器：负责创建/关闭搜题悬浮窗
 * 用系统悬浮窗能力（WINDOW_TYPE_FLOAT），答题时可悬浮在任意应用之上
 */
import { window } from '@kit.ArkUI';
import { common } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { hilog } from '@kit.PerformanceAnalysisKit';

const TAG = 'FloatWindow';

/** 窗口位置（ArkTS 不允许匿名对象类型，需显式声明） */
export interface FloatPosition {
  x: number;
  y: number;
}

export class FloatWindowManager {
  /** 悬浮窗实例（全局唯一） */
  static floatWin: window.Window | null = null;

  /** 是否已经显示 */
  static isShowing(): boolean {
    return FloatWindowManager.floatWin !== null;
  }

  /**
   * 创建并显示悬浮窗
   */
  static async createFloatWindow(ctx: common.UIAbilityContext): Promise<boolean> {
    // 已经存在就直接返回
    if (FloatWindowManager.floatWin !== null) {
      return true;
    }
    try {
      // 1. 创建悬浮窗
      const win = await window.createWindow({
        name: 'souti_float',
        windowType: window.WindowType.TYPE_FLOAT,
        ctx: ctx
      });
      // 2. 设置大小（宽260dp 高340dp，稍后按识别框需要再调）
      await win.resize(260, 340);
      // 3. 放到屏幕右上角
      await win.moveWindowTo(24, 120);
      // 4. 加载悬浮窗页面内容
      await win.setUIContent('pages/FloatPage');
      // 5. 显示
      await win.showWindow();
      FloatWindowManager.floatWin = win;
      hilog.info(0x0000, TAG, 'float window created');
      return true;
    } catch (err) {
      const e = err as BusinessError;
      hilog.error(0x0000, TAG, 'create float window failed: %{public}s', JSON.stringify(e));
      return false;
    }
  }

  /**
   * 关闭悬浮窗
   */
  static async destroyFloatWindow(): Promise<void> {
    const win = FloatWindowManager.floatWin;
    if (win !== null) {
      try {
        await win.destroyWindow();
      } catch (err) {
        const e = err as BusinessError;
        hilog.error(0x0000, TAG, 'destroy float window failed: %{public}s', JSON.stringify(e));
      }
      FloatWindowManager.floatWin = null;
    }
  }

  /**
   * 移动悬浮窗到指定位置（供页面内拖动调用）
   */
  static async moveTo(x: number, y: number): Promise<void> {
    const win = FloatWindowManager.floatWin;
    if (win === null) {
      return;
    }
    try {
      await win.moveWindowTo(x, y);
    } catch (err) {
      const e = err as BusinessError;
      hilog.error(0x0000, TAG, 'move float window failed: %{public}s', JSON.stringify(e));
    }
  }

  /** 获取悬浮窗当前位置 */
  static getPosition(): FloatPosition {
    const win = FloatWindowManager.floatWin;
    if (win === null) {
      const empty: FloatPosition = { x: 0, y: 0 };
      return empty;
    }
    const rect = win.getWindowProperties().windowRect;
    const pos: FloatPosition = { x: rect.left, y: rect.top };
    return pos;
  }
}
