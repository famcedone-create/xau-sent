package com.papa.xausent

data class XauData(
    val buyPct: Int,
    val sellPct: Int,
    val buyEntries: Int,
    val sellEntries: Int,
    val price: Double?,
    val flow: String,
    val longPct: Int?,
    val shortPct: Int?,
    val sample: String,
    val market: MarketAnalysis?,
    val feedStatus: String,
    val flowX: XFlowData = XFlowData("non disponibile"),
    val aiInsight: AiInsight = AiInsight(),
    val markets: Map<String, MarketAnalysis> = emptyMap(),
    val forexFactory5m: FlowSummary = FlowSummary(),
    val forexFactory10m: FlowSummary = FlowSummary(),
    val flowX5m: FlowSummary = FlowSummary(),
    val flowX10m: FlowSummary = FlowSummary(),
    val updatedEpochMs: Long
)

data class FlowSummary(
    val status: String = "nessun dato",
    val buyPct: Int = 0,
    val sellPct: Int = 0,
    val buyEntries: Int = 0,
    val sellEntries: Int = 0,
    val events: Int = 0,
    val sample: String = "--"
)

data class AiInsight(
    val available: Boolean = false,
    val structure: String = "...",
    val now: String = "...",
    val flow: String = "...",
    val reading: String = "...",
    val confidence: Int = 0
)

data class XFlowData(
    val status: String,
    val buyPct: Int = 0,
    val sellPct: Int = 0,
    val buyEntries: Int = 0,
    val sellEntries: Int = 0,
    val totalPosts: Int = 0,
    val sample: String = "--",
    val accountsOk: Int = 0,
    val timelinesOk: Int = 0,
    val accountsConfigured: Int = 20,
    val accountStatuses: Map<String, String> = emptyMap(),
    val lastXauPostEpochMs: Long? = null
)

data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

data class PriceGap(
    val bullish: Boolean,
    val low: Double,
    val high: Double,
    val startIndex: Int,
    val endIndex: Int
)

data class MarketAnalysis(
    val candles: List<Candle>,
    val trend: String,
    val upperTrendline: Pair<Double, Double>,
    val lowerTrendline: Pair<Double, Double>,
    val channelHigh: Double,
    val channelLow: Double,
    val fibonacci: List<Double>,
    val bullishFvgs: List<PriceGap>,
    val bearishFvgs: List<PriceGap>,
    val support1: Double?,
    val support2: Double?,
    val resistance1: Double?,
    val resistance2: Double?,
    val state: String
)
