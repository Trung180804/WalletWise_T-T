import Foundation
import PhotosUI
import UniformTypeIdentifiers
import UIKit
import WalletWiseShared
import FirebaseAuth

private final class ImagePickerCancellation: NSObject, SupportCancellation {
    private(set) var active = true
    var onCancel: (() -> Void)?

    func cancel() {
        precondition(Thread.isMainThread)
        guard active else { return }
        active = false
        let cleanup = onCancel
        onCancel = nil
        cleanup?()
    }
}

/** One-shot native picker and bounded encoder. It never persists or uploads image bytes. */
final class SupportImagePickerAdapter: NSObject, CallbackSupportImagePicker, PHPickerViewControllerDelegate {
    weak var hostViewController: UIViewController?
    private let baseUrl: String
    private weak var pickerViewController: PHPickerViewController?
    private var cancellation: ImagePickerCancellation?
    private var completion: SupportImagePickCompletion?
    private var selectionId: String?

    init(baseUrl: String) {
        self.baseUrl = baseUrl
        super.init()
    }

    func pick(selectionId: String, completion: SupportImagePickCompletion) -> SupportCancellation {
        precondition(Thread.isMainThread)
        guard cancellation?.active != true else {
            completion.imagePicked(image: nil, failure: .busy)
            return ImagePickerCancellation()
        }
        guard let host = visibleController(from: hostViewController), host.presentedViewController == nil else {
            completion.imagePicked(image: nil, failure: .unavailable)
            return ImagePickerCancellation()
        }

        let token = ImagePickerCancellation()
        self.cancellation = token
        self.completion = completion
        self.selectionId = selectionId
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images
        configuration.selectionLimit = 1
        configuration.preferredAssetRepresentationMode = .current
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = self
        picker.modalPresentationStyle = .formSheet
        pickerViewController = picker
        token.onCancel = { [weak self, weak picker] in
            picker?.dismiss(animated: true)
            self?.clear()
        }
        host.present(picker, animated: true)
        return token
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        precondition(Thread.isMainThread)
        picker.dismiss(animated: true)
        pickerViewController = nil
        guard cancellation?.active == true else { clear(); return }
        guard let result = results.first else {
            finish(image: nil, failure: .cancelled)
            return
        }
        let provider = result.itemProvider
        guard provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) else {
            finish(image: nil, failure: .invalidImage)
            return
        }
        let requestedId = selectionId
        let suggestedName = provider.suggestedName
        provider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { [weak self] url, _ in
            guard let self, let requestedId, let url else {
                DispatchQueue.main.async { self?.finish(image: nil, failure: .invalidImage) }
                return
            }
            let result = autoreleasepool { Self.prepare(url: url, originalName: suggestedName, selectionId: requestedId) }
            DispatchQueue.main.async {
                switch result {
                case .success(let payload): self.upload(image: payload.0, data: payload.1)
                case .failure(let error): self.finish(image: nil, failure: error.failure)
                }
            }
        }
    }

    private func upload(image: PreparedSupportImage, data: Data) {
        let origin = baseUrl.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard !origin.isEmpty, let url = URL(string: origin + "/api/upload/image") else {
            self.finish(image: nil, failure: .unavailable)
            return
        }
        guard let user = Auth.auth().currentUser else {
            self.finish(image: nil, failure: .unavailable)
            return
        }
        user.getIDTokenForcingRefresh(false) { [weak self] token, error in
            guard let self = self, let token = token else {
                DispatchQueue.main.async { self?.finish(image: nil, failure: .unavailable) }
                return
            }
            self.executeUpload(url: url, token: token, data: data, image: image, retry: true)
        }
    }

    private func executeUpload(url: URL, token: String, data: Data, image: PreparedSupportImage, retry: Bool) {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"File\"; filename=\"\(image.fileName)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(image.mimeType)\r\n\r\n".data(using: .utf8)!)
        body.append(data)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)

        let task = URLSession.shared.uploadTask(with: request, from: body) { [weak self] responseData, response, error in
            guard let self = self else { return }
            if let http = response as? HTTPURLResponse, http.statusCode == 401, retry {
                Auth.auth().currentUser?.getIDTokenForcingRefresh(true) { newToken, error in
                    if let newToken = newToken {
                        self.executeUpload(url: url, token: newToken, data: data, image: image, retry: false)
                    } else {
                        DispatchQueue.main.async { self.finish(image: nil, failure: .unavailable) }
                    }
                }
                return
            }
            if let responseData = responseData, let http = response as? HTTPURLResponse, http.statusCode >= 200, http.statusCode < 300,
               let json = try? JSONSerialization.jsonObject(with: responseData) as? [String: Any],
               let uploadUrl = json["url"] as? String {
                let finalImage = PreparedSupportImage(
                    selectionId: image.selectionId,
                    fileName: image.fileName,
                    mimeType: image.mimeType,
                    byteCount: image.byteCount,
                    pixelWidth: image.pixelWidth,
                    pixelHeight: image.pixelHeight,
                    uploadUrl: uploadUrl
                )
                DispatchQueue.main.async { self.finish(image: finalImage, failure: nil) }
            } else {
                DispatchQueue.main.async { self.finish(image: nil, failure: .unavailable) }
            }
        }
        task.resume()
    }

    private func finish(image: PreparedSupportImage?, failure: SupportImagePickFailure?) {
        precondition(Thread.isMainThread)
        guard let token = cancellation, token.active else { clear(); return }
        let callback = completion
        token.onCancel = nil
        token.cancel()
        clear()
        callback?.imagePicked(image: image, failure: failure)
    }

    private func clear() {
        cancellation = nil
        completion = nil
        selectionId = nil
        pickerViewController = nil
    }

    private func visibleController(from controller: UIViewController?) -> UIViewController? {
        if let navigation = controller as? UINavigationController { return visibleController(from: navigation.visibleViewController) }
        if let tab = controller as? UITabBarController { return visibleController(from: tab.selectedViewController) }
        if let presented = controller?.presentedViewController { return visibleController(from: presented) }
        return controller
    }

    private static func prepare(url: URL, originalName: String?, selectionId: String) -> Result<(PreparedSupportImage, Data), ImagePreparationError> {
        do {
            let values = try url.resourceValues(forKeys: [.fileSizeKey, .isRegularFileKey])
            guard values.isRegularFile == true, let sourceSize = values.fileSize, sourceSize > 0 else {
                return .failure(.invalid)
            }
            guard sourceSize <= 12 * 1024 * 1024 else { return .failure(.tooLarge) }
            let handle = try FileHandle(forReadingFrom: url)
            defer { handle.closeFile() }
            let sourceData = handle.readData(ofLength: 12 * 1024 * 1024 + 1)
            guard sourceData.count == sourceSize, sourceData.count <= 12 * 1024 * 1024,
                  supportedImageBytes(sourceData),
                  let source = UIImage(data: sourceData) else { return .failure(.invalid) }

            let orientedWidth = source.imageOrientation.isQuarterTurn ? source.size.height : source.size.width
            let orientedHeight = source.imageOrientation.isQuarterTurn ? source.size.width : source.size.height
            guard orientedWidth > 0, orientedHeight > 0,
                  orientedWidth * orientedHeight <= 40_000_000 else { return .failure(.tooLarge) }
            var normalized = render(source, fitting: CGSize(width: orientedWidth, height: orientedHeight), maxDimension: 1600)
            var quality: CGFloat = 0.88
            var encoded: Data?
            for _ in 0..<14 {
                if let candidate = normalized.jpegData(compressionQuality: quality), candidate.count <= 512 * 1024 {
                    encoded = candidate
                    break
                }
                if quality > 0.58 {
                    quality -= 0.10
                } else {
                    let next = CGSize(
                        width: max(320, floor(normalized.size.width * 0.8)),
                        height: max(320, floor(normalized.size.height * 0.8))
                    )
                    guard next.width < normalized.size.width || next.height < normalized.size.height else { break }
                    normalized = render(normalized, fitting: next, maxDimension: max(next.width, next.height))
                    quality = 0.82
                }
            }
            guard let encoded else { return .failure(.tooLarge) }
            let prepared = PreparedSupportImage(
                selectionId: selectionId,
                fileName: safeJpegName(originalName),
                mimeType: "image/jpeg",
                byteCount: Int64(encoded.count),
                pixelWidth: Int32(normalized.size.width.rounded()),
                pixelHeight: Int32(normalized.size.height.rounded()),
                uploadUrl: ""
            )
            return .success((prepared, encoded))
        } catch {
            return .failure(.invalid)
        }
    }

    private static func render(_ source: UIImage, fitting sourceSize: CGSize, maxDimension: CGFloat) -> UIImage {
        let largest = max(sourceSize.width, sourceSize.height)
        let ratio = largest > maxDimension ? maxDimension / largest : 1
        let size = CGSize(width: max(1, floor(sourceSize.width * ratio)), height: max(1, floor(sourceSize.height * ratio)))
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            UIColor.white.setFill()
            context.fill(CGRect(origin: .zero, size: size))
            source.draw(in: CGRect(origin: .zero, size: size))
        }
    }

    private static func supportedImageBytes(_ data: Data) -> Bool {
        let bytes = [UInt8](data.prefix(12))
        let jpeg = bytes.count >= 3 && bytes[0...2] == [0xFF, 0xD8, 0xFF]
        let png = bytes.count >= 8 && bytes[0...7] == [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]
        let webp = bytes.count >= 12 && String(bytes: bytes[0...3], encoding: .ascii) == "RIFF" && String(bytes: bytes[8...11], encoding: .ascii) == "WEBP"
        return jpeg || png || webp
    }

    private static func safeJpegName(_ original: String?) -> String {
        let stem = (original as NSString?)?.deletingPathExtension ?? "support-image"
        let safe = stem.unicodeScalars.map { scalar -> Character in
            let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "._-"))
            return allowed.contains(scalar) ? Character(String(scalar)) : "_"
        }
        let value = String(safe).trimmingCharacters(in: CharacterSet(charactersIn: "._-")).prefix(96)
        return (value.isEmpty ? "support-image" : String(value)) + ".jpg"
    }
}

private enum ImagePreparationError: Error {
    case invalid
    case tooLarge
    var failure: SupportImagePickFailure { self == .tooLarge ? .tooLarge : .invalidImage }
}

private extension UIImage.Orientation {
    var isQuarterTurn: Bool { self == .left || self == .leftMirrored || self == .right || self == .rightMirrored }
}
