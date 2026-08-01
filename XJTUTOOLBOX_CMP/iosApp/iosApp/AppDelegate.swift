import UIKit
import OpenKuiklyIOSRender

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        initKuiklyAdapters()

        window = UIWindow(frame: UIScreen.main.bounds)
        let vc = KuiklyRenderViewController(pageName: "main", pageData: [:])
        window?.rootViewController = vc
        window?.makeKeyAndVisible()
        return true
    }

    private func initKuiklyAdapters() {
        KuiklyRenderAdapterManager.shared().imageAdapter = KRImageAdapter()
        KuiklyRenderAdapterManager.shared().logAdapter = KRLogAdapter()
        KuiklyRenderAdapterManager.shared().routerAdapter = KRRouterAdapter()
        KuiklyRenderAdapterManager.shared().exceptionAdapter = KRExceptionAdapter()
    }
}
