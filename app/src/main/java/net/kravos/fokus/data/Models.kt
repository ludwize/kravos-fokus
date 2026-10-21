package net.kravos.fokus.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Day key in local time, e.g. "2026-10-21". */
object Day {
    private val fmt = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    fun key(ms: Long = System.currentTimeMillis()): String = fmt.get()!!.format(ms)
    fun start(ms: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    /** Start of the day [n] days before the day containing [ms]. */
    fun startMinus(n: Int, ms: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = start(ms); add(Calendar.DAY_OF_YEAR, -n)
    }.timeInMillis
    fun nextStart(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = start(ms); add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
    /** 0 = Monday … 6 = Sunday. */
    fun dow(cal: Calendar): Int = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    fun minuteOfDay(cal: Calendar): Int = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

val DAY_SHORT = listOf("Pon", "Uto", "Sre", "Čet", "Pet", "Sub", "Ned")
const val ALL_DAYS = 0b1111111
const val WORK_DAYS = 0b0011111
const val WEEKEND = 0b1100000

fun hm(min: Int): String = "%02d:%02d".format((min / 60) % 24, min % 60)

/**
 * A recurring time range during which the group's apps are blocked.
 * [days] is a bitmask (bit 0 = Monday). [from] / [to] are minutes of the day; to <= from crosses midnight
 * (the part after midnight belongs to the day the window started on). from == to means the whole day.
 */
data class TimeWindow(val days: Int, val from: Int, val to: Int) {
    private fun has(d: Int) = days and (1 shl ((d + 7) % 7)) != 0

    fun activeAt(cal: Calendar): Boolean {
        val d = Day.dow(cal)
        val m = Day.minuteOfDay(cal)
        return when {
            from == to -> has(d)
            from < to -> has(d) && m >= from && m < to
            else -> (has(d) && m >= from) || (has(d - 1) && m < to)
        }
    }

    /** End of the currently active occurrence (epoch ms). */
    fun endAfter(now: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val m = Day.minuteOfDay(cal)
        val base = Day.start(now)
        return when {
            from == to -> Day.nextStart(now)
            from < to -> base + to * 60_000L
            m >= from -> Day.nextStart(now) + to * 60_000L
            else -> base + to * 60_000L
        }
    }

    fun daysLabel(): String = when (days) {
        ALL_DAYS -> "Svaki dan"
        WORK_DAYS -> "Radnim danima"
        WEEKEND -> "Vikendom"
        else -> (0..6).filter { days and (1 shl it) != 0 }.joinToString(", ") { DAY_SHORT[it] }
    }

    fun label(): String = if (from == to) "Ceo dan · ${daysLabel()}" else "${hm(from)}–${hm(to)} · ${daysLabel()}"

    fun toJson() = JSONObject().put("d", days).put("f", from).put("t", to)

    companion object {
        fun fromJson(o: JSONObject) = TimeWindow(o.optInt("d", ALL_DAYS), o.optInt("f"), o.optInt("t"))
    }
}

/** A group of apps with its limits. */
data class Rule(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val apps: List<String> = emptyList(),
    /** Daily time limit for the whole group, minutes. 0 = none. */
    val limitMin: Int = 0,
    /** Daily opens limit for the whole group. 0 = none. */
    val opensLimit: Int = 0,
    val windows: List<TimeWindow> = emptyList(),
    val strict: Boolean = false,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** For strict rules: an edit that only takes effect from the next day. */
    val pending: Rule? = null,
    val pendingDay: String? = null,
) {
    fun summary(): String {
        val parts = mutableListOf<String>()
        if (limitMin > 0) parts += "${fmtMin(limitMin)} dnevno"
        if (opensLimit > 0) parts += "$opensLimit otvaranja"
        if (windows.isNotEmpty()) parts += if (windows.size == 1) windows[0].label() else "${windows.size} rasporeda"
        if (parts.isEmpty()) parts += "Samo praćenje"
        return parts.joinToString(" · ")
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name)
        .put("apps", JSONArray(apps))
        .put("limit", limitMin).put("opens", opensLimit)
        .put("win", JSONArray(windows.map { it.toJson() }))
        .put("strict", strict).put("on", enabled).put("created", createdAt)
        .also { o -> pending?.let { o.put("pending", it.toJson()); o.put("pendingDay", pendingDay) } }

    companion object {
        fun fromJson(o: JSONObject): Rule = Rule(
            id = o.optString("id"),
            name = o.optString("name"),
            apps = o.optJSONArray("apps")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            limitMin = o.optInt("limit"),
            opensLimit = o.optInt("opens"),
            windows = o.optJSONArray("win")?.let { a -> (0 until a.length()).map { TimeWindow.fromJson(a.getJSONObject(it)) } } ?: emptyList(),
            strict = o.optBoolean("strict"),
            enabled = o.optBoolean("on", true),
            createdAt = o.optLong("created"),
            pending = o.optJSONObject("pending")?.let { fromJson(it) },
            pendingDay = o.optString("pendingDay").ifEmpty { null },
        )
    }
}

/** A running focus session. Empty [ruleIds] = every group. */
data class Session(val startAt: Long, val endAt: Long, val ruleIds: List<String>, val strict: Boolean) {
    fun active(now: Long = System.currentTimeMillis()) = now in startAt until endAt
    fun toJson() = JSONObject().put("s", startAt).put("e", endAt).put("r", JSONArray(ruleIds)).put("x", strict)

    companion object {
        fun fromJson(o: JSONObject) = Session(
            o.optLong("s"), o.optLong("e"),
            o.optJSONArray("r")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            o.optBoolean("x"),
        )
    }
}

data class SessionLog(val startAt: Long, val plannedMin: Int, val actualMin: Int, val completed: Boolean) {
    fun toJson() = JSONObject().put("s", startAt).put("p", plannedMin).put("a", actualMin).put("c", completed)

    companion object {
        fun fromJson(o: JSONObject) = SessionLog(o.optLong("s"), o.optInt("p"), o.optInt("a"), o.optBoolean("c"))
    }
}

/** Short-form video feeds that can be blocked inside otherwise allowed apps. */
enum class ShortsTarget(val pkg: String, val app: String, val feature: String) {
    YOUTUBE("com.google.android.youtube", "YouTube", "Shorts"),
    INSTAGRAM("com.instagram.android", "Instagram", "Reels"),
    FACEBOOK("com.facebook.katana", "Facebook", "Reels"),
    SNAPCHAT("com.snapchat.android", "Snapchat", "Spotlight"),
    TIKTOK("com.zhiliaoapp.musically", "TikTok", "cela aplikacija");

    companion object {
        val TIKTOK_PKGS = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go")
        fun forPkg(p: String): ShortsTarget? =
            if (p in TIKTOK_PKGS) TIKTOK else entries.firstOrNull { it.pkg == p }
    }
}

fun fmtMin(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${min / 60} h ${min % 60} min"
}

fun fmtMs(ms: Long): String {
    val m = (ms / 60_000).toInt()
    return when {
        ms in 1 until 60_000 -> "<1 min"
        m < 60 -> "$m min"
        else -> "${m / 60} h ${m % 60} min"
    }
}

/** Compact form for charts: "3h 12m" / "45m". */
fun fmtShort(ms: Long): String {
    val m = (ms / 60_000).toInt()
    return if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
}
