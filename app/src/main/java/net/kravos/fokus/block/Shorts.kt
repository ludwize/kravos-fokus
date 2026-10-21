package net.kravos.fokus.block

import android.view.accessibility.AccessibilityNodeInfo
import net.kravos.fokus.data.ShortsTarget

/**
 * Recognises a short-video player inside an app from its view tree. These ids come from the apps' own
 * layouts and change from time to time, so each target has exact ids plus a looser fallback.
 */
object Shorts {
    private val EXACT = mapOf(
        ShortsTarget.YOUTUBE to listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/reel_watch_player",
            "com.google.android.youtube:id/reel_player_underlay",
        ),
        ShortsTarget.INSTAGRAM to listOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_viewer_pager",
            "com.instagram.android:id/root_clips_layout",
        ),
        ShortsTarget.FACEBOOK to listOf(
            "com.facebook.katana:id/reels_viewer_container",
            "com.facebook.katana:id/reel_viewer",
        ),
        ShortsTarget.SNAPCHAT to listOf(
            "com.snapchat.android:id/spotlight_container",
            "com.snapchat.android:id/spotlight_feed",
        ),
    )

    fun detect(target: ShortsTarget, root: AccessibilityNodeInfo): Boolean {
        EXACT[target]?.forEach { id ->
            val hits = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull().orEmpty()
            if (hits.any { it.isVisibleToUser }) return true
        }
        return when (target) {
            ShortsTarget.YOUTUBE, ShortsTarget.INSTAGRAM -> false
            // Facebook and Snapchat render with few ids: look for the selected "Reels"/"Spotlight" tab.
            ShortsTarget.FACEBOOK -> selectedTab(root, "reels")
            ShortsTarget.SNAPCHAT -> selectedTab(root, "spotlight")
            ShortsTarget.TIKTOK -> true
        }
    }

    private fun selectedTab(root: AccessibilityNodeInfo, word: String): Boolean {
        var seen = 0
        fun walk(n: AccessibilityNodeInfo?, depth: Int): Boolean {
            if (n == null || depth > 30 || seen > 600) return false
            seen++
            val d = n.contentDescription?.toString()?.lowercase().orEmpty()
            val t = n.text?.toString()?.lowercase().orEmpty()
            val id = n.viewIdResourceName?.lowercase().orEmpty()
            if (n.isSelected && (d.startsWith(word) || t == word)) return true
            if (id.contains("${word}_viewer") || id.contains("${word}_player")) return true
            for (i in 0 until n.childCount) if (walk(runCatching { n.getChild(i) }.getOrNull(), depth + 1)) return true
            return false
        }
        return walk(root, 0)
    }
}
