package com.mplayeraudio.feature.auth.yamusic

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.mplayeraudio.core.domain.yandexauth.YandexAuthorizationRequest
import com.mplayeraudio.core.domain.yandexauth.YandexAuthorizationResponseType
import com.mplayeraudio.core.ui.components.MultiplayerSurface
import com.mplayeraudio.core.ui.components.MultiplayerText
import com.mplayeraudio.core.ui.theme.MultiplayerTheme
import com.mplayeraudio.feature.auth.R

@Composable
@Suppress("LongMethod")
fun YandexMusicAuthWebViewScreen(
    request: YandexAuthorizationRequest,
    isAuthorizing: Boolean,
    onCloseClick: () -> Unit,
    onAuthorizationCallback: (String) -> Unit,
    onLaunchFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spacing = MultiplayerTheme.spacing
    val colors = MultiplayerTheme.colors
    val typography = MultiplayerTheme.typography
    val currentRequest by rememberUpdatedState(request)
    val currentOnAuthorizationCallback by rememberUpdatedState(onAuthorizationCallback)
    val currentOnLaunchFailure by rememberUpdatedState(onLaunchFailure)
    var pageTitle by remember(request.url) { mutableStateOf<String?>(null) }
    var isPageLoading by remember(request.url) { mutableStateOf(true) }
    var hasDeliveredCallback by remember(request.url) { mutableStateOf(false) }
    val webView = remember(request.url) { WebView(context) }

    BackHandler(enabled = true) {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            onCloseClick()
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    MultiplayerSurface(
        modifier = modifier.fillMaxSize(),
        color = colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .systemBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MultiplayerText(
                    text = stringResource(R.string.auth_webview_close),
                    style = typography.label,
                    color = colors.brandVisualPrimary,
                    modifier = Modifier.clickable(onClick = onCloseClick),
                )

                Spacer(modifier = Modifier.weight(1f))

                MultiplayerText(
                    text = pageTitle ?: stringResource(R.string.auth_webview_title),
                    style = typography.title,
                    color = colors.textPrimary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(WebViewTitleWidthFraction),
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = {
                        webView.apply {
                            configureForYandexOAuth(
                                requestProvider = { currentRequest },
                                onPageTitleChanged = { title -> pageTitle = title },
                                onLoadingStateChanged = { loading -> isPageLoading = loading },
                                onAuthorizationCallback = { callbackUrl ->
                                    if (hasDeliveredCallback) {
                                        return@configureForYandexOAuth
                                    }
                                    hasDeliveredCallback = true
                                    currentOnAuthorizationCallback(callbackUrl)
                                },
                            )
                        }
                    },
                    update = { view ->
                        if (view.url.isNullOrBlank()) {
                            try {
                                view.loadUrl(currentRequest.url)
                            } catch (_: Throwable) {
                                currentOnLaunchFailure()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (isPageLoading || isAuthorizing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.background.copy(alpha = 0.24f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.brandVisualPrimary)
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForYandexOAuth(
    requestProvider: () -> YandexAuthorizationRequest,
    onPageTitleChanged: (String?) -> Unit,
    onLoadingStateChanged: (Boolean) -> Unit,
    onAuthorizationCallback: (String) -> Unit,
) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(this, true)

    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadsImagesAutomatically = true
        javaScriptCanOpenWindowsAutomatically = false
        mediaPlaybackRequiresUserGesture = false
        builtInZoomControls = false
        displayZoomControls = false
        setSupportZoom(false)
    }

    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            onLoadingStateChanged(newProgress < WebViewLoadCompleteProgress)
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            onPageTitleChanged(title)
        }
    }

    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
        ): Boolean {
            val url = request?.url?.toString()
            return consumeCallbackUrl(
                candidateUrl = url,
                request = requestProvider(),
                onAuthorizationCallback = onAuthorizationCallback,
            )
        }

        override fun onPageStarted(
            view: WebView?,
            url: String?,
            favicon: android.graphics.Bitmap?,
        ) {
            onLoadingStateChanged(true)
            consumeCallbackUrl(
                candidateUrl = url,
                request = requestProvider(),
                onAuthorizationCallback = onAuthorizationCallback,
            )
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            onLoadingStateChanged(false)
            val currentRequest = requestProvider()
            val shouldInspectWindowLocation = view != null &&
                currentRequest.responseType == YandexAuthorizationResponseType.Token &&
                !url.isNullOrBlank() &&
                url.startsWith(currentRequest.callbackUrlPrefix)
            if (shouldInspectWindowLocation) {
                view.evaluateJavascript("(function() { return window.location.href; })();") { rawValue ->
                    val normalizedUrl = rawValue
                        .removeSurrounding("\"")
                        .replace("\\u003D", "=")
                        .replace("\\u0026", "&")
                        .replace("\\/", "/")
                        .replace("\\\\", "\\")
                    consumeCallbackUrl(
                        candidateUrl = normalizedUrl,
                        request = currentRequest,
                        onAuthorizationCallback = onAuthorizationCallback,
                    )
                }
            }
        }

        override fun doUpdateVisitedHistory(
            view: WebView?,
            url: String?,
            isReload: Boolean,
        ) {
            consumeCallbackUrl(
                candidateUrl = url,
                request = requestProvider(),
                onAuthorizationCallback = onAuthorizationCallback,
            )
        }
    }
}

private fun consumeCallbackUrl(
    candidateUrl: String?,
    request: YandexAuthorizationRequest,
    onAuthorizationCallback: (String) -> Unit,
): Boolean {
    val shouldConsume = !candidateUrl.isNullOrBlank() &&
        candidateUrl.startsWith(request.callbackUrlPrefix) &&
        request.containsCallbackPayload(candidateUrl)
    if (shouldConsume) {
        onAuthorizationCallback(candidateUrl)
    }
    return shouldConsume
}

private fun YandexAuthorizationRequest.containsCallbackPayload(candidateUrl: String): Boolean {
    return when (responseType) {
        YandexAuthorizationResponseType.Code -> {
            val parsedUri = Uri.parse(candidateUrl)
            parsedUri.getQueryParameter("code") != null ||
                parsedUri.getQueryParameter("error") != null
        }

        YandexAuthorizationResponseType.Token -> {
            val fragment = Uri.parse(candidateUrl).fragment.orEmpty()
            fragment.contains("access_token=") || fragment.contains("error=")
        }
    }
}

private const val WebViewLoadCompleteProgress: Int = 100
private const val WebViewTitleWidthFraction: Float = 0.68f
