package net.kravos.fokus.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.kravos.fokus.App
import net.kravos.fokus.data.Day
import net.kravos.fokus.data.fmtMs
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Evening summary notification. Re-schedules itself for the next day after each run. */
class ReportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        val prefs = app.prefs
        try {
            val today = Day.key()
            if (prefs.reportEnabled && prefs.lastReportDay != today && app.usage.hasAccess()) {
                val (title, text) = build(app)
                Notif.report(applicationContext, title, text)
                prefs.lastReportDay = today
            }
        } finally {
            schedule(applicationContext, fromWorker = true)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "daily-report"

        suspend fun build(app: App): Pair<String, String> {
            val d = app.usage.today(0)
            val prev = (1..7).map { app.usage.day(it) }.filter { it.totalMs > 0 }
            val avg = if (prev.isEmpty()) 0L else prev.sumOf { it.totalMs } / prev.size
            val goal = app.prefs.goalMin.value * 60_000L
            val lines = mutableListOf<String>()
            if (avg > 0) {
                val pct = ((d.totalMs - avg) * 100.0 / avg).roundToInt()
                lines += if (pct <= 0) "${-pct}% manje od proseka prošle nedelje" else "$pct% više od proseka prošle nedelje"
            }
            lines += "${d.unlocks} otključavanja · ${d.opens} otvaranja aplikacija"
            d.apps.take(3).takeIf { it.isNotEmpty() }?.let { top ->
                lines += "Najviše: " + top.joinToString(", ") { "${app.apps.label(it.pkg)} ${fmtMs(it.ms)}" }
            }
            val rules = app.prefs.rules.value.filter { it.enabled && it.apps.isNotEmpty() }
            if (rules.isNotEmpty()) lines += "Ometajuće: ${fmtMs(d.msFor(rules.flatMap { it.apps }.toSet()))}"
            val ovr = app.prefs.overridesToday()
            if (ovr > 0) lines += "Produžavanja: $ovr×"
            val title = "Danas ${fmtMs(d.totalMs)} " + if (d.totalMs <= goal) "· cilj ispunjen ✓" else "· preko cilja"
            return title to lines.joinToString("\n")
        }

        fun schedule(ctx: Context, fromWorker: Boolean = false) {
            val app = ctx.applicationContext as App
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                timeInMillis = Day.start(now)
                add(Calendar.MINUTE, app.prefs.reportMinute)
            }
            if (cal.timeInMillis <= now + 30_000) cal.add(Calendar.DAY_OF_YEAR, 1)
            val req = OneTimeWorkRequestBuilder<ReportWorker>()
                .setInitialDelay(cal.timeInMillis - now, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                NAME, if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, req,
            )
        }

        /** Sends the report right away (from Settings, to preview it). */
        fun runNow(ctx: Context) {
            WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<PreviewWorker>().build())
        }
    }
}

class PreviewWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        if (app.usage.hasAccess()) {
            val (t, x) = ReportWorker.build(app)
            Notif.report(applicationContext, t, x)
        }
        return Result.success()
    }
}
