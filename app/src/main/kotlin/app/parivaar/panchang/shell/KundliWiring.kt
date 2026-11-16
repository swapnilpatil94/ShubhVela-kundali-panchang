package app.parivaar.panchang.shell

import app.parivaar.panchang.feature.kundli.KundliViewModel
import app.parivaar.panchang.feature.kundli.ParivaarViewModel
import app.parivaar.panchang.feature.kundli.ProfileEditorViewModel

/** The Kundli tab's state holders (activity-scoped, in [ShellViewModel]). */
class KundliWiring(
    val kundli: KundliViewModel,
    val parivaar: ParivaarViewModel,
    val newEditor: () -> ProfileEditorViewModel,
)
