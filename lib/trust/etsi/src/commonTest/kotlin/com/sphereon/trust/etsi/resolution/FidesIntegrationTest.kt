/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.trust.etsi.resolution

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.IdkOkResult
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.x509.X509VerificationRequestType
import com.sphereon.crypto.core.x509.X509VerificationResult
import com.sphereon.crypto.core.x509.X509VerificationResultType
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.context.createAnonymousSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.resolver.ResolutionOptions
import com.sphereon.trust.core.resolver.TrustListData
import com.sphereon.trust.core.resolver.TrustListResolver
import com.sphereon.trust.etsi.lote.model.EidasRole
import com.sphereon.trust.etsi.matcher.CertificateTrustListMatcher
import com.sphereon.trust.etsi.model.ETSIServiceStatus
import com.sphereon.trust.etsi.model.ETSIServiceType
import com.sphereon.trust.etsi.parser.ETSITrustListParser
import com.sphereon.trust.etsi.parser.StreamingETSITrustListParser
import com.sphereon.trust.etsi.testutil.EtsiTestContext
import com.sphereon.trust.etsi.testutil.FIDES_LOTL_URL
import com.sphereon.trust.etsi.testutil.FIDES_TL_URL
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Integration tests using the FIDES sandbox trust list data.
 *
 * Tests parse real ETSI TS 119 612 XML files fetched from GitHub, then exercise
 * the certificate matcher, X.509 validation service, and role verification service
 * against actual certificates embedded in the FIDES trust lists.
 */
class FidesIntegrationTest {
    private val ctx = EtsiTestContext("fides-integration-test", this)
    private val parser = StreamingETSITrustListParser()

    // -- Parsing integration tests --

    @Test
    fun parseFidesLotlStructure() =
        runTest {
            val lotlXml = ctx.fetchUrl(FIDES_LOTL_URL)
            val lotl = parser.parseFromString(lotlXml)

            assertEquals("NL", lotl.schemeTerritory)
            assertTrue(lotl.sequenceNumber > 0, "Sequence number should be positive")
            assertTrue(lotl.pointersToOtherLoTE.isNotEmpty())

            val pointer = lotl.pointersToOtherLoTE.first()
            assertEquals("NL", pointer.schemeTerritory)
            assertTrue(pointer.location.contains("FIDES-TL.xml"))
            assertTrue(pointer.serviceDigitalIdentities.isNotEmpty())
        }

    @Test
    fun parseFidesTlEntities() =
        runTest {
            val tlXml = ctx.fetchUrl(FIDES_TL_URL)
            val tl = parser.parseFromString(tlXml)

            assertEquals("NL", tl.schemeTerritory)
            assertTrue(tl.sequenceNumber > 0, "Sequence number should be positive")
            assertTrue(tl.trustedEntities.size >= 3, "Expected at least 3 trusted entities")
        }

    @Test
    fun parseFidesTlCertificatesAreExtractable() =
        runTest {
            val tlXml = ctx.fetchUrl(FIDES_TL_URL)
            val tl = parser.parseFromString(tlXml)

            for (entity in tl.trustedEntities) {
                for (service in entity.trustedEntityServices) {
                    val certs = service.serviceInformation.serviceDigitalIdentity.x509Certificates
                    assertTrue(certs.isNotEmpty(), "Service ${service.serviceInformation.serviceName.first().value} should have certs")

                    // Verify Base64 can be decoded
                    val certDER = certs.first().decodeFrom(Encoding.BASE64)
                    assertTrue(certDER.isNotEmpty(), "Certificate DER should not be empty")
                }
            }
        }

    @Test
    fun parseFidesTlAllServicesAreGranted() =
        runTest {
            val tlXml = ctx.fetchUrl(FIDES_TL_URL)
            val tl = parser.parseFromString(tlXml)

            for (entity in tl.trustedEntities) {
                for (service in entity.trustedEntityServices) {
                    assertEquals(
                        ETSIServiceStatus.GRANTED,
                        service.serviceInformation.serviceStatus,
                        "Service ${service.serviceInformation.serviceName.first().value} should be GRANTED",
                    )
                }
            }
        }

