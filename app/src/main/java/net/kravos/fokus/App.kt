package net.kravos.fokus

import android.app.Application
import net.kravos.fokus.data.Apps
import net.kravos.fokus.data.Http
import net.kravos.fokus.data.Prefs
import net.kravos.fokus.data.UsageEngine
import net.kravos.fokus.work.Notif
import net.kravos.fokus.work.ReportWorker

class App : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var apps: Apps
        private set
    lateinit var usage: UsageEngine
        private set

    override fun onCreate() {
        super.onCreate()
        Http.init(this)
        prefs = Prefs(this)
        apps = Apps(this)
        usage = UsageEngine(this, apps)
        prefs.applyPending()
        prefs.prune()
        Notif.channels(this)
        ReportWorker.schedule(this)
        prefs.session.value?.let { if (it.active()) Notif.session(this, it) }
    }
}
