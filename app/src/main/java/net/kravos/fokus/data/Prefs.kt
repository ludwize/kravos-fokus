package net.kravos.fokus.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * All settings and rule state. The accessibility service and the UI live in the same process,
 * so they share this single instance (App.prefs) and see each other's changes through the flows.
 */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("fokus", Context.MODE_PRIVATE)

    // ---------- appearance / misc ----------
    private val _theme = MutableStateFlow(runCatching { ThemeMode.valueOf(sp.getString("theme", "DARK")!!) }.getOrDefault(ThemeMode.DARK))
    val theme: StateFlow<ThemeMode> = _theme
    fun setTheme(t: ThemeMode) { _theme.value = t; sp.edit { putString("theme", t.name) } }

    var autoUpdateCheck: Boolean
        get() = sp.getBoolean("autoUpdate", true)
        set(v) = sp.edit { putBoolean("autoUpdate", v) }
    var skippedBuild: Int
        get() = sp.getInt("skippedBuild", 0)
        set(v) = sp.edit { putInt("skippedBuild", v) }

    // ---------- goals / report ----------
    private val _goal = MutableStateFlow(sp.getInt("goalMin", 180))
    val goalMin: StateFlow<Int> = _goal
    fun setGoal(min: Int) { _goal.value = min; sp.edit { putInt("goalMin", min) } }

    var reportEnabled: Boolean
        get() = sp.getBoolean("report", true)
        set(v) = sp.edit { putBoolean("report", v) }
    var reportMinute: Int
        get() = sp.getInt("reportMin", 21 * 60 + 30)
        set(v) = sp.edit { putInt("reportMin", v) }
    var lastReportDay: String?
        get() = sp.getString("lastReport", null)
        set(v) = sp.edit { putString("lastReport", v) }
    var limitWarnings: Boolean
        get() = sp.getBoolean("warn", true)
        set(v) = sp.edit { putBoolean("warn", v) }

    /** Blocks the app-info / accessibility screens for this app while a strict block is in force. */
    var protectUninstall: Boolean
        get() = sp.getBoolean("protect", false)
        set(v) = sp.edit { putBoolean("protect", v) }

    // ---------- rules ----------
    private val _rules = MutableStateFlow(loadRules())
    val rules: StateFlow<List<Rule>> = _rules

    private fun loadRules(): List<Rule> {
        val a = runCatching { JSONArray(sp.getString("rules", "[]")) }.getOrDefault(JSONArray())
        return (0 until a.length()).mapNotNull { runCatching { Rule.fromJson(a.getJSONObject(it)) }.getOrNull() }
    }

    private fun saveRules(list: List<Rule>) {
        _rules.value = list
        sp.edit { putString("rules", JSONArray(list.map { it.toJson() }).toString()) }
    }

    /** Applies strict-rule edits whose day has come. Called by the guard and on app start. */
    fun applyPending(today: String = Day.key()) {
        val cur = _rules.value
        var changed = false
        val out = cur.mapNotNull { r ->
            val p = r.pending
            if (p != null && r.pendingDay != null && today > r.pendingDay) {
                changed = true
                if (p.name == DELETE_MARK) null else p.copy(id = r.id, createdAt = r.createdAt, pending = null, pendingDay = null)
            } else r
        }
        if (changed) saveRules(out)
    }

    /** A strict rule can be changed freely in its first 10 minutes (to fix mistakes). */
    fun isLocked(r: Rule) = r.strict && System.currentTimeMillis() - r.createdAt > 10 * 60_000L

    sealed interface SaveResult {
        data object Saved : SaveResult
        data object Scheduled : SaveResult
    }

    fun upsert(rule: Rule): SaveResult {
        val cur = _rules.value
        val old = cur.firstOrNull { it.id == rule.id }
        return if (old != null && isLocked(old)) {
            saveRules(cur.map { if (it.id == old.id) old.copy(pending = rule.copy(pending = null, pendingDay = null), pendingDay = Day.key()) else it })
            SaveResult.Scheduled
        } else {
            saveRules(if (old == null) cur + rule else cur.map { if (it.id == rule.id) rule.copy(pending = null, pendingDay = null) else it })
            SaveResult.Saved
        }
    }

    fun delete(id: String): SaveResult {
        val cur = _rules.value
        val old = cur.firstOrNull { it.id == id } ?: return SaveResult.Saved
        return if (isLocked(old)) {
            saveRules(cur.map { if (it.id == id) old.copy(pending = old.copy(name = DELETE_MARK, pending = null), pendingDay = Day.key()) else it })
            SaveResult.Scheduled
        } else {
            saveRules(cur.filterNot { it.id == id })
            SaveResult.Saved
        }
    }

    fun cancelPending(id: String) {
        saveRules(_rules.value.map { if (it.id == id) it.copy(pending = null, pendingDay = null) else it })
    }

    // ---------- focus session ----------
    private val _session = MutableStateFlow(sp.getString("session", null)?.let { runCatching { Session.fromJson(JSONObject(it)) }.getOrNull() })
    val session: StateFlow<Session?> = _session
    fun setSession(s: Session?) {
        _session.value = s
        sp.edit { if (s == null) remove("session") else putString("session", s.toJson().toString()) }
    }

    fun sessionLog(): List<SessionLog> {
        val a = runCatching { JSONArray(sp.getString("sessionLog", "[]")) }.getOrDefault(JSONArray())
        return (0 until a.length()).mapNotNull { runCatching { SessionLog.fromJson(a.getJSONObject(it)) }.getOrNull() }
    }

    fun logSession(l: SessionLog) {
        val list = (listOf(l) + sessionLog()).take(200)
        sp.edit { putString("sessionLog", JSONArray(list.map { it.toJson() }).toString()) }
    }

    // ---------- shorts ----------
    private val _shorts = MutableStateFlow(
        sp.getStringSet("shorts", emptySet())!!.mapNotNull { runCatching { ShortsTarget.valueOf(it) }.getOrNull() }.toSet(),
    )
    val shorts: StateFlow<Set<ShortsTarget>> = _shorts
    fun setShorts(t: ShortsTarget, on: Boolean) {
        val s = if (on) _shorts.value + t else _shorts.value - t
        _shorts.value = s
        sp.edit { putStringSet("shorts", s.map { it.name }.toSet()) }
    }

    // ---------- soft-mode grants & counters ----------
    private val grants = HashMap<String, Long>()

    /** "Još 5 minuta" for a soft rule. Key is a rule id, or "session". */
    fun grant(key: String, untilMs: Long) {
        grants[key] = untilMs
        val day = Day.key()
        sp.edit { putInt("ovr_$day", overridesToday() + 1) }
    }

    fun granted(key: String, now: Long = System.currentTimeMillis()): Boolean = (grants[key] ?: 0L) > now

    fun overridesToday(day: String = Day.key()): Int = sp.getInt("ovr_$day", 0)

    /** Group opens counted by the guard (used for the opens limit). */
    fun opens(ruleId: String, day: String = Day.key()): Int = sp.getInt("op_${day}_$ruleId", 0)
    fun bumpOpens(ruleId: String, day: String = Day.key()): Int {
        val n = opens(ruleId, day) + 1
        sp.edit { putInt("op_${day}_$ruleId", n) }
        return n
    }

    /** Remembers one-shot things per day ("warned 5 min left for rule X"). */
    fun once(key: String, day: String = Day.key()): Boolean {
        val k = "once_${day}_$key"
        if (sp.getBoolean(k, false)) return false
        sp.edit { putBoolean(k, true) }
        return true
    }

    /** Drops per-day counters older than a week. */
    fun prune() {
        val cutoff = Day.key(Day.startMinus(8))
        val dead = sp.all.keys.filter { k ->
            val d = when {
                k.startsWith("op_") -> k.substring(3, 13)
                k.startsWith("ovr_") -> k.substring(4, 14)
                k.startsWith("once_") -> k.substring(5, 15)
                else -> null
            }
            d != null && d < cutoff
        }
        if (dead.isNotEmpty()) sp.edit { dead.forEach { remove(it) } }
    }

    companion object { const val DELETE_MARK = "\u0000delete" }
}
