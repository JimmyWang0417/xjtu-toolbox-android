import Foundation
import OpenKuiklyIOSRender

class KRLogAdapter: NSObject, IKRLogAdapter {
    func info(_ tag: String, msg: String) {
        print("[INFO][\(tag)] \(msg)")
    }

    func debug(_ tag: String, msg: String) {
        print("[DEBUG][\(tag)] \(msg)")
    }

    func error(_ tag: String, msg: String) {
        print("[ERROR][\(tag)] \(msg)")
    }
}
