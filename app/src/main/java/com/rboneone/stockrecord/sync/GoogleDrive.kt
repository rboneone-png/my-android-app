package com.rboneone.stockrecord.sync

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * 透過 Google Drive 的「App 專用資料夾」（appDataFolder）存放備份。
 * 這個資料夾在 Drive 網頁上看不到，只有這個 App 能讀寫。
 */
object GoogleDrive {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val API = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE)))
        .build()

    /** 第一次需要使用者同意時，結果會帶有 pendingIntent */
    suspend fun authorize(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request()).await()

    /** 已授權時取得 access token；需要使用者同意時回傳 null */
    suspend fun token(context: Context): String? {
        val result = authorize(context)
        return if (result.hasResolution()) null else result.accessToken
    }

    suspend fun upload(token: String, fileName: String, content: String) = withContext(Dispatchers.IO) {
        val old = findFileIds(token, fileName)
        val boundary = "stockrecord-" + UUID.randomUUID()
        val metadata = JSONObject().put("name", fileName).put("parents", JSONArray().put("appDataFolder"))
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(content)
            append("\r\n--$boundary--\r\n")
        }
        request(token, "POST", UPLOAD, body, "multipart/related; boundary=$boundary")
        // 新檔案上傳成功後才刪除舊的，避免上傳失敗時資料遺失
        old.forEach { request(token, "DELETE", "$API/$it", null, null) }
    }

    /** 回傳備份內容；雲端沒有備份時回傳 null */
    suspend fun download(token: String, fileName: String): String? = withContext(Dispatchers.IO) {
        val id = findFileIds(token, fileName).firstOrNull() ?: return@withContext null
        request(token, "GET", "$API/$id?alt=media", null, null)
    }

    private fun findFileIds(token: String, fileName: String): List<String> {
        val escaped = fileName.replace("\\", "\\\\").replace("'", "\\'")
        val q = URLEncoder.encode("name = '$escaped'", "UTF-8")
        val json = request(
            token, "GET",
            "$API?spaces=appDataFolder&q=$q&orderBy=modifiedTime%20desc&fields=files(id)",
            null, null,
        )
        val files = JSONObject(json).optJSONArray("files") ?: return emptyList()
        return (0 until files.length()).map { files.getJSONObject(it).getString("id") }
    }

    private fun request(token: String, method: String, url: String, body: String?, contentType: String?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", contentType)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Google Drive 回應 $code ${err.take(200)}")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
