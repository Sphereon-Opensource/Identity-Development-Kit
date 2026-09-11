/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.model.Origin
import com.sphereon.core.api.session.CommandLifecycleInterceptorChain
import com.sphereon.core.api.session.EmptyInterceptorChain
import com.sphereon.crypto.certificate.persistence.CertificateReferenceHistoryCapability
import com.sphereon.crypto.certificate.persistence.CertificateReferenceKind
import com.sphereon.crypto.certificate.persistence.CertificateReferenceRecord
import com.sphereon.crypto.certificate.persistence.CertificateReferenceSource
import com.sphereon.crypto.certificate.persistence.CertificateReferenceStore
import com.sphereon.crypto.certificate.persistence.CertificateReferenceStoreErrorCodes
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ManagedKeyReferenceFilter
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.core.interop.derPublicKeyToJwk
import com.sphereon.crypto.core.kms.CertificateService
import com.sphereon.crypto.core.kms.CertificateStoreService
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyStoreService
import com.sphereon.crypto.core.kms.ProviderCertificateReference
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.crypto.kms.rest.api.command.RegisterCertificateReferenceInput
import com.sphereon.crypto.kms.rest.api.generated.infrastructure.Base64ByteArray
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.UserContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.spec.ECGenParameterSpec
import java.util.Date
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class CertificateReferenceLifecycleTest {
    @Test
    fun storedPublicChainRegistersReadsListsAndRepeatedlyDeletesWithoutProviderMutation() =
        runTest {
            val fixture = Fixture()
            val keyPair = generateKeyPair("EC")
            val leaf = certificateFor(keyPair, "stored-ec")
            fixture.addLinkedKey("stored-key", keyPair)

            val registered = fixture.service.registerCertificateReference(
                storedChainInput(alias = "stored-chain", linkedKeyAlias = "stored-key", leaf = leaf),
            )

            assertEquals(Origin.EXTERNAL, registered.origin)
            assertEquals(ResourceControlMode.EXTERNALLY_MANAGED, registered.controlMode)
            assertContentEquals(leaf.der, fixture.service.getCertificateChain("stored-chain", PROVIDER_ID).certificates.single().value)
            assertEquals(listOf("stored-chain"), fixture.service.listCertificateChainAliases(PROVIDER_ID).aliases.toList())
            assertTrue(fixture.service.deleteCertificateChain("stored-chain", PROVIDER_ID))
            assertTrue(fixture.service.deleteCertificateChain("stored-chain", PROVIDER_ID))
            assertEquals(0, fixture.platformStore.deleteCertificateChainCalls)
            assertEquals(0, fixture.kmsProviderLookups)
        }

    @Test
    fun providerNativeReadIsFreshListIsLocalAndDeleteNeverMutatesProvider() =
        runTest {
            val fixture = Fixture()
            val leaf = certificateFor(generateKeyPair("EC"), "provider-native")
            fixture.providerInspector.references["provider-cert"] = providerReference("provider-cert", leaf)
            fixture.platformAliasLister.trustedAliases += listOf("legacy-platform", "provider-cert")

            fixture.service.registerCertificateReference(
                RegisterCertificateReferenceInput(
                    providerId = PROVIDER_ID,
                    alias = "provider-cert",
                    kind = CertificateReferenceKind.TRUSTED_CERTIFICATE,
                    source = CertificateReferenceSource.PROVIDER_NATIVE,
                ),
            )

            assertContentEquals(leaf.der, fixture.service.getTrustedCertificate("provider-cert", PROVIDER_ID).certificate.value)
            assertContentEquals(leaf.der, fixture.service.getTrustedCertificate("provider-cert", PROVIDER_ID).certificate.value)
            assertEquals(3, fixture.providerInspector.inspectCalls)
            assertEquals(
                listOf("provider-cert", "legacy-platform"),
                fixture.service.listTrustedCertificateAliases(PROVIDER_ID).aliases.toList(),
            )
            assertEquals(1, fixture.platformAliasLister.listTrustedCalls)
            assertEquals(0, fixture.platformStore.listCertificateAliasesCalls)
            assertEquals(0, fixture.kmsProviderLookups)
            assertTrue(fixture.service.deleteTrustedCertificate("provider-cert", PROVIDER_ID))
            assertTrue(fixture.service.deleteTrustedCertificate("provider-cert", PROVIDER_ID))
            assertEquals(0, fixture.platformStore.deleteCertificateCalls)
            assertEquals(0, fixture.kmsProviderLookups)
        }

    @Test
    fun providerDriftFailsClosedAfterFreshRead() =
        runTest {
            val fixture = Fixture()
            val original = certificateFor(generateKeyPair("EC"), "original")
            val replacement = certificateFor(generateKeyPair("EC"), "replacement")
            fixture.providerInspector.references["drifting-cert"] = providerReference("drifting-cert", original)
            fixture.service.registerCertificateReference(
                RegisterCertificateReferenceInput(
                    providerId = PROVIDER_ID,
                    alias = "drifting-cert",
                    kind = CertificateReferenceKind.TRUSTED_CERTIFICATE,
                    source = CertificateReferenceSource.PROVIDER_NATIVE,
                ),
            )
            fixture.providerInspector.references["drifting-cert"] = providerReference("drifting-cert", replacement)

            val failure = assertFailsWith<CertificateReferenceResolutionException> {
                fixture.service.getTrustedCertificate("drifting-cert", PROVIDER_ID)
            }

            assertEquals(CertificateReferenceStoreErrorCodes.PROVIDER_DRIFT, failure.code)
        }

    @Test
    fun providerNativeKeyChainFreshReadRequiresTheLinkedProviderKeyIdentity() =
        runTest {
            val fixture = Fixture()
            val keyPair = generateKeyPair("EC")
            val leaf = certificateFor(keyPair, "provider-native-chain")
            fixture.addLinkedKey("provider-native-key", keyPair)
            fixture.providerInspector.references["provider-native-chain"] =
                providerReference("provider-native-chain", leaf)

            fixture.service.registerCertificateReference(
                RegisterCertificateReferenceInput(
                    providerId = PROVIDER_ID,
                    alias = "provider-native-chain",
                    kind = CertificateReferenceKind.KEY_CERTIFICATE_CHAIN,
                    source = CertificateReferenceSource.PROVIDER_NATIVE,
                    linkedKeyAlias = "provider-native-key",
                ),
            )

            assertContentEquals(
                leaf.der,
                fixture.service.getCertificateChain("provider-native-chain", PROVIDER_ID).certificates.single().value,
            )

            fixture.addLinkedKey("provider-native-key", generateKeyPair("EC"))
            val wrongKey = assertFailsWith<CertificateReferenceResolutionException> {
                fixture.service.getCertificateChain("provider-native-chain", PROVIDER_ID)
            }
            assertEquals(CertificateReferenceStoreErrorCodes.KEY_IDENTITY_MISMATCH, wrongKey.code)
        }

    @Test
    fun storedChainReadResolvesTheLinkedKeyByAliasWhenTheProviderCannotResolveAKid() =
        runTest {
            val fixture = Fixture()
            val keyPair = generateKeyPair("EC")
            val leaf = certificateFor(keyPair, "byoc-leaf")
            fixture.addLinkedKey("byoc-key", keyPair)

            fixture.service.registerCertificateReference(
                RegisterCertificateReferenceInput(
                    providerId = PROVIDER_ID,
                    alias = "byoc-chain",
                    kind = CertificateReferenceKind.KEY_CERTIFICATE_CHAIN,
                    source = CertificateReferenceSource.STORED_PUBLIC_MATERIAL,
                    linkedKeyAlias = "byoc-key",
                    certificateChain = listOf(Base64ByteArray(leaf.der)),
                ),
            )

            val chain = fixture.service.getCertificateChain("byoc-chain", PROVIDER_ID).certificates
            assertEquals(1, chain.size)
            assertContentEquals(leaf.der, chain[0].value)
        }

    @Test
    fun providerNativeKeyChainCompletesTheLiveLeafFromTheTenantTrustStore() =
        runTest {
            val fixture = Fixture()
            val caKeyPair = generateKeyPair("EC")
            val ca = certificateFor(caKeyPair, "issuing-ca")
            val keyPair = generateKeyPair("EC")
            val leaf = certificateSignedBy(keyPair, "integrated-leaf", caKeyPair, "issuing-ca")

            fixture.service.registerCertificateReference(
                RegisterCertificateReferenceInput(
                    providerId = PROVIDER_ID,
                    alias = "issuing-ca",
                    kind = CertificateReferenceKind.TRUSTED_CERTIFICATE,
                    source = CertificateReferenceSource.STORED_PUBLIC_MATERIAL,
                    certificateChain = listOf(Base64ByteArray(ca.der)),
                ),
            )

            fixture.addLinkedKey("integrated-key", keyPair)
            fixture.providerInspector.references["integrated-chain"] =
                providerReference("integrated-chain", leaf)
            fixture.service.registerCertificateReference(
                RegisterCertificateReferenceInput(
                    providerId = PROVIDER_ID,
                    alias = "integrated-chain",
                    kind = CertificateReferenceKind.KEY_CERTIFICATE_CHAIN,
                    source = CertificateReferenceSource.PROVIDER_NATIVE,
                    linkedKeyAlias = "integrated-key",
                ),
            )

            val chain = fixture.service.getCertificateChain("integrated-chain", PROVIDER_ID).certificates
            assertEquals(2, chain.size)
            assertContentEquals(leaf.der, chain[0].value)
            assertContentEquals(ca.der, chain[1].value)
        }

    @Test
    fun providerNativeKeyChainFailsWhenNoRegisteredAuthorityIssuesTheLeaf() =
        runTest {
            val fixture = Fixture()
            val caKeyPair = generateKeyPair("EC")
            val keyPair = generateKeyPair("EC")
            val leaf = certificateSignedBy(keyPair, "orphan-leaf", caKeyPair, "unregistered-ca")

            fixture.addLinkedKey("orphan-key", keyPair)
            fixture.providerInspector.references["orphan-chain"] = providerReference("orphan-chain", leaf)
            fixture.service.registerCertificateReference(
                RegisterCertificateReferenceInput(
                    providerId = PROVIDER_ID,
                    alias = "orphan-chain",
                    kind = CertificateReferenceKind.KEY_CERTIFICATE_CHAIN,
                    source = CertificateReferenceSource.PROVIDER_NATIVE,
                    linkedKeyAlias = "orphan-key",
                ),
            )

            val missing = assertFailsWith<CertificateReferenceResolutionException> {
                fixture.service.getCertificateChain("orphan-chain", PROVIDER_ID)
            }
            assertEquals(CertificateReferenceStoreErrorCodes.PROVIDER_DRIFT, missing.code)
        }

    @Test
    fun defaultPlatformManagedStoreRetainsLegacyListReadAndDeleteFallback() =
        runTest {
            val fixture = Fixture()
            val legacy = certificateFor(generateKeyPair("RSA"), "legacy")
            fixture.platformStore.trusted["legacy-managed"] = legacy
            fixture.platformAliasLister.trustedAliases += "legacy-managed"

            assertEquals(listOf("legacy-managed"), fixture.service.listTrustedCertificateAliases().aliases.toList())
            assertContentEquals(legacy.der, fixture.service.getTrustedCertificate("legacy-managed").certificate.value)
            assertTrue(fixture.service.deleteTrustedCertificate("legacy-managed"))
            assertEquals(1, fixture.platformAliasLister.listTrustedCalls)
            assertEquals(0, fixture.platformStore.listCertificateAliasesCalls)
            assertEquals(1, fixture.platformStore.deleteCertificateCalls)
        }

    @Test
    fun genericPlatformAliasListerDelegatesOnlyToTheDefaultCertificateStore() =
        runTest {
            val platformStore = RecordingPlatformCertificateStore()
            platformStore.trusted["trusted-platform"] = certificateFor(generateKeyPair("EC"), "trusted-platform")
            platformStore.chains["chain-platform"] = arrayOf(certificateFor(generateKeyPair("RSA"), "chain-platform"))
            var providerLookups = 0
            val lister = DefaultPlatformManagedCertificateAliasLister(
                keyManagerProxy(platformStore) { providerLookups++ },
            )

            assertEquals(listOf("trusted-platform"), lister.listTrustedCertificateAliases())
            assertEquals(listOf("chain-platform"), lister.listCertificateChainAliases())
            assertEquals(0, providerLookups)
        }

    @Test
    fun sharedProviderListingIsTenantScopedAndNeverEnumeratesTheProvider() =
        runTest {
            val sharedStore = InMemoryCertificateReferenceStore()
            val sharedProviderInspector = RecordingProviderInspector()
            val tenantA = Fixture(tenantId = "tenant-a", certificateStore = sharedStore, providerInspector = sharedProviderInspector)
            val tenantB = Fixture(tenantId = "tenant-b", certificateStore = sharedStore, providerInspector = sharedProviderInspector)
            val leafA = certificateFor(generateKeyPair("EC"), "tenant-a")
            val leafB = certificateFor(generateKeyPair("EC"), "tenant-b")
            sharedProviderInspector.references["tenant-a-cert"] = providerReference("tenant-a-cert", leafA)
            sharedProviderInspector.references["tenant-b-cert"] = providerReference("tenant-b-cert", leafB)
            tenantA.service.registerCertificateReference(providerNativeTrustedInput("tenant-a-cert"))
            tenantB.service.registerCertificateReference(providerNativeTrustedInput("tenant-b-cert"))
            tenantA.platformAliasLister.trustedAliases += "tenant-a-platform-cert"
            tenantB.platformAliasLister.trustedAliases += "tenant-b-platform-cert"

            assertEquals(
                listOf("tenant-a-cert", "tenant-a-platform-cert"),
                tenantA.service.listTrustedCertificateAliases(PROVIDER_ID).aliases.toList(),
            )
            assertEquals(
                listOf("tenant-b-cert", "tenant-b-platform-cert"),
                tenantB.service.listTrustedCertificateAliases(PROVIDER_ID).aliases.toList(),
            )
            assertEquals(listOf<String?>(PROVIDER_ID), tenantA.platformAliasLister.trustedProviderIds)
            assertEquals(listOf<String?>(PROVIDER_ID), tenantB.platformAliasLister.trustedProviderIds)
            assertEquals(0, tenantA.kmsProviderLookups)
            assertEquals(0, tenantB.kmsProviderLookups)
        }

    @Test
    fun metadataProjectionIsTenantScopedAndFiltersWithoutProviderAccess() =
        runTest {
            val sharedStore = InMemoryCertificateReferenceStore()
            val tenantA = Fixture(tenantId = "tenant-a", certificateStore = sharedStore)
            val tenantB = Fixture(tenantId = "tenant-b", certificateStore = sharedStore)
            tenantA.seedReference(referenceRecord("a-provider-native", "tenant-a", "provider-1"))
            tenantA.seedReference(
                referenceRecord(
                    id = "a-stored",
                    tenantId = "tenant-a",
                    providerId = "provider-2",
                    source = CertificateReferenceSource.STORED_PUBLIC_MATERIAL,
                ),
            )
            tenantA.seedReference(
                referenceRecord(
                    id = "a-chain",
                    tenantId = "tenant-a",
                    providerId = "provider-1",
                    kind = CertificateReferenceKind.KEY_CERTIFICATE_CHAIN,
                    source = CertificateReferenceSource.STORED_PUBLIC_MATERIAL,
                ),
            )
            tenantB.seedReference(referenceRecord("b-provider-native", "tenant-b", "provider-1"))

            assertEquals(
                setOf("a-provider-native", "a-chain"),
                tenantA.service.listCertificateReferences(providerId = "provider-1").references.map { it.id }.toSet(),
            )
            assertEquals(
                setOf("a-provider-native", "a-stored"),
                tenantA.service.listCertificateReferences(kind = CertificateReferenceKind.TRUSTED_CERTIFICATE).references.map { it.id }.toSet(),
            )
            assertEquals(
                setOf("a-stored", "a-chain"),
                tenantA.service.listCertificateReferences(source = CertificateReferenceSource.STORED_PUBLIC_MATERIAL).references.map { it.id }.toSet(),
            )
            assertEquals(
                listOf("a-chain"),
                tenantA.service.listCertificateReferences(
                    providerId = "provider-1",
                    kind = CertificateReferenceKind.KEY_CERTIFICATE_CHAIN,
                    source = CertificateReferenceSource.STORED_PUBLIC_MATERIAL,
                ).references.map { it.id },
            )
            assertEquals(listOf("b-provider-native"), tenantB.service.listCertificateReferences().references.map { it.id })
            assertEquals(0, tenantA.providerInspector.inspectCalls)
            assertEquals(0, tenantA.platformAliasLister.listTrustedCalls)
            assertEquals(0, tenantA.platformAliasLister.listChainCalls)
            assertEquals(0, tenantA.kmsProviderLookups)
        }

    @Test
    fun metadataProjectionDetailMapsPublicFieldsAndHidesUnknownCrossTenantAndDeletedReferences() =
        runTest {
            val sharedStore = InMemoryCertificateReferenceStore()
            val tenantA = Fixture(tenantId = "tenant-a", certificateStore = sharedStore)
            val tenantB = Fixture(tenantId = "tenant-b", certificateStore = sharedStore)
            val target = referenceRecord(
                id = "target-reference",
                tenantId = "tenant-a",
                providerId = "provider-1",
                providerCertificateId = "provider-certificate-1",
                kind = CertificateReferenceKind.KEY_CERTIFICATE_CHAIN,
                source = CertificateReferenceSource.STORED_PUBLIC_MATERIAL,
                controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                certificateFingerprint = ByteArray(32) { 7 },
                publicKeyFingerprint = ByteArray(32) { 8 },
            )
            tenantA.seedReference(target)
            tenantA.seedKeyReference(
                id = target.linkedKeyReferenceId!!,
                alias = "linked-key-alias",
                kid = "linked-key-kid",
                providerId = target.providerId,
            )
            tenantA.seedReference(referenceRecord("deleted-reference", "tenant-a", "provider-1", deleted = true))

            val response = tenantA.service.getCertificateReference(target.id)

            assertEquals(target.id, response.id)
            assertEquals(target.alias, response.alias)
            assertEquals(target.providerId, response.providerId)
            assertEquals(target.providerCertificateId, response.providerCertificateId)
            assertEquals(target.kind, response.kind)
            assertEquals(target.source, response.source)
            assertEquals(target.controlMode, response.controlMode)
            assertEquals(Origin.EXTERNAL, response.origin)
            assertEquals(target.linkedKeyReferenceId, response.linkedKeyReferenceId)
            assertEquals("linked-key-alias", response.linkedKeyAlias)
            assertEquals("linked-key-kid", response.linkedKeyKid)
            assertContentEquals(target.certificateFingerprint, response.certificateFingerprint.value)
            assertContentEquals(target.publicKeyFingerprint, response.publicKeyFingerprint.value)

            val unknown = assertFailsWith<CertificateReferenceResolutionException> {
                tenantA.service.getCertificateReference("unknown-reference")
            }
            val crossTenant = assertFailsWith<CertificateReferenceResolutionException> {
                tenantB.service.getCertificateReference(target.id)
            }
            val deleted = assertFailsWith<CertificateReferenceResolutionException> {
                tenantA.service.getCertificateReference("deleted-reference")
            }
            assertEquals("NOT_FOUND_ERROR", unknown.code)
            assertEquals(unknown.code, crossTenant.code)
            assertEquals(unknown.message, crossTenant.message)
            assertEquals(unknown.code, deleted.code)
            assertEquals(0, tenantA.providerInspector.inspectCalls)
            assertEquals(0, tenantA.platformAliasLister.listTrustedCalls)
            assertEquals(0, tenantA.platformAliasLister.listChainCalls)
            assertEquals(0, tenantA.kmsProviderLookups)
        }

    @Test
    fun unavailableHistoryMakesDeleteFailClosedBeforeLegacyProviderFallback() =
        runTest {
            val unavailable = InMemoryCertificateReferenceStore(isAvailable = false)
            val fixture = Fixture(certificateStore = unavailable)

            val failure = assertFailsWith<CertificateReferenceResolutionException> {
                fixture.service.deleteTrustedCertificate("possibly-external", PROVIDER_ID)
            }

            assertEquals(CertificateReferenceStoreErrorCodes.STORE_UNAVAILABLE, failure.code)
            assertEquals(0, fixture.platformStore.deleteCertificateCalls)
            assertEquals(0, fixture.kmsProviderLookups)
        }

    @Test
    fun nonDurableHistoryMakesDeleteFailClosedBeforeLegacyProviderFallback() =
        runTest {
            val unsupported = InMemoryCertificateReferenceStore(
                ownershipHistoryCapability = CertificateReferenceHistoryCapability.UNSUPPORTED,
            )
            val fixture = Fixture(certificateStore = unsupported)

            val failure = assertFailsWith<CertificateReferenceResolutionException> {
                fixture.service.deleteCertificateChain("possibly-external", PROVIDER_ID)
            }

            assertEquals(CertificateReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED, failure.code)
            assertEquals(0, fixture.platformStore.deleteCertificateChainCalls)
            assertEquals(0, fixture.kmsProviderLookups)
        }

    @Test
    fun canonicalEcAndRsaSpkiMatchingAcceptsEquivalentProviderKeys() =
        runTest {
            for (algorithm in listOf("EC", "RSA")) {
                val fixture = Fixture()
                val keyPair = generateKeyPair(algorithm)
                val alias = "${algorithm.lowercase()}-key"
                fixture.addLinkedKey(alias, keyPair)
                val result = fixture.service.registerCertificateReference(
                    storedChainInput(
                        alias = "${algorithm.lowercase()}-chain",
                        linkedKeyAlias = alias,
                        leaf = certificateFor(keyPair, algorithm.lowercase()),
                    ),
                )

                assertEquals(ResourceControlMode.EXTERNALLY_MANAGED, result.controlMode)
            }
        }

    @Test
    fun canonicalSpkiMismatchIsAConflictForEcAndRsa() =
        runTest {
            for (algorithm in listOf("EC", "RSA")) {
                val fixture = Fixture()
                fixture.addLinkedKey("mismatch-key", generateKeyPair(algorithm))
                val failure = assertFailsWith<CertificateReferenceResolutionException> {
                    fixture.service.registerCertificateReference(
                        storedChainInput(
                            alias = "mismatch-${algorithm.lowercase()}",
                            linkedKeyAlias = "mismatch-key",
                            leaf = certificateFor(generateKeyPair(algorithm), "mismatch"),
                        ),
                    )
                }

                assertEquals(CertificateReferenceStoreErrorCodes.KEY_IDENTITY_MISMATCH, failure.code)
            }
        }

    @Test
    fun azureStyleLeafOnlyProviderRegistersAKeyCertificateChain() =
        runTest {
            val fixture = Fixture()
            val keyPair = generateKeyPair("EC")
            val leaf = certificateFor(keyPair, "azure-leaf-only")
            fixture.addLinkedKey("azure-leaf-only-key", keyPair)
            fixture.providerInspector.references["azure-leaf-only"] =
                providerReference("azure-leaf-only", leaf)

            val record = fixture.service.registerCertificateReference(
                RegisterCertificateReferenceInput(
                    providerId = PROVIDER_ID,
                    alias = "azure-leaf-only",
                    kind = CertificateReferenceKind.KEY_CERTIFICATE_CHAIN,
                    source = CertificateReferenceSource.PROVIDER_NATIVE,
                    linkedKeyAlias = "azure-leaf-only-key",
                ),
            )

            assertEquals("azure-leaf-only", record.alias)
            assertEquals(1, fixture.certificateStore.rows.size)
        }

    @Test
    fun providerNativeRegistrationRejectsASuppliedEmptyCertificateChainBeforeProviderAccess() =
        runTest {
            val fixture = Fixture()

            val failure =
                assertFailsWith<CertificateReferenceResolutionException> {
                    fixture.service.registerCertificateReference(
                        RegisterCertificateReferenceInput(
                            providerId = PROVIDER_ID,
                            alias = "provider-native-empty-chain",
                            kind = CertificateReferenceKind.TRUSTED_CERTIFICATE,
                            source = CertificateReferenceSource.PROVIDER_NATIVE,
                            certificateChain = emptyList(),
                        ),
                    )
                }

            assertEquals("ILLEGAL_ARGUMENT_ERROR", failure.code)
            assertEquals(0, fixture.providerInspector.inspectCalls)
            assertTrue(fixture.certificateStore.rows.isEmpty())
        }

    private class Fixture(
        private val tenantId: String = TENANT_ID,
        val certificateStore: InMemoryCertificateReferenceStore = InMemoryCertificateReferenceStore(),
        val providerInspector: RecordingProviderInspector = RecordingProviderInspector(),
        val platformStore: RecordingPlatformCertificateStore = RecordingPlatformCertificateStore(),
        val platformAliasLister: RecordingPlatformAliasLister = RecordingPlatformAliasLister(),
    ) {
        private val execution = testSessionExecution(tenantId)
        private val keyStore = InMemoryKeyReferenceStore()
        private val inspectedKeys = linkedMapOf<String, ManagedKeyInfoType<*>>()
        var kmsProviderLookups: Int = 0
            private set

        private val keyInspector = object : ProviderKeyReferenceInspector {
            override suspend fun inspect(
                providerId: String,
                alias: String,
                kid: String?,
            ): IdkResult<ManagedKeyInfoType<*>, IdkError> =
                // The software provider resolves a tenant key by alias and cannot look one up by
                // kid. Modelling that here is what catches a read path insisting on a stored kid.
                if (kid != null) {
                    Err(IdkError.NOT_FOUND_ERROR(message = "provider key missing"))
                } else {
                    inspectedKeys[alias]?.let { Ok(it) }
                        ?: Err(IdkError.NOT_FOUND_ERROR(message = "provider key missing"))
                }
        }
        private val registrar = CertificateReferenceRegistrar(
            certificateReferenceStore = certificateStore,
            keyReferenceStore = keyStore,
            keyInspector = keyInspector,
            providerInspector = providerInspector,
            execution = execution,
        )
        val service = CertificatesRestServiceImpl(
            kms = keyManagerProxy(platformStore) { kmsProviderLookups++ },
            certificateService = unusedProxy(CertificateService::class.java),
            certificateReferenceRegistrar = registrar,
            platformManagedCertificateAliasLister = platformAliasLister,
        )

        suspend fun addLinkedKey(alias: String, keyPair: KeyPair) {
            val kid = "$alias-kid"
            val key = ManagedKeyInfo.fromKeyInfo(
                KeyInfo(
                    key = derPublicKeyToJwk(keyPair.public.encoded).copy(kid = kid),
                    alias = alias,
                    kid = kid,
                    providerId = PROVIDER_ID,
                ),
            )
            inspectedKeys[alias] = key
            keyStore.save(
                KeyReferenceRecord(
                    id = "$tenantId-$alias-id",
                    tenantId = tenantId,
                    alias = alias,
                    kid = kid,
                    providerId = PROVIDER_ID,
                    origin = Origin.EXTERNAL,
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                ),
            )
        }

        suspend fun seedReference(record: CertificateReferenceRecord) {
            certificateStore.save(record)
        }

        suspend fun seedKeyReference(
            id: String,
            alias: String,
            kid: String,
            providerId: String,
        ) {
            val now = Clock.System.now()
            keyStore.save(
                KeyReferenceRecord(
                    id = id,
                    tenantId = tenantId,
                    alias = alias,
                    kid = kid,
                    providerId = providerId,
                    origin = Origin.EXTERNAL,
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    private class RecordingProviderInspector : ProviderCertificateReferenceInspector {
        val references = linkedMapOf<String, ProviderCertificateReference>()
        var nextError: IdkError? = null
        var inspectCalls: Int = 0

        override suspend fun inspect(
            providerId: String,
            alias: String,
            providerCertificateId: String?,
            kind: CertificateReferenceKind,
        ): IdkResult<ProviderCertificateReference, IdkError> {
            inspectCalls++
            nextError?.let { return Err(it) }
            return references[alias]?.let { Ok(it) }
                ?: Err(IdkError.NOT_FOUND_ERROR(message = "provider certificate missing"))
        }
    }

    private class RecordingPlatformAliasLister : PlatformManagedCertificateAliasLister {
        val trustedAliases = mutableListOf<String>()
        val chainAliases = mutableListOf<String>()
        val trustedProviderIds = mutableListOf<String?>()
        val chainProviderIds = mutableListOf<String?>()
        var listTrustedCalls = 0
        var listChainCalls = 0

        override suspend fun listTrustedCertificateAliases(providerId: String?): List<String> {
            listTrustedCalls++
            trustedProviderIds += providerId
            return trustedAliases.toList()
        }

        override suspend fun listCertificateChainAliases(providerId: String?): List<String> {
            listChainCalls++
            chainProviderIds += providerId
            return chainAliases.toList()
        }
    }

    private class InMemoryCertificateReferenceStore(
        override val isAvailable: Boolean = true,
        override val ownershipHistoryCapability: CertificateReferenceHistoryCapability = CertificateReferenceHistoryCapability.DURABLE,
    ) : CertificateReferenceStore {
        val rows = linkedMapOf<String, CertificateReferenceRecord>()

        override suspend fun save(record: CertificateReferenceRecord): IdkResult<CertificateReferenceRecord, IdkError> {
            rows[record.id] = record
            return Ok(record)
        }

        override suspend fun upsert(record: CertificateReferenceRecord): IdkResult<CertificateReferenceRecord, IdkError> {
            val existing = rows.values.firstOrNull {
                it.tenantId == record.tenantId && it.providerId == record.providerId &&
                    it.alias == record.alias && it.kind == record.kind && it.deletedAt == null
            }
            val stored = record.copy(id = existing?.id ?: record.id, createdAt = existing?.createdAt ?: record.createdAt)
            rows[stored.id] = stored
            return Ok(stored)
        }

        override suspend fun findById(tenantId: String, id: String): IdkResult<CertificateReferenceRecord?, IdkError> =
            Ok(rows[id]?.takeIf { it.tenantId == tenantId && it.deletedAt == null })

        override suspend fun findByAlias(
            tenantId: String,
            alias: String,
            providerId: String,
            kind: CertificateReferenceKind,
        ): IdkResult<CertificateReferenceRecord?, IdkError> = Ok(active(tenantId).firstOrNull {
            it.alias == alias && it.providerId == providerId && it.kind == kind
        })

        override suspend fun findAllByAliasIncludingDeleted(
            tenantId: String,
            alias: String,
            providerId: String?,
            kind: CertificateReferenceKind,
        ): IdkResult<List<CertificateReferenceRecord>, IdkError> = Ok(rows.values
            .filter {
                it.tenantId == tenantId && it.alias == alias && it.kind == kind &&
                    (providerId == null || it.providerId == providerId)
            }
            .sortedWith(compareBy<CertificateReferenceRecord> { it.deletedAt != null }.thenByDescending { it.updatedAt }))

        override suspend fun findLatestByAliasIncludingDeleted(
            tenantId: String,
            alias: String,
            providerId: String?,
            kind: CertificateReferenceKind,
        ): IdkResult<CertificateReferenceRecord?, IdkError> =
            findAllByAliasIncludingDeleted(tenantId, alias, providerId, kind).map { it.firstOrNull() }

        override suspend fun findByProviderCertificateId(
            tenantId: String,
            providerId: String,
            providerCertificateId: String,
        ): IdkResult<List<CertificateReferenceRecord>, IdkError> = Ok(active(tenantId).filter {
            it.providerId == providerId && it.providerCertificateId == providerCertificateId
        })

        override suspend fun findByLinkedKeyReferenceId(
            tenantId: String,
            linkedKeyReferenceId: String,
        ): IdkResult<List<CertificateReferenceRecord>, IdkError> =
            Ok(active(tenantId).filter { it.linkedKeyReferenceId == linkedKeyReferenceId })

        override suspend fun findAll(
            tenantId: String,
            providerId: String?,
            kind: CertificateReferenceKind?,
            source: CertificateReferenceSource?,
        ): IdkResult<List<CertificateReferenceRecord>, IdkError> = Ok(active(tenantId).filter {
            (providerId == null || it.providerId == providerId) &&
                (kind == null || it.kind == kind) &&
                (source == null || it.source == source)
        })

        override suspend fun delete(
            tenantId: String,
            alias: String,
            providerId: String,
            kind: CertificateReferenceKind,
        ): IdkResult<Boolean, IdkError> = softDelete {
            it.tenantId == tenantId && it.alias == alias && it.providerId == providerId && it.kind == kind
        }

        override suspend fun deleteById(tenantId: String, id: String): IdkResult<Boolean, IdkError> =
            softDelete { it.tenantId == tenantId && it.id == id }

        private fun active(tenantId: String): List<CertificateReferenceRecord> =
            rows.values.filter { it.tenantId == tenantId && it.deletedAt == null }

        private fun softDelete(predicate: (CertificateReferenceRecord) -> Boolean): IdkResult<Boolean, IdkError> {
            val row = rows.values.firstOrNull { it.deletedAt == null && predicate(it) } ?: return Ok(false)
            rows[row.id] = row.copy(deletedAt = Clock.System.now(), updatedAt = Clock.System.now())
            return Ok(true)
        }
    }

    private class InMemoryKeyReferenceStore : KeyReferenceStore {
        private val rows = linkedMapOf<String, KeyReferenceRecord>()

        override suspend fun save(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> {
            rows[record.id] = record
            return Ok(record)
        }

        override suspend fun upsert(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> = save(record)

        override suspend fun findById(tenantId: String, id: String): IdkResult<KeyReferenceRecord?, IdkError> =
            Ok(rows[id]?.takeIf { it.tenantId == tenantId && it.deletedAt == null })

        override suspend fun findByKid(
            tenantId: String,
            kid: String,
            providerId: String?,
        ): IdkResult<KeyReferenceRecord?, IdkError> = Ok(rows.values.firstOrNull {
            it.tenantId == tenantId && it.kid == kid && it.deletedAt == null &&
                (providerId == null || it.providerId == providerId)
        })

        override suspend fun findByAlias(
            tenantId: String,
            alias: String,
            providerId: String?,
        ): IdkResult<KeyReferenceRecord?, IdkError> = Ok(rows.values.firstOrNull {
            it.tenantId == tenantId && it.alias == alias && it.deletedAt == null &&
                (providerId == null || it.providerId == providerId)
        })

        override suspend fun findAll(
            tenantId: String,
            filter: ManagedKeyReferenceFilter?,
        ): IdkResult<List<KeyReferenceRecord>, IdkError> = Ok(rows.values.filter {
            it.tenantId == tenantId && it.deletedAt == null &&
                (filter?.providerId == null || it.providerId == filter.providerId)
        })

        override suspend fun delete(tenantId: String, alias: String, providerId: String): IdkResult<Boolean, IdkError> = Ok(false)

        override suspend fun deleteByKid(tenantId: String, kid: String, providerId: String?): IdkResult<Boolean, IdkError> = Ok(false)

        override suspend fun exists(tenantId: String, alias: String, providerId: String): IdkResult<Boolean, IdkError> =
            Ok(rows.values.any { it.tenantId == tenantId && it.alias == alias && it.providerId == providerId && it.deletedAt == null })
    }

    private class RecordingPlatformCertificateStore : KeyStoreService, CertificateStoreService {
        val trusted = linkedMapOf<String, Certificate>()
        val chains = linkedMapOf<String, Array<Certificate>>()
        var listCertificateAliasesCalls = 0
        var listCertificateChainAliasesCalls = 0
        var deleteCertificateCalls = 0
        var deleteCertificateChainCalls = 0

        override val settings: KeyProviderSettings? = null
        override suspend fun listKeys(): Array<ManagedKeyReference> = emptyArray()
        override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> = error("unused")
        override suspend fun storeKey(
            keyInfo: ResolvedKeyInfoType<*>,
            providerId: String,
            alias: String,
            certChain: Array<Certificate>?,
        ): ManagedKeyInfoType<*> = error("unused")
        override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean = error("unused")
        override fun keyVisibility(): KeyVisibility = KeyVisibility.PUBLIC

        override suspend fun storeCertificateChain(
            alias: String,
            certificates: Array<Certificate>,
            keyInfo: ResolvedKeyInfoType<*>?,
        ) {
            chains[alias] = certificates
        }

        override suspend fun listCertificateChainAliases(): Array<String> {
            listCertificateChainAliasesCalls++
            return chains.keys.toTypedArray()
        }

        override suspend fun getCertificateChain(alias: String): Array<Certificate> = chains.getValue(alias)

        override suspend fun deleteCertificateChain(alias: String): Boolean {
            deleteCertificateChainCalls++
            return chains.remove(alias) != null
        }

        override suspend fun storeTrustedCertificate(alias: String, certificate: Certificate) {
            trusted[alias] = certificate
        }

        override suspend fun listCertificateAliases(): Array<String> {
            listCertificateAliasesCalls++
            return trusted.keys.toTypedArray()
        }

        override suspend fun getCertificate(alias: String): Certificate = trusted.getValue(alias)

        override suspend fun deleteCertificate(alias: String): Boolean {
            deleteCertificateCalls++
            return trusted.remove(alias) != null
        }
    }

    private companion object {
        const val TENANT_ID = "tenant-1"
        const val PROVIDER_ID = "shared-provider"

        fun providerNativeTrustedInput(alias: String) = RegisterCertificateReferenceInput(
            providerId = PROVIDER_ID,
            alias = alias,
            kind = CertificateReferenceKind.TRUSTED_CERTIFICATE,
            source = CertificateReferenceSource.PROVIDER_NATIVE,
        )

        fun storedChainInput(
            alias: String,
            linkedKeyAlias: String,
            leaf: Certificate,
        ) = RegisterCertificateReferenceInput(
            providerId = PROVIDER_ID,
            alias = alias,
            kind = CertificateReferenceKind.KEY_CERTIFICATE_CHAIN,
            source = CertificateReferenceSource.STORED_PUBLIC_MATERIAL,
            linkedKeyAlias = linkedKeyAlias,
            certificateChain = listOf(Base64ByteArray(leaf.der)),
        )

        fun providerReference(alias: String, certificate: Certificate) = ProviderCertificateReference(
            providerId = PROVIDER_ID,
            alias = alias,
            id = "$alias-id",
            certificate = certificate,
        )

        fun referenceRecord(
            id: String,
            tenantId: String,
            providerId: String,
            providerCertificateId: String? = null,
            kind: CertificateReferenceKind = CertificateReferenceKind.TRUSTED_CERTIFICATE,
            source: CertificateReferenceSource = CertificateReferenceSource.PROVIDER_NATIVE,
            controlMode: ResourceControlMode = ResourceControlMode.EXTERNALLY_MANAGED,
            certificateFingerprint: ByteArray = ByteArray(32) { 3 },
            publicKeyFingerprint: ByteArray = ByteArray(32) { 4 },
            deleted: Boolean = false,
        ): CertificateReferenceRecord {
            val now = Clock.System.now()
            return CertificateReferenceRecord(
                id = id,
                tenantId = tenantId,
                alias = "$id-alias",
                providerId = providerId,
                providerCertificateId = providerCertificateId,
                kind = kind,
                source = source,
                controlMode = controlMode,
                linkedKeyReferenceId = if (kind == CertificateReferenceKind.KEY_CERTIFICATE_CHAIN) "$id-key-reference" else null,
                certificateChainDer = if (source == CertificateReferenceSource.STORED_PUBLIC_MATERIAL) {
                    CertificateReferenceRecord.encodeCertificateChain(listOf(byteArrayOf(1, 2, 3)))
                } else {
                    null
                },
                certificateFingerprint = certificateFingerprint,
                publicKeyFingerprint = publicKeyFingerprint,
                createdAt = now,
                updatedAt = now,
                deletedAt = now.takeIf { deleted },
            )
        }

        fun generateKeyPair(algorithm: String): KeyPair = when (algorithm) {
            "EC" -> KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()
            "RSA" -> KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            else -> error("Unsupported test algorithm: $algorithm")
        }

        fun certificateFor(keyPair: KeyPair, commonName: String): Certificate {
            if (Security.getProvider("BC") == null) Security.addProvider(BouncyCastleProvider())
            val now = Clock.System.now()
            val subject = X500Name("CN=$commonName")
            val signatureAlgorithm = if (keyPair.public.algorithm == "EC") "SHA256withECDSA" else "SHA256withRSA"
            val holder = JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now.epochSeconds),
                Date.from(java.time.Instant.ofEpochSecond((now - 1.days).epochSeconds)),
                Date.from(java.time.Instant.ofEpochSecond((now + 30.days).epochSeconds)),
                subject,
                keyPair.public,
            ).build(JcaContentSignerBuilder(signatureAlgorithm).setProvider("BC").build(keyPair.private))
            return certificateFromDer(JcaX509CertificateConverter().setProvider("BC").getCertificate(holder).encoded)
        }

        fun certificateSignedBy(
            keyPair: KeyPair,
            commonName: String,
            issuerKeyPair: KeyPair,
            issuerCommonName: String,
        ): Certificate {
            if (Security.getProvider("BC") == null) Security.addProvider(BouncyCastleProvider())
            val now = Clock.System.now()
            val signatureAlgorithm = if (issuerKeyPair.public.algorithm == "EC") "SHA256withECDSA" else "SHA256withRSA"
            val holder = JcaX509v3CertificateBuilder(
                X500Name("CN=" + issuerCommonName),
                BigInteger.valueOf(now.epochSeconds),
                Date.from(java.time.Instant.ofEpochSecond((now - 1.days).epochSeconds)),
                Date.from(java.time.Instant.ofEpochSecond((now + 30.days).epochSeconds)),
                X500Name("CN=" + commonName),
                keyPair.public,
            ).build(JcaContentSignerBuilder(signatureAlgorithm).setProvider("BC").build(issuerKeyPair.private))
            return certificateFromDer(JcaX509CertificateConverter().setProvider("BC").getCertificate(holder).encoded)
        }

        fun keyManagerProxy(
            platformStore: RecordingPlatformCertificateStore,
            providerLookup: () -> Unit,
        ): KeyManagerService = Proxy.newProxyInstance(
            KeyManagerService::class.java.classLoader,
            arrayOf(KeyManagerService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getKeyStore" -> platformStore
                "getProviderById" -> {
                    providerLookup()
                    error("Provider-wide certificate store access is forbidden in this test")
                }
                else -> error("KeyManagerService.${method.name} must not be called")
            }
        } as KeyManagerService

        @Suppress("UNCHECKED_CAST")
        fun <T> unusedProxy(type: Class<T>): T = Proxy.newProxyInstance(
            type.classLoader,
            arrayOf(type),
        ) { _, method, _ -> error("${type.simpleName}.${method.name} must not be called") } as T

        fun testSessionExecution(tenantId: String): SessionExecution = object : SessionExecution {
            override val tenantId: String = tenantId
            override val principalId: String = "principal-1"
            override val correlationId: String = "correlation-1"
            override val sessionContextManager: SessionContextManager get() = error("unused")
            override val sessionContext: SessionContext = object : SessionContext {
                override val sessionId: String = "session-$tenantId"
                override val context: UserContext = object : UserContext {
                    override val id: String = "user-1"
                    override val tenant: TenantContextData = object : TenantContextData {
                        override val tenantId: String = tenantId
                    }
                    override val principal: Any? = "principal-1"
                    override val secureDetails = null
                }
            }
            override val log: SessionLogService = NoOpSessionLogService(sessionContext)
            override val interceptorChain: CommandLifecycleInterceptorChain = EmptyInterceptorChain
            override val conf: ContextConfig = NoOpContextConfig
        }

        object NoOpContextConfig : ContextConfig {
            override val app: AppConfigService get() = error("unused")
            override val tenant: TenantConfigService get() = error("unused")
            override val principal: PrincipalConfigService get() = error("unused")
            override fun conf(level: ConfigLevel): ConfigService = error("unused")
        }

        class NoOpSessionLogService(
            override val sessionContext: SessionContext,
        ) : SessionLogService {
            override val id: String = "certificate-reference-test-log"
            override val isEnabled: Boolean = false
            override val scope: IdkScope = IdkScope.SESSION
            override val logManager: SessionLogManager get() = throw NotImplementedError("unused")
            override suspend fun setConfig(config: LoggerConfig): LogService = this
            override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)
            override fun toAsync(): AsyncLogService = throw NotImplementedError("unused")
        }
    }
}
