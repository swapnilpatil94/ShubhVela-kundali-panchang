@file:Suppress("MaxLineLength")
package app.parivaar.panchang.core.designsystem.tokens

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Semantic haptic moments (DESIGN_V2 section 9). Never buzzy one-shots; the system haptics setting is honoured. */
enum class PpHaptic { TAB, BEAD, COMPLETE, BELL, REWARD, ERROR }

object PpHaptics {
    /** Platform constant for [haptic] on [sdk]; below API 34/30 the closest older constants are used. */
    fun constantFor(haptic: PpHaptic, sdk: Int): Int = when (haptic) {
        PpHaptic.TAB ->
            if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
        PpHaptic.BEAD ->
            if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
        PpHaptic.COMPLETE, PpHaptic.BELL, PpHaptic.REWARD ->
            if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        PpHaptic.ERROR ->
            if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    }

    fun perform(view: View, haptic: PpHaptic) {
        view.performHapticFeedback(constantFor(haptic, Build.VERSION.SDK_INT))
    }
}

/** `val haptic = rememberPpHaptics(); haptic(PpHaptic.TAB)`. */
@Composable
fun rememberPpHaptics(): (PpHaptic) -> Unit {
    val view = LocalView.current
    return remember(view) { { h: PpHaptic -> PpHaptics.perform(view, h) } }
}
