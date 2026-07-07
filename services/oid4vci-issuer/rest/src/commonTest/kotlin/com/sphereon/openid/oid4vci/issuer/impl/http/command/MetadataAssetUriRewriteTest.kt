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

package com.sphereon.openid.oid4vci.issuer.impl.http.command

import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vc.common.ImageProperties
import com.sphereon.openid.oid4vc.common.LogoProperties
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialMetadata
import com.sphereon.sdjwt.vc.DisplayInformation
import com.sphereon.sdjwt.vc.LogoMetadata
import com.sphereon.sdjwt.vc.RenderingMetadata
import com.sphereon.sdjwt.vc.SdJwtVcTypeMetadata
import com.sphereon.sdjwt.vc.SimpleRenderingMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Fix #5 serve-side rewrite: stored RELATIVE design-asset URIs in OID4VCI issuer metadata and in
 * SD-JWT VC type metadata are resolved to PER-TENANT absolute URLs using the same base the issuer
 * advertises for `credential_issuer` / endpoints.
 */
class MetadataAssetUriRewriteTest {
    private companion object {
        const val ACME_BASE = "https://acme.saas.localtest.me"
        const val RELATIVE_LOGO = "/public/assets/design/abc123.png"
        const val RELATIVE_BG = "/public/assets/design/def456.jpg"
    }

    @Test
    fun rewritesIssuerAndCredentialDisplayLogosToPerTenantAbsolute() {
        val metadata =
            CredentialIssuerMetadata(
                credentialIssuer = ACME_BASE,
                credentialEndpoint = "$ACME_BASE/oid4vci/credential",
                display =
                    listOf(
                        DisplayProperties(
                            name = "Acme",
                            locale = "en-US",
                            logo = LogoProperties(uri = RELATIVE_LOGO, altText = "Acme"),
                            backgroundImage = ImageProperties(uri = RELATIVE_BG),
                        ),
                    ),
                credentialConfigurationsSupported =
                    mapOf(
                        "EuPid" to
                            CredentialConfigurationSupported(
                                format = "dc+sd-jwt",
                                display = listOf(DisplayProperties(name = "EU PID", logo = LogoProperties(uri = RELATIVE_LOGO))),
                                credentialMetadata =
                                    CredentialMetadata(
                                        display = listOf(DisplayProperties(name = "EU PID", logo = LogoProperties(uri = RELATIVE_LOGO))),
                                    ),
                            ),
                    ),
            )

        val rewritten = metadata.withAbsoluteAssetUris(ACME_BASE)

        // Top-level issuer display (Fix #4 logo too).
        assertEquals(
            "$ACME_BASE$RELATIVE_LOGO",
            rewritten.display!!
                .single()
                .logo!!
                .uri
        )
        assertEquals(
            "$ACME_BASE$RELATIVE_BG",
            rewritten.display!!
                .single()
                .backgroundImage!!
                .uri
        )
        // altText preserved
        assertEquals(
            "Acme",
            rewritten.display!!
                .single()
                .logo!!
                .altText
        )

        // Credential-level top display + credential_metadata.display
        val cfg = rewritten.credentialConfigurationsSupported.getValue("EuPid")
        assertEquals(
            "$ACME_BASE$RELATIVE_LOGO",
            cfg.display!!
                .single()
                .logo!!
                .uri
        )
        assertEquals(
            "$ACME_BASE$RELATIVE_LOGO",
            cfg.credentialMetadata!!
                .display!!
                .single()
                .logo!!
                .uri
        )
    }

    @Test
    fun rewritesPublicAssetUrisToOriginWhenIssuerBaseCarriesPath() {
        val metadata =
            CredentialIssuerMetadata(
                credentialIssuer = "$ACME_BASE/oid4vci",
                credentialEndpoint = "$ACME_BASE/oid4vci/api/oid4vci/v1/credential",
                display =
                    listOf(
                        DisplayProperties(
                            name = "Acme",
                            logo = LogoProperties(uri = RELATIVE_LOGO),
                            backgroundImage = ImageProperties(uri = RELATIVE_BG),
                        ),
                    ),
                credentialConfigurationsSupported = emptyMap(),
            )

        val rewritten = metadata.withAbsoluteAssetUris("$ACME_BASE/oid4vci")

        assertEquals("$ACME_BASE$RELATIVE_LOGO", rewritten.display!!.single().logo!!.uri)
        assertEquals("$ACME_BASE$RELATIVE_BG", rewritten.display!!.single().backgroundImage!!.uri)
    }

