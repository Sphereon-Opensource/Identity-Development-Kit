/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.wallet.credential.BodyStorageKind
import com.sphereon.wallet.credential.BodyStorageRef
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.CredentialInstance
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.CredentialTypeRefKind
import com.sphereon.wallet.credential.CredentialTypeRefSource
import com.sphereon.wallet.credential.IdentifierRef
import com.sphereon.wallet.credential.KeyRef
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * A record keeps every instance an issuer delivered for one credential configuration, so after the configuration's vct moved
 * from v1 to v2 it holds a v1 and a v2 instance and its type references name both. A query for v2 must be answered with the v2
 * instance, never the older v1 one the verifier would reject.
 */
class Oid4vpQueryInstanceSelectionTest {
    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val v1 = "https://types.example/adl-achievement-v1"
    private val v2 = "https://types.example/adl-achievement-v2"
    private val record = record(
        instance("old-v1", v1, issuedAt = Instant.fromEpochSeconds(1_700_000_000)),
        instance("new-v2", v2, issuedAt = Instant.fromEpochSeconds(1_790_000_000)),
    )

    @Test
    fun aQueryIsAnsweredWithTheInstanceOfTheTypeItAsksFor() {
        assertEquals("new-v2", record.presentableOid4vpInstance(now, query(v2))?.id)
        assertEquals("old-v1", record.presentableOid4vpInstance(now, query(v1))?.id)
        assertEquals("new-v2", record.presentableOid4vpInstance(now, query(v1, v2))?.id, "the newest accepted instance wins")
    }

    @Test
    fun aRecordWithoutAnInstanceOfTheRequestedTypeAnswersNothing() {
        assertNull(record(instance("old-v1", v1)).presentableOid4vpInstance(now, query(v2)))
    }

    @Test
    fun withoutAQueryTheNewestPresentableInstanceIsChosen() {
        assertEquals("new-v2", record.presentableOid4vpInstance(now)?.id)
    }

    @Test
    fun anInstanceWithoutAReadableVctIsOnlyTheFallback() {
        assertEquals("opaque", record(instance("opaque", vct = null)).presentableOid4vpInstance(now, query(v2))?.id)
        val mixed = record(
            instance("opaque", vct = null, issuedAt = Instant.fromEpochSeconds(1_795_000_000)),
            instance("typed-v2", v2, issuedAt = Instant.fromEpochSeconds(1_700_000_000)),
        )
        assertEquals("typed-v2", mixed.presentableOid4vpInstance(now, query(v2))?.id, "an instance that answers by type wins")
    }

    private fun query(vararg vcts: String) = DcqlCredentialQuery(
        id = "adl_achievement",
        format = "dc+sd-jwt",
        meta = JsonObject(mapOf("vct_values" to JsonArray(vcts.map(::JsonPrimitive)))),
    )

    private fun sdJwt(vct: String?): String {
        val header = """{"alg":"ES256","typ":"dc+sd-jwt"}""".encodeToByteArray().encodeToBase64Url()
        val claims = if (vct == null) """{"iss":"https://issuer.example"}""" else """{"iss":"https://issuer.example","vct":"$vct"}"""
        return "$header.${claims.encodeToByteArray().encodeToBase64Url()}.c2ln~"
    }

    private fun record(vararg instances: CredentialInstance) = CredentialRecord(
        id = "record",
        walletUnitId = "wallet",
        issuerRef = IdentifierRef(IdentifierType("https"), "https://issuer.example"),
        format = CredentialFormat.SD_JWT_VC,
        credentialTypeRefs = setOf(v1, v2).mapTo(mutableSetOf()) {
            CredentialTypeRef(
                format = CredentialFormat.SD_JWT_VC,
                kind = CredentialTypeRefKind.SD_JWT_VCT,
                value = it,
                source = CredentialTypeRefSource.CREDENTIAL_PAYLOAD,
                primary = it == v2,
            )
        },
        instances = instances.toList(),
        createdAt = now,
        updatedAt = now,
    )

    private fun instance(id: String, vct: String?, issuedAt: Instant? = null) = CredentialInstance(
        id = id,
        walletUnitId = "wallet",
        credentialRecordId = "record",
        format = CredentialFormat.SD_JWT_VC,
        raw = sdJwt(vct),
        holderKeyRef = KeyRef(alias = "key-$id"),
        bodyStorageRef = BodyStorageRef(BodyStorageKind.WALLET_STORE, "wallet/$id/body"),
        lifecycleState = CredentialLifecycleState.ACTIVE,
        issuedAt = issuedAt,
        storedAt = now,
        updatedAt = now,
    )
}
