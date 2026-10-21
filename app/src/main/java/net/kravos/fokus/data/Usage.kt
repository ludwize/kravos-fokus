package net.kravos.fokus.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AppUsage(val pkg: String, val ms: Long, val opens: Int)

data class DayStats(
    val day: String,
    val start: Long,
    val totalMs: Long,
    /** Unlocks (or screen-on events on devices without a lock screen). */
    val unlocks: Int,
    val apps: List<AppUsage>,
    val hourly: List<Long>,
    /** First unlock / screen on of the day, epoch ms. */
    val firstPickup: Long?,
    /** False when the day had to be reconstructed from coarse daily buckets (no opens / hours). */
    val detailed: Boolean = true,
) {
    val opens: Int get() = apps.sumOf { it.opens }
    fun msFor(pkgs: Collection<String>): Long = apps.filter { it.pkg in pkgs }.sumOf { it.ms }
    fun opensFor(pkgs: Collection<String>): Int = apps.filter { it.pkg in pkgs }.sumOf { it.opens }

    fun toJson(): JSONObject = JSONObject()
        .put("day", day).put("start", start).put("total", totalMs).put("unlocks", unlocks)
        .put("apps", JSONArray(apps.map { JSONObject().put("p", it.pkg).put("m", it.ms).put("o", it.opens) }))
        .put("hourly", JSONArray(hourly))
        .put("first", firstPickup ?: -1L)
        .put("detailed", detailed)

    companion object {
        fun fromJson(o: JSONObject): DayStats {
            val a = o.getJSONArray("apps")
            val h = o.getJSONArray("hourly")
            return DayStats(
                o.getString("day"), o.getLong("start"), o.getLong("total"), o.optInt("unlocks"),
                (0 until a.length()).map { a.getJSONObject(it).let { x -> AppUsage(x.getString("p"), x.getLong("m"), x.optInt("o")) } },
                (0 until h.length()).map { h.getLong(it) },
                o.optLong("first", -1L).takeIf { it > 0 },
                o.optBoolean("detailed", true),
            )
        }
    }
}

/**
 * Screen-time numbers computed from the system's UsageEvents stream (same source Digital Wellbeing uses).
 * Finished days are cached on disk, because the system only keeps detailed events for about a week.
 */
class UsageEngine(private val ctx: Context, private val apps: Apps) {
    private val usm = ctx.getSystemService(UsageStatsManager::class.java)
    private val dir = File(ctx.filesDir, "days").apply { mkdirs() }
    private val lock = Mutex()
    @Volatile private var todayCache: Pair<Long, DayStats>? = null

