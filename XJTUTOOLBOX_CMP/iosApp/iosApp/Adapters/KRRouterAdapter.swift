import UIKit
import OpenKuiklyIOSRender

class KRRouterAdapter: NSObject, IKRRouterAdapter {
    func openPage(_ pageName: String, pageData: [String: Any], from context: UIViewController?) {
        let vc = KuiklyRenderViewController(pageName: pageName, pageData: pageData)
        context?.navigationController?.pushViewController(vc, animated: true)
    }

    func closePage(from context: UIViewController?) {
        context?.navigationController?.popViewController(animated: true)
    }
}
