package com.papa.xausent

import org.jsoup.Jsoup
import java.text.DecimalFormat
import kotlin.math.roundToInt

object ForexFactoryClient {
    private val urls = listOf(
        "https://mds-wss.forexfactory.com/trades",
        "https://calendar.forexfactory.com/trades",
        "https://www.forexfactory.com/trades"
    )

    private val goldRegex = Regex("(?i)(gold(?:/usd)?|xau[^\\s|]*)")
    private val minuteRegex = Regex("(?i)(?:~\\s*)?(<\\s*1|\\d+)\\s*min\\s+ago")
    private val numberAfterSide = Regex("(?i)\\b(?:BUY|SELL)\\s+~?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)")

    fun fetch(): XauData {
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
                    .timeout(12000)
                    .referrer("https://www.forexfactory.com/")
                    .get()

                val rows = doc.select("tr")
                val entries = mutableListOf<Entry>()
                val prices = mutableListOf<Double>()

                for (row in rows) {
                    val text = row.text().replace(Regex("\\s+"), " ").trim()
                    if (!goldRegex.containsMatchIn(text)) continue
                    val mins = parseMinutes(text) ?: continue
                    if (mins > 10) continue

                    val long = text.contains("Opened Long", ignoreCase = true)
                    val short = text.contains("Opened Short", ignoreCase = true)
                    if (!long && !short) continue

                    // Count a scaled-in row once, not every child fill.
                    val direction = if (long) "BUY" else "SELL"
                    val trader = row.select("td").getOrNull(1)?.text()?.trim().orEmpty()
                    val key = (if (trader.isBlank()) text.take(100) else trader) + "|" + direction
                    val price = numberAfterSide.find(text)?.groupValues?.getOrNull(1)
                        ?.replace(",", "")?.toDoubleOrNull()
                    entries += Entry(key, direction, mins, price)
                    if (price != null && mins <= 3) prices += price
                }

                // Conservative dedupe: one BUY and one SELL max per trader in the 10m window.
                val dedup = entries.distinctBy { it.key }
                val buy = dedup.count { it.direction == "BUY" }
                val sell = dedup.count { it.direction == "SELL" }
                val total = buy + sell

                val buyPct = if (total == 0) 0 else (buy * 100.0 / total).roundToInt()
                val sellPct = if (total == 0) 0 else 100 - buyPct
                val flow = when {
                    buyPct >= 60 -> "BUY"
                    sellPct >= 60 -> "SELL"
                    else -> "NEUTRO"
                }

                if (prices.isEmpty()) {
                    entries.mapNotNullTo(prices) { it.price }
                }
                val price = median(prices)
                val positioning = parseGoldPositioning(doc.text())
                val sample = when {
                    total >= 8 -> "campione buono"
                    total >= 4 -> "campione medio"
                    else -> "campione basso"
                }

                return XauData(
                    buyPct = buyPct,
                    sellPct = sellPct,
                    buyEntries = buy,
                    sellEntries = sell,
                    price = price,
                    flow = flow,
                    longPct = positioning?.first,
                    shortPct = positioning?.second,
                    sample = sample,
                    market = null,
                    feedStatus = "feed 5m non disponibile",
                    updatedEpochMs = System.currentTimeMillis()
                )
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw IllegalStateException("Nessuna fonte disponibile", lastError)
    }

    private fun parseMinutes(text: String): Int? {
        val m = minuteRegex.find(text) ?: return null
        val v = m.groupValues[1]
        return if (v.contains("<")) 0 else v.toIntOrNull()
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    private fun parseGoldPositioning(text: String): Pair<Int, Int>? {
        val compact = text.replace(Regex("\\s+"), " ")
        val idx = compact.indexOf("Gold/USD", ignoreCase = true)
        if (idx < 0) return null
        val chunk = compact.substring(idx, minOf(compact.length, idx + 700))
        val re = Regex("(?i)(\\d{1,3})%\\s+(\\d+)\\s+Traders.*?(\\d+)\\s+Traders\\s+(\\d{1,3})%")
        val m = re.find(chunk) ?: return null
        val longPct = m.groupValues[1].toIntOrNull() ?: return null
        val shortPct = m.groupValues[4].toIntOrNull() ?: return null
        if (longPct + shortPct !in 98..102) return null
        return longPct to shortPct
    }

    fun fmt(v: Double?): String = if (v == null) "--" else DecimalFormat("0.00").format(v)

    private data class Entry(val key: String, val direction: String, val minutes: Int, val price: Double?)
}
