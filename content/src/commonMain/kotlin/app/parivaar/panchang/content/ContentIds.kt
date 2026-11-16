package app.parivaar.panchang.content

import app.parivaar.panchang.engine.jyotish.Graha

/**
 * Interpretation content ids (docs/specs/M11.md section 3 "IDs"). Graha codes follow [Graha] ordinals 0..8.
 * [REQUIRED] holds every id with its v1 only: 108 gochar + 2 + 1 + 9 tara + 1 + 1 + 5 Sade Sati = 127.
 */
object ContentIds {
    private const val HOUSES = 12
    private const val TARAS = 9

    /** Short codes in [Graha] order. */
    val GRAHA_CODES: List<String> = listOf("su", "mo", "ma", "me", "ju", "ve", "sa", "ra", "ke")

    const val MOON_SHIFT = "rf.moon_shift"
    const val INGRESS = "rf.ingress"
    const val RETRO = "rf.retro"
    const val DASHA_LORD = "rf.dasha_lord"
    const val CHANDRASHTAMA = "rf.chandrashtama"

    /** Sade Sati and Dhaiya phases, in `SadeSatiPhase` order without NONE. */
    val SADESATI: List<String> = listOf("rising", "peak", "setting", "dhaiya_4th", "dhaiya_8th").map {
        "rf.sadesati.$it"
    }

    /** `gochar.<g>.h<house>` without the variant suffix (the prefix for [ContentPack.variantCount]). */
    fun gocharPrefix(g: Graha, house: Int): String {
        require(house in 1..HOUSES) { "house out of range" }
        return "gochar.${GRAHA_CODES[g.ordinal]}.h$house"
    }

    /** `gochar.<g>.h<house>.v<v>`; [v] is 1-based. */
    fun gochar(g: Graha, house: Int, v: Int): String {
        require(v >= 1) { "variant out of range" }
        return "${gocharPrefix(g, house)}.v$v"
    }

    /** `rf.tara.t<n>`, [n] in 1..9. */
    fun tara(n: Int): String {
        require(n in 1..TARAS) { "tara out of range" }
        return "rf.tara.t$n"
    }

    val REQUIRED: Set<String> = buildSet {
        for (g in Graha.entries) for (h in 1..HOUSES) add(gochar(g, h, 1))
        add(MOON_SHIFT)
        add(INGRESS)
        add(RETRO)
        for (t in 1..TARAS) add(tara(t))
        add(DASHA_LORD)
        add(CHANDRASHTAMA)
        addAll(SADESATI)
    }

    /** The named arguments each fixed id takes (lint L5 compares packs against en; this is the code's contract). */
    val ARGS: Map<String, Set<String>> = mapOf(
        MOON_SHIFT to setOf("time", "rashi"),
        INGRESS to setOf("graha", "rashi", "time"),
        RETRO to setOf("graha"),
        DASHA_LORD to setOf("graha"),
    )
}