    // -- CertificateTrustListMatcher against real FIDES data --

    @Test
    fun matcherDirectMatchesFidesLabsCertificate() =
        runTest {
            val tlXml = ctx.fetchUrl(FIDES_TL_URL)
            val tl = parser.parseFromString(tlXml)
            val fidesEntity =
                tl.trustedEntities.first {
                    it.trustedEntityInformation.name
                        .first()
                        .value == "FIDES Labs"
                }
            val certBase64 =
                fidesEntity.trustedEntityServices
                    .first()
                    .serviceInformation.serviceDigitalIdentity.x509Certificates
                    .first()
            val certDER = certBase64.decodeFrom(Encoding.BASE64)

            val result =
                CertificateTrustListMatcher.findMatchingEntity(
                    trustList = tl,
                    certDER = certDER,
                    chain = null,
                    serviceTypeFilter = null,
                    allowCAChainMatch = false,
                )

            assertNotNull(result)
            assertEquals(TspMatchType.DIRECT_MATCH, result.matchType)
            assertEquals(
                "FIDES Labs",
                result.entity.trustedEntityInformation.name
                    .first()
                    .value,
            )
        }

    @Test
    fun matcherDirectMatchesKvkCertificate() =
        runTest {
            val tlXml = ctx.fetchUrl(FIDES_TL_URL)
            val tl = parser.parseFromString(tlXml)
            val kvkEntity =
                tl.trustedEntities.first {
                    it.trustedEntityInformation.name
                        .first()
                        .value == "Kamer van Koophandel"
                }
            val certBase64 =
                kvkEntity.trustedEntityServices
                    .first()
                    .serviceInformation.serviceDigitalIdentity.x509Certificates
                    .first()
            val certDER = certBase64.decodeFrom(Encoding.BASE64)

            val result =
                CertificateTrustListMatcher.findMatchingEntity(
                    trustList = tl,
                    certDER = certDER,
                    chain = null,
                    serviceTypeFilter = null,
                    allowCAChainMatch = false,
                )

            assertNotNull(result)
            assertEquals(TspMatchType.DIRECT_MATCH, result.matchType)
            assertEquals(
                "Kamer van Koophandel",
                result.entity.trustedEntityInformation.name
                    .first()
                    .value,
            )
        }

    @Test
    fun matcherReturnsNullForUnknownCertificate() =
        runTest {
            val tlXml = ctx.fetchUrl(FIDES_TL_URL)
            val tl = parser.parseFromString(tlXml)
            val fakeCert = "not-a-real-certificate".encodeToByteArray()

            val result =
                CertificateTrustListMatcher.findMatchingEntity(
                    trustList = tl,
                    certDER = fakeCert,
                    chain = null,
                    serviceTypeFilter = null,
                    allowCAChainMatch = false,
                )

            assertNull(result)
        }

    @Test
    fun matcherServiceTypeFilterExcludesNonMatchingServices() =
        runTest {
            val tlXml = ctx.fetchUrl(FIDES_TL_URL)
            val tl = parser.parseFromString(tlXml)
            val fidesEntity =
                tl.trustedEntities.first {
                    it.trustedEntityInformation.name
                        .first()
                        .value == "FIDES Labs"
                }
            val certBase64 =
                fidesEntity.trustedEntityServices
                    .first()
                    .serviceInformation.serviceDigitalIdentity.x509Certificates
                    .first()
            val certDER = certBase64.decodeFrom(Encoding.BASE64)

            // FIDES uses EAA service type; filtering for PID should exclude it
            val result =
                CertificateTrustListMatcher.findMatchingEntity(
                    trustList = tl,
                    certDER = certDER,
                    chain = null,
                    serviceTypeFilter = listOf(ETSIServiceType.PID_ISSUANCE),
                    allowCAChainMatch = false,
                )

            assertNull(result, "PID service type filter should not match EAA service")
        }

