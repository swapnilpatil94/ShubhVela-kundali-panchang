package app.parivaar.panchang

import android.view.WindowManager

/**
 * The "privacy screen" setting (MASTER Part 5): FLAG_SECURE blocks screenshots, screen recording and the recents
 * thumbnail. [setFlags] is `Window::setFlags` (a seam so the decision is unit-testable without a window).
 */
internal object PrivacyScreen {
    const val FLAG = WindowManager.LayoutParams.FLAG_SECURE

    fun apply(enabled: Boolean, setFlags: (flags: Int, mask: Int) -> Unit) = setFlags(if (enabled) FLAG else 0, FLAG)
}
