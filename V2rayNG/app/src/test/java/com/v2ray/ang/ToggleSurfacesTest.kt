package com.v2ray.ang

import com.v2ray.ang.core.ToggleSurfaces
import org.junit.Assert.assertEquals
import org.junit.Test

class ToggleSurfacesTest {
    @Test fun verifiedExitShowsFlag() = assertEquals("🇳🇱", ToggleSurfaces.flagFor("NL", verified = true))
    @Test fun unverifiedStaysBlank() = assertEquals("", ToggleSurfaces.flagFor("IR", verified = false))
    @Test fun unknownStaysBlank() {
        assertEquals("", ToggleSurfaces.flagFor(null, verified = true))
        assertEquals("", ToggleSurfaces.flagFor("XYZ", verified = true))
    }
}
