/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core

import com.sphereon.trust.core.model.TrustAnchor
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.trust.core.model.TrustValidationResult
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Service for validating trust in cryptographic credentials and certificates.
 *
 * Supports multiple trust models including ETSI Trust Lists, X.509 CA bundles,
 * DIDs, OpenID Federation, and custom trust anchors.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustValidationService", exact = true)
interface TrustValidationService {
    fun getId(): String
    suspend fun validate(request: TrustValidationRequest): TrustValidationResult
    fun supports(context: TrustContext): Boolean
    suspend fun getTrustAnchors(): List<TrustAnchor>
    suspend fun refresh(): Boolean
}
