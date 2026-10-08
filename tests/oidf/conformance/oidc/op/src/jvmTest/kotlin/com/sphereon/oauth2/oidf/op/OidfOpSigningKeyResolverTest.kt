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

package com.sphereon.oauth2.oidf.op

import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.di.context.PrincipalType
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.signing.AsSigningKeyPublicJwkResolver
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OidfOpSigningKeyResolverTest {
    @Test
    fun registeredSigningKeyResolvesOnlyPublicMaterialWithItsRegisteredKid() =
        runTest {
            val fixture = OidfOpServerFixture()
            try {
                val signingKey =
                    assertNotNull(
                        (fixture.graph as OidfOpSigningKeyStoreGraph)
                            .signingKeyStore
                            .getActive("default")
                            .getOrNull(),
                    )
                val context =
                    fixture.graph.userContextManager.createOrGetFromInputs(
                        DefaultTenantInputString("default"),
                        DefaultPrincipalInputString("anonymous"),
                        makeActive = false,
                    )
                val session =
                    context.sessionContextManager.createOrGetFromId(
                        "public-signing-material",
                        principalType = PrincipalType.USER,
                    )
                try {
                    val publicJwk =
                        assertNotNull(
                            (session.graph as OidfOpSigningMaterialGraph)
                                .signingKeyPublicJwkResolver
                                .resolve(signingKey),
                        )
                    assertEquals(signingKey.kid, publicJwk.kid)
                    assertNull(publicJwk.d)
                    assertNull(publicJwk.p)
                    assertNull(publicJwk.q)
                    assertNull(publicJwk.dP)
                    assertNull(publicJwk.dQ)
                    assertNull(publicJwk.qInv)
                    assertNull(publicJwk.k)
                } finally {
                    session.destroy()
                }
            } finally {
                fixture.stop()
            }
        }

    @Test
    fun foreignTenantAndMissingRegisteredKeyFailClosed() =
        runTest {
            val fixture = OidfOpServerFixture()
            try {
                val signingKey =
                    assertNotNull(
                        (fixture.graph as OidfOpSigningKeyStoreGraph)
                            .signingKeyStore
                            .getActive("default")
                            .getOrNull(),
                    )
                val context =
                    fixture.graph.userContextManager.createOrGetFromInputs(
                        DefaultTenantInputString("default"),
                        DefaultPrincipalInputString("anonymous"),
                        makeActive = false,
                    )
                val session =
                    context.sessionContextManager.createOrGetFromId(
                        "rejected-signing-material",
                        principalType = PrincipalType.USER,
                    )
                try {
                    val resolver = (session.graph as OidfOpSigningMaterialGraph).signingKeyPublicJwkResolver
                    assertNull(resolver.resolve(signingKey.copy(tenantId = "foreign-tenant")))
                    assertNull(
                        resolver.resolve(
                            signingKey.copy(
                                keyInfo =
                                    signingKey.keyInfo.copy(
                                        alias = "missing-conformance-signing-key",
                                        kid = "missing-conformance-signing-key",
                                    ),
                            ),
                        ),
                    )
                } finally {
                    session.destroy()
                }
            } finally {
                fixture.stop()
            }
        }
}

@ContributesTo(SessionScope::class)
internal interface OidfOpSigningMaterialGraph {
    val signingKeyPublicJwkResolver: AsSigningKeyPublicJwkResolver
}
