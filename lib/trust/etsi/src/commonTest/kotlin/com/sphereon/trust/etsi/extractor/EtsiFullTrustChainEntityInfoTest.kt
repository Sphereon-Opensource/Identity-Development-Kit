/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.etsi.extractor

import com.sphereon.core.api.IdkOkResult
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
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
import com.sphereon.trust.core.model.EntityDiscoveryOptions
import com.sphereon.trust.core.model.TrustAnchorType
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.model.toContact
import com.sphereon.trust.core.resolver.ResolutionOptions
import com.sphereon.trust.core.resolver.TrustListData
import com.sphereon.trust.core.resolver.TrustListResolver
import com.sphereon.trust.etsi.parser.StreamingETSITrustListParser
import com.sphereon.trust.etsi.resolution.ExternalIdentifierX509ETSIValidationOpts
import com.sphereon.trust.etsi.resolution.X509ETSIValidationIdentifierResolutionServiceImpl
import com.sphereon.trust.etsi.testutil.EtsiTestContext
import com.sphereon.trust.etsi.testutil.FIDES_LOTL_URL
import com.sphereon.trust.etsi.testutil.FIDES_TL_URL
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * End-to-end test: automatic full trust chain entity discovery.
 *
 * The user just passes `entityDiscovery = EntityDiscoveryOptions(enabled = true)` —
 * the validation service automatically builds the full chain with contact info:
 *   Cert → Issuing CA → TSP (trust anchor) → NL TL Operator → LOTL Operator
 */
class EtsiFullTrustChainEntityInfoTest {
    private val ctx = EtsiTestContext("full-chain-test", this)
    private val parser = StreamingETSITrustListParser()

    @Test
    fun automaticFullChainDiscoveryViaLotlNavigation() =
        runTest {
            val service = createValidationService()

            // Get FIDES Labs cert
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

            // Just enable entity discovery — everything else is automatic
            val opts =
                ExternalIdentifierX509ETSIValidationOpts(
                    identifier = KeyInfo<Nothing>(x5c = arrayOf(certBase64)),
                    lotlUri = FIDES_LOTL_URI,
                    entityDiscovery = EntityDiscoveryOptions(enabled = true, maxDepth = 0),
                )

            val result = service.execute(opts)

            assertTrue(result.isOk, "Validation should succeed: ${if (result.isErr) result.error else ""}")
            val validation = result.value
            assertTrue(validation.trusted, "Should be trusted: ${validation.details}")
            assertEquals(TrustStatus.TRUSTED, validation.trustStatus)

            // The embedded TrustValidationResult should have discovered entities
            val trustResult = validation.trustValidation
            assertNotNull(trustResult, "trustValidation should be populated when entityDiscovery is enabled")

            val entities = trustResult.discoveredEntities
            assertTrue(entities.isNotEmpty(), "Should have discovered entities")

            println("=== Automatic Full Trust Chain Discovery ===")
            println("Trusted: ${validation.trusted}")
            println("Matched TSP: ${validation.matchedTSP?.tspName}")
            println("Entities discovered: ${entities.size}")
            println()

            for (entity in entities) {
                val contact = entity.toContact()
                val anchor = if (entity.trustAnchor) " ** TRUST ANCHOR **" else ""
                println("[${entity.chainPosition.depth}] ${entity.chainPosition.role}$anchor")
                println("    Entity:       ${entity.entityIdentifier}")
                println("    Source:       ${entity.sourceType}")
                println("    Organization: ${contact.organizationName ?: "-"}")
                println("    Emails:       ${contact.emails.ifEmpty { listOf("-") }}")
                println("    URLs:         ${contact.urls.ifEmpty { listOf("-") }}")
                println("    Jurisdiction: ${contact.jurisdiction ?: "-"}")
                println("    Address:      ${contact.address?.let { listOfNotNull(it.streetAddress, it.locality, it.postalCode, it.countryName).joinToString(", ") } ?: "-"}")
                println("    Roles:        ${entity.roles.map { it.value }.ifEmpty { listOf("-") }}")
                println()
            }

            // At least: leaf cert + issuing CA + TSP + TL operator + LOTL operator = 5
            assertTrue(entities.size >= 4, "Should have at least 4 chain levels (cert, CA, TSP, TL operator), got ${entities.size}")

            // Exactly one trust anchor
            val trustAnchors = entities.filter { it.trustAnchor }
            assertEquals(1, trustAnchors.size, "Exactly one entity should be the trust anchor")
            val anchor = trustAnchors.first()
            assertEquals(TrustAnchorType.ETSI_TSL, anchor.sourceType, "Trust anchor should be from ETSI trust list")
            assertTrue(anchor.contacts.isNotEmpty(), "Trust anchor should have contacts")

            // First entity should be from X.509 (the leaf cert)
            assertEquals(TrustAnchorType.X509_CA_BUNDLE, entities.first().sourceType)

            // Last entity should be from ETSI (LOTL or TL operator)
            assertEquals(TrustAnchorType.ETSI_TSL, entities.last().sourceType)
        }

