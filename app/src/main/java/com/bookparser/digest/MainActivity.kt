package com.bookparser.digest

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bookparser.digest.web.AuthManager
import com.bookparser.digest.web.EncryptedWebViewClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DigestApp"
        private const val PREFS_NAME = "4pda_auth_prefs"
        private const val KEY_COOKIES = "saved_cookies"
        private const val KEY_USERNAME = "saved_username"
        private const val URL_DIGEST_ASSET = "file:///android_asset/4pda_digest_mobile.html"
        private const val URL_FORUM_BASE = "https://4pda.to/forum/index.php"
        private const val USER_AGENT_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    private lateinit var webViewParser: WebView
    private lateinit var webViewForum: WebView
    private lateinit var forumTopBar: LinearLayout
    private lateinit var btnForumBackToDigest: Button
    private lateinit var tvForumUrl: TextView

    private lateinit var logPanel: LinearLayout
    private lateinit var logTextView: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var fabLogToggle: FloatingActionButton

    private lateinit var authManager: AuthManager
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var isWaitingForLogin = false

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback ?: return@registerForActivityResult
        filePathCallback = null
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
                ?: data?.data?.let { arrayOf(it) }
            callback.onReceiveValue(uris)
        } else {
            callback.onReceiveValue(null)
        }
    }

    private val logListener: (String) -> Unit = { _ ->
        runOnUiThread {
            if (::logTextView.isInitialized && logPanel.visibility == View.VISIBLE) {
                logTextView.text = AppLogger.getAll()
                logScrollView.post { logScrollView.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authManager = AuthManager(this)

        webViewParser = findViewById(R.id.webViewParser)
        webViewForum = findViewById(R.id.webViewForum)
        forumTopBar = findViewById(R.id.forumTopBar)
        btnForumBackToDigest = findViewById(R.id.btnForumBackToDigest)
        tvForumUrl = findViewById(R.id.tvForumUrl)

        logPanel = findViewById(R.id.logPanel)
        logTextView = findViewById(R.id.logTextView)
        logScrollView = findViewById(R.id.logScrollView)
        fabLogToggle = findViewById(R.id.fabLogToggle)

        setupLogPanel()
        setupForumWebView()
        setupParserWebView()
        setupBackNavigation()

        AppLogger.addListener(logListener)
        AppLogger.i(TAG, "Приложение запущено (4PDA Digest v6.4)")
    }

    override fun onDestroy() {
        AppLogger.removeListener(logListener)
        super.onDestroy()
    }

    private fun setupLogPanel() {
        btnForumBackToDigest.setOnClickListener {
            showParserWebView()
            syncAuthToWebView()
        }

        fabLogToggle.setOnClickListener {
            if (logPanel.visibility == View.VISIBLE) {
                logPanel.visibility = View.GONE
            } else {
                logTextView.text = AppLogger.getAll()
                logPanel.visibility = View.VISIBLE
                logScrollView.post { logScrollView.fullScroll(View.FOCUS_DOWN) }
            }
        }

        findViewById<Button>(R.id.btnCopyLog).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("AppLogs", AppLogger.getAll()))
            Toast.makeText(this, "Логи скопированы", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            AppLogger.clear()
            logTextView.text = ""
        }

        findViewById<Button>(R.id.btnCloseLog).setOnClickListener {
            logPanel.visibility = View.GONE
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupParserWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webViewParser, true)

        webViewParser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webViewParser.webViewClient = object : EncryptedWebViewClient(this) {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                checkExistingAuth()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString() ?: return false
                // Ссылки на 4pda.to из предпросмотра открываем во встроенном форуме или внешнем браузере
                if (target.startsWith("http://") || target.startsWith("https://")) {
                    if (target.contains("4pda.to")) {
                        showForumWebView()
                        webViewForum.loadUrl(target)
                        return true
                    } else {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                        return true
                    }
                }
                return false
            }
        }

        webViewParser.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/plain"
                    }
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    false
                }
            }
        }

        webViewParser.addJavascriptInterface(ParserJsInterface(), "AndroidBridge")
        webViewParser.loadUrl(URL_DIGEST_ASSET)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupForumWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webViewForum, true)

        webViewForum.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = USER_AGENT_DESKTOP
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            builtInZoomControls = true
            displayZoomControls = false
        }

        webViewForum.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url ?: return
                tvForumUrl.text = url

                val cookies = CookieManager.getInstance().getCookie(url)
                    ?: CookieManager.getInstance().getCookie("https://4pda.to")
                    ?: ""
                val memberCookie = cookies.split(";").map { it.trim() }.find { it.startsWith("member_id=") }
                val passHashCookie = cookies.split(";").map { it.trim() }.find { it.startsWith("pass_hash=") }

                val hasValidSession = memberCookie != null &&
                        memberCookie != "member_id=0" &&
                        memberCookie != "member_id=" &&
                        passHashCookie != null &&
                        passHashCookie != "pass_hash=0"

                if (hasValidSession) {
                    saveCookies(cookies)
                    CookieManager.getInstance().flush()

                    webViewForum.evaluateJavascript(
                        """
                        (function() {
                            var userMenu = document.querySelector('#user-menu');
                            var username = '';
                            if (userMenu) {
                                var nameElement = userMenu.querySelector('.name, span, a');
                                if (nameElement) username = nameElement.innerText.trim();
                            }
                            if (!username) {
                                var links = document.querySelectorAll('a[href*="showuser"]');
                                for (var i = 0; i < links.length; i++) {
                                    var t = links[i].innerText.trim();
                                    if (t && t.length < 40 && !/профиль|выход/i.test(t)) {
                                        username = t;
                                        break;
                                    }
                                }
                            }
                            return username;
                        })();
                        """.trimIndent()
                    ) { result ->
                        val extracted = result?.removeSurrounding("\"")?.takeIf {
                            it.isNotBlank() && it != "null"
                        }
                        if (extracted != null) {
                            saveUsername(extracted)
                        }
                        val finalUsername = extracted ?: getSavedUsername() ?: "4PDA User"
                        AppLogger.i(TAG, "Авторизация 4PDA подтверждена: $finalUsername")
                        parserCallback("window.onAuthStateChanged(true, '${finalUsername.escapeJs()}')")

                        if (isWaitingForLogin && !url.contains("act=auth") && !url.contains("act=login")) {
                            isWaitingForLogin = false
                            Toast.makeText(
                                this@MainActivity,
                                "Вход выполнен: $finalUsername",
                                Toast.LENGTH_SHORT
                            ).show()
                            showParserWebView()
                        }
                    }
                }
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    logPanel.visibility == View.VISIBLE -> {
                        logPanel.visibility = View.GONE
                    }
                    webViewForum.visibility == View.VISIBLE && webViewForum.canGoBack() -> {
                        webViewForum.goBack()
                    }
                    webViewForum.visibility == View.VISIBLE -> {
                        showParserWebView()
                        syncAuthToWebView()
                    }
                    webViewParser.canGoBack() -> {
                        webViewParser.goBack()
                    }
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun checkExistingAuth() {
        val savedCookies = getSavedCookies()
        if (!savedCookies.isNullOrBlank()) {
            restoreCookies(savedCookies)
        }
        syncAuthToWebView()
    }

    private fun syncAuthToWebView() {
        val loggedIn = authManager.isLoggedIn()
        val username = if (loggedIn) (getSavedUsername() ?: "4PDA User") else ""
        parserCallback("window.onAuthStateChanged($loggedIn, '${username.escapeJs()}')")
    }

    private fun saveCookies(cookies: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_COOKIES, cookies)
            .apply()
    }

    private fun getSavedCookies(): String? {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_COOKIES, null)
    }

    private fun restoreCookies(cookies: String) {
        val cm = CookieManager.getInstance()
        cookies.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { cookiePair ->
            cm.setCookie("https://4pda.to", "$cookiePair; Domain=.4pda.to; Path=/")
        }
        cm.flush()
    }

    private fun saveUsername(username: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_USERNAME, username)
            .apply()
    }

    private fun getSavedUsername(): String? {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_USERNAME, null)
    }

    private fun clearCookies() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply()
    }

    private fun showForumWebView() {
        forumTopBar.visibility = View.VISIBLE
        webViewForum.visibility = View.VISIBLE
        webViewParser.visibility = View.GONE
    }

    private fun showParserWebView() {
        forumTopBar.visibility = View.GONE
        webViewForum.visibility = View.GONE
        webViewParser.visibility = View.VISIBLE
    }

    private fun parserCallback(jsCode: String) {
        runOnUiThread {
            if (::webViewParser.isInitialized) {
                webViewParser.evaluateJavascript(jsCode, null)
            }
        }
    }

    private fun String.escapeJs(): String {
        return this
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    /**
     * JavaScript-мост AndroidBridge (полная совместимость с 4pda_digest_mobile.html и Practician/Bookstore).
     */
    inner class ParserJsInterface {

        @JavascriptInterface
        fun getAuthState(): String {
            val loggedIn = authManager.isLoggedIn()
            val username = if (loggedIn) (getSavedUsername() ?: "4PDA User") else ""
            return JSONObject().apply {
                put("loggedIn", loggedIn)
                put("username", username)
            }.toString()
        }

        @JavascriptInterface
        fun openForumUrl(url: String) {
            runOnUiThread {
                AppLogger.i(TAG, "Открытие страницы форума: $url")
                if (url.contains("act=auth") || url.contains("act=login")) {
                    isWaitingForLogin = true
                }
                showForumWebView()
                webViewForum.loadUrl(url.ifBlank { URL_FORUM_BASE })
            }
        }

        @JavascriptInterface
        fun logoutFromForum() {
            runOnUiThread {
                AppLogger.i(TAG, "Выход из аккаунта 4PDA")
                clearCookies()
                parserCallback("window.onAuthStateChanged(false, '')")
                Toast.makeText(this@MainActivity, "Выход выполнен", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun goBack() {
            runOnUiThread {
                if (webViewForum.visibility == View.VISIBLE) {
                    showParserWebView()
                    syncAuthToWebView()
                } else if (webViewParser.canGoBack()) {
                    webViewParser.goBack()
                }
            }
        }

        /**
         * Нативный загрузчик страниц 4PDA с куками приложения (вызывается из bridgeFetchBytes в 4pda_digest_mobile.html).
         * Возвращает сырые байты ответа в Base64 через window.onForumPage(callbackId, ok, b64Bytes),
         * чтобы JS-движок BKT корректно декодировал windows-1251 / UTF-8.
         */
        @JavascriptInterface
        fun fetchForumPage(url: String, callbackId: String) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val cookies = CookieManager.getInstance().getCookie(url)
                        ?: CookieManager.getInstance().getCookie("https://4pda.to")
                        ?: getSavedCookies()
                        ?: ""

                    val reqBuilder = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT_DESKTOP)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")

                    if (cookies.isNotBlank()) {
                        reqBuilder.header("Cookie", cookies)
                    }

                    httpClient.newCall(reqBuilder.build()).execute().use { response ->
                        val bytes = response.body?.bytes()
                        if (response.isSuccessful && bytes != null && bytes.isNotEmpty()) {
                            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            AppLogger.d(TAG, "fetchForumPage OK (${bytes.size} B): $url")
                            parserCallback("window.onForumPage('${callbackId.escapeJs()}', true, '$b64')")
                        } else {
                            AppLogger.w(TAG, "fetchForumPage HTTP ${response.code}: $url")
                            parserCallback("window.onForumPage('${callbackId.escapeJs()}', false, null)")
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "fetchForumPage ошибка: $url", e)
                    parserCallback("window.onForumPage('${callbackId.escapeJs()}', false, null)")
                }
            }
        }

        /**
         * Сохранение готового BBCode-поста в папку Загрузки (Downloads).
         */
        @JavascriptInterface
        fun saveTxtFile(fileName: String, content: String) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val safeName = fileName.ifBlank { "novinki_knigohranilisha_post.txt" }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                            put(MediaStore.Downloads.MIME_TYPE, "text/plain; charset=utf-8")
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(content.toByteArray(Charsets.UTF_8))
                            }
                        }
                    } else {
                        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (!dir.exists()) dir.mkdirs()
                        val file = File(dir, safeName)
                        FileOutputStream(file).use { out ->
                            out.write(content.toByteArray(Charsets.UTF_8))
                        }
                    }
                    AppLogger.i(TAG, "Файл сохранён в Загрузки: $safeName")
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Ошибка сохранения файла $fileName", e)
                }
            }
        }

        @JavascriptInterface
        fun saveSetting(key: String, value: String) {
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            prefs.edit().putString(key, value).apply()
        }

        @JavascriptInterface
        fun getSetting(key: String, defaultVal: String): String {
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            return prefs.getString(key, null) ?: defaultVal
        }

        @JavascriptInterface
        fun setKeepScreenOn(keepOn: Boolean) {
            runOnUiThread {
                if (keepOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        @JavascriptInterface
        fun nativeRequestWithProxy(
            url: String,
            method: String,
            body: String,
            headersJson: String,
            callbackJsId: String,
            proxyType: String,
            proxyHost: String,
            proxyPort: Int
        ) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val clientBuilder = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)

                    if (proxyType.isNotEmpty() && proxyHost.isNotEmpty() && proxyPort > 0) {
                        val proxy = if (proxyType.equals("socks5", ignoreCase = true)) {
                            Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyHost, proxyPort))
                        } else {
                            Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort))
                        }
                        clientBuilder.proxy(proxy)
                    }

                    val client = clientBuilder.build()
                    val requestBuilder = Request.Builder().url(url)

                    if (headersJson.isNotEmpty()) {
                        val headersObj = JSONObject(headersJson)
                        headersObj.keys().forEach { key ->
                            if (!key.equals("Accept-Encoding", ignoreCase = true)) {
                                requestBuilder.header(key, headersObj.getString(key))
                            }
                        }
                    }

                    if (method.equals("POST", ignoreCase = true)) {
                        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                        requestBuilder.post(body.toRequestBody(mediaType))
                    }

                    client.newCall(requestBuilder.build()).execute().use { response ->
                        val respBody = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            val b64 = Base64.encodeToString(
                                respBody.toByteArray(Charsets.UTF_8),
                                Base64.NO_WRAP
                            )
                            parserCallback("window.onNativeResponse('${callbackJsId.escapeJs()}', null, '$b64')")
                        } else {
                            val err = "HTTP ${response.code}: $respBody"
                            val b64Err = Base64.encodeToString(
                                err.toByteArray(Charsets.UTF_8),
                                Base64.NO_WRAP
                            )
                            parserCallback("window.onNativeResponse('${callbackJsId.escapeJs()}', '$b64Err', null)")
                        }
                    }
                } catch (e: Exception) {
                    val b64Err = Base64.encodeToString(
                        (e.message ?: "Error").toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP
                    )
                    parserCallback("window.onNativeResponse('${callbackJsId.escapeJs()}', '$b64Err', null)")
                }
            }
        }
    }
}
