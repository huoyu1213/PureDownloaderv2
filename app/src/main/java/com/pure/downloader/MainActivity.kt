package com.pure.downloader

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PureDownloaderApp()
            }
        }
    }
}

@Composable
fun PureDownloaderApp() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val sniffedUrls = remember { mutableStateListOf<String>() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("直链/磁力") },
                    icon = {}
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("网页嗅探") },
                    icon = {}
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> DirectAndMagnetTab(
                    onStartDirectDownload = { url ->
                        startSystemDownload(context, url)
                    },
                    onOpenMagnet = { magnet ->
                        openMagnetLink(context, magnet)
                    }
                )
                1 -> WebSnifferTab(
                    sniffedUrls = sniffedUrls,
                    onDownloadSniffed = { url ->
                        startSystemDownload(context, url)
                    }
                )
            }
        }
    }
}

@Composable
fun DirectAndMagnetTab(
    onStartDirectDownload: (String) -> Unit,
    onOpenMagnet: (String) -> Unit
) {
    var inputLink by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("纯净下载器（无广告）", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = inputLink,
            onValueChange = { inputLink = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("粘贴直链或 magnet:? 磁力链接") },
            singleLine = false,
            maxLines = 4
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val trimmed = inputLink.trim()
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        onStartDirectDownload(trimmed)
                    } else if (trimmed.startsWith("magnet:?")) {
                        onOpenMagnet(trimmed)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("开始下载 / 唤起磁力")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebSnifferTab(
    sniffedUrls: MutableList<String>,
    onDownloadSniffed: (String) -> Unit
) {
    var pageUrl by remember { mutableStateOf("https://www.example.com") }
    var activeUrl by remember { mutableStateOf("https://www.example.com") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = pageUrl,
                onValueChange = { pageUrl = it },
                modifier = Modifier.weight(1f),
                label = { Text("输入网页地址") },
                singleLine = true
            )
            Button(onClick = {
                val target = pageUrl.trim()
                if (target.startsWith("http://") || target.startsWith("https://")) {
                    activeUrl = target
                }
            }) {
                Text("前往")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString().orEmpty()
                            if (reqUrl.contains(".mp4") || reqUrl.contains(".m3u8") || reqUrl.contains(".mp3")) {
                                view?.post {
                                    if (!sniffedUrls.contains(reqUrl)) {
                                        sniffedUrls.add(0, reqUrl)
                                    }
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }
                    loadUrl(activeUrl)
                }
            },
            update = { webView ->
                if (webView.url != activeUrl) {
                    webView.loadUrl(activeUrl)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text("嗅探到的媒体链接（${sniffedUrls.size}）", style = MaterialTheme.typography.titleMedium)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(sniffedUrls) { mediaUrl ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(mediaUrl, maxLines = 2)
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(onClick = { onDownloadSniffed(mediaUrl) }) {
                            Text("下载此资源")
                        }
                    }
                }
            }
        }
    }
}

private fun startSystemDownload(context: Context, url: String) {
    try {
        val uri = Uri.parse(url)
        val fileName = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "download_file"
        val request = DownloadManager.Request(uri)
            .setTitle(fileName)
            .setDescription("纯净下载器正在下载")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "已加入下载队列：$fileName", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "下载失败：${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun openMagnetLink(context: Context, magnetUrl: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnetUrl))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "未找到支持磁力链接的下载引擎", Toast.LENGTH_LONG).show()
    }
}