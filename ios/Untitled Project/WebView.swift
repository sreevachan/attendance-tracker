import SwiftUI
import WebKit
import WidgetKit

// Must match the App Group in both entitlements files and AttendanceWidget.swift.
let appGroup = "group.sreevachan.attendance"

struct WebView: UIViewRepresentable {
    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "att")
        let web = WKWebView(frame: .zero, configuration: config)
        web.scrollView.contentInsetAdjustmentBehavior = .never

        // Same colours as the page background, so there is no white flash in dark mode.
        let bg = UIColor { traits in
            traits.userInterfaceStyle == .dark
                ? UIColor(red: 0.059, green: 0.086, blue: 0.141, alpha: 1)
                : UIColor(red: 0.933, green: 0.949, blue: 0.969, alpha: 1)
        }
        web.isOpaque = false
        web.backgroundColor = bg
        web.scrollView.backgroundColor = bg

        // Looks in web/ first, then at the top level of the bundle.
        let url = Bundle.main.url(forResource: "index", withExtension: "html", subdirectory: "web")
            ?? Bundle.main.url(forResource: "index", withExtension: "html")
        if let url = url {
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
            s.set((d["min"] as? NSNumber)?.doubleValue ?? 75, forKey: "min")
            WidgetCenter.shared.reloadAllTimelines()
        }
    }
}