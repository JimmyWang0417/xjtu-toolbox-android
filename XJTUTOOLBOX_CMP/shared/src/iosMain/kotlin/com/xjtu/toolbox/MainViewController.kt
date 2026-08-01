package com.xjtu.toolbox

import platform.UIKit.UIViewController

// KuiklyUI iOS integration: uses KuiklyRender framework
// The actual iOS entry point is configured via KuiklyUI's iOS SDK
// This placeholder returns a basic UIViewController;
// in production, KuiklyRenderView handles page rendering natively
fun MainViewController(): UIViewController {
    return UIViewController()
}