    @Test
    fun matcherServiceTypeFilterIncludesMatchingServices() =
        runTest {
            val tlXml = ctx.fetchUrl(FIDES_TL_URL)
            val tl = parser.parseFromString(tlXml)
            val fidesEntity =
                tl.trustedEntities.first {
                    it.trustedEntityInformation.name
                        .first()
                        .value == "FIDES Labs"
                }
            val certBase64 =
                fidesEntity.trustedEntityServices
                    .first()
                    .serviceInformation.serviceDigitalIdentity.x509Certificates
                    .first()
            val certDER = certBase64.decodeFrom(Encoding.BASE64)

            // FIDES uses EAA service type; filtering for EAA should include it
            val result =
                CertificateTrustListMatcher.findMatchingEntity(
                    trustList = tl,
                    certDER = certDER,
                    chain = null,
                    serviceTypeFilter = listOf("http://uri.etsi.org/TrstSvc/Svctype/EAA"),
                    allowCAChainMatch = false,
                )

            assertNotNull(result)
            assertEquals(TspMatchType.DIRECT_MATCH, result.matchType)
        }

    @Test
    fun matcherMatchResultContainsServiceStatus() =
        runTest {
            val tlXml = ctx.fetchUrl(FIDES_TL_URL)
            val tl = parser.parseFromString(tlXml)
            val fidesEntity =
                tl.trustedEntities.first {
                    it.trustedEntityInformation.name
                        .first()
                        .value == "FIDES Labs"
                }
            val certBase64 =
                fidesEntity.trustedEntityServices
                    .first()
                    .serviceInformation.serviceDigitalIdentity.x509Certificates
                    .first()
            val certDER = certBase64.decodeFrom(Encoding.BASE64)

            val result =
                CertificateTrustListMatcher.findMatchingEntity(
                    trustList = tl,
                    certDER = certDER,
                    chain = null,
                    serviceTypeFilter = null,
                    allowCAChainMatch = false,
                )

            assertNotNull(result)
            assertEquals(ETSIServiceStatus.GRANTED, result.serviceInfo.serviceStatus)
            assertEquals("http://uri.etsi.org/TrstSvc/Svctype/EAA", result.serviceInfo.serviceTypeIdentifier)
        }

    // -- X509 validation service integration with FIDES data --

    @Test
    fun certParsesViaServiceExecute() =
        runTest {
            // Reproduce the exact flow the service uses
            val fidesLabsCert = extractFidesLabsCertBase64()

            // Step 1: x509DerOrPemToPem (same as service line 154)
            val certPEM =
                com.sphereon.crypto.core.x509
                    .x509DerOrPemToPem(fidesLabsCert)
            assertNotNull(certPEM, "Should convert to PEM")

            // Step 2: certificateFromPem (same as service line 155)
            val certificate =
                com.sphereon.crypto.core.x509
                    .certificateFromPem(certPEM)
            assertNotNull(certificate, "Should parse PEM to Certificate")

            // Step 3: Now try via the service
            val service = createX509ValidationService()
            val opts =
                ExternalIdentifierX509ETSIValidationOpts(
                    identifier = KeyInfo<Nothing>(x5c = arrayOf(fidesLabsCert)),
                    explicitTslUri = FIDES_TL_URI,
                )

            // Verify the x5c value survives the KeyInfo construction
            assertEquals(fidesLabsCert, opts.identifier.x5c!![0], "x5c[0] should be unchanged")

            val result = service.execute(opts)
            assertTrue(result.isOk, "Service execute should succeed: ${if (result.isErr) result.error else ""}")
        }

