import { KRRecord } from '@kuikly-open/render';
import { IKRRouterAdapter } from '@kuikly-open/render';
import router from '@ohos.router';
import { common } from '@kit.AbilityKit';

export class AppKRRouterAdapter implements IKRRouterAdapter {
  openPage(context: common.UIAbilityContext, pageName: string, pageData: KRRecord): void {
    router.pushUrl({
      url: 'pages/KuiklyPage',
      params: { pageName, pageData }
    });
  }

  closePage(context: common.UIAbilityContext): void {
    router.back();
  }
}
