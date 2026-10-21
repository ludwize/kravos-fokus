package net.kravos.fokus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kravos.fokus.data.Apps
import net.kravos.fokus.data.AppUsage
import net.kravos.fokus.data.DAY_SHORT
import net.kravos.fokus.data.Day
import net.kravos.fokus.data.DayStats
import net.kravos.fokus.data.Insights
import net.kravos.fokus.data.fmtMs
import net.kravos.fokus.data.hm
import net.kravos.fokus.ui.LocalApp
import net.kravos.fokus.ui.LocalNav
import net.kravos.fokus.ui.Route
import net.kravos.fokus.ui.components.AppRow
import net.kravos.fokus.ui.components.BarChart
import net.kravos.fokus.ui.components.Card
import net.kravos.fokus.ui.components.SectionTitle
import net.kravos.fokus.ui.components.Segmented
import net.kravos.fokus.ui.components.Stat
import net.kravos.fokus.ui.components.StatRow
import net.kravos.fokus.ui.components.TopBar
import net.kravos.fokus.ui.components.rememberPerms
import net.kravos.fokus.ui.rememberLoad
import net.kravos.fokus.ui.theme.K
import net.kravos.fokus.ui.theme.LocalPalette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val MONTHS = listOf("jan", "feb", "mar", "apr", "maj", "jun", "jul", "avg", "sep", "okt", "nov", "dec")

fun dateLabel(start: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = start }
    return "${DAY_SHORT[Day.dow(c)]}, ${c.get(Calendar.DAY_OF_MONTH)}. ${MONTHS[c.get(Calendar.MONTH)]}"
}

@Composable
fun StatsScreen(pad: PaddingValues) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var dayBack by rememberSaveable { mutableIntStateOf(0) }
    var weekBack by rememberSaveable { mutableIntStateOf(0) }
    val perms = rememberPerms()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 16.dp)) {
        item { TopBar("Statistika") }
        item { Segmented(listOf("Dan", "Nedelja"), mode, { mode = it }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
        if (!perms.usage) {
            item { Card { Text("Uključi „Pristup korišćenju” na ekranu Danas.", color = LocalPalette.current.dim) } }
        } else if (mode == 0) {
            dayContent(dayBack, { dayBack = it })
        } else {
            weekContent(weekBack, { weekBack = it }) { back -> dayBack = back; mode = 0 }
        }
    }
}

@Composable
private fun Pager(label: String, canBack: Boolean, canFwd: Boolean, onBack: () -> Unit, onFwd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = canBack) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Prethodni") }
        Text(label, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Medium)
        IconButton(onClick = onFwd, enabled = canFwd) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Sledeći") }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dayContent(back: Int, setBack: (Int) -> Unit) {
    item {
        val label = when (back) { 0 -> "Danas"; 1 -> "Juče"; else -> dateLabel(Day.startMinus(back)) }
        Pager(label, back < 29, back > 0, { setBack(back + 1) }, { setBack(back - 1) })
    }
    item { DayBody(back) }
}

@Composable
private fun DayBody(back: Int) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val pal = LocalPalette.current
    val load = rememberLoad(back, refreshMs = if (back == 0) 60_000 else 0) { app.usage.day(back) }
    val d = load.data ?: run {
        Card { Text("Računam…", color = pal.dim) }
        return
    }
    Column {
        Card {
            StatRow {
                Stat("vreme ekrana", fmtMs(d.totalMs), Modifier.weight(1.4f))
                Stat("otključavanja", if (d.detailed) d.unlocks.toString() else "–", Modifier.weight(1f))
                Stat("otvaranja", if (d.detailed) d.opens.toString() else "–", Modifier.weight(1f))
            }
            if (d.detailed && d.totalMs > 0) {
                Spacer(Modifier.height(16.dp))
                BarChart(d.hourly.map { it / 60_000f }, (0..23).map { it.toString() }, height = 110.dp, labelEvery = 3)
            }
            if (!d.detailed) {
                Spacer(Modifier.height(8.dp))
                Text("Za ovaj dan sistem čuva samo ukupna vremena po aplikaciji.", fontSize = 12.sp, color = pal.dim)
            }
        }
        Categories(d.apps)
        if (d.apps.isNotEmpty()) {
            SectionTitle("Aplikacije")
            Card {
                val max = d.apps.first().ms.toFloat().coerceAtLeast(1f)
                d.apps.take(40).forEach { a ->
                    AppRow(a.pkg, fmtMs(a.ms), a.ms / max, if (d.detailed) "${a.opens} otvaranja" else null) { nav.go(Route.AppDetail(a.pkg)) }
                }
            }
        }
    }
}