    @Test
    fun certParsingDiagnostic() =
        runTest {
            val tlXml = ctx.fetchUrl(FIDES_TL_URL)
            val tl = parser.parseFromString(tlXml)

            // Check all entity certs can be parsed
            for (entity in tl.trustedEntities) {
                val name =
                    entity.trustedEntityInformation.name
                        .first()
                        .value
                for (service in entity.trustedEntityServices) {
                    val certBase64 =
                        service.serviceInformation.serviceDigitalIdentity.x509Certificates
                            .first()

                    // Test 1: Decode to DER
                    val der = certBase64.decodeFrom(Encoding.BASE64)
                    assertTrue(der.isNotEmpty(), "$name: DER should not be empty")

                    // Test 2: Parse as PEM via wrapX509CertificatePem + certificateFromPem
                    try {
                        val pem =
                            com.sphereon.crypto.core.x509
                                .wrapX509CertificatePem(certBase64)
                        val cert =
                            com.sphereon.crypto.core.x509
                                .certificateFromPem(pem)
                        assertNotNull(cert, "$name: Certificate should be parseable via PEM path")
                    } catch (expected: Exception) {
                        throw AssertionError("$name: Failed to parse cert via PEM: ${expected.message}", expected)
                    }

                    // Test 3: Parse via x509DerOrPemToPem (the path the X509 validation service uses)
                    try {
                        val pem =
                            com.sphereon.crypto.core.x509
                                .x509DerOrPemToPem(certBase64)
                        assertNotNull(pem, "$name: Should convert to PEM")
                    } catch (expected: Exception) {
                        throw AssertionError("$name: x509DerOrPemToPem failed: ${expected.message}", expected)
                    }
                }
            }
        }

    @Test
    fun parserStripsWhitespaceFromBase64Fields() =
        runTest {
            val tlXml = ctx.fetchUrl(FIDES_TL_URL)
            // XML formatting introduces whitespace/newlines in X509Certificate text elements.
            // Verify the parser strips it so downstream consumers get clean Base64.
            val tl = parser.parseFromString(tlXml)
            val certBase64 =
                tl.trustedEntities
                    .first {
                        it.trustedEntityInformation.name
                            .first()
                            .value == "FIDES Labs"
                    }.trustedEntityServices
                    .first()
                    .serviceInformation.serviceDigitalIdentity.x509Certificates
                    .first()

            assertFalse(certBase64.contains("\n"), "Parser should strip newlines from X509Certificate Base64")
            assertFalse(certBase64.contains(" "), "Parser should strip spaces from X509Certificate Base64")

            // Verify the same for parseFromBytes path
            val freshTl = parser.parseFromBytes(tlXml.encodeToByteArray())
            val freshCertBase64 =
                freshTl.trustedEntities
                    .first {
                        it.trustedEntityInformation.name
                            .first()
                            .value == "FIDES Labs"
                    }.trustedEntityServices
                    .first()
                    .serviceInformation.serviceDigitalIdentity.x509Certificates
                    .first()

            assertEquals(certBase64, freshCertBase64, "Both parse paths should produce identical clean Base64")
        }

    @Test
    fun x509ValidationServiceFindsFidesLabsCert() =
        runTest {
            val service = createX509ValidationService()

            val fidesLabsCert = extractFidesLabsCertBase64()
            val opts =
                ExternalIdentifierX509ETSIValidationOpts(
                    identifier = KeyInfo<Nothing>(x5c = arrayOf(fidesLabsCert)),
                    explicitTslUri = FIDES_TL_URI,
                )

            val result = service.execute(opts)

            assertTrue(result.isOk, "Validation should succeed: ${if (result.isErr) result.error else ""}")
            val validation = result.value
            assertTrue(validation.trusted, "FIDES Labs cert should be trusted: ${validation.details}")
            assertEquals(TrustStatus.TRUSTED, validation.trustStatus)
            assertNotNull(validation.matchedTSP)
            assertEquals("FIDES Labs", validation.matchedTSP!!.tspName)
            assertEquals(TspMatchType.DIRECT_MATCH, validation.matchedTSP!!.matchType)
        }

    @Test
    fun x509ValidationServiceFindsKvkCert() =
        runTest {
            val service = createX509ValidationService()

            val kvkCert = extractKvkCertBase64()
            val opts =
                ExternalIdentifierX509ETSIValidationOpts(
                    identifier = KeyInfo<Nothing>(x5c = arrayOf(kvkCert)),
                    explicitTslUri = FIDES_TL_URI,
                )

            val result = service.execute(opts)

            assertTrue(result.isOk)
            val validation = result.value
            assertTrue(validation.trusted, "KVK cert should be trusted: ${validation.details}")
            assertEquals("Kamer van Koophandel", validation.matchedTSP!!.tspName)
        }

