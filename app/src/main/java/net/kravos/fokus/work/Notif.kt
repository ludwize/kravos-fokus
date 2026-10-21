package net.kravos.fokus.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import net.kravos.fokus.R
import net.kravos.fokus.data.Session
import net.kravos.fokus.ui.MainActivity

object Notif {
    private const val CH_WARN = "limits"
    private const val CH_REPORT = "report"
    private const val CH_SESSION = "session"
    const val ID_SESSION = 10
    const val ID_REPORT = 11
    const val ID_SESSION_DONE = 12

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_WARN, "Upozorenja o limitima", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_REPORT, "Dnevni izveštaj", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_SESSION, "Fokus sesija", NotificationManager.IMPORTANCE_LOW))
    }

    fun allowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(ctx: Context, tab: String? = null): PendingIntent = PendingIntent.getActivity(
        ctx, tab.hashCode(),
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { tab?.let { putExtra(MainActivity.EXTRA_TAB, it) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(ctx: Context, id: Int, n: android.app.Notification) {
        if (!allowed(ctx)) return
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    fun warn(ctx: Context, key: String, title: String, text: String) = post(
        ctx, 1000 + (key.hashCode() and 0xfff),
        NotificationCompat.Builder(ctx, CH_WARN)
            .setSmallIcon(R.drawable.ic_launcher_mono)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(open(ctx)).setAutoCancel(true)
            .setTimeoutAfter(10 * 60_000L)
            .build(),
    )

    fun report(ctx: Context, title: String, text: String) = post(
        ctx, ID_REPORT,
        NotificationCompat.Builder(ctx, CH_REPORT)
            .setSmallIcon(R.drawable.ic_launcher_mono)
            .setContentTitle(title).setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(ctx, "STATS")).setAutoCancel(true)
            .build(),
    )

    fun session(ctx: Context, s: Session) = post(
        ctx, ID_SESSION,
        NotificationCompat.Builder(ctx, CH_SESSION)
            .setSmallIcon(R.drawable.ic_launcher_mono)
            .setContentTitle(if (s.strict) "Stroga fokus sesija" else "Fokus sesija")
            .setContentText("Ometajuće aplikacije su blokirane")
            .setWhen(s.endAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(open(ctx, "FOCUS"))
            .build(),
    )

    fun sessionDone(ctx: Context, minutes: Int) {
        NotificationManagerCompat.from(ctx).cancel(ID_SESSION)
        post(
            ctx, ID_SESSION_DONE,
            NotificationCompat.Builder(ctx, CH_WARN)
                .setSmallIcon(R.drawable.ic_launcher_mono)
                .setContentTitle("Sesija završena")
                .setContentText("$minutes min fokusa. Aplikacije su ponovo dostupne.")
                .setContentIntent(open(ctx, "FOCUS")).setAutoCancel(true)
                .build(),
        )
    }

    fun cancelSession(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_SESSION)
}
