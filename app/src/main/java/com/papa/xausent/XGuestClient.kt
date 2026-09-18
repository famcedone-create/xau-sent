package com.papa.xausent

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object XGuestClient {
    private const val BEARER = "AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA"
    private const val USER_QUERY = "KybxDj9RrADIITXlGG8kpw"
    private const val TWEETS_QUERY = "jeAA-59Y9FL7FmjgBNIVPw"
    private val watchlist = listOf(
        "Team_XAUUSD", "EaconomySignals", "DanielGoldTrade",
        "MARKE_TMASTER", "Prince_ict_smc", "TradingPulseFx"
    )
    private val goldMark = Regex("\\b(xauusd[m]?|xau/usd|[$]xau|[$]gold|#xauusd|#gold|#xau)\\b", RegexOption.IGNORE_CASE)
    private val goldWord = Regex("\\b(gold|oro)\\b", RegexOption.IGNORE_CASE)
    private val tradeWord = Regex("\\b(buy|sell|long|short|entry|tp\\d*|take\\s*profit|sl|stop\\s*loss|exit|chiud|target)\\b", RegexOption.IGNORE_CASE)
    private val buy = Regex("\\b(buy|long|entry\\s+buy|buy\\s+(now|zone|side|alert|gold|xau)|going\\s+long|compro|acquisto)\\b", RegexOption.IGNORE_CASE)
    private val sell = Regex("\\b(sell|short|entry\\s+sell|sell\\s+(now|zone|side|alert|gold|xau)|going\\s+short|vendo|vendita)\\b", RegexOption.IGNORE_CASE)
    private val exit = Regex("\\b(tp\\s*(hit|done)|hit\\s*tp|closed|close\\s+now|chiud[oa]|booked|breakeven|be|sl\\s*hit|stop\\s*hit|exit|pips?\\s+profit|profit\\s+done|in\\s+profit)\\b", RegexOption.IGNORE_CASE)
    private val tp = Regex("\\b(tp\\d*|take\\s*profit|target)\\b", RegexOption.IGNORE_CASE)
    private val twitterDate = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US)

    fun fetch(): XFlowData {
        return try {
            val session = createGuestSession() ?: return unavailable()
            val posts = dedupe(watchlist.flatMap { handle ->
                try {
                    val user = gqlUser(session, handle) ?: return@flatMap emptyList()
                    gqlTweets(session, user)
                } catch (_: Throwable) {
                    emptyList()
                }
            }).filter { it.postedAt >= System.currentTimeMillis() - 10 * 60_000L && it.postedAt <= System.currentTimeMillis() }
            summarize(posts)
        } catch (_: Throwable) {
            unavailable()
        }
    }

    private fun unavailable() = XFlowData("non disponibile")

    private fun summarize(posts: List<XPost>): XFlowData {
        if (posts.isEmpty()) return XFlowData("nessun post")
        val buyEntries = posts.count { it.isBuy && !it.isExit && !it.hasTp }
        val sellEntries = posts.count { it.isSell && !it.isExit && !it.hasTp }
        val totalEntries = buyEntries + sellEntries
        val buyPct = if (totalEntries == 0) 0 else buyEntries * 100 / totalEntries
        val sellPct = if (totalEntries == 0) 0 else 100 - buyPct
        val sample = when {
            posts.size >= 8 -> "campione buono"
            posts.size >= 4 -> "campione medio"
            else -> "campione basso"
        }
        return XFlowData("attivo", buyPct, sellPct, buyEntries, sellEntries, posts.size, sample)
    }

    private fun dedupe(posts: List<XPost>): List<XPost> {
        val seen = HashSet<String>()
        return posts.filter { seen.add(it.id) }
    }

    private fun classify(id: String, text: String, postedAt: Long): XPost? {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return null
        val goldRelated = goldMark.containsMatchIn(normalized) ||
            (goldWord.containsMatchIn(normalized) && tradeWord.containsMatchIn(normalized))
        if (!goldRelated) return null
        val isBuy = buy.containsMatchIn(normalized)
        val isSell = sell.containsMatchIn(normalized)
        val hasTp = tp.containsMatchIn(normalized)
        val isExit = exit.containsMatchIn(normalized)
        if (!isBuy && !isSell && !isExit && !hasTp) return null
        return XPost(id, postedAt, isBuy, isSell, isExit, hasTp)
    }

    private fun createGuestSession(): GuestSession? {
        val connection = open("https://api.twitter.com/1.1/guest/activate.json", "POST", null)
        return try {
            if (connection.responseCode !in 200..299) return null
            val token = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optString("guest_token")
            if (token.isBlank()) null else GuestSession(token, "guest_id=v1%3A$token")
        } finally { connection.disconnect() }
    }

    private fun gqlUser(session: GuestSession, handle: String): GqlUser? {
        val variables = JSONObject().put("screen_name", handle).put("withSafetyModeUserFields", true)
        val url = "https://x.com/i/api/graphql/$USER_QUERY/UserByScreenName?${query(variables, userFeatures())}"
        val connection = open(url, "GET", headers(session, "https://x.com/$handle"))
        return try {
            if (connection.responseCode !in 200..299) return null
            val result = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                .optJSONObject("data")?.optJSONObject("user")?.optJSONObject("result") ?: return null
            GqlUser(result.optString("rest_id"), result.optJSONObject("core")?.optString("screen_name").orEmpty(), result.optJSONObject("core")?.optString("name").orEmpty())
        } finally { connection.disconnect() }
    }

    private fun gqlTweets(session: GuestSession, user: GqlUser): List<XPost> {
        val variables = JSONObject()
            .put("userId", user.id).put("count", 12).put("includePromotedContent", false)
            .put("withQuickPromoteEligibilityTweetFields", true).put("withVoice", true).put("withV2Timeline", true)
        val url = "https://x.com/i/api/graphql/$TWEETS_QUERY/UserTweets?${query(variables, tweetFeatures())}"
        val connection = open(url, "GET", headers(session, "https://x.com/${user.handle}"))
        return try {
            if (connection.responseCode !in 200..299) return emptyList()
            val out = mutableListOf<XPost>()
            walk(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }), out)
            out
        } finally { connection.disconnect() }
    }

    private fun walk(value: Any?, out: MutableList<XPost>) {
        when (value) {
            is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i), out)
            is JSONObject -> {
                val result = value.optJSONObject("result")
                val tweet = when {
                    result?.optString("__typename") == "Tweet" -> result
                    value.optString("__typename") == "Tweet" -> value
                    else -> null
                }
                if (tweet != null) {
                    val legacy = tweet.optJSONObject("legacy") ?: tweet
                    val id = tweet.optString("rest_id", legacy.optString("id_str"))
                    val text = legacy.optString("full_text", legacy.optString("text"))
                    val created = parseTwitterDate(legacy.optString("created_at"))
                    if (id.isNotBlank() && text.isNotBlank() && created != null) classify(id, text, created)?.let(out::add)
                }
                val keys = value.keys()
                while (keys.hasNext()) walk(value.opt(keys.next()), out)
            }
        }
    }

    private fun parseTwitterDate(value: String): Long? = try {
        twitterDate.timeZone = TimeZone.getTimeZone("UTC")
        twitterDate.parse(value)?.time
    } catch (_: Throwable) { null }

    private fun query(variables: JSONObject, features: JSONObject) = "variables=${encode(variables.toString())}&features=${encode(features.toString())}"
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun open(url: String, method: String, extra: Map<String, String>?): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 8000; readTimeout = 10000
            setRequestProperty("Authorization", "Bearer $BEARER")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128 Safari/537.36")
            extra?.forEach { (key, value) -> setRequestProperty(key, value) }
        }

    private fun headers(session: GuestSession, referer: String) = mapOf(
        "x-guest-token" to session.token, "x-twitter-client-language" to "en",
        "x-twitter-active-user" to "yes", "Cookie" to session.cookie, "Referer" to referer
    )

    private fun userFeatures() = JSONObject().put("hidden_profile_subscriptions_enabled", true).put("rweb_tipjar_consumption_enabled", true).put("responsive_web_graphql_exclude_directive_enabled", true).put("verified_phone_label_enabled", false).put("subscriptions_verification_info_is_identity_verified_enabled", true).put("subscriptions_verification_info_verified_since_enabled", true).put("highlights_tweets_tab_ui_enabled", true).put("responsive_web_twitter_article_notes_tab_enabled", true).put("subscriptions_feature_can_gift_premium", true).put("creator_subscriptions_tweet_preview_api_enabled", true).put("responsive_web_graphql_skip_user_profile_image_extensions_enabled", false).put("responsive_web_graphql_timeline_navigation_enabled", true)

    private fun tweetFeatures() = JSONObject().put("rweb_video_screen_enabled", false).put("rweb_cashtags_enabled", true).put("profile_label_improvements_pcf_label_in_post_enabled", true).put("responsive_web_profile_redirect_enabled", true).put("rweb_tipjar_consumption_enabled", true).put("verified_phone_label_enabled", false).put("creator_subscriptions_tweet_preview_api_enabled", true).put("responsive_web_graphql_timeline_navigation_enabled", true).put("premium_content_api_read_enabled", false).put("communities_web_enable_tweet_community_results_fetch", true).put("c9s_tweet_anatomy_moderator_badge_enabled", true).put("articles_preview_enabled", true).put("responsive_web_edit_tweet_api_enabled", true).put("graphql_is_translatable_rweb_tweet_is_translatable_enabled", true).put("view_counts_everywhere_api_enabled", true).put("longform_notetweets_consumption_enabled", true).put("longform_notetweets_rich_text_read_enabled", true).put("longform_notetweets_inline_media_enabled", true).put("freedom_of_speech_not_reach_fetch_enabled", true).put("standardized_nudges_misinfo", true).put("tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled", true).put("responsive_web_enhance_cards_enabled", false).put("responsive_web_graphql_exclude_directive_enabled", true)

    private data class GuestSession(val token: String, val cookie: String)
    private data class GqlUser(val id: String, val handle: String, val name: String)
    private data class XPost(val id: String, val postedAt: Long, val isBuy: Boolean, val isSell: Boolean, val isExit: Boolean, val hasTp: Boolean)
}