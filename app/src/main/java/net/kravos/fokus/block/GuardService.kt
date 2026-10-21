package net.kravos.fokus.block

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import net.kravos.fokus.App
import net.kravos.fokus.data.Day
import net.kravos.fokus.data.Rule
import net.kravos.fokus.data.SessionLog
import net.kravos.fokus.data.ShortsTarget
import net.kravos.fokus.data.UsageEngine
import net.kravos.fokus.data.fmtMin
import net.kravos.fokus.work.Notif

/**
 * The enforcement loop. Watches which app is in front (window-state events), blocks it when a group's
 * schedule, limit or a focus session says so, and pops out of Shorts/Reels players.
 * All decisions run on one background thread; only UI calls hop to the main thread.
 */
class GuardService : AccessibilityService() {
    private lateinit var app: App
    private val main = Handler(Looper.getMainLooper())
    private lateinit var worker: Handler
    private var thread: HandlerThread? = null

    @Volatile private var fgPkg: String? = null
    @Volatile private var fgSince = 0L
    private var lastBlockPkg: String? = null
    private var lastBlockAt = 0L
    private var lastShortsCheck = 0L
    private var lastShortsAction = 0L
    private var lastProtectAt = 0L
    private var dayKey = Day.key()

    override fun onServiceConnected() {
        super.onServiceConnected()
        app = application as App
        thread = HandlerThread("fokus-guard").also { it.start() }
        worker = Handler(thread!!.looper)
        running = true
        worker.post(tick)
        Log.i(UsageEngine.TAG, "guard connected")
    }

