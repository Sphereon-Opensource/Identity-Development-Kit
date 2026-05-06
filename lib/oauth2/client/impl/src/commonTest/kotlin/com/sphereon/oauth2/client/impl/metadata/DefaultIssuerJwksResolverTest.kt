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

package com.sphereon.oauth2.client.impl.metadata

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.cache.CacheManager
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
import com.sphereon.oauth2.client.command.FetchJwksArgs
import com.sphereon.oauth2.client.command.FetchJwksCommand
import com.sphereon.oauth2.client.command.FetchServerMetadataArgs
import com.sphereon.oauth2.client.testutil.createOAuth2ClientTestAppGraph
import com.sphereon.oauth2.common.error.MetadataError
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ContributesTo(scope = AppScope::class)
interface IssuerJwksResolverCacheTestGraph {
    val cacheManager: CacheManager
}

class DefaultIssuerJwksResolverTest {
    private val app = createOAuth2ClientTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("issuer-jwks-resolver-test")
    private val execution = session.asCoreApiServiceGraph().serviceExecution
    private val cacheManager = (app as IssuerJwksResolverCacheTestGraph).cacheManager

    private fun newResolver(
        metadata: CountingFetchMetadata,
        jwks: CountingFetchJwks,
    ): DefaultIssuerJwksResolver = DefaultIssuerJwksResolver(metadata, jwks, cacheManager)

    private val sampleJwks =
        JwkSet(
            keys =
                arrayOf(
                    Jwk(kty = JwaKeyType.RSA, kid = "key-1"),
                ),
        )

    private val sampleMetadata =
        AuthorizationServerMetadata(
            issuer = "https://issuer.example.com",
            authorizationEndpoint = "https://issuer.example.com/authorize",
            tokenEndpoint = "https://issuer.example.com/token",
            jwksUri = "https://issuer.example.com/.well-known/jwks.json",
        )

    private inner class CountingFetchMetadata(
        private val respond: (String) -> IdkResult<AuthorizationServerMetadata, IdkError>,
    ) : TypedServiceCommandAdapter<FetchServerMetadataArgs, AuthorizationServerMetadata, IdkError>(
            commandId = FetchAuthorizationServerMetadataCommand.COMMAND_ID,
            execution = execution,
            inputTypeToken = typeToken<FetchServerMetadataArgs>(),
            outputTypeToken = typeToken<AuthorizationServerMetadata>(),
        ),
        FetchAuthorizationServerMetadataCommand {
        var calls = 0
        override val commandId: String get() = FetchAuthorizationServerMetadataCommand.COMMAND_ID

        override suspend fun supports(args: Any): Boolean = args is FetchServerMetadataArgs

        override suspend fun doExecute(
            args: FetchServerMetadataArgs,
            applyDuring: (FetchServerMetadataArgs) -> FetchServerMetadataArgs,
        ): IdkResult<AuthorizationServerMetadata, IdkError> {
            calls += 1
            return respond(args.issuer)
        }
    }

    private inner class CountingFetchJwks(
        private val respond: (String) -> IdkResult<JwkSet, IdkError>,
    ) : TypedServiceCommandAdapter<FetchJwksArgs, JwkSet, IdkError>(
            commandId = FetchJwksCommand.COMMAND_ID,
            execution = execution,
            inputTypeToken = typeToken<FetchJwksArgs>(),
            outputTypeToken = typeToken<JwkSet>(),
        ),
        FetchJwksCommand {
        var calls = 0
        override val commandId: String get() = FetchJwksCommand.COMMAND_ID

        override suspend fun supports(args: Any): Boolean = args is FetchJwksArgs

        override suspend fun doExecute(
            args: FetchJwksArgs,
            applyDuring: (FetchJwksArgs) -> FetchJwksArgs,
        ): IdkResult<JwkSet, IdkError> {
            calls += 1
            return respond(args.jwksUri)
        }
    }

    @Test
    fun resolve_fetchesMetadataThenJwks() =
        runTest {
            val metadata = CountingFetchMetadata { Ok(sampleMetadata) }
            val jwks = CountingFetchJwks { Ok(sampleJwks) }
            val resolver = newResolver(metadata, jwks)
            resolver.invalidate(sampleMetadata.issuer)

            val result = resolver.resolve(sampleMetadata.issuer)

            assertTrue(result.isOk)
            assertEquals(sampleJwks, result.value)
            assertEquals(1, metadata.calls)
            assertEquals(1, jwks.calls)
        }

    @Test
    fun resolve_cachedWithinTtl_noRefetch() =
        runTest {
            val metadata = CountingFetchMetadata { Ok(sampleMetadata) }
            val jwks = CountingFetchJwks { Ok(sampleJwks) }
            val resolver = newResolver(metadata, jwks)
            resolver.invalidate(sampleMetadata.issuer)

            resolver.resolve(sampleMetadata.issuer)
            resolver.resolve(sampleMetadata.issuer)
            resolver.resolve(sampleMetadata.issuer)

            assertEquals(1, metadata.calls, "metadata fetched once")
            assertEquals(1, jwks.calls, "jwks fetched once")
        }

    @Test
    fun resolve_invalidateForcesRefetch() =
        runTest {
            val metadata = CountingFetchMetadata { Ok(sampleMetadata) }
            val jwks = CountingFetchJwks { Ok(sampleJwks) }
            val resolver = newResolver(metadata, jwks)
            resolver.invalidate(sampleMetadata.issuer)

            resolver.resolve(sampleMetadata.issuer)
            resolver.invalidate(sampleMetadata.issuer)
            resolver.resolve(sampleMetadata.issuer)

            assertEquals(2, metadata.calls)
            assertEquals(2, jwks.calls)
        }

    @Test
    fun resolve_httpOnlyJwksUri_rejects() =
        runTest {
            val insecureMetadata = sampleMetadata.copy(jwksUri = "http://not-secure.example.com/jwks")
            val metadata = CountingFetchMetadata { Ok(insecureMetadata) }
            val jwks = CountingFetchJwks { Ok(sampleJwks) }
            val resolver = newResolver(metadata, jwks)
            resolver.invalidate(sampleMetadata.issuer)

            val result = resolver.resolve(insecureMetadata.issuer)

            assertTrue(result.isErr)
            assertTrue(result.error is MetadataError.InvalidUrl, "expected InvalidUrl, got ${result.error}")
            assertEquals(0, jwks.calls, "must not fetch insecure JWKS URI")
        }

    @Test
    fun resolve_missingJwksUri_rejects() =
        runTest {
            val noJwks = sampleMetadata.copy(jwksUri = null)
            val metadata = CountingFetchMetadata { Ok(noJwks) }
            val jwks = CountingFetchJwks { Ok(sampleJwks) }
            val resolver = newResolver(metadata, jwks)
            resolver.invalidate(sampleMetadata.issuer)

            val result = resolver.resolve(noJwks.issuer)

            assertTrue(result.isErr)
            assertTrue(result.error is MetadataError.ValidationFailed)
        }

    @Test
    fun resolve_metadataFetchFails_propagatesError() =
        runTest {
            val metadata =
                CountingFetchMetadata {
                    Err(
                        IdkError(
                            code = "metadata_not_found",
                            message = IdkError.Message(i18nKey = "t", defaultMessage = "not found"),
                        ),
                    )
                }
            val jwks = CountingFetchJwks { Ok(sampleJwks) }
            val resolver = newResolver(metadata, jwks)
            resolver.invalidate(sampleMetadata.issuer)

            val result = resolver.resolve(sampleMetadata.issuer)

            assertTrue(result.isErr)
            assertEquals(0, jwks.calls)
        }
}
