/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance.services

// Shared by deployed issuer, verifier, and wallet conformance drivers.

import java.nio.file.Path

internal interface OidfEnterpriseDeploymentProfile : AutoCloseable {
    val productSigningTrustAnchorPath: Path
    val walletInteractionGrpcEndpoint: String
    val walletUnitGrpcEndpoint: String

    fun apply()

    fun exportServiceEvidence()

    override fun close() = Unit
}