@Composable
private fun Categories(apps: List<AppUsage>) {
    val app = LocalApp.current
    val pal = LocalPalette.current
    val cats = apps.groupBy { app.apps.category(it.pkg) }
        .map { (c, l) -> c to l.sumOf { it.ms } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    if (cats.isEmpty()) return
    val total = cats.sumOf { it.second }.toFloat()
    SectionTitle("Po kategoriji")
    Card {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
            cats.take(6).forEachIndexed { i, (_, ms) ->
                Spacer(Modifier.weight((ms / total).coerceAtLeast(0.01f)).height(10.dp).background(K.Series[i % K.Series.size]))
            }
        }
        Spacer(Modifier.height(10.dp))
        cats.take(6).forEachIndexed { i, (c, ms) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(10.dp).clip(CircleShape).background(K.Series[i % K.Series.size]))
                Spacer(Modifier.width(10.dp))
                Text(Apps.categoryLabel(c), Modifier.weight(1f), fontSize = 14.sp)
                Text(fmtMs(ms), fontSize = 14.sp, color = pal.dim)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.weekContent(back: Int, setBack: (Int) -> Unit, openDay: (Int) -> Unit) {
    item {
        val label = when (back) { 0 -> "Ova nedelja"; 1 -> "Prošla nedelja"; else -> "Pre $back nedelje" }
        Pager(label, back < 3, back > 0, { setBack(back + 1) }, { setBack(back - 1) })
    }
    item { WeekBody(back, openDay) }
}

/** daysAgo of Monday..Sunday of the week [back] weeks before the current one (negative = future). */
private fun weekDaysAgo(back: Int): List<Int> {
    val dow = Day.dow(Calendar.getInstance())
    return (0..6).map { i -> dow - i + 7 * back }
}

@Composable
private fun WeekBody(back: Int, openDay: (Int) -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val pal = LocalPalette.current
    val goal by app.prefs.goalMin.collectAsState()
    val load = rememberLoad(back) {
        val cur = weekDaysAgo(back).map { if (it >= 0) app.usage.day(it) else null }
        val prev = weekDaysAgo(back + 1).map { app.usage.day(it) }
        cur to prev
    }
    val (cur, prev) = load.data ?: run {
        Card { Text("Računam…", color = pal.dim) }
        return
    }
    val days = cur.filterNotNull()
    val avg = Insights.avg(days) { it.totalMs }
    val prevAvg = Insights.avg(prev) { it.totalMs }
    val ch = Insights.change(avg, prevAvg)
    val todayIdx = if (back == 0) Day.dow(Calendar.getInstance()) else null
    Column {
        Card {
            StatRow {
                Stat("prosek dnevno", fmtMs(avg), Modifier.weight(1.3f), sub = ch?.let { if (it <= 0) "↓ ${-it}% od prethodne" else "↑ $it% od prethodne" })
                Stat("ukupno", fmtMs(days.sumOf { it.totalMs }), Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            BarChart(
                cur.map { (it?.totalMs ?: 0L) / 60_000f }, DAY_SHORT, highlight = todayIdx, goal = goal.toFloat(), height = 150.dp,
                onBarClick = { i -> weekDaysAgo(back)[i].takeIf { it >= 0 }?.let(openDay) },
            )
            Spacer(Modifier.height(6.dp))
            Text("Isprekidana linija je dnevni cilj. Dodirni dan za detalje.", fontSize = 11.sp, color = pal.dim)
        }

        SectionTitle("Uvidi")
        Card {
            val within = days.count { it.totalMs in 1..goal * 60_000L }
            val lines = mutableListOf<Pair<String, String>>()
            lines += "U okviru cilja" to "$within od ${days.count { it.totalMs > 0 }} dana"
            Insights.peakHour(days)?.let { lines += "Najaktivniji sat" to "${hm(it * 60)}–${hm((it + 1) * 60)}" }
            Insights.avgFirstPickup(days)?.let { lines += "Prvo uzimanje telefona" to "oko ${hm(it)}" }
            val detailed = days.filter { it.detailed && it.totalMs > 0 }
            if (detailed.isNotEmpty()) {
                lines += "Otključavanja dnevno" to "${detailed.sumOf { it.unlocks } / detailed.size}"
                lines += "Otvaranja aplikacija dnevno" to "${detailed.sumOf { it.opens } / detailed.size}"
            }
            days.filter { it.totalMs > 0 }.minByOrNull { it.totalMs }?.let { lines += "Najbolji dan" to "${dateLabel(it.start)} · ${fmtMs(it.totalMs)}" }
            days.maxByOrNull { it.totalMs }?.takeIf { it.totalMs > 0 }?.let { lines += "Najviše ekrana" to "${dateLabel(it.start)} · ${fmtMs(it.totalMs)}" }
            val rules = app.prefs.rules.value.filter { it.enabled && it.apps.isNotEmpty() }
            if (rules.isNotEmpty()) {
                val pk = rules.flatMap { it.apps }.toSet()
                lines += "Ometajuće aplikacije" to "${fmtMs(Insights.avg(days) { it.msFor(pk) })} dnevno"
            }
            lines.forEach { (k, v) ->
                Row(Modifier.padding(vertical = 5.dp)) {
                    Text(k, Modifier.weight(1f), fontSize = 14.sp, color = pal.dim)
                    Text(v, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        val merged = Insights.mergeApps(days)
        val prevMerged = Insights.mergeApps(prev).associateBy { it.pkg }
        val nDays = days.count { it.totalMs > 0 }.coerceAtLeast(1)
        val nPrev = prev.count { it.totalMs > 0 }.coerceAtLeast(1)
        Categories(merged)
        if (merged.isNotEmpty()) {
            SectionTitle("Aplikacije (prosek dnevno)")
            Card {
                val max = merged.first().ms.toFloat().coerceAtLeast(1f)
                merged.take(25).forEach { a ->
                    val p = prevMerged[a.pkg]?.ms?.div(nPrev)
                    val c = p?.let { Insights.change(a.ms / nDays, it) }
                    AppRow(
                        a.pkg, fmtMs(a.ms / nDays), a.ms / max,
                        listOfNotNull("${a.opens / nDays} otvaranja", c?.let { if (it <= 0) "↓ ${-it}%" else "↑ $it%" }).joinToString(" · "),
                        color = K.Accent,
                    ) { nav.go(Route.AppDetail(a.pkg)) }
                }
            }
        }
    }
}

@Composable
fun AppDetailScreen(pkg: String, pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val pal = LocalPalette.current
    val rules by app.prefs.rules.collectAsState()
    val load = rememberLoad(pkg) { (13 downTo 0).map { app.usage.day(it) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 16.dp)) {
        item { TopBar(app.apps.label(pkg), back = true) }
        val days = load.data
        if (days == null) {
            item { Card { Text("Računam…", color = pal.dim) } }
            return@LazyColumn
        }
        val week = days.takeLast(7)
        val per = week.map { d -> d.apps.firstOrNull { it.pkg == pkg } }
        val ms = per.map { it?.ms ?: 0L }
        val opens = per.sumOf { it?.opens ?: 0 }
        val today = ms.last()
        val avg = ms.sum() / 7
        val prevAvg = days.take(7).sumOf { d -> d.apps.firstOrNull { it.pkg == pkg }?.ms ?: 0L } / 7
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    net.kravos.fokus.ui.components.AppIcon(pkg, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(Apps.categoryLabel(app.apps.category(pkg)), fontSize = 13.sp, color = pal.dim)
                        Text("Danas ${fmtMs(today)}", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(16.dp))
                BarChart(ms.map { it / 60_000f }, week.map { DAY_SHORT[Day.dow(Calendar.getInstance().apply { timeInMillis = it.start })] }, highlight = 6, height = 130.dp)
                Spacer(Modifier.height(14.dp))
                StatRow {
                    Stat("prosek / dan", fmtMs(avg), Modifier.weight(1f), sub = Insights.change(avg, prevAvg)?.let { if (it <= 0) "↓ ${-it}%" else "↑ $it%" })
                    Stat("otvaranja / dan", "${opens / 7}", Modifier.weight(1f))
                    Stat("po otvaranju", if (opens > 0) fmtMs(ms.sum() / opens) else "–", Modifier.weight(1f))
                }
            }
        }
        val inRules = rules.filter { pkg in it.apps }
        item { SectionTitle("Grupe") }
        item {
            Card {
                if (inRules.isEmpty()) Text("Nije ni u jednoj grupi.", color = pal.dim, fontSize = 14.sp)
                inRules.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.name, fontSize = 15.sp)
                        Text(r.summary(), fontSize = 12.sp, color = pal.dim)
                    }
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.FilledTonalButton(onClick = { nav.go(Route.RuleEdit(null, presetApp = pkg)) }) {
                    Text("Nova grupa sa ovom aplikacijom")
                }
            }
        }
    }
}

@Suppress("unused")
private val fmtDay = SimpleDateFormat("d.M.", Locale.US)
