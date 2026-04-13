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

package com.sphereon.crypto.resolution.managed


import com.sphereon.crypto.core.IdentifierLookupType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import com.sphereon.crypto.resolution.IdentifierTypeUtils
import kotlinx.serialization.json.JsonObject


/**
 * Union type for managed identifier options or results
 */
abstract class ManagedIdentifierOptsOrResult(
    override val method: IIdentifierMethod? = null,
    override val identifier: Any,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: IdentifierLookupType = AdditionalIdentifierLookup(),
) : IdentifierOptsOrResult(method = method, identifier = identifier, context = context, lookup = lookup) {
    override fun asOpts() = this as ManagedIdentifierOpts
    override fun asResult() = this as ManagedIdentifierResult<*>
}

data class AdditionalDidLookupInfo(
    override val noCache: Boolean = false,
    override val kid: String? = null,
    override val alias: String? = null,
    val keyType: String? = null,
//    val offlineWhenNoDIDRegistered: Boolean? = null,
//    val noVerificationMethodFallback: Boolean? = null,
    val controllerKey: Boolean? = null,
    val vmRelationship: String? = null,
    override val opts: Map<String, String>? = emptyMap(),
    override val providerId: String? = null
) : IdentifierLookupType




abstract class ManagedIdentifierOpts(
    override val identifier: Any,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: IdentifierLookupType = AdditionalIdentifierLookup(),
    override val method: IIdentifierMethod? = null
) : ManagedIdentifierOptsOrResult(identifier = identifier, method = method, context = context, lookup = lookup) {
    override val isResolved: Boolean = false
}

data class ManagedOptsDid(
    override val identifier: String,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: AdditionalDidLookupInfo = AdditionalDidLookupInfo(),

    ) : ManagedIdentifierOpts(identifier = identifier, context = context, lookup = lookup) {
    override val method = IdentifierMethodDefaults.DID
}

data class ManagedOptsKid(
    override val identifier: String,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: KeyInfoType<KeyType> = KeyInfo(kid = identifier),
) : ManagedIdentifierOpts(identifier = identifier, context = context, lookup = lookup) {
    override val method = IdentifierMethodDefaults.KID
}

data class ManagedOptsAlias(
    override val identifier: String,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: KeyInfoType<*> = KeyInfo<KeyType>(alias = identifier),
) : ManagedIdentifierOpts(identifier = identifier, context = context, lookup = lookup) {
    override val method = IdentifierMethodDefaults.KEY_ALIAS
}

data class ManagedOptsKey(
    override val identifier: KeyType,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: KeyInfoType<*> = ResolvedKeyInfo(key = identifier),
) : ManagedIdentifierOpts(identifier = identifier, context = context, lookup = lookup) {
    override val method = IdentifierMethodDefaults.KEY
}


data class ManagedOptsKeyInfo(
    override val identifier: KeyInfoType<*>,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: KeyInfoType<*> = identifier,
) : ManagedIdentifierOpts(identifier = identifier, context = context, lookup = lookup) {
    override val method = IdentifierMethodDefaults.KEY
}

data class ManagedOptsCoseKey(
    override val identifier: CoseKey,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: KeyInfoType<CoseKeyType> = ResolvedKeyInfo(key = identifier),
) : ManagedIdentifierOpts(identifier = identifier, context = context, lookup = lookup) {
    override val method = IdentifierMethodDefaults.COSE_KEY
}

data class ManagedOptsJwk(
    override val identifier: Jwk,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: KeyInfoType<Jwk> = ResolvedKeyInfo(key = identifier),
) : ManagedIdentifierOpts(identifier = identifier, context = context, lookup = lookup) {
    override val method = IdentifierMethodDefaults.JWK
}

data class ManagedOptsOID4VCIssuer(
    override val identifier: String,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: AdditionalIdentifierLookup = AdditionalIdentifierLookup(),
) : ManagedIdentifierOpts(identifier = identifier, context = context, lookup = lookup) {
    override val method = IdentifierMethodDefaults.OID4VCI_ISSUER
}

data class ManagedOptsX5c(
    override val identifier: List<String>,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: AdditionalIdentifierLookup = AdditionalIdentifierLookup(),
) : ManagedIdentifierOpts(identifier = identifier, context = context, lookup = lookup) {
    override val method = IdentifierMethodDefaults.X5C
}

/**
 * Base interface for managed identifier results
 */
