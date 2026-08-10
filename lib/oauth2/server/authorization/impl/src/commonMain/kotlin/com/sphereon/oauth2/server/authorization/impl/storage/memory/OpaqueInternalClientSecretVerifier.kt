/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.oauth2.server.authorization.impl.storage.memory

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.OpaqueSecretResolver
import com.sphereon.core.api.security.ConstantTime
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Server-derived binding between an internal OAuth client and its write-only secret record.
 *
 * This type never crosses a REST, serialization, or generated-client boundary. The tenant and
 * secret handle originate only from principal configuration loaded by the authorization server.
 * A credential without a configured locator can only be verified by a platform-provided verifier
 * that derives the secret binding from the tenant and client identity; the default resolver-based
 * verifier rejects it.
 */
data class OpaqueInternalClientCredential(
    val clientId: String,
    val tenantId: String,
    val secretId: String? = null,
) {
    init {
        require(clientId.isNotBlank()) { "Internal OAuth client id is required" }
        require(tenantId.isNotBlank()) { "Internal OAuth client tenant is required" }
        require(secretId == null || OPAQUE_SECRET_ID_PATTERN.matches(secretId)) {
            "Internal OAuth client secret id must be an opaque server-generated identifier"
        }
    }

    override fun toString(): String = "OpaqueInternalClientCredential([REDACTED])"
}

/**
 * Narrow authentication seam for internal OAuth clients backed by opaque secret handles.
 *
 * The platform host replaces the default with a local runtime verifier carrying an exact,
 * persisted authentication grant. Tenant authorization-server satellites use the default remote
 * resolver, whose bootstrap workload session is independent of the unauthenticated token request.
 */
fun interface OpaqueInternalClientSecretVerifier {
    suspend fun verify(
        credential: OpaqueInternalClientCredential,
        presentedSecret: String,
    ): IdkResult<Boolean, AuthorizationServerError.StorageError>
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OpaqueInternalClientSecretVerifier>())
class DefaultOpaqueInternalClientSecretVerifier(
    private val opaqueSecretResolver: OpaqueSecretResolver,
) : OpaqueInternalClientSecretVerifier {
    override suspend fun verify(
        credential: OpaqueInternalClientCredential,
        presentedSecret: String,
    ): IdkResult<Boolean, AuthorizationServerError.StorageError> {
        val secretId =
            credential.secretId
                ?: return Err(
                    AuthorizationServerError.StorageError(
                        operation = "verifyOpaqueInternalClientCredentials",
                        details = "Internal client credential has no configured locator",
                    ),
                )
        val expected = opaqueSecretResolver.resolve(secretId)
        return if (expected.isOk) {
            Ok(ConstantTime.equalsCT(expected.value, presentedSecret))
        } else {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "verifyOpaqueInternalClientCredentials",
                    details = "Internal client credential could not be resolved",
                ),
            )
        }
    }
}

private val OPAQUE_SECRET_ID_PATTERN: Regex = Regex("^sec_[A-Za-z0-9_-]{16,128}$")
