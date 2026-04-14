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

package com.sphereon.oauth2.common.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Custom serializer for audience claim that handles both string and array representations
 *
 * Per OpenID Connect Core 1.0 Section 2 and RFC 7662:
 * - If single audience: serialize as string
 * - If multiple audiences: serialize as array
 * - Deserialize string as single-element list
 * - Deserialize array as list
 *
 * Used for: IdTokenPayload.aud, TokenIntrospectionResponse.aud, and other OAuth2 aud fields
 */
object AudienceSerializer : KSerializer<List<String>> {
    private val listSerializer = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: List<String>,
    ) {
        require(encoder is JsonEncoder) { "AudienceSerializer only works with JSON format" }

        val element =
            when (value.size) {
                0 -> {
                    // Empty audience: encode as absent by encoding an empty array
                    // (callers should use nullable aud fields and pass null instead of emptyList)
                    encoder.encodeJsonElement(JsonArray(emptyList()))
                    return
                }

                1 -> {
                    JsonPrimitive(value[0])
                }

                else -> {
                    JsonArray(value.map { JsonPrimitive(it) })
                }
            }
        encoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): List<String> {
        require(decoder is JsonDecoder) { "AudienceSerializer only works with JSON format" }

        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive -> listOf(element.jsonPrimitive.content)
            element is JsonArray -> element.jsonArray.map { it.jsonPrimitive.content }
            else -> throw IllegalArgumentException("Audience must be string or array")
        }
    }
}

/**
 * Custom serializer for IdTokenPayload that handles additional unknown claims
 *
 * This serializer:
 * 1. On serialize: Manually constructs JSON with all known fields + additional claims
 * 2. On deserialize: Extracts known fields, captures everything else as additional claims
 *
 * **Why this approach?**
 * kotlinx.serialization does NOT have native support for capturing unknown properties.
 * See: https://github.com/Kotlin/kotlinx.serialization/issues/1978
 * Official recommendation: implement custom serializer (which is what this is)
 *
 * **Alternatives considered:**
 * - JsonTransformingSerializer: Doesn't give us the granular control we need
 * - Delegate to default serializer: Causes recursion (can't call IdTokenPayload.serializer() inside itself)
 * - Duplicate internal data class: Just moves the brittleness elsewhere
 *
 * **Maintenance:**
 * When adding a new standard field to IdTokenPayload:
 *   1. Add the property to the data class
 *   2. Add the JSON key to knownJsonKeys set below
 *   3. Add serialization logic in serialize() method
 *   4. Add deserialization logic in deserialize() method
 *
 * The compiler helps: missing step 1 won't compile; missing steps 3-4 = fields don't serialize (caught by tests)
 */
