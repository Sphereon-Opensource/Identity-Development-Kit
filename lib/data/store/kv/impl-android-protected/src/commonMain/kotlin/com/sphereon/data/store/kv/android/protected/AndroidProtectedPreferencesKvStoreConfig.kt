/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.data.store.kv.android.secure

import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreScopeBinding
import kotlinx.serialization.Serializable

@Serializable
data class AndroidProtectedPreferencesKvStoreConfig(
    override val id: String,
    override val scopeBinding: KvStoreScopeBinding = KvStoreScopeBinding.TENANT,
    override val enabled: Boolean = true,
    override val order: Int = 100,
    val keyAlias: String = "sphereon.wallet.kv.metadata.wrap",
) : KvStoreConfigBase {
    override val backendId: String = BACKEND_ID

    companion object {
        const val BACKEND_ID = "android-protected-preferences"
    }
}
