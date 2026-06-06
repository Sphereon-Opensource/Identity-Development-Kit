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

package com.sphereon.statuslist.impl.verify

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.statuslist.CredentialStatusDecision
import com.sphereon.statuslist.CredentialStatusPolicy
import com.sphereon.statuslist.ResolveStatusArgs
import com.sphereon.statuslist.ResolvedStatus
import com.sphereon.statuslist.StatusValues
import com.sphereon.statuslist.evaluateCredentialStatus
import com.sphereon.statuslist.spi.CredentialStatusVerifier
import com.sphereon.statuslist.spi.StatusListResolver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Resolver stub returning a fixed status value (or a failure) so the evaluation matrix is deterministic. */
private class FakeResolver(
    private val value: Int,
    private val fail: Boolean = false,
) : StatusListResolver {
    override suspend fun resolveStatus(args: ResolveStatusArgs): IdkResult<ResolvedStatus, IdkError> =
        if (fail) {
            Err(IdkError.UNKNOWN_ERROR(message = "boom"))
        } else {
            Ok(ResolvedStatus(value = value, valid = value == StatusValues.VALID, statusListUri = args.uri))
        }
}

class CredentialStatusVerificationTest {
    private fun claims(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun tokenVerifiers(
        value: Int,
        fail: Boolean = false,
    ): Set<CredentialStatusVerifier> = setOf(TokenStatusListCredentialStatusVerifier(FakeResolver(value, fail)))

    @Test
    fun tokenReferenceExtraction() {
        val verifier = TokenStatusListCredentialStatusVerifier(FakeResolver(0))
        val refs = verifier.references(claims("""{"status":{"status_list":{"uri":"https://x/sl","idx":5}}}"""))
        assertEquals(1, refs.size)
        assertEquals("https://x/sl", refs[0].uri)
        assertEquals(5, refs[0].index)
        assertTrue(verifier.references(claims("""{"foo":1}""")).isEmpty())
    }

    @Test
    fun bitstringReferenceExtraction() {
        val verifier = BitstringStatusListCredentialStatusVerifier(FakeResolver(0))
        val refs =
            verifier.references(
                claims(
                    """{"credentialStatus":{"type":"BitstringStatusListEntry","statusListCredential":"https://x/bs","statusListIndex":"7","statusPurpose":"revocation"}}""",
                ),
            )
        assertEquals(1, refs.size)
        assertEquals(7, refs[0].index)
        assertEquals("https://x/bs", refs[0].uri)

        // Also recognized under a `vc` envelope.
        val nested =
            verifier.references(
                claims(
                    """{"vc":{"credentialStatus":{"type":"BitstringStatusListEntry","statusListCredential":"https://x/bs","statusListIndex":"3"}}}""",
                ),
            )
        assertEquals(3, nested.single().index)

        // A non-Bitstring credentialStatus is ignored.
        assertTrue(
            verifier.references(claims("""{"credentialStatus":{"type":"OtherEntry","statusListCredential":"https://x/bs","statusListIndex":"1"}}""")).isEmpty(),
        )
    }

    @Test
    fun acceptanceMatrix() =
        runTest {
            val claims = claims("""{"status":{"status_list":{"uri":"https://x/sl","idx":1}}}""")

            // Active is accepted by default.
            assertEquals(CredentialStatusDecision.ACCEPT, evaluateCredentialStatus(tokenVerifiers(StatusValues.VALID), claims, CredentialStatusPolicy()).decision)
            // Revoked / suspended are rejected by default, accepted only when explicitly allowed.
            assertEquals(CredentialStatusDecision.REJECT, evaluateCredentialStatus(tokenVerifiers(StatusValues.INVALID), claims, CredentialStatusPolicy()).decision)
            assertEquals(
                CredentialStatusDecision.ACCEPT,
                evaluateCredentialStatus(tokenVerifiers(StatusValues.INVALID), claims, CredentialStatusPolicy(acceptRevoked = true)).decision,
            )
            assertEquals(CredentialStatusDecision.REJECT, evaluateCredentialStatus(tokenVerifiers(StatusValues.SUSPENDED), claims, CredentialStatusPolicy()).decision)
            assertEquals(
                CredentialStatusDecision.ACCEPT,
                evaluateCredentialStatus(tokenVerifiers(StatusValues.SUSPENDED), claims, CredentialStatusPolicy(acceptSuspended = true)).decision,
            )
            // Unresolvable fails closed by default; accepted only when rejectOnUnresolvable is off.
            assertEquals(CredentialStatusDecision.REJECT, evaluateCredentialStatus(tokenVerifiers(0, fail = true), claims, CredentialStatusPolicy()).decision)
            assertEquals(
                CredentialStatusDecision.ACCEPT,
                evaluateCredentialStatus(tokenVerifiers(0, fail = true), claims, CredentialStatusPolicy(rejectOnUnresolvable = false)).decision,
            )
        }

    @Test
    fun requireStatusAndEmptySet() =
        runTest {
            val noRef = claims("""{"foo":1}""")
            val verifiers = tokenVerifiers(StatusValues.VALID)

            // No reference + not required → accept (nothing to check).
            assertEquals(CredentialStatusDecision.ACCEPT, evaluateCredentialStatus(verifiers, noRef, CredentialStatusPolicy()).decision)
            // No reference + requireStatus → reject.
            assertEquals(CredentialStatusDecision.REJECT, evaluateCredentialStatus(verifiers, noRef, CredentialStatusPolicy(requireStatus = true)).decision)
            // Empty verifier set → skipped, even with requireStatus (nothing wired to enforce it).
            assertEquals(CredentialStatusDecision.SKIPPED, evaluateCredentialStatus(emptySet(), noRef, CredentialStatusPolicy(requireStatus = true)).decision)
        }
}
