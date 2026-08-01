import Foundation
import OpenKuiklyIOSRender

class KRExceptionAdapter: NSObject, IKRUncaughtExceptionHandlerAdapter {
    func uncaughtException(_ error: Error) {
        print("[KuiklyError] Uncaught exception: \(error.localizedDescription)")
    }
}
