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
            val allMarkets = if (data.markets.isEmpty()) mapOf("5m" to data.market) else data.markets
            allMarkets.forEach { (timeframe, market) -> put(timeframe, market?.let(::marketPayload) ?: JSONObject.NULL) }
        })
        put("flow", JSONObject().apply {
            put("forexFactory5m", flowPayload(data.forexFactory5m)); put("forexFactory10m", flowPayload(data.forexFactory10m))
            put("x5m", flowPayload(data.flowX5m)); put("x10m", flowPayload(data.flowX10m))
        })
    }

    private fun marketPayload(market: MarketAnalysis) = JSONObject().apply {
            put("price", market.candles.lastOrNull()?.close ?: JSONObject.NULL)
            put("candles", JSONArray().apply {
                market.candles.takeLast(24).forEach { candle ->
                    put(JSONObject().apply {
                        put("time", candle.time); put("open", candle.open)
                        put("high", candle.high); put("low", candle.low); put("close", candle.close)
                    })
                }
            })
            put("trend", market.trend); put("state", market.state)
            put("resistance1", market.resistance1 ?: JSONObject.NULL); put("support1", market.support1 ?: JSONObject.NULL)
            put("fvgSell", market.bearishFvgs.lastOrNull()?.let(::gapToJson) ?: JSONObject.NULL)
            put("fvgBuy", market.bullishFvgs.lastOrNull()?.let(::gapToJson) ?: JSONObject.NULL)
                val current = market.candles.lastOrNull()?.close
                val previous = market.candles.dropLast(1).lastOrNull()?.close
                put("momentum", if (current != null && previous != null) current - previous else JSONObject.NULL)
                put("structure", market.state)
                put("pricePosition", current?.let { pricePosition(it, market) } ?: JSONObject.NULL)
    }

            private fun pricePosition(price: Double, market: MarketAnalysis): String = when {
            price > market.channelHigh -> "sopra canale"
            price < market.channelLow -> "sotto canale"
            market.resistance1 != null && price >= market.resistance1 -> "vicino resistenza"
            market.support1 != null && price <= market.support1 -> "vicino supporto"
            else -> "nel canale"
            }

    private fun flowPayload(flow: FlowSummary) = JSONObject().apply {
        put("status", flow.status); put("buyPct", flow.buyPct); put("sellPct", flow.sellPct)
        put("buyEntries", flow.buyEntries); put("sellEntries", flow.sellEntries); put("events", flow.events); put("sample", flow.sample)
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