import UIKit
import OpenKuiklyIOSRender

class KRImageAdapter: NSObject, IKRImageAdapter {
    func fetchImage(_ url: String, completion: @escaping (UIImage?) -> Void) {
        // Basic: load from bundle or URL
        if let bundleImage = UIImage(named: url) {
            completion(bundleImage)
            return
        }
        guard let imageUrl = URL(string: url) else {
            completion(nil)
            return
        }
        URLSession.shared.dataTask(with: imageUrl) { data, _, _ in
            if let data = data, let image = UIImage(data: data) {
                DispatchQueue.main.async { completion(image) }
            } else {
                DispatchQueue.main.async { completion(nil) }
            }
        }.resume()
    }
}
