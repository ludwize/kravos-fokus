package net.kravos.fokus.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import net.kravos.fokus.App

enum class Tab(val label: String) { TODAY("Danas"), STATS("Statistika"), RULES("Pravila"), FOCUS("Fokus") }

sealed class Route {
    data class RuleEdit(val id: String?, val presetApp: String? = null, val template: String? = null) : Route()
    data class AppDetail(val pkg: String) : Route()
    data object Settings : Route()
}

class Navigator {
    val stack = mutableStateListOf<Route>()
    val tab = mutableStateOf(Tab.TODAY)
    fun go(r: Route) { stack.add(r) }
    fun back(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); true } else false
    fun tab(t: Tab) { stack.clear(); tab.value = t }
}

val LocalNav = staticCompositionLocalOf { Navigator() }
val LocalApp = staticCompositionLocalOf<App> { error("no app") }
/** Bumps every time the activity resumes (permission screens return here). */
val LocalResume = staticCompositionLocalOf { 0 }

data class Load<T>(val data: T? = null, val loading: Boolean = true, val error: String? = null)

class Loader<T>(private val st: MutableState<Load<T>>, private val tick: MutableState<Int>) {
    val value: Load<T> get() = st.value
    val data: T? get() = st.value.data
    fun reload() { st.value = st.value.copy(loading = true, error = null); tick.value = tick.value + 1 }
}

/** Loads [block] when [keys] change and keeps refreshing every [refreshMs] while the app is in front. */
@Composable
fun <T> rememberLoad(vararg keys: Any?, refreshMs: Long = 0L, block: suspend () -> T): Loader<T> {
    val state = remember(*keys) { mutableStateOf(Load<T>()) }
    val tick = remember(*keys) { mutableStateOf(0) }
    LaunchedEffect(state, tick.value) {
        while (true) {
            try {
                state.value = Load(block(), false, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.value = state.value.copy(loading = false, error = e.message ?: e.javaClass.simpleName)
            }
            if (refreshMs <= 0) break
            delay(refreshMs)
            while (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) delay(1500)
        }
    }
    return remember(state, tick) { Loader(state, tick) }
}