abstract class ManagedIdentifierResult<KeyType : com.sphereon.crypto.core.KeyType>(
    override val method: IIdentifierMethod,
    open val keyInfo: ManagedKeyInfoType<KeyType>,
    override val context: IdentifierContext,

    override val identifier: Any,
    val resultMetadata: JsonObject = JsonObject(emptyMap()),
) : ManagedIdentifierOptsOrResult(identifier = identifier, method = method, context = context), ManagedKeyInfoType<KeyType> by keyInfo {
    override val lookup: IdentifierLookupType = keyInfo
    override val isResolved: Boolean = true

}

/**
 * Result for managed DID identifiers
 */
data class ManagedIdentifierDidResult(
    override val identifier: String,
    override val context: IdentifierContext,
    override val keyInfo: ManagedKeyInfoType<KeyType>,
    val keys: List<ManagedKeyInfoType<JwkType>>,
    val verificationMethodSection: String? = null,
    val controllerKeyId: String? = null
) : ManagedIdentifierResult<KeyType>(identifier = identifier, method = IdentifierMethodDefaults.DID, context = context, keyInfo = keyInfo)

/**
 * Result for managed JWK identifiers
 */
class ManagedIdentifierJwkResult(
    override val context: IdentifierContext,
    override val keyInfo: ManagedKeyInfoType<JwkType>,
    override val identifier: JwkType,
) : ManagedIdentifierResult<JwkType>(identifier = identifier, method = IdentifierMethodDefaults.JWK, context = context, keyInfo = keyInfo)

/**
 * Result for managed KID identifiers
 */
data class ManagedIdentifierKidResult(
    override val context: IdentifierContext,
    override val keyInfo: ManagedKeyInfoType<KeyType>,
    override val identifier: String
) : ManagedIdentifierResult<KeyType>(identifier = identifier, method = IdentifierMethodDefaults.KID, context = context, keyInfo = keyInfo)


/**
 * Result for managed Key identifiers
 */
data class ManagedIdentifierKeyResult(
    override val context: IdentifierContext,
    override val keyInfo: ManagedKeyInfoType<KeyType>,
    override val identifier: KeyType,
) : ManagedIdentifierResult<KeyType>(identifier = identifier, method = IdentifierMethodDefaults.KEY, context = context, keyInfo = keyInfo)

/**
 * Result for managed COSE Key identifiers
 */
data class ManagedIdentifierCoseKeyResult(
    override val context: IdentifierContext,
    override val keyInfo: ManagedKeyInfoType<CoseKeyType>,
    override val identifier: CoseKeyType,
) : ManagedIdentifierResult<CoseKeyType>(identifier = identifier, method = IdentifierMethodDefaults.COSE_KEY, context = context, keyInfo = keyInfo)

/**
 * Result for managed OID4VCIssuer identifiers
 */
data class ManagedIdentifierOID4VCIssuerResult(
    override val context: IdentifierContext,
    override val keyInfo: ManagedKeyInfoType<JwkType>,
    override val identifier: String,
) : ManagedIdentifierResult<JwkType>(identifier = identifier, method = IdentifierMethodDefaults.OID4VCI_ISSUER, context = context, keyInfo = keyInfo)

/**
 * Result for managed X5C identifiers
 */
data class ManagedIdentifierX5cResult(
    override val context: IdentifierContext,
    override val keyInfo: ManagedKeyInfoType<JwkType>,
    override val identifier: List<String>,
    override val x5c: Array<String>,
    val certificate: Any? = null
) : ManagedIdentifierResult<JwkType>(identifier = identifier, method = IdentifierMethodDefaults.X5C, context = context, keyInfo = keyInfo)


/**
 * Type guard functions for managed identifier options
 */
object ManagedIdentifierOptsTypeGuards {
    fun isManagedIdentifierDidOpts(opts: ManagedIdentifierOpts): Boolean {
        return (opts.method == IdentifierMethodDefaults.DID) || IdentifierTypeUtils.isDidIdentifier(opts.identifier)
    }

    fun isManagedIdentifierKidOpts(opts: ManagedIdentifierOpts): Boolean {
        return (opts.method == IdentifierMethodDefaults.KID) || IdentifierTypeUtils.isKidIdentifier(opts.identifier)
    }

    fun isManagedIdentifierKeyOpts(opts: ManagedIdentifierOpts): Boolean {
        return (opts.method == IdentifierMethodDefaults.KEY) || IdentifierTypeUtils.isKeyIdentifier(opts.identifier)
    }

