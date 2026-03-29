import AuthFeature
import Observation
import SwiftUI
import WebKit

struct YandexMusicAuthWebViewScreen: View {
    let request: YandexAuthorizationRequest
    let onClose: () -> Void
    let onAuthorizationCallback: (URL) -> Void
    let onLaunchFailure: () -> Void

    @State private var controller = YandexMusicAuthWebViewController()

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                YandexMusicOAuthWebView(
                    request: request,
                    controller: controller,
                    onAuthorizationCallback: onAuthorizationCallback,
                    onLaunchFailure: onLaunchFailure
                )
                .ignoresSafeArea(edges: .bottom)

                if controller.isLoading {
                    ProgressView()
                        .padding(.top, 12)
                }
            }
            .navigationTitle(controller.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Закрыть", action: onClose)
                }

                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        if controller.canGoBack {
                            controller.goBack()
                        } else {
                            controller.reload()
                        }
                    } label: {
                        Image(systemName: controller.canGoBack ? "chevron.backward" : "arrow.clockwise")
                    }
                }
            }
        }
    }
}

@MainActor
@Observable
private final class YandexMusicAuthWebViewController {
    var title = "Yandex ID"
    var isLoading = true
    var canGoBack = false

    private weak var webView: WKWebView?

    func attach(webView: WKWebView) {
        self.webView = webView
        sync(with: webView)
    }

    func sync(with webView: WKWebView) {
        title = webView.title?.nilIfBlank ?? "Yandex ID"
        isLoading = webView.isLoading
        canGoBack = webView.canGoBack
    }

    func reload() {
        webView?.reload()
    }

    func goBack() {
        webView?.goBack()
    }
}

private struct YandexMusicOAuthWebView: UIViewRepresentable {
    let request: YandexAuthorizationRequest
    let controller: YandexMusicAuthWebViewController
    let onAuthorizationCallback: (URL) -> Void
    let onLaunchFailure: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(
            request: request,
            controller: controller,
            onAuthorizationCallback: onAuthorizationCallback,
            onLaunchFailure: onLaunchFailure
        )
    }

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView(frame: .zero)
        webView.navigationDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = true
        controller.attach(webView: webView)
        webView.load(URLRequest(url: request.url))
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.request = request
        controller.attach(webView: webView)
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var request: YandexAuthorizationRequest

        private let controller: YandexMusicAuthWebViewController
        private let onAuthorizationCallback: (URL) -> Void
        private let onLaunchFailure: () -> Void

        init(
            request: YandexAuthorizationRequest,
            controller: YandexMusicAuthWebViewController,
            onAuthorizationCallback: @escaping (URL) -> Void,
            onLaunchFailure: @escaping () -> Void
        ) {
            self.request = request
            self.controller = controller
            self.onAuthorizationCallback = onAuthorizationCallback
            self.onLaunchFailure = onLaunchFailure
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            if consumeCallbackURL(navigationAction.request.url) {
                decisionHandler(.cancel)
                return
            }

            decisionHandler(.allow)
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            controller.sync(with: webView)
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            controller.sync(with: webView)

            guard
                let currentURL = webView.url?.absoluteString,
                currentURL.hasPrefix(request.callbackURLPrefix)
            else {
                return
            }

            webView.evaluateJavaScript("(function() { return window.location.href; })();") { value, _ in
                guard let rawValue = value as? String else {
                    return
                }
                _ = self.consumeCallbackURL(URL(string: rawValue.normalizedJavaScriptURLString))
            }
        }

        func webView(
            _ webView: WKWebView,
            didFail navigation: WKNavigation!,
            withError error: Error
        ) {
            handleFailure(error, webView: webView)
        }

        func webView(
            _ webView: WKWebView,
            didFailProvisionalNavigation navigation: WKNavigation!,
            withError error: Error
        ) {
            handleFailure(error, webView: webView)
        }

        private func handleFailure(_ error: Error, webView: WKWebView) {
            controller.sync(with: webView)

            let nsError = error as NSError
            if nsError.domain == NSURLErrorDomain,
               nsError.code == NSURLErrorCancelled {
                return
            }

            onLaunchFailure()
        }

        private func consumeCallbackURL(_ url: URL?) -> Bool {
            guard
                let url,
                url.absoluteString.hasPrefix(request.callbackURLPrefix),
                request.containsCallbackPayload(url)
            else {
                return false
            }

            onAuthorizationCallback(url)
            return true
        }
    }
}

private extension YandexAuthorizationRequest {
    func containsCallbackPayload(_ candidateURL: URL) -> Bool {
        let fragment = candidateURL.fragment ?? ""
        return fragment.contains("access_token=") || fragment.contains("error=")
    }
}

private extension String {
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    var normalizedJavaScriptURLString: String {
        self
            .removeSurroundingQuotes()
            .replacingOccurrences(of: "\\u003D", with: "=")
            .replacingOccurrences(of: "\\u0026", with: "&")
            .replacingOccurrences(of: "\\/", with: "/")
            .replacingOccurrences(of: "\\\\", with: "\\")
    }

    private func removeSurroundingQuotes() -> String {
        guard hasPrefix("\""), hasSuffix("\""), count >= 2 else {
            return self
        }
        return String(dropFirst().dropLast())
    }
}
