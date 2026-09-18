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
    val updatedEpochMs: Long
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
