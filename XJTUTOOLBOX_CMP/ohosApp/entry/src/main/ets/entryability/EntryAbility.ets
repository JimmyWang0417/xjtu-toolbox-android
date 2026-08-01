import { AbilityConstant, UIAbility, Want } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { window } from '@kit.ArkUI';
import { KuiklyRenderAdapterManager } from '@kuikly-open/render';
import { AppKRLogAdapter } from '../kuikly/adapters/AppKRLogAdapter';
import { AppKRRouterAdapter } from '../kuikly/adapters/AppKRRouterAdapter';

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    hilog.info(0x0000, 'XJTUToolbox', '%{public}s', 'Ability onCreate');
  }

  onDestroy(): void {
    hilog.info(0x0000, 'XJTUToolbox', '%{public}s', 'Ability onDestroy');
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    hilog.info(0x0000, 'XJTUToolbox', '%{public}s', 'Ability onWindowStageCreate');

    KuiklyRenderAdapterManager.krLogAdapter = new AppKRLogAdapter();
    KuiklyRenderAdapterManager.krRouterAdapter = new AppKRRouterAdapter();

    windowStage.loadContent('pages/Index', (err) => {
      if (err.code) {
        hilog.error(0x0000, 'XJTUToolbox', 'Failed to load content. Cause: %{public}s', JSON.stringify(err) ?? '');
        return;
      }
      hilog.info(0x0000, 'XJTUToolbox', 'Succeeded in loading the content.');
    });
  }

  onWindowStageDestroy(): void {
    hilog.info(0x0000, 'XJTUToolbox', '%{public}s', 'Ability onWindowStageDestroy');
  }

  onForeground(): void {
    hilog.info(0x0000, 'XJTUToolbox', '%{public}s', 'Ability onForeground');
  }

  onBackground(): void {
    hilog.info(0x0000, 'XJTUToolbox', '%{public}s', 'Ability onBackground');
  }
}
