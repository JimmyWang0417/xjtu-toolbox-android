import { KuiklyRenderView } from '@kuikly-open/render';
import globalNativeManager from '../kuikly/MyNativeManager';
import { KuiklyViewDelegate } from '../kuikly/KuiklyViewDelegate';

@Entry
@Component
struct Index {
  private delegate: KuiklyViewDelegate = new KuiklyViewDelegate();

  build() {
    Column() {
      KuiklyRenderView({
        url: 'main',
        nativeManager: globalNativeManager,
        delegate: this.delegate,
        initData: {}
      })
        .width('100%')
        .height('100%')
    }
    .width('100%')
    .height('100%')
  }
}
