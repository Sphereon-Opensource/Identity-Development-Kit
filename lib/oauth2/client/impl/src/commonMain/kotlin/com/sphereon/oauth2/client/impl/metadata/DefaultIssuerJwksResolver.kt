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
import com.sphereon.core.api.cache.CacheManager
import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheSerializers
import com.sphereon.core.api.cache.ScopedCache
import com.sphereon.core.api.validation.ValidationErrorDetail
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
import com.sphereon.oauth2.client.command.FetchJwksArgs
import com.sphereon.oauth2.client.command.FetchJwksCommand
import com.sphereon.oauth2.client.command.FetchServerMetadataArgs
import com.sphereon.oauth2.client.metadata.IssuerJwksResolver
import com.sphereon.oauth2.client.util.isSecureUrl
import com.sphereon.oauth2.common.error.MetadataError
import com.sphereon.oauth2.common.error.Oauth2Error
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Default [IssuerJwksResolver] — wraps [FetchAuthorizationServerMetadataCommand] and
 * [FetchJwksCommand]. Caches JWKS per issuer through the IDK [CacheManager] abstraction so the
 * cache participates in app-wide eviction, statistics, and distributed-backend policy if the
 * deployment configures one. `jwks_uri` must be HTTPS.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<IssuerJwksResolver>())
public class DefaultIssuerJwksResolver(
    private val fetchMetadataCommand: FetchAuthorizationServerMetadataCommand,
    private val fetchJwksCommand: FetchJwksCommand,
    private val cacheManager: CacheManager,
) : IssuerJwksResolver {
    private val cache: ScopedCache<String, JwkSet> by lazy {
        cacheManager.getCache<String, JwkSet>(CACHE_NAMESPACE)
            ?: cacheManager.createStringCache(
                CacheRequirements.localOnly(CACHE_NAMESPACE),
                CacheSerializers.json(JwkSet.serializer()),
            )
    }

    override suspend fun resolve(issuer: String): IdkResult<JwkSet, Oauth2Error> {
        cache.getApp(issuer)?.let { return Ok(it) }

        val metadataResult = fetchMetadataCommand.execute(FetchServerMetadataArgs(issuer))
        if (metadataResult.isErr) {
            return Err(
                MetadataError.FetchFailed(
                    url = issuer,
                    reason = metadataResult.error.message.defaultMessage ?: metadataResult.error.code,
                ),
            )
        }
        return resolve(metadataResult.value)
    }

    override suspend fun resolve(metadata: AuthorizationServerMetadata): IdkResult<JwkSet, Oauth2Error> {
        cache.getApp(metadata.issuer)?.let { return Ok(it) }

        val jwksUri =
            metadata.jwksUri
                ?: return Err(
                    MetadataError.ValidationFailed(
                        url = metadata.issuer,
                        details =
                            listOf(
                                ValidationErrorDetail(
                                    path = "jwks_uri",
                                    message = "Authorization server metadata must include jwks_uri for OIDC ID token validation",
                                ),
                            ),
                    ),
                )

        if (!isSecureUrl(jwksUri)) {
            return Err(
                MetadataError.InvalidUrl(
                    url = jwksUri,
                    reason = "jwks_uri must be an HTTPS URL (HTTP only allowed for localhost)",
                ),
            )
        }

        val jwksResult = fetchJwksCommand.execute(FetchJwksArgs(jwksUri))
        if (jwksResult.isErr) {
            return Err(
                MetadataError.FetchFailed(
                    url = jwksUri,
                    reason = jwksResult.error.message.defaultMessage ?: jwksResult.error.code,
                ),
            )
        }

        val jwks = jwksResult.value
        cache.putApp(metadata.issuer, jwks, JWKS_CACHE_TTL)
        return Ok(jwks)
    }

    override suspend fun invalidate(issuer: String) {
        cache.removeApp(issuer)
    }

    private companion object {
        const val CACHE_NAMESPACE = "oauth2.rp.issuer-jwks"

        // Issuer keys rotate on human timescales; 10 minutes balances freshness with backend load.
        // Callers invalidate explicitly on `kid` miss so key rotations still surface promptly.
        val JWKS_CACHE_TTL: Duration = 10.minutes
    }
}
