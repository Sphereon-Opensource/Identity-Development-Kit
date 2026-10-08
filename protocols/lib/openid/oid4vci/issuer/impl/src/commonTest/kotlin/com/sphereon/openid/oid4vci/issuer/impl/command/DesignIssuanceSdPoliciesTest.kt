package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.data.store.credential.design.model.ClaimLabel
import com.sphereon.data.store.credential.design.model.ClaimPathSegment
import com.sphereon.data.store.credential.design.model.ClaimPresentation
import com.sphereon.data.store.credential.design.model.SdPolicy as DesignSdPolicy
import com.sphereon.openid.oid4vci.issuer.format.SdPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DesignIssuanceSdPoliciesTest {
    private fun claim(
        vararg path: String,
        sdPolicy: DesignSdPolicy,
    ): ClaimPresentation =
        ClaimPresentation(
            path = path.map { ClaimPathSegment.Property(it) },
            labels = listOf(ClaimLabel(locale = "en", label = path.last())),
            mandatory = true,
            sdPolicy = sdPolicy,
        )

    @Test
    fun designPoliciesFollowSdJwtVcTypeMetadataSemantics() {
        val policies =
            designIssuanceSdPolicies(
                listOf(
                    claim("family_name", sdPolicy = DesignSdPolicy.ALWAYS),
                    claim("birth_date", sdPolicy = DesignSdPolicy.ALLOWED),
                    claim("address", "country", sdPolicy = DesignSdPolicy.NEVER),
                ),
            )

        assertEquals(
            mapOf(
                "family_name" to SdPolicy.SELECTIVELY_DISCLOSABLE,
                "birth_date" to SdPolicy.SELECTIVELY_DISCLOSABLE,
                "address.country" to SdPolicy.ALWAYS_DISCLOSED,
            ),
            policies,
        )
    }

    @Test
    fun noDesignPolicyDropsTheClaimFromTheCredential() {
        DesignSdPolicy.entries.forEach { policy ->
            assertNotEquals(
                SdPolicy.NEVER_DISCLOSED,
                policy.toIssuanceSdPolicy(),
                "design policy $policy must never omit the claim from the issued credential",
            )
        }
    }
}
