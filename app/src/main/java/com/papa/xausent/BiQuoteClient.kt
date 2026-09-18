package com.papa.xausent

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

object BiQuoteClient {
    private const val ENDPOINT = "https://biquote.io/api/XAUUSD/ohlc?interval=5m&limit=100"

    fun fetch(): MarketAnalysis {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("BiQuote HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val bars = JSONObject(body).getJSONArray("bars")
                .let { array -> (0 until array.length()).mapNotNull { parseBar(array.getJSONObject(it)) } }
                .sortedBy { it.first.time }
            val complete = bars.filterNot { it.second }
            if (complete.size < 20) throw IllegalStateException("BiQuote ha restituito poche candele complete")
            return MarketAnalyzer.analyze(
                input = complete.map { it.first },
                displayCandles = bars.map { it.first }
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseBar(json: JSONObject): Pair<Candle, Boolean>? {
        val time = Instant.parse(json.getString("openTime")).toEpochMilli()
        val open = json.getDouble("open")
        val high = json.getDouble("high")
        val low = json.getDouble("low")
        val close = json.getDouble("close")
        if (listOf(open, high, low, close).any { !it.isFinite() }) return null
        return Candle(time, open, high, low, close) to json.optBoolean("isOpen", false)
    }
}