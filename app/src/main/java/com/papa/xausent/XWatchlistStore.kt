package com.papa.xausent

import android.content.Context
import org.json.JSONArray

object XWatchlistStore {
    private const val PREFS = "xau_sent_x_watchlist"
    private const val ACTIVE_KEY = "active_handles"
    private const val AVAILABLE_KEY = "available_handles"
    private const val LEGACY_KEY = "handles"
    const val MAX_ACCOUNTS = 20

    val defaults = listOf(
        "Team_XAUUSD", "EaconomySignals", "DanielGoldTrade", "MARKE_TMASTER",
        "Prince_ict_smc", "TradingPulseFx", "GoldTraderOG", "TradersXauusd",
        "ForexSignalsSMS", "TheAlertNation", "VasilyTrader", "TradeWithJesse",
        "laura_XAUUSD", "Digital45599167", "BestGoldSignals", "goldtradermo",
        "XauusdSignal", "FX_GoldSniper", "TradingXAUUSD", "sally_xauusd",
        "MDMOINKHANah", "FxFiza98242", "Ilyas1026079", "zahidTraderfx",
        "JohnSmithj9y", "MarcusXauTrades", "wizzysophi"
    )

    fun available(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = read(prefs.getString(AVAILABLE_KEY, null))
        if (saved.isNotEmpty()) return saved
        return normalize(defaults + read(prefs.getString(LEGACY_KEY, null)))
    }

    fun active(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = read(prefs.getString(ACTIVE_KEY, null))
        val source = if (saved.isNotEmpty()) saved else read(prefs.getString(LEGACY_KEY, null)).ifEmpty { defaults }
        val available = available(context).map { it.lowercase() }.toSet()
        return normalize(source).filter { it.lowercase() in available }.take(MAX_ACCOUNTS)
    }

    fun save(context: Context, handles: List<String>) = saveActive(context, handles)

    fun saveActive(context: Context, handles: List<String>) {
        val available = available(context).map { it.lowercase() }.toSet()
        val normalized = normalize(handles).filter { it.lowercase() in available }.take(MAX_ACCOUNTS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ACTIVE_KEY, JSONArray(normalized).toString()).apply()
    }

    fun saveAvailable(context: Context, handles: List<String>) {
        val normalized = normalize(handles)
        val lower = normalized.map { it.lowercase() }.toSet()
        val active = active(context).filter { it.lowercase() in lower }.take(MAX_ACCOUNTS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(AVAILABLE_KEY, JSONArray(normalized).toString())
            .putString(ACTIVE_KEY, JSONArray(active).toString()).apply()
    }

    private fun read(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            normalize((0 until array.length()).mapNotNull { array.optString(it, null) })
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun normalize(handles: List<String>): List<String> {
        val valid = Regex("^[A-Za-z0-9_]{1,15}$")
        val seen = HashSet<String>()
        return handles.asSequence()
            .map { it.trim().removePrefix("@").trim() }
            .filter { valid.matches(it) }
            .filter { seen.add(it.lowercase()) }
            .toList()
    }
}