/*
 * Â© 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.rest.impl.event

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

internal fun JsonObjectBuilder.putSessionEventIdentity(
    protocolSessionId: String,
    instanceId: String,
    oldState: String? = null,
    newState: String? = null,
    templateId: String? = null,
    creationSnapshot: JsonObject? = null,
    currentResult: JsonObject? = null,
) {
    require(protocolSessionId.isNotBlank()) { "protocolSessionId must not be blank" }
    require(instanceId.isNotBlank()) { "instanceId must not be blank" }
    put("protocolSessionId", protocolSessionId)
    put("instanceId", instanceId)
    oldState?.let { put("oldState", it) }
    newState?.let { put("newState", it) }
    templateId?.let { put("templateId", it) }
    creationSnapshot?.let { put("creationSnapshot", it) }
    currentResult?.let { put("currentResult", it) }
}
