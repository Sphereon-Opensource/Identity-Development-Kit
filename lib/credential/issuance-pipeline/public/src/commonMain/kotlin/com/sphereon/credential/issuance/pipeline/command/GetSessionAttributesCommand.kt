/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.credential.issuance.pipeline.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Reads back the attribute values accumulated in a pipeline session's bag, plus the names of
 * any lookup keys that have been promoted to an attribute path.
 *
 * Returns attribute data records projected to JSON and the names (NOT values) of promoted
 * lookup keys. Lookup key values are PII and are never returned over REST.
 */
interface GetSessionAttributesCommand : ServiceCommand<GetSessionAttributesArgs, GetSessionAttributesResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ

    companion object {
        const val COMMAND_ID: String = "issuance.pipeline.get-session-attributes"
    }
}

@Serializable
data class GetSessionAttributesArgs(
    /** The session whose attributes are read. */
    val correlationId: String,
)

@Serializable
data class GetSessionAttributesResult(
    /**
     * The effective attribute data records from the session bag, keyed by attribute path string.
     * Only records whose value is plain attribute data are included; blob / key / evidence
     * references have no direct JSON form and are omitted.
     */
    val attributes: Map<String, JsonElement>,
    /**
     * The names of lookup keys that have a non-null promotedToAttributePath: i.e. the keys
     * that will be emitted as attributes during credential assembly. The key VALUES are omitted:
     * they are PII and are never returned in the clear over REST.
     */
    val promotedLookupKeyNames: List<String>,
)
