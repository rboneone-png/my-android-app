package com.rboneone.stockrecord.network

import java.net.HttpURLConnection
import java.net.URL

object Http {
    /** 回傳內容；伺服器回應錯誤碼時回傳 null，連不上網路時拋出 IOException */
    fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        try {
            if (conn.responseCode !in 200..299) return null
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** 以表單格式 POST，回傳內容；錯誤處理同 get() */
    fun postForm(url: String, form: Map<String, String>): String? {
        val body = form.entries.joinToString("&") { (k, v) ->
            java.net.URLEncoder.encode(k, "UTF-8") + "=" + java.net.URLEncoder.encode(v, "UTF-8")
        }.toByteArray(Charsets.UTF_8)
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
        try {
            conn.outputStream.use { it.write(body) }
            if (conn.responseCode !in 200..299) return null
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
