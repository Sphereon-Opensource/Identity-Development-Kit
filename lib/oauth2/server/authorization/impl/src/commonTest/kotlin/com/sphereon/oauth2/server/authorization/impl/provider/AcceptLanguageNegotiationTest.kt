/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.oauth2.server.authorization.impl.provider

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RFC 7231 §5.3.5 Accept-Language negotiation. Locks down q-value sorting, q=0 rejections,
 * language-tag prefix matching, and the default-locale fallback used by the IDK login page.
 */
class AcceptLanguageNegotiationTest {
    private val supported = setOf("en", "nl")
    private val default = "en"

    @Test
    fun singleLocaleNlReturnsDutch() {
        val locale = AcceptLanguageNegotiation.negotiate("nl", supported, default)
        assertEquals("nl", locale)
    }

    @Test
    fun qWeightedListPicksHighestQ() {
        // en;q=0.3, nl;q=0.9 -> Dutch wins on q-value despite earlier appearance order
        val locale = AcceptLanguageNegotiation.negotiate("en;q=0.3, nl;q=0.9", supported, default)
        assertEquals("nl", locale)
    }

    @Test
    fun firstTokenImplicitQ1BeatsLaterExplicitLowerQ() {
        // en (implicit q=1.0), nl;q=0.9 -> English wins because implicit q=1.0
        val locale = AcceptLanguageNegotiation.negotiate("en, nl;q=0.9", supported, default)
        assertEquals("en", locale)
    }

    @Test
    fun qZeroRejectionExcludesLocale() {
        // en;q=0 explicitly rejects English; nl;q=0.5 wins
        val locale = AcceptLanguageNegotiation.negotiate("en;q=0, nl;q=0.5", supported, default)
        assertEquals("nl", locale)
    }

    @Test
    fun regionTagPrefixMatchesLanguage() {
        val locale = AcceptLanguageNegotiation.negotiate("nl-NL", supported, default)
        assertEquals("nl", locale)
    }

    @Test
    fun unknownLocaleFallsBackToDefault() {
        val locale = AcceptLanguageNegotiation.negotiate("de", supported, default)
        assertEquals("en", locale)
    }

    @Test
    fun emptyHeaderReturnsDefault() {
        val locale = AcceptLanguageNegotiation.negotiate(null, supported, default)
        assertEquals("en", locale)
        val locale2 = AcceptLanguageNegotiation.negotiate("", supported, default)
        assertEquals("en", locale2)
    }

    @Test
    fun mixedCaseAndExtraSpacesAreNormalised() {
        val locale = AcceptLanguageNegotiation.negotiate("  EN-US  ;  q=0.4  ,  NL ; q=0.8 ", supported, default)
        assertEquals("nl", locale)
    }

    @Test
    fun parseDropsZeroQuality() {
        val parsed = AcceptLanguageNegotiation.parse("en;q=0, nl;q=0.5, de;q=0.3")
        assertEquals(listOf("nl", "de"), parsed)
    }

    @Test
    fun parseSortsDescendingPreservingOrderOnTies() {
        val parsed = AcceptLanguageNegotiation.parse("fr;q=0.8, de;q=0.8, nl;q=0.9, en;q=0.7")
        assertEquals(listOf("nl", "fr", "de", "en"), parsed)
    }
}
