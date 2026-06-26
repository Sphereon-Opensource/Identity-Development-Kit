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
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
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
import com.sphereon.oauth2.client.command.FetchJwksArgs
import com.sphereon.oauth2.client.testutil.createOAuth2ClientTestAppGraph
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Direct coverage for [FetchJwksCommandImpl], which delegates its JWKS fetch to the IDK
 * identifier-resolution system. The command is constructed directly with a real [SessionExecution]
 * (from the test app graph) and a hand-written [CountingJwksResolver] double, then invoked through
 * its public `execute` entry point exactly the way MetadataServiceImpl does.
 *
 * The command's public result is `IdkResult<JwkSet, IdkError>`: `doExecute` runs `fetchJwksInternal`
 * and maps the internal [MetadataError] to an [IdkError] via `IdkError.fromDTO`, which PRESERVES the
 * error `code`. We therefore assert on `result.error.code` against the [MetadataError] codes
 * (`metadata_invalid_url`, `metadata_fetch_failed`, `metadata_validation_failed`).
 */
class FetchJwksCommandImplTest {
    private val app = createOAuth2ClientTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("fetch-jwks-command-test")
    private val execution = session.asCoreApiServiceGraph().serviceExecution

    /**
     * Hand-written concrete double for the IDK identifier-resolution service the production command
     * now delegates JWKS fetching to. (mockk is JVM-only and unusable in commonTest, so this is a
     * real implementation of every [JwksUrlExternalIdentifierResolutionService] member.) [respond]
     * yields the raw [JwkSet] (or error) for a given `jwks_uri`; [resolve] bridges that to an
     * [ExternalIdentifierResult.JwksUrl] the same way the production resolver does.
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
            // [keyInfo] is required non-null even when the set is empty (the empty case exercises
            // the production ValidationFailed branch, which only inspects [jwks]). Use a throwaway
            // dummy key for [keyInfo] when there are no resolved keys.
            val keyInfo: ResolvedKeyInfoType<JwkType> =
                resolvedKeys.firstOrNull()
                    ?: (ResolvedKeyInfo.fromKey(Jwk(kty = JwaKeyType.RSA, kid = "dummy")) as ResolvedKeyInfoType<JwkType>)
            return Ok(
                ExternalIdentifierResult.JwksUrl(
                    identifierOpts = jwksOpts,
                    jwks = resolvedKeys,
                    keyInfo = keyInfo,
                    jwksUrl = jwksOpts.identifier,
                    selectedKid = jwksOpts.lookup.kid,
                ),
            )
        }
    }

    private val twoKeyJwks =
        JwkSet(
            keys =
                arrayOf(
                    Jwk(kty = JwaKeyType.RSA, kid = "key-1"),
                    Jwk(kty = JwaKeyType.RSA, kid = "key-2"),
                ),
        )

    private fun newCommand(jwksResolver: CountingJwksResolver): FetchJwksCommandImpl = FetchJwksCommandImpl(execution, jwksResolver)

    @Test
    fun execute_happyPath_returnsFullMultiKeySet() =
        runTest {
            val jwksResolver = CountingJwksResolver { Ok(twoKeyJwks) }
            val command = newCommand(jwksResolver)

            val result = command.execute(FetchJwksArgs("https://issuer.example.com/.well-known/jwks.json"))

            assertTrue(result.isOk, "expected Ok, got $result")
            // The bridge keeps EVERY resolved key, not just a selected one.
            assertEquals(2, result.value.keys.size, "expected the full 2-key set")
            assertEquals(1, jwksResolver.calls)
        }

    @Test
    fun execute_resolverError_mapsToFetchFailed() =
        runTest {
            val jwksResolver =
                CountingJwksResolver {
                    Err(
                        IdkError(
                            code = "jwks_unreachable",
                            message = IdkError.Message(i18nKey = "t", defaultMessage = "could not reach jwks_uri"),
                        ),
                    )
                }
            val command = newCommand(jwksResolver)

            val result = command.execute(FetchJwksArgs("https://issuer.example.com/.well-known/jwks.json"))

            assertTrue(result.isErr, "expected Err, got $result")
            // fetchJwksInternal wraps the resolver Err in MetadataError.FetchFailed; fromDTO preserves the code.
            assertEquals("metadata_fetch_failed", result.error.code, "expected FetchFailed, got ${result.error}")
            assertEquals(1, jwksResolver.calls, "resolver should have been consulted")
        }

    @Test
    fun execute_insecureHttpJwksUri_rejectedAsInvalidUrlBeforeResolver() =
        runTest {
            val jwksResolver = CountingJwksResolver { Ok(twoKeyJwks) }
            val command = newCommand(jwksResolver)

            val result = command.execute(FetchJwksArgs("http://not-secure.example.com/jwks"))

            assertTrue(result.isErr, "expected Err, got $result")
            // The HTTPS guard fires BEFORE the resolver, so it is never consulted.
            assertEquals("metadata_invalid_url", result.error.code, "expected InvalidUrl, got ${result.error}")
            assertEquals(0, jwksResolver.calls, "resolver must not run for an insecure jwks_uri")
        }

    @Test
    fun execute_emptyResolvedKeySet_mapsToValidationFailed() =
        runTest {
            val jwksResolver = CountingJwksResolver { Ok(JwkSet(keys = emptyArray())) }
            val command = newCommand(jwksResolver)

            val result = command.execute(FetchJwksArgs("https://issuer.example.com/.well-known/jwks.json"))

            assertTrue(result.isErr, "expected Err, got $result")
            assertEquals("metadata_validation_failed", result.error.code, "expected ValidationFailed, got ${result.error}")
            assertEquals(1, jwksResolver.calls)
        }
}
