package net.kravos.fokus.ui.screens

import android.app.TimePickerDialog
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kravos.fokus.data.ALL_DAYS
import net.kravos.fokus.data.AppInfo
import net.kravos.fokus.data.Apps
import net.kravos.fokus.data.DAY_SHORT
import net.kravos.fokus.data.Prefs
import net.kravos.fokus.data.Rule
import net.kravos.fokus.data.ShortsTarget
import net.kravos.fokus.data.TimeWindow
import net.kravos.fokus.data.WORK_DAYS
import net.kravos.fokus.data.fmtMin
import net.kravos.fokus.data.fmtMs
import net.kravos.fokus.data.hm
import net.kravos.fokus.ui.LocalApp
import net.kravos.fokus.ui.LocalNav
import net.kravos.fokus.ui.Route
import net.kravos.fokus.ui.components.AppIcon
import net.kravos.fokus.ui.components.Badge
import net.kravos.fokus.ui.components.Card
import net.kravos.fokus.ui.components.Pill
import net.kravos.fokus.ui.components.SectionTitle
import net.kravos.fokus.ui.components.Segmented
import net.kravos.fokus.ui.components.TopBar
import net.kravos.fokus.ui.rememberLoad
import net.kravos.fokus.ui.theme.K
import net.kravos.fokus.ui.theme.LocalPalette

@Composable
fun RulesScreen(pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val rules by app.prefs.rules.collectAsState()
    val shorts by app.prefs.shorts.collectAsState()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 16.dp)) {
        item {
            TopBar("Pravila") {
                IconButton(onClick = { nav.go(Route.RuleEdit(null)) }) { Icon(Icons.Filled.Add, "Nova grupa") }
            }
        }
        item { SectionTitle("Grupe aplikacija") }
        if (rules.isEmpty()) {
            item {
                Card {
                    Text("Još nema grupa. Kreni od šablona:", fontSize = 14.sp, color = pal.dim)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { nav.go(Route.RuleEdit(null, template = "social")) }) { Text("Društvene mreže") }
                        FilledTonalButton(onClick = { nav.go(Route.RuleEdit(null, template = "night")) }) { Text("Noć") }
                    }
                }
            }
        }
        items(rules, key = { it.id }) { r ->
            Card(onClick = { nav.go(Route.RuleEdit(r.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            Badge(if (r.strict) "STROGO" else "MEKANO", if (r.strict) K.Strict else K.Soft)
                        }
                        Text(r.summary(), fontSize = 13.sp, color = pal.dim)
                    }
                    Switch(checked = r.enabled, onCheckedChange = { on ->
                        if (app.prefs.upsert(r.copy(enabled = on)) == Prefs.SaveResult.Scheduled) {
                            Toast.makeText(ctx, "Strogo pravilo: promena važi od sutra", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
                if (r.apps.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        r.apps.take(8).forEach { AppIcon(it, 26.dp) }
                        if (r.apps.size > 8) Text("+${r.apps.size - 8}", fontSize = 12.sp, color = pal.dim)
                    }
                }
                if (r.pending != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (r.pending.name == Prefs.DELETE_MARK) "Biće obrisana sutra" else "Izmena važi od sutra",
                            Modifier.weight(1f), fontSize = 12.sp, color = K.Warn,
                        )
                        TextButton(onClick = { app.prefs.cancelPending(r.id) }) { Text("Poništi") }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                FilledTonalButton(onClick = { nav.go(Route.RuleEdit(null)) }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Nova grupa")
                }
            }
        }

        item { SectionTitle("Kratki video") }
        item {
            Card {
                Text("Blokira samo feed kratkih videa. Ostatak aplikacije radi normalno.", fontSize = 13.sp, color = pal.dim)
                Spacer(Modifier.height(4.dp))
                ShortsTarget.entries.forEach { t ->
                    val installed = remember(t) { app.apps.installed(t.pkg) || (t == ShortsTarget.TIKTOK && ShortsTarget.TIKTOK_PKGS.any { app.apps.installed(it) }) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (installed) AppIcon(if (t == ShortsTarget.TIKTOK) ShortsTarget.TIKTOK_PKGS.first { app.apps.installed(it) } else t.pkg, 30.dp)
                        else Spacer(Modifier.width(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${t.app} ${t.feature}", fontSize = 15.sp)
                            if (!installed) Text("nije instaliran", fontSize = 12.sp, color = pal.dim)
                            else if (t == ShortsTarget.FACEBOOK || t == ShortsTarget.SNAPCHAT) Text("radi na osnovu izgleda aplikacije, može da promaši", fontSize = 12.sp, color = pal.dim)
                        }
                        Switch(checked = t in shorts, onCheckedChange = { app.prefs.setShorts(t, it) })
                    }
                }
            }
        }
    }
}

private val LIMITS = listOf(0, 15, 30, 45, 60, 90, 120, 180)
private val OPENS = listOf(0, 5, 10, 15, 20, 30, 50)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleEditScreen(route: Route.RuleEdit, pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val existing = remember(route) { app.prefs.rules.value.firstOrNull { it.id == route.id } }
    val locked = existing != null && app.prefs.isLocked(existing)
    // when a strict rule already has a pending edit, keep editing that
    val start = remember(route) {
        existing?.pending?.takeIf { it.name != Prefs.DELETE_MARK }?.copy(id = existing.id, createdAt = existing.createdAt)
            ?: existing
            ?: when (route.template) {
                "night" -> Rule(name = "Noć", windows = listOf(TimeWindow(ALL_DAYS, 23 * 60, 7 * 60)))
                "social" -> Rule(name = "Društvene mreže", limitMin = 60)
                else -> Rule(name = "", apps = listOfNotNull(route.presetApp))
            }
    }
    var rule by remember(route) { mutableStateOf(start) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var addWindow by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val apps = rememberLoad("apps") {
        val list = app.apps.launchable()
        val usage = if (app.usage.hasAccess()) net.kravos.fokus.data.Insights.mergeApps((6 downTo 0).map { app.usage.day(it) }).associate { it.pkg to it.ms } else emptyMap()
        list to usage
    }
    // the social template fills in installed social apps once the list is there
    var templateFilled by remember(route) { mutableStateOf(false) }
    apps.data?.let { (list, _) ->
        if (route.template == "social" && !templateFilled && existing == null) {
            templateFilled = true
            rule = rule.copy(apps = list.filter { it.category == ApplicationInfo.CATEGORY_SOCIAL }.map { it.pkg })
        }
    }

    fun save() {
        val r = rule.copy(name = rule.name.trim().ifEmpty { "Grupa" })
        when (app.prefs.upsert(r)) {
            Prefs.SaveResult.Scheduled -> Toast.makeText(ctx, "Strogo pravilo: izmene važe od sutra", Toast.LENGTH_LONG).show()
            Prefs.SaveResult.Saved -> Unit
        }
        nav.back()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
        item {
            TopBar(if (existing == null) "Nova grupa" else rule.name.ifEmpty { "Grupa" }, back = true) {
                TextButton(onClick = { save() }, enabled = rule.apps.isNotEmpty()) { Text("Sačuvaj", fontWeight = FontWeight.SemiBold) }
            }
        }
        if (locked) {
            item {
                Card {
                    Text("Ovo je strogo pravilo", fontWeight = FontWeight.SemiBold, color = K.Strict)
                    Text("Sve izmene (i brisanje) stupaju na snagu tek sutra. Tako odluke donosiš smirene glave, a ne u trenutku kad ti se baš skroluje.", fontSize = 13.sp, color = pal.dim)
                }
            }
        }
        item {
            Card {
                OutlinedTextField(
                    rule.name, { rule = rule.copy(name = it) }, Modifier.fillMaxWidth(),
                    label = { Text("Naziv grupe") }, singleLine = true,
                )
            }
        }

        // ---- apps ----
        item {
            SectionTitle("Aplikacije (${rule.apps.size})") {
                Text(
                    if (picking) "Gotovo" else "Izmeni", Modifier.clickable { picking = !picking }.padding(4.dp),
                    color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (!picking) {
            item {
                Card(onClick = { picking = true }) {
                    if (rule.apps.isEmpty()) Text("Dodaj aplikacije koje ova grupa ograničava", color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rule.apps.forEach { p ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(62.dp)) {
                                AppIcon(p, 40.dp)
                                Text(app.apps.label(p), fontSize = 11.sp, maxLines = 1, color = pal.dim)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Card {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Pretraži aplikacije") }, singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Apps.DISTRACTING.forEach { c ->
                            Pill("+ ${Apps.categoryLabel(c)}", false, {
                                val add = apps.data?.first?.filter { it.category == c }?.map { it.pkg }.orEmpty()
                                rule = rule.copy(apps = (rule.apps + add).distinct())
                            })
                        }
                    }
                }
            }
            val data = apps.data
            if (data == null) item { Card { Text("Učitavam aplikacije…", color = pal.dim) } }
            else {
                val (list, usage) = data
                val q = query.trim().lowercase()
                val shown = list.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q) }
                    .sortedWith(compareByDescending<AppInfo> { it.pkg in start.apps }.thenByDescending { usage[it.pkg] ?: 0L })
                items(shown, key = { it.pkg }) { a ->
                    val on = a.pkg in rule.apps
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            rule = rule.copy(apps = if (on) rule.apps - a.pkg else rule.apps + a.pkg)
                        }.padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(a.pkg, 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label, fontSize = 15.sp)
                            val u = usage[a.pkg]
                            Text(
                                listOfNotNull(Apps.categoryLabel(a.category), u?.let { "${fmtMs(it / 7)}/dan" }).joinToString(" · "),
                                fontSize = 12.sp, color = pal.dim,
                            )
                        }
                        Checkbox(checked = on, onCheckedChange = { rule = rule.copy(apps = if (on) rule.apps - a.pkg else rule.apps + a.pkg) })
                    }
                }
            }
            return@LazyColumn
        }

        // ---- limits ----
        item { SectionTitle("Dnevni limit vremena") }
        item {
            Card {
                Choice(LIMITS, rule.limitMin, { if (it == 0) "Bez" else fmtMin(it) }) { rule = rule.copy(limitMin = it) }
                Stepper(if (rule.limitMin == 0) "Bez limita" else fmtMin(rule.limitMin), 5) { d -> rule = rule.copy(limitMin = (rule.limitMin + d).coerceIn(0, 12 * 60)) }
                Text("Zbir za sve aplikacije u grupi.", fontSize = 12.sp, color = pal.dim)
            }
        }
        item { SectionTitle("Broj otvaranja dnevno") }
        item {
            Card {
                Choice(OPENS, rule.opensLimit, { if (it == 0) "Bez" else "$it" }) { rule = rule.copy(opensLimit = it) }
                Stepper(if (rule.opensLimit == 0) "Bez limita" else "${rule.opensLimit} otvaranja", 1) { d -> rule = rule.copy(opensLimit = (rule.opensLimit + d).coerceIn(0, 500)) }
            }
        }

        // ---- schedules ----
        item { SectionTitle("Rasporedi blokade") }
        item {
            Card {
                if (rule.windows.isEmpty()) Text("Nijedan. U zakazanom periodu aplikacije su potpuno blokirane.", fontSize = 13.sp, color = pal.dim)
                rule.windows.forEachIndexed { i, w ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(w.label(), Modifier.weight(1f), fontSize = 15.sp)
                        IconButton(onClick = { rule = rule.copy(windows = rule.windows.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Filled.Close, "Ukloni") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                FilledTonalButton(onClick = { addWindow = true }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Dodaj raspored") }
            }
        }

        // ---- strictness ----
        item { SectionTitle("Strogoća") }
        item {
            Card {
                Segmented(listOf("Mekano", "Strogo"), if (rule.strict) 1 else 0, { rule = rule.copy(strict = it == 1) })
                Spacer(Modifier.height(10.dp))
                Text(
                    if (rule.strict) "Kad se blokira, nema izuzetaka. Izmene i brisanje važe tek od sutra (prvih 10 minuta posle kreiranja još možeš da ispraviš grešku)."
                    else "Kad se blokira, posle 10 sekundi čekanja možeš da uzmeš još 5 minuta. Svako produženje se beleži u statistici.",
                    fontSize = 13.sp, color = pal.dim,
                )
            }
        }
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Grupa je uključena", Modifier.weight(1f), fontSize = 15.sp)
                    Switch(checked = rule.enabled, onCheckedChange = { rule = rule.copy(enabled = it) })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { save() }, enabled = rule.apps.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Sačuvaj") }
                if (existing != null) {
                    FilledTonalButton(onClick = { confirmDelete = true }) { Text("Obriši", color = K.Bad) }
                }
            }
        }
    }

    if (addWindow) WindowDialog(onDismiss = { addWindow = false }) { w ->
        rule = rule.copy(windows = rule.windows + w)
        addWindow = false
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Obrisati grupu „${existing.name}”?") },
            text = { Text(if (locked) "Strogo pravilo: grupa će biti obrisana sutra." else "Limiti i rasporedi ove grupe prestaju da važe.") },
            confirmButton = {
                TextButton(onClick = {
                    if (app.prefs.delete(existing.id) == Prefs.SaveResult.Scheduled) Toast.makeText(ctx, "Grupa će biti obrisana sutra", Toast.LENGTH_SHORT).show()
                    confirmDelete = false
                    nav.back()
                }) { Text("Obriši", color = K.Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Otkaži") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choice(values: List<Int>, selected: Int, label: (Int) -> String, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { v -> Pill(label(v), v == selected, { onPick(v) }) }
    }
}

@Composable
private fun Stepper(text: String, step: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(-step) }) { Icon(Icons.Filled.Remove, "Manje") }
        Text(text, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Medium)
        IconButton(onClick = { onChange(step) }) { Icon(Icons.Filled.Add, "Više") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WindowDialog(onDismiss: () -> Unit, onAdd: (TimeWindow) -> Unit) {
    val ctx = LocalContext.current
    var days by remember { mutableStateOf(ALL_DAYS) }
    var from by remember { mutableStateOf(23 * 60) }
    var to by remember { mutableStateOf(7 * 60) }
    fun pick(init: Int, set: (Int) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> set(h * 60 + m) }, init / 60, init % 60, true).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novi raspored") },
        text = {
            Column {
                Text("Brzi izbor", fontSize = 12.sp, color = LocalPalette.current.dim)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Noć 23–07", false, { days = ALL_DAYS; from = 23 * 60; to = 7 * 60 })
                    Pill("Posao 09–17", false, { days = WORK_DAYS; from = 9 * 60; to = 17 * 60 })
                    Pill("Jutro 06–09", false, { days = ALL_DAYS; from = 6 * 60; to = 9 * 60 })
                    Pill("Ceo dan", false, { from = 0; to = 0 })
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { pick(from) { from = it } }) { Text("Od ${hm(from)}") }
                    FilledTonalButton(onClick = { pick(to) { to = it } }) { Text("Do ${hm(to)}") }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_SHORT.forEachIndexed { i, d ->
                        val on = days and (1 shl i) != 0
                        Pill(d, on, { days = days xor (1 shl i) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(TimeWindow(days, from, to).label(), fontSize = 13.sp, color = LocalPalette.current.dim)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(TimeWindow(days, from, to)) }, enabled = days != 0) { Text("Dodaj") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Otkaži") } },
    )
}
