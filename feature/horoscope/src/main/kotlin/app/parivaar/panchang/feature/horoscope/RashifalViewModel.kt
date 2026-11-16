package app.parivaar.panchang.feature.horoscope

import app.parivaar.panchang.content.horoscope.Period
import app.parivaar.panchang.content.horoscope.Line
import app.parivaar.panchang.content.pack.ContentPack
import app.parivaar.panchang.content.text.TemplateRenderer
import app.parivaar.panchang.content.text.TermResolver
import app.parivaar.panchang.engine.jyotish.Rashi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/** Rendered reading lines. [usedFallback] is true when any line is shown in English instead of the app language. */
data class ReadingUi(val lines: List<String>, val usedFallback: Boolean) {
    override fun toString(): String = "ReadingUi(redacted)"
}

/** Why "Your day" is not shown. */
enum class YourDayStatus { NO_PROFILE, NEEDS_BIRTH_TIME, SHOWN, UNAVAILABLE }

data class RashifalState(
    val period: Period = Period.DAY,
    val sign: Rashi = Rashi.MESHA,
    /** The basis the [sign] came from; null when the sign was picked by hand or no profile exists. */
    val basis: Basis? = null,
    /** Bases the active profile can offer (Lagna only with a birth time). */
    val bases: Set<Basis> = emptySet(),
    val reading: ReadingUi? = null,
    val yourDay: ReadingUi? = null,
    val yourDayStatus: YourDayStatus = YourDayStatus.NO_PROFILE,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val noPlace: Boolean = false,
) {
    /** The "Some text is shown in English" note. */
    val showFallbackNote: Boolean get() = reading?.usedFallback == true || yourDay?.usedFallback == true

    override fun toString(): String = "RashifalState(redacted)"
}

sealed interface RashifalEvent {
    data class PeriodTab(val period: Period) : RashifalEvent
    data class Sign(val sign: Rashi) : RashifalEvent
    data class PickBasis(val basis: Basis) : RashifalEvent

    /** The app language (`en`, `hi`, `mr`, `gu`; anything else falls back to English with the note). */
    data class Language(val lang: String) : RashifalEvent

    /** The screen opened or the date may have changed. */
    data object Refresh : RashifalEvent
}

/** Renders lines: each in [lang], else the English line (marked), else dropped (lint keeps this unreachable). */
object ReadingText {
    fun render(lines: List<Line>, renderer: TemplateRenderer, lang: String): ReadingUi {
        var fallback = false
        val out = lines.mapNotNull { l ->
            val r = renderer.render(l.id, l.args, lang).getOrNull()
                ?: renderer.render(l.id, l.args, TermResolver.FALLBACK).getOrNull()?.copy(usedFallback = true)
            if (r != null && r.usedFallback && lang != TermResolver.FALLBACK) fallback = true
            r?.text
        }
        return ReadingUi(out, fallback)
    }

    /** Variant count of a `gochar.<g>.h<h>` prefix in the pack serving [lang] (en when falling back); at least 1. */
    fun variants(resolver: TermResolver, lang: String): (String) -> Int = { prefix ->
        resolver.packFor("$prefix.v1", lang)?.variantCount(prefix)?.coerceAtLeast(1) ?: 1
    }
}

/**
 * Rashifal state holder (M11 I5). Reads the active profile's [natal] snapshot (never from the route); engine and
 * rendering run on [compute]. Nothing is logged or stored; readings live in memory only.
 */
@Suppress("LongParameterList")
class RashifalViewModel(
    natal: Flow<NatalSnapshot?>,
    private val engine: RashifalEngine,
    private val packs: () -> Map<String, ContentPack>,
    private val timeFormat: (Instant, Period) -> String,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val compute: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(RashifalState())
    val state: StateFlow<RashifalState> = _state.asStateFlow()
    private var natalNow: NatalSnapshot? = null
    private var lang: String = TermResolver.FALLBACK
    private var job: Job? = null
    private val resolver: TermResolver by lazy { TermResolver(packs()) }

    init {
        scope.launch {
            natal.collect { n ->
                natalNow = n
                _state.update { s -> withNatal(s, n) }
                load()
            }
        }
    }

    fun onEvent(e: RashifalEvent) {
        when (e) {
            is RashifalEvent.PeriodTab -> _state.update { it.copy(period = e.period) }
            is RashifalEvent.Sign -> _state.update { it.copy(sign = e.sign, basis = null) }
            is RashifalEvent.PickBasis -> {
                val sign = natalNow?.sign(e.basis) ?: return
                _state.update { it.copy(sign = sign, basis = e.basis) }
            }
            is RashifalEvent.Language -> if (e.lang == lang) return else lang = e.lang
            RashifalEvent.Refresh -> Unit
        }
        load()
    }

    private fun load() {
        val s = _state.value
        val n = natalNow
        val language = lang
        job?.cancel()
        _state.update { it.copy(loading = true, failed = false) }
        job = scope.launch {
            val result = withContext(compute) {
                runCatching {
                    val now = clock.now()
                    val renderer = TemplateRenderer(resolver) { timeFormat(it, s.period) }
                    val variants = ReadingText.variants(resolver, language)
                    val h1 = engine.h1(s.sign, s.period, now, variants)
                    val dayRenderer = TemplateRenderer(resolver) { timeFormat(it, Period.DAY) }
                    val h2 = n?.takeIf { it.lagna != null }?.let { engine.h2(it, now, variants) }
                    Pair(h1?.let { ReadingText.render(it, renderer, language) }, h2?.let {
                        ReadingText.render(it, dayRenderer, language)
                    })
                }
            }
            result.onSuccess { (h1, h2) ->
                _state.update {
                    it.copy(
                        // No line rendered at all (for example a pack failed verification): an error, never blank.
                        reading = h1?.takeIf { r -> r.lines.isNotEmpty() },
                        yourDay = h2?.takeIf { r -> r.lines.isNotEmpty() },
                        loading = false, failed = h1 != null && h1.lines.isEmpty(), noPlace = h1 == null,
                        yourDayStatus = yourDayStatus(n, h2),
                    )
                }
            }.onFailure { t ->
                if (t is CancellationException) throw t
                _state.update { it.copy(reading = null, yourDay = null, loading = false, failed = true) }
            }
        }
    }

    private companion object {
        fun yourDayStatus(n: NatalSnapshot?, h2: ReadingUi?): YourDayStatus = when {
            n == null -> YourDayStatus.NO_PROFILE
            n.lagna == null -> YourDayStatus.NEEDS_BIRTH_TIME
            h2 == null -> YourDayStatus.UNAVAILABLE
            else -> YourDayStatus.SHOWN
        }

        /** Defaults the sign to the profile's Moon sign until the user picks one by hand (else MESHA). */
        fun withNatal(s: RashifalState, n: NatalSnapshot?): RashifalState {
            val bases = n?.let { Basis.entries.filter { b -> it.sign(b) != null }.toSet() }.orEmpty()
            val manual = s.basis == null && s.bases.isNotEmpty()
            val basis = s.basis?.takeIf { it in bases } ?: Basis.MOON
            return when {
                // Vault locked or no profile: drop anything derived from the chart.
                n == null -> s.copy(
                    sign = if (s.basis == null && s.bases.isEmpty()) s.sign else Rashi.MESHA, basis = null,
                    bases = emptySet(), yourDay = null, yourDayStatus = YourDayStatus.NO_PROFILE,
                )
                manual -> s.copy(bases = bases)
                else -> s.copy(sign = n.sign(basis) ?: n.moon, basis = basis, bases = bases)
            }
        }
    }
}
