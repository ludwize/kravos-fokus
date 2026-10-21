package net.kravos.fokus.block

import net.kravos.fokus.data.DayStats
import net.kravos.fokus.data.Prefs
import net.kravos.fokus.data.Rule
import net.kravos.fokus.data.Session
import net.kravos.fokus.data.fmtMin
import net.kravos.fokus.data.hm
import java.util.Calendar

enum class Kind { SESSION, SCHEDULE, TIME, OPENS, SHORTS }

/** Why an app is blocked right now. [grantKey] is what a soft "još 5 minuta" unlocks. */
data class Block(
    val kind: Kind,
    val title: String,
    val detail: String,
    val strict: Boolean,
    val grantKey: String,
    val rule: Rule?,
    /** When the block ends by itself (schedule / session end, midnight for limits). */
    val until: Long?,
)

object Engine {
    const val GRANT_MS = 5 * 60_000L

    /**
     * Decides whether [pkg] must be blocked. [usedMs] gives a group's screen time today including the live,
     * not-yet-recorded part of the current visit.
     */
    fun check(
        pkg: String,
        rules: List<Rule>,
        session: Session?,
        prefs: Prefs,
        usedMs: (Rule) -> Long,
        now: Long = System.currentTimeMillis(),
    ): Block? {
        val enabled = rules.filter { it.enabled && pkg in it.apps }
        if (enabled.isEmpty()) return null
        val found = mutableListOf<Block>()

        if (session != null && session.active(now)) {
            val inSession = enabled.any { session.ruleIds.isEmpty() || it.id in session.ruleIds }
            if (inSession && (session.strict || !prefs.granted("session", now))) {
                found += Block(
                    Kind.SESSION, "Fokus sesija je u toku",
                    "Traje do ${hm(minuteOf(session.endAt))}. Vrati se onome što radiš.",
                    session.strict, "session", null, session.endAt,
                )
            }
        }

        val cal = Calendar.getInstance().apply { timeInMillis = now }
        for (r in enabled) {
            val soft = !r.strict && prefs.granted(r.id, now)
            if (soft) continue
            r.windows.firstOrNull { it.activeAt(cal) }?.let { w ->
                val end = w.endAfter(now)
                found += Block(
                    Kind.SCHEDULE, "${r.name}: blokirano po rasporedu",
                    "Raspored ${w.label()}. Otključava se u ${hm(minuteOf(end))}.",
                    r.strict, r.id, r, end,
                )
            }
            if (r.limitMin > 0 && usedMs(r) >= r.limitMin * 60_000L) {
                found += Block(
                    Kind.TIME, "${r.name}: dnevni limit je potrošen",
                    "Limit je ${fmtMin(r.limitMin)} dnevno. Novi dan, novi limit.",
                    r.strict, r.id, r, null,
                )
            }
            if (r.opensLimit > 0 && prefs.opens(r.id) > r.opensLimit) {
                found += Block(
                    Kind.OPENS, "${r.name}: iskorišćena sva otvaranja",
                    "Dozvoljeno je ${r.opensLimit} otvaranja dnevno.",
                    r.strict, r.id, r, null,
                )
            }
        }
        // A strict reason wins over a soft one, so a soft "još 5 min" can't sneak past a strict group.
        return found.firstOrNull { it.strict } ?: found.firstOrNull()
    }

    /** True while at least one strict block is in force somewhere (used by uninstall protection). */
    fun strictActive(rules: List<Rule>, session: Session?, today: DayStats?, prefs: Prefs, now: Long = System.currentTimeMillis()): Boolean {
        if (session != null && session.strict && session.active(now)) return true
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return rules.any { r ->
            r.enabled && r.strict && (
                r.windows.any { it.activeAt(cal) } ||
                    (r.limitMin > 0 && today != null && today.msFor(r.apps) >= r.limitMin * 60_000L) ||
                    (r.opensLimit > 0 && prefs.opens(r.id) > r.opensLimit)
                )
        }
    }

    private fun minuteOf(ms: Long): Int = Calendar.getInstance().run {
        timeInMillis = ms
        get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
    }
}