    // -- Infrastructure --

    private suspend fun createValidationService(): X509ETSIValidationIdentifierResolutionServiceImpl =
        X509ETSIValidationIdentifierResolutionServiceImpl(
            execution = TestSessionExecution(createAnonymousSessionContext("full-chain-test", "full-chain-test-correlation")),
            trustListResolvers = setOf(FidesTrustListResolver()),
            trustListParser = parser,
            x509VerifyService = PermissiveX509VerifyService(),
        )

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

                    uri.contains("FIDES-TL") -> {
                        if (cachedTlXml == null) cachedTlXml = ctx.fetchUrl(FIDES_TL_URL)
                        cachedTlXml!!
                    }

                    else -> {
                        throw IllegalArgumentException("Unknown URI: $uri")
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

        override fun supports(uri: String): Boolean = uri == FIDES_LOTL_URI || uri.contains("FIDES-TL")
    }

    private class PermissiveX509VerifyService : X509VerifyService {
        override suspend fun verifyCertificateChain(req: X509VerificationRequestType): X509VerificationResultType =
            X509VerificationResult(certificateChain = emptyArray(), critical = false, message = "Test: accepted", error = false)

        override fun setTrustedCerts(trustedCerts: Array<String>?): X509VerifyService = this

        override fun getTrustedCerts(): Array<String>? = null
    }

    private class TestSessionExecution(
        override val sessionContext: SessionContext,
    ) : SessionExecution {
        override val sessionContextManager: SessionContextManager get() = throw NotImplementedError()
        override val log: SessionLogService = TestLogService(sessionContext)
        override val conf: ContextConfig = TestContextConfig()
    }

    private class TestContextConfig : ContextConfig {
        override val app: AppConfigService get() = throw NotImplementedError()
        override val tenant: TenantConfigService get() = throw NotImplementedError()
        override val principal: PrincipalConfigService get() = throw NotImplementedError()

        override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError()
    }

    private class TestLogService(
        override val sessionContext: SessionContext = NoOpSessionContext,
    ) : SessionLogService {
        override val logManager: SessionLogManager = TestLogManager(sessionContext)
        override val scope: IdkScope = IdkScope.SESSION
        override val id: String = "test-log"
        override val isEnabled: Boolean = false

        override suspend fun setConfig(config: LoggerConfig): SessionLogService = this

        override fun executeAsync(message: LogMessage) = IdkOkResult(Unit)

        override fun toAsync(): AsyncLogService = TestAsyncLogService(sessionContext)
    }

    private class TestLogManager(
        private val ctx: SessionContext,
    ) : SessionLogManager {
        override suspend fun setGlobalConfig(config: LoggerConfig): SessionLogManager = this

        override suspend fun getGlobalConfig(): LoggerConfig = LoggerConfig.Default

        override fun withTagAsync(
            tag: String,
            config: LoggerConfig?,
        ): AsyncLogService = TestAsyncLogService(ctx)

        override fun withTag(
            tag: String,
            config: LoggerConfig?,
        ): SessionLogService = TestLogService(ctx)
    }

    private class TestAsyncLogService(
        override val sessionContext: SessionContext = NoOpSessionContext,
    ) : AsyncLogService {
        override val scope: IdkScope = IdkScope.SESSION
        override val id: String = "test-async-log"
        override val isEnabled: Boolean = false

        override suspend fun setConfig(config: LoggerConfig): AsyncLogService = this

        override suspend fun execute(args: LogMessage) = IdkOkResult(Unit)

        override fun toSync(): SessionLogService = TestLogService(sessionContext)
    }

    companion object {
        const val FIDES_LOTL_URI = "https://raw.githubusercontent.com/FIDEScommunity/fides-trust-list/refs/heads/main/FIDES-LOTL.xml"
    }
}
