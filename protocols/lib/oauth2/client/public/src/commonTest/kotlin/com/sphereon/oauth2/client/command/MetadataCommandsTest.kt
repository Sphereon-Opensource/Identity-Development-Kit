/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.oauth2.client.command

import kotlin.test.Test
import kotlin.test.assertEquals

class MetadataCommandsTest {
    @Test
    fun authorizationServerMetadataDiscoveryUrlsUseRfc8414PathForIssuerWithPath() {
        val urls = authorizationServerMetadataDiscoveryUrls("https://issuer.example.com:8443/test/a/as/")

        assertEquals(
            listOf(
                "https://issuer.example.com:8443/.well-known/oauth-authorization-server/test/a/as",
                "https://issuer.example.com:8443/test/a/as/.well-known/oauth-authorization-server",
                "https://issuer.example.com:8443/test/a/as/.well-known/openid-configuration",
            ),
            urls,
        )
    }

    @Test
    fun authorizationServerMetadataDiscoveryUrlsAvoidDuplicateLegacyUrlForRootIssuer() {
        val urls = authorizationServerMetadataDiscoveryUrls("https://issuer.example.com")

        assertEquals(
            listOf(
                "https://issuer.example.com/.well-known/oauth-authorization-server",
                "https://issuer.example.com/.well-known/openid-configuration",
            ),
            urls,
        )
    }

    @Test
    fun authorizationServerMetadataDiscoveryUrlsCanPreferOidcDiscovery() {
        val urls =
            authorizationServerMetadataDiscoveryUrls(
                issuer = "https://issuer.example.com/tenant",
                discoveryMode = DiscoveryMode.OIDC_FIRST,
            )

        assertEquals(
            listOf(
                "https://issuer.example.com/tenant/.well-known/openid-configuration",
                "https://issuer.example.com/.well-known/oauth-authorization-server/tenant",
                "https://issuer.example.com/tenant/.well-known/oauth-authorization-server",
            ),
            urls,
        )
    }
}
