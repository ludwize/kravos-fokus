package net.kravos.fokus.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.kravos.fokus.App
import net.kravos.fokus.data.ALL_DAYS
import net.kravos.fokus.data.Rule
import net.kravos.fokus.data.ShortsTarget
import net.kravos.fokus.data.TimeWindow
import net.kravos.fokus.ui.screens.AppDetailScreen
import net.kravos.fokus.ui.screens.FocusScreen
import net.kravos.fokus.ui.screens.RuleEditScreen
import net.kravos.fokus.ui.screens.RulesScreen
import net.kravos.fokus.ui.screens.SettingsScreen
import net.kravos.fokus.ui.screens.StatsScreen
import net.kravos.fokus.ui.screens.TodayScreen
import net.kravos.fokus.ui.theme.FokusTheme
import net.kravos.fokus.update.Release
import net.kravos.fokus.update.Updater

class MainActivity : ComponentActivity() {
    private val nav = Navigator()
    private var resume by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        handle(intent)
        setContent {
            val theme by app.prefs.theme.collectAsState()
            FokusTheme(theme) {
                CompositionLocalProvider(LocalNav provides nav, LocalApp provides app, LocalResume provides resume) { Root(app) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resume++
        (application as App).usage.invalidateToday()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        i ?: return
        i.getStringExtra(EXTRA_TAB)?.let { t -> runCatching { nav.tab(Tab.valueOf(t)) } }
        if (i.getBooleanExtra("smoke_seed", false)) seed()
    }

    /** Emulator smoke test only: a couple of groups so the screens and the block page have something to show. */
    private fun seed() {
        val app = application as App
        if (app.prefs.rules.value.isNotEmpty()) return
        val clock = listOf("com.google.android.deskclock", "com.android.deskclock").filter { app.apps.installed(it) }
        val contacts = listOf("com.google.android.contacts", "com.android.contacts", "com.google.android.dialer").filter { app.apps.installed(it) }
        val past = System.currentTimeMillis() - 60 * 60_000L // created an hour ago, so it's past the edit grace period
        app.prefs.upsert(Rule(name = "Test blokada", apps = clock, windows = listOf(TimeWindow(ALL_DAYS, 0, 0)), createdAt = past))
        app.prefs.upsert(Rule(name = "Društvene", apps = contacts, limitMin = 30, opensLimit = 15, strict = true, createdAt = past))
        app.prefs.setShorts(ShortsTarget.YOUTUBE, true)
    }

    companion object { const val EXTRA_TAB = "tab" }
}

@Composable
private fun Root(app: App) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<Release?>(null) }
    var bannerHidden by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (app.prefs.autoUpdateCheck) release = Updater.latest() }

    BackHandler(enabled = nav.stack.isNotEmpty() || nav.tab.value != Tab.TODAY) {
        if (!nav.back()) nav.tab(Tab.TODAY)
    }

    val top = nav.stack.lastOrNull()
    val holder = rememberSaveableStateHolder()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = nav.tab.value == t && top == null,
                        onClick = { nav.tab(t) },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.TODAY -> Icons.Filled.Today
                                    Tab.STATS -> Icons.Filled.BarChart
                                    Tab.RULES -> Icons.Filled.Shield
                                    Tab.FOCUS -> Icons.Filled.Timer
                                }, t.label,
                            )
                        },
                        label = { Text(t.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer),
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize()) {
            val r = release
            val showingBanner = r != null && Updater.isNewer(r) && !bannerHidden && r.build != app.prefs.skippedBuild
            if (showingBanner && r != null) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).statusBarsPadding()
                        .clickable { bannerHidden = true; nav.go(Route.Settings) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("Nova verzija 1.0.${r.build} je spremna — dodirni za instalaciju", Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Kasnije", Modifier.clickable { bannerHidden = true; app.prefs.skippedBuild = r.build }, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp)
                }
            }
            val p = if (showingBanner) PaddingValues(top = 0.dp, bottom = pad.calculateBottomPadding()) else pad
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                AnimatedContent(targetState = top ?: nav.tab.value, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { dest ->
                    holder.SaveableStateProvider(if (dest is Tab) "tab-${dest.name}" else "r-${nav.stack.size}-$dest") {
                        when (dest) {
                            is Route.RuleEdit -> RuleEditScreen(dest, p)
                            is Route.AppDetail -> AppDetailScreen(dest.pkg, p)
                            is Route.Settings -> SettingsScreen(p, release) { scope.launch { release = Updater.latest() } }
                            Tab.TODAY -> TodayScreen(p)
                            Tab.STATS -> StatsScreen(p)
                            Tab.RULES -> RulesScreen(p)
                            Tab.FOCUS -> FocusScreen(p)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}