    @Test
    fun x509ValidationServiceReturnsErrorForUnparseableCert() =
        runTest {
            val service = createX509ValidationService()

            // DUMMY_CERT_BASE64 is not a valid X.509 certificate structure (it's a public key),
            // so the service should return an error during certificate parsing.
            val opts =
                ExternalIdentifierX509ETSIValidationOpts(
                    identifier = KeyInfo<Nothing>(x5c = arrayOf(DUMMY_CERT_BASE64)),
                    explicitTslUri = FIDES_TL_URI,
                )

            val result = service.execute(opts)

            assertTrue(result.isErr, "Should return error for unparseable certificate data")
        }

    @Test
    fun x509ValidationServiceReturnsUntrustedForUnknownCert() =
        runTest {
            val service = createX509ValidationService()

            // Use the Belastingdienst cert against a service type filter that won't match,
            // to test the "not found in trust list" path with a valid but non-matching cert
            val tlXml = ctx.fetchUrl(FIDES_TL_URL)
            val tl = parser.parseFromString(tlXml)
            val belastingdienstEntity =
                tl.trustedEntities.first {
                    it.trustedEntityInformation.name
                        .first()
                        .value == "Belastingdienst"
                }
            val certBase64 =
                belastingdienstEntity.trustedEntityServices
                    .first()
                    .serviceInformation.serviceDigitalIdentity.x509Certificates
                    .first()

            val opts =
                ExternalIdentifierX509ETSIValidationOpts(
                    identifier = KeyInfo<Nothing>(x5c = arrayOf(certBase64)),
                    explicitTslUri = FIDES_TL_URI,
                    // Filter for a service type that doesn't exist in FIDES, so the cert won't match
                    serviceTypeFilter = listOf(ETSIServiceType.PID_ISSUANCE),
                )

            val result = service.execute(opts)

            assertTrue(result.isOk, "Validation should succeed (not error): ${if (result.isErr) result.error else ""}")
            val validation = result.value
            assertFalse(validation.trusted)
            assertEquals(TrustStatus.UNTRUSTED, validation.trustStatus)
            assertNull(validation.matchedTSP)
        }

    @Test
    fun x509ValidationServiceNavigatesLotlToFindTl() =
        runTest {
            val service = createX509ValidationService()

            val fidesLabsCert = extractFidesLabsCertBase64()
            // Use LOTL navigation instead of explicit TSL
            val opts =
                ExternalIdentifierX509ETSIValidationOpts(
                    identifier = KeyInfo<Nothing>(x5c = arrayOf(fidesLabsCert)),
                    lotlUri = FIDES_LOTL_URI,
                )

            val result = service.execute(opts)

            assertTrue(result.isOk)
            val validation = result.value
            assertTrue(validation.trusted, "Should find cert via LOTL navigation: ${validation.details}")
            assertEquals(TslDeterminationMethod.LOTL_NAVIGATION, validation.trustListInfo.determinationMethod)
            assertEquals("NL", validation.trustListInfo.territory)
        }

    // -- LoTERoleVerificationService integration with FIDES data --

    @Test
    fun roleVerificationWithFidesDataNoRoleMatch() =
        runTest {
            val service = createRoleVerificationService()

            val fidesLabsCert = extractFidesLabsCertBase64()
            val request =
                RoleVerificationRequest(
                    certificate = KeyInfo<Nothing>(x5c = arrayOf(fidesLabsCert)),
                    role = EidasRole.PID_ISSUER,
                    lotlUri = FIDES_LOTL_URI,
                )

            val result = service.verifyRole(request)

            // FIDES uses EAA service type, not PID_ISSUANCE, so no role-specific match
            assertTrue(result.isOk)
            assertFalse(result.value.verified)
        }

