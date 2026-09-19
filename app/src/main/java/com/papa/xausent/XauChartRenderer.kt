package com.papa.xausent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
        val visibleCandles = market.candles.takeLast(70)
        val candleOffset = market.candles.size - visibleCandles.size
        val min = visibleCandles.minOf { it.low }
        val max = visibleCandles.maxOf { it.high }
        val range = (max - min).coerceAtLeast(0.01)
        val scaleX = (plotRight - left) / visibleCandles.lastIndex.coerceAtLeast(1)
        fun y(value: Double) = bottom - ((value - min) / (max - min).coerceAtLeast(0.01) * (bottom - top)).toFloat()
        fun x(index: Int) = left + index.coerceIn(0, visibleCandles.lastIndex) * scaleX
        fun price(value: Double) = String.format(Locale.US, "%.2f", value)
        val scaleLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(200, 211, 213)
            textSize = 24f
            textAlign = Paint.Align.RIGHT
        }
        val scaleLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(65, 76, 81); strokeWidth = 1f }
        for (index in 0..5) {
            val value = max - range * index / 5.0
            val tickY = y(value)
            canvas.drawLine(scaleLeft, tickY, scaleRight, tickY, scaleLine)
            canvas.drawText(price(value), scaleRight, tickY + 7f, scaleLabel)
        }
        val fvgBull = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(105, 56, 196, 210) }
        val fvgBear = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(105, 145, 153, 224) }
        fun drawFvg(gap: PriceGap, paint: Paint) {
            val start = gap.startIndex - candleOffset
            val end = gap.endIndex - candleOffset
            if (end >= 0 && start <= visibleCandles.lastIndex) {
                canvas.drawRoundRect(
                    x(start.coerceAtLeast(0)), y(gap.high),
                    x(end.coerceAtMost(visibleCandles.lastIndex)), y(gap.low),
                    2f, 2f, paint
                )
            }
        }
        market.bullishFvgs.forEach { drawFvg(it, fvgBull) }
        market.bearishFvgs.forEach { drawFvg(it, fvgBear) }
        val fib = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; color = Color.argb(150, 238, 190, 93) }
        market.fibonacci.forEachIndexed { index, value ->
            canvas.drawLine(left, y(value), plotRight - 8f, y(value), fib)
            canvas.drawText(listOf("0", ".236", ".382", ".5", ".618", ".786", "1")[index], plotRight - 16f, y(value) + 7f, label)
        }
        val upper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(241, 126, 82); strokeWidth = 4f }
        val lower = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(75, 174, 156); strokeWidth = 4f }
        val trendlineOffset = candleOffset.toDouble() / market.candles.lastIndex.coerceAtLeast(1)
        val upperStart = market.upperTrendline.first + (market.upperTrendline.second - market.upperTrendline.first) * trendlineOffset
        val lowerStart = market.lowerTrendline.first + (market.lowerTrendline.second - market.lowerTrendline.first) * trendlineOffset
        canvas.drawLine(x(0), y(upperStart), x(visibleCandles.lastIndex), y(market.upperTrendline.second), upper)
        canvas.drawLine(x(0), y(lowerStart), x(visibleCandles.lastIndex), y(market.lowerTrendline.second), lower)
        val wickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val bodyWidth = (scaleX * .62f).coerceIn(7f, 18f)
        visibleCandles.forEachIndexed { index, candle ->
            val candleColor = if (candle.close >= candle.open) Color.rgb(75, 174, 156) else Color.rgb(221, 93, 93)
            wickPaint.color = candleColor
            bodyPaint.color = candleColor
            canvas.drawLine(x(index), y(candle.high), x(index), y(candle.low), wickPaint)
            val bodyTop = y(maxOf(candle.open, candle.close))
            val bodyBottom = y(minOf(candle.open, candle.close))
            val readableTop = minOf(bodyTop, bodyBottom - 3f)
            canvas.drawRoundRect(x(index) - bodyWidth / 2f, readableTop, x(index) + bodyWidth / 2f, bodyBottom, 2f, 2f, bodyPaint)
        }
        val current = visibleCandles.last().close
        val currentY = y(current)
        val currentLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 232, 112)
            strokeWidth = 1.8f
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
