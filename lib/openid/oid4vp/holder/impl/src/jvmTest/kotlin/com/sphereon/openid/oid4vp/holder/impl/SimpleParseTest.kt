package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.core.defaults.app.staticMinimalTestAppComponent
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.MultiExternalIdentifierService
import com.sphereon.ktor.http.client.FetchRequestUriCommandImpl
import com.sphereon.ktor.http.client.ParseUriQueryCommandImpl
import com.sphereon.oauth2.client.JarService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Simple test to debug ParseAuthorizationRequestCommandImpl
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleParseTest {

    @Test
    fun `test basic parse`() = runTest {
        val app = staticMinimalTestAppComponent(this, "test-app", "test", "1.0.0")
        val user = app.userContextManager.getAnonymous()
        val session = user.sessionContextManager.getAnonymous()
        val execution = session.asCoreApiServiceComponent().serviceExecution

        // Create real instances
        val httpClientFactory = object : com.sphereon.ktor.http.client.provider.HttpClientFactory {
            override fun createClient(options: com.sphereon.ktor.http.client.provider.HttpClientOptions) =
                io.ktor.client.HttpClient()
            override fun isSupportedOptions(options: com.sphereon.ktor.http.client.provider.HttpClientOptions) = true
            override fun getEngineTypesSupported() = listOf(com.sphereon.ktor.http.client.provider.HttpClientEngineType.CIO)
            override fun getEngineTypeDefault() = com.sphereon.ktor.http.client.provider.HttpClientEngineType.CIO
        }

        val fetchCommand = FetchRequestUriCommandImpl(execution, httpClientFactory)
        val parseUriCommand = ParseUriQueryCommandImpl(execution)

        // Mock JAR service
        val mockJarService = object : JarService {
            override suspend fun createSignedJar(args: com.sphereon.oauth2.client.command.CreateSignedJarArgs) =
                com.sphereon.core.api.Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "not impl"))
            override suspend fun createEncryptedJar(args: com.sphereon.oauth2.client.command.CreateEncryptedJarArgs) =
                com.sphereon.core.api.Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "not impl"))
            override suspend fun parseJar(args: com.sphereon.oauth2.client.command.ParseJarArgs) =
                com.sphereon.core.api.Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "not impl"))
            override suspend fun mergeRequestObject(args: com.sphereon.oauth2.client.command.MergeRequestObjectArgs) =
                com.sphereon.core.api.Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "not impl"))
            override val commands: JarService.Commands
                get() = TODO("Not needed")
        }

        // Mock external identifier service
        val mockExternalIdentifierService = object : MultiExternalIdentifierService {
            override val supportedIdentifierMethods: List<IIdentifierMethod> = emptyList()
            override suspend fun isSupportedIdentifier(identifier: Any): Boolean = false
            override suspend fun isSupportedIdentifierMethod(identifierMethod: IIdentifierMethod): Boolean = false
            override suspend fun isSupportedOpts(opts: ExternalIdentifierOptsOrResult): Boolean = false
            override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierOpts, IdkErrorType> =
                IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(message = "Not implemented in test").asErrorResult()
            override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult, IdkErrorType> =
                IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(message = "Not implemented in test").asErrorResult()
        }

        val command = ParseAuthorizationRequestCommandImpl(
            execution = execution,
            parseUriQueryCommand = parseUriCommand,
            fetchRequestUriCommand = fetchCommand,
            jarService = mockJarService,
            httpClientFactory = httpClientFactory,
            externalIdentifierService = mockExternalIdentifierService
        )

        // Simple test - use longer nonce (min 8 chars)
        val result = command.parseAuthorizationRequest(
            requestUri = "openid4vp://?client_id=test&redirect_uri=https://example.com&response_type=vp_token&nonce=test123456",
            walletConfig = null
        )

        println("Result: $result")
        if (result is com.sphereon.core.api.Err) {
            println("ERROR: ${result.error.message.defaultMessage}")
        }

        assertTrue(result is Ok, "Result should be Ok")
        val request = (result as Ok).value
        assertEquals("test", request.clientId)
    }
}
