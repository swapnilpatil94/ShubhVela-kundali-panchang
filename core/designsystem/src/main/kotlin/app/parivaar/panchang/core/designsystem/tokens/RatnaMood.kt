package app.parivaar.panchang.core.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colour mood. NIGHT is the dark default, CHANDAN the light mode; UTSAV and PAPER are scoped, never global (D5). */
enum class RatnaMood { NIGHT, UTSAV, CHANDAN, PAPER }

/** Compose view of [PpPalette.Spec]. Roles, not hexes: features never name a colour. */
@Immutable
data class RatnaColors(
    val mood: RatnaMood,
    val isDark: Boolean,
    val bg0: Color, val bg1: Color,
    val lacquerTop: Color, val lacquerBottom: Color,
    val ink3: Color, val line: Color,
    val text: Color, val text2: Color, val text3: Color, val onSky: Color,
    /** Auspicious and primary accent for text and icons. */
    val gold: Color,
    val goldFill: Color, val onGoldFill: Color,
    /** Avoid, Rahu, caution. */
    val kumkum: Color,
    /** Neutral times. */
    val okay: Color,
    val glassTintTop: Color, val glassTintBottom: Color, val glassBorder: Color,
    /** Top, middle and bottom stops of the gold button gradient. */
    val goldGradient: List<Color>,
)

internal fun PpPalette.Spec.toColors() = RatnaColors(
    mood, isDark, Color(bg0), Color(bg1), Color(lacquerTop), Color(lacquerBottom), Color(ink3), Color(line),
    Color(text), Color(text2), Color(text3), Color(onSky),
    Color(gold), Color(goldFill), Color(onGoldFill), Color(kumkum), Color(okay),
    Color(glassTintTop), Color(glassTintBottom), Color(glassBorder),
    listOf(Color(PpPalette.GOLD_GRAD_TOP), Color(PpPalette.GOLD_GRAD_MID), Color(PpPalette.GOLD_GRAD_BOTTOM)),
)

fun ratnaColorsOf(mood: RatnaMood): RatnaColors = ratnaCache.getValue(mood)

private val ratnaCache: Map<RatnaMood, RatnaColors> =
    RatnaMood.entries.associateWith { PpPalette.of(it).toColors() }

val LocalRatnaColors = compositionLocalOf { ratnaColorsOf(RatnaMood.NIGHT) }
