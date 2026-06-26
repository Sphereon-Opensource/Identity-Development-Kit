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
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwksUrlOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.JwksUrlExternalIdentifierResolutionService
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
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
        jwksResolver: CountingJwksResolver,
    ): DefaultIssuerJwksResolver = DefaultIssuerJwksResolver(metadata, jwksResolver, cacheManager)

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

    /**
     * Hand-written concrete double for the IDK identifier-resolution service the production
     * resolver now delegates JWKS fetching to. (mockk is JVM-only and unusable in commonTest,
     * so this is a real implementation of every [JwksUrlExternalIdentifierResolutionService]
     * member.) [respond] yields the raw [JwkSet] (or error) for a given `jwks_uri`; [resolve]
     * bridges that to an [ExternalIdentifierResult.JwksUrl] the same way the production impl
     * (JwksUrlExternalIdentifierResolutionServiceImpl) does.
     */
    private class CountingJwksResolver(
        private val respond: (String) -> IdkResult<JwkSet, IdkError>,
    ) : JwksUrlExternalIdentifierResolutionService {
        var calls = 0

        override val supportedIdentifierMethods: List<IIdentifierMethod> = listOf(IdentifierMethodDefaults.JWKS_URL)

        override suspend fun isSupportedIdentifier(identifier: Any): Boolean =
            identifier is String &&
                (identifier.startsWith("https://", ignoreCase = true) || identifier.startsWith("http://", ignoreCase = true)) &&
                identifier.contains("://")

        override suspend fun isSupportedIdentifierMethod(identifierMethod: IIdentifierMethod): Boolean = identifierMethod == IdentifierMethodDefaults.JWKS_URL

        override suspend fun isSupportedOpts(opts: ExternalIdentifierOptsOrResult): Boolean = opts is ExternalIdentifierJwksUrlOpts

        override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierOpts, IdkErrorType> =
            if (opts is ExternalIdentifierJwksUrlOpts) {
                Ok(opts)
            } else {
                Err(IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR())
            }

        @Suppress("UNCHECKED_CAST")
        override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult.JwksUrl, IdkErrorType> {
            calls += 1
            val jwksOpts = opts as ExternalIdentifierJwksUrlOpts
            val jwkSet = respond(jwksOpts.identifier).getOrElse { return Err(it) }
            val resolvedKeys =
                jwkSet.keys
                    .map { ResolvedKeyInfo.fromKey(it) as ResolvedKeyInfoType<JwkType> }
                    .toTypedArray()
            return Ok(
                ExternalIdentifierResult.JwksUrl(
                    identifierOpts = jwksOpts,
                    jwks = resolvedKeys,
                    keyInfo = resolvedKeys.first(),
                    jwksUrl = jwksOpts.identifier,
                    selectedKid = jwksOpts.lookup.kid,
                ),
            )
        }
    }

    @Test
    fun resolve_fetchesMetadataThenJwks() =
        runTest {
            val metadata = CountingFetchMetadata { Ok(sampleMetadata) }
            val jwksResolver = CountingJwksResolver { Ok(sampleJwks) }
            val resolver = newResolver(metadata, jwksResolver)
            resolver.invalidate(sampleMetadata.issuer)

            val result = resolver.resolve(sampleMetadata.issuer)

            assertTrue(result.isOk)
            assertEquals(sampleJwks, result.value)
            assertEquals(1, metadata.calls)
            assertEquals(1, jwksResolver.calls)
        }

    @Test
    fun resolve_cachedWithinTtl_noRefetch() =
        runTest {
            val metadata = CountingFetchMetadata { Ok(sampleMetadata) }
            val jwksResolver = CountingJwksResolver { Ok(sampleJwks) }
            val resolver = newResolver(metadata, jwksResolver)
            resolver.invalidate(sampleMetadata.issuer)

            resolver.resolve(sampleMetadata.issuer)
            resolver.resolve(sampleMetadata.issuer)
            resolver.resolve(sampleMetadata.issuer)

            assertEquals(1, metadata.calls, "metadata fetched once")
            assertEquals(1, jwksResolver.calls, "jwks fetched once")
        }

    @Test
    fun resolve_invalidateForcesRefetch() =
        runTest {
            val metadata = CountingFetchMetadata { Ok(sampleMetadata) }
            val jwksResolver = CountingJwksResolver { Ok(sampleJwks) }
            val resolver = newResolver(metadata, jwksResolver)
            resolver.invalidate(sampleMetadata.issuer)

            resolver.resolve(sampleMetadata.issuer)
            resolver.invalidate(sampleMetadata.issuer)
            resolver.resolve(sampleMetadata.issuer)

            assertEquals(2, metadata.calls)
            assertEquals(2, jwksResolver.calls)
        }

    @Test
    fun resolve_httpOnlyJwksUri_rejects() =
        runTest {
            val insecureMetadata = sampleMetadata.copy(jwksUri = "http://not-secure.example.com/jwks")
            val metadata = CountingFetchMetadata { Ok(insecureMetadata) }
            val jwksResolver = CountingJwksResolver { Ok(sampleJwks) }
            val resolver = newResolver(metadata, jwksResolver)
            resolver.invalidate(sampleMetadata.issuer)

            val result = resolver.resolve(insecureMetadata.issuer)

            assertTrue(result.isErr)
            assertTrue(result.error is MetadataError.InvalidUrl, "expected InvalidUrl, got ${result.error}")
            assertEquals(0, jwksResolver.calls, "must not fetch insecure JWKS URI")
        }

    @Test
    fun resolve_missingJwksUri_rejects() =
        runTest {
            val noJwks = sampleMetadata.copy(jwksUri = null)
            val metadata = CountingFetchMetadata { Ok(noJwks) }
            val jwksResolver = CountingJwksResolver { Ok(sampleJwks) }
            val resolver = newResolver(metadata, jwksResolver)
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
            val jwksResolver = CountingJwksResolver { Ok(sampleJwks) }
            val resolver = newResolver(metadata, jwksResolver)
            resolver.invalidate(sampleMetadata.issuer)

            val result = resolver.resolve(sampleMetadata.issuer)

            assertTrue(result.isErr)
            assertEquals(0, jwksResolver.calls)
        }
}
