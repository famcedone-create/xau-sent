package com.papa.xausent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import java.util.Locale

object XauChartRenderer {
    fun render(market: MarketAnalysis?, timeframe: String = "5m"): Bitmap {
        val width = 900; val height = 700
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(25, 31, 35))
        val left = 34f; val plotRight = 744f; val scaleLeft = 766f; val scaleRight = 890f
        val top = 42f; val bottom = 650f
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(65, 76, 81); strokeWidth = 1f }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(200, 211, 213); textSize = 22f }
        for (index in 0..6) canvas.drawLine(left, top + (bottom - top) * index / 6f, plotRight, top + (bottom - top) * index / 6f, grid)
        for (index in 0..8) canvas.drawLine(left + (plotRight - left) * index / 8f, top, left + (plotRight - left) * index / 8f, bottom, grid)
        if (market == null) {
            canvas.drawText("feed $timeframe non disponibile", left, 210f, label)
            return bitmap
        }
        val min = market.candles.minOf { it.low }
        val max = market.candles.maxOf { it.high }
        val range = (max - min).coerceAtLeast(0.01)
        val scaleX = (plotRight - left) / market.candles.lastIndex.coerceAtLeast(1)
        fun y(value: Double) = bottom - ((value - min) / (max - min).coerceAtLeast(0.01) * (bottom - top)).toFloat()
        fun x(index: Int) = left + index.coerceIn(0, market.candles.lastIndex) * scaleX
        fun price(value: Double) = String.format(Locale.US, "%.2f", value)
        val scaleLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(200, 211, 213)
            textSize = 20f
            textAlign = Paint.Align.RIGHT
        }
        val scaleLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(65, 76, 81); strokeWidth = 1f }
        for (index in 0..5) {
            val value = max - range * index / 5.0
            val tickY = y(value)
            canvas.drawLine(scaleLeft, tickY, scaleRight, tickY, scaleLine)
            canvas.drawText(price(value), scaleRight, tickY + 7f, scaleLabel)
        }
        val fvgBull = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(85, 64, 181, 133) }
        val fvgBear = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(85, 221, 93, 93) }
        market.bullishFvgs.forEach { canvas.drawRect(x(it.startIndex), y(it.high), x(it.endIndex), y(it.low), fvgBull) }
        market.bearishFvgs.forEach { canvas.drawRect(x(it.startIndex), y(it.high), x(it.endIndex), y(it.low), fvgBear) }
        val fib = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; color = Color.argb(150, 238, 190, 93) }
        market.fibonacci.forEachIndexed { index, value ->
            canvas.drawLine(left, y(value), plotRight - 8f, y(value), fib)
            canvas.drawText(listOf("0", ".236", ".382", ".5", ".618", ".786", "1")[index], plotRight - 16f, y(value) + 7f, label)
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
        val current = market.candles.last().close
        val currentY = y(current)
        val currentLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 232, 112)
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }
        canvas.drawLine(left, currentY, plotRight, currentY, currentLine)
        val currentLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 31, 35)
            textSize = 20f
            textAlign = Paint.Align.RIGHT
        }
        val currentBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 232, 112) }
        val labelTop = (currentY - 17f).coerceIn(top, bottom - 30f)
        canvas.drawRoundRect(scaleLeft - 6f, labelTop, scaleRight, labelTop + 28f, 4f, 4f, currentBackground)
        canvas.drawText(price(current), scaleRight - 8f, labelTop + 21f, currentLabel)
        canvas.drawText("$timeframe • BiQuote", 44f, 22f, label)
        return bitmap
    }
}
