package net.kravos.fokus.data

import java.util.Calendar
import kotlin.math.roundToInt

/** Small derived numbers for the "Danas" and "Statistika" screens. */
object Insights {
    /** Consecutive finished days (yesterday backwards) within the goal. [days] oldest-first, today excluded. */
    fun streak(days: List<DayStats>, goalMs: Long): Int {
        var n = 0
        for (d in days.asReversed()) {
            if (d.totalMs in 1..goalMs) n++ else break
        }
        return n
    }

    fun avg(days: List<DayStats>, f: (DayStats) -> Long): Long {
        val withData = days.filter { it.totalMs > 0 }
        return if (withData.isEmpty()) 0L else withData.sumOf(f) / withData.size
    }

    /** Percentage change of [cur] vs [prev]; null when there's nothing to compare. */
    fun change(cur: Long, prev: Long): Int? = if (prev <= 0 || cur <= 0) null else ((cur - prev) * 100.0 / prev).roundToInt()

    fun peakHour(days: List<DayStats>): Int? {
        val sum = LongArray(24)
        days.filter { it.detailed }.forEach { d -> d.hourly.forEachIndexed { i, v -> sum[i] += v } }
        val max = sum.maxOrNull() ?: 0L
        return if (max <= 0) null else sum.indexOfFirst { it == max }
    }

    /** Average minute of day of the first pickup. */
    fun avgFirstPickup(days: List<DayStats>): Int? {
        val mins = days.mapNotNull { d ->
            d.firstPickup?.let { Calendar.getInstance().apply { timeInMillis = it }.let { c -> Day.minuteOfDay(c) } }
        }.filter { it in 240..14 * 60 } // ignore night-owl outliers before 04:00
        return if (mins.isEmpty()) null else mins.average().roundToInt()
    }

    /** Merges several days' per-app numbers. */
    fun mergeApps(days: List<DayStats>): List<AppUsage> = days.flatMap { it.apps }
        .groupBy { it.pkg }
        .map { (p, l) -> AppUsage(p, l.sumOf { it.ms }, l.sumOf { it.opens }) }
        .sortedByDescending { it.ms }
}
