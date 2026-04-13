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

package com.sphereon.crypto.resolution.managed

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.crypto.resolution.IdentifierTypeUtils
import com.sphereon.crypto.resolution.managed.ManagedIdentifierServiceAdapter
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import software.amazon.app.platform.scope.Scoped
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<KeyInfoIdentifierResolutionService>())
@ContributesIntoSet(SessionScope::class, binding = binding<ManagedIdentifierService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyInfoIdentifierResolutionServiceImpl", exact = true)
class KeyInfoIdentifierResolutionServiceImpl(
    execution: SessionExecution,
    val kms: KeyManagerService,
) : ManagedIdentifierServiceAdapter<ManagedIdentifierKeyResult>(
        supportedIdentifierMethods =
            listOf(
                IdentifierMethodDefaults.KEY,
                IdentifierMethodDefaults.KEY_ALIAS,
                IdentifierMethodDefaults.KID,
                IdentifierMethodDefaults.JWK,
                IdentifierMethodDefaults.COSE_KEY,
            ),
        execution = execution,
        commandId = COMMAND_ID,
    ),
    KeyInfoIdentifierResolutionService,
    Scoped {
    @Suppress("MagicNumber")
    override suspend fun doExecute(
        args: ManagedIdentifierOptsOrResult,
        applyDuring: (ManagedIdentifierOptsOrResult) -> ManagedIdentifierOptsOrResult,
    ): IdkResult<ManagedIdentifierKeyResult, IdkErrorType> {
        log.debug("Resolving managed key identifier: ${args.identifier.toString().take(100)}...")
        if (!supports(args)) {
            return IdkError
                .COMMAND_ARG_NOT_SUPPORTED_ERROR(message = "Managed identifier opts for Key expected. Type ${args.method ?: args.identifier} not supported")
                .asErrorResult()
        }
        val optsResult = asSupportedOpts(args)
        if (optsResult.isErr) {
            return optsResult.error.asErrorResult()
        }

        val opts = optsResult.value
        log.debug("opts.identifier.signatureAlgorithm = ${opts.identifier.signatureAlgorithm}")
        var managedKeyInfo = kms.getKey(opts.identifier)
        log.debug("Retrieved key with signatureAlgorithm = ${managedKeyInfo.signatureAlgorithm}")

        // If the opts.identifier has a signatureAlgorithm hint (e.g., from JWT header),
        // and the retrieved key doesn't have one (or has a different default), prefer the hint
        val algorithmHint = opts.identifier.signatureAlgorithm
        if (algorithmHint != null && managedKeyInfo.signatureAlgorithm != algorithmHint) {
            log.debug("Overriding key algorithm from ${managedKeyInfo.signatureAlgorithm} to $algorithmHint based on identifier hint")
            // Create a new ManagedKeyInfo with the algorithm from the hint
            // Use alias and providerId from the original managedKeyInfo (which came from KMS)
            val updatedResolvedKeyInfo =
                ResolvedKeyInfo(
                    key = managedKeyInfo.key,
                    signatureAlgorithm = algorithmHint,
                    kid = managedKeyInfo.kid,
                    alias = managedKeyInfo.alias,
                    providerId = managedKeyInfo.providerId,
                    keyVisibility = managedKeyInfo.keyVisibility,
                    keyType = managedKeyInfo.keyType,
                    keyEncoding = managedKeyInfo.keyEncoding,
                )
            managedKeyInfo =
                ManagedKeyInfo(
                    alias = managedKeyInfo.alias,
                    providerId = managedKeyInfo.providerId,
                    resolvedKeyInfo = updatedResolvedKeyInfo,
                )
        }

        @Suppress("UNCHECKED_CAST")
        return ManagedIdentifierKeyResult(
            keyInfo = managedKeyInfo as ManagedKeyInfoType<KeyType>,
            context = args.context,
            identifier = managedKeyInfo.key,
        ).asOkResult()
            .also { log.debug("Resolved managed key identifier: ${args.identifier.toString().take(100)}") }
    }

    override suspend fun supports(args: Any): Boolean = supportsManagedIdentifierArgs(args)

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean =
        when (identifier) {
            is KeyInfoType<*> -> true
            is KeyType -> true
            is String -> IdentifierTypeUtils.isKidIdentifier(identifier) || IdentifierTypeUtils.isKeyAliasIdentifier(identifier)
            else -> false
        }

    override suspend fun resolve(opts: ManagedIdentifierOptsOrResult): IdkResult<ManagedIdentifierKeyResult, IdkErrorType> = execute(opts)

    override suspend fun asSupportedOpts(opts: ManagedIdentifierOptsOrResult): IdkResult<ManagedOptsKeyInfo, IdkErrorType> {
        if (!isSupportedOpts(opts)) {
            return IdkError
                .COMMAND_ARG_NOT_SUPPORTED_ERROR(message = "Managed identifier opts or result not supported : ${opts::class.simpleName}, id: ${opts.identifier}")
                .asErrorResult()
        }
        return when (opts) {
            is ManagedIdentifierResult<*> -> {
                ManagedOptsKeyInfo(identifier = opts.keyInfo, context = opts.context, lookup = opts.keyInfo)
            }

            is ManagedOptsKeyInfo -> {
                opts
            }

            is ManagedOptsAlias -> {
                ManagedOptsKeyInfo(
                    identifier = KeyInfo<KeyType>(alias = opts.identifier),
                    context = opts.context,
                    lookup = opts.lookup,
                )
            }

            is ManagedOptsKid -> {
                // Use the lookup as the base identifier and only override the kid field if not present
                val lookupKeyInfo = opts.lookup
                val identifier =
                    if (lookupKeyInfo.kid == null) {
                        // If lookup doesn't have kid, set it from opts.identifier
                        when (lookupKeyInfo) {
                            is ResolvedKeyInfo -> lookupKeyInfo.copy(kid = opts.identifier)
                            is KeyInfo<*> -> lookupKeyInfo.copy(kid = opts.identifier)
                            else -> KeyInfo<KeyType>(kid = opts.identifier, signatureAlgorithm = lookupKeyInfo.signatureAlgorithm)
                        }
                    } else {
                        // Use lookup as-is since it already has the kid
                        lookupKeyInfo
                    }
                ManagedOptsKeyInfo(identifier = identifier, context = opts.context, lookup = lookupKeyInfo)
            }

            is ManagedOptsJwk -> {
                ManagedOptsKeyInfo(
                    identifier = ResolvedKeyInfo.fromKey(opts.identifier),
                    context = opts.context,
                    lookup = opts.lookup,
                )
            }

            is ManagedOptsCoseKey -> {
                ManagedOptsKeyInfo(
                    identifier = ResolvedKeyInfo.fromKey(opts.identifier),
                    context = opts.context,
                    lookup = opts.lookup,
                )
            }

            is ManagedOptsKey -> {
                ManagedOptsKeyInfo(identifier = ResolvedKeyInfo.fromKey(opts.identifier), context = opts.context, lookup = opts.lookup)
            }

            else -> {
                return IdkError
                    .COMMAND_ARG_NOT_SUPPORTED_ERROR(message = "Supplied managed identifier opts are not supported ${opts::class.simpleName}, id: ${opts.identifier}")
                    .asErrorResult()
            }
        }.asOkResult()
    }

    private suspend fun supportsManagedIdentifierArgs(args: Any): Boolean {
        val managedArgs = args as? ManagedIdentifierOptsOrResult ?: return false
        val methodSupported = managedArgs.method?.let { isSupportedIdentifierMethod(it) } ?: true
        return methodSupported && isSupportedIdentifier(managedArgs.identifier)
    }

    companion object {
        const val COMMAND_ID = "crypto.resolution.keyinfo"
    }
}
