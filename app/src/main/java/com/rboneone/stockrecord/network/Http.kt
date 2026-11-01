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
}
