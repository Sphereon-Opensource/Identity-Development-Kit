package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
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
    private val fakeDecryptJweCommand = FakeDecryptJweCommand()

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
        )
    private val credentialOfferCommand = GetCredentialOfferEndpointCommandImpl(execution, fakeOfferStore, FakeCredentialIssuanceSessionStore())
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
    private val metadataAdapter = Oid4vciIssuerMetadataHttpAdapter(execution, metadataCommand)
    private val protocolAdapter =
        Oid4vciIssuerProtocolHttpAdapter(
            execution,
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