    @Test
    fun roleDiscoveryWithFidesData() =
        runTest {
            val service = createRoleVerificationService()

            val fidesLabsCert = extractFidesLabsCertBase64()
            val request =
                RoleDiscoveryRequest(
                    certificate = KeyInfo<Nothing>(x5c = arrayOf(fidesLabsCert)),
                    lotlUri = FIDES_LOTL_URI,
                )

            val result = service.discoverRoles(request)

            assertTrue(result.isOk)
            val discovery = result.value
            assertEquals(EidasRole.entries.size, discovery.verifiedRoles.size)

            // FIDES uses EAA type which doesn't map to any specific 602 role
            assertTrue(
                discovery.verifiedRoles.none { it.verified },
                "No roles should match since FIDES uses EAA service type",
            )
        }

    // -- Helper methods --

    private suspend fun extractFidesLabsCertBase64(): String {
        val tlXml = ctx.fetchUrl(FIDES_TL_URL)
        val tl = parser.parseFromString(tlXml)
        val fidesEntity =
            tl.trustedEntities.first {
                it.trustedEntityInformation.name
                    .first()
                    .value == "FIDES Labs"
            }
        return fidesEntity.trustedEntityServices
            .first()
            .serviceInformation.serviceDigitalIdentity.x509Certificates
            .first()
    }

    private suspend fun extractKvkCertBase64(): String {
        val tlXml = ctx.fetchUrl(FIDES_TL_URL)
        val tl = parser.parseFromString(tlXml)
        val kvkEntity =
            tl.trustedEntities.first {
                it.trustedEntityInformation.name
                    .first()
                    .value == "Kamer van Koophandel"
            }
        return kvkEntity.trustedEntityServices
            .first()
            .serviceInformation.serviceDigitalIdentity.x509Certificates
            .first()
    }

    private suspend fun createX509ValidationService(): X509ETSIValidationIdentifierResolutionServiceImpl =
        X509ETSIValidationIdentifierResolutionServiceImpl(
            execution = TestSessionExecution(createAnonymousSessionContext("fides-x509-test")),
            trustListResolvers = setOf(FidesTrustListResolver()),
            trustListParser = parser,
            x509VerifyService = PermissiveX509VerifyService(),
        )

    private suspend fun createRoleVerificationService(): LoTERoleVerificationServiceImpl =
        LoTERoleVerificationServiceImpl(
            execution = TestSessionExecution(createAnonymousSessionContext("fides-role-test")),
            trustListResolvers = setOf(FidesTrustListResolver()),
            trustListParser = parser,
        )

    /**
     * Resolver that returns FIDES test data fetched from GitHub.
     * Maps well-known FIDES URIs to the online XML files.
     */
    private inner class FidesTrustListResolver : TrustListResolver {
        private var cachedLotlXml: String? = null
        private var cachedTlXml: String? = null

        override fun getId(): String = "fides-test-resolver"

        override suspend fun resolve(
            uri: String,
            options: ResolutionOptions,
        ): TrustListData {
            val xml =
                when {
                    uri == FIDES_LOTL_URI -> {
                        if (cachedLotlXml == null) cachedLotlXml = ctx.fetchUrl(FIDES_LOTL_URL)
                        cachedLotlXml!!
                    }

                    uri == FIDES_TL_URI || uri.contains("FIDES-TL") -> {
                        if (cachedTlXml == null) cachedTlXml = ctx.fetchUrl(FIDES_TL_URL)
                        cachedTlXml!!
                    }

                    else -> {
                        throw IllegalArgumentException("Unknown URI for FIDES test resolver: $uri")
                    }
                }
            return TrustListData(
                data = xml.encodeToByteArray(),
                sourceUri = uri,
                contentType = "application/xml",
                fromCache = false,
                retrievedAt = Clock.System.now().toEpochMilliseconds(),
            )
        }

        override fun supports(uri: String): Boolean = uri == FIDES_LOTL_URI || uri == FIDES_TL_URI || uri.contains("FIDES-TL")
    }

