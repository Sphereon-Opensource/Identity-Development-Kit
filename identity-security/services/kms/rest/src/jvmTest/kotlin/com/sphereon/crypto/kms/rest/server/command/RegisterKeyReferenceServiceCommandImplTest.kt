/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReferenceFilter
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.key.persistence.KeyReferenceHistoryCapability
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.crypto.kms.rest.api.command.RegisterKeyReferenceInput
import com.sphereon.crypto.key.persistence.KeyReferenceResolutionException
import com.sphereon.crypto.key.persistence.KeyReferenceStoreErrorCodes
import com.sphereon.crypto.kms.rest.api.command.RegisterKeyReferenceResponse
import com.sphereon.crypto.kms.rest.server.adapter.keyDeleteErrorResponse
import com.sphereon.crypto.kms.rest.server.adapter.keyDeleteNotFoundResponse
import com.sphereon.crypto.kms.rest.server.adapter.registrationHttpStatus
import com.sphereon.crypto.kms.rest.server.service.ProviderKeyReferenceInspector
import com.sphereon.core.api.session.CommandLifecycleInterceptorChain
import com.sphereon.core.api.session.EmptyInterceptorChain
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.UserContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class RegisterKeyReferenceServiceCommandImplTest {
    @Test
    fun registrationErrorsMapToTheRequiredHttpStatuses() {
        assertEquals(400, registrationHttpStatus(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "malformed")))
        assertEquals(404, registrationHttpStatus(IdkError.NOT_FOUND_ERROR(message = "missing")))
        assertEquals(
            404,
            registrationHttpStatus(IdkError.fromString(code = "KMS_PROVIDER_NOT_FOUND", message = "missing")),
        )
        assertEquals(
            409,
            registrationHttpStatus(IdkError.fromString(code = "KMS_EXTERNAL_KEY_IDENTITY_MISMATCH", message = "conflict")),
        )
        assertEquals(
            409,
            registrationHttpStatus(IdkError.fromString(code = "KMS_EXTERNAL_KEY_REGISTRATION_CONFLICT", message = "conflict")),
        )
        assertEquals(
            409,
            registrationHttpStatus(
                IdkError.fromString(code = KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED, message = "unsupported"),
            ),
        )
        assertEquals(500, registrationHttpStatus(IdkError.UNKNOWN_ERROR(message = "persistence")))
    }

    @Test
    fun deleteAmbiguityMapsToStableConflictResponse() {
        val response =
            keyDeleteErrorResponse(
                KeyReferenceResolutionException(
                    code = KeyReferenceStoreErrorCodes.AMBIGUOUS_REFERENCE,
                    message = "More than one key reference matches",
                ),
            )

        assertEquals(409, response.statusCode)
        assertTrue(response.body!!.contains("\"code\":\"${KeyReferenceStoreErrorCodes.AMBIGUOUS_REFERENCE}\""))
    }

    @Test
    fun unsupportedDurableHistoryMapsToStableConflictResponse() {
        val response =
            keyDeleteErrorResponse(
                KeyReferenceResolutionException(
                    code = KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED,
                    message = "Durable ownership history is unavailable",
                ),
            )

        assertEquals(409, response.statusCode)
        assertTrue(response.body!!.contains("\"code\":\"${KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED}\""))
    }

    @Test
    fun unrelatedDeleteFailureRemainsInternalServerError() {
        val response = keyDeleteErrorResponse(RuntimeException("database unavailable"))

        assertEquals(500, response.statusCode)
    }

    @Test
    fun falseDeleteResultMapsToNotFoundInsteadOfNoContent() {
        val response = keyDeleteNotFoundResponse("missing-key")

        assertEquals(404, response.statusCode)
        assertTrue(response.body!!.contains("Key not found: missing-key"))
    }

    @Test
    fun registrationResponseAddsOwnershipFieldsWithoutBreakingOldPayloads() {
        val oldPayload = """{"registered":true,"alias":"alias","providerId":"provider","kid":"kid"}"""
        val decoded = Json.decodeFromString<RegisterKeyReferenceResponse>(oldPayload)

        assertEquals(Origin.EXTERNAL, decoded.origin)
        assertEquals(ResourceControlMode.EXTERNALLY_MANAGED, decoded.controlMode)
    }

    @Test
    fun safePublicJwkStripsPrivateMaterialAndRejectsSymmetricMaterial() {
        val privateKey =
            ManagedKeyInfo.fromKeyInfo(
                KeyInfo(
                    key =
                        Jwk(
                            kty = JwaKeyType.EC,
                            kid = "provider-kid",
                            crv = JwaCurve.P_256,
                            x = "x",
                            y = "y",
                            d = "private",
                            k = "symmetric-material-must-not-survive",
                            x5c = arrayOf("leaf-certificate", "issuer-certificate"),
                            x5t = "sha1-thumbprint",
                            x5u = "https://provider.invalid/certificate",
                            x5t_S256 = "sha256-thumbprint",
                    ),
                    alias = "alias",
                    providerId = "provider",
                    x5c = arrayOf("leaf-certificate", "issuer-certificate"),
                ),
            )
        val symmetricKey =
            ManagedKeyInfo.fromKeyInfo(
                KeyInfo(
                    key = Jwk(kty = JwaKeyType.oct, k = "symmetric"),
                    alias = "alias",
                    providerId = "provider",
                ),
            )

        val publicJwkResult = privateKey.safePublicJwk()
        assertTrue(publicJwkResult.isOk)
        val publicJwk = publicJwkResult.value!!

        assertFalse(publicJwk.contains("\"d\""))
        assertFalse(publicJwk.contains("\"k\""))
        assertFalse(publicJwk.contains("\"x5u\""))
        assertTrue(publicJwk.contains("\"x5t#S256\":\"sha256-thumbprint\""))
        assertFalse(publicJwk.contains("\"x5t_S256\""))
        val projected = Json.decodeFromString<Jwk>(publicJwk)
        assertEquals("provider-kid", projected.kid)
        assertEquals(listOf("leaf-certificate", "issuer-certificate"), projected.x5c?.toList())
        assertEquals("sha1-thumbprint", projected.x5t)
        assertEquals("sha256-thumbprint", projected.x5t_S256)
        assertNull(projected.d)
        assertNull(projected.k)
        assertNull(projected.x5u)
        val symmetricPublicJwkResult = symmetricKey.safePublicJwk()
        assertTrue(symmetricPublicJwkResult.isOk)
        assertNull(symmetricPublicJwkResult.value)
    }

    @Test
    fun registerCommandPersistsProviderPublicMetadataAndExternalOwnership() =
        runTest {
            val store = InMemoryKeyReferenceStore()
            val providerKey =
                ManagedKeyInfo.fromKeyInfo(
                    KeyInfo(
                        key =
                            Jwk(
                                kty = JwaKeyType.EC,
                                kid = "provider-ec-kid",
                                crv = JwaCurve.P_256,
                                x = "public-x",
                                y = "public-y",
                                d = "private-d",
                                x5c = arrayOf("leaf-certificate", "issuer-certificate"),
                                x5t = "sha1-thumbprint",
                                x5u = "https://provider.invalid/certificate",
                                x5t_S256 = "sha256-thumbprint",
                            ),
                        alias = "provider-ec-alias",
                        providerId = "provider-1",
                        x5c = arrayOf("leaf-certificate", "issuer-certificate"),
                    ),
                )

            val result = registerProviderKey(store, providerKey)

            assertTrue(result.isOk)
            assertEquals("provider-ec-alias", result.value.alias)
            assertEquals(Origin.EXTERNAL, result.value.origin)
            assertEquals(ResourceControlMode.EXTERNALLY_MANAGED, result.value.controlMode)

            val reloaded = store.findByAlias("tenant-1", "provider-ec-alias", "provider-1").value
            assertNotNull(reloaded)
            assertEquals("provider-ec-kid", reloaded.kid)
            assertEquals(Origin.EXTERNAL, reloaded.origin)
            assertEquals(ResourceControlMode.EXTERNALLY_MANAGED, reloaded.controlMode)
            assertEquals("provider-1", reloaded.providerId)
            assertEquals("provider-ec-alias", reloaded.alias)

            val publicJwk = Json.decodeFromString<Jwk>(reloaded.publicKeyJwk!!)
            assertEquals(arrayOf("leaf-certificate", "issuer-certificate").toList(), publicJwk.x5c!!.toList())
            assertEquals("sha1-thumbprint", publicJwk.x5t)
            assertEquals("sha256-thumbprint", publicJwk.x5t_S256)
            assertNull(publicJwk.x5u)
            assertNull(publicJwk.d)
        }

    @Test
    fun registerCommandStripsAllSupportedRsaPrivateFieldsAndRetainsX5Metadata() =
        runTest {
            val store = InMemoryKeyReferenceStore()
            val providerKey =
                ManagedKeyInfo.fromKeyInfo(
                    KeyInfo(
                        key =
                            Jwk(
                                kty = JwaKeyType.RSA,
                                kid = "provider-rsa-kid",
                                n = "modulus",
                                e = "exponent",
                                d = "private-d",
                                p = "private-p",
                                q = "private-q",
                                dP = "private-dp",
                                dQ = "private-dq",
                                qInv = "private-qinv",
                                x5c = arrayOf("rsa-leaf", "rsa-issuer"),
                                x5t = "rsa-sha1",
                                x5t_S256 = "rsa-sha256",
                                x5u = "https://provider.invalid/rsa-certificate",
                            ),
                        alias = "provider-rsa-alias",
                        providerId = "provider-1",
                        x5c = arrayOf("rsa-leaf", "rsa-issuer"),
                    ),
                )

            val result = registerProviderKey(store, providerKey)

            assertTrue(result.isOk)
            val reloaded = store.findByAlias("tenant-1", "provider-rsa-alias", "provider-1").value
            assertNotNull(reloaded)
            val publicJwk = Json.decodeFromString<Jwk>(reloaded.publicKeyJwk!!)
            assertNull(publicJwk.d)
            assertNull(publicJwk.p)
            assertNull(publicJwk.q)
            assertNull(publicJwk.dP)
            assertNull(publicJwk.dQ)
            assertNull(publicJwk.qInv)
            assertEquals(arrayOf("rsa-leaf", "rsa-issuer").toList(), publicJwk.x5c!!.toList())
            assertEquals("rsa-sha1", publicJwk.x5t)
            assertEquals("rsa-sha256", publicJwk.x5t_S256)
            assertNull(publicJwk.x5u)
        }

    @Test
    fun failedAsymmetricPublicJwkSanitizationAbortsBeforePersistence() =
        runTest {
            val store = InMemoryKeyReferenceStore()
            val malformedProviderKey =
                ManagedKeyInfo.fromKeyInfo(
                    KeyInfo(
                        key =
                            Jwk(
                                kty = JwaKeyType.EC,
                                kid = "malformed-ec-kid",
                                crv = JwaCurve.P_256,
                                x = " ",
                                y = "public-y",
                                d = "private-d",
                            ),
                        alias = "malformed-ec-alias",
                        providerId = "provider-1",
                    ),
                )

            val result = registerProviderKey(store, malformedProviderKey)

            assertTrue(result.isErr)
            assertEquals("KMS_PUBLIC_JWK_PROJECTION_FAILED", result.error.code)
            assertEquals("The provider public key could not be safely projected", result.error.message.defaultMessage)
            assertFalse(result.error.message.defaultMessage.contains("public-x"))
            assertTrue(store.findAll("tenant-1").value.isEmpty())
        }

    private suspend fun registerProviderKey(
        store: InMemoryKeyReferenceStore,
        providerKey: ManagedKeyInfoType<*>,
    ): IdkResult<RegisterKeyReferenceResponse, IdkError> {
        val execution = testSessionExecution()
        val command =
            RegisterKeyReferenceServiceCommandImpl(
                execution = execution,
                registrar = com.sphereon.crypto.key.persistence.impl.ManagedKeyReferenceRegistrar(store, execution),
                inspector =
                    object : ProviderKeyReferenceInspector {
                        override suspend fun inspect(
                            providerId: String,
                            alias: String,
                            kid: String?,
                        ): IdkResult<ManagedKeyInfoType<*>, IdkError> = Ok(providerKey)
                    },
            )
        return command.execute(
            RegisterKeyReferenceInput(
                providerId = providerKey.providerId,
                alias = providerKey.alias,
                kid = providerKey.kid,
            ),
        )
    }

    private fun testSessionExecution(): SessionExecution =
        object : SessionExecution {
            override val tenantId: String = "tenant-1"
            override val principalId: String = "principal-1"
            override val correlationId: String = "correlation-1"
            override val sessionContextManager: SessionContextManager get() = error("unused")
            override val sessionContext: SessionContext =
                object : SessionContext {
                    override val sessionId: String = "session-1"
                    override val context: UserContext =
                        object : UserContext {
                            override val id: String = "user-1"
                            override val tenant: TenantContextData =
                                object : TenantContextData {
                                    override val tenantId: String = "tenant-1"
                                }
                            override val principal: Any? = "principal-1"
                            override val secureDetails = null
                        }
                }
            override val log: SessionLogService = NoOpSessionLogService(sessionContext)
            override val interceptorChain: CommandLifecycleInterceptorChain = EmptyInterceptorChain
            override val conf: ContextConfig = NoOpContextConfig
        }

    private object NoOpContextConfig : ContextConfig {
        override val app: AppConfigService get() = error("unused")
        override val tenant: TenantConfigService get() = error("unused")
        override val principal: PrincipalConfigService get() = error("unused")
        override fun conf(level: ConfigLevel): ConfigService = error("unused")
    }

    private class NoOpSessionLogService(
        override val sessionContext: SessionContext,
    ) : SessionLogService {
        override val id: String = "register-key-reference-test-log"
        override val isEnabled: Boolean = false
        override val scope: IdkScope = IdkScope.SESSION
        override val logManager: SessionLogManager get() = throw NotImplementedError("unused")
        override suspend fun setConfig(config: LoggerConfig): LogService = this
        override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)
        override fun toAsync(): AsyncLogService = throw NotImplementedError("unused")
    }

    private class InMemoryKeyReferenceStore : KeyReferenceStore {
        private val records = linkedMapOf<String, KeyReferenceRecord>()

        override val ownershipHistoryCapability: KeyReferenceHistoryCapability = KeyReferenceHistoryCapability.DURABLE

        override suspend fun save(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> {
            records[record.id] = record
            return Ok(record)
        }

        override suspend fun upsert(record: KeyReferenceRecord): IdkResult<KeyReferenceRecord, IdkError> {
            val existing = records.values.firstOrNull {
                it.tenantId == record.tenantId &&
                    it.alias == record.alias &&
                    it.providerId == record.providerId &&
                    it.deletedAt == null
            }
            val stored = record.copy(id = existing?.id ?: record.id)
            records[stored.id] = stored
            return Ok(stored)
        }

        override suspend fun findById(tenantId: String, id: String): IdkResult<KeyReferenceRecord?, IdkError> =
            Ok(records[id]?.takeIf { it.tenantId == tenantId && it.deletedAt == null })

        override suspend fun findByKid(
            tenantId: String,
            kid: String,
            providerId: String?,
        ): IdkResult<KeyReferenceRecord?, IdkError> =
            Ok(records.values.firstOrNull {
                it.tenantId == tenantId &&
                    it.kid == kid &&
                    it.deletedAt == null &&
                    (providerId == null || it.providerId == providerId)
            })

        override suspend fun findByAlias(
            tenantId: String,
            alias: String,
            providerId: String?,
        ): IdkResult<KeyReferenceRecord?, IdkError> =
            Ok(records.values.firstOrNull {
                it.tenantId == tenantId &&
                    it.alias == alias &&
                    it.deletedAt == null &&
                    (providerId == null || it.providerId == providerId)
            })

        override suspend fun findAll(
            tenantId: String,
            filter: ManagedKeyReferenceFilter?,
        ): IdkResult<List<KeyReferenceRecord>, IdkError> =
            Ok(records.values.filter { it.tenantId == tenantId && it.deletedAt == null })

        override suspend fun delete(tenantId: String, alias: String, providerId: String): IdkResult<Boolean, IdkError> =
            softDelete { it.tenantId == tenantId && it.alias == alias && it.providerId == providerId }

        override suspend fun deleteByKid(tenantId: String, kid: String, providerId: String?): IdkResult<Boolean, IdkError> =
            softDelete { it.tenantId == tenantId && it.kid == kid && (providerId == null || it.providerId == providerId) }

        override suspend fun exists(tenantId: String, alias: String, providerId: String): IdkResult<Boolean, IdkError> =
            Ok(records.values.any { it.tenantId == tenantId && it.alias == alias && it.providerId == providerId && it.deletedAt == null })

        private fun softDelete(predicate: (KeyReferenceRecord) -> Boolean): IdkResult<Boolean, IdkError> {
            val existing = records.values.firstOrNull { it.deletedAt == null && predicate(it) } ?: return Ok(false)
            records[existing.id] = existing.copy(deletedAt = Clock.System.now())
            return Ok(true)
        }
    }
}
