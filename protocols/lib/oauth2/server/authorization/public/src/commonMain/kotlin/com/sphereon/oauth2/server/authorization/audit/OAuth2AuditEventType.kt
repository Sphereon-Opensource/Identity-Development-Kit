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

import com.sphereon.core.api.session.isValidCommandId

/**
 * Domain audit event types emitted by the OAuth2 Authorization Server. Sit on top of the generic
 * per-command lifecycle events the transport-layer audit interceptor already records — these
 * carry OAuth-specific semantics (LOGIN, REFRESH_TOKEN_REUSE_DETECTED, USER_DISABLED_BY_*) that
 * a security analyst or SIEM rule needs to alert on, distinct from "command X started/finished".
 *
 * Each entry declares:
 *  - [eventName]: stable wire name carried in the persisted audit row's metadata. Operators
 *    write SIEM rules / dashboards against this string, so changes are breaking.
 *  - [category]: coarse grouping for filtering / authorization / retention policy.
 *  - [severity]: alerting tier. ERROR-severity events should page on-call.
 *  - [saveByDefault]: whether a fresh deployment persists this event without operator opt-in.
 *    Mirrors the per-event default-persist switch on mature IdPs so a deployment doesn't have
 *    to enumerate every type to get the security-critical ones in the audit table.
 *
 * Extension policy: add new entries instead of repurposing existing ones. The wire name is
 * append-only; renames break consumers downstream.
 */
