package com.papa.xausent

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object OandaClient {
    private const val ENDPOINT = "https://api-fxpractice.oanda.com/v3/instruments/XAU_USD/candles"

    fun fetch(context: Context): MarketAnalysis? {
        val credentials = SecureConfig.read(context) ?: return null
        val connection = (URL("$ENDPOINT?granularity=M5&count=120&price=M").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Authorization", "Bearer ${credentials.token}")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("OANDA HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val candles = JSONArray(org.json.JSONObject(body).getJSONArray("candles").toString())
                .let { array -> (0 until array.length()).mapNotNull { parseCandle(array.getJSONObject(it)) } }
                .filter { it.close > 0.0 }
            if (candles.size < 20) throw IllegalStateException("OANDA ha restituito poche candele")
            return MarketAnalyzer.analyze(candles)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCandle(json: org.json.JSONObject): Candle? {
        if (!json.optBoolean("complete", false)) return null
        val mid = json.optJSONObject("mid") ?: return null
        val time = java.time.Instant.parse(json.getString("time")).toEpochMilli()
        return Candle(time, mid.getDouble("o"), mid.getDouble("h"), mid.getDouble("l"), mid.getDouble("c"))
    }
}