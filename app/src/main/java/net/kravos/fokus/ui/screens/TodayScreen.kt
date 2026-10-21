package net.kravos.fokus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kravos.fokus.block.Engine
import net.kravos.fokus.data.Day
import net.kravos.fokus.data.DayStats
import net.kravos.fokus.data.Insights
import net.kravos.fokus.data.Rule
import net.kravos.fokus.data.fmtMin
import net.kravos.fokus.data.fmtMs
import net.kravos.fokus.data.hm
import net.kravos.fokus.ui.LocalApp
import net.kravos.fokus.ui.LocalNav
import net.kravos.fokus.ui.Route
import net.kravos.fokus.ui.Tab
import net.kravos.fokus.ui.components.AppRow
import net.kravos.fokus.ui.components.Badge
import net.kravos.fokus.ui.components.Bar
import net.kravos.fokus.ui.components.BarChart
import net.kravos.fokus.ui.components.Card
import net.kravos.fokus.ui.components.PermissionSteps
import net.kravos.fokus.ui.components.Ring
import net.kravos.fokus.ui.components.SectionTitle
import net.kravos.fokus.ui.components.Stat
import net.kravos.fokus.ui.components.StatRow
import net.kravos.fokus.ui.components.TopBar
import net.kravos.fokus.ui.components.rememberPerms
import net.kravos.fokus.ui.rememberLoad
import net.kravos.fokus.ui.theme.K
import net.kravos.fokus.ui.theme.LocalPalette
import java.util.Calendar

private data class TodayData(val today: DayStats, val past: List<DayStats>)

@Composable
fun TodayScreen(pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val pal = LocalPalette.current
    val perms = rememberPerms()
    val goal by app.prefs.goalMin.collectAsState()
    val rules by app.prefs.rules.collectAsState()
    val session by app.prefs.session.collectAsState()
    val load = rememberLoad(perms.usage, refreshMs = 30_000) {
        if (!perms.usage) null else TodayData(app.usage.today(), (14 downTo 1).map { app.usage.day(it) })
    }
    val data = load.data

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 16.dp)) {
        item {
            TopBar("Danas") {
                IconButton(onClick = { nav.go(Route.Settings) }) { Icon(Icons.Filled.Settings, "Podešavanja") }
            }
        }
        if (!perms.essentials) {
            item {
                Card {
                    Text("Podesi Kravos Fokus", style = MaterialTheme.typography.titleMedium)
                    Text("Dve dozvole i spreman si. Ništa ne napušta telefon.", fontSize = 13.sp, color = pal.dim)
                    Spacer(Modifier.height(6.dp))
                    PermissionSteps(perms, showDone = true)
                }
            }
        }
        if (data != null) {
            val d = data.today
            val goalMs = goal * 60_000L
            item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val frac = d.totalMs.toFloat() / goalMs
                        Ring(frac, if (frac <= 1f) K.Accent else K.Warn, 132.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(fmtMs(d.totalMs).replace(" min", "m").replace(" h ", "h "), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("od ${fmtMin(goal)}", fontSize = 12.sp, color = pal.dim)
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Stat("otključavanja", d.unlocks.toString())
                            Stat("otvaranja", d.opens.toString())
                            d.firstPickup?.let { Stat("prvo uzimanje", hm(Day.minuteOfDay(Calendar.getInstance().apply { timeInMillis = it }))) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val streak = Insights.streak(data.past, goalMs)
                    val left = goalMs - d.totalMs
                    Text(
                        when {
                            left > 0 -> "Još ${fmtMs(left)} do dnevnog cilja."
                            else -> "Cilj je danas premašen za ${fmtMs(-left)}."
                        } + if (streak > 0) "  Niz: $streak ${if (streak == 1) "dan" else "dana"} u okviru cilja." else "",
                        fontSize = 13.sp, color = pal.dim,
                    )
                }
            }

            val s = session
            if (s != null && s.active()) {
                item {
                    Card(onClick = { nav.tab(Tab.FOCUS) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Fokus sesija do ${hm(Day.minuteOfDay(Calendar.getInstance().apply { timeInMillis = s.endAt }))}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Badge(if (s.strict) "STROGO" else "MEKANO", if (s.strict) K.Strict else K.Soft)
                        }
                        Text("Ostalo ${fmtMs(s.endAt - System.currentTimeMillis())}", fontSize = 13.sp, color = pal.dim)
                    }
                }
            }

            val active = rules.filter { it.enabled && it.apps.isNotEmpty() }
            if (active.isNotEmpty()) {
                item { SectionTitle("Grupe danas") }
                item {
                    Card {
                        active.forEachIndexed { i, r ->
                            if (i > 0) Spacer(Modifier.height(14.dp))
                            GroupProgress(r, d)
                        }
                    }
                }
            }

            item { SectionTitle("Po satima") }
            item {
                Card {
                    val now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    BarChart(
                        d.hourly.map { it / 60_000f }, (0..23).map { it.toString() },
                        highlight = now, height = 110.dp, labelEvery = 3,
                    )
                }
            }

            if (d.apps.isNotEmpty()) {
                item { SectionTitle("Najviše korišćeno", trailing = { Text("Sve", Modifier.clickable { nav.tab(Tab.STATS) }.padding(4.dp), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }) }
                item {
                    Card {
                        val max = d.apps.first().ms.toFloat()
                        d.apps.take(6).forEach { a ->
                            AppRow(
                                a.pkg, fmtMs(a.ms), a.ms / max, "${a.opens} otvaranja",
                                onClick = { nav.go(Route.AppDetail(a.pkg)) },
                            )
                        }
                    }
                }
            }

            val weekNow = (data.past.takeLast(6) + d)
            val weekPrev = data.past.take(7)
            val ch = Insights.change(Insights.avg(weekNow) { it.totalMs }, Insights.avg(weekPrev) { it.totalMs })
            if (ch != null) {
                item {
                    Card(onClick = { nav.tab(Tab.STATS) }) {
                        Text(
                            if (ch <= 0) "${-ch}% manje nego prethodnih 7 dana" else "$ch% više nego prethodnih 7 dana",
                            style = MaterialTheme.typography.titleMedium, color = if (ch <= 0) K.Good else K.Warn,
                        )
                        Text("Prosek: ${fmtMs(Insights.avg(weekNow) { it.totalMs })} dnevno", fontSize = 13.sp, color = pal.dim)
                    }
                }
            }
        } else if (perms.usage) {
            item { Card { Text("Računam…", color = pal.dim) } }
        }
        if (rules.isEmpty()) {
            item {
                Card {
                    Text("Napravi prvu grupu", style = MaterialTheme.typography.titleMedium)
                    Text("Npr. društvene mreže sa limitom od sat vremena dnevno, ili noćni raspored.", fontSize = 13.sp, color = pal.dim)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { nav.tab(Tab.RULES) }) { Text("Pravila") }
                }
            }
        }
    }
}

@Composable
fun GroupProgress(r: Rule, d: DayStats) {
    val app = LocalApp.current
    val pal = LocalPalette.current
    val used = d.msFor(r.apps)
    val opens = app.prefs.opens(r.id).takeIf { it > 0 } ?: d.opensFor(r.apps)
    val blocked = Engine.check(r.apps.first(), listOf(r), null, app.prefs, { used })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(r.name, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (blocked != null) Badge("BLOKIRANO", K.Bad)
        else if (r.strict) Badge("STROGO", K.Strict)
    }
    Spacer(Modifier.height(4.dp))
    val parts = mutableListOf<String>()
    parts += if (r.limitMin > 0) "${fmtMs(used)} / ${fmtMin(r.limitMin)}" else fmtMs(used)
    parts += if (r.opensLimit > 0) "$opens / ${r.opensLimit} otvaranja" else "$opens otvaranja"
    Text(parts.joinToString(" · "), fontSize = 13.sp, color = pal.dim)
    if (r.limitMin > 0) {
        Spacer(Modifier.height(6.dp))
        val f = used / (r.limitMin * 60_000f)
        Bar(f, when { f >= 1f -> K.Bad; f > 0.8f -> K.Warn; else -> K.Accent })
    }
    Spacer(Modifier.fillMaxWidth())
}