    fun hasAccess(): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Today so far. Cached for [maxAgeMs]. */
    suspend fun today(maxAgeMs: Long = 20_000): DayStats = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        todayCache?.let { (at, d) -> if (now - at < maxAgeMs && d.day == Day.key(now)) return@withContext d }
        val d = compute(Day.start(now), now)
        todayCache = now to d
        d
    }

    /** Synchronous variant for the guard service: (time computed, stats). */
    fun todaySnapshot(maxAgeMs: Long = 15_000): Pair<Long, DayStats> {
        val now = System.currentTimeMillis()
        todayCache?.let { c -> if (now - c.first < maxAgeMs && c.second.day == Day.key(now)) return c }
        return (now to compute(Day.start(now), now)).also { todayCache = it }
    }

    fun invalidateToday() { todayCache = null }

    /** Stats for the day [daysAgo] days before today (0 = today). */
    suspend fun day(daysAgo: Int): DayStats = if (daysAgo == 0) today() else lock.withLock {
        withContext(Dispatchers.IO) {
            val start = Day.startMinus(daysAgo)
            val key = Day.key(start)
            val f = File(dir, "$key.json")
            if (f.exists()) runCatching { return@withContext DayStats.fromJson(JSONObject(f.readText())) }
            var d = compute(start, Day.nextStart(start))
            if (d.totalMs == 0L && daysAgo > 5) d = coarse(start, Day.nextStart(start)) ?: d
            // Only persist days that actually had data: an empty day may just mean we lacked permission.
            if (d.totalMs > 0) runCatching { f.writeText(d.toJson().toString()) }
            d
        }
    }

    /** Oldest-first list of the last [n] days, today included. */
    suspend fun lastDays(n: Int): List<DayStats> = (n - 1 downTo 0).map { day(it) }

    private fun compute(start: Long, end: Long): DayStats {
        val ignored = apps.ignored()
        val time = HashMap<String, Long>()
        val opens = HashMap<String, Int>()
        val hourly = LongArray(24)
        var unlocks = 0
        var screenOns = 0
        var firstUnlock: Long? = null
        var firstScreenOn: Long? = null

        var cur: String? = null
        var curStart = 0L
        var lastPkg: String? = null
        var lastEnd = 0L

        fun add(pkg: String, a0: Long, b0: Long) {
            val a = maxOf(a0, start)
            val b = minOf(b0, end)
            if (b <= a) return
            time[pkg] = (time[pkg] ?: 0L) + (b - a)
            var t = a
            while (t < b) {
                val h = ((t - start) / 3_600_000L).toInt().coerceIn(0, 23)
                val hourEnd = start + (h + 1) * 3_600_000L
                val segEnd = if (h == 23) b else minOf(b, hourEnd)
                hourly[h] += segEnd - t
                t = segEnd
            }
        }

        fun close(ts: Long) {
            val c = cur ?: return
            add(c, curStart, ts)
            lastPkg = c
            lastEnd = ts
            cur = null
        }

        try {
            val ev = usm.queryEvents(start - 3 * 3_600_000L, end)
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                val ts = e.timeStamp
                val p = e.packageName ?: continue
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (cur == p) continue
                        close(ts)
                        if (p in ignored) { lastPkg = p; lastEnd = ts; continue }
                        val sameAsBefore = lastPkg == p && ts - lastEnd < 3_000
                        cur = p
                        curStart = ts
                        if (!sameAsBefore && ts >= start) opens[p] = (opens[p] ?: 0) + 1
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED, ACTIVITY_STOPPED -> if (p == cur) close(ts)
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> { close(ts); lastPkg = null }
                    UsageEvents.Event.KEYGUARD_HIDDEN -> if (ts >= start) {
                        unlocks++
                        if (firstUnlock == null) firstUnlock = ts
                    }
                    UsageEvents.Event.SCREEN_INTERACTIVE -> if (ts >= start) {
                        screenOns++
                        if (firstScreenOn == null) firstScreenOn = ts
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "usage events failed: ${t.message}")
        }
        close(end)

        val list = (time.keys + opens.keys).toSet()
            .map { AppUsage(it, time[it] ?: 0L, opens[it] ?: 0) }
            .filter { it.ms >= 1_000 || it.opens > 0 }
            .sortedByDescending { it.ms }
        return DayStats(
            day = Day.key(start),
            start = start,
            totalMs = list.sumOf { it.ms },
            unlocks = if (unlocks > 0) unlocks else screenOns,
            apps = list,
            hourly = hourly.toList(),
            firstPickup = firstUnlock ?: firstScreenOn,
        )
    }

    /** Fallback for old days whose events the system already dropped: per-app totals only. */
    private fun coarse(start: Long, end: Long): DayStats? = runCatching {
        val ignored = apps.ignored()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .filter { it.firstTimeStamp >= start - 3_600_000 && it.firstTimeStamp < end }
            .groupBy { it.packageName }
            .map { (p, l) -> AppUsage(p, l.sumOf { it.totalTimeInForeground }, 0) }
            .filter { it.pkg !in ignored && it.ms >= 1_000 }
            .sortedByDescending { it.ms }
        if (stats.isEmpty()) null
        else DayStats(Day.key(start), start, stats.sumOf { it.ms }, 0, stats, List(24) { 0L }, null, detailed = false)
    }.getOrNull()

    companion object {
        const val TAG = "KravosFokus"
        /** UsageEvents.Event.ACTIVITY_STOPPED (API 29). Same numeric value on older releases is unused. */
        private const val ACTIVITY_STOPPED = 23
    }
}
