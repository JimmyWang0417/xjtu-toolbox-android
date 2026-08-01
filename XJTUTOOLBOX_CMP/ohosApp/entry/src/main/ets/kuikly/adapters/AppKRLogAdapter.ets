import { IKRLogAdapter } from '@kuikly-open/render';
import { hilog } from '@kit.PerformanceAnalysisKit';

export class AppKRLogAdapter implements IKRLogAdapter {
  i(tag: string, msg: string): void {
    hilog.info(0x30, tag, '%{public}s', msg);
  }

  d(tag: string, msg: string): void {
    hilog.debug(0x30, tag, '%{public}s', msg);
  }

  e(tag: string, msg: string): void {
    hilog.error(0x30, tag, '%{public}s', msg);
  }
}
