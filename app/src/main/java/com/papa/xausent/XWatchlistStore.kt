package com.papa.xausent

import android.content.Context
import org.json.JSONArray

object XWatchlistStore {
    private const val PREFS = "xau_sent_x_watchlist"
    private const val KEY = "handles"
    const val MAX_ACCOUNTS = 20

    val defaults = listOf(
        "Team_XAUUSD", "EaconomySignals", "DanielGoldTrade", "MARKE_TMASTER",
        "Prince_ict_smc", "TradingPulseFx", "GoldTraderOG", "TradersXauusd",
        "ForexSignalsSMS", "TheAlertNation", "VasilyTrader", "TradeWithJesse",
        "laura_XAUUSD", "Digital45599167", "BestGoldSignals", "goldtradermo",
        "XauusdSignal", "FX_GoldSniper", "TradingXAUUSD", "sally_xauusd"
    )

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (raw.isNullOrBlank()) return defaults
        return try {
            val array = JSONArray(raw)
            val values = (0 until array.length()).mapNotNull { array.optString(it, null) }
            normalize(values).ifEmpty { defaults }
        } catch (_: Throwable) {
            defaults
        }
    }

    fun save(context: Context, handles: List<String>) {
        val normalized = normalize(handles).ifEmpty { defaults }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, JSONArray(normalized).toString()).apply()
    }

    fun normalize(handles: List<String>): List<String> {
        val valid = Regex("^[A-Za-z0-9_]{1,15}$")
        val seen = HashSet<String>()
        return handles.asSequence()
            .map { it.trim().removePrefix("@").trim() }
            .filter { valid.matches(it) }
            .filter { seen.add(it.lowercase()) }
            .take(MAX_ACCOUNTS)
            .toList()
    }
}