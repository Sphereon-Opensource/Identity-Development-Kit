package com.sphereon.statuslist.impl.codec

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.statuslist.MdocStatusListCodec as MdocStatusListCodecSpi
import com.sphereon.statuslist.MdocStatusListPayload

/** Encodes only the mdoc second-edition inner payload; it never handles generic integer-key CWT maps. */
object MdocStatusListCodec : MdocStatusListCodecSpi {
    override fun encode(payload: MdocStatusListPayload): ByteArray =
        when (payload) {
            is MdocStatusListPayload.Token -> {
                val values = mutableMapOf<CborString, CborItem<*>>(
                    CborString("bits") to CborUInt(payload.bits.toLong()),
                    CborString("lst") to CborByteString(payload.list),
                )
                payload.aggregationUri?.let { values[CborString("aggregation_uri")] = CborString(it) }
                Cbor.encode(CborMap(values))
            }

            is MdocStatusListPayload.IdentifierList -> {
                val identifiers =
                    payload.identifiers.associate { identifier ->
                        CborByteString(identifier) as CborItem<*> to
                            CborMap<CborItem<*>, CborItem<*>>(mutableMapOf()) as CborItem<*>
                    }.toMutableMap()
                val values =
                    mutableMapOf<CborString, CborItem<*>>(
                        CborString("identifiers") to
                            CborMap<CborItem<*>, CborItem<*>>(identifiers),
                    )
                payload.aggregationUri?.let { values[CborString("aggregation_uri")] = CborString(it) }
                Cbor.encode(CborMap(values))
            }
        }

    override fun decode(encoded: ByteArray): MdocStatusListPayload =
        try {
            val map = Cbor.tryDecode(encoded).getOrThrow() as? CborMap<*, *>
                ?: error("mdoc status payload must be a CBOR map")
            require(map.value.keys.all { it is CborString }) { "mdoc status payload keys must be text" }
            val values = map.value.entries.associate { (key, value) -> (key as CborString).value to value }
            when {
                "bits" in values || "lst" in values -> {
                    require(values.keys.all { it == "bits" || it == "lst" || it == "aggregation_uri" })
                    val encodedBits = (values["bits"] as? CborUInt)?.value
                        ?: error("mdoc Token Status List requires integer bits")
                    require(encodedBits == 1L) { "mdoc Token Status List requires bits=1" }
                    val list = (values["lst"] as? CborByteString)?.value
                        ?: error("mdoc Token Status List requires binary lst")
                    MdocStatusListPayload.Token(1, list, optionalText(values, "aggregation_uri"))
                }

                "identifiers" in values -> {
                    require(values.keys.all { it == "identifiers" || it == "aggregation_uri" })
                    val identifierMap = values["identifiers"] as? CborMap<*, *>
                        ?: error("mdoc identifier list requires an identifiers map")
                    val identifiers = identifierMap.value.entries.map { (key, value) ->
                        val identifier = (key as? CborByteString)?.value?.copyOf()
                            ?: error("mdoc identifier list keys must be byte strings")
                        require(value is CborMap<*, *>) { "mdoc identifier list values must be IdentifierInfo maps" }
                        identifier
                    }
                    MdocStatusListPayload.IdentifierList(
                        identifiers = identifiers,
                        aggregationUri = optionalText(values, "aggregation_uri"),
                    )
                }

                else -> error("unknown mdoc status payload")
            }
        } catch (expected: IllegalArgumentException) {
            throw expected
        } catch (expected: Exception) {
            throw IllegalArgumentException("Invalid mdoc status payload", expected)
        }

    private fun optionalText(
        values: Map<String, CborItem<*>?>,
        key: String,
    ): String? =
        when (val value = values[key]) {
            null -> null
            is CborString -> value.value
            else -> throw IllegalArgumentException("mdoc status payload $key must be text")
        }
}
