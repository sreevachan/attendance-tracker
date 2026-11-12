import SwiftUI
import WebKit
import WidgetKit

// Change this to your own App Group ID (same one in the widget file).
let appGroup = "group.com.yourname.attendance"

struct WebView: UIViewRepresentable {
    func makeCoordinator() -> Coordinator { Coordinator() }
    
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "att")
        let web = WKWebView(frame: .zero, configuration: config)
        web.scrollView.contentInsetAdjustmentBehavior = .never
        if let url = Bundle.main.url(forResource: "index", withExtension: "html") {
            web.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        }
        return web
    }
    
    func updateUIView(_ uiView: WKWebView, context: Context) {}
    
    class Coordinator: NSObject, WKScriptMessageHandler {
        func userContentController(_ c: WKUserContentController, didReceive m: WKScriptMessage) {
            guard let d = m.body as? [String: Any], let s = UserDefaults(suiteName: appGroup) else { return }
            s.set((d["pct"] as? NSNumber)?.doubleValue ?? 0, forKey: "pct")
            s.set((d["a"] as? NSNumber)?.intValue ?? 0, forKey: "a")
            s.set((d["c"] as? NSNumber)?.intValue ?? 0, forKey: "c")
            WidgetCenter.shared.reloadAllTimelines()
        }
    }
}
