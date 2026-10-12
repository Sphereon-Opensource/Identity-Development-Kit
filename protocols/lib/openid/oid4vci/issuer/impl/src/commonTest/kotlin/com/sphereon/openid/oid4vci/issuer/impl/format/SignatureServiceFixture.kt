package com.sphereon.openid.oid4vci.issuer.impl.format

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.sign.SignatureService
import com.sphereon.crypto.core.sign.model.*
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock

/** Retains existing payload/header assertions while exercising the shared signing boundary. */
internal fun JwtService.asSignatureServiceFixture(): SignatureService = object : SignatureService {
    override suspend fun sign(
        signInput: SignInput,
        keyInfo: KeyInfoType<*>,
        parameters: SignatureParameters?,
    ): IdkResult<SignOutput, IdkError> {
        val jws = parameters as JwsSignatureParameters
        assertNull(keyInfo.key, "Issuer must retain KMS custody")
        assertEquals((jws.issuer as ManagedOptsAlias).identifier, keyInfo.alias)
        val result = createJwsCompact(CreateJwsArgs(issuer = jws.issuer, payload = jws.payload, mode = jws.mode, opts = jws.opts))
            .getOrElse { return Err(it) }
        return Ok(SignOutputData(result.jwt.encodeToByteArray(), SignatureLevel.JWS, Clock.System.now()))
    }
    override fun supportedForms() = setOf(SignatureForm.JWS)
    override suspend fun createRawSignature(keyInfo: KeyInfoType<*>, input: ByteArray, requireX5Chain: Boolean): ByteArray = error("Unexpected raw signing")
    override suspend fun isValidRawSignature(keyInfo: KeyInfoType<*>, input: ByteArray, signature: ByteArray): Boolean = error("Unexpected verification")
    override suspend fun createDigest(request: DigestRequest): IdkResult<DigestResponse, IdkError> = error("Unexpected digest")
    override suspend fun completeSignature(request: CompleteSignatureRequest): IdkResult<SignOutput, IdkError> = error("Unexpected completion")
    override suspend fun validate(signInput: SignInput, signature: ByteArray, keyInfo: KeyInfoType<*>): IdkResult<Boolean, IdkError> = error("Unexpected validation")
}
