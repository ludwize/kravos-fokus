package net.kravos.fokus.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kravos.fokus.block.GuardService
import net.kravos.fokus.ui.LocalApp
import net.kravos.fokus.ui.LocalResume
import net.kravos.fokus.ui.theme.K
import net.kravos.fokus.ui.theme.LocalPalette
import net.kravos.fokus.work.Notif

data class PermState(val usage: Boolean, val guard: Boolean, val notif: Boolean, val battery: Boolean) {
    val essentials get() = usage && guard
    val all get() = usage && guard && notif && battery
}

@Composable
fun rememberPerms(): PermState {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val resume = LocalResume.current
    return remember(resume) {
        PermState(
            usage = app.usage.hasAccess(),
            guard = GuardService.isEnabled(ctx),
            notif = Notif.allowed(ctx),
            battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName),
        )
    }
}

object PermIntents {
    fun usage() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun appInfo(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @android.annotation.SuppressLint("BatteryLife")
    fun battery(ctx: Context) = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Checklist of the permissions the app needs, each with a button that opens the right system page. */
@Composable
fun PermissionSteps(p: PermState, showDone: Boolean = true) {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column {
        Step(
            p.usage, "Pristup korišćenju", "Za statistiku i dnevne limite.",
            showDone,
        ) { runCatching { ctx.startActivity(PermIntents.usage()) } }
        Step(
            p.guard, "Čuvar (pristupačnost)",
            "Za blokiranje, rasporede, fokus i Shorts. Ako je opcija siva: Info o aplikaciji → ⋮ → „Dozvoli ograničena podešavanja”, pa ponovo.",
            showDone,
            secondary = "Info o aplikaciji" to { runCatching { ctx.startActivity(PermIntents.appInfo(ctx)) } },
        ) { runCatching { ctx.startActivity(GuardService.settingsIntent()) } }
        Step(p.notif, "Notifikacije", "Upozorenja pred limit, dnevni izveštaj i fokus sesija.", showDone) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Step(p.battery, "Rad u pozadini", "Da sistem ne gasi čuvara i izveštaj.", showDone) {
            runCatching { ctx.startActivity(PermIntents.battery(ctx)) }
        }
    }
}

@Composable
private fun Step(
    done: Boolean, title: String, desc: String, showDone: Boolean,
    secondary: Pair<String, () -> Unit>? = null, onClick: () -> Unit,
) {
    if (done && !showDone) return
    val pal = LocalPalette.current
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null,
            Modifier.size(22.dp).padding(top = 1.dp), tint = if (done) K.Good else pal.dim,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (!done) {
                Text(desc, fontSize = 12.sp, color = pal.dim)
                Spacer(Modifier.height(6.dp))
                Row {
                    FilledTonalButton(onClick = onClick) { Text("Uključi") }
                    if (secondary != null) {
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = secondary.second) { Text(secondary.first) }
                    }
                }
            }
        }
    }
}
