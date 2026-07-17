/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Creates Wscd instances from WscdConfig. Implementations live in the wscd impl
 * modules (lib-wallet-wscd-software, lib-wallet-wscd-mobile, EDK remote) and are the
 * only components that may construct KMS providers.
 */
interface WscdFactory {
    fun supports(config: WscdConfig): Boolean

    suspend fun create(config: WscdConfig): IdkResult<Wscd, IdkError>
}
