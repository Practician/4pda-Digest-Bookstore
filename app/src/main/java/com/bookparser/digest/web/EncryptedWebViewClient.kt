package com.bookparser.digest.web

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * EncryptedWebViewClient из Practician/Bookstore:
 * Расшифровывает файлы .enc из assets на лету с помощью XOR-ключа (или отдает обычный файл из assets, если .enc не найден).
 */
open class EncryptedWebViewClient(private val context: Context) : WebViewClient() {

    private val KEY = "MySecretKey123".toByteArray()

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null

        if (url.startsWith("https://app-assets/")) {
            val fileName = url.substringAfter("https://app-assets/").substringBefore("?").substringBefore("#")
            return getEncryptedAsset(fileName)
        }

        if (url.contains("android_asset/")) {
            val fileName = url.substringAfter("android_asset/").substringBefore("?").substringBefore("#")
            return getEncryptedAsset(fileName)
        }

        return super.shouldInterceptRequest(view, request)
    }

    private fun getEncryptedAsset(fileName: String): WebResourceResponse? {
        val mimeType = getMimeType(fileName)
        // 1. Сначала пробуем расшифровать .enc
        try {
            val encryptedFileName = if (fileName.endsWith(".enc")) fileName else "$fileName.enc"
            val inputStream = context.assets.open(encryptedFileName)
            val decryptedData = decrypt(inputStream)
            return WebResourceResponse(mimeType, "UTF-8", ByteArrayInputStream(decryptedData))
        } catch (_: Exception) {
            // 2. Fallback на обычный нешифрованный файл из assets
            return try {
                val plainName = fileName.removeSuffix(".enc")
                val inputStream = context.assets.open(plainName)
                WebResourceResponse(mimeType, "UTF-8", inputStream)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun decrypt(inputStream: InputStream): ByteArray {
        val encryptedData = inputStream.use { it.readBytes() }
        val decryptedData = ByteArray(encryptedData.size)
        for (i in encryptedData.indices) {
            decryptedData[i] = (encryptedData[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
        }
        return decryptedData
    }

    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".html") || fileName.endsWith(".html.enc") -> "text/html"
            fileName.endsWith(".js") || fileName.endsWith(".js.enc") -> "application/javascript"
            fileName.endsWith(".css") || fileName.endsWith(".css.enc") -> "text/css"
            fileName.endsWith(".png") || fileName.endsWith(".png.enc") -> "image/png"
            fileName.endsWith(".jpg") || fileName.endsWith(".jpg.enc") || fileName.endsWith(".jpeg") -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }
}
