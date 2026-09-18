package com.papa.xausent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AiInsightClient {
    private const val PREFS = "xau_sent_ai"
    private const val ENDPOINT_KEY = "backend_endpoint"

    fun configureBackend(context: Context, endpoint: String) {
        val normalized = endpoint.trim()
        if (normalized.isNotEmpty() && !normalized.startsWith("https://")) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ENDPOINT_KEY, normalized).apply()
    }

    fun fetch(context: Context, data: XauData): AiInsight {
        val endpoint = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ENDPOINT_KEY, "").orEmpty()
        if (!endpoint.startsWith("https://")) return AiInsight()
        return try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                connection.outputStream.use { it.write(payload(data).toString().toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode !in 200..299) return AiInsight()
                parseResponse(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        } catch (_: Throwable) {
            AiInsight()
        }
    }

    private fun payload(data: XauData) = JSONObject().apply {
        put("market", JSONObject().apply {
            put("price", data.market?.candles?.lastOrNull()?.close ?: JSONObject.NULL)
            put("candles", JSONArray().apply {
                data.market?.candles?.takeLast(24)?.forEach { candle ->
                    put(JSONObject().apply {
                        put("time", candle.time); put("open", candle.open)
                        put("high", candle.high); put("low", candle.low); put("close", candle.close)
                    })
                }
            })
            put("trend", data.market?.trend ?: JSONObject.NULL)
            put("state", data.market?.state ?: JSONObject.NULL)
            put("resistance1", data.market?.resistance1 ?: JSONObject.NULL)
            put("support1", data.market?.support1 ?: JSONObject.NULL)
            put("fvgSell", data.market?.bearishFvgs?.lastOrNull()?.let(::gapToJson) ?: JSONObject.NULL)
            put("fvgBuy", data.market?.bullishFvgs?.lastOrNull()?.let(::gapToJson) ?: JSONObject.NULL)
        })
        put("forexFactory", JSONObject().apply {
            put("buyPct", data.buyPct); put("sellPct", data.sellPct)
            put("buyEntries", data.buyEntries); put("sellEntries", data.sellEntries)
            put("sample", data.sample)
        })
        put("flowX", JSONObject().apply {
            put("status", data.flowX.status); put("buyPct", data.flowX.buyPct); put("sellPct", data.flowX.sellPct)
            put("buyEntries", data.flowX.buyEntries); put("sellEntries", data.flowX.sellEntries)
            put("totalPosts", data.flowX.totalPosts); put("sample", data.flowX.sample)
            put("accountsOk", data.flowX.accountsOk); put("timelinesOk", data.flowX.timelinesOk)
        })
    }

    private fun gapToJson(gap: PriceGap) = JSONObject().apply {
        put("low", gap.low); put("high", gap.high)
    }

    private fun parseResponse(body: String): AiInsight {
        val json = JSONObject(body)
        if (!json.has("structure") || !json.has("now") || !json.has("flow") || !json.has("reading") || !json.has("confidence")) return AiInsight()
        val rawConfidence = json.getDouble("confidence")
        val percentConfidence = if (rawConfidence in 0.0..1.0) rawConfidence * 100.0 else rawConfidence
        return AiInsight(
            available = true,
            structure = json.getString("structure"),
            now = json.getString("now"),
            flow = json.getString("flow"),
            reading = json.getString("reading"),
            confidence = percentConfidence.toInt().coerceIn(0, 100)
        )
    }
}