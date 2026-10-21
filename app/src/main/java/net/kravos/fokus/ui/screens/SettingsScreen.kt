package net.kravos.fokus.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.kravos.fokus.BuildConfig
import net.kravos.fokus.data.ThemeMode
import net.kravos.fokus.data.fmtMin
import net.kravos.fokus.data.hm
import net.kravos.fokus.ui.LocalApp
import net.kravos.fokus.ui.components.Card
import net.kravos.fokus.ui.components.PermissionSteps
import net.kravos.fokus.ui.components.Pill
import net.kravos.fokus.ui.components.SectionTitle
import net.kravos.fokus.ui.components.TopBar
import net.kravos.fokus.ui.components.rememberPerms
import net.kravos.fokus.ui.theme.LocalPalette
import net.kravos.fokus.update.Release
import net.kravos.fokus.update.Updater
import net.kravos.fokus.work.ReportWorker

@Composable
fun SettingsScreen(pad: PaddingValues, release: Release?, onCheckUpdate: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val scope = rememberCoroutineScope()
    val perms = rememberPerms()
    val theme by app.prefs.theme.collectAsState()
    val goal by app.prefs.goalMin.collectAsState()
    var report by remember { mutableStateOf(app.prefs.reportEnabled) }
    var reportMin by remember { mutableIntStateOf(app.prefs.reportMinute) }
    var warn by remember { mutableStateOf(app.prefs.limitWarnings) }
    var protect by remember { mutableStateOf(app.prefs.protectUninstall) }
    var auto by remember { mutableStateOf(app.prefs.autoUpdateCheck) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var updMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
        item { TopBar("Podešavanja", back = true) }

        item { SectionTitle("Dozvole") }
        item { Card { PermissionSteps(perms, showDone = true) } }

        item { SectionTitle("Dnevni cilj") }
        item {
            Card {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { app.prefs.setGoal((goal - 15).coerceAtLeast(15)) }) { Icon(Icons.Filled.Remove, "Manje") }
                    Text(fmtMin(goal), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    IconButton(onClick = { app.prefs.setGoal((goal + 15).coerceAtMost(12 * 60)) }) { Icon(Icons.Filled.Add, "Više") }
                }
                Text("Ukupno vreme ekrana dnevno. Dani u okviru cilja grade niz.", fontSize = 12.sp, color = pal.dim)
            }
        }

        item { SectionTitle("Notifikacije") }
        item {
            Card {
                Toggle("Dnevni izveštaj", "Rezime dana u ${hm(reportMin)}", report) {
                    report = it; app.prefs.reportEnabled = it; ReportWorker.schedule(ctx)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        TimePickerDialog(ctx, { _, h, m ->
                            reportMin = h * 60 + m; app.prefs.reportMinute = reportMin; ReportWorker.schedule(ctx)
                        }, reportMin / 60, reportMin % 60, true).show()
                    }) { Text("Promeni vreme") }
                    TextButton(onClick = { ReportWorker.runNow(ctx) }) { Text("Pošalji probni") }
                }
                Spacer(Modifier.height(8.dp))
                Toggle("Upozorenje pred limit", "Notifikacija kad ostane oko 5 minuta", warn) { warn = it; app.prefs.limitWarnings = it }
            }
        }

        item { SectionTitle("Zaštita") }
        item {
            Card {
                Toggle(
                    "Zaključaj podešavanja tokom strogih pravila",
                    "Dok je strogo pravilo ili stroga sesija aktivna, ekrani „Info o aplikaciji” i pristupačnost za Kravos Fokus se odmah zatvaraju, pa ne možeš da ga isključiš ili deinstaliraš. Na Androidu ništa nije neprobojno (npr. safe mode), ali ovo je dovoljno trenja.",
                    protect,
                ) { protect = it; app.prefs.protectUninstall = it }
            }
        }

        item { SectionTitle("Izgled") }
        item {
            Card {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.DARK to "Tamna", ThemeMode.LIGHT to "Svetla", ThemeMode.SYSTEM to "Sistem").forEach { (m, l) ->
                        Pill(l, theme == m, { app.prefs.setTheme(m) })
                    }
                }
            }
        }

        item { SectionTitle("Ažuriranje") }
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Verzija ${BuildConfig.VERSION_NAME}", fontSize = 15.sp)
                        Text(
                            when {
                                release == null -> "Nije provereno"
                                Updater.isNewer(release) -> "Dostupna je verzija 1.0.${release.build}"
                                else -> "Imaš najnoviju verziju"
                            }, fontSize = 12.sp, color = pal.dim,
                        )
                    }
                    if (release != null && Updater.isNewer(release)) {
                        Button(enabled = progress == null, onClick = {
                            scope.launch {
                                try {
                                    progress = 0f
                                    val f = Updater.download(ctx, release) { progress = it }
                                    progress = null
                                    if (!Updater.install(ctx, f)) updMsg = "Dozvoli instalaciju iz ove aplikacije, pa ponovo pritisni Instaliraj."
                                } catch (e: Exception) {
                                    progress = null; updMsg = "Greška: ${e.message}"
                                }
                            }
                        }) { Text("Instaliraj") }
                    } else TextButton(onClick = onCheckUpdate) { Text("Proveri") }
                }
                progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) }
                updMsg?.let { Text(it, fontSize = 12.sp) }
                Toggle("Automatski proveri pri pokretanju", null, auto) { auto = it; app.prefs.autoUpdateCheck = it }
            }
        }
        item {
            Text(
                "Kravos Fokus · sve ostaje na telefonu, bez naloga i bez reklama.",
                Modifier.fillMaxWidth().padding(20.dp), fontSize = 12.sp, color = pal.dim, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Toggle(title: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = LocalPalette.current.dim)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}
