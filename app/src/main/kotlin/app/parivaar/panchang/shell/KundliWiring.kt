package app.parivaar.panchang.shell

import app.parivaar.panchang.feature.kundli.KundliViewModel
import app.parivaar.panchang.feature.kundli.ParivaarViewModel
import app.parivaar.panchang.feature.kundli.ProfileEditorViewModel
import app.parivaar.panchang.feature.horoscope.RashifalViewModel

/** The Kundli tab's and Rashifal's state holders (activity-scoped, in [ShellViewModel]). */
class KundliWiring(
    val kundli: KundliViewModel,
    val parivaar: ParivaarViewModel,
    val newEditor: () -> ProfileEditorViewModel,
    /** M11: Rashifal, reading the Kundli tab's active profile (never a route argument). */
    val rashifal: RashifalViewModel,
)
