package com.papa.xausent

import android.content.Context

object XauStore {
    private const val PREF = "xau_sent"

    fun save(context: Context, d: XauData) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt("buyPct", d.buyPct)
            .putInt("sellPct", d.sellPct)
            .putInt("buyEntries", d.buyEntries)
            .putInt("sellEntries", d.sellEntries)
            .putLong("price", java.lang.Double.doubleToRawLongBits(d.price ?: Double.NaN))
            .putLong("t1", java.lang.Double.doubleToRawLongBits(d.t1 ?: Double.NaN))
            .putLong("t2", java.lang.Double.doubleToRawLongBits(d.t2 ?: Double.NaN))
            .putString("flow", d.flow)
            .putInt("longPct", d.longPct ?: -1)
            .putInt("shortPct", d.shortPct ?: -1)
            .putString("sample", d.sample)
            .putLong("updated", d.updatedEpochMs)
            .apply()
    }

    fun load(context: Context): XauData? {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!p.contains("updated")) return null
        fun readDouble(k: String): Double? {
            val d = java.lang.Double.longBitsToDouble(p.getLong(k, java.lang.Double.doubleToRawLongBits(Double.NaN)))
            return if (d.isNaN()) null else d
        }
        return XauData(
            buyPct = p.getInt("buyPct", 0),
            sellPct = p.getInt("sellPct", 0),
            buyEntries = p.getInt("buyEntries", 0),
            sellEntries = p.getInt("sellEntries", 0),
            price = readDouble("price"),
            t1 = readDouble("t1"),
            t2 = readDouble("t2"),
            flow = p.getString("flow", "--") ?: "--",
            longPct = p.getInt("longPct", -1).takeIf { it >= 0 },
            shortPct = p.getInt("shortPct", -1).takeIf { it >= 0 },
            sample = p.getString("sample", "--") ?: "--",
            updatedEpochMs = p.getLong("updated", 0)
        )
    }
}
