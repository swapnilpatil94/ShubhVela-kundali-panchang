package app.parivaar.panchang.shell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Tab { AAJ, CALENDAR, KUNDLI, BONDS, BHAKTI }

sealed interface Dest {
    /** Immersive sacred or full-screen destinations (aarti, jap, reveal) hide the dock. None exist yet. */
    val immersive: Boolean get() = false

    data class Root(val tab: Tab) : Dest

    data object Settings : Dest

    data object Conventions : Dest

    data object Privacy : Dest

    /** Festival guide page; [id] is a festival rule id (lower-case letters, digits, underscore). */
    data class FestivalGuide(val id: String) : Dest

    data object Grahan : Dest

    data object Transits : Dest

    data object ShouldI : Dest

    /** M11 Rashifal. Carries no profile id, sign or birth data (the view model reads the active profile). */
    data object Rashifal : Dest
}

/** Hand-written back stack: one stack per [Tab], each rooted at `Root(tab)`. Thread-safe. */
class AppNavigator(initial: Tab = Tab.AAJ) {
    private val lock = Any()
    private val stacks: Map<Tab, MutableList<Dest>> = Tab.entries.associateWith { mutableListOf<Dest>(Dest.Root(it)) }
    private var currentTab: Tab = initial

    private val _current = MutableStateFlow<Dest>(Dest.Root(initial))
    private val _tab = MutableStateFlow(initial)
    private val _canGoBack = MutableStateFlow(initial != Tab.AAJ)

    val current: StateFlow<Dest> get() = _current
    val tab: StateFlow<Tab> get() = _tab

    /** True while [back] would consume the event (the activity then keeps predictive back enabled). */
    val canGoBack: StateFlow<Boolean> get() = _canGoBack

    private fun publish() {
        _tab.value = currentTab
        _current.value = stacks.getValue(currentTab).last()
        _canGoBack.value = stacks.getValue(currentTab).size > 1 || currentTab != Tab.AAJ
    }

    fun select(tab: Tab) =
        synchronized(lock) {
            if (tab == currentTab) {
                val s = stacks.getValue(tab)
                while (s.size > 1) s.removeAt(s.lastIndex)
            } else {
                currentTab = tab
            }
            publish()
        }

    /** Pushes onto the current tab's stack. `Root(t)` is a tab switch, not a push. */
    fun push(d: Dest) {
        if (d is Dest.Root) return select(d.tab)
        synchronized(lock) {
            val s = stacks.getValue(currentTab)
            if (s.last() != d) s.add(d)
            publish()
        }
    }

    /** False means nothing was consumed and the activity should finish. */
    fun back(): Boolean =
        synchronized(lock) {
            val s = stacks.getValue(currentTab)
            when {
                s.size > 1 -> s.removeAt(s.lastIndex)
                currentTab != Tab.AAJ -> currentTab = Tab.AAJ
                else -> return false
            }
            publish()
            true
        }

    /** Stable string keys: element 0 is the current tab; then one `TAB:key,key` entry per tab with a non-root stack. */
    fun save(): List<String> =
        synchronized(lock) {
            buildList {
                add(currentTab.name)
                for (t in Tab.entries) {
                    val extra = stacks.getValue(t).drop(1)
                    if (extra.isNotEmpty()) add("${t.name}:" + extra.joinToString(",") { key(it) })
                }
            }
        }

    companion object {
        private val ID = Regex("[a-z0-9_]{1,64}")

        private fun key(d: Dest): String =
            when (d) {
                is Dest.Root -> "root"
                Dest.Settings -> "settings"
                Dest.Conventions -> "conventions"
                Dest.Privacy -> "privacy"
                is Dest.FestivalGuide -> "guide=${d.id}"
                Dest.Grahan -> "grahan"
                Dest.Transits -> "transits"
                Dest.ShouldI -> "shouldi"
                Dest.Rashifal -> "rashifal"
            }

        private fun parse(k: String): Dest? =
            when (k) {
                "settings" -> Dest.Settings
                "conventions" -> Dest.Conventions
                "privacy" -> Dest.Privacy
                "grahan" -> Dest.Grahan
                "transits" -> Dest.Transits
                "shouldi" -> Dest.ShouldI
                "rashifal" -> Dest.Rashifal
                else -> k.takeIf { it.startsWith("guide=") }?.substringAfter('=')?.takeIf(ID::matches)
                    ?.let { Dest.FestivalGuide(it) }
            }

        /** Malformed or unknown input degrades to the parts that parse; never throws. */
        fun restore(s: List<String>): AppNavigator {
            val tab = s.firstOrNull()?.let { n -> Tab.entries.firstOrNull { it.name == n } } ?: Tab.AAJ
            val nav = AppNavigator(tab)
            for (line in s.drop(1)) {
                val t = Tab.entries.firstOrNull { line.startsWith(it.name + ":") } ?: continue
                val stack = nav.stacks.getValue(t)
                for (d in line.substringAfter(':').split(',').mapNotNull(::parse)) {
                    if (stack.last() != d) stack.add(d)
                }
            }
            nav.publish()
            return nav
        }
    }
}
