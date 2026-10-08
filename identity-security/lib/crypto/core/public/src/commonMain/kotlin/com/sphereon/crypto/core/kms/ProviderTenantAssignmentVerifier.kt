/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.core.kms

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/** The provider-native object kind whose tenant assignment is being checked. */
@JsExportCompat
@Serializable
enum class ProviderNativeObjectType {
    KEY,
    CERTIFICATE,
}

/**
 * Public, credential-free coordinates for an existing provider-native object.
 *
 * The optional [id] is an immutable provider-native identity such as a key ARN or
 * version. Provider implementations must resolve and validate it against [alias]
 * before checking assignment.
 */
@JsExportCompat
@Serializable
data class ProviderNativeObjectLookup(
    val type: ProviderNativeObjectType,
    val alias: String,
    val id: String? = null,
) {
    init {
        require(alias.isNotBlank()) { "Provider-native object alias is required" }
        require(id?.isBlank() != true) { "Provider-native object id must not be blank" }
    }
}

/**
 * Optional read-only capability for proving that a provider-native object is assigned
 * to the authenticated tenant. Tags and credentials never cross this boundary.
 */
@JsExportCompat
interface ProviderTenantAssignmentVerifier {
    suspend fun isAssignedToTenant(
        lookup: ProviderNativeObjectLookup,
        tenantId: String,
    ): Boolean
}
