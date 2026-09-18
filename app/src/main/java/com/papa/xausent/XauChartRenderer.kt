package com.papa.xausent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

object XauChartRenderer {
    fun render(): Bitmap {
        val width = 900
        val height = 430
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(25, 31, 35))
        val chartLeft = 34f
        val chartRight = 790f
        val chartTop = 26f
        val chartBottom = 390f
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(65, 76, 81)
            strokeWidth = 1f
        }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(200, 211, 213)
            textSize = 22f
        }
        for (index in 0..6) {
            val y = chartTop + (chartBottom - chartTop) * index / 6f
            canvas.drawLine(chartLeft, y, chartRight, y, grid)
        }
        for (index in 0..8) {
            val x = chartLeft + (chartRight - chartLeft) * index / 8f
            canvas.drawLine(x, chartTop, x, chartBottom, grid)
        }
        val fib = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 2f
            color = Color.argb(150, 238, 190, 93)
        }
        listOf("0", ".236", ".382", ".5", ".618", ".786", "1").forEachIndexed { index, value ->
            val y = chartTop + (chartBottom - chartTop) * index / 6f
            canvas.drawLine(chartLeft, y, chartRight, y, fib)
            canvas.drawText(value, 805f, y + 7f, label)
        }
        val fvgBearish = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(85, 221, 93, 93) }
        val fvgBullish = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(85, 64, 181, 133) }
        canvas.drawRect(190f, 106f, 300f, 148f, fvgBearish)
        canvas.drawRect(556f, 231f, 670f, 268f, fvgBullish)
        canvas.drawText("FVG", 202f, 133f, label)
        canvas.drawText("FVG", 568f, 256f, label)
        val upper = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(241, 126, 82)
            strokeWidth = 4f
        }
        val lower = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(75, 174, 156)
            strokeWidth = 4f
        }
        canvas.drawLine(70f, 105f, 744f, 181f, upper)
        canvas.drawLine(70f, 294f, 744f, 363f, lower)
        val price = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 244, 244)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val path = Path().apply {
            moveTo(45f, 286f)
            lineTo(103f, 261f)
            lineTo(154f, 276f)
            lineTo(204f, 214f)
            lineTo(255f, 229f)
            lineTo(307f, 174f)
            lineTo(360f, 202f)
            lineTo(416f, 143f)
            lineTo(472f, 177f)
            lineTo(526f, 120f)
            lineTo(581f, 154f)
            lineTo(638f, 201f)
            lineTo(696f, 178f)
            lineTo(752f, 217f)
        }
        canvas.drawPath(path, price)
        canvas.drawText("5m", 44f, 22f, label)
        return bitmap
    }
}