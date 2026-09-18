package com.papa.xausent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object XauChartRenderer {
    fun render(market: MarketAnalysis?): Bitmap {
        val width = 900; val height = 430
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(25, 31, 35))
        val left = 34f; val right = 790f; val top = 26f; val bottom = 390f
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(65, 76, 81); strokeWidth = 1f }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(200, 211, 213); textSize = 22f }
        for (index in 0..6) canvas.drawLine(left, top + (bottom - top) * index / 6f, right, top + (bottom - top) * index / 6f, grid)
        for (index in 0..8) canvas.drawLine(left + (right - left) * index / 8f, top, left + (right - left) * index / 8f, bottom, grid)
        if (market == null) {
            canvas.drawText("5m feed non configurato", left, 210f, label)
            return bitmap
        }
        val min = market.candles.minOf { it.low }.coerceAtMost(market.fibonacci.minOrNull() ?: 0.0)
        val max = market.candles.maxOf { it.high }.coerceAtLeast(market.fibonacci.maxOrNull() ?: 0.0)
        val scaleX = (right - left) / market.candles.lastIndex.coerceAtLeast(1)
        fun y(value: Double) = bottom - ((value - min) / (max - min).coerceAtLeast(0.01) * (bottom - top)).toFloat()
        fun x(index: Int) = left + index.coerceIn(0, market.candles.lastIndex) * scaleX
        val fvgBull = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(85, 64, 181, 133) }
        val fvgBear = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(85, 221, 93, 93) }
        market.bullishFvgs.forEach { canvas.drawRect(x(it.startIndex), y(it.high), x(it.endIndex), y(it.low), fvgBull) }
        market.bearishFvgs.forEach { canvas.drawRect(x(it.startIndex), y(it.high), x(it.endIndex), y(it.low), fvgBear) }
        val fib = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; color = Color.argb(150, 238, 190, 93) }
        market.fibonacci.forEachIndexed { index, value ->
            canvas.drawLine(left, y(value), right, y(value), fib)
            canvas.drawText(listOf("0", ".236", ".382", ".5", ".618", ".786", "1")[index], 805f, y(value) + 7f, label)
        }
        val upper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(241, 126, 82); strokeWidth = 4f }
        val lower = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(75, 174, 156); strokeWidth = 4f }
        canvas.drawLine(x(0), y(market.upperTrendline.first), x(market.candles.lastIndex), y(market.upperTrendline.second), upper)
        canvas.drawLine(x(0), y(market.lowerTrendline.first), x(market.candles.lastIndex), y(market.lowerTrendline.second), lower)
        val candlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f }
        market.candles.forEachIndexed { index, candle ->
            candlePaint.color = if (candle.close >= candle.open) Color.rgb(75, 174, 156) else Color.rgb(221, 93, 93)
            canvas.drawLine(x(index), y(candle.high), x(index), y(candle.low), candlePaint)
            canvas.drawLine(x(index), y(candle.open), x(index) + scaleX * .65f, y(candle.open), candlePaint)
            canvas.drawLine(x(index), y(candle.close), x(index) + scaleX * .65f, y(candle.close), candlePaint)
        }
        canvas.drawText("5m • OANDA", 44f, 22f, label)
        return bitmap
    }
}
