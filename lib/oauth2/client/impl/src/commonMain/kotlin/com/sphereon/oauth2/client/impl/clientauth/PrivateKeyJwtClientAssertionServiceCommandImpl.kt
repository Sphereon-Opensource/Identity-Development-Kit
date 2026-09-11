/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.oauth2.client.impl.clientauth

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.command.CreateRawSignatureArgs
import com.sphereon.crypto.core.kms.command.CreateRawSignatureCommand
import com.sphereon.crypto.core.kms.command.ResolvePublicKeyArgs
import com.sphereon.crypto.core.kms.command.ResolvePublicKeyCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.PrivateKeyJwtClientAssertionArgs
import com.sphereon.oauth2.client.command.PrivateKeyJwtClientAssertionServiceCommand
import com.sphereon.oauth2.common.command.PrivateKeyJwtAssertionAssembly
import com.sphereon.oauth2.common.command.PrivateKeyJwtAssertionAssemblyRequest
import com.sphereon.oauth2.common.command.resolveJoseSignatureAlgorithm
import com.sphereon.oauth2.common.model.ClientAssertion
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.http.Url

@Inject
@SingleIn(SessionScope::class)
class PrivateKeyJwtClientAssertionCreator(
    private val resolvePublicKeyCommand: ResolvePublicKeyCommand,
    private val createRawSignatureCommand: CreateRawSignatureCommand,
    private val assertionAssembly: PrivateKeyJwtAssertionAssembly,
) {
    suspend fun create(args: PrivateKeyJwtClientAssertionArgs): IdkResult<ClientAssertion, IdkError> {
        validate(args)?.let { return Err(it) }

        val selectedJoseAlgorithm = JwaAlgorithm.fromValue(args.keySelector.algorithm)
            ?: return invalid("Unknown JOSE signing algorithm: ${args.keySelector.algorithm}")
        if (selectedJoseAlgorithm.value !in ALLOWED_ASYMMETRIC_JOSE_ALGORITHMS) {
            return invalid("Unsupported private_key_jwt signing algorithm: ${selectedJoseAlgorithm.value}")
        }
        val signatureAlgorithm = SignatureAlgorithm.tryFromJose(selectedJoseAlgorithm).getOrElse { return Err(it) }
        val keyReference = ManagedKeyReference(
            providerId = args.keySelector.providerId,
            alias = args.keySelector.alias,
            kid = args.keySelector.kid,
            signatureAlgorithm = signatureAlgorithm,
        )

        val resolution = resolvePublicKeyCommand.execute(ResolvePublicKeyArgs(keyInfo = keyReference))
        if (resolution.isErr) return Err(resolution.error)
        val resolved = resolution.value.resolvedKeyInfo
            ?: return invalid("The selected client assertion key did not resolve")
        val publicJwk = resolved.key as? Jwk
            ?: return invalid("The selected client assertion key must resolve to a public JWK")
        if (resolved.keyVisibility != KeyVisibility.PUBLIC || publicJwk.hasPrivateMaterial()) {
            return invalid("The selected client assertion key must resolve as public-only key material")
        }
        val resolvedKid = publicJwk.kid?.takeIf { it.isNotBlank() }
            ?: return invalid("The resolved client assertion JWK has no kid")
        if (resolvedKid != args.keySelector.kid ||
            resolved.kid?.takeIf { it.isNotBlank() }?.let { it != args.keySelector.kid } == true
        ) {
            return invalid("The resolved client assertion kid does not match the opaque selector")
        }
        if (resolved.providerId != args.keySelector.providerId || resolved.alias != args.keySelector.alias) {
            return invalid("The resolved client assertion key location does not match the opaque selector")
        }

        val jwkJoseAlgorithm = publicJwk.alg?.value
            ?: return invalid("The resolved client assertion JWK has no signing algorithm")
        val resolvedJoseAlgorithm = try {
            resolveJoseSignatureAlgorithm(publicJwk)
        } catch (expected: IllegalArgumentException) {
            return invalid(expected.message ?: "The resolved client assertion algorithm is unsupported")
        }
        val metadataJoseAlgorithm = resolved.signatureAlgorithm?.jose?.value
            ?: return invalid("The resolved client assertion key metadata has no JOSE signing algorithm")
        if (jwkJoseAlgorithm != selectedJoseAlgorithm.value ||
            resolvedJoseAlgorithm != selectedJoseAlgorithm.value ||
            metadataJoseAlgorithm != selectedJoseAlgorithm.value ||
            !publicJwk.matchesSigningKeyShape(selectedJoseAlgorithm)
        ) {
            return invalid("The resolved client assertion algorithm does not match the opaque selector")
        }

        val signingInput = try {
            assertionAssembly.assemble(
                request = PrivateKeyJwtAssertionAssemblyRequest(
                    clientId = args.clientId,
                    audience = args.effectiveTokenEndpoint,
                    lifetimeSeconds = args.lifetimeSeconds
                        ?: PrivateKeyJwtAssertionAssembly.DEFAULT_ASSERTION_LIFETIME_SECONDS,
                ),
                publicJwk = publicJwk,
            )
        } catch (expected: IllegalArgumentException) {
            return invalid(expected.message ?: "Failed to assemble private_key_jwt assertion")
        }

        val signature = createRawSignatureCommand.execute(
            CreateRawSignatureArgs(
                keyInfo = keyReference,
                input = signingInput.signingInput,
                requireX5Chain = false,
            ),
        )
        if (signature.isErr) return Err(signature.error)
        if (signature.value.signature.isEmpty()) return invalid("The client assertion signature is empty")

        return Ok(
            ClientAssertion(
                clientId = args.clientId,
                assertionType = JWT_ASSERTION_TYPE,
                assertion = assertionAssembly.finish(signingInput, signature.value.signature),
            ),
        )
    }

    private fun validate(args: PrivateKeyJwtClientAssertionArgs): IdkError? {
        val fields = listOf(
            "providerId" to args.keySelector.providerId,
            "alias" to args.keySelector.alias,
            "kid" to args.keySelector.kid,
            "algorithm" to args.keySelector.algorithm,
            "clientId" to args.clientId,
            "effectiveTokenEndpoint" to args.effectiveTokenEndpoint,
        )
        fields.firstOrNull { (_, value) -> value.isBlank() }?.let { (name, _) ->
            return invalidError("$name must not be blank")
        }
        args.lifetimeSeconds?.let { lifetime ->
            if (lifetime !in 1..PrivateKeyJwtAssertionAssembly.MAX_ASSERTION_LIFETIME_SECONDS) {
                return invalidError("private_key_jwt lifetime must be between 1 and 300 seconds")
            }
        }
        val audience = runCatching { Url(args.effectiveTokenEndpoint) }.getOrNull()
            ?: return invalidError("The effective token endpoint audience is invalid")
        if (!audience.protocol.name.equals("https", ignoreCase = true) || audience.host.isBlank()) {
            return invalidError("The effective token endpoint audience must use HTTPS")
        }
        if (audience.user != null || audience.password != null || audience.fragment.isNotEmpty()) {
            return invalidError("The effective token endpoint audience must not contain userinfo or a fragment")
        }
        return null
    }

    private fun Jwk.hasPrivateMaterial(): Boolean =
        listOf(d, k, p, q, dP, dQ, qInv).any { !it.isNullOrBlank() }

    private fun Jwk.matchesSigningKeyShape(algorithm: JwaAlgorithm): Boolean =
        when (algorithm) {
            JwaAlgorithm.ES256 -> kty == JwaKeyType.EC && crv == JwaCurve.P_256
            JwaAlgorithm.ES384 -> kty == JwaKeyType.EC && crv == JwaCurve.P_384
            JwaAlgorithm.ES512 -> kty == JwaKeyType.EC && crv == JwaCurve.P_521
            JwaAlgorithm.EdDSA -> kty == JwaKeyType.OKP && crv == JwaCurve.Ed25519
            JwaAlgorithm.RS256, JwaAlgorithm.PS256 -> kty == JwaKeyType.RSA
            else -> false
        }

    private fun invalid(message: String): IdkResult<Nothing, IdkError> = Err(invalidError(message))

    private fun invalidError(message: String): IdkError = IdkError.ILLEGAL_ARGUMENT_ERROR(message = message)

    private companion object {
        const val JWT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
        val ALLOWED_ASYMMETRIC_JOSE_ALGORITHMS = setOf("PS256", "ES256", "EdDSA", "RS256", "ES384", "ES512")
    }
}

@Inject
@SingleIn(SessionScope::class)
class PrivateKeyJwtClientAssertionServiceCommandImpl(
    execution: SessionExecution,
    private val creator: PrivateKeyJwtClientAssertionCreator,
) : TypedServiceCommandAdapter<PrivateKeyJwtClientAssertionArgs, ClientAssertion, IdkError>(
        commandId = PrivateKeyJwtClientAssertionServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<PrivateKeyJwtClientAssertionArgs>(),
        outputTypeToken = typeToken<ClientAssertion>(),
    ),
    PrivateKeyJwtClientAssertionServiceCommand {
    override val commandId: String get() = PrivateKeyJwtClientAssertionServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is PrivateKeyJwtClientAssertionArgs

    override suspend fun doExecute(
        args: PrivateKeyJwtClientAssertionArgs,
        applyDuring: (PrivateKeyJwtClientAssertionArgs) -> PrivateKeyJwtClientAssertionArgs,
    ): IdkResult<ClientAssertion, IdkError> = creator.create(applyDuring(args))
}
