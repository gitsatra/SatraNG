package com.v2ray.ang

import com.v2ray.ang.handler.parseExitBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExitIpProbeParseTest {
    @Test fun ipSb() = assertEquals("1.2.3.4" to "NL",
        parseExitBody("""{"ip":"1.2.3.4","country":"Netherlands","country_code":"NL"}"""))
    @Test fun ipApi() = assertEquals("5.6.7.8" to "SE",
        parseExitBody("""{"status":"success","countryCode":"SE","query":"5.6.7.8"}"""))
    @Test fun countryIs() = assertEquals("9.9.9.9" to "DE",
        parseExitBody("""{"ip":"9.9.9.9","country":"DE"}"""))
    @Test fun ipWho() = assertEquals("8.8.8.8" to "US",
        parseExitBody("""{"ip":"8.8.8.8","success":true,"country_code":"US"}"""))
    @Test fun fullNameOnlyIsNotACode() = assertNull(parseExitBody("""{"ip":"1.1.1.1","country":"Netherlands"}""")?.second)
    @Test fun emptyJsonHasNoCountry() = assertNull(parseExitBody("{}")?.second)
}
