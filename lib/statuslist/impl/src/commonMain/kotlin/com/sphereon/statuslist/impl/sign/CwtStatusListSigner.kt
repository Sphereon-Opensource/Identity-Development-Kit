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

package com.sphereon.statuslist.impl.sign

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.dsl.encode
import com.sphereon.cbor.toCborByteString
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.CoseCryptoService
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseSign1CborCodec
import com.sphereon.crypto.core.cose.CoseSign1Input
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.did.models.VerificationPurpose
import com.sphereon.statuslist.StatusListContentTypes
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.spi.SignStatusListTokenArgs
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Signs an IETF Token Status List token in CWT form (`application/statuslist+cwt`) as a COSE_Sign1
 * over a CWT claim set, mirroring how the credentials that reference the list are signed
 * (`signingKeyMode` → DID `kid` or `x5chain`). The JWT form lives in [JwsStatusListSigner], which
 * delegates the `TOKEN_STATUS_LIST` + `CWT` case here.
 *
 * CWT claims (RFC 8392 + the status-list draft, integer keys):
 * `1`=iss, `2`=sub (= the list URI), `6`=iat, `4`=exp?, `65534`=ttl?,
 * `65533`=status_list `{ 0: bits, 1: lst }` where `lst` is the RAW zlib-compressed byte string.
 * The protected header carries `alg`, the `typ` (label 16) = `application/statuslist+cwt`, and the
 * key reference (`kid` for DID mode, `x5chain` for x5c mode).
 */
