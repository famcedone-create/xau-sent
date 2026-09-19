package com.papa.xausent

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class RemoteConfig(
    val version: Int = 1,
    val aiEnabled: Boolean = true,
    val aiManualOnly: Boolean = true,
    val keepPreviousAi: Boolean = true,
    val entryEnabled: Boolean = false,
    val entryMinConfidence: Int = 70,
    val useFlow: Boolean = true,
    val requireBos: Boolean = true,
    val requireRetest: Boolean = false,
    val pathMax: Double = 0.45,
    val chaseMin: Double = 0.60
)

object RemoteConfigStore {
    private const val PREFS = "xau_sent_remote_config"
    private const val CONFIG_KEY = "config"
    private const val LAST_CHECK_KEY = "last_check"

    fun load(context: Context): RemoteConfig? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CONFIG_KEY, null) ?: return null
        return try { parse(JSONObject(raw)) } catch (_: Throwable) { null }
    }

    fun loadOrDefault(context: Context): RemoteConfig = load(context) ?: RemoteConfig()

    fun shouldRefresh(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val lastCheck = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(LAST_CHECK_KEY, 0L)
        return now - lastCheck >= RemoteConfigClient.CACHE_TTL_MS
    }

    fun save(context: Context, config: RemoteConfig, now: Long = System.currentTimeMillis()) {
        val json = JSONObject().apply {
            put("version", config.version)
            put("aiEnabled", config.aiEnabled)
            put("aiManualOnly", config.aiManualOnly)
            put("keepPreviousAi", config.keepPreviousAi)
            put("entryEnabled", config.entryEnabled)
            put("entryMinConfidence", config.entryMinConfidence)
            put("useFlow", config.useFlow)
            put("requireBos", config.requireBos)
            put("requireRetest", config.requireRetest)
            put("pathMax", config.pathMax)
            put("chaseMin", config.chaseMin)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(CONFIG_KEY, json.toString())
            .putLong(LAST_CHECK_KEY, now)
            .apply()
    }

    fun markChecked(context: Context, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(LAST_CHECK_KEY, now).apply()
    }

    fun parseResponse(body: String): RemoteConfig = parse(JSONObject(body))

    private fun parse(json: JSONObject): RemoteConfig = RemoteConfig(
        version = json.optInt("version", 1).coerceAtLeast(1),
        aiEnabled = json.optBoolean("aiEnabled", true),
        aiManualOnly = json.optBoolean("aiManualOnly", true),
        keepPreviousAi = json.optBoolean("keepPreviousAi", true),
        entryEnabled = json.optBoolean("entryEnabled", false),
        entryMinConfidence = json.optInt("entryMinConfidence", 70).coerceIn(0, 100),
        useFlow = json.optBoolean("useFlow", true),
        requireBos = json.optBoolean("requireBos", true),
        requireRetest = json.optBoolean("requireRetest", false),
        pathMax = json.optDouble("pathMax", 0.45).coerceIn(0.0, 1.0),
        chaseMin = json.optDouble("chaseMin", 0.60).coerceIn(0.0, 1.0)
    )
}

object RemoteConfigClient {
    private const val ENDPOINT = "https://script.google.com/macros/s/AKfycbwrQFQBGwt_E_i7Ey89ZsxylZx9Cjs4lKDpQxJbWzfdo0zFtOkI6se7z_7q7BzI_PHdWQ/exec?mode=config"
    private const val MAX_REDIRECTS = 4
    const val CACHE_TTL_MS = 15 * 60 * 1000L

    fun refresh(context: Context): RemoteConfig {
        val cached = RemoteConfigStore.loadOrDefault(context)
        if (!RemoteConfigStore.shouldRefresh(context)) return cached
        return try {
            val config = getJson(ENDPOINT, 0)
            RemoteConfigStore.save(context, config)
            config
        } catch (_: Throwable) {
            RemoteConfigStore.markChecked(context)
            cached
        }
    }

    private fun getJson(endpoint: String, redirectCount: Int): RemoteConfig {
        if (redirectCount > MAX_REDIRECTS || !endpoint.startsWith("https://")) throw IllegalArgumentException("HTTPS endpoint required")
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            when (val responseCode = connection.responseCode) {
                in 200..299 -> RemoteConfigStore.parseResponse(connection.inputStream.bufferedReader().use { it.readText() })
                in 301..303, in 307..308 -> {
                    val location = connection.getHeaderField("Location") ?: throw IllegalStateException("Missing redirect")
                    val next = URI(endpoint).resolve(location).toString()
                    getJson(next, redirectCount + 1)
                }
                else -> throw IllegalStateException("Config HTTP $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }
}