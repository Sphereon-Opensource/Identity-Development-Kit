/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.openid.oid4vci.issuer.config.CredentialSigningConfig
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider

/**
 * The issuance signing configuration for [configId].
 *
 * The provider may serve its signing configurations from a snapshot that lags a credential
 * configuration written moments ago, possibly by another process: the offer for it can already
 * exist while the snapshot does not know the configuration yet. A miss therefore reloads the
 * configuration once and looks again; only a configuration that is still unknown returns null.
 */
internal suspend fun Oid4vciIssuerConfigProvider.signingConfigReloadingOnMiss(configId: String): CredentialSigningConfig? {
    credentialSigningConfigs()[configId]?.let { return it }
    reloadConfiguration()
    return credentialSigningConfigs()[configId]
}
