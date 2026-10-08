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

package com.sphereon.oauth2.server.authorization.audit

/**
 * SPI for emitting OAuth2 domain audit events from the IDK authorization-server commands. The
 * IDK is KMP and cannot depend on the EDK audit pipeline directly; downstream EDK / VDX modules
 * provide a concrete implementation that bridges to whatever audit sink the deployment wires
 * (EDK `AuditEventSink`, a Kafka producer, a SIEM HTTP collector, etc.). The IDK itself ships
 * the [NoOpOAuth2AuditEmitter] default so consumers without an audit pipeline still compile and
 * run.
 *
 * Why a dedicated SPI rather than reusing the per-command audit interceptor: the existing
 * transport-layer audit captures generic command lifecycle (STARTED / SUCCEEDED / FAILED). It
 * cannot represent semantic OAuth events (LOGIN, REFRESH_TOKEN_REUSE_DETECTED, ...) that span
 * multiple commands or carry OAuth-specific metadata (client_id, grant_type, scope). This
 * emitter is the seam where business-meaningful security events leave the AS.
 *
 * Implementations MUST be safe to call from any coroutine context and SHOULD return promptly:
 * audit emission is on the hot path of token issuance and login, so blocking IO must be
 * dispatched off-thread by the implementation, not by the caller.
 */
interface OAuth2AuditEmitter {
    /**
     * Emit a single [OAuth2AuditEventType] occurrence. Caller responsibilities:
     *  - Pass [tenantId] from the resolved session execution context. Audit implementations must
     *    never derive ownership from OAuth client input, request metadata, or an anonymous caller.
     *  - Pass [clientId] when known. Without it, SIEM rules that pivot on client cannot fire.
     *  - Pass [subject] only when the event semantically attaches to a user (LOGIN_SUCCESS yes,
     *    pre-credential anonymous failure no).
     *  - For *_ERROR variants, populate [errorCode] with the wire-visible OAuth2 error code
     *    (`invalid_grant`, `invalid_request`, ...) so downstream rules can correlate against
     *    HTTP responses. Free-text [errorMessage] is for analyst eyeballs only.
     *  - Use [metadata] for OAuth-specific fields the type implies but the parameters do not
     *    cover: `grant_type`, `scope`, `response_type`, `acr`, `amr`, etc. Keys SHOULD be
     *    snake_case to match the wire vocabulary.
     *
     * Implementations MUST NOT throw; emission is best-effort and a downstream failure must
     * not propagate into the OAuth flow.
     */
    suspend fun emit(
        type: OAuth2AuditEventType,
        tenantId: String,
        clientId: String? = null,
        subject: String? = null,
        metadata: Map<String, String> = emptyMap(),
        errorCode: String? = null,
        errorMessage: String? = null,
    )
}

/**
 * No-op default. Wired by IDK consumers that do not have an audit pipeline. Drops every event
 * silently. Production deployments override via DI binding (EDK `AuditLogServiceSinkGraph` or
 * equivalent).
 */
object NoOpOAuth2AuditEmitter : OAuth2AuditEmitter {
    override suspend fun emit(
        type: OAuth2AuditEventType,
        tenantId: String,
        clientId: String?,
        subject: String?,
        metadata: Map<String, String>,
        errorCode: String?,
        errorMessage: String?,
    ) = Unit
}
