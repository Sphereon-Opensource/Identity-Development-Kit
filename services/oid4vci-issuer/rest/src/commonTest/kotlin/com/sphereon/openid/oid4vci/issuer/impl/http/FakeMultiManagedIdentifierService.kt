/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOpts
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierResult
import com.sphereon.crypto.resolution.managed.MultiManagedIdentifierService

/**
 * Test stub for [MultiManagedIdentifierService] used by the OID4VCI metadata-endpoint unit
 * tests. The metadata command only consults this service when the config provider supplies a
 * `credentialRequestDecryptionKey` alias (see
 * [com.sphereon.openid.oid4vci.issuer.impl.http.command.GetIssuerMetadataEndpointCommandImpl.resolveCredentialRequestEncryption]).
 * The fixture's [FakeOid4vciIssuerConfigProvider] returns `null` for that field, so this stub
 * is wired purely to satisfy DI and never has its methods invoked. Any call therefore throws —
 * if a future test starts exercising the encryption path, the failure will point straight at
 * the missing fake instead of producing a misleading null-key error downstream.
 */
internal object FakeMultiManagedIdentifierService : MultiManagedIdentifierService {
    override val supportedIdentifierMethods: List<IIdentifierMethod> = emptyList()

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean = error("FakeMultiManagedIdentifierService.isSupportedIdentifier was not expected to be called in this test")

    override suspend fun isSupportedIdentifierMethod(identifierMethod: IIdentifierMethod): Boolean =
        error("FakeMultiManagedIdentifierService.isSupportedIdentifierMethod was not expected to be called in this test")

    override suspend fun isSupportedOpts(opts: ManagedIdentifierOptsOrResult): Boolean = error("FakeMultiManagedIdentifierService.isSupportedOpts was not expected to be called in this test")

    override suspend fun asSupportedOpts(opts: ManagedIdentifierOptsOrResult): IdkResult<ManagedIdentifierOpts, IdkErrorType> =
        error("FakeMultiManagedIdentifierService.asSupportedOpts was not expected to be called in this test")

    override suspend fun resolve(opts: ManagedIdentifierOptsOrResult): IdkResult<ManagedIdentifierResult<KeyType>, IdkErrorType> =
        error("FakeMultiManagedIdentifierService.resolve was not expected to be called in this test")
}
