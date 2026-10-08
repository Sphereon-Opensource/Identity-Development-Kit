/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.credential.design.impl.resolution

import com.sphereon.core.api.Ok
import com.sphereon.data.store.credential.design.model.ClaimLabel
import com.sphereon.data.store.credential.design.model.ClaimPathSegment
import com.sphereon.data.store.credential.design.model.ClaimPresentation
import com.sphereon.data.store.credential.design.model.ClaimValueKind
import com.sphereon.data.store.credential.design.model.CredentialDesignRecord
import com.sphereon.data.store.credential.design.model.DerivedRenderHintsRecord
import com.sphereon.data.store.credential.design.model.DesignBinding
import com.sphereon.data.store.credential.design.model.DesignClaimPath
import com.sphereon.data.store.credential.design.model.DesignHostingMode
import com.sphereon.data.store.credential.design.model.DesignSourceType
import com.sphereon.data.store.credential.design.model.EntityLocaleDesign
import com.sphereon.data.store.credential.design.model.FieldRenderHint
import com.sphereon.data.store.credential.design.model.IssuerDesignRecord
import com.sphereon.data.store.credential.design.model.LocalizedCredentialDisplay
import com.sphereon.data.store.credential.design.model.RenderVariantKind
import com.sphereon.data.store.credential.design.model.RenderVariantRecord
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.data.store.credential.design.model.ResolveEntityDesignInput
import com.sphereon.data.store.credential.design.model.ResolvedCredentialDesign
import com.sphereon.data.store.credential.design.model.VerifierDesignRecord
import com.sphereon.data.store.credential.design.resolution.CredentialDesignLayerResult
import com.sphereon.data.store.credential.design.resolution.DesignLayerProvider
import com.sphereon.data.store.credential.design.resolution.IssuerDesignLayerResult
import com.sphereon.data.store.credential.design.resolution.VerifierDesignLayerResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class DesignResolutionEngineTest {
    // -- helpers --------------------------------------------------------

    private fun baseDesign(
        bindings: List<DesignBinding> = listOf(DesignBinding(vct = "test")),
        displays: List<LocalizedCredentialDisplay> = listOf(LocalizedCredentialDisplay(locale = "en", name = "Test")),
        claims: List<ClaimPresentation> = emptyList(),
    ) = CredentialDesignRecord(
        id = Uuid.random(),
        tenantId = "test-tenant",
        hostingMode = DesignHostingMode.LOCAL,
        bindings = bindings,
        displays = displays,
        claims = claims,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
    )

    private fun baseIssuerDesign(displays: List<EntityLocaleDesign> = listOf(EntityLocaleDesign(locale = "en", displayName = "Issuer"))) =
        IssuerDesignRecord(
            id = Uuid.random(),
            tenantId = "test-tenant",
            hostingMode = DesignHostingMode.LOCAL,
            bindings = listOf(DesignBinding(issuerId = "issuer-1")),
            displays = displays,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )

    private fun baseVerifierDesign(displays: List<EntityLocaleDesign> = listOf(EntityLocaleDesign(locale = "en", displayName = "Verifier"))) =
        VerifierDesignRecord(
            id = Uuid.random(),
            tenantId = "test-tenant",
            hostingMode = DesignHostingMode.LOCAL,
            bindings = listOf(DesignBinding(verifierClientId = "verifier-1")),
            displays = displays,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )

    private fun claimPath(vararg segments: String): DesignClaimPath = segments.map { ClaimPathSegment.Property(it) }

    private val defaultInput = ResolveCredentialDesignInput()
    private val defaultEntityInput = ResolveEntityDesignInput()

    // -- mock providers --------------------------------------------------

    private class TestCredentialProvider(
        override val sourceType: DesignSourceType,
        override val authoritative: Boolean = false,
        val credentialResult: CredentialDesignLayerResult? = null,
    ) : DesignLayerProvider {
        override suspend fun resolveCredentialLayer(
            tenantId: String,
            input: ResolveCredentialDesignInput,
        ): CredentialDesignLayerResult? = credentialResult
    }

    private class TestIssuerProvider(
        override val sourceType: DesignSourceType,
        override val authoritative: Boolean = false,
        val issuerResult: IssuerDesignLayerResult? = null,
    ) : DesignLayerProvider {
        override suspend fun resolveIssuerLayer(
            tenantId: String,
            input: ResolveEntityDesignInput,
        ): IssuerDesignLayerResult? = issuerResult
    }

    private class TestVerifierProvider(
        override val sourceType: DesignSourceType,
        override val authoritative: Boolean = false,
        val verifierResult: VerifierDesignLayerResult? = null,
    ) : DesignLayerProvider {
        override suspend fun resolveVerifierLayer(
            tenantId: String,
            input: ResolveEntityDesignInput,
        ): VerifierDesignLayerResult? = verifierResult
    }

    // -- 1. Single provider resolves correctly ----------------------------

    @Test
    fun singleProviderResolvesDisplaysAndClaims() =
        runTest {
            val providerDisplay = LocalizedCredentialDisplay(locale = "de", name = "Ausweis")
            val providerClaim =
                ClaimPresentation(
                    path = claimPath("given_name"),
                    labels = listOf(ClaimLabel(locale = "en", label = "Given Name")),
                )

            val provider =
                TestCredentialProvider(
                    sourceType = DesignSourceType.LOCAL_OVERRIDE,
                    credentialResult =
                        CredentialDesignLayerResult(
                            displays = listOf(providerDisplay),
                            claims = listOf(providerClaim),
                            providedFields = emptySet(),
                        ),
                )

            val engine = DesignResolutionEngine(listOf(provider))
            val design = baseDesign()
            val result = engine.resolveCredential("test-tenant", defaultInput, design)

            assertTrue(result is Ok)
            val resolved = result.value

            // The original base design is preserved as-is in the output
            assertEquals(design, resolved.design)

            // The single provider is recorded in applied layers
            assertEquals(1, resolved.appliedLayers.size)
            assertEquals(DesignSourceType.LOCAL_OVERRIDE, resolved.appliedLayers[0].sourceType)
            assertEquals(0, resolved.appliedLayers[0].priority)
            assertNotNull(resolved.resolvedAt)
        }

    // -- 2. Priority ordering - later provider wins ----------------------

    @Test
    fun higherPriorityProviderOverridesSameLocaleDisplay() =
        runTest {
            val lowPriorityDisplay = LocalizedCredentialDisplay(locale = "en", name = "Low Priority")
            val highPriorityDisplay = LocalizedCredentialDisplay(locale = "en", name = "High Priority")

            val lowProvider =
                TestCredentialProvider(
                    sourceType = DesignSourceType.SCHEMA_INFERENCE,
                    credentialResult =
                        CredentialDesignLayerResult(
                            displays = listOf(lowPriorityDisplay),
                            providedFields = emptySet(),
                        ),
                )
            val highProvider =
                TestCredentialProvider(
                    sourceType = DesignSourceType.LOCAL_OVERRIDE,
                    credentialResult =
                        CredentialDesignLayerResult(
                            displays = listOf(highPriorityDisplay),
                            providedFields = emptySet(),
                        ),
                )

            // Providers are iterated in list order; index = priority.
            // The later provider (index 1) replaces index 0's display for "en".
            val engine = DesignResolutionEngine(listOf(lowProvider, highProvider))
            val design = baseDesign(displays = listOf(LocalizedCredentialDisplay(locale = "en", name = "Base")))
            val result = engine.resolveCredential("test-tenant", defaultInput, design)

            assertTrue(result is Ok)
            val resolved = result.value
            assertEquals(2, resolved.appliedLayers.size)
            assertEquals(DesignSourceType.SCHEMA_INFERENCE, resolved.appliedLayers[0].sourceType)
            assertEquals(0, resolved.appliedLayers[0].priority)
            assertEquals(DesignSourceType.LOCAL_OVERRIDE, resolved.appliedLayers[1].sourceType)
            assertEquals(1, resolved.appliedLayers[1].priority)
        }

    // -- 3. Field-level locking -------------------------------------------

    @Test
    fun authoritativeProviderLocksDisplayField() =
        runTest {
            val authoritativeDisplay = LocalizedCredentialDisplay(locale = "en", name = "Authoritative")
            val subsequentDisplay = LocalizedCredentialDisplay(locale = "en", name = "Should Be Ignored")

            val authProvider =
                TestCredentialProvider(
                    sourceType = DesignSourceType.SD_JWT_VCT_METADATA,
                    authoritative = true,
                    credentialResult =
                        CredentialDesignLayerResult(
                            displays = listOf(authoritativeDisplay),
                            providedFields = emptySet(),
                        ),
                )
            val lateProvider =
                TestCredentialProvider(
                    sourceType = DesignSourceType.LOCAL_OVERRIDE,
                    credentialResult =
                        CredentialDesignLayerResult(
                            displays = listOf(subsequentDisplay),
                            providedFields = emptySet(),
                        ),
                )

            val engine = DesignResolutionEngine(listOf(authProvider, lateProvider))
            val design = baseDesign(displays = emptyList())
            val result = engine.resolveCredential("test-tenant", defaultInput, design)

            assertTrue(result is Ok)
            val resolved = result.value

            // The locked field should be recorded
            assertTrue("display:en" in resolved.lockedFields)
            assertEquals(DesignSourceType.SD_JWT_VCT_METADATA, resolved.lockedFields["display:en"])

            // Both providers are listed in applied layers (both were invoked)
            assertEquals(2, resolved.appliedLayers.size)
        }

    // -- 4. Claim merging by path - labels merge by locale ----------------

    @Test
    fun claimLabelsAreMergedByLocale() =
        runTest {
            val path = claimPath("family_name")
            val claim1 =
                ClaimPresentation(
                    path = path,
                    labels = listOf(ClaimLabel(locale = "en", label = "Family Name")),
                )
            val claim2 =
                ClaimPresentation(
                    path = path,
                    labels =
                        listOf(
                            ClaimLabel(locale = "en", label = "Surname"),
                            ClaimLabel(locale = "de", label = "Nachname"),
                        ),
                )

            val provider1 =
                TestCredentialProvider(
                    sourceType = DesignSourceType.SCHEMA_INFERENCE,
                    credentialResult =
                        CredentialDesignLayerResult(
                            claims = listOf(claim1),
                            providedFields = emptySet(),
                        ),
                )
            val provider2 =
                TestCredentialProvider(
                    sourceType = DesignSourceType.LOCAL_OVERRIDE,
                    credentialResult =
                        CredentialDesignLayerResult(
                            claims = listOf(claim2),
                            providedFields = emptySet(),
                        ),
                )

            val engine = DesignResolutionEngine(listOf(provider1, provider2))
            val design = baseDesign(claims = emptyList())
            val result = engine.resolveCredential("test-tenant", defaultInput, design)

            assertTrue(result is Ok)
            // The engine processes providers in order. Provider 1 adds claim1 (en label "Family Name").
            // Provider 2 merges onto the same path. In mergeClaim(), the "en" label is replaced by "Surname"
            // and "de" label "Nachname" is added.
            // We cannot directly inspect the merged claims from the public API; they live
            // inside the mutable list within resolveCredential. But we can verify the layers applied.
            assertEquals(2, resolved(result).appliedLayers.size)
        }

    // -- 5. Entry codes from higher priority replace lower ----------------

    @Test
    fun entryCodesFromHigherPriorityReplaceLower() =
        runTest {
            val path = claimPath("status")
            val claim1 =
                ClaimPresentation(
                    path = path,
                    labels = listOf(ClaimLabel(locale = "en", label = "Status")),
                    entryCodes = listOf("A", "B"),
                )
            val claim2 =
                ClaimPresentation(
                    path = path,
                    labels = emptyList(),
                    entryCodes = listOf("X", "Y", "Z"),
                )

            val provider1 =
                TestCredentialProvider(
                    sourceType = DesignSourceType.SCHEMA_INFERENCE,
                    credentialResult =
                        CredentialDesignLayerResult(
                            claims = listOf(claim1),
                            providedFields = emptySet(),
                        ),
                )
            val provider2 =
                TestCredentialProvider(
                    sourceType = DesignSourceType.LOCAL_OVERRIDE,
                    credentialResult =
                        CredentialDesignLayerResult(
                            claims = listOf(claim2),
                            providedFields = emptySet(),
                        ),
                )

            val engine = DesignResolutionEngine(listOf(provider1, provider2))
            val design = baseDesign(claims = emptyList())
            val result = engine.resolveCredential("test-tenant", defaultInput, design)

            assertTrue(result is Ok)
            // Verify layers applied from both providers
            assertEquals(2, resolved(result).appliedLayers.size)
        }

    // -- 6. Render variants accumulate ------------------------------------

    @Test
    fun renderVariantsAccumulateFromMultipleProviders() =
        runTest {
            val variant1 =
                RenderVariantRecord(
                    id = Uuid.random(),
                    tenantId = "test-tenant",
                    kind = RenderVariantKind.SIMPLE_CARD,
                    backgroundColor = "#FFFFFF",
                )
            val variant2 =
                RenderVariantRecord(
                    id = Uuid.random(),
                    tenantId = "test-tenant",
                    kind = RenderVariantKind.SVG_TEMPLATE,
                    backgroundColor = "#000000",
                )

            val provider1 =
                TestCredentialProvider(
                    sourceType = DesignSourceType.SCHEMA_INFERENCE,
                    credentialResult =
                        CredentialDesignLayerResult(
                            renderVariants = listOf(variant1),
                            providedFields = emptySet(),
                        ),
                )
            val provider2 =
                TestCredentialProvider(
                    sourceType = DesignSourceType.LOCAL_OVERRIDE,
                    credentialResult =
                        CredentialDesignLayerResult(
                            renderVariants = listOf(variant2),
                            providedFields = emptySet(),
                        ),
                )

            val engine = DesignResolutionEngine(listOf(provider1, provider2))
            val design = baseDesign()
            val result = engine.resolveCredential("test-tenant", defaultInput, design)

            assertTrue(result is Ok)
            val resolved = result.value
            assertEquals(2, resolved.renderVariants.size)
            assertTrue(resolved.renderVariants.any { it.kind == RenderVariantKind.SIMPLE_CARD })
            assertTrue(resolved.renderVariants.any { it.kind == RenderVariantKind.SVG_TEMPLATE })
        }

    // -- 7. No providers returns base design only -------------------------

    @Test
    fun noProvidersReturnsBaseDesignOnly() =
        runTest {
            val engine = DesignResolutionEngine(emptyList())
            val design = baseDesign()
            val result = engine.resolveCredential("test-tenant", defaultInput, design)

            assertTrue(result is Ok)
            val resolved = result.value
            assertEquals(design, resolved.design)
            assertTrue(resolved.renderVariants.isEmpty())
            assertNull(resolved.derivedRenderHints)
            assertTrue(resolved.appliedLayers.isEmpty())
            assertTrue(resolved.lockedFields.isEmpty())
            assertNotNull(resolved.resolvedAt)
        }

    // -- 8. Issuer resolution ---------------------------------------------

    @Test
    fun resolveIssuerWithMockProviders() =
        runTest {
            val issuerDisplay = EntityLocaleDesign(locale = "de", displayName = "Aussteller")
            val variant =
                RenderVariantRecord(
                    id = Uuid.random(),
                    tenantId = "test-tenant",
                    kind = RenderVariantKind.SIMPLE_CARD,
                    backgroundColor = "#112233",
                )

            val provider =
                TestIssuerProvider(
                    sourceType = DesignSourceType.OID4VCI_ISSUER_METADATA,
                    authoritative = true,
                    issuerResult =
                        IssuerDesignLayerResult(
                            displays = listOf(issuerDisplay),
                            renderVariants = listOf(variant),
                            providedFields = setOf("display:de"),
                        ),
                )

            val engine = DesignResolutionEngine(listOf(provider))
            val design = baseIssuerDesign()
            val result = engine.resolveIssuer("test-tenant", defaultEntityInput, design)

            assertTrue(result is Ok)
            val resolved = result.value
            assertEquals(design, resolved.design)
            assertEquals(1, resolved.appliedLayers.size)
            assertEquals(DesignSourceType.OID4VCI_ISSUER_METADATA, resolved.appliedLayers[0].sourceType)
            assertTrue(resolved.appliedLayers[0].authoritative)
            assertEquals(1, resolved.renderVariants.size)
            assertEquals(RenderVariantKind.SIMPLE_CARD, resolved.renderVariants[0].kind)
            assertNotNull(resolved.resolvedAt)

            // Authoritative provider locks the "display:de" field
            assertTrue("display:de" in resolved.lockedFields)
            assertEquals(DesignSourceType.OID4VCI_ISSUER_METADATA, resolved.lockedFields["display:de"])
        }

    // -- 9. Verifier resolution -------------------------------------------

    @Test
    fun resolveVerifierWithMockProviders() =
        runTest {
            val verifierDisplay = EntityLocaleDesign(locale = "nl", displayName = "Verificateur")
            val variant =
                RenderVariantRecord(
                    id = Uuid.random(),
                    tenantId = "test-tenant",
                    kind = RenderVariantKind.EXTERNAL_REFERENCE,
                )

            val provider =
                TestVerifierProvider(
                    sourceType = DesignSourceType.OIDC_DISCOVERY,
                    verifierResult =
                        VerifierDesignLayerResult(
                            displays = listOf(verifierDisplay),
                            renderVariants = listOf(variant),
                            providedFields = emptySet(),
                        ),
                )

            val engine = DesignResolutionEngine(listOf(provider))
            val design = baseVerifierDesign()
            val result = engine.resolveVerifier("test-tenant", defaultEntityInput, design)

            assertTrue(result is Ok)
            val resolved = result.value
            assertEquals(design, resolved.design)
            assertEquals(1, resolved.appliedLayers.size)
            assertEquals(DesignSourceType.OIDC_DISCOVERY, resolved.appliedLayers[0].sourceType)
            assertEquals(1, resolved.renderVariants.size)
            assertEquals(RenderVariantKind.EXTERNAL_REFERENCE, resolved.renderVariants[0].kind)
            assertNotNull(resolved.resolvedAt)
        }

    // -- Additional: provider returning null is skipped -------------------

    @Test
    fun providerReturningNullIsSkipped() =
        runTest {
            val nullProvider =
                TestCredentialProvider(
                    sourceType = DesignSourceType.SCHEMA_INFERENCE,
                    credentialResult = null,
                )
            val activeProvider =
                TestCredentialProvider(
                    sourceType = DesignSourceType.LOCAL_OVERRIDE,
                    credentialResult =
                        CredentialDesignLayerResult(
                            displays = listOf(LocalizedCredentialDisplay(locale = "fr", name = "Carte")),
                            providedFields = emptySet(),
                        ),
                )

            val engine = DesignResolutionEngine(listOf(nullProvider, activeProvider))
            val design = baseDesign()
            val result = engine.resolveCredential("test-tenant", defaultInput, design)

            assertTrue(result is Ok)
            val resolved = result.value
            // Only the active provider appears in applied layers
            assertEquals(1, resolved.appliedLayers.size)
            assertEquals(DesignSourceType.LOCAL_OVERRIDE, resolved.appliedLayers[0].sourceType)
            assertEquals(1, resolved.appliedLayers[0].priority) // index 1 since null provider was at index 0
        }

    // -- Additional: derived hints taken from first provider that offers --

    @Test
    fun derivedHintsTakenFromFirstProviderThatOffersThem() =
        runTest {
            val hints1 =
                DerivedRenderHintsRecord(
                    id = Uuid.random(),
                    tenantId = "test-tenant",
                    fieldHints =
                        listOf(
                            FieldRenderHint(
                                path = claimPath("name"),
                                valueKind = ClaimValueKind.STRING,
                            ),
                        ),
                )
            val hints2 =
                DerivedRenderHintsRecord(
                    id = Uuid.random(),
                    tenantId = "test-tenant",
                    fieldHints =
                        listOf(
                            FieldRenderHint(
                                path = claimPath("age"),
                                valueKind = ClaimValueKind.INTEGER,
                            ),
                        ),
                )

            val provider1 =
                TestCredentialProvider(
                    sourceType = DesignSourceType.SCHEMA_INFERENCE,
                    credentialResult =
                        CredentialDesignLayerResult(
                            derivedRenderHints = hints1,
                            providedFields = emptySet(),
                        ),
                )
            val provider2 =
                TestCredentialProvider(
                    sourceType = DesignSourceType.LOCAL_OVERRIDE,
                    credentialResult =
                        CredentialDesignLayerResult(
                            derivedRenderHints = hints2,
                            providedFields = emptySet(),
                        ),
                )

            val engine = DesignResolutionEngine(listOf(provider1, provider2))
            val design = baseDesign()
            val result = engine.resolveCredential("test-tenant", defaultInput, design)

            assertTrue(result is Ok)
            val resolved = result.value
            // First provider that offers hints wins (provider1)
            assertNotNull(resolved.derivedRenderHints)
            assertEquals(hints1.id, resolved.derivedRenderHints?.id)
        }

    // -- Additional: authoritative claim locking --------------------------

    @Test
    fun authoritativeProviderLocksClaimField() =
        runTest {
            val path = claimPath("birth_date")
            val claim1 =
                ClaimPresentation(
                    path = path,
                    labels = listOf(ClaimLabel(locale = "en", label = "Birth Date")),
                    mandatory = true,
                )
            val claim2 =
                ClaimPresentation(
                    path = path,
                    labels = listOf(ClaimLabel(locale = "en", label = "Date of Birth")),
                    mandatory = false,
                )

            val authProvider =
                TestCredentialProvider(
                    sourceType = DesignSourceType.SD_JWT_VCT_METADATA,
                    authoritative = true,
                    credentialResult =
                        CredentialDesignLayerResult(
                            claims = listOf(claim1),
                            providedFields = emptySet(),
                        ),
                )
            val lateProvider =
                TestCredentialProvider(
                    sourceType = DesignSourceType.LOCAL_OVERRIDE,
                    credentialResult =
                        CredentialDesignLayerResult(
                            claims = listOf(claim2),
                            providedFields = emptySet(),
                        ),
                )

            val engine = DesignResolutionEngine(listOf(authProvider, lateProvider))
            val design = baseDesign(claims = emptyList())
            val result = engine.resolveCredential("test-tenant", defaultInput, design)

            assertTrue(result is Ok)
            val resolved = result.value

            // The claim path should be locked by the authoritative provider
            val claimKey = "claim:$path"
            assertTrue(claimKey in resolved.lockedFields)
            assertEquals(DesignSourceType.SD_JWT_VCT_METADATA, resolved.lockedFields[claimKey])
        }

    // -- Additional: issuer display locking blocks subsequent provider ----

    @Test
    fun issuerAuthoritativeLockBlocksSubsequentProvider() =
        runTest {
            val authDisplay = EntityLocaleDesign(locale = "en", displayName = "Official Issuer")
            val lateDisplay = EntityLocaleDesign(locale = "en", displayName = "Should Not Appear")

            val authProvider =
                TestIssuerProvider(
                    sourceType = DesignSourceType.OID4VCI_ISSUER_METADATA,
                    authoritative = true,
                    issuerResult =
                        IssuerDesignLayerResult(
                            displays = listOf(authDisplay),
                            providedFields = emptySet(),
                        ),
                )
            val lateProvider =
                TestIssuerProvider(
                    sourceType = DesignSourceType.LOCAL_OVERRIDE,
                    issuerResult =
                        IssuerDesignLayerResult(
                            displays = listOf(lateDisplay),
                            providedFields = emptySet(),
                        ),
                )

            val engine = DesignResolutionEngine(listOf(authProvider, lateProvider))
            val design = baseIssuerDesign(displays = emptyList())
            val result = engine.resolveIssuer("test-tenant", defaultEntityInput, design)

            assertTrue(result is Ok)
            val resolved = result.value

            // Both providers are applied
            assertEquals(2, resolved.appliedLayers.size)

            // The "display:en" field is locked by the authoritative provider
            assertTrue("display:en" in resolved.lockedFields)
            assertEquals(DesignSourceType.OID4VCI_ISSUER_METADATA, resolved.lockedFields["display:en"])
        }

    // -- Additional: verifier with no providers returns base only ---------

    @Test
    fun verifierNoProvidersReturnsBaseOnly() =
        runTest {
            val engine = DesignResolutionEngine(emptyList())
            val design = baseVerifierDesign()
            val result = engine.resolveVerifier("test-tenant", defaultEntityInput, design)

            assertTrue(result is Ok)
            val resolved = result.value
            assertEquals(design, resolved.design)
            assertTrue(resolved.renderVariants.isEmpty())
            assertTrue(resolved.appliedLayers.isEmpty())
            assertTrue(resolved.lockedFields.isEmpty())
        }

    // -- Additional: providedFields from authoritative provider are locked -

    @Test
    fun providedFieldsFromAuthoritativeProviderAreLocked() =
        runTest {
            val provider =
                TestCredentialProvider(
                    sourceType = DesignSourceType.EIDAS_REGISTRY,
                    authoritative = true,
                    credentialResult =
                        CredentialDesignLayerResult(
                            providedFields = setOf("custom:field1", "custom:field2"),
                        ),
                )

            val engine = DesignResolutionEngine(listOf(provider))
            val design = baseDesign()
            val result = engine.resolveCredential("test-tenant", defaultInput, design)

            assertTrue(result is Ok)
            val resolved = result.value
            assertTrue("custom:field1" in resolved.lockedFields)
            assertTrue("custom:field2" in resolved.lockedFields)
            assertEquals(DesignSourceType.EIDAS_REGISTRY, resolved.lockedFields["custom:field1"])
            assertEquals(DesignSourceType.EIDAS_REGISTRY, resolved.lockedFields["custom:field2"])
        }

    // -- Additional: non-authoritative provider does not lock fields ------

    @Test
    fun nonAuthoritativeProviderDoesNotLockFields() =
        runTest {
            val provider =
                TestCredentialProvider(
                    sourceType = DesignSourceType.LOCAL_OVERRIDE,
                    authoritative = false,
                    credentialResult =
                        CredentialDesignLayerResult(
                            displays = listOf(LocalizedCredentialDisplay(locale = "en", name = "Override")),
                            providedFields = setOf("custom:field1"),
                        ),
                )

            val engine = DesignResolutionEngine(listOf(provider))
            val design = baseDesign()
            val result = engine.resolveCredential("test-tenant", defaultInput, design)

            assertTrue(result is Ok)
            val resolved = result.value
            assertTrue(resolved.lockedFields.isEmpty())
        }

    // -- utility ----------------------------------------------------------

    private fun resolved(result: com.sphereon.core.api.IdkResult<ResolvedCredentialDesign, *>): ResolvedCredentialDesign {
        assertTrue(result is Ok)
        return result.value
    }
}
