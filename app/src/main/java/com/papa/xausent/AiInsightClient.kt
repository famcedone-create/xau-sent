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
            val candles = market.candles
            val current = candles.lastOrNull()?.close
            val previous = candles.dropLast(1).lastOrNull()?.close
            val swings = swingSnapshot(candles)
            put("price", current ?: JSONObject.NULL)
            put("candles", JSONArray().apply {
                candles.takeLast(36).forEach { candle ->
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
            put("momentum", if (current != null && previous != null) current - previous else JSONObject.NULL)
            put("structure", swings.structure)
            put("swingHigh", swings.high ?: JSONObject.NULL); put("swingLow", swings.low ?: JSONObject.NULL)
            put("bos", swings.bos); put("choch", swings.choch)
            put("pattern", detectPattern(candles, market))
            put("trendline", JSONObject().apply {
                put("upper", linePayload(market.upperTrendline, candles, current))
                put("lower", linePayload(market.lowerTrendline, candles, current))
                put("convergence", convergence(market))
            })
            put("breakoutRetest", market.state)
            put("pricePosition", current?.let { pricePosition(it, market) } ?: JSONObject.NULL)
    }

    private data class SwingSnapshot(val high: Double?, val low: Double?, val structure: String, val bos: String, val choch: String)

    private fun swingSnapshot(candles: List<Candle>): SwingSnapshot {
        if (candles.size < 5) return SwingSnapshot(null, null, "dati insufficienti", "nessun BOS", "nessun CHOCH")
        val highs = candles.drop(2).dropLast(2).mapIndexedNotNull { index, candle ->
            val center = index + 2
            val window = candles.subList(center - 2, center + 3)
            if (candle.high >= window.maxOf { it.high }) candle.high else null
        }
        val lows = candles.drop(2).dropLast(2).mapIndexedNotNull { index, candle ->
            val center = index + 2
            val window = candles.subList(center - 2, center + 3)
            if (candle.low <= window.minOf { it.low }) candle.low else null
        }
        val high = highs.lastOrNull(); val low = lows.lastOrNull()
        val structure = when {
            highs.size >= 2 && lows.size >= 2 && highs.last() > highs[highs.lastIndex - 1] && lows.last() > lows[lows.lastIndex - 1] -> "HH/HL rialzista"
            highs.size >= 2 && lows.size >= 2 && highs.last() < highs[highs.lastIndex - 1] && lows.last() < lows[lows.lastIndex - 1] -> "LH/LL ribassista"
            highs.size >= 2 && lows.size >= 2 -> "struttura mista"
            else -> "struttura non chiara"
        }
        val close = candles.last().close
        val bos = when { high != null && close > high -> "BOS rialzista"; low != null && close < low -> "BOS ribassista"; else -> "nessun BOS" }
        val choch = if (bos != "nessun BOS" && structure.contains("mista")) bos.replace("BOS", "CHOCH") else "nessun CHOCH"
        return SwingSnapshot(high, low, structure, bos, choch)
    }

    private fun linePayload(line: Pair<Double, Double>, candles: List<Candle>, current: Double?) = JSONObject().apply {
        val slope = if (candles.size > 1) (line.second - line.first) / (candles.size - 1) else 0.0
        put("start", line.first); put("end", line.second); put("slope", slope)
        put("touchesApprox", approximateTouches(line, candles)); put("distance", current?.let { kotlin.math.abs(it - line.second) } ?: JSONObject.NULL)
    }

    private fun approximateTouches(line: Pair<Double, Double>, candles: List<Candle>): Int {
        if (candles.isEmpty()) return 0
        val tolerance = candles.map { it.high - it.low }.average().coerceAtLeast(0.01) * 0.7
        return candles.mapIndexed { index, candle ->
            val expected = line.first + (line.second - line.first) * index.toDouble() / candles.lastIndex.coerceAtLeast(1)
            kotlin.math.abs(candle.high - expected) <= tolerance || kotlin.math.abs(candle.low - expected) <= tolerance
        }.count { it }
    }

    private fun convergence(market: MarketAnalysis): String = if (market.upperTrendline.second > market.upperTrendline.first && market.lowerTrendline.second > market.lowerTrendline.first) "ascendente" else if (market.upperTrendline.second < market.upperTrendline.first && market.lowerTrendline.second < market.lowerTrendline.first) "discendente" else "divergente"

    private fun detectPattern(candles: List<Candle>, market: MarketAnalysis): String {
        if (candles.size < 8) return "nessun pattern chiaro"
        val recent = candles.takeLast(8); val highs = recent.map { it.high }; val lows = recent.map { it.low }
        val highest = highs.maxOrNull() ?: return "nessun pattern chiaro"
        val lowest = lows.minOrNull() ?: return "nessun pattern chiaro"
        val highRange = highest - (highs.minOrNull() ?: highest); val lowRange = (lows.maxOrNull() ?: lowest) - lowest
        val range = (highest - lowest).coerceAtLeast(0.01)
        return when {
            highRange / range < 0.18 && lowRange / range < 0.18 -> "range / rettangolo"
            market.state == "breakout" -> "breakout trend line"
            highRange < lowRange * 0.65 -> "compressione / triangolo"
            else -> "nessun pattern chiaro"
        }
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
        if (!json.has("structure") || !json.has("pattern") || !json.has("scalp") || !json.has("flow") || !json.has("reading") || !json.has("confidence")) return AiInsight()
        val rawConfidence = json.getDouble("confidence")
        val percentConfidence = if (rawConfidence in 0.0..1.0) rawConfidence * 100.0 else rawConfidence
        return AiInsight(
            available = true,
            structure = json.getString("structure"),
            pattern = json.getString("pattern"),
            scalp = json.getString("scalp"),
            flow = json.getString("flow"),
            reading = json.getString("reading"),
            confidence = percentConfidence.toInt().coerceIn(0, 100)
        )
    }
}