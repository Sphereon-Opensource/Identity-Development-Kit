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

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vc.common.ImageProperties
import com.sphereon.openid.oid4vc.common.LogoProperties
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.issuer.command.BuildIssuerMetadataArgs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildIssuerMetadataCommandImplTest {
    @Test
    fun issuerMetadataDisplayOmitsCredentialDisplayExtensions() =
        runTest {
            val display =
                DisplayProperties(
                    name = "Acme Issuer",
                    locale = "en",
                    logo = LogoProperties(uri = "https://issuer.example/logo.svg", altText = "Acme"),
                    description = "Rich console description",
                    backgroundColor = "#0B5FFF",
                    backgroundImage = ImageProperties(uri = "https://issuer.example/background.svg"),
                    textColor = "#FFFFFF",
                )
            val result =
                BuildIssuerMetadataCommandImpl(TestSessionExecution()).execute(
                    BuildIssuerMetadataArgs(
                        issuerIdentifier = "https://issuer.example",
                        baseUrl = "https://issuer.example",
                        credentialConfigurations =
                            mapOf("EuPid" to CredentialConfigurationSupported(format = "dc+sd-jwt")),
                        display = listOf(display),
                    ),
                )

            assertTrue(result.isOk, "metadata build failed: ${if (result.isErr) result.error else null}")
            val emitted = result.value.display!!.single()
            assertEquals(display.name, emitted.name)
            assertEquals(display.locale, emitted.locale)
            assertEquals(display.logo, emitted.logo)
            assertNull(emitted.description)
            assertNull(emitted.backgroundColor)
            assertNull(emitted.backgroundImage)
            assertNull(emitted.textColor)
        }
}
