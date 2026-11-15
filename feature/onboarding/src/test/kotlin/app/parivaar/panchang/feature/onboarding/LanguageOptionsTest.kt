package app.parivaar.panchang.feature.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageOptionsTest {
    @Test fun exactlyTwelveTagsInOrder() {
        assertEquals(
            listOf("en", "hi", "bn", "mr", "te", "ta", "gu", "kn", "ml", "or", "pa", "as"),
            LanguageOptions.all.map { it.tag },
        )
    }

    @Test fun preselectUsesPrimaryDeviceLanguage() {
        assertEquals("pa", LanguageOptions.preselect(listOf("pa-IN")))
        assertEquals("hi", LanguageOptions.preselect(listOf("hi-IN", "en-US")))
        assertEquals("en", LanguageOptions.preselect(listOf("fr-FR")))
        assertEquals("en", LanguageOptions.preselect(emptyList()))
    }
}
