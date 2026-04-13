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

package com.sphereon.openid.oid4vci.holder.dsl

import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HolderCommandDslTest {
    // -----------------------------------------------------------------------
    // requestCredentialArgs — credentialConfigurationId
    // -----------------------------------------------------------------------

    @Test
    fun requestCredentialArgsWithConfigurationId() {
        val args =
            requestCredentialArgs {
                endpoint("https://issuer.example.com/credential", accessToken = "Bearer token123")
                credentialConfigurationId("UniversityDegree")
                nonceEndpoint = "https://issuer.example.com/nonce"
            }

        assertEquals("https://issuer.example.com/credential", args.credentialEndpoint)
        assertEquals("Bearer token123", args.accessToken)
        assertEquals("UniversityDegree", args.credentialConfigurationId)
        assertNull(args.credentialIdentifier)
        assertNull(args.proofs)
        assertEquals("https://issuer.example.com/nonce", args.nonceEndpoint)
        assertNull(args.credentialResponseEncryption)
    }

    // -----------------------------------------------------------------------
    // requestCredentialArgs — credentialIdentifier
    // -----------------------------------------------------------------------

    @Test
    fun requestCredentialArgsWithCredentialIdentifier() {
        val args =
            requestCredentialArgs {
                endpoint("https://issuer.example.com/credential", accessToken = "tok-abc")
                credentialIdentifier("cred-id-xyz")
            }

        assertNull(args.credentialConfigurationId)
        assertEquals("cred-id-xyz", args.credentialIdentifier)
    }

    // -----------------------------------------------------------------------
    // requestCredentialArgs — XOR: both set must fail
    // -----------------------------------------------------------------------

    @Test
    fun requestCredentialArgsBothConfigIdAndIdentifierFails() {
        assertFailsWith<IllegalArgumentException> {
            requestCredentialArgs {
                endpoint("https://issuer.example.com/credential", accessToken = "tok")
                credentialConfigurationId("UniversityDegree")
                credentialIdentifier("cred-id-xyz")
            }
        }
    }

    // -----------------------------------------------------------------------
    // requestCredentialArgs — XOR: neither set must fail
    // -----------------------------------------------------------------------

    @Test
    fun requestCredentialArgsNeitherConfigIdNorIdentifierFails() {
        assertFailsWith<IllegalArgumentException> {
            requestCredentialArgs {
                endpoint("https://issuer.example.com/credential", accessToken = "tok")
                // neither credentialConfigurationId nor credentialIdentifier set
            }
        }
    }

    // -----------------------------------------------------------------------
    // requestCredentialArgs — missing endpoint must fail
    // -----------------------------------------------------------------------

    @Test
    fun requestCredentialArgsMissingEndpointFails() {
        assertFailsWith<IllegalArgumentException> {
            requestCredentialArgs {
                credentialConfigurationId("UniversityDegree")
            }
        }
    }

    // -----------------------------------------------------------------------
    // requestCredentialArgs — with proofs
    // -----------------------------------------------------------------------

    @Test
    fun requestCredentialArgsWithProofs() {
        val args =
            requestCredentialArgs {
                endpoint("https://issuer.example.com/credential", accessToken = "tok")
                credentialConfigurationId("UniversityDegree")
                proofs("jwt", "eyJhbGciOiJFUzI1NiJ9.proof1.sig", "eyJhbGciOiJFUzI1NiJ9.proof2.sig")
            }

        assertNotNull(args.proofs)
        assertEquals("jwt", args.proofs!!.proofType)
        assertEquals(2, args.proofs!!.proofValues.size)
    }

    // -----------------------------------------------------------------------
    // createProofArgs — all fields
    // -----------------------------------------------------------------------

    @Test
    fun createProofArgsWithAllFields() {
        val args =
            createProofArgs {
                issuerUrl("https://issuer.example.com")
                signingKey("key-id-1", JwaAlgorithm.ES384)
                nonce("c_nonce_abc")
                clientId("wallet-client")
                batch(count = 3)
                keyMode(JwsIdentifierMode.JWK)
            }

        assertEquals("https://issuer.example.com", args.issuerUrl)
        assertEquals("key-id-1", args.signingKeyId)
        assertEquals("ES384", args.signingAlgorithm)
        assertEquals("c_nonce_abc", args.cNonce)
        assertEquals("wallet-client", args.clientId)
        assertEquals(3, args.count)
        assertEquals(JwsIdentifierMode.JWK, args.keyInclusionMode)
    }

    // -----------------------------------------------------------------------
    // createProofArgs — defaults
    // -----------------------------------------------------------------------

    @Test
    fun createProofArgsDefaults() {
        val args =
            createProofArgs {
                issuerUrl("https://issuer.example.com")
                signingKey("key-id-1")
            }

        assertEquals("ES256", args.signingAlgorithm)
        assertEquals(1, args.count)
        assertEquals(JwsIdentifierMode.KID, args.keyInclusionMode)
        assertNull(args.cNonce)
        assertNull(args.clientId)
    }

    // -----------------------------------------------------------------------
    // createProofArgs — missing issuerUrl must fail
    // -----------------------------------------------------------------------

    @Test
    fun createProofArgsMissingIssuerUrlFails() {
        assertFailsWith<IllegalArgumentException> {
            createProofArgs {
                signingKey("key-id-1")
            }
        }
    }

    // -----------------------------------------------------------------------
    // createProofArgs — missing signingKey must fail
    // -----------------------------------------------------------------------

    @Test
    fun createProofArgsMissingSigningKeyFails() {
        assertFailsWith<IllegalArgumentException> {
            createProofArgs {
                issuerUrl("https://issuer.example.com")
            }
        }
    }

    // -----------------------------------------------------------------------
    // createProofArgs — batch count < 1 must fail
    // -----------------------------------------------------------------------

    @Test
    fun createProofArgsBatchCountZeroFails() {
        assertFailsWith<IllegalArgumentException> {
            createProofArgs {
                issuerUrl("https://issuer.example.com")
                signingKey("key-id-1")
                batch(count = 0)
            }
        }
    }

    // -----------------------------------------------------------------------
    // credentialFlowArgs — complete flow
    // -----------------------------------------------------------------------

    @Test
    fun credentialFlowArgsWithAllEndpoints() {
        val args =
            credentialFlowArgs {
                sessionId("sess-abc")
                endpoint("https://issuer.example.com/credential", accessToken = "Bearer flow-token")
                issuerUrl("https://issuer.example.com")
                signingKey("key-2", JwaAlgorithm.ES256)
                credentialConfigurationId("MembershipCard")
                nonceEndpoint("https://issuer.example.com/nonce")
                deferredEndpoint("https://issuer.example.com/deferred")
                notificationEndpoint("https://issuer.example.com/notification")
            }

        assertEquals("sess-abc", args.sessionId)
        assertEquals("https://issuer.example.com/credential", args.credentialEndpoint)
        assertEquals("Bearer flow-token", args.accessToken)
        assertEquals("https://issuer.example.com", args.issuerUrl)
        assertEquals("key-2", args.signingKeyId)
        assertEquals("ES256", args.signingAlgorithm)
        assertEquals("MembershipCard", args.credentialConfigurationId)
        assertNull(args.credentialIdentifier)
        assertEquals("https://issuer.example.com/nonce", args.nonceEndpoint)
        assertEquals("https://issuer.example.com/deferred", args.deferredCredentialEndpoint)
        assertEquals("https://issuer.example.com/notification", args.notificationEndpoint)
        assertNull(args.credentialResponseEncryption)
    }

    // -----------------------------------------------------------------------
    // credentialFlowArgs — credentialIdentifier variant
    // -----------------------------------------------------------------------

    @Test
    fun credentialFlowArgsWithCredentialIdentifier() {
        val args =
            credentialFlowArgs {
                sessionId("sess-1")
                endpoint("https://issuer.example.com/credential", accessToken = "tok")
                issuerUrl("https://issuer.example.com")
                signingKey("key-1")
                credentialIdentifier("ident-007")
            }

        assertNull(args.credentialConfigurationId)
        assertEquals("ident-007", args.credentialIdentifier)
    }

    // -----------------------------------------------------------------------
    // credentialFlowArgs — missing sessionId must fail
    // -----------------------------------------------------------------------

    @Test
    fun credentialFlowArgsMissingSessionIdFails() {
        assertFailsWith<IllegalArgumentException> {
            credentialFlowArgs {
                endpoint("https://issuer.example.com/credential", accessToken = "tok")
                issuerUrl("https://issuer.example.com")
                signingKey("key-1")
                credentialConfigurationId("UniversityDegree")
            }
        }
    }

    // -----------------------------------------------------------------------
    // credentialFlowArgs — XOR enforced
    // -----------------------------------------------------------------------

    @Test
    fun credentialFlowArgsBothConfigIdAndIdentifierFails() {
        assertFailsWith<IllegalArgumentException> {
            credentialFlowArgs {
                sessionId("sess-1")
                endpoint("https://issuer.example.com/credential", accessToken = "tok")
                issuerUrl("https://issuer.example.com")
                signingKey("key-1")
                credentialConfigurationId("UniversityDegree")
                credentialIdentifier("ident-007")
            }
        }
    }

    // -----------------------------------------------------------------------
    // pollDeferredArgs — interval and maxAttempts
    // -----------------------------------------------------------------------

    @Test
    fun pollDeferredArgsWithPollingParams() {
        val args =
            pollDeferredArgs {
                endpoint("https://issuer.example.com/deferred", accessToken = "Bearer def-token")
                transactionId("tx-001")
                sessionId("sess-2")
                polling(interval = 10, maxAttempts = 60)
            }

        assertEquals("https://issuer.example.com/deferred", args.deferredCredentialEndpoint)
        assertEquals("Bearer def-token", args.accessToken)
        assertEquals("tx-001", args.transactionId)
        assertEquals("sess-2", args.sessionId)
        assertEquals(10, args.interval)
        assertEquals(60, args.maxAttempts)
        assertNull(args.credentialResponseEncryption)
    }

    // -----------------------------------------------------------------------
    // pollDeferredArgs — nulls for optional polling params
    // -----------------------------------------------------------------------

    @Test
    fun pollDeferredArgsNullPollingParamsWhenNotSet() {
        val args =
            pollDeferredArgs {
                endpoint("https://issuer.example.com/deferred", accessToken = "tok")
                transactionId("tx-002")
                sessionId("sess-3")
            }

        assertNull(args.interval)
        assertNull(args.maxAttempts)
    }

    // -----------------------------------------------------------------------
    // pollDeferredArgs — missing endpoint must fail
    // -----------------------------------------------------------------------

    @Test
    fun pollDeferredArgsMissingEndpointFails() {
        assertFailsWith<IllegalArgumentException> {
            pollDeferredArgs {
                transactionId("tx-001")
                sessionId("sess-1")
            }
        }
    }

    // -----------------------------------------------------------------------
    // pollDeferredArgs — missing transactionId must fail
    // -----------------------------------------------------------------------

    @Test
    fun pollDeferredArgsMissingTransactionIdFails() {
        assertFailsWith<IllegalArgumentException> {
            pollDeferredArgs {
                endpoint("https://issuer.example.com/deferred", accessToken = "tok")
                sessionId("sess-1")
            }
        }
    }

    // -----------------------------------------------------------------------
    // pollDeferredArgs — invalid interval must fail
    // -----------------------------------------------------------------------

    @Test
    fun pollDeferredArgsIntervalZeroFails() {
        assertFailsWith<IllegalArgumentException> {
            pollDeferredArgs {
                endpoint("https://issuer.example.com/deferred", accessToken = "tok")
                transactionId("tx-001")
                sessionId("sess-1")
                polling(interval = 0)
            }
        }
    }

    // -----------------------------------------------------------------------
    // notifyWithRetryArgs — all fields
    // -----------------------------------------------------------------------

    @Test
    fun notifyWithRetryArgsAllFields() {
        val args =
            notifyWithRetryArgs {
                endpoint("https://issuer.example.com/notification", accessToken = "Bearer notif-tok")
                notificationId("notif-id-123")
                event(CredentialNotificationEvent.CREDENTIAL_ACCEPTED)
                eventDescription = "Credential stored in wallet"
                retryPolicy(maxRetries = 5, initialBackoffMs = 500L)
            }

        assertEquals("https://issuer.example.com/notification", args.notificationEndpoint)
        assertEquals("Bearer notif-tok", args.accessToken)
        assertEquals("notif-id-123", args.notificationId)
        assertEquals(CredentialNotificationEvent.CREDENTIAL_ACCEPTED, args.event)
        assertEquals("Credential stored in wallet", args.eventDescription)
        assertEquals(5, args.maxRetries)
        assertEquals(500L, args.initialBackoffMs)
    }

    // -----------------------------------------------------------------------
    // notifyWithRetryArgs — retry policy defaults
    // -----------------------------------------------------------------------

    @Test
    fun notifyWithRetryArgsDefaults() {
        val args =
            notifyWithRetryArgs {
                endpoint("https://issuer.example.com/notification", accessToken = "tok")
                notificationId("notif-id-abc")
                event(CredentialNotificationEvent.CREDENTIAL_FAILURE)
            }

        assertEquals(3, args.maxRetries)
        assertEquals(1000L, args.initialBackoffMs)
        assertNull(args.eventDescription)
    }

    // -----------------------------------------------------------------------
    // notifyWithRetryArgs — CREDENTIAL_DELETED event
    // -----------------------------------------------------------------------

    @Test
    fun notifyWithRetryArgsDeletedEvent() {
        val args =
            notifyWithRetryArgs {
                endpoint("https://issuer.example.com/notification", accessToken = "tok")
                notificationId("notif-del")
                event(CredentialNotificationEvent.CREDENTIAL_DELETED)
            }

        assertEquals(CredentialNotificationEvent.CREDENTIAL_DELETED, args.event)
    }

    // -----------------------------------------------------------------------
    // notifyWithRetryArgs — missing endpoint must fail
    // -----------------------------------------------------------------------

    @Test
    fun notifyWithRetryArgsMissingEndpointFails() {
        assertFailsWith<IllegalArgumentException> {
            notifyWithRetryArgs {
                notificationId("notif-id")
                event(CredentialNotificationEvent.CREDENTIAL_ACCEPTED)
            }
        }
    }

    // -----------------------------------------------------------------------
    // notifyWithRetryArgs — missing notificationId must fail
    // -----------------------------------------------------------------------

    @Test
    fun notifyWithRetryArgsMissingNotificationIdFails() {
        assertFailsWith<IllegalArgumentException> {
            notifyWithRetryArgs {
                endpoint("https://issuer.example.com/notification", accessToken = "tok")
                event(CredentialNotificationEvent.CREDENTIAL_ACCEPTED)
            }
        }
    }

    // -----------------------------------------------------------------------
    // notifyWithRetryArgs — missing event must fail
    // -----------------------------------------------------------------------

    @Test
    fun notifyWithRetryArgsMissingEventFails() {
        assertFailsWith<IllegalArgumentException> {
            notifyWithRetryArgs {
                endpoint("https://issuer.example.com/notification", accessToken = "tok")
                notificationId("notif-id")
            }
        }
    }

    // -----------------------------------------------------------------------
    // notifyWithRetryArgs — negative maxRetries must fail
    // -----------------------------------------------------------------------

    @Test
    fun notifyWithRetryArgsNegativeMaxRetriesFails() {
        assertFailsWith<IllegalArgumentException> {
            notifyWithRetryArgs {
                endpoint("https://issuer.example.com/notification", accessToken = "tok")
                notificationId("notif-id")
                event(CredentialNotificationEvent.CREDENTIAL_ACCEPTED)
                retryPolicy(maxRetries = -1)
            }
        }
    }
}
