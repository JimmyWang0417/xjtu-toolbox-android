import { KuiklyRenderView } from '@kuikly-open/render';
import globalNativeManager from '../kuikly/MyNativeManager';
import { KuiklyViewDelegate } from '../kuikly/KuiklyViewDelegate';
import router from '@ohos.router';

interface PageParams {
  pageName: string;
  pageData: Record<string, string>;
}

@Entry
@Component
struct KuiklyPage {
  private delegate: KuiklyViewDelegate = new KuiklyViewDelegate();
  @State pageName: string = '';
  @State pageData: Record<string, string> = {};

  aboutToAppear(): void {
    const params = router.getParams() as PageParams;
    if (params) {
      this.pageName = params.pageName || 'main';
      this.pageData = params.pageData || {};
    }
  }

  build() {
    Column() {
      KuiklyRenderView({
        url: this.pageName,
        nativeManager: globalNativeManager,
        delegate: this.delegate,
        initData: this.pageData
      })
        .width('100%')
        .height('100%')
    }
    .width('100%')
    .height('100%')
  }
}
