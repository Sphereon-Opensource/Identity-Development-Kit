/* Copyright 2026 Sphereon International B.V. SPDX-License-Identifier: Apache-2.0 */
package com.sphereon.crypto.jose.jws

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.sign.SignatureService
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommandImpl
import com.sphereon.crypto.resolution.IdentifierService
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerifyJwsFailureLoggingTest {
    @Test
    fun trustedKeyRefusalLogsClosedReasonWithoutCredentialOrKeyIdentifiers() = runTest {
        val execution = mockk<SessionExecution>(relaxed = true)
        val messages = mutableListOf<String>()
        val sessionLog = execution.log
        every { sessionLog.warn(any<String>()) } answers { messages += firstArg<String>() }
        val identifiers = mockk<IdentifierService>()
        val signatures = mockk<SignatureService>()
        val command = VerifyJwsCommandImpl(execution, identifiers, signatures)
        val header = """{"alg":"ES256","kid":"private-key-alias"}""".encodeToByteArray().encodeToBase64Url()
        val payload = """{"private":"credential-contents"}""".encodeToByteArray().encodeToBase64Url()
        val compact = "$header.$payload.c2ln"
        val result = command.execute(
            VerifyJwsArgs(jws = JwsCompact(compact), trustedJwks = Json.parseToJsonElement("""{"keys":[]}""").jsonObject),
        )
        assertTrue(result.isOk)
        assertFalse(result.value.trustEstablished)
        assertEquals(listOf("JWS_VERIFICATION_FAILED stage=trusted-key-selection reason=trusted_key_missing signatureIndex=0"), messages)
        listOf(compact, "private-key-alias", "credential-contents").forEach { assertTrue(it !in messages.toString()) }
        coVerify(exactly = 0) { identifiers.resolve(any()) }
    }

    @Test
    fun invalidSerializationLogsExceptionTypeWithoutItsMessage() = runTest {
        val execution = mockk<SessionExecution>(relaxed = true)
        val messages = mutableListOf<String>()
        val sessionLog = execution.log
        every { sessionLog.warn(any<String>()) } answers { messages += firstArg<String>() }
        val command = VerifyJwsCommandImpl(execution, mockk(), mockk())
        val result = command.execute(VerifyJwsArgs(jws = JwsCompact("private-malformed-token")))
        assertTrue(result.isErr)
        assertTrue(messages.single().startsWith("JWS_VERIFICATION_FAILED stage=serialization reason=jws_format_invalid causeType="))
        assertTrue("private-malformed-token" !in messages.toString())
    }
}
