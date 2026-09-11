/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.openid.oid4vp.common.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vp.common.Oid4vpRequestTrustMaterial
import com.sphereon.openid.oid4vp.common.Oid4vpRequestTrustMaterialProvider

/**
 * Fail-closed provider with no roots at all. Not bound in any graph: tests and hosts that must
 * refuse every x5c chain construct it explicitly.
 */
class UnavailableOid4vpRequestTrustMaterialProvider : Oid4vpRequestTrustMaterialProvider {
    override suspend fun resolve(): IdkResult<Oid4vpRequestTrustMaterial, IdkError> =
        Err(
            IdkError.fromString(
                message = "Governed X.509 request trust material is unavailable",
                code = "X5C_TRUST_MATERIAL_UNAVAILABLE",
                category = ErrorCategory.UNAVAILABLE,
            ),
        )
}