@Inject
@SingleIn(SessionScope::class)
class CwtStatusListSigner(
    private val coseCryptoService: CoseCryptoService,
    private val coseSign1Codec: CoseSign1CborCodec,
    private val kms: KeyManagerService,
    private val didProviderRegistry: DidProviderRegistry,
) {
    suspend fun sign(args: SignStatusListTokenArgs): IdkResult<StatusListToken, IdkError> {
        val managed =
            kms.getKeyResult(KeyInfo<Nothing>(alias = args.signingKeyAlias)).getOrElse { return Err(it) }.key
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "No key for alias '${args.signingKeyAlias}'"))
        val coseKeyInfo: ManagedKeyInfoType<CoseKeyType> =
            ManagedKeyInfo(
                alias = managed.alias,
                providerId = managed.providerId,
                resolvedKeyInfo = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(managed),
            )

        // The public Jwk is already in hand from the resolved key — reuse it for the header (DID kid /
        // x5chain) instead of re-fetching from the KMS.
        val (issuer, header, requireX5Chain) = resolveHeader(args, coseKeyInfo, managed.key as? Jwk)
        val payload = buildClaims(args, issuer).encode()

        val input =
            CoseSign1Input
                .Builder()
                .withPayload(payload)
                .withEncodePayloadAsDataItem(true)
                .withProtectedHeader(header)
                .build()

        val signResult =
            try {
                coseCryptoService.sign1<Any>(input = input, keyInfo = coseKeyInfo, requireX5Chain = requireX5Chain)
            } catch (e: Exception) {
                return Err(
                    IdkError.fromString(
                        code = "STATUSLIST_CWT_SIGN_FAILED",
                        message = "COSE_Sign1 signing failed: ${e.message}",
                        category = ErrorCategory.INTERNAL,
                        exception = e,
                    ),
                )
            }
        val bytes = coseSign1Codec.encode(signResult.coseSign1).getOrElse { return Err(it) }
        return Ok(
            StatusListToken(
                token = bytes.encodeToBase64Url(),
                contentType = StatusListContentTypes.STATUSLIST_CWT,
                ttlSeconds = args.ttlSeconds,
                tokenBytes = bytes,
            ),
        )
    }

    /** Build the CWT claims map (integer keys). `lst` is the raw zlib bytes (the base64url of the encoded list, decoded). */
    private fun buildClaims(
        args: SignStatusListTokenArgs,
        issuer: String,
    ): CborMap<NumberLabel, CborItem<*>> {
        val statusList =
            CborMap<NumberLabel, CborItem<*>>(
                mutableMapOf(
                    NumberLabel(0) to CborUInt(args.bitsPerStatus.toLong()),
                    NumberLabel(1) to CborByteString(args.encodedList.decodeFromBase64Url()),
                ),
            )
        val claims =
            mutableMapOf<NumberLabel, CborItem<*>>(
                NumberLabel(CWT_ISS) to CborString(issuer),
                NumberLabel(CWT_SUB) to CborString(args.statusListUri),
                NumberLabel(CWT_IAT) to CborUInt(args.issuedAtEpochSeconds),
                NumberLabel(CWT_STATUS_LIST) to statusList,
            )
        args.expiresAtEpochSeconds?.let { claims[NumberLabel(CWT_EXP)] = CborUInt(it) }
        args.ttlSeconds?.let { claims[NumberLabel(CWT_TTL)] = CborUInt(it) }
        return CborMap(claims)
    }

    /**
     * Build the protected header (alg + typ + key reference) for the configured `signingKeyMode`,
     * returning the (possibly DID-rooted) issuer, the header, and whether to require an x5chain.
     * Resolution failures fall back to the KMS default rather than failing the whole list.
     */
    private suspend fun resolveHeader(
        args: SignStatusListTokenArgs,
        coseKeyInfo: ManagedKeyInfoType<CoseKeyType>,
        jwk: Jwk?,
    ): ResolvedCwtHeader {
        val alg: CoseAlgorithm? =
            coseKeyInfo.signatureAlgorithm?.cose ?: coseKeyInfo.key.alg?.let { CoseAlgorithm.fromValue(it.value.toInt()) }
        val header =
            CoseHeaderCbor(alg = alg, typ = CborString(StatusListContentTypes.STATUSLIST_CWT))
        val mode = args.signingKeyMode
        return when {
            mode != null && mode.startsWith("did:") -> {
                val vmId = jwk?.let { resolveDidVerificationMethodId(it, mode.removePrefix("did:")) }
                if (vmId == null) {
                    ResolvedCwtHeader(args.issuer, header, false)
                } else {
                    header.kid = vmId.toCborByteString(Encoding.UTF8)
                    ResolvedCwtHeader(vmId.substringBefore('#'), header, false)
                }
            }

            mode != null && mode.equals("x5c", ignoreCase = true) -> {
                val chain = jwk?.x5c
                if (chain != null) {
                    header.x5chain = CborArray(chain.map { it.toCborByteString(Encoding.BASE64) }.toMutableList())
                    ResolvedCwtHeader(args.issuer, header, true)
                } else {
                    ResolvedCwtHeader(args.issuer, header, false)
                }
            }

            else -> {
                ResolvedCwtHeader(args.issuer, header, false)
            }
        }
    }

    private suspend fun resolveDidVerificationMethodId(
        jwk: Jwk,
        method: String,
    ): String? {
        val provider = didProviderRegistry.getProvider(method) ?: return null
        val created =
            provider
                .create(DidCreateOptions(method = method, publicKeyJwk = jwk.toPublicKey()))
                .getOrNull() ?: return null
        return created.verificationMethodsByPurpose[VerificationPurpose.ASSERTION_METHOD]
            ?.firstOrNull()
            ?.id
            ?: "${created.did}#0"
    }

    /** The protected header plus the (possibly DID-rooted) issuer and whether to require an x5chain. */
    private data class ResolvedCwtHeader(
        val issuer: String,
        val header: CoseHeaderCbor,
        val requireX5Chain: Boolean,
    )

    private companion object {
        const val CWT_ISS = 1
        const val CWT_SUB = 2
        const val CWT_EXP = 4
        const val CWT_IAT = 6
        const val CWT_TTL = 65534
        const val CWT_STATUS_LIST = 65533
    }
}
