package net.kravos.fokus.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.kravos.fokus.App
import net.kravos.fokus.data.Day
import net.kravos.fokus.data.ThemeMode
import net.kravos.fokus.data.fmtMs
import net.kravos.fokus.ui.theme.FokusTheme
import net.kravos.fokus.ui.theme.K
import net.kravos.fokus.ui.theme.LocalPalette

/** Full-screen "this app is blocked" page shown by the guard. */
class BlockActivity : ComponentActivity() {
    private var info by mutableStateOf<Info?>(null)

    data class Info(
        val pkg: String, val kind: Kind, val title: String, val detail: String,
        val strict: Boolean, val grantKey: String, val ruleId: String?,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        info = read(intent)
        val app = application as App
        setContent {
            FokusTheme(ThemeMode.DARK) {
                BackHandler { goHome() }
                androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    info?.let { Screen(app, it) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        info = read(intent)
    }

    private fun read(i: Intent?): Info? {
        i ?: return null
        return Info(
            i.getStringExtra("pkg") ?: return null,
            runCatching { Kind.valueOf(i.getStringExtra("kind")!!) }.getOrDefault(Kind.TIME),
            i.getStringExtra("title").orEmpty(),
            i.getStringExtra("detail").orEmpty(),
            i.getBooleanExtra("strict", true),
            i.getStringExtra("grant").orEmpty(),
            i.getStringExtra("rule"),
        )
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun grantAndReturn(app: App, i: Info) {
        app.prefs.grant(i.grantKey, System.currentTimeMillis() + Engine.GRANT_MS)
        packageManager.getLaunchIntentForPackage(i.pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }

    @Composable
    private fun Screen(app: App, i: Info) {
        val pal = LocalPalette.current
        val label = remember(i.pkg) { app.apps.label(i.pkg) }
        val icon = remember(i.pkg) { app.apps.icon(i.pkg) }
        var stats by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(i) {
            if (app.usage.hasAccess()) {
                val d = app.usage.today()
                val rule = app.prefs.rules.value.firstOrNull { it.id == i.ruleId }
                stats = if (rule != null) {
                    "${rule.name} danas: ${fmtMs(d.msFor(rule.apps))} · ${app.prefs.opens(rule.id).takeIf { it > 0 } ?: d.opensFor(rule.apps)} otvaranja"
                } else {
                    "$label danas: ${fmtMs(d.msFor(listOf(i.pkg)))}"
                }
            }
        }
        val canGrant = !i.strict && i.kind != Kind.SHORTS
        var wait by remember(i) { mutableIntStateOf(10) }
        LaunchedEffect(i) { while (wait > 0) { delay(1000); wait-- } }

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (icon != null) {
                    Image(icon, null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
                    Spacer(Modifier.height(10.dp))
                }
                Text(label, color = pal.dim, fontSize = 14.sp)
                Spacer(Modifier.height(28.dp))
                Text(i.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(i.detail, color = pal.dim, textAlign = TextAlign.Center, fontSize = 15.sp)
                stats?.let {
                    Spacer(Modifier.height(18.dp))
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    if (i.strict) "STROGO" else "MEKANO", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    color = if (i.strict) K.Strict else K.Soft,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(pal.card).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(48.dp))
                Button(
                    onClick = { goHome() }, modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Zatvori", fontWeight = FontWeight.SemiBold) }
                if (canGrant) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { grantAndReturn(app, i) }, enabled = wait == 0,
                        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    ) { Text(if (wait > 0) "Još 5 minuta (sačekaj $wait s)" else "Još 5 minuta") }
                    val n = app.prefs.overridesToday(Day.key())
                    if (n > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("Danas si već produžio $n×", fontSize = 12.sp, color = pal.dim)
                    }
                } else if (i.kind != Kind.SHORTS) {
                    Spacer(Modifier.height(12.dp))
                    Text("Strogo pravilo nema izuzetaka.", fontSize = 12.sp, color = pal.dim)
                }
            }
        }
    }

    companion object {
        fun intent(ctx: Context, pkg: String, b: Block) = Intent(ctx, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("pkg", pkg).putExtra("kind", b.kind.name)
            .putExtra("title", b.title).putExtra("detail", b.detail)
            .putExtra("strict", b.strict).putExtra("grant", b.grantKey)
            .putExtra("rule", b.rule?.id)
    }
}
