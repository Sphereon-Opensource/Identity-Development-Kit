package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.wallet.interaction.WalletInteractionLaunchAuthority
import com.sphereon.wallet.interaction.WalletInteractionProcessBinding

/** Explicit host admission for the in-process real-protocol fixture. */
internal fun integrationLaunchAuthority(): WalletInteractionLaunchAuthority =
    WalletInteractionLaunchAuthority {
        Ok(
            WalletInteractionProcessBinding(
                tenantId = OID4VCI_TEST_TENANT_ID,
                modelId = "fides.waterdam.authorization-decision-log",
                caseType = "wallet-protocol-integration",
                caseId = "wallet-interaction-real-protocol",
                executionId = "wallet-interaction-real-protocol-execution",
                policyBindingRef = "test:waterdam-wallet-protocol-policy-binding",
                policyRevision = 1,
                action = "present-or-issue-credential",
                role = "holder",
                policyVersion = "test",
            ),
        )
    }

/** Host admission for ordinary fixtures without a process-authority store. */
internal fun integrationOrdinaryLaunchAuthority(): WalletInteractionLaunchAuthority =
    WalletInteractionLaunchAuthority { input ->
        if (input.processBinding == null) {
            Ok(null)
        } else {
            Err(IdkError.FORBIDDEN_ERROR(message = "Integration test host cannot authorize process-bound interactions"))
        }
    }