    @Test
    fun issuerMetadataRewriteIsNoOpWithoutBase() {
        val metadata =
            CredentialIssuerMetadata(
                credentialIssuer = ACME_BASE,
                credentialEndpoint = "$ACME_BASE/oid4vci/credential",
                display = listOf(DisplayProperties(name = "Acme", logo = LogoProperties(uri = RELATIVE_LOGO))),
                credentialConfigurationsSupported = emptyMap(),
            )
        val rewritten = metadata.withAbsoluteAssetUris(null)
        assertEquals(
            RELATIVE_LOGO,
            rewritten.display!!
                .single()
                .logo!!
                .uri
        )
    }

    @Test
    fun rewritesHostedVctUrlsToPerTenantIssuerBase() {
        val metadata =
            CredentialIssuerMetadata(
                credentialIssuer = ACME_BASE,
                credentialEndpoint = "$ACME_BASE/oid4vci/credential",
                credentialConfigurationsSupported =
                    mapOf(
                        "EuPid" to
                            CredentialConfigurationSupported(
                                format = "dc+sd-jwt",
                                vct = "https://issuer.example.com/public/schema/vct/EuPid",
                            ),
                        "External" to
                            CredentialConfigurationSupported(
                                format = "dc+sd-jwt",
                                vct = "https://schemas.example.com/types/External",
                            ),
                    ),
            )

        val rewritten = metadata.withHostedVctUrls(ACME_BASE)

        assertEquals("$ACME_BASE/public/schema/vct/EuPid", rewritten.credentialConfigurationsSupported.getValue("EuPid").vct)
        assertEquals(
            "https://schemas.example.com/types/External",
            rewritten.credentialConfigurationsSupported.getValue("External").vct,
        )
    }

    @Test
    fun rewritesHostedVctUrlsToOriginWhenIssuerBaseCarriesPath() {
        val metadata =
            CredentialIssuerMetadata(
                credentialIssuer = "$ACME_BASE/oid4vci",
                credentialEndpoint = "$ACME_BASE/oid4vci/api/oid4vci/v1/credential",
                credentialConfigurationsSupported =
                    mapOf(
                        "EuPid" to
                            CredentialConfigurationSupported(
                                format = "dc+sd-jwt",
                                vct = "$ACME_BASE/oid4vci/public/schema/vct/EuPid",
                            ),
                    ),
            )

        val rewritten = metadata.withHostedVctUrls("$ACME_BASE/oid4vci")

        assertEquals("$ACME_BASE/public/schema/vct/EuPid", rewritten.credentialConfigurationsSupported.getValue("EuPid").vct)
    }

    @Test
    fun rewritesHostedVctMetadataSelfUrlToPerTenantIssuerBase() {
        val vct =
            SdJwtVcTypeMetadata(
                vct = "https://platform.example.com/public/schema/vct/EuPid",
                display = listOf(DisplayInformation(locale = "en-US", name = "EU PID")),
            )

        val rewritten = vct.withHostedVctUrl(ACME_BASE)

        assertEquals("$ACME_BASE/public/schema/vct/EuPid", rewritten.vct)
    }

    @Test
    fun rewritesVctRenderingLogoToPerTenantAbsolute() {
        val vct =
            SdJwtVcTypeMetadata(
                vct = "$ACME_BASE/public/schema/vct/EuPid",
                display =
                    listOf(
                        DisplayInformation(
                            locale = "en-US",
                            name = "EU PID",
                            rendering =
                                RenderingMetadata(
                                    simple =
                                        SimpleRenderingMethod(
                                            logo = LogoMetadata(uri = RELATIVE_LOGO, altText = "logo"),
                                            backgroundColor = "#102030",
                                        ),
                                ),
                        ),
                    ),
            )

        val rewritten = vct.withAbsoluteAssetUris(ACME_BASE)
        val simple =
            rewritten.display!!
                .single()
                .rendering!!
                .simple!!
        assertEquals("$ACME_BASE$RELATIVE_LOGO", simple.logo!!.uri)
        assertEquals("logo", simple.logo!!.altText)
        assertEquals("#102030", simple.backgroundColor)
    }

    @Test
    fun vctRewriteHandlesMissingRendering() {
        val vct =
            SdJwtVcTypeMetadata(
                vct = "$ACME_BASE/public/schema/vct/EuPid",
                display = listOf(DisplayInformation(locale = "en-US", name = "EU PID")),
            )
        val rewritten = vct.withAbsoluteAssetUris(ACME_BASE)
        assertNull(rewritten.display!!.single().rendering)
    }
}