    override fun onDestroy() {
        running = false
        thread?.quitSafely()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || !::worker.isInitialized) return
        val pkg = e.packageName?.toString() ?: return
        val type = e.eventType
        val cls = e.className?.toString()
        worker.post { handle(pkg, type, cls) }
    }

    private fun handle(pkg: String, type: Int, cls: String?) {
        val apps = app.apps
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            protect(pkg)
            if (pkg in apps.transient()) return
            if (pkg != fgPkg) onForeground(pkg)
        }
        val target = ShortsTarget.forPkg(pkg)
        if (target != null && target != ShortsTarget.TIKTOK && target in app.prefs.shorts.value && pkg == fgPkg) checkShorts(target)
    }

    private fun onForeground(pkg: String) {
        val prev = fgPkg
        fgPkg = pkg
        fgSince = System.currentTimeMillis()
        if (pkg in app.apps.launchers()) return
        // count group opens: entering a group's app from outside that group
        for (r in app.prefs.rules.value) {
            if (r.enabled && pkg in r.apps && (prev == null || prev !in r.apps) && r.opensLimit > 0) app.prefs.bumpOpens(r.id)
        }
        if (ShortsTarget.forPkg(pkg) == ShortsTarget.TIKTOK && ShortsTarget.TIKTOK in app.prefs.shorts.value) {
            blockShortsApp(pkg)
            return
        }
        evaluate(pkg, fresh = true)
    }

    /** Checks the app in front against all rules. */
    private fun evaluate(pkg: String, fresh: Boolean) {
        if (pkg in app.apps.ignored()) return
        val prefs = app.prefs
        val rules = prefs.rules.value
        if (rules.none { it.enabled && pkg in it.apps }) return
        val (at, today) = if (app.usage.hasAccess()) app.usage.todaySnapshot(if (fresh) 0 else 15_000) else (0L to null)
        val now = System.currentTimeMillis()
        val used: (Rule) -> Long = { r ->
            val base = today?.msFor(r.apps) ?: 0L
            val live = if (pkg in r.apps && fgPkg == pkg) now - maxOf(at, fgSince) else 0L
            base + maxOf(0L, live)
        }
        val b = Engine.check(pkg, rules, prefs.session.value, prefs, used, now)
        if (b != null) {
            block(pkg, b)
            return
        }
        // gentle heads-up a few minutes before a time limit runs out
        if (prefs.limitWarnings) {
            for (r in rules) {
                if (!r.enabled || r.limitMin <= 0 || pkg !in r.apps) continue
                val left = r.limitMin * 60_000L - used(r)
                if (left in 1..5 * 60_000L && prefs.once("warn5_${r.id}")) {
                    Notif.warn(this, r.id, "${r.name}: još oko ${(left / 60_000).coerceAtLeast(1)} min", "Dnevni limit od ${fmtMin(r.limitMin)} uskoro ističe.")
                }
            }
        }
    }

    private fun block(pkg: String, b: Block) {
        val now = System.currentTimeMillis()
        if (pkg == lastBlockPkg && now - lastBlockAt < 1500) return
        lastBlockPkg = pkg
        lastBlockAt = now
        Log.i(UsageEngine.TAG, "block $pkg: ${b.kind} ${b.title}")
        main.post {
            performGlobalAction(GLOBAL_ACTION_HOME)
            startActivity(BlockActivity.intent(this, pkg, b))
        }
    }

    private fun blockShortsApp(pkg: String) {
        block(pkg, Block(Kind.SHORTS, "TikTok je blokiran", "Kratki video feedovi su isključeni u Kravos Fokusu.", true, "shorts", null, null))
    }

    private fun checkShorts(target: ShortsTarget) {
        val now = System.currentTimeMillis()
        if (now - lastShortsCheck < 350 || now - lastShortsAction < 1200) return
        lastShortsCheck = now
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        if (root.packageName?.toString() != target.pkg) return
        if (Shorts.detect(target, root)) {
            lastShortsAction = now
            Log.i(UsageEngine.TAG, "shorts blocked in ${target.app}")
            main.post {
                performGlobalAction(GLOBAL_ACTION_BACK)
                Toast.makeText(this, "${target.feature} su blokirani", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Optional: keep the app-info / accessibility pages for this app shut while something strict is active. */
    private fun protect(pkg: String) {
        val prefs = app.prefs
        if (!prefs.protectUninstall || pkg !in PROTECTED_PKGS) return
        val now = System.currentTimeMillis()
        if (now - lastProtectAt < 800) return
        val today = if (app.usage.hasAccess()) app.usage.todaySnapshot(60_000).second else null
        if (!Engine.strictActive(prefs.rules.value, prefs.session.value, today, prefs, now)) return
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        val hit = runCatching { root.findAccessibilityNodeInfosByText("Kravos Fokus") }.getOrNull().orEmpty().isNotEmpty()
        if (hit) {
            lastProtectAt = now
            main.post {
                performGlobalAction(GLOBAL_ACTION_BACK)
                Toast.makeText(this, "Zaključano dok traje strogo pravilo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                housekeeping()
                val pm = getSystemService(PowerManager::class.java)
                val p = fgPkg
                if (pm.isInteractive && p != null) evaluate(p, fresh = false)
            } catch (t: Throwable) {
                Log.w(UsageEngine.TAG, "tick failed", t)
            }
            if (running) worker.postDelayed(this, 4_000)
        }
    }

    private fun housekeeping() {
        val prefs = app.prefs
        val today = Day.key()
        if (today != dayKey) {
            dayKey = today
            prefs.applyPending(today)
            prefs.prune()
            app.usage.invalidateToday()
        }
        val s = prefs.session.value ?: return
        val now = System.currentTimeMillis()
        if (now >= s.endAt) {
            prefs.setSession(null)
            val min = ((s.endAt - s.startAt) / 60_000).toInt()
            prefs.logSession(SessionLog(s.startAt, min, min, true))
            Notif.sessionDone(this, min)
        }
    }

    companion object {
        @Volatile var running = false
            private set
        private val PROTECTED_PKGS = setOf(
            "com.android.settings", "com.google.android.packageinstaller", "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
        )

        fun isEnabled(ctx: android.content.Context): Boolean {
            val s = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return s.split(':').any { it.startsWith(ctx.packageName + "/") }
        }

        fun settingsIntent() = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
