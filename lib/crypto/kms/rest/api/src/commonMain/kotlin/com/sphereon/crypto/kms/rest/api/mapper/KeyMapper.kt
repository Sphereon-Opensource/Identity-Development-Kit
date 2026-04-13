/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.crypto.kms.rest.api.mapper

import com.sphereon.core.api.error.NotFoundException
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyJsonType
import com.sphereon.crypto.core.cose.CoseKeyOperations
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.generic.CoseKeyPair
import com.sphereon.crypto.core.generic.JoseKeyPair
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.JwkUse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.sphereon.crypto.kms.rest.api.generated.models.CoseKey as CoseKeyRest
import com.sphereon.crypto.kms.rest.api.generated.models.CoseKeyPair as CoseKeyPairRest
import com.sphereon.crypto.kms.rest.api.generated.models.CoseKeyType as CoseKeyTypeRest
import com.sphereon.crypto.kms.rest.api.generated.models.Curve as CurveRest
import com.sphereon.crypto.kms.rest.api.generated.models.JoseKeyPair as JoseKeyPairRest
import com.sphereon.crypto.kms.rest.api.generated.models.Jwk as JwkRest
import com.sphereon.crypto.kms.rest.api.generated.models.JwkKeyType as JwkKeyTypeRest
import com.sphereon.crypto.kms.rest.api.generated.models.JwkUse as JwkUseRest
import com.sphereon.crypto.kms.rest.api.generated.models.KeyEncoding as KeyEncodingRest
import com.sphereon.crypto.kms.rest.api.generated.models.KeyInfo as KeyInfoRest
import com.sphereon.crypto.kms.rest.api.generated.models.KeyOperations as KeyOperationsRest
import com.sphereon.crypto.kms.rest.api.generated.models.KeyType as KeyTypeRest
import com.sphereon.crypto.kms.rest.api.generated.models.KeyVisibility as KeyVisibilityRest
import com.sphereon.crypto.kms.rest.api.generated.models.ListKeysResponse as ListKeysResponseRest
import com.sphereon.crypto.kms.rest.api.generated.models.ManagedKeyInfo as ManagedKeyInfoRest
import com.sphereon.crypto.kms.rest.api.generated.models.ManagedKeyPair as ManagedKeyPairRest
import com.sphereon.crypto.kms.rest.api.generated.models.ResolvedKeyInfo as ResolvedKeyInfoRest
import com.sphereon.crypto.kms.rest.api.generated.models.SignatureAlgorithm as SignatureAlgorithmRest


fun JwaKeyType.toRest() = JwkKeyTypeRest.valueOf(this.value)
fun JwkKeyTypeRest.toSdk() = JwaKeyType.valueOf(this.value)
fun JwkUse?.toRest() = this?.let { JwkUseRest.valueOf(it.value) }
fun JwkUseRest?.toSdk() = this?.let { JwkUse.valueOf(it.value).value }
internal fun String?.jwkUseToRest() = this?.let { JwkUseRest.valueOf(it) }
fun JwaCurve?.toRest() = this?.let { CurveRest.decode(it) }
fun CurveRest?.toSdk() = this?.let { JwaCurve.fromValue(it.value) }

fun SignatureAlgorithm?.toRest() = this?.let { SignatureAlgorithmRest.decode(it::class.simpleName) }
fun JwkType.toRest(): JwkRest {
    return JwkRest(
        kty = this.kty.toRest(),
        kid = this.kid,
        alg = this.alg?.value,
        use = this.use?.jwkUseToRest(),
        keyOps = this.key_ops?.toRest(),
        crv = this.crv?.toRest(),
        x = this.x,
        y = this.y,
        d = this.d,
        n = this.n,
        e = this.e,
        p = this.p,
        q = this.q,
        dp = this.dP,
        qi = this.qInv,
        k = this.k,
        x5c = this.x5c,
        x5t = this.x5t,
        x5u = this.x5u,
        x5tHashS256 = this.x5t_S256
    )
}

fun Array<KeyOperationsRest>?.toSdk() =
    this?.map { JoseKeyOperations.valueOf(it.value.uppercase()) }?.toTypedArray()

fun Array<out KeyOperations>?.toRest() = this?.mapNotNull { KeyOperationsRest.decode(it.jose.value) }?.toTypedArray()
fun JwkRest.toSdk(): JwkType {
    return Jwk(
        kty = this.kty.toSdk(),
        kid = this.kid,
        alg = this.alg?.let { JwaAlgorithm.valueOf(it) },
        use = this.use?.toSdk(),
        key_ops = this.keyOps?.toSdk(),
        crv = this.crv?.toSdk(),
        x = this.x,
        y = this.y,
        d = this.d,
        n = this.n,
        e = this.e,
        p = this.p,
        q = this.q,
        dP = this.dp,
        qInv = this.qi,
        k = this.k,
        x5c = this.x5c,
        x5t = this.x5t,
        x5u = this.x5u,
        x5t_S256 = this.x5tHashS256
    )
}

fun Array<JoseKeyOperations>?.toRest(): Array<KeyOperationsRest>? {
    return this?.map { KeyOperationsRest.decode(it.value)!! }?.toTypedArray()
}

fun CoseKeyType.toRest(): CoseKeyRest {
    return CoseKeyRest(
        kty = CoseKeyTypeRest.decode(this.getKeyType().cose.value) ?: throw IllegalArgumentException("Unknown COSE key type: ${this.getKeyType().cose.value}"),
        kid = this.getKeyId(),
        alg = this.alg?.asInt,
        keyOps = this.getKeyOperations()?.map { it.cose.value }?.toTypedArray(),
        baseIV = this.baseIV?.asStr,
        crv = this.crv?.asInt,
        x = this.getXAsString(),
        y = this.getYAsString(),
        d = this.getDAsString(),
        x5chain = this.getX509CertificateChain(),
    )
}

fun CoseKeyRest.toSdk(): CoseKeyJsonType {
    return CoseKeyJson(
        kty = CoseKeyTypeEnum.fromValue(this.kty.value.toLong()),
        kid = this.kid,
        alg = this.alg?.let { CoseAlgorithm.fromValue(it, null) },
        d = this.d,
        x = this.x,
        y = this.y,
        crv = this.crv?.let { CoseCurve.fromValue(it) },
        baseIV = this.baseIV,
        key_ops = this.keyOps?.map { CoseKeyOperations.fromValue(it) }?.toTypedArray(),
        x5chain = this.x5chain,
        generateKid = false
    )

}

fun ManagedKeyPair.toRest(): ManagedKeyPairRest {
    return ManagedKeyPairRest(
        kid = this.kid,
        providerId = this.providerId,
        alias = this.alias,
        cose = this.cose.toRest(),
        jose = this.jose.toRest(),
    )
}

fun ManagedKeyPairRest.toSdk(): ManagedKeyPair {
    return ManagedKeyPair(
        kid = this.kid,
        providerId = this.providerId,
        alias = this.alias,
        cose = this.cose.toSdk(),
        jose = this.jose.toSdk(),
    )
}

fun CoseKeyPair.toRest(): CoseKeyPairRest {
    return CoseKeyPairRest(
        privateCoseKey = this.privateCoseKey?.toRest(),
        publicCoseKey = this.publicCoseKey.toRest()
    )
}

fun CoseKeyPairRest.toSdk(): CoseKeyPair {
    return CoseKeyPair(
        privateCoseKey = this.privateCoseKey?.let { CoseKeyJson.fromJsonDTO(it.toSdk()).toCbor() },
        publicCoseKey = CoseKeyJson.fromJsonDTO(this.publicCoseKey.toSdk()).toCbor()
    )
}

fun JoseKeyPair.toRest(): JoseKeyPairRest {
    return JoseKeyPairRest(
        privateJwk = this.privateJwk?.toRest(),
        publicJwk = this.publicJwk.toRest()
    )
}

fun JoseKeyPairRest.toSdk(): JoseKeyPair {
    return JoseKeyPair(
        privateJwk = this.privateJwk?.toSdk()?.let { Jwk.from(it) },
        publicJwk = Jwk.from(this.publicJwk.toSdk())
    )
}


fun ManagedKeyInfoType<*>.toRest(): ManagedKeyInfoRest {
    return ManagedKeyInfoRest(
        key = (this.key as Jwk).toRest(),
        alias = this.alias,
        providerId = this.providerId,
        kid = this.kid,
        signatureAlgorithm = this.signatureAlgorithm
            ?.let { algo -> algo::class.simpleName }
            ?.let { name -> SignatureAlgorithmRest.decode(name) },
        keyVisibility = this.keyVisibility?.let { KeyVisibilityRest.valueOf(it.name) },
        x5c = this.x5c,
        keyType = this.keyType?.let { KeyTypeRest.valueOf(it.jose.value.uppercase()) },
        keyEncoding = this.keyEncoding?.let { KeyEncodingRest.valueOf(it.name.uppercase()) },
        opts = this.opts
    )
}