    /**
     * X509VerifyService that accepts any chain as valid.
     * In test context we trust the FIDES sandbox certificates without full chain validation.
     */
    private class PermissiveX509VerifyService : X509VerifyService {
        override suspend fun verifyCertificateChain(req: X509VerificationRequestType): X509VerificationResultType =
            X509VerificationResult(
                certificateChain = emptyArray(),
                critical = false,
                message = "Test: chain accepted",
                error = false,
            )

        override fun setTrustedCerts(trustedCerts: Array<String>?): X509VerifyService = this

        override fun getTrustedCerts(): Array<String>? = null
    }

    private class TestSessionExecution(
        override val sessionContext: SessionContext,
    ) : SessionExecution {
        override val sessionContextManager: SessionContextManager
            get() = throw NotImplementedError()
        override val log: SessionLogService = TestSessionLogService(sessionContext)
        override val conf: ContextConfig = TestContextConfig()
    }

    private class TestContextConfig : ContextConfig {
        override val app: AppConfigService get() = throw NotImplementedError()
        override val tenant: TenantConfigService get() = throw NotImplementedError()
        override val principal: PrincipalConfigService get() = throw NotImplementedError()

        override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError()
    }

    private class TestSessionLogService(
        override val sessionContext: SessionContext = NoOpSessionContext,
    ) : SessionLogService {
        override val logManager: SessionLogManager = TestSessionLogManager(sessionContext)
        override val scope: IdkScope = IdkScope.SESSION
        override val id: String = "test-log"
        override val isEnabled: Boolean = false

        override suspend fun setConfig(config: LoggerConfig): SessionLogService = this

        override fun executeAsync(message: LogMessage) = IdkOkResult(Unit)

        override fun toAsync(): AsyncLogService = TestAsyncLogService(sessionContext)
    }

    private class TestSessionLogManager(
        private val sessionContext: SessionContext,
    ) : SessionLogManager {
        override suspend fun setGlobalConfig(config: LoggerConfig): SessionLogManager = this

        override suspend fun getGlobalConfig(): LoggerConfig = LoggerConfig.Default

        override fun withTagAsync(
            tag: String,
            config: LoggerConfig?,
        ): AsyncLogService = TestAsyncLogService(sessionContext)

        override fun withTag(
            tag: String,
            config: LoggerConfig?,
        ): SessionLogService = TestSessionLogService(sessionContext)
    }

    private class TestAsyncLogService(
        override val sessionContext: SessionContext = NoOpSessionContext,
    ) : AsyncLogService {
        override val scope: IdkScope = IdkScope.SESSION
        override val id: String = "test-async-log"
        override val isEnabled: Boolean = false

        override suspend fun setConfig(config: LoggerConfig): AsyncLogService = this

        override suspend fun execute(args: LogMessage) = IdkOkResult(Unit)

        override fun toSync(): SessionLogService = TestSessionLogService(sessionContext)
    }

    companion object {
        const val FIDES_LOTL_URI = "https://raw.githubusercontent.com/FIDEScommunity/fides-trust-list/refs/heads/main/FIDES-LOTL.xml"
        const val FIDES_TL_URI = "https://raw.githubusercontent.com/FIDEScommunity/fides-trust-list/refs/heads/main/FIDES-TL.xml"

        // A structurally valid Base64 string that does not match any certificate in the FIDES trust list
        const val DUMMY_CERT_BASE64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0Z3VS5JJcds3xfn/ygWep4PAtGoRBh" +
                "sFBALPCV1M2D5GEsFEYzBhMGPjz6j4c9TZZ0gKIhUAhhfgLJBaIDadMvY8ZDx4Hf7cfEkn50Gx" +
                "fj0KXOQg0v+IPpBK/5rWOEJBsb2t0gLCBE3r3y1x6vVHYwJ6E1L6IA9M/bm/dIVKQFhfk3J4H" +
                "osFnhkES1DGtOOhEgZAcas+Bpr9O0tYXhQ8JcHyMhHQFAQ=="
    }
}