    fun isManagedIdentifierCoseKeyOpts(opts: ManagedIdentifierOpts): Boolean {
        return (opts.method == IdentifierMethodDefaults.COSE_KEY) || IdentifierTypeUtils.isCoseKeyIdentifier(opts.identifier)
    }

    fun isManagedIdentifierOID4VCIssuerOpts(opts: ManagedIdentifierOpts): Boolean {
        return (opts.method == IdentifierMethodDefaults.OID4VCI_ISSUER) || IdentifierTypeUtils.isOID4VCIssuerIdentifier(opts.identifier)
    }

    fun isManagedIdentifierJwkOpts(opts: ManagedIdentifierOpts): Boolean {
        return (opts.method == IdentifierMethodDefaults.JWK) || IdentifierTypeUtils.isJwkIdentifier(opts.identifier)
    }

    fun isManagedIdentifierX5cOpts(opts: ManagedIdentifierOpts): Boolean {
        return (opts.method == IdentifierMethodDefaults.X5C) || IdentifierTypeUtils.isX5cIdentifier(opts.identifier)
    }


    fun asManagedIdentifierDidOpts(opts: ManagedIdentifierOpts): ManagedOptsDid {
        return require(isManagedIdentifierDidOpts(opts)) { "opts is not a ManagedOptsDid" }.let { opts as ManagedOptsDid }
    }

    fun asManagedIdentifierKidOpts(opts: ManagedIdentifierOpts): ManagedOptsKid {
        return require(isManagedIdentifierKidOpts(opts)) { "opts is not a ManagedIdentifierOpts.Kid" }.let { opts as ManagedOptsKid }
    }

    fun asManagedIdentifierKeyOpts(opts: ManagedIdentifierOpts): ManagedOptsKey {
        return require(isManagedIdentifierKeyOpts(opts)) { "opts is not a ManagedIdentifierOpts.Key" }.let { opts as ManagedOptsKey }
    }

    fun asManagedIdentifierCoseKeyOpts(opts: ManagedIdentifierOpts): ManagedOptsCoseKey {
        return require(isManagedIdentifierCoseKeyOpts(opts)) { "opts is not a ManagedIdentifierOpts.CoseKey" }.let { opts as ManagedOptsCoseKey }
    }

    fun asManagedIdentifierJwkOpts(opts: ManagedIdentifierOpts): ManagedOptsJwk {
        return require(isManagedIdentifierJwkOpts(opts)) { "opts is not a ManagedIdentifierOpts.Jwk" }.let { opts as ManagedOptsJwk }
    }

    fun asManagedIdentifierOID4VCIssuerOpts(opts: ManagedIdentifierOpts): ManagedOptsOID4VCIssuer {
        return require(isManagedIdentifierOID4VCIssuerOpts(opts)) { "opts is not a ManagedIdentifierOID4VCIssuerOpts" }.let { opts as ManagedOptsOID4VCIssuer }
    }

    fun asManagedIdentifierX5cOpts(opts: ManagedIdentifierOpts): ManagedOptsX5c {
        return require(isManagedIdentifierX5cOpts(opts)) { "opts is not a ManagedIdentifierOpts.X5c" }.let { opts as ManagedOptsX5c }
    }
}

/**
 * Type guard functions for managed identifier results
 */
object ManagedIdentifierResultTypeGuards {
    fun isManagedIdentifierCoseKeyResult(result: ManagedIdentifierResult<*>): Boolean {
        return result.method == IdentifierMethodDefaults.COSE_KEY
    }

    fun isManagedIdentifierDidResult(result: ManagedIdentifierResult<*>): Boolean {
        return result.method == IdentifierMethodDefaults.DID
    }

    fun isManagedIdentifierX5cResult(result: ManagedIdentifierResult<*>): Boolean {
        return result.method == IdentifierMethodDefaults.X5C
    }

    fun isManagedIdentifierJwkResult(result: ManagedIdentifierResult<*>): Boolean {
        return result.method == IdentifierMethodDefaults.JWK
    }

    fun isManagedIdentifierKidResult(result: ManagedIdentifierResult<*>): Boolean {
        return result.method == IdentifierMethodDefaults.KID
    }

    fun isManagedIdentifierKeyResult(result: ManagedIdentifierResult<*>): Boolean {
        return result.method == IdentifierMethodDefaults.KEY
    }
}

