package com.downflow.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.downflow.app.browser.DownloadInterceptor
import com.downflow.app.ui.DownloadsViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    var addressBar by remember { mutableStateOf("https://") }
    var currentUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var snackMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snackMsg) {
        snackMsg?.let {
            snackbarHostState.showSnackbar(it)
            snackMsg = null
        }
    }

    fun navigateTo(url: String) {
        val normalized = when {
            url.startsWith("http://") || url.startsWith("https://") || url.startsWith("ftp://") -> url
            url.contains(".") && !url.contains(" ") -> "https://$url"
            else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(url, "UTF-8")}"
        }
        webViewRef?.loadUrl(normalized)
        pageError = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = addressBar,
                            onValueChange = { addressBar = it },
                            modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(onGo = { navigateTo(addressBar) }),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { webViewRef?.goBack() },
                            enabled = webViewRef?.canGoBack() == true) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { webViewRef?.goForward() },
                            enabled = webViewRef?.canGoForward() == true) {
                            Icon(Icons.Default.ArrowForward, "Forward")
                        }
                        IconButton(onClick = {
                            if (isLoading) webViewRef?.stopLoading()
                            else webViewRef?.reload()
                        }) {
                            Icon(
                                if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                if (isLoading) "Stop" else "Reload"
                            )
                        }
                        // Download current URL button
                        IconButton(onClick = {
                            val url = currentUrl.ifBlank { addressBar }
                            if (url.isNotBlank()) {
                                viewModel.addDownload(url)
                                snackMsg = "Added: $url"
                            }
                        }) {
                            Icon(Icons.Default.Download, "Download current URL")
                        }
                    }
                )
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).also { wv ->
                        webViewRef = wv
                        wv.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            userAgentString = wv.settings.userAgentString
                            // Do NOT setAllowFileAccess to false to avoid local file leakage
                            allowFileAccess = false
                            allowContentAccess = false
                        }

                        // ── Download listener ─────────────────────────────
                        wv.setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                            when {
                                DownloadInterceptor.isMagnetLink(url) -> {
                                    viewModel.addDownload(url)
                                    snackMsg = "Magnet link added to queue"
                                }
                                DownloadInterceptor.isTorrentUrl(url) ||
                                DownloadInterceptor.isDirectDownload(url, mimetype) -> {
                                    viewModel.addDownload(url)
                                    snackMsg = "Download added: ${url.substringAfterLast('/').take(40)}"
                                }
                                else -> {
                                    snackMsg = "Cannot intercept this download. Copy the URL and paste it manually."
                                }
                            }
                        }

                        wv.webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                isLoading = true
                                currentUrl = url
                                addressBar = url
                                pageError = null
                            }
                            override fun onPageFinished(view: WebView, url: String) {
                                isLoading = false
                                currentUrl = url
                                addressBar = url
                            }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) {
                                    pageError = "Error ${error.errorCode}: ${error.description}"
                                    isLoading = false
                                }
                            }
                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                if (DownloadInterceptor.isMagnetLink(url)) {
                                    viewModel.addDownload(url)
                                    snackMsg = "Magnet link added to queue"
                                    return true
                                }
                                return false
                            }
                        }

                        wv.webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress / 100f
                                isLoading = newProgress < 100
                            }
                        }

                        wv.loadUrl("https://www.google.com")
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (pageError != null) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = pageError!!,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
