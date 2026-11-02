/*
 * © 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.credential.design.CredentialDesignService
import com.sphereon.data.store.credential.design.IssuanceDesignVersionSelector
import com.sphereon.data.store.credential.design.model.DesignBindingKey
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.data.store.credential.design.model.ResolvedCredentialDesign

internal const val INVALID_CREDENTIAL_CONFIGURATION = "invalid_credential_configuration"

/**
 * The credential design issuance of [configId] renders from, or `null` when no design is bound to it.
 *
 * With a [versionSelector] the design and version come from the selector, independent of who calls, and a configuration
 * whose design has no version issuance may use is refused. Without one the design bound to the configuration is resolved
 * as stored.
 */
internal suspend fun resolveIssuanceDesign(
    service: CredentialDesignService,
    versionSelector: IssuanceDesignVersionSelector?,
    tenantId: String,
    configId: String,
): IdkResult<ResolvedCredentialDesign?, IdkError> {
    if (versionSelector != null) {
        val selected =
            versionSelector.issuanceVersion(tenantId, configId).getOrElse { error ->
                return Err(
                    IdkError.fromString(
                        code = INVALID_CREDENTIAL_CONFIGURATION,
                        message = "Credential configuration '$configId' has no credential design version to issue from: " +
                            error.message.defaultMessage,
                    ),
                )
            } ?: return Ok(null)
        return service.resolveCredentialDesign(
            tenantId,
            ResolveCredentialDesignInput(designId = selected.designId, designVersion = selected.version),
        )
    }
    val record =
        service
            .findCredentialDesignByBindingKey(tenantId, DesignBindingKey.CREDENTIAL_CONFIGURATION_ID, configId)
            .getOrElse { return Err(it) }
            .firstOrNull() ?: return Ok(null)
    return service.resolveCredentialDesign(tenantId, ResolveCredentialDesignInput(designId = record.id))
}
