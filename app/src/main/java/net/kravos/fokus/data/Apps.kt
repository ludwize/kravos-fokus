package net.kravos.fokus.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class AppInfo(val pkg: String, val label: String, val category: Int)

/** Installed apps, labels, icons and the packages that never count as "using an app". */
class Apps(private val ctx: Context) {
    private val pm: PackageManager = ctx.packageManager
    private val labels = ConcurrentHashMap<String, String>()
    private val categories = ConcurrentHashMap<String, Int>()
    private val icons = object : LruCache<String, ImageBitmap>(160) {}
    @Volatile private var launchableCache: List<AppInfo>? = null
    @Volatile private var ignoredCache: Set<String>? = null

    /** Apps with a launcher icon, sorted by name (our own app excluded). */
    suspend fun launchable(refresh: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        launchableCache?.takeIf { !refresh } ?: run {
            val ri = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            ri.map { it.activityInfo.packageName }.distinct()
                .filter { it != ctx.packageName }
                .map { AppInfo(it, label(it), category(it)) }
                .sortedBy { it.label.lowercase() }
                .also { launchableCache = it }
        }
    }

    fun label(pkg: String): String = labels.getOrPut(pkg) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })
    }

    fun category(pkg: String): Int = categories.getOrPut(pkg) {
        val known = KNOWN_CATEGORY[pkg]
        known ?: runCatching { pm.getApplicationInfo(pkg, 0).category }.getOrDefault(ApplicationInfo.CATEGORY_UNDEFINED)
    }

    fun installed(pkg: String): Boolean = runCatching { pm.getApplicationInfo(pkg, 0); true }.getOrDefault(false)

    fun icon(pkg: String): ImageBitmap? {
        icons.get(pkg)?.let { return it }
        val bmp = runCatching {
            pm.getApplicationIcon(pkg).toBitmap(96, 96, Bitmap.Config.ARGB_8888).asImageBitmap()
        }.getOrNull() ?: return null
        icons.put(pkg, bmp)
        return bmp
    }

    /** Launchers, system UI, keyboards and ourselves: switching to these isn't "opening an app". */
    fun ignored(): Set<String> = ignoredCache ?: (launchers() + transient()).also { ignoredCache = it }

    /** Home screens plus this app: being here means the user left whatever app they were in. */
    fun launchers(): Set<String> = launcherCache ?: run {
        val home = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
        (home + ctx.packageName + "com.android.settings.intelligence").toSet().also { launcherCache = it }
    }

    /** Windows that float over the current app (shade, keyboard, system dialogs) and don't change it. */
    fun transient(): Set<String> = transientCache ?: run {
        val imm = ctx.getSystemService(InputMethodManager::class.java)
        val ime = runCatching { imm.enabledInputMethodList.map { it.packageName } }.getOrDefault(emptyList())
        (ime + listOf("com.android.systemui", "android", "com.google.android.permissioncontroller", "com.android.permissioncontroller")).toSet().also { transientCache = it }
    }

    @Volatile private var launcherCache: Set<String>? = null
    @Volatile private var transientCache: Set<String>? = null

    fun invalidate() { launchableCache = null; ignoredCache = null; launcherCache = null; transientCache = null }

    companion object {
        /** Popular apps whose manifest category is missing or misleading. */
        val KNOWN_CATEGORY = mapOf(
            "com.google.android.youtube" to ApplicationInfo.CATEGORY_VIDEO,
            "com.instagram.android" to ApplicationInfo.CATEGORY_SOCIAL,
            "com.facebook.katana" to ApplicationInfo.CATEGORY_SOCIAL,
            "com.facebook.orca" to ApplicationInfo.CATEGORY_SOCIAL,
            "com.zhiliaoapp.musically" to ApplicationInfo.CATEGORY_VIDEO,
            "com.twitter.android" to ApplicationInfo.CATEGORY_SOCIAL,
            "com.reddit.frontpage" to ApplicationInfo.CATEGORY_SOCIAL,
            "com.snapchat.android" to ApplicationInfo.CATEGORY_SOCIAL,
            "com.linkedin.android" to ApplicationInfo.CATEGORY_SOCIAL,
            "com.whatsapp" to ApplicationInfo.CATEGORY_SOCIAL,
            "org.telegram.messenger" to ApplicationInfo.CATEGORY_SOCIAL,
            "com.viber.voip" to ApplicationInfo.CATEGORY_SOCIAL,
            "com.netflix.mediaclient" to ApplicationInfo.CATEGORY_VIDEO,
            "com.spotify.music" to ApplicationInfo.CATEGORY_AUDIO,
            "com.android.chrome" to ApplicationInfo.CATEGORY_PRODUCTIVITY,
        )

        fun categoryLabel(c: Int): String = when (c) {
            ApplicationInfo.CATEGORY_SOCIAL -> "Društvene mreže"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_GAME -> "Igre"
            ApplicationInfo.CATEGORY_NEWS -> "Vesti"
            ApplicationInfo.CATEGORY_AUDIO -> "Muzika i audio"
            ApplicationInfo.CATEGORY_IMAGE -> "Fotografije"
            ApplicationInfo.CATEGORY_MAPS -> "Mape i navigacija"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Produktivnost"
            else -> "Ostalo"
        }

        /** Categories that are typically distracting (used for "add all" shortcuts). */
        val DISTRACTING = listOf(
            ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_NEWS,
        )
    }
}
