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
 *
 */

package com.sphereon.crypto.resolution

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.annotations.Beta
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.resolution.managed.ManagedIdentifierCoseKeyResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierJwkResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierKeyResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOpts
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierResultTypeGuards
import com.sphereon.crypto.resolution.managed.ManagedOptsCoseKey
import com.sphereon.crypto.resolution.managed.ManagedOptsJwk
import com.sphereon.crypto.resolution.managed.ManagedOptsKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Check if an object is a managed identifier result
 */
fun isManagedIdentifierResult(identifier: ManagedIdentifierOptsOrResult): Boolean = identifier is ManagedIdentifierResult<*>

/**
 * Safely ensure that an object is a managed identifier result.
 * @return IdkResult containing the result, or an error if conversion fails.
 */
@Beta(message = "Safe conversion API - may have minor changes in future versions")
suspend fun tryEnsureManagedIdentifierResult(identifier: ManagedIdentifierOptsOrResult): IdkResult<ManagedIdentifierResult<*>, IdkError> =
    withContext(Dispatchers.Default) {
        if (isManagedIdentifierResult(identifier)) {
            Ok(identifier as ManagedIdentifierResult<*>)
        } else {
            // For opts that already contain resolved key material, construct a result directly
            val opts = identifier as ManagedIdentifierOpts
            when (opts) {
                is ManagedOptsJwk -> {
                    val jwk = opts.identifier
                    val keyInfo =
                        opts.lookup as? com.sphereon.crypto.core.ResolvedKeyInfoType<com.sphereon.crypto.core.jose.JwkType>
                            ?: return@withContext Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "ManagedOptsJwk lookup must be a ResolvedKeyInfoType"))
                    val alias =
                        keyInfo.alias ?: jwk.kid
                            ?: return@withContext Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "JWK must have kid or alias"))
                    val managedKeyInfo =
                        ManagedKeyInfo(
                            alias = alias,
                            providerId = keyInfo.providerId ?: "local",
                            resolvedKeyInfo = keyInfo,
                        )
                    Ok(
                        ManagedIdentifierJwkResult(
                            identifier = jwk,
                            keyInfo = managedKeyInfo,
                            context = opts.context,
                        ),
                    )
                }

                is ManagedOptsKey -> {
                    val keyInfo =
                        opts.lookup as? com.sphereon.crypto.core.ResolvedKeyInfoType<com.sphereon.crypto.core.KeyType>
                            ?: return@withContext Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "ManagedOptsKey lookup must be a ResolvedKeyInfoType"))
                    val alias =
                        keyInfo.alias ?: keyInfo.kid
                            ?: return@withContext Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Key must have kid or alias"))
                    val managedKeyInfo =
                        ManagedKeyInfo(
                            alias = alias,
                            providerId = keyInfo.providerId ?: "local",
                            resolvedKeyInfo = keyInfo,
                        )
                    Ok(
                        ManagedIdentifierKeyResult(
                            identifier = opts.identifier,
                            keyInfo = managedKeyInfo,
                            context = opts.context,
                        ),
                    )
                }

                is ManagedOptsCoseKey -> {
                    val coseKey = opts.identifier
                    val keyInfo =
                        opts.lookup as? com.sphereon.crypto.core.ResolvedKeyInfoType<com.sphereon.crypto.core.cose.CoseKeyType>
                            ?: return@withContext Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "ManagedOptsCoseKey lookup must be a ResolvedKeyInfoType"))
                    val alias =
                        keyInfo.alias ?: keyInfo.kid
                            ?: return@withContext Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "CoseKey must have kid or alias"))
                    val managedKeyInfo =
                        ManagedKeyInfo(
                            alias = alias,
                            providerId = keyInfo.providerId ?: "local",
                            resolvedKeyInfo = keyInfo,
                        )
                    Ok(
                        ManagedIdentifierCoseKeyResult(
                            identifier = coseKey,
                            keyInfo = managedKeyInfo,
                            context = opts.context,
                        ),
                    )
                }

                else -> {
                    Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message =
                                "Cannot resolve ${opts::class.simpleName} without a ManagedIdentifierService. " +
                                    "Use a ManagedIdentifierService to resolve the identifier first.",
                        ),
                    )
                }
            }
        }
    }

/**
 * Ensure that an object is a managed identifier result.
 * @throws IllegalArgumentException if conversion fails.
 */
suspend fun ensureManagedIdentifierResult(identifier: ManagedIdentifierOptsOrResult): ManagedIdentifierResult<*> =
    tryEnsureManagedIdentifierResult(identifier).getOrElse {
        throw IllegalArgumentException(it.message.defaultMessage)
    }

/**
 * Safely convert a managed identifier to a key result.
 * @return IdkResult containing the key result, or an error if conversion fails.
 */
@Beta(message = "Safe conversion API - may have minor changes in future versions")
suspend fun tryManagedIdentifierToKeyResult(identifier: ManagedIdentifierOptsOrResult): IdkResult<ManagedIdentifierKeyResult, IdkError> {
    val managedResult = tryEnsureManagedIdentifierResult(identifier).getOrElse { return Err(it) }
    return if (ManagedIdentifierResultTypeGuards.isManagedIdentifierKeyResult(managedResult)) {
        Ok(managedResult as ManagedIdentifierKeyResult)
    } else {
        val managedJwkInfo = ManagedKeyInfo(alias = managedResult.keyInfo.alias, providerId = managedResult.providerId, resolvedKeyInfo = managedResult.keyInfo)
        Ok(
            ManagedIdentifierKeyResult(
                identifier = managedJwkInfo.key,
                keyInfo = managedJwkInfo,
                context = managedResult.context,
            ),
        )
    }
}

/**
 * Convert a managed identifier to a key result.
 * @throws IllegalArgumentException if conversion fails.
 */
suspend fun managedIdentifierToKeyResult(identifier: ManagedIdentifierOptsOrResult): ManagedIdentifierKeyResult =
    tryManagedIdentifierToKeyResult(identifier).getOrElse {
        throw IllegalArgumentException(it.message.defaultMessage)
    }

/**
 * Safely convert a managed identifier to a JWK result.
 * @return IdkResult containing the JWK result, or an error if conversion fails.
 */
@Beta(message = "Safe conversion API - may have minor changes in future versions")
suspend fun tryManagedIdentifierToJwk(identifier: ManagedIdentifierOptsOrResult): IdkResult<ManagedIdentifierJwkResult, IdkError> {
    val managedResult = tryEnsureManagedIdentifierResult(identifier).getOrElse { return Err(it) }
    return if (ManagedIdentifierResultTypeGuards.isManagedIdentifierJwkResult(managedResult)) {
        Ok(managedResult as ManagedIdentifierJwkResult)
    } else {
        val managedJwkInfo =
            ManagedKeyInfo(alias = managedResult.keyInfo.alias, providerId = managedResult.providerId, resolvedKeyInfo = CoseJoseKeyMappingService.toResolvedJwkKeyInfo(managedResult.keyInfo))
        Ok(
            ManagedIdentifierJwkResult(
                identifier = managedJwkInfo.key,
                keyInfo = managedJwkInfo,
                context = managedResult.context,
            ),
        )
    }
}

/**
 * Convert a managed identifier to a JWK result.
 * @throws IllegalArgumentException if conversion fails.
 */
suspend fun managedIdentifierToJwk(identifier: ManagedIdentifierOptsOrResult): ManagedIdentifierJwkResult =
    tryManagedIdentifierToJwk(identifier).getOrElse {
        throw IllegalArgumentException(it.message.defaultMessage)
    }
