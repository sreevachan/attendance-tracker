import SwiftUI
import WebKit
import WidgetKit
import FirebaseCore
import FirebaseAuth
import GoogleSignIn

// Must match the App Group in both entitlements files and AttendanceWidget.swift.
let appGroup = "group.sreevachan.attendance"

/// Google sign-in, sign-out and account deletion. The web page asks; this class does it
/// natively, because Google does not allow sign-in inside an embedded web view.
final class AuthBridge {
    weak var web: WKWebView?

    func handle(_ action: String) {
        switch action {
        case "status": sendStatus()
        case "signIn": signIn(reauthAndDelete: false)
        case "delete": signIn(reauthAndDelete: true)
        case "signOut": signOut()
        default: break
        }
    }

    private func signIn(reauthAndDelete: Bool) {
        guard let clientID = FirebaseApp.app()?.options.clientID else { fail("Sign-in is not set up."); return }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        guard let vc = Self.topViewController() else { fail("Could not open sign-in."); return }
        GIDSignIn.sharedInstance.signIn(withPresenting: vc) { [weak self] result, error in
            guard let self = self else { return }
            guard let user = result?.user, let idToken = user.idToken?.tokenString else {
                let cancelled = (error as NSError?)?.code == GIDSignInError.canceled.rawValue
                self.fail(cancelled ? "" : "Sign-in failed. Check your connection and try again.")
                return
            }
            let cred = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: user.accessToken.tokenString)
            if reauthAndDelete {
                // Deleting needs a fresh sign-in, so the user confirms who they are first.
                guard let current = Auth.auth().currentUser else { self.fail("You are not signed in."); return }
                current.reauthenticate(with: cred) { _, err in
                    if err != nil { self.fail("Could not confirm your identity."); return }
                    current.delete { err in
                        if err != nil { self.fail("Could not delete the account. Try again."); return }
                        GIDSignIn.sharedInstance.signOut()
                        self.clearWidget()
                        self.run("window.onDeleted&&window.onDeleted()")
                    }
                }
            } else {
                Auth.auth().signIn(with: cred) { _, err in
                    if err != nil { self.fail("Sign-in failed. Try again."); return }
                    self.sendStatus()
                }
            }
        }
    }

    private func signOut() {
        GIDSignIn.sharedInstance.signOut()
        try? Auth.auth().signOut()
        clearWidget()
        sendStatus()
    }

    // The session is kept on the device, so this also works offline after the first sign-in.
    private func sendStatus() {
        var obj: [String: Any] = ["signedIn": false]
        if let u = Auth.auth().currentUser {
            obj = ["signedIn": true, "uid": u.uid, "name": u.displayName ?? "", "email": u.email ?? ""]
        }
        send("onAuth", obj)
    }

    private func fail(_ message: String) { send("onAuthError", message) }

    private func send(_ fn: String, _ obj: Any) {
        guard let data = try? JSONSerialization.data(withJSONObject: obj, options: [.fragmentsAllowed]),
              let json = String(data: data, encoding: .utf8) else { return }
        run("window.\(fn)&&window.\(fn)(\(json))")
    }

    private func run(_ script: String) {
        DispatchQueue.main.async { self.web?.evaluateJavaScript(script, completionHandler: nil) }
    }

    // The home-screen widget must not keep showing numbers after sign-out or deletion.
    private func clearWidget() {
        if let s = UserDefaults(suiteName: appGroup) {
            ["pct", "a", "c", "min"].forEach { s.removeObject(forKey: $0) }
        }
        WidgetCenter.shared.reloadAllTimelines()
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var vc = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController
        while let presented = vc?.presentedViewController { vc = presented }
        return vc
    }
}

struct WebView: UIViewRepresentable {
    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "att")
        config.userContentController.add(context.coordinator, name: "auth")
        let web = WKWebView(frame: .zero, configuration: config)
        context.coordinator.auth.web = web
        web.navigationDelegate = context.coordinator
        web.uiDelegate = context.coordinator
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

    class Coordinator: NSObject, WKScriptMessageHandler, WKNavigationDelegate, WKUIDelegate {
        let auth = AuthBridge()

        func userContentController(_ c: WKUserContentController, didReceive m: WKScriptMessage) {
            // Only the app's own page may talk to the native side.
            guard m.frameInfo.request.url?.isFileURL == true else { return }
            if m.name == "auth" {
                if let d = m.body as? [String: Any], let action = d["action"] as? String { auth.handle(action) }
                return
            }
            guard let d = m.body as? [String: Any], let s = UserDefaults(suiteName: appGroup) else { return }
            s.set((d["pct"] as? NSNumber)?.doubleValue ?? 0, forKey: "pct")
            s.set((d["a"] as? NSNumber)?.intValue ?? 0, forKey: "a")
            s.set((d["c"] as? NSNumber)?.intValue ?? 0, forKey: "c")
            s.set((d["min"] as? NSNumber)?.doubleValue ?? 75, forKey: "min")
            WidgetCenter.shared.reloadAllTimelines()
        }

        // Links to the web (such as "Report a bug") open in Safari instead of replacing the app.
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            if navigationAction.navigationType == .linkActivated,
               let url = navigationAction.request.url,
               url.scheme == "https" || url.scheme == "http" || url.scheme == "mailto" {
                UIApplication.shared.open(url)
                decisionHandler(.cancel)
            } else {
                decisionHandler(.allow)
            }
        }

        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                     for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            if let url = navigationAction.request.url { UIApplication.shared.open(url) }
            return nil
        }
    }
}
