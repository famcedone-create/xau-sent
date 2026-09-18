package com.papa.xausent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object XauStore {
    private const val PREF = "xau_sent"

    fun save(context: Context, data: XauData) {
        val json = JSONObject().apply {
            put("buyPct", data.buyPct); put("sellPct", data.sellPct)
            put("buyEntries", data.buyEntries); put("sellEntries", data.sellEntries)
            put("price", data.price ?: JSONObject.NULL); put("flow", data.flow)
            put("longPct", data.longPct ?: JSONObject.NULL); put("shortPct", data.shortPct ?: JSONObject.NULL)
            put("sample", data.sample); put("feedStatus", data.feedStatus); put("updated", data.updatedEpochMs)
            put("flowX", xFlowToJson(data.flowX))
            put("aiInsight", aiToJson(data.aiInsight))
            put("markets", marketsToJson(data.markets))
            put("ff5", flowToJson(data.forexFactory5m)); put("ff10", flowToJson(data.forexFactory10m))
            put("x5", flowToJson(data.flowX5m)); put("x10", flowToJson(data.flowX10m))
            put("market", data.market?.let(::marketToJson) ?: JSONObject.NULL)
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("data", json.toString()).apply()
    }

    fun load(context: Context): XauData? {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("data", null) ?: return null
        return try {
            JSONObject(raw).let { json ->
                XauData(
                    buyPct = json.getInt("buyPct"), sellPct = json.getInt("sellPct"),
                    buyEntries = json.getInt("buyEntries"), sellEntries = json.getInt("sellEntries"),
                    price = json.optionalDouble("price"), flow = json.getString("flow"),
                    longPct = json.optionalInt("longPct"), shortPct = json.optionalInt("shortPct"),
                    sample = json.getString("sample"), market = json.optJSONObject("market")?.let(::marketFromJson),
                    feedStatus = json.optString("feedStatus", "feed 5m non disponibile"),
                    flowX = xFlowFromJson(json), aiInsight = aiFromJson(json), markets = marketsFromJson(json),
                    forexFactory5m = flowFromJson(json.optJSONObject("ff5")), forexFactory10m = flowFromJson(json.optJSONObject("ff10")),
                    flowX5m = flowFromJson(json.optJSONObject("x5")), flowX10m = flowFromJson(json.optJSONObject("x10")),
                    updatedEpochMs = json.getLong("updated")
                )
            }
        } catch (_: Throwable) { null }
    }

    private fun marketToJson(market: MarketAnalysis) = JSONObject().apply {
        put("candles", JSONArray().apply { market.candles.forEach { put(JSONObject().apply { put("time", it.time); put("open", it.open); put("high", it.high); put("low", it.low); put("close", it.close) }) } })
        put("trend", market.trend); put("channelHigh", market.channelHigh); put("channelLow", market.channelLow)
        put("fib", JSONArray(market.fibonacci)); put("state", market.state)
        put("support1", market.support1 ?: JSONObject.NULL); put("support2", market.support2 ?: JSONObject.NULL)
        put("resistance1", market.resistance1 ?: JSONObject.NULL); put("resistance2", market.resistance2 ?: JSONObject.NULL)
        put("upper", JSONArray(listOf(market.upperTrendline.first, market.upperTrendline.second)))
        put("lower", JSONArray(listOf(market.lowerTrendline.first, market.lowerTrendline.second)))
        put("bullishFvgs", gapsToJson(market.bullishFvgs)); put("bearishFvgs", gapsToJson(market.bearishFvgs))
    }

    private fun marketFromJson(json: JSONObject): MarketAnalysis {
        val candleArray = json.getJSONArray("candles")
        val candles = (0 until candleArray.length()).map { candleArray.getJSONObject(it).let { c -> Candle(c.getLong("time"), c.getDouble("open"), c.getDouble("high"), c.getDouble("low"), c.getDouble("close")) } }
        return MarketAnalysis(candles, json.getString("trend"), json.pair("upper"), json.pair("lower"), json.getDouble("channelHigh"), json.getDouble("channelLow"), json.doubleList("fib"), json.gaps("bullishFvgs"), json.gaps("bearishFvgs"), json.optionalDouble("support1"), json.optionalDouble("support2"), json.optionalDouble("resistance1"), json.optionalDouble("resistance2"), json.getString("state"))
    }

    private fun xFlowToJson(flow: XFlowData) = JSONObject().apply {
        put("status", flow.status); put("buyPct", flow.buyPct); put("sellPct", flow.sellPct)
        put("buyEntries", flow.buyEntries); put("sellEntries", flow.sellEntries)
        put("totalPosts", flow.totalPosts); put("sample", flow.sample)
        put("accountsOk", flow.accountsOk); put("timelinesOk", flow.timelinesOk)
        put("accountsConfigured", flow.accountsConfigured)
        put("accountStatuses", JSONObject(flow.accountStatuses))
        put("lastXauPost", flow.lastXauPostEpochMs ?: JSONObject.NULL)
    }

    private fun xFlowFromJson(json: JSONObject): XFlowData {
        val flow = json.optJSONObject("flowX") ?: return XFlowData("non disponibile")
        return XFlowData(
            status = flow.optString("status", "non disponibile"),
            buyPct = flow.optInt("buyPct"), sellPct = flow.optInt("sellPct"),
            buyEntries = flow.optInt("buyEntries"), sellEntries = flow.optInt("sellEntries"),
            totalPosts = flow.optInt("totalPosts"), sample = flow.optString("sample", "--"),
            accountsOk = flow.optInt("accountsOk"), timelinesOk = flow.optInt("timelinesOk"),
            accountsConfigured = flow.optInt("accountsConfigured", XWatchlistStore.MAX_ACCOUNTS),
            accountStatuses = statusMap(flow.optJSONObject("accountStatuses")),
            lastXauPostEpochMs = if (flow.isNull("lastXauPost")) null else flow.optLong("lastXauPost")
        )
    }

    private fun aiToJson(insight: AiInsight) = JSONObject().apply {
        put("available", insight.available); put("structure", insight.structure); put("now", insight.now)
        put("flow", insight.flow); put("reading", insight.reading); put("confidence", insight.confidence)
    }

    private fun statusMap(json: JSONObject?): Map<String, String> {
        if (json == null) return emptyMap()
        val result = mutableMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = json.optString(key, "ERRORE")
        }
        return result
    }

    private fun aiFromJson(json: JSONObject): AiInsight {
        val ai = json.optJSONObject("aiInsight") ?: return AiInsight()
        return AiInsight(
            available = ai.optBoolean("available", false),
            structure = ai.optString("structure", "..."), now = ai.optString("now", "..."),
            flow = ai.optString("flow", "..."), reading = ai.optString("reading", "..."),
            confidence = ai.optInt("confidence", 0).coerceIn(0, 100)
        )
    }

    private fun flowToJson(flow: FlowSummary) = JSONObject().apply {
        put("status", flow.status); put("buyPct", flow.buyPct); put("sellPct", flow.sellPct)
        put("buyEntries", flow.buyEntries); put("sellEntries", flow.sellEntries); put("events", flow.events); put("sample", flow.sample)
    }

    private fun flowFromJson(json: JSONObject?): FlowSummary = json?.let {
        FlowSummary(it.optString("status", "nessun dato"), it.optInt("buyPct"), it.optInt("sellPct"), it.optInt("buyEntries"), it.optInt("sellEntries"), it.optInt("events"), it.optString("sample", "--"))
    } ?: FlowSummary()

    private fun marketsToJson(markets: Map<String, MarketAnalysis>) = JSONObject().apply {
        markets.forEach { (key, value) -> put(key, marketToJson(value)) }
    }

    private fun marketsFromJson(json: JSONObject): Map<String, MarketAnalysis> {
        val result = mutableMapOf<String, MarketAnalysis>()
        val markets = json.optJSONObject("markets") ?: return result
        val keys = markets.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            markets.optJSONObject(key)?.let { result[key] = marketFromJson(it) }
        }
        return result
    }

    private fun gapsToJson(gaps: List<PriceGap>) = JSONArray().apply { gaps.forEach { put(JSONObject().apply { put("bullish", it.bullish); put("low", it.low); put("high", it.high); put("start", it.startIndex); put("end", it.endIndex) }) } }
    private fun JSONObject.optionalInt(key: String): Int? = if (isNull(key)) null else optInt(key)
    private fun JSONObject.optionalDouble(key: String): Double? = if (isNull(key)) null else optDouble(key)
    private fun JSONObject.doubleList(key: String): List<Double> { val array = getJSONArray(key); return (0 until array.length()).map { array.getDouble(it) } }
    private fun JSONObject.pair(key: String): Pair<Double, Double> { val array = getJSONArray(key); return array.getDouble(0) to array.getDouble(1) }
    private fun JSONObject.gaps(key: String): List<PriceGap> { val array = getJSONArray(key); return (0 until array.length()).map { val gap = array.getJSONObject(it); PriceGap(gap.getBoolean("bullish"), gap.getDouble("low"), gap.getDouble("high"), gap.getInt("start"), gap.getInt("end")) } }
}
