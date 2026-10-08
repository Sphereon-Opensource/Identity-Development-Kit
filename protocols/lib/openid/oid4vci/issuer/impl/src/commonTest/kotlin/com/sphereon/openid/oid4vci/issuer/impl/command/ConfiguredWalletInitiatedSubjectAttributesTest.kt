/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.DefaultInterpolationPolicyProvider
import com.sphereon.core.api.conf.DefaultPropertyInterpolator
import com.sphereon.core.api.conf.DefaultPropertySources
import com.sphereon.core.api.conf.InterpolatingPropertySourcesPropertyResolver
import com.sphereon.core.api.conf.InterpolationPolicy
import com.sphereon.core.api.conf.MapPropertySource
import com.sphereon.core.api.conf.PropertySourcesPropertyResolver
import com.sphereon.core.api.conf.ScopedPropertySourceWrapper
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfiguredWalletInitiatedSubjectAttributesTest {
    @Test
    fun resolvesOnlyTheExactSubjectAndCredentialConfiguration() {
        val result =
            resolveConfiguredWalletInitiatedSubjectAttributes(
                propertyResolver =
                    resolver(
                        """
                        {
                          "testuser": {
                            "org.iso.18013.5.1.mDL": {
                              "family_name": "Mustermann",
                              "age_over_18": true,
                              "portrait": [1, 2, 3]
                            },
                            "eu.europa.ec.eudi.pid.1": {
                              "family_name": "Different"
                            }
                          },
                          "other": {
                            "org.iso.18013.5.1.mDL": {
                              "family_name": "Wrong subject"
                            }
                          }
                        }
                        """.trimIndent(),
                    ),
                subject = "testuser",
                credentialConfigurationId = "org.iso.18013.5.1.mDL",
            )

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("Mustermann"), result.value["family_name"])
        assertEquals(JsonPrimitive(true), result.value["age_over_18"])
        assertEquals(
            JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2), JsonPrimitive(3))),
            result.value["portrait"],
        )
        assertEquals(3, result.value.size)
    }

    @Test
    fun absentSubjectOrCredentialConfigurationContributesNothing() {
        val resolver =
            resolver(
                """
                {
                  "testuser": {
                    "org.iso.18013.5.1.mDL": { "family_name": "Mustermann" }
                  }
                }
                """.trimIndent(),
            )

        val absentSubject =
            resolveConfiguredWalletInitiatedSubjectAttributes(
                resolver,
                subject = "unknown",
                credentialConfigurationId = "org.iso.18013.5.1.mDL",
            )
        val absentConfiguration =
            resolveConfiguredWalletInitiatedSubjectAttributes(
                resolver,
                subject = "testuser",
                credentialConfigurationId = "unknown",
            )

        assertTrue(absentSubject.isOk)
        assertTrue(absentSubject.value.isEmpty())
        assertTrue(absentConfiguration.isOk)
        assertTrue(absentConfiguration.value.isEmpty())
    }

    @Test
    fun malformedRegistryFailsClosed() {
        val malformedJson =
            resolveConfiguredWalletInitiatedSubjectAttributes(
                resolver("not-json"),
                subject = "testuser",
                credentialConfigurationId = "org.iso.18013.5.1.mDL",
            )
        val malformedEntry =
            resolveConfiguredWalletInitiatedSubjectAttributes(
                resolver("""{ "testuser": { "org.iso.18013.5.1.mDL": "not-an-object" } }"""),
                subject = "testuser",
                credentialConfigurationId = "org.iso.18013.5.1.mDL",
            )

        assertTrue(malformedJson.isErr)
        assertEquals("invalid_credential_configuration", malformedJson.error.code)
        assertTrue(malformedEntry.isErr)
        assertEquals("invalid_credential_configuration", malformedEntry.error.code)
    }

    @Test
    fun resolvesAnInterpolatedJsonRegistry() {
        val sources =
            DefaultPropertySources().apply {
                add(
                    ScopedPropertySourceWrapper(
                        delegateSource =
                            MapPropertySource(
                                name = "test",
                                source =
                                    mapOf(
                                        WALLET_INITIATED_SUBJECT_ATTRIBUTES_PROPERTY to "\${wallet.registry}",
                                        "wallet.registry" to """{ "testuser": { "Mdl": { "family_name": "Mustermann" } } }""",
                                    ),
                            ),
                        configLevel = ConfigLevel.APP,
                    ),
                )
            }
        val result =
            resolveConfiguredWalletInitiatedSubjectAttributes(
                propertyResolver =
                    InterpolatingPropertySourcesPropertyResolver(
                        sources,
                        DefaultPropertyInterpolator(),
                        resolverLevel = ConfigLevel.APP,
                        interpolationPolicyProvider =
                            DefaultInterpolationPolicyProvider(
                                mapOf(
                                    WALLET_INITIATED_SUBJECT_ATTRIBUTES_PROPERTY to
                                        InterpolationPolicy.PROPERTY_REFERENCES_ONLY,
                                ),
                            ),
                    ),
                subject = "testuser",
                credentialConfigurationId = "Mdl",
            )

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("Mustermann"), result.value["family_name"])
    }

    @Test
    fun resolvesTheRoutedIssuerInstanceRegistryWithoutFallingBackToSingularConfig() {
        val instanceProperty = walletInitiatedSubjectAttributesProperty("acme")
        val sources =
            DefaultPropertySources().apply {
                add(
                    MapPropertySource(
                        name = "test",
                        source =
                            mapOf(
                                WALLET_INITIATED_SUBJECT_ATTRIBUTES_PROPERTY to
                                    """{ "subject-1": { "Mdl": { "family_name": "Wrong singular value" } } }""",
                                instanceProperty to
                                    """{ "subject-1": { "Mdl": { "family_name": "Mustermann" } } }""",
                            ),
                    ),
                )
            }

        val result =
            resolveConfiguredWalletInitiatedSubjectAttributes(
                propertyResolver = PropertySourcesPropertyResolver(sources),
                subject = "subject-1",
                credentialConfigurationId = "Mdl",
                issuerInstanceId = "acme",
            )

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("Mustermann"), result.value["family_name"])
        assertEquals(
            "oid4vci.issuers.acme.walletInitiated.subjectAttributesJson",
            instanceProperty,
        )
    }

    private fun resolver(json: String): PropertySourcesPropertyResolver {
        val source =
            MapPropertySource(
                name = "test",
                source = mapOf(WALLET_INITIATED_SUBJECT_ATTRIBUTES_PROPERTY to json),
            )
        return PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
    }
}