fun ManagedKeyInfoRest.toSdk(): ManagedKeyInfoType<*> {
    return ManagedKeyInfo(
        alias = this.alias,
        providerId = this.providerId,
        resolvedKeyInfo = ResolvedKeyInfo(
            alias = this.alias,
            kid = this.kid,
            providerId = this.providerId,
            signatureAlgorithm = this.signatureAlgorithm
                ?.let { algo -> algo::class.simpleName }
                ?.let { name -> SignatureAlgorithm.fromValue(name) },
            keyVisibility = this.keyVisibility?.let { KeyVisibility.valueOf(it.name) },
            x5c = this.x5c,
            keyEncoding = this.keyEncoding?.let { KeyEncoding.valueOf(it.name.uppercase()) },
            opts = this.opts,
            keyType = this.keyType?.let { KeyTypeMapping.fromValue(it.value.uppercase()) },
            key = this.key.toSdk()
        )
    )
}

fun ListKeysResponseRest.toSdk() = this.keyInfos.map { it.toSdk() }.toTypedArray()

fun ResolvedKeyInfoType<*>.toRest(): ResolvedKeyInfoRest {
    return ResolvedKeyInfoRest(
        key = (this.key as Jwk).toRest(),
        kid = this.kid,
        signatureAlgorithm = this.signatureAlgorithm
            ?.let { algo -> algo::class.simpleName }
            ?.let { name -> SignatureAlgorithmRest.decode(name) },
        keyVisibility = this.keyVisibility?.let { KeyVisibilityRest.valueOf(it.name) },
        x5c = this.x5c,
        alias = this.alias,
        providerId = this.providerId,
        keyType = this.keyType?.let { KeyTypeRest.valueOf(it.jose.value.uppercase()) },
        keyEncoding = this.keyEncoding?.let { KeyEncodingRest.valueOf(it.name.uppercase()) },
        opts = this.opts
    )
}

fun ResolvedKeyInfoRest.toSdk(): ResolvedKeyInfoType<*> {
    return ResolvedKeyInfo(
        alias = this.alias,
        kid = this.kid,
        providerId = this.providerId,
        signatureAlgorithm = this.signatureAlgorithm
            ?.let { algo -> algo::class.simpleName }
            ?.let { name -> SignatureAlgorithm.fromValue(name) },
        keyVisibility = this.keyVisibility?.let { KeyVisibility.valueOf(it.name) },
        x5c = this.x5c,
        keyEncoding = this.keyEncoding?.let { KeyEncoding.valueOf(it.name.uppercase()) },
        opts = this.opts,
        keyType = this.keyType?.let { KeyTypeMapping.fromValue(it.value.uppercase()) },
        key = this.key.toSdk()
    )
}

fun KeyInfoRest.toSdk(): KeyInfoType<*> {
    return KeyInfo(
        alias = this.alias,
        kid = this.kid,
        providerId = this.providerId,
        signatureAlgorithm = this.signatureAlgorithm
            ?.let { algo -> algo::class.simpleName }
            ?.let { name -> SignatureAlgorithm.fromValue(name) },
        keyVisibility = this.keyVisibility?.let { KeyVisibility.valueOf(it.name) },
        x5c = this.x5c,
        keyEncoding = this.keyEncoding?.let { KeyEncoding.valueOf(it.name.uppercase()) },
        opts = this.opts,
        keyType = this.keyType?.let { KeyTypeMapping.fromValue(it.value.uppercase()) },
        key = this.key?.toSdk() as? Jwk
    )
}

fun KeyInfoType<*>.toRest(): KeyInfoRest {
    val jwkKey = this.key as? Jwk
    return KeyInfoRest(
        key = jwkKey?.toRest(),
        alias = this.alias,
        providerId = this.providerId,
        kid = this.kid,
        signatureAlgorithm = this.signatureAlgorithm
            ?.let { algo -> algo::class.simpleName }
            ?.let { name -> SignatureAlgorithmRest.decode(name) },
        keyVisibility = this.keyVisibility?.let { KeyVisibilityRest.valueOf(it.name) },
        x5c = this.x5c,
        keyType = this.keyType?.let { KeyTypeRest.valueOf(it.jose.value.uppercase()) },
        keyEncoding = this.keyEncoding?.let { KeyEncodingRest.valueOf(it.name.uppercase()) },
        opts = this.opts
    )
}

fun Array<ManagedKeyInfoRest>.toRestResponse(): ListKeysResponseRest {
    return ListKeysResponseRest(
        keyInfos = this,
    )
}

fun mapToJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> mapToJsonObject(value)
    is Iterable<*> -> JsonArray(value.map { mapToJsonElement(it) })
    is Array<*> -> JsonArray(value.map { mapToJsonElement(it) })
    else -> JsonPrimitive(value.toString())
}

fun mapToJsonObject(input: Map<*, *>?): JsonObject {
    if (input == null) return JsonObject(emptyMap())
    val content = input.mapNotNull { (key, value) ->
        val keyStr = key as? String ?: return@mapNotNull null
        keyStr to mapToJsonElement(value)
    }.toMap()
    return JsonObject(content)
}
