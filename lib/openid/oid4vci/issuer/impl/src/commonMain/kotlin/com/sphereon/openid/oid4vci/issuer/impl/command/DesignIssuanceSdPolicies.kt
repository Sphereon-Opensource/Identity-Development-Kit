/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.data.store.credential.design.impl.mapper.Oid4vciDesignMapper
import com.sphereon.data.store.credential.design.model.ClaimPresentation
import com.sphereon.data.store.credential.design.model.SdPolicy as DesignSdPolicy
import com.sphereon.openid.oid4vci.issuer.format.SdPolicy

/**
 * The issuance action for each design claim, keyed by claim path.
 *
 * The design policy follows SD-JWT VC type metadata `sd`: `always` MUST be selectively disclosable,
 * `allowed` MAY be, and `never` MUST stay in the clear. The issuer enum instead names the concrete
 * issuance action, so the similarly named values must not be mapped by name. Immediate and
 * deferred issuance both use this mapping so a design yields the same credential either way.
 */
internal fun designIssuanceSdPolicies(claims: List<ClaimPresentation>): Map<String, SdPolicy> =
    claims.associate { claim -> Oid4vciDesignMapper.claimPathString(claim) to claim.sdPolicy.toIssuanceSdPolicy() }

internal fun DesignSdPolicy.toIssuanceSdPolicy(): SdPolicy =
    when (this) {
        DesignSdPolicy.ALWAYS -> SdPolicy.SELECTIVELY_DISCLOSABLE
        DesignSdPolicy.ALLOWED -> SdPolicy.SELECTIVELY_DISCLOSABLE
        DesignSdPolicy.NEVER -> SdPolicy.ALWAYS_DISCLOSED
    }
