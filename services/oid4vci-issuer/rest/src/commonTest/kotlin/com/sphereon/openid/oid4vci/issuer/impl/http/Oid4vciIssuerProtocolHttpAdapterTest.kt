package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.defaults.http.NoOpRoutableSlugLookup
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.openid.oid4vci.issuer.impl.http.command.DefaultOid4vciIssuerPublicUrlResolver
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetCredentialOfferEndpointCommandImpl
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetIssuerMetadataEndpointCommandImpl
import com.sphereon.openid.oid4vci.issuer.impl.http.command.HandleCredentialEndpointCommandImpl
import com.sphereon.openid.oid4vci.issuer.impl.http.command.HandleDeferredCredentialEndpointCommandImpl
import com.sphereon.openid.oid4vci.issuer.impl.http.command.HandleNotificationEndpointCommandImpl
import com.sphereon.openid.oid4vci.issuer.impl.http.command.IssueNonceEndpointCommandImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Oid4vciIssuerProtocolHttpAdapterTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    private val execution = TestSessionExecution()
    private val fakeConfigProvider = FakeOid4vciIssuerConfigProvider()
    private val fakeOfferStore = FakeCredentialOfferStore()
    private val fakeOfferSessionStore = FakeCredentialOfferSessionStore()
    private val rateLimiterClock =
        object : kotlin.time.Clock {
            override fun now(): kotlin.time.Instant = kotlin.time.Instant.parse("2026-05-15T12:00:00Z")
        }
    private val offerRateLimiter =
        com.sphereon.openid.oid4vci.issuer.impl.command
            .InMemoryOfferRateLimiter(rateLimiterClock)
    private val fakeDecryptJweCommand = FakeDecryptJweCommand()
    private val fakeCreateCredentialOffer = FakeCreateCredentialOfferCommand()

    // Fake service commands
    private val fakeBuildMetadata = FakeBuildIssuerMetadataCommand()
    private val fakeBuildSignedMetadata = FakeBuildSignedIssuerMetadataCommand()
    private val fakeIssueNonce = FakeIssueNonceCommand()
    private val fakeHandleCredential = FakeHandleCredentialRequestCommand()
    private val fakeHandleDeferred = FakeHandleDeferredCredentialRequestCommand()
    private val fakeHandleNotification = FakeHandleNotificationCommand()

    // Real endpoint commands with fake dependencies
    private val fakeRestConfigProvider =
        object : com.sphereon.openid.oid4vci.rest.Oid4vciRestConfigProvider {
            override fun getConfig() =
                com.sphereon.openid.oid4vci.rest
                    .Oid4vciRestConfig(externalBaseUrl = null)
        }
    private val metadataCommand =
        GetIssuerMetadataEndpointCommandImpl(
            execution,
            fakeBuildMetadata,
            fakeBuildSignedMetadata,
            fakeConfigProvider,
            fakeRestConfigProvider,
            FakeMultiManagedIdentifierService,
            DefaultOid4vciIssuerPublicUrlResolver(NoOpAppConfigService),
        )
    private val credentialOfferCommand =
        GetCredentialOfferEndpointCommandImpl(
            execution,
            fakeOfferStore,
            FakeCredentialIssuanceSessionStore(),
            fakeOfferSessionStore,
            offerRateLimiter,
            fakeCreateCredentialOffer,
        )
    private val nonceCommand = IssueNonceEndpointCommandImpl(execution, fakeIssueNonce)

    // Real CredentialResponseEncryptor wired with a JweService that throws — none of the
    // happy-path tests in this file exercise credential_response_encryption, so the encryptor
    // stays on its `Plain` short-circuit and never reaches JweService. Tests that DO exercise
    // encryption (e.g. encryption_required violation) use the credentialCommand fixture
    // unchanged because the violation rejection happens before the encryptor is invoked.
    private val credentialResponseEncryptor =
        com.sphereon.openid.oid4vci.issuer.impl.encryption
            .CredentialResponseEncryptor(
                jweService = ThrowingJweService,
                configProvider = fakeConfigProvider,
            )
    private val credentialCommand =
        HandleCredentialEndpointCommandImpl(
            execution,
            fakeHandleCredential,
            fakeDecryptJweCommand,
            fakeConfigProvider,
            fakeRestConfigProvider,
            DefaultOid4vciIssuerPublicUrlResolver(NoOpAppConfigService),
            credentialResponseEncryptor,
        )
    private val deferredCommand =
        HandleDeferredCredentialEndpointCommandImpl(
            execution,
            fakeHandleDeferred,
            fakeDecryptJweCommand,
            credentialResponseEncryptor,
            fakeConfigProvider,
        )
    private val notificationCommand = HandleNotificationEndpointCommandImpl(execution, fakeHandleNotification, fakeConfigProvider)

    // Adapters
    private val metadataAdapter =
        Oid4vciIssuerMetadataHttpAdapter(
            execution,
            NoOpRoutableSlugLookup(),
            testTenantIdProvider(),
            metadataCommand,
        )
    private val protocolAdapter =
        Oid4vciIssuerProtocolHttpAdapter(
            execution,
            NoOpRoutableSlugLookup(),
            testTenantIdProvider(),
            NoOpAppConfigService,
            credentialOfferCommand,
            nonceCommand,
            credentialCommand,
            deferredCommand,
            notificationCommand,
        )

    // ========================================================================
    // 1. Metadata endpoint
    // ========================================================================

    @Test
    fun metadataEndpointReturns200WithMetadata() =
        runTest {
            val metadata =
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example.com",
                    credentialEndpoint = "https://issuer.example.com/oid4vci/credential",
                    credentialConfigurationsSupported =
                        mapOf(
                            "TestCred" to CredentialConfigurationSupported(format = "jwt_vc_json"),
                        ),
                )
            fakeBuildMetadata.result = Ok(metadata)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/openid-credential-issuer",
                    headers = mapOf("host" to "issuer.example.com"),
                )

            val response = metadataAdapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            assertNotNull(response.body)
            val body = json.parseToJsonElement(response.body!!)
            assertEquals(
                "https://issuer.example.com",
                body.jsonObject["credential_issuer"]?.jsonPrimitive?.content,
            )
        }

    @Test
    fun pathBearingMetadataEndpointReturns200WithoutTenantResolution() =
        runTest {
            val pathConfigProvider =
                FakeOid4vciIssuerConfigProvider(
                    issuerIdentifier = "http://localhost:18084/oid4vci",
                )
            val buildMetadata = FakeBuildIssuerMetadataCommand()
            val buildSignedMetadata = FakeBuildSignedIssuerMetadataCommand()
            buildMetadata.result =
                Ok(
                    CredentialIssuerMetadata(
                        credentialIssuer = "http://localhost:18084/oid4vci",
                        credentialEndpoint = "http://localhost:18084/oid4vci/credential",
                        credentialConfigurationsSupported =
                            mapOf(
                                "TestCred" to CredentialConfigurationSupported(format = "jwt_vc_json"),
                            ),
                    ),
                )
            val command =
                GetIssuerMetadataEndpointCommandImpl(
                    execution,
                    buildMetadata,
                    buildSignedMetadata,
                    pathConfigProvider,
                    fakeRestConfigProvider,
                    FakeMultiManagedIdentifierService,
                    DefaultOid4vciIssuerPublicUrlResolver(NoOpAppConfigService),
                )
            val adapter =
                Oid4vciIssuerMetadataHttpAdapter(
                    execution,
                    NoOpRoutableSlugLookup(),
                    testTenantIdProvider(),
                    command,
                )
            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/openid-credential-issuer/oid4vci",
                    headers = mapOf("host" to "localhost:18084"),
                )

            val response = adapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            assertEquals("http://localhost:18084/oid4vci", buildMetadata.lastArgs?.issuerIdentifier)
            assertEquals("http://localhost:18084/oid4vci", buildMetadata.lastArgs?.baseUrl)
        }

    // ========================================================================
    // 2. Credential offer endpoint
    // ========================================================================

    @Test
    fun credentialOfferReturns200WhenFound() =
        runTest {
            val offer =
                CredentialOffer(
                    credentialIssuer = "https://issuer.example.com",
                    credentialConfigurationIds = listOf("TestCred"),
                )
            fakeOfferStore.putOffer("offer-123", offer)

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vci/credentials/offers/offer-123",
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            assertNotNull(response.body)
            val body = json.parseToJsonElement(response.body!!)
            assertEquals(
                "https://issuer.example.com",
                body.jsonObject["credential_issuer"]?.jsonPrimitive?.content,
            )
        }

    @Test
    fun credentialOfferReturns404WhenNotFound() =
        runTest {
            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vci/credentials/offers/nonexistent",
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(404, response.statusCode)
        }

    @Test
    fun singleUseOfferReturns200WithoutNoStoreCacheControl() =
        runTest {
            val offer =
                CredentialOffer(
                    credentialIssuer = "https://issuer.example.com",
                    credentialConfigurationIds = listOf("TestCred"),
                )
            fakeOfferStore.putOffer("single-use-offer", offer)
            fakeOfferSessionStore.putSession(
                singleUseSession(offerId = "single-use-offer"),
            )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vci/credentials/offers/single-use-offer",
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            assertNotNull(response.body)
            // SINGLE_USE keeps the pre-Task-4.3 behaviour: no reusable no-store cache directive.
            assertEquals("application/json", response.headers["Content-Type"])
            assertTrue(response.headers["Cache-Control"] != "no-store, no-cache, must-revalidate")
        }

    @Test
    fun reusableOfferOverRateLimitReturns429() =
        runTest {
            val offer =
                CredentialOffer(
                    credentialIssuer = "https://issuer.example.com",
                    credentialConfigurationIds = listOf("TestCred"),
                )
            fakeOfferStore.putOffer("reusable-offer", offer)
            fakeOfferSessionStore.putSession(
                reusableSession(
                    offerId = "reusable-offer",
                    rateLimit =
                        com.sphereon.openid.oid4vci.issuer.command
                            .OfferRateLimit(maxPerWindow = 1, windowSeconds = 60),
                ),
            )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vci/credentials/offers/reusable-offer",
                )

            // First fetch consumes the only slot in the window.
            protocolAdapter.handleRequest(request)
            // Second fetch is over the limit.
            val response = protocolAdapter.handleRequest(request)

            assertEquals(429, response.statusCode)
            assertEquals("no-store, no-cache, must-revalidate", response.headers["Cache-Control"])
        }

    @Test
    fun reusableOfferWithinRateLimitMintsFreshOfferPerFetch() =
        runTest {
            val offer =
                CredentialOffer(
                    credentialIssuer = "https://issuer.example.com",
                    credentialConfigurationIds = listOf("TestCred"),
                )
            fakeOfferStore.putOffer("reusable-offer-2", offer)
            fakeOfferSessionStore.putSession(
                reusableSession(
                    offerId = "reusable-offer-2",
                    rateLimit =
                        com.sphereon.openid.oid4vci.issuer.command
                            .OfferRateLimit(maxPerWindow = 5, windowSeconds = 60),
                ),
            )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/oid4vci/credentials/offers/reusable-offer-2",
                )

            val first = protocolAdapter.handleRequest(request)
            val second = protocolAdapter.handleRequest(request)

            // Both fetches succeed with the reusable no-store cache directive.
            assertEquals(200, first.statusCode)
            assertEquals(200, second.statusCode)
            assertEquals("no-store, no-cache, must-revalidate", first.headers["Cache-Control"])
            assertEquals("no-store, no-cache, must-revalidate", second.headers["Cache-Control"])

            // The fresh-per-fetch property: each GET mints a brand-new inner offer. The protocol
            // CredentialOffer wire object carries no correlation_id, so the per-mint distinguishing
            // value is the freshly registered pre-authorized code inside `grants`.
            assertNotNull(first.body)
            assertNotNull(second.body)
            val firstCode = preAuthorizedCodeOf(first.body!!)
            val secondCode = preAuthorizedCodeOf(second.body!!)
            assertNotNull(firstCode)
            assertNotNull(secondCode)
            assertTrue(firstCode != secondCode, "two GETs on the same reusable URI must mint distinct inner offers")
        }

    private fun preAuthorizedCodeOf(body: String): String? =
        json
            .parseToJsonElement(body)
            .jsonObject["grants"]
            ?.jsonObject
            ?.get("urn:ietf:params:oauth:grant-type:pre-authorized_code")
            ?.jsonObject
            ?.get("pre-authorized_code")
            ?.jsonPrimitive
            ?.content

    private fun singleUseSession(offerId: String) =
        com.sphereon.openid.oid4vci.rest.CredentialOfferSession(
            correlationId = "corr-$offerId",
            offerId = offerId,
            status = com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStatus.CREDENTIAL_OFFER_CREATED,
            createdAt = 0L,
            lastUpdatedAt = 0L,
            uriLifecycle = com.sphereon.openid.oid4vci.issuer.command.OfferUriLifecycle.SINGLE_USE,
        )

    private fun reusableSession(
        offerId: String,
        rateLimit: com.sphereon.openid.oid4vci.issuer.command.OfferRateLimit,
    ) = com.sphereon.openid.oid4vci.rest.CredentialOfferSession(
        correlationId = "corr-$offerId",
        offerId = offerId,
        status = com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStatus.CREDENTIAL_OFFER_CREATED,
        createdAt = 0L,
        lastUpdatedAt = 0L,
        uriLifecycle = com.sphereon.openid.oid4vci.issuer.command.OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH,
        rateLimit = rateLimit,
        offerTemplate =
            com.sphereon.openid.oid4vci.rest.CredentialOfferTemplate(
                issuerId = "https://issuer.example.com",
                credentialConfigurationIds = listOf("TestCred"),
                preAuthorizedCodeGrant = true,
            ),
    )

    // ========================================================================
    // 3. Nonce endpoint
    // ========================================================================

    @Test
    fun nonceEndpointReturns200() =
        runTest {
            fakeIssueNonce.result =
                Ok(
                    NonceResponse(cNonce = "test-nonce-value", cNonceExpiresIn = 300),
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vci/nonce",
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            assertNotNull(response.body)
            val body = json.parseToJsonElement(response.body!!)
            assertEquals("test-nonce-value", body.jsonObject["c_nonce"]?.jsonPrimitive?.content)
        }

    @Test
    fun nonceEndpointIncludesCacheControlNoStore() =
        runTest {
            fakeIssueNonce.result =
                Ok(
                    NonceResponse(cNonce = "cache-test-nonce", cNonceExpiresIn = 300),
                )

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vci/nonce",
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            assertEquals("no-store", response.headers["Cache-Control"])
        }

    // ========================================================================
    // 4. Credential endpoint - success
    // ========================================================================

    @Test
    fun credentialEndpointReturns200OnSuccess() =
        runTest {
            fakeHandleCredential.result =
                Ok(
                    CredentialResponse(
                        credentials = listOf(CredentialResponseItem(credential = JsonPrimitive("eyJ.test.credential"))),
                    ),
                )

            val requestBody = """{"credential_configuration_id": "TestCred", "proofs": {"jwt": ["eyJ..."]}}"""

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vci/credential",
                    headers = mapOf("Authorization" to "Bearer test-token"),
                    bodySupplier = { requestBody },
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            assertNotNull(response.body)
            val body = json.parseToJsonElement(response.body!!)
            assertNotNull(body.jsonObject["credentials"])

            assertNotNull(fakeHandleCredential.lastArgs)
            assertEquals("test-token", fakeHandleCredential.lastArgs!!.accessToken)
        }

    @Test
    fun credentialEndpointPreparesConfigProviderBeforeReadingCredentialConfigurations() =
        runTest {
            val preparedConfigId = "PreparedCred"
            val preparingConfigProvider =
                FakeOid4vciIssuerConfigProvider(
                    initialCredentialConfigurations = emptyMap(),
                    preparedCredentialConfigurations =
                        mapOf(
                            preparedConfigId to CredentialConfigurationSupported(format = "jwt_vc_json"),
                        ),
                )
            val handleCredential = FakeHandleCredentialRequestCommand()
            handleCredential.result =
                Ok(
                    CredentialResponse(
                        credentials = listOf(CredentialResponseItem(credential = JsonPrimitive("eyJ.prepared.credential"))),
                    ),
                )
            val encryptor =
                com.sphereon.openid.oid4vci.issuer.impl.encryption.CredentialResponseEncryptor(
                    jweService = ThrowingJweService,
                    configProvider = preparingConfigProvider,
                )
            val command =
                HandleCredentialEndpointCommandImpl(
                    execution,
                    handleCredential,
                    fakeDecryptJweCommand,
                    preparingConfigProvider,
                    fakeRestConfigProvider,
                    DefaultOid4vciIssuerPublicUrlResolver(NoOpAppConfigService),
                    encryptor,
                )

            val result =
                command.execute(
                    GenericHttpRequest(
                        method = "POST",
                        path = "/credential",
                        headers = mapOf("Authorization" to "Bearer test-token"),
                        bodySupplier = {
                            """{"credential_configuration_id": "$preparedConfigId", "proofs": {"jwt": ["eyJ..."]}}"""
                        },
                    ),
                )

            assertTrue(result.isOk, "credential endpoint should succeed with prepared provider: ${result.errorOrNull()}")
            assertEquals(200, result.value.statusCode)
            assertEquals(1, preparingConfigProvider.prepareCount)
            assertTrue(
                handleCredential.lastArgs?.credentialConfigurations?.containsKey(preparedConfigId) == true,
                "credential endpoint must pass the prepared configuration snapshot",
            )
        }

    // ========================================================================
    // 5. Credential endpoint - missing bearer token
    // ========================================================================

    @Test
    fun credentialEndpointReturns401WithoutBearerToken() =
        runTest {
            val requestBody = """{"credential_configuration_id": "TestCred"}"""

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vci/credential",
                    headers = emptyMap(),
                    bodySupplier = { requestBody },
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(401, response.statusCode)
            assertNotNull(response.body)
            val body = json.parseToJsonElement(response.body!!)
            assertEquals(Oid4vciErrors.INVALID_TOKEN, body.jsonObject["error"]?.jsonPrimitive?.content)
        }

    // ========================================================================
    // 6. Deferred credential endpoint - success (credential ready)
    // ========================================================================

    @Test
    fun deferredCredentialEndpointReturns200WhenReady() =
        runTest {
            fakeHandleDeferred.result =
                Ok(
                    CredentialResponse(
                        credentials = listOf(CredentialResponseItem(credential = JsonPrimitive("eyJ.deferred.credential"))),
                    ),
                )

            val requestBody = """{"transaction_id": "txn-123"}"""

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vci/deferredCredential",
                    headers = mapOf("Authorization" to "Bearer test-token"),
                    bodySupplier = { requestBody },
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(200, response.statusCode)
            assertNotNull(response.body)
            val body = json.parseToJsonElement(response.body!!)
            assertNotNull(body.jsonObject["credentials"])

            assertNotNull(fakeHandleDeferred.lastArgs)
            assertEquals("test-token", fakeHandleDeferred.lastArgs!!.accessToken)
            assertEquals("txn-123", fakeHandleDeferred.lastArgs!!.deferredRequest.transactionId)
        }

    // ========================================================================
    // 7. Deferred credential endpoint - issuance pending
    // ========================================================================

    @Test
    fun deferredCredentialEndpointReturns202WhenPending() =
        runTest {
            fakeHandleDeferred.result =
                Ok(
                    CredentialResponse(
                        transactionId = "txn-456",
                        interval = 5,
                    ),
                )

            val requestBody = """{"transaction_id": "txn-456"}"""

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vci/deferredCredential",
                    headers = mapOf("Authorization" to "Bearer test-token"),
                    bodySupplier = { requestBody },
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(202, response.statusCode)
            assertNotNull(response.body)
            val body = json.parseToJsonElement(response.body!!)
            assertEquals("txn-456", body.jsonObject["transaction_id"]?.jsonPrimitive?.content)
            assertNotNull(body.jsonObject["interval"])
            assertNull(body.jsonObject["error"])
        }

    // ========================================================================
    // 8. Notification endpoint - success
    // ========================================================================

    @Test
    fun notificationEndpointReturns204OnSuccess() =
        runTest {
            fakeHandleNotification.result = Ok(Unit)

            val requestBody = """{"notification_id": "notif-1", "event": "credential_accepted"}"""

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vci/notification",
                    headers = mapOf("Authorization" to "Bearer test-token"),
                    bodySupplier = { requestBody },
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(204, response.statusCode)
            assertNull(response.body)

            assertNotNull(fakeHandleNotification.lastArgs)
            assertEquals("test-token", fakeHandleNotification.lastArgs!!.accessToken)
            assertEquals("notif-1", fakeHandleNotification.lastArgs!!.notification.notificationId)
            assertEquals(CredentialNotificationEvent.CREDENTIAL_ACCEPTED, fakeHandleNotification.lastArgs!!.notification.event)
        }

    // ========================================================================
    // 9. Notification endpoint - missing bearer token
    // ========================================================================

    @Test
    fun notificationEndpointReturns401WithoutBearerToken() =
        runTest {
            val requestBody = """{"notification_id": "notif-1", "event": "credential_accepted"}"""

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vci/notification",
                    headers = emptyMap(),
                    bodySupplier = { requestBody },
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(401, response.statusCode)
            assertNotNull(response.body)
            val body = json.parseToJsonElement(response.body!!)
            assertEquals(Oid4vciErrors.INVALID_TOKEN, body.jsonObject["error"]?.jsonPrimitive?.content)
        }

    // ========================================================================
    // 10. Credential endpoint - service error
    // ========================================================================

    @Test
    fun credentialEndpointReturns400OnServiceError() =
        runTest {
            fakeHandleCredential.result =
                Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid credential request"),
                )

            val requestBody = """{"credential_configuration_id": "TestCred"}"""

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/oid4vci/credential",
                    headers = mapOf("Authorization" to "Bearer test-token"),
                    bodySupplier = { requestBody },
                )

            val response = protocolAdapter.handleRequest(request)

            assertEquals(400, response.statusCode)
            assertNotNull(response.body)
            val body = json.parseToJsonElement(response.body!!)
            assertEquals(
                Oid4vciErrors.INVALID_CREDENTIAL_REQUEST,
                body.jsonObject["error"]?.jsonPrimitive?.content,
            )
            assertTrue(
                body.jsonObject["error_description"]
                    ?.jsonPrimitive
                    ?.content
                    ?.contains("Invalid credential request") == true,
            )
        }

    /**
     * JweService stand-in that throws on every method. None of the happy-path tests in this file
     * exercise credential_response_encryption, so the encryptor's `Plain` short-circuit always
     * fires and the JWE pipeline is never reached. Throwing makes any accidental test that DOES
     * trigger the encrypt path loud rather than silently producing fake JWE bytes.
     */
    private object ThrowingJweService : com.sphereon.crypto.jose.jwe.JweService {
        override val commands: com.sphereon.crypto.jose.jwe.JweService.Commands
            get() = throw UnsupportedOperationException("not used in HTTP-adapter tests")

        override suspend fun prepareJwe(args: com.sphereon.crypto.jose.jwe.PrepareJweArgs) = throw UnsupportedOperationException("ThrowingJweService.prepareJwe")

        override suspend fun createJweCompact(args: com.sphereon.crypto.jose.jwe.CreateJweCompactArgs) = throw UnsupportedOperationException("ThrowingJweService.createJweCompact")

        override suspend fun createJweJsonFlattened(args: com.sphereon.crypto.jose.jwe.CreateJweJsonArgs) = throw UnsupportedOperationException("ThrowingJweService.createJweJsonFlattened")

        override suspend fun createJweJsonGeneral(args: com.sphereon.crypto.jose.jwe.CreateJweJsonGeneralArgs) = throw UnsupportedOperationException("ThrowingJweService.createJweJsonGeneral")

        override suspend fun decryptJwe(args: com.sphereon.crypto.jose.jwe.DecryptJweArgs) = throw UnsupportedOperationException("ThrowingJweService.decryptJwe")
    }
}
