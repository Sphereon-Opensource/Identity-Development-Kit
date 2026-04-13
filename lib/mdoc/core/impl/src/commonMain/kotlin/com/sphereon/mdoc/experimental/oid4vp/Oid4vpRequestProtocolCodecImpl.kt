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
 *
 */

package com.sphereon.mdoc.experimental.oid4vp

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.StringLabel
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.mdoc.engagement.ProtocolInfo
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<Oid4vpRequestProtocolCodec>())
class Oid4vpRequestProtocolCodecImpl : Oid4vpRequestProtocolCodec {
    override fun encode(value: Oid4vpRequestProtocol): IdkResult<ProtocolInfo, IdkError> =
        runCatchingResult("encode OID4VP protocol info") {
            CborMap(
                mutableMapOf<StringLabel, CborItem<*>>(
                    OID4VP_PROTOCOL_INFO_LABEL to
                        CborMap(
                            mutableMapOf<StringLabel, CborItem<*>>(
                                Oid4vpRequestProtocol.CREDENTIAL_FORMAT to encodeCredentialFormats(value.format),
                            ),
                        ),
                ),
            )
        }

    override fun decode(value: ProtocolInfo): IdkResult<Oid4vpRequestProtocol, IdkError> =
        runCatchingResult("decode OID4VP protocol info") {
            require(value is CborMap<*, *>) { "Protocol info needs to be a map if it contains Oid4vpRequest" }
            val protocolInfoMap = value.asMap[OID4VP_PROTOCOL_INFO_LABEL]
            checkNotNull(protocolInfoMap) { "No ${OID4VP_PROTOCOL_INFO_LABEL.value} key present in the protocol info" }
            require(protocolInfoMap is CborMap<*, *>) { "OID4VP protocol info must be encoded as a CBOR map" }

            @Suppress("UNCHECKED_CAST")
            Oid4vpRequestProtocol(
                format =
                    decodeCredentialFormats(
                        Oid4vpRequestProtocol.CREDENTIAL_FORMAT.required(
                            protocolInfoMap as CborMap<StringLabel, CborItem<*>>,
                        ),
                    ),
            )
        }
}

private fun encodeCredentialFormats(formats: MutableMap<CborString, CredentialFormat>): CborMap<CborString, CborItem<*>> =
    CborMap(
        formats.entries
            .associate { (formatId, credentialFormat) ->
                formatId to
                    CborMap(
                        mutableMapOf(
                            CredentialFormat.ALG to credentialFormat.alg,
                        ),
                    )
            }.toMutableMap(),
    )

@Suppress("UNCHECKED_CAST")
private fun decodeCredentialFormats(item: CborItem<*>): MutableMap<CborString, CredentialFormat> {
    require(item is CborMap<*, *>) { "credentialFormat must be encoded as a CBOR map" }
    return item.value.entries
        .associate { (key, value) ->
            val formatId =
                key as? CborString
                    ?: throw IllegalArgumentException("credentialFormat keys must be CBOR strings")
            val formatMap =
                value as? CborMap<StringLabel, CborItem<*>>
                    ?: throw IllegalArgumentException("credentialFormat values must be CBOR maps")
            formatId to
                CredentialFormat(
                    alg = CredentialFormat.ALG.required(formatMap),
                )
        }.toMutableMap()
}

private inline fun <T> runCatchingResult(
    operationName: String,
    block: () -> T,
): IdkResult<T, IdkError> =
    try {
        Ok(block())
    } catch (e: IllegalArgumentException) {
        Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to $operationName: ${e.message}", throwable = e))
    } catch (e: IllegalStateException) {
        Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to $operationName: ${e.message}", throwable = e))
    } catch (expected: Throwable) {
        Err(IdkError.UNKNOWN_ERROR(message = "Failed to $operationName: ${expected.message}", exception = expected))
    }
