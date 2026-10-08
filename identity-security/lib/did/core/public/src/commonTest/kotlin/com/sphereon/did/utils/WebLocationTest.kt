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
 *
 */

package com.sphereon.did.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebLocationTest {
    @Test
    fun webAndWebvhShareTheSameLocationForTheSameHostAndPath() {
        val web = WebLocation.fromDid("web", "did:web:example.com:tenants:acme")
        val webvh = WebLocation.fromDid("webvh", "did:webvh:QmScid123:example.com:tenants:acme")
        assertEquals("example.com:tenants:acme", web)
        assertEquals(web, webvh, "did:web and did:webvh must resolve to the identical web location")
    }

    @Test
    fun wellKnownLocationHasNoPath() {
        assertEquals("example.com", WebLocation.fromDid("web", "did:web:example.com"))
        assertEquals("example.com", WebLocation.fromDid("webvh", "did:webvh:QmScid:example.com"))
    }

    @Test
    fun hostIsLowercasedButPortAndPathArePreserved() {
        assertEquals("example.com%3A3000:Path:CaseSensitive", WebLocation.fromDid("web", "did:web:Example.COM%3A3000:Path:CaseSensitive"))
    }

    @Test
    fun nonWebMethodsHaveNoWebLocation() {
        assertNull(WebLocation.fromDid("key", "did:key:z6Mk"))
        assertNull(WebLocation.fromDid("jwk", "did:jwk:eyJ"))
    }

    @Test
    fun malformedIdentifiersReturnNull() {
        assertNull(WebLocation.fromDid("web", "did:key:z6Mk"))
        // did:webvh with only an SCID and no host yields no location.
        assertNull(WebLocation.fromDid("webvh", "did:webvh:QmScidOnly"))
    }

    @Test
    fun fromRequestMatchesStoredLocation() {
        assertEquals(
            WebLocation.fromDid("web", "did:web:example.com:tenants:acme"),
            WebLocation.fromRequest(host = "example.com", pathSegments = listOf("tenants", "acme")),
        )
        assertEquals(
            WebLocation.fromDid("web", "did:web:example.com"),
            WebLocation.fromRequest(host = "EXAMPLE.com"),
        )
        assertEquals(
            WebLocation.fromDid("web", "did:web:example.com%3A3000:a"),
            WebLocation.fromRequest(host = "example.com", port = 3000, pathSegments = listOf("a")),
        )
    }

    @Test
    fun fromRequestMatchesWebvhCompanionLocation() {
        // A wallet hitting example.com/tenants/acme/did.json must resolve the location the
        // stored did:webvh record carries — proving web and webvh share the read key.
        val requestLocation = WebLocation.fromRequest(host = "example.com", pathSegments = listOf("tenants", "acme"))
        val webvhLocation = WebLocation.fromDid("webvh", "did:webvh:QmScid:example.com:tenants:acme")
        assertEquals(webvhLocation, requestLocation)
    }
}
