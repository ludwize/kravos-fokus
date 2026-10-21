package net.kravos.fokus.ui.components

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kravos.fokus.ui.LocalApp
import net.kravos.fokus.ui.LocalNav
import net.kravos.fokus.ui.theme.K
import net.kravos.fokus.ui.theme.LocalPalette

@Composable
fun TopBar(title: String, back: Boolean = false, actions: @Composable RowScope.() -> Unit = {}) {
    val nav = LocalNav.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = if (back) 4.dp else 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back) IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nazad") }
        Text(
            title, Modifier.weight(1f),
            style = if (back) MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp) else MaterialTheme.typography.titleLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, pad: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPalette.current
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(18.dp))
            .background(pal.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(pad),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = LocalPalette.current.dim)
        trailing()
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp = 36.dp) {
    val app = LocalApp.current
    val img = remember(pkg) { app.apps.icon(pkg) }
    if (img != null) Image(img, null, Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    else Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(LocalPalette.current.track))
}

/** App row with a usage bar relative to [max]. */
@Composable
fun AppRow(pkg: String, value: String, frac: Float, sub: String? = null, color: Color = K.Accent, onClick: (() -> Unit)? = null) {
    val app = LocalApp.current
    val pal = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(pkg)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(remember(pkg) { app.apps.label(pkg) }, Modifier.weight(1f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(5.dp))
            Bar(frac, color)
            if (sub != null) {
                Spacer(Modifier.height(3.dp))
                Text(sub, fontSize = 12.sp, color = pal.dim)
            }
        }
    }
}

@Composable
fun Bar(frac: Float, color: Color, height: Dp = 5.dp) {
    val pal = LocalPalette.current
    Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(pal.track)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}

/** Circular progress ring with content in the middle. */
@Composable
fun Ring(frac: Float, color: Color, size: Dp, stroke: Dp = 12.dp, content: @Composable () -> Unit) {
    val pal = LocalPalette.current
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val r = Size(this.size.width - s, this.size.height - s)
            val o = Offset(s / 2, s / 2)
            drawArc(pal.track, 0f, 360f, false, o, r, style = Stroke(s))
            val f = frac.coerceIn(0f, 1f)
            if (f > 0f) drawArc(color, -90f, 360f * f, false, o, r, style = Stroke(s, cap = StrokeCap.Round))
            // overflow beyond the goal: second lap in a warning tone
            if (frac > 1f) drawArc(K.Bad, -90f, 360f * (frac - 1f).coerceAtMost(1f), false, o, r, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/**
 * Vertical bar chart. [values] in minutes; [goal] draws a dashed line; [highlight] index is drawn in accent,
 * the rest muted.
 */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    highlight: Int? = null,
    goal: Float? = null,
    height: Dp = 140.dp,
    color: Color = K.Accent,
    labelEvery: Int = 1,
    onBarClick: ((Int) -> Unit)? = null,
) {
    val pal = LocalPalette.current
    val max = maxOf(values.maxOrNull() ?: 0f, goal ?: 0f, 1f) * 1.08f
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(height)) {
            Canvas(Modifier.fillMaxWidth().height(height)) {
                val n = values.size.coerceAtLeast(1)
                val slot = size.width / n
                val bw = (slot * 0.62f).coerceAtMost(28.dp.toPx())
                values.forEachIndexed { i, v ->
                    val h = (v / max) * size.height
                    val x = i * slot + (slot - bw) / 2
                    val c = if (highlight == null || highlight == i) color else color.copy(alpha = 0.45f)
                    drawRoundRect(pal.track, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 3))
                    if (h > 0f) drawRoundRect(c, Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 3))
                }
                if (goal != null && goal > 0f) {
                    val y = size.height - goal / max * size.height
                    drawLine(K.Good, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
            }
            if (onBarClick != null) {
                Row(Modifier.fillMaxWidth().height(height)) {
                    values.indices.forEach { i -> Box(Modifier.weight(1f).fillMaxHeight().clickable { onBarClick(i) }) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            if (labelEvery <= 1) {
                labels.forEachIndexed { i, l ->
                    Text(
                        l, Modifier.weight(1f), fontSize = 10.sp,
                        color = if (highlight == i) MaterialTheme.colorScheme.onSurface else pal.dim,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                    )
                }
            } else {
                // one label per group of bars, aligned to the group's first bar
                labels.indices.chunked(labelEvery).forEach { group ->
                    Text(
                        labels[group.first()], Modifier.weight(group.size.toFloat()), fontSize = 10.sp,
                        color = pal.dim, maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    Column(modifier) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 12.sp, color = LocalPalette.current.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, fontSize = 11.sp, color = LocalPalette.current.dim)
    }
}

@Composable
fun StatRow(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    Text(
        text,
        modifier.clip(RoundedCornerShape(50)).background(if (selected) MaterialTheme.colorScheme.primary else pal.track)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
fun Badge(text: String, color: Color) {
    Text(
        text, Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 2.dp),
        color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
    )
}

/** Segmented control. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.track).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (i == selected) pal.card else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, fontSize = 14.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

/** True when the system-wide animation scale is 0 (the emulator smoke test runs that way). */
@Composable
fun animationsOff(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