enum class OAuth2AuditEventType(
    val eventName: String,
    val category: OAuth2AuditCategory,
    val severity: OAuth2AuditSeverity,
    val saveByDefault: Boolean,
) {
    /**
     * The end user successfully completed the AS login form (`POST /login`). Emitted from the
     * login-submit handler after [com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider]
     * returns success and the OIDC login session is materialized.
     */
    LOGIN("oauth2.login", OAuth2AuditCategory.AUTH, OAuth2AuditSeverity.INFO, saveByDefault = true),

    /**
     * The end user submitted credentials at `POST /login` but authentication failed (wrong
     * password, unknown user, locked account). The wire-visible error message is unified at
     * the UI layer (RFC 6749 §4.1.2.1 anti-enumeration), so the emitted event is the only
     * place the AS internally distinguishes which failure mode occurred — keep the
     * `error_subcode` metadata accurate.
     */
    LOGIN_ERROR("oauth2.login_error", OAuth2AuditCategory.AUTH, OAuth2AuditSeverity.WARN, saveByDefault = true),

    /**
     * RFC 6749 §10.4 / OAuth 2.1 refresh-token reuse detection: a previously-rotated refresh
     * token was presented again. The whole token family SHOULD be revoked at this point
     * because reuse means either an attacker captured the token before it rotated or the
     * client implementation is broken. ERROR-severity so on-call notices.
     */
    REFRESH_TOKEN_REUSE_DETECTED(
        "oauth2.refresh_token_reuse_detected",
        OAuth2AuditCategory.TOKEN,
        OAuth2AuditSeverity.ERROR,
        saveByDefault = true,
    ),

    /**
     * Brute-force protection tripped: the user's credential is locked for a finite window
     * (defined by realm config). Emitted once per lockout transition, not per failed attempt.
     * Pairs with [LOGIN_ERROR] which is per-attempt.
     */
    USER_DISABLED_BY_TEMPORARY_LOCKOUT(
        "oauth2.user_disabled_temporary_lockout",
        OAuth2AuditCategory.AUTH,
        OAuth2AuditSeverity.WARN,
        saveByDefault = true,
    ),

    /**
     * Brute-force protection escalated to permanent lock. The credential needs explicit
     * operator action (unlock command) before login is possible again. ERROR-severity to
     * surface as a paging event.
     */
    USER_DISABLED_BY_PERMANENT_LOCKOUT(
        "oauth2.user_disabled_permanent_lockout",
        OAuth2AuditCategory.AUTH,
        OAuth2AuditSeverity.ERROR,
        saveByDefault = true,
    ),

    /**
     * Authorization-code grant succeeded at `POST /token`. The AS exchanged a one-time
     * authorization code (from a `/authorize` round-trip) for an access token (and optionally
     * a refresh token + id token). Carries the `client_id` of the redeeming client and the
     * `subject` resolved from the consumed code. Operators correlate this with the preceding
     * [LOGIN] event via the subject + login_session_id metadata to reconstruct the full flow.
     */
    CODE_TO_TOKEN("oauth2.code_to_token", OAuth2AuditCategory.TOKEN, OAuth2AuditSeverity.INFO, saveByDefault = true),

    /**
     * Authorization-code grant rejected at `POST /token`. Common subcodes: `invalid_grant`
     * (code unknown / consumed / expired / redirect-uri mismatch / wrong client_id), PKCE
     * verifier mismatch, DPoP jkt-mismatch. Subject is intentionally omitted because the
     * caller's authorization to act for any subject is exactly what the failure rejects.
     */
    CODE_TO_TOKEN_ERROR(
        "oauth2.code_to_token_error",
        OAuth2AuditCategory.TOKEN,
        OAuth2AuditSeverity.WARN,
        saveByDefault = true,
    ),

    /**
     * Refresh-token grant succeeded at `POST /token`. The AS exchanged a refresh token for a
     * fresh access token (and optionally a rotated refresh token). Subject is the original
     * `sub` from the refresh-token row. Pair with [REFRESH_TOKEN_REUSE_DETECTED] which fires
     * when the SAME chain is re-presented after rotation.
     */
    REFRESH_TOKEN("oauth2.refresh_token", OAuth2AuditCategory.TOKEN, OAuth2AuditSeverity.INFO, saveByDefault = true),

    /**
     * Refresh-token grant rejected at `POST /token`. Distinct from [REFRESH_TOKEN_REUSE_DETECTED]:
     * this fires for unknown / expired / scope-narrowing-violation / DPoP-mismatch / client-
     * mismatch failures where the presented token was never valid for this exchange. Reuse
     * detection (the previously-rotated chain re-presented) gets its own ERROR-severity event.
     */
    REFRESH_TOKEN_ERROR(
        "oauth2.refresh_token_error",
        OAuth2AuditCategory.TOKEN,
        OAuth2AuditSeverity.WARN,
        saveByDefault = true,
    ),

    /**
     * RFC 7662 token introspection succeeded at `POST /introspect`. The metadata MUST include
     * the `active` flag the AS returned to the caller (resource servers use the introspection
     * endpoint to learn whether a presented token is still valid; an `active=false` response
     * is a successful introspection of a revoked / expired token, not an introspection failure).
     */
    INTROSPECT_TOKEN(
        "oauth2.introspect_token",
        OAuth2AuditCategory.TOKEN,
        OAuth2AuditSeverity.INFO,
        saveByDefault = true,
    ),

    /**
     * Token introspection failed before the AS could decide active/inactive. Typically client
     * authentication failure, missing `token` parameter, or storage backend error.
     */
    INTROSPECT_TOKEN_ERROR(
        "oauth2.introspect_token_error",
        OAuth2AuditCategory.TOKEN,
        OAuth2AuditSeverity.WARN,
        saveByDefault = true,
    ),

    /**
     * RFC 7009 token revocation succeeded at `POST /revoke`. Per RFC 7009 §2.2 the AS returns
     * 200 OK whether or not the token was active at submission time, so this event is emitted
     * for every authenticated revocation attempt regardless of whether anything was actually
     * revoked. Metadata records the `token_type_hint` the caller supplied (if any).
     */
    REVOKE_GRANT(
        "oauth2.revoke_grant",
        OAuth2AuditCategory.TOKEN,
        OAuth2AuditSeverity.INFO,
        saveByDefault = true,
    ),

    /**
     * Token revocation rejected before the AS reached the revoke step. Almost always client-
     * authentication failure (RFC 7009 §2.1 requires authentication identical to the token
     * endpoint). Lower severity than [INTROSPECT_TOKEN_ERROR] only because revocation has no
     * side-effect on success or failure.
     */
    REVOKE_GRANT_ERROR(
        "oauth2.revoke_grant_error",
        OAuth2AuditCategory.TOKEN,
        OAuth2AuditSeverity.WARN,
        saveByDefault = true,
    ),
    ;

    /**
     * Canonical command ID used by the shared command-audit pipeline. [eventName] remains the
     * append-only OAuth/SIEM wire vocabulary and is intentionally not changed.
     */
    val commandId: String =
        "oauth2.audit.${eventName.removePrefix("oauth2.").replace('_', '-')}".also {
            require(isValidCommandId(it)) {
                "OAuth2 audit command ID derived from '$eventName' is invalid: '$it'"
            }
        }
}

/**
 * Coarse domain grouping for [OAuth2AuditEventType]. Used by retention policy and SIEM
 * rule scoping (e.g. "alert on any TOKEN-category ERROR-severity event").
 */
enum class OAuth2AuditCategory {
    AUTH,
    TOKEN,
    ADMIN,
    FEDERATION,
}

/**
 * Severity tiers for [OAuth2AuditEventType], independent of HTTP status. INFO is for routine
 * success events, WARN for client-error rejections that do not require operator attention,
 * ERROR for events that should page on-call.
 */
enum class OAuth2AuditSeverity {
    INFO,
    WARN,
    ERROR,
}