internal object IdTokenPayloadSerializer : KSerializer<IdTokenPayload> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IdTokenPayload")

    // Define the JSON keys for all known standard properties
    // When adding a new property, add its JSON key here
    private val knownJsonKeys =
        setOf(
            "iss",
            "sub",
            "aud",
            "exp",
            "iat",
            "auth_time",
            "nonce",
            "acr",
            "amr",
            "azp",
            "at_hash",
            "c_hash",
            "name",
            "given_name",
            "family_name",
            "middle_name",
            "nickname",
            "preferred_username",
            "profile",
            "picture",
            "website",
            "email",
            "email_verified",
            "gender",
            "birthdate",
            "zoneinfo",
            "locale",
            "phone_number",
            "phone_number_verified",
            "address",
            "updated_at",
        )

    override fun serialize(
        encoder: Encoder,
        value: IdTokenPayload,
    ) {
        require(encoder is JsonEncoder) { "IdTokenPayloadSerializer only works with JSON format" }

        val jsonObject =
            buildJsonObject {
                // Required claims
                put("iss", JsonPrimitive(value.iss))
                put("sub", JsonPrimitive(value.sub))

                // Aud with special handling
                when (value.aud.size) {
                    0 -> throw IllegalArgumentException("Audience list cannot be empty")
                    1 -> put("aud", JsonPrimitive(value.aud[0]))
                    else -> put("aud", JsonArray(value.aud.map { JsonPrimitive(it) }))
                }

                put("exp", JsonPrimitive(value.exp))
                put("iat", JsonPrimitive(value.iat))

                // Optional claims (only include if non-null)
                value.authTime?.let { put("auth_time", JsonPrimitive(it)) }
                value.nonce?.let { put("nonce", JsonPrimitive(it)) }
                value.acr?.let { put("acr", JsonPrimitive(it)) }
                value.amr?.let { put("amr", JsonArray(it.map { JsonPrimitive(it) })) }
                value.azp?.let { put("azp", JsonPrimitive(it)) }
                value.atHash?.let { put("at_hash", JsonPrimitive(it)) }
                value.cHash?.let { put("c_hash", JsonPrimitive(it)) }

                value.name?.let { put("name", JsonPrimitive(it)) }
                value.givenName?.let { put("given_name", JsonPrimitive(it)) }
                value.familyName?.let { put("family_name", JsonPrimitive(it)) }
                value.middleName?.let { put("middle_name", JsonPrimitive(it)) }
                value.nickname?.let { put("nickname", JsonPrimitive(it)) }
                value.preferredUsername?.let { put("preferred_username", JsonPrimitive(it)) }
                value.profile?.let { put("profile", JsonPrimitive(it)) }
                value.picture?.let { put("picture", JsonPrimitive(it)) }
                value.website?.let { put("website", JsonPrimitive(it)) }
                value.email?.let { put("email", JsonPrimitive(it)) }
                value.emailVerified?.let { put("email_verified", JsonPrimitive(it)) }
                value.gender?.let { put("gender", JsonPrimitive(it)) }
                value.birthdate?.let { put("birthdate", JsonPrimitive(it)) }
                value.zoneinfo?.let { put("zoneinfo", JsonPrimitive(it)) }
                value.locale?.let { put("locale", JsonPrimitive(it)) }
                value.phoneNumber?.let { put("phone_number", JsonPrimitive(it)) }
                value.phoneNumberVerified?.let { put("phone_number_verified", JsonPrimitive(it)) }

                value.address?.let { addr ->
                    put(
                        "address",
                        buildJsonObject {
                            addr.formatted?.let { put("formatted", JsonPrimitive(it)) }
                            addr.streetAddress?.let { put("street_address", JsonPrimitive(it)) }
                            addr.locality?.let { put("locality", JsonPrimitive(it)) }
                            addr.region?.let { put("region", JsonPrimitive(it)) }
                            addr.postalCode?.let { put("postal_code", JsonPrimitive(it)) }
                            addr.country?.let { put("country", JsonPrimitive(it)) }
                        },
                    )
                }

                value.updatedAt?.let { put("updated_at", JsonPrimitive(it)) }

                // Add all additional claims at top level
                value.additionalClaims.forEach { (key, jsonValue) ->
                    put(key, jsonValue)
                }
            }

        encoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): IdTokenPayload {
        require(decoder is JsonDecoder) { "IdTokenPayloadSerializer only works with JSON format" }

        val jsonObject = decoder.decodeJsonElement().jsonObject

        // Extract additional claims (anything not in knownJsonKeys)
        val additionalClaims = jsonObject.filterKeys { it !in knownJsonKeys }

        // Parse aud
        val audElement = jsonObject["aud"] ?: throw IllegalArgumentException("aud is required")
        val aud =
            when {
                audElement is JsonPrimitive -> listOf(audElement.jsonPrimitive.content)
                audElement is JsonArray -> audElement.jsonArray.map { it.jsonPrimitive.content }
                else -> throw IllegalArgumentException("aud must be string or array")
            }

        // Parse address if present
        val address =
            jsonObject["address"]?.jsonObject?.let { addr ->
                AddressClaim(
                    formatted = addr["formatted"]?.jsonPrimitive?.content,
                    streetAddress = addr["street_address"]?.jsonPrimitive?.content,
                    locality = addr["locality"]?.jsonPrimitive?.content,
                    region = addr["region"]?.jsonPrimitive?.content,
                    postalCode = addr["postal_code"]?.jsonPrimitive?.content,
                    country = addr["country"]?.jsonPrimitive?.content,
                )
            }

        return IdTokenPayload(
            iss = jsonObject["iss"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("iss is required"),
            sub = jsonObject["sub"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("sub is required"),
            aud = aud,
            exp = jsonObject["exp"]?.jsonPrimitive?.content?.toLong() ?: throw IllegalArgumentException("exp is required"),
            iat = jsonObject["iat"]?.jsonPrimitive?.content?.toLong() ?: throw IllegalArgumentException("iat is required"),
            authTime = jsonObject["auth_time"]?.jsonPrimitive?.content?.toLong(),
            nonce = jsonObject["nonce"]?.jsonPrimitive?.content,
            acr = jsonObject["acr"]?.jsonPrimitive?.content,
            amr = jsonObject["amr"]?.jsonArray?.map { it.jsonPrimitive.content },
            azp = jsonObject["azp"]?.jsonPrimitive?.content,
            atHash = jsonObject["at_hash"]?.jsonPrimitive?.content,
            cHash = jsonObject["c_hash"]?.jsonPrimitive?.content,
            name = jsonObject["name"]?.jsonPrimitive?.content,
            givenName = jsonObject["given_name"]?.jsonPrimitive?.content,
            familyName = jsonObject["family_name"]?.jsonPrimitive?.content,
            middleName = jsonObject["middle_name"]?.jsonPrimitive?.content,
            nickname = jsonObject["nickname"]?.jsonPrimitive?.content,
            preferredUsername = jsonObject["preferred_username"]?.jsonPrimitive?.content,
            profile = jsonObject["profile"]?.jsonPrimitive?.content,
            picture = jsonObject["picture"]?.jsonPrimitive?.content,
            website = jsonObject["website"]?.jsonPrimitive?.content,
            email = jsonObject["email"]?.jsonPrimitive?.content,
            emailVerified = jsonObject["email_verified"]?.jsonPrimitive?.content?.toBoolean(),
            gender = jsonObject["gender"]?.jsonPrimitive?.content,
            birthdate = jsonObject["birthdate"]?.jsonPrimitive?.content,
            zoneinfo = jsonObject["zoneinfo"]?.jsonPrimitive?.content,
            locale = jsonObject["locale"]?.jsonPrimitive?.content,
            phoneNumber = jsonObject["phone_number"]?.jsonPrimitive?.content,
            phoneNumberVerified = jsonObject["phone_number_verified"]?.jsonPrimitive?.content?.toBoolean(),
            address = address,
            updatedAt = jsonObject["updated_at"]?.jsonPrimitive?.content?.toLong(),
            additionalClaims = additionalClaims,
        )
    }
}

/**
 * OpenID Connect ID Token Claims (OpenID Connect Core 1.0 Section 2)
 *
 * The ID Token is a security token that contains Claims about the Authentication
 * of an End-User by an Authorization Server.
 *
 * Used in:
 * - OpenID Connect authentication flows
 * - OpenID4VP (presentation exchange)
 * - OpenID4VCI (credential issuance)
 *
 * @property iss REQUIRED. Issuer Identifier for the Issuer of the response
 * @property sub REQUIRED. Subject Identifier
 * @property aud REQUIRED. Audience(s) that this ID Token is intended for (client_id)
 * @property exp REQUIRED. Expiration time (Unix timestamp)
 * @property iat REQUIRED. Time at which the JWT was issued (Unix timestamp)
 * @property authTime Time when the End-User authentication occurred (Unix timestamp)
 * @property nonce String value used to associate a Client session with an ID Token
 * @property acr Authentication Context Class Reference
 * @property amr Authentication Methods References
 * @property azp Authorized party - the party to which the ID Token was issued
 * @property atHash Access Token hash value (for implicit/hybrid flows)
 * @property cHash Authorization Code hash value (for hybrid flow)
 *
 * Standard UserInfo Claims (OpenID Connect Core 1.0 Section 5.1):
 * @property name End-User's full name
 * @property givenName Given name(s) or first name(s)
 * @property familyName Surname(s) or last name(s)
 * @property middleName Middle name(s)
 * @property nickname Casual name
 * @property preferredUsername Shorthand name by which the End-User wishes to be referred to
 * @property profile Profile page URL
 * @property picture Profile picture URL
 * @property website Web page or blog URL
 * @property email Email address
 * @property emailVerified True if email has been verified; otherwise false
 * @property gender Gender
 * @property birthdate Birthday in YYYY-MM-DD format
 * @property zoneinfo Time zone (e.g., "Europe/Paris")
 * @property locale Locale (e.g., "en-US")
 * @property phoneNumber Phone number (E.164 format recommended)
 * @property phoneNumberVerified True if phone number has been verified; otherwise false
 * @property address Physical mailing address
 * @property updatedAt Time the End-User's information was last updated (Unix timestamp)
 * @property additionalClaims Additional custom claims not defined in the standard (serialized at top level)
 */
@Serializable(with = IdTokenPayloadSerializer::class)
@JsExportCompat
@Suppress("NON_EXPORTABLE_TYPE")
data class IdTokenPayload(
    // Required claims (OpenID Connect Core Section 2)
    val iss: String,
    val sub: String,
    @Serializable(with = AudienceSerializer::class)
    val aud: List<String>,
    val exp: Long,
    val iat: Long,
    // Optional ID Token claims
    @SerialName("auth_time") val authTime: Long? = null,
    val nonce: String? = null,
    val acr: String? = null,
    val amr: List<String>? = null,
    val azp: String? = null,
    // Hash values for token binding
    @SerialName("at_hash") val atHash: String? = null,
    @SerialName("c_hash") val cHash: String? = null,
    // Standard UserInfo Claims (OpenID Connect Core Section 5.1)
    val name: String? = null,
    @SerialName("given_name") val givenName: String? = null,
    @SerialName("family_name") val familyName: String? = null,
    @SerialName("middle_name") val middleName: String? = null,
    val nickname: String? = null,
    @SerialName("preferred_username") val preferredUsername: String? = null,
    val profile: String? = null,
    val picture: String? = null,
    val website: String? = null,
    val email: String? = null,
    @SerialName("email_verified") val emailVerified: Boolean? = null,
    val gender: String? = null,
    val birthdate: String? = null,
    val zoneinfo: String? = null,
    val locale: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("phone_number_verified") val phoneNumberVerified: Boolean? = null,
    val address: AddressClaim? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
    // Additional custom claims (serialized at top level in JSON)
    val additionalClaims: Map<String, JsonElement> = emptyMap(),
)

/**
 * Address claim structure (OpenID Connect Core 1.0 Section 5.1.1)
 */
@JsExportCompat
@Serializable
data class AddressClaim(
    val formatted: String? = null,
    @SerialName("street_address") val streetAddress: String? = null,
    val locality: String? = null,
    val region: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    val country: String? = null,
)

/**
 * ID Token validation result
 *
 * Contains the validated payload and metadata about the validation process
 */
@JsExportCompat
data class ValidatedIdToken(
    /**
     * The validated ID Token payload
     */
    val payload: IdTokenPayload,
    /**
     * The original ID Token JWT string
     */
    val rawIdToken: String,
    /**
     * Whether the nonce matched (if nonce validation was requested)
     */
    val nonceMatched: Boolean? = null,
    /**
     * Whether the at_hash matched (if access token was provided)
     */
    val atHashMatched: Boolean? = null,
    /**
     * Whether the c_hash matched (if authorization code was provided)
     */
    val cHashMatched: Boolean? = null,
)

/**
 * ID Token validation options
 *
 * Specifies what validations to perform on the ID Token
 */
@JsExportCompat
data class IdTokenValidationOptions(
    /**
     * Expected issuer (iss claim)
     */
    val expectedIssuer: String,
    /**
     * Expected audience (aud claim) - usually client_id
     */
    val expectedAudience: String,
    /**
     * Expected nonce (for CSRF protection)
     */
    val expectedNonce: String? = null,
    /**
     * Access token for at_hash validation (implicit/hybrid flow)
     */
    val accessToken: String? = null,
    /**
     * Authorization code for c_hash validation (hybrid flow)
     */
    val authorizationCode: String? = null,
    /**
     * Maximum age of authentication (in seconds)
     * If set, validates that (now - auth_time) <= maxAge
     */
    val maxAge: Long? = null,
    /**
     * Clock skew tolerance (in seconds) for time-based validations
     * Default: 60 seconds
     */
    val clockSkewSeconds: Long = 60,
    /**
     * Whether to require auth_time claim
     */
    val requireAuthTime: Boolean = false,
    /**
     * Expected ACR (Authentication Context Class Reference) value
     */
    val expectedAcr: String? = null,
)
