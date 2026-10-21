package net.kravos.fokus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.kravos.fokus.data.Day
import net.kravos.fokus.data.Session
import net.kravos.fokus.data.SessionLog
import net.kravos.fokus.data.fmtMin
import net.kravos.fokus.data.hm
import net.kravos.fokus.ui.LocalApp
import net.kravos.fokus.ui.LocalNav
import net.kravos.fokus.ui.Tab
import net.kravos.fokus.ui.components.Badge
import net.kravos.fokus.ui.components.Card
import net.kravos.fokus.ui.components.Pill
import net.kravos.fokus.ui.components.Ring
import net.kravos.fokus.ui.components.SectionTitle
import net.kravos.fokus.ui.components.Stat
import net.kravos.fokus.ui.components.StatRow
import net.kravos.fokus.ui.components.TopBar
import net.kravos.fokus.ui.components.rememberPerms
import net.kravos.fokus.ui.theme.K
import net.kravos.fokus.ui.theme.LocalPalette
import net.kravos.fokus.work.Notif
import java.util.Calendar

private val DURATIONS = listOf(15, 25, 45, 60, 90, 120)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val perms = rememberPerms()
    val session by app.prefs.session.collectAsState()
    val rules by app.prefs.rules.collectAsState()
    var minutes by rememberSaveable { mutableIntStateOf(25) }
    var strict by rememberSaveable { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session) { while (session != null) { now = System.currentTimeMillis(); delay(1000) } }
    var logVersion by remember { mutableIntStateOf(0) }
    val log = remember(logVersion, session) { app.prefs.sessionLog() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 16.dp)) {
        item { TopBar("Fokus") }
        val s = session?.takeIf { it.active(now) }
        if (s != null) {
            item {
                Card {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        val total = (s.endAt - s.startAt).toFloat()
                        val left = (s.endAt - now).coerceAtLeast(0)
                        Ring(1f - left / total, if (s.strict) K.Strict else K.Accent, 210.dp, 14.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val m = left / 60_000; val sec = (left / 1000) % 60
                                Text("%d:%02d".format(m, sec), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                                Text("do ${hm(Day.minuteOfDay(Calendar.getInstance().apply { timeInMillis = s.endAt }))}", color = pal.dim)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Badge(if (s.strict) "STROGA SESIJA" else "MEKANA SESIJA", if (s.strict) K.Strict else K.Soft)
                        Spacer(Modifier.height(8.dp))
                        val names = if (s.ruleIds.isEmpty()) "Sve grupe" else rules.filter { it.id in s.ruleIds }.joinToString(", ") { it.name }
                        Text("Blokirano: $names", fontSize = 13.sp, color = pal.dim, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        if (s.strict) {
                            Text("Stroga sesija se ne može prekinuti.", fontSize = 13.sp, color = pal.dim)
                        } else {
                            FilledTonalButton(onClick = {
                                val mins = ((System.currentTimeMillis() - s.startAt) / 60_000).toInt()
                                app.prefs.logSession(SessionLog(s.startAt, ((s.endAt - s.startAt) / 60_000).toInt(), mins, false))
                                app.prefs.setSession(null)
                                Notif.cancelSession(ctx)
                                logVersion++
                            }) { Text("Završi ranije") }
                        }
                    }
                }
            }
        } else {
            if (!perms.guard) {
                item {
                    Card(onClick = { nav.tab(Tab.TODAY) }) {
                        Text("Za fokus sesije uključi čuvara (pristupačnost) na ekranu Danas.", fontSize = 14.sp, color = K.Warn)
                    }
                }
            }
            item { SectionTitle("Trajanje") }
            item {
                Card {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DURATIONS.forEach { d -> Pill(fmtMin(d), d == minutes, { minutes = d }) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { minutes = (minutes - 5).coerceAtLeast(5) }) { Icon(Icons.Filled.Remove, "Manje") }
                        Text(fmtMin(minutes), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium, fontSize = 18.sp)
                        IconButton(onClick = { minutes = (minutes + 5).coerceAtMost(8 * 60) }) { Icon(Icons.Filled.Add, "Više") }
                    }
                }
            }
            item { SectionTitle("Šta se blokira") }
            item {
                Card {
                    val usable = rules.filter { it.enabled && it.apps.isNotEmpty() }
                    if (usable.isEmpty()) {
                        Text("Prvo napravi bar jednu grupu aplikacija u Pravilima.", fontSize = 14.sp, color = pal.dim)
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { nav.tab(Tab.RULES) }) { Text("Pravila") }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Sve grupe", picked.isEmpty(), { picked = emptySet() })
                            usable.forEach { r -> Pill(r.name, r.id in picked, { picked = if (r.id in picked) picked - r.id else picked + r.id }) }
                        }
                    }
                }
            }
            item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Stroga sesija", fontSize = 15.sp)
                            Text("Ne može se prekinuti ni produžiti pristup.", fontSize = 12.sp, color = pal.dim)
                        }
                        Switch(checked = strict, onCheckedChange = { strict = it })
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Button(
                        onClick = {
                            val t = System.currentTimeMillis()
                            val ns = Session(t, t + minutes * 60_000L, picked.toList(), strict)
                            app.prefs.setSession(ns)
                            Notif.session(ctx, ns)
                        },
                        enabled = rules.any { it.enabled && it.apps.isNotEmpty() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) { Text("Započni ${fmtMin(minutes)}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                }
            }
        }

        // history
        val todayStart = Day.start()
        val weekStart = Day.startMinus(6)
        val todayMin = log.filter { it.startAt >= todayStart }.sumOf { it.actualMin }
        val weekMin = log.filter { it.startAt >= weekStart }.sumOf { it.actualMin }
        val done = log.filter { it.startAt >= weekStart }.let { l -> if (l.isEmpty()) null else l.count { it.completed } to l.size }
        item { SectionTitle("Istorija") }
        item {
            Card {
                StatRow {
                    Stat("danas", fmtMin(todayMin), Modifier.weight(1f))
                    Stat("7 dana", fmtMin(weekMin), Modifier.weight(1f))
                    Stat("završeno", done?.let { "${it.first}/${it.second}" } ?: "–", Modifier.weight(1f))
                }
                if (log.isNotEmpty()) Spacer(Modifier.height(10.dp))
                log.take(6).forEach { l ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(dateLabel(l.startAt) + " " + hm(Day.minuteOfDay(Calendar.getInstance().apply { timeInMillis = l.startAt })), Modifier.weight(1f), fontSize = 13.sp, color = pal.dim)
                        Text(if (l.completed) "${fmtMin(l.actualMin)} ✓" else "${fmtMin(l.actualMin)} od ${fmtMin(l.plannedMin)}", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
