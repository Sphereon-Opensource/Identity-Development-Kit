/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.vctValues
import com.sphereon.sdjwt.vc.SdJwtVcClaims
import com.sphereon.wallet.credential.CredentialInstance
import com.sphereon.wallet.credential.CredentialRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * The instance of [this] record to present for [query]: the newest presentable instance whose own type the query accepts.
 *
 * A record collects every instance an issuer delivered for one credential configuration, and a configuration's type can
 * move on (a design version changes the vct) while older instances stay in the record. The record's type references are the
 * union over its instances, so a query can match a record through one instance and must then be answered with that one: an
 * SD-JWT VC instance whose own `vct` claim is in the query's `vct_values` is preferred, and one whose `vct` names another
 * type is never presented for it. An instance whose `vct` cannot be read is used only when no instance answers by type.
 * Other formats, and a query without `vct_values`, accept every presentable instance. Newest means latest issuance, then
 * latest storage.
 */
internal fun CredentialRecord.presentableOid4vpInstance(now: Instant, query: DcqlCredentialQuery): CredentialInstance? {
    val presentable = instances.filter { it.isPresentableOid4vpInstance(now) }
    val byType = presentable.groupBy { it.typeMatch(query) }
    return (byType[TypeMatch.ANSWERS] ?: byType[TypeMatch.UNKNOWN])?.newest()
}

private enum class TypeMatch { ANSWERS, UNKNOWN, OTHER_TYPE }

/** How this instance's own type relates to [query]: it answers it, it names another type, or it cannot be told. */
private fun CredentialInstance.typeMatch(query: DcqlCredentialQuery): TypeMatch {
    if (!format.isSdJwt) return TypeMatch.ANSWERS
    val accepted = query.vctValues() ?: return TypeMatch.ANSWERS
    val vct = sdJwtIssuerPayload()?.let(SdJwtVcClaims::vct) ?: return TypeMatch.UNKNOWN
    return if (vct in accepted) TypeMatch.ANSWERS else TypeMatch.OTHER_TYPE
}

private fun List<CredentialInstance>.newest(): CredentialInstance? =
    maxWithOrNull(compareBy<CredentialInstance>({ it.issuedAt ?: Instant.DISTANT_PAST }, { it.storedAt }))

/** The payload of the issuer-signed JWT of an SD-JWT instance, or null when the instance has no readable raw form. */
private fun CredentialInstance.sdJwtIssuerPayload(): JsonObject? {
    val issuerJwt = raw?.substringBefore('~')?.takeIf { it.isNotBlank() } ?: return null
    val segments = issuerJwt.split('.')
    if (segments.size != 3) return null
    return runCatching { Json.parseToJsonElement(segments[1].decodeFromBase64Url().decodeToString()) as? JsonObject }.getOrNull()
}
