package com.sphereon.crypto.kms.keystore.software

/** Optional provider capability. A missing capability must never be interpreted as retention. */
interface RetainedAliasAuthority {
    /** Checks the effective provider configuration, durable retention marker, and actual key entry. */
    suspend fun isRetainedKeyAlias(alias: String): Boolean
}
