package com.papa.xausent

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

object MarketAnalyzer {
    fun analyze(input: List<Candle>, displayCandles: List<Candle> = input): MarketAnalysis {
        val candles = input.takeLast(96)
        val chartCandles = displayCandles.takeLast(96)
        val closes = candles.map { it.close }
        val regression = regression(closes)
        val residuals = closes.mapIndexed { index, close -> close - (regression.first + regression.second * index) }
        val deviation = sqrt(residuals.map { it.pow(2) }.average())
        val upper = regression.first + deviation * 1.8
        val lower = regression.first - deviation * 1.8
        val slopeThreshold = averageTrueRange(candles).coerceAtLeast(0.01) * 0.025
        val trend = when {
            regression.second > slopeThreshold -> "rialzista"
            regression.second < -slopeThreshold -> "ribassista"
            else -> "laterale"
        }
        val highest = candles.maxOf { it.high }
        val lowest = candles.minOf { it.low }
        val range = (highest - lowest).coerceAtLeast(0.01)
        val fib = listOf(0.0, .236, .382, .5, .618, .786, 1.0).map { lowest + range * it }
        val gaps = findFvgs(candles)
        val current = closes.last()
        val prior = closes.dropLast(1).lastOrNull() ?: current
        val state = when {
            current > upper || current < lower -> "breakout"
            (prior > upper || prior < lower) && abs(current - regression.first) < deviation -> "retest"
            (prior > upper || prior < lower) -> "fail"
            else -> "nel canale"
        }
        val pivots = pivotLevels(candles, current)
        return MarketAnalysis(
            trend = trend,
            candles = chartCandles,
            upperTrendline = line(regression, deviation * 1.8, chartCandles.lastIndex),
            lowerTrendline = line(regression, -deviation * 1.8, chartCandles.lastIndex),
            channelHigh = upper,
            channelLow = lower,
            fibonacci = fib,
            bullishFvgs = gaps.filter { it.bullish }.takeLast(4),
            bearishFvgs = gaps.filterNot { it.bullish }.takeLast(4),
            support1 = pivots.getOrNull(0),
            support2 = pivots.getOrNull(1),
            resistance1 = pivots.getOrNull(2),
            resistance2 = pivots.getOrNull(3),
            state = state
        )
    }

    private fun regression(values: List<Double>): Pair<Double, Double> {
        val n = values.size.toDouble()
        val xMean = (values.indices).average()
        val yMean = values.average()
        val denominator = values.indices.sumOf { (it - xMean).pow(2) }.coerceAtLeast(0.0001)
        val slope = values.indices.sumOf { (it - xMean) * (values[it] - yMean) } / denominator
        return yMean - slope * xMean to slope
    }

    private fun line(regression: Pair<Double, Double>, offset: Double, lastIndex: Int): Pair<Double, Double> =
        regression.first + offset + regression.second * 0 to regression.first + offset + regression.second * lastIndex

    private fun averageTrueRange(candles: List<Candle>): Double = candles.zipWithNext { previous, current ->
        maxOf(current.high - current.low, abs(current.high - previous.close), abs(current.low - previous.close))
    }.average()

    private fun findFvgs(candles: List<Candle>): List<PriceGap> = candles.drop(2).mapIndexedNotNull { index, current ->
        val first = candles[index]
        val second = candles[index + 1]
        when {
            current.low > first.high -> PriceGap(true, first.high, current.low, index, index + 2)
            current.high < first.low -> PriceGap(false, current.high, first.low, index, index + 2)
            else -> null
        }
    }

    private fun pivotLevels(candles: List<Candle>, current: Double): List<Double?> {
        val supports = mutableListOf<Double>()
        val resistances = mutableListOf<Double>()
        for (index in 2 until candles.lastIndex - 1) {
            val candle = candles[index]
            val neighbors = candles.subList(index - 2, index + 3)
            if (candle.low == neighbors.minOf { it.low } && candle.low < current) supports += candle.low
            if (candle.high == neighbors.maxOf { it.high } && candle.high > current) resistances += candle.high
        }
        val sortedSupports = supports.distinct().sortedDescending()
        val sortedResistances = resistances.distinct().sorted()
        return listOf(sortedSupports.getOrNull(0), sortedSupports.getOrNull(1), sortedResistances.getOrNull(0), sortedResistances.getOrNull(1))
    }
}