/*
 * Â© 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vp.universal.impl.event

import com.sphereon.openid.oid4vp.verifier.model.Oid4vpSessionIdentity
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
    put("protocolSessionId", Oid4vpSessionIdentity.normalize("protocolSessionId", protocolSessionId))
    put("instanceId", Oid4vpSessionIdentity.normalize("instanceId", instanceId))
    oldState?.let { put("oldState", it) }
    newState?.let { put("newState", it) }
    templateId?.let { put("templateId", it) }
    creationSnapshot?.let { put("creationSnapshot", it) }
    currentResult?.let { put("currentResult", it) }
}
