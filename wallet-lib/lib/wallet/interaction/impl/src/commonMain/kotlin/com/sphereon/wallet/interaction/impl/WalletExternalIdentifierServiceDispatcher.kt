/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierService

/** Priority dispatcher for the existing external identifier service contributions. */
class WalletExternalIdentifierServiceDispatcher(
    services: Set<ExternalIdentifierService>,
) : ExternalIdentifierService {
    private val services = services.sortedBy { it.order }

    override val supportedIdentifierMethods = services.flatMap { it.supportedIdentifierMethods }.distinct()

    private suspend fun serviceFor(opts: ExternalIdentifierOptsOrResult): ExternalIdentifierService? =
        services.firstOrNull { it.isSupportedOpts(opts) }

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean = services.any { it.isSupportedIdentifier(identifier) }

    override suspend fun isSupportedIdentifierMethod(identifierMethod: com.sphereon.crypto.resolution.IIdentifierMethod): Boolean =
        services.any { it.isSupportedIdentifierMethod(identifierMethod) }

    override suspend fun isSupportedOpts(opts: ExternalIdentifierOptsOrResult): Boolean = serviceFor(opts) != null

    override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierOpts, IdkErrorType> =
        serviceFor(opts)?.asSupportedOpts(opts)
            ?: IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(message = "No external identifier service supports ${opts::class.simpleName}").asErrorResult()

    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult, IdkErrorType> =
        serviceFor(opts)?.resolve(opts)
            ?: IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(message = "No external identifier service supports ${opts::class.simpleName}").asErrorResult()
}
