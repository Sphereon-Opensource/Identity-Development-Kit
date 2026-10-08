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

package com.sphereon.oauth2.server.authorization.storage

import com.sphereon.core.api.IdkResult
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import kotlin.time.Instant

/**
 * Storage abstraction for RFC 8628 OAuth 2.0 Device Authorization Grant records.
 *
 * Each record represents an in-flight or completed device-authorization request created at the
 * `/device_authorization` endpoint. The record carries the opaque [DeviceAuthorizationRecord.deviceCode]
 * that the device polls with at `/token`, the human-typeable [DeviceAuthorizationRecord.userCode]
 * that the user enters at the verification URI, the requested grant scope, and the lifecycle
 * [DeviceAuthorizationState] driven by the verification UI (approval/denial) and the token
 * endpoint (consumption on successful exchange).
 *
 * Spec mapping:
 * - §3.1: device-authorization request creates a record in [DeviceAuthorizationState.PENDING].
 * - §3.3: verification UI transitions the record to [DeviceAuthorizationState.APPROVED] or
 *   [DeviceAuthorizationState.DENIED] and pins the resolved subject and auth-time.
 * - §3.4: `/token` consumes an [DeviceAuthorizationState.APPROVED] record exactly once via
 *   [DeviceAuthorizationStorage.consume].
 * - §3.5: `slow_down` / `authorization_pending` enforcement uses the per-record poll interval
 *   plus the last polled instant recorded via [DeviceAuthorizationStorage.recordPolledAt].
 *
 * **Tenancy partitioning contract**:
 *
 * IDK ships an in-memory implementation that is single-tenant: every caller into a given binding
 * shares the same device-authorization keyspace. Implementations backing a multi-tenant deployment
 * MUST partition both the `deviceCode` and `userCode` lookup namespaces by tenant so that tenant A
 * cannot resolve, approve, deny, or consume tenant B's records. EDK durable implementations
 * (Redis, SQL, Caffeine-with-namespacing, etc.) are responsible for this partitioning, typically
 * via a tenant-prefixed storage key, a per-tenant table partition, or a per-tenant storage
 * instance resolved through SessionScope. Failing to partition lets a hostile tenant guess
 * another tenant's user code (8 alphanumeric chars carry only ~41 bits of entropy and are spoken
 * aloud) and impersonate a device-flow user across tenant boundaries.
 *
 * Thread safety: implementations MUST be thread-safe; [consume] MUST be atomic so a concurrent
 * second `/token` poll cannot observe a partially-consumed record.
 */
interface DeviceAuthorizationStorage {
    /**
     * Persist a freshly-issued device-authorization record. Returns the persisted record on
     * success.
     *
     * The record is stored under both the `deviceCode` and `userCode` lookup keys; on conflict
     * the storage MUST reject the create with [AuthorizationServerError.StorageError] rather
     * than silently overwriting either key (collisions imply a CSPRNG fault).
     */
    suspend fun create(record: DeviceAuthorizationRecord): IdkResult<DeviceAuthorizationRecord, AuthorizationServerError.StorageError>

    /**
     * Look up a record by its opaque device code. Returns `null` only when no record exists for
     * the code. Records past their `expiresAt` are returned as-is so the caller (typically the
     * device-code grant verifier at `/token`) can transition the record to
     * [DeviceAuthorizationState.EXPIRED] and emit `expired_token` per RFC 8628 §3.5. Records in
     * [DeviceAuthorizationState.CONSUMED] are also returned so the verifier can detect replay
     * attempts and emit `invalid_grant`. Expired-record purging is handled out of band by a
     * periodic sweep, not on every read.
     */
    suspend fun findByDeviceCode(deviceCode: String): IdkResult<DeviceAuthorizationRecord?, AuthorizationServerError.StorageError>

    /**
     * Look up a record by its human-typeable user code. Used by the verification UI after the
     * user enters the code at the `verification_uri`. Returns `null` only when no record exists
     * for the code; expired and consumed records are surfaced so the UI can render an explicit
     * "code expired" / "code already used" message rather than a generic miss.
     */
    suspend fun findByUserCode(userCode: String): IdkResult<DeviceAuthorizationRecord?, AuthorizationServerError.StorageError>

    /**
     * Persist a state transition or approval-side metadata update on an existing record. The
     * caller MUST have read the record first; implementations MAY reject updates whose
     * `deviceCode` does not match an existing record with
     * [AuthorizationServerError.StorageError].
     *
     * Used to:
     * - flip [DeviceAuthorizationState.PENDING] to [DeviceAuthorizationState.APPROVED] / [DeviceAuthorizationState.DENIED] (verification UI),
     * - pin `approvedSub`, `approvedAuthTime`, `approvedSessionId`, `grantedScope` on approval,
     * - flip to [DeviceAuthorizationState.EXPIRED] when a periodic sweep detects an aged-out record.
     */
    suspend fun update(record: DeviceAuthorizationRecord): IdkResult<DeviceAuthorizationRecord, AuthorizationServerError.StorageError>

    /**
     * Atomically mark an approved record as [DeviceAuthorizationState.CONSUMED] at `/token`.
     *
     * RFC 8628 §3.4 requires single-use semantics: a successful token exchange invalidates the
     * device code. The record is retained in CONSUMED state through its existing `expiresAt`
     * lifetime so a duplicate poll for the same `device_code` resolves the CONSUMED record and
     * is rejected as `invalid_grant`. Without retention the AS could not distinguish replay
     * from a typo on a long-lived device code, so the response would degrade to the same error
     * the spec mandates anyway, but operators would lose audit visibility into replays. The
     * periodic [DeviceAuthorizationStorage] sweep purges CONSUMED records once their original
     * lifetime elapses; durable backings should mirror that with a TTL on the row.
     *
     * Implementations MUST be atomic so a concurrent second poll observes either the original
     * `APPROVED` record (which it then consumes) or the CONSUMED record (which it then rejects),
     * never a partial state.
     */
    suspend fun consume(deviceCode: String): IdkResult<Unit, AuthorizationServerError.StorageError>

    /**
     * Stamp the most recent poll time for `slow_down` enforcement (RFC 8628 §3.5). The token
     * endpoint calls this after every poll so the next poll can compare against the per-record
     * `intervalSeconds` and decide between `authorization_pending` and `slow_down`.
     */
    suspend fun recordPolledAt(
        deviceCode: String,
        instant: Instant,
    ): IdkResult<Unit, AuthorizationServerError.StorageError>
}

/**
 * RFC 8628 device-authorization record, pinned at issuance and updated through approval and
 * consumption. Field-level mapping to RFC 8628:
 *
 * - [deviceCode]: §3.2 `device_code`. Opaque, ~256-bit URL-safe random; the AS issues at
 *   `/device_authorization` and the device polls with at `/token`.
 * - [userCode]: §3.2 `user_code`. Human-typeable, alphanumeric with a `-` separator (e.g.
 *   `WDJB-MJHT`) so it can be spoken or typed on a phone with minimal ambiguity.
 * - [clientId]: §3.1 `client_id` from the device-authorization request.
 * - [scope]: §3.1 requested scope, before approval.
 * - [resource]: RFC 8707 `resource` parameter(s) carried through from the device-authorization
 *   request when the client constrained the grant to specific resources.
 * - [audience]: requested audience(s) carried through to the eventual access token.
 * - [state]: lifecycle position, see [DeviceAuthorizationState].
 * - [createdAt], [expiresAt]: §3.2 `expires_in` lifetime ceiling.
 * - [intervalSeconds]: §3.2 `interval` baseline for the device's polling cadence; can be bumped
 *   via `slow_down` (§3.5) by adding to the response value, not this stored field.
 * - [lastPolledAt]: most recent poll instant for `slow_down` enforcement.
 * - [approvedSub] / [approvedAuthTime] / [approvedSessionId]: pinned at the approval step from the
 *   verification-UI session so the access token, id_token, and back-channel logout (`sid`) are
 *   correctly populated when the device finally polls.
 * - [grantedScope]: scope actually granted by the user (may narrow from [scope]).
 */
data class DeviceAuthorizationRecord(
    val deviceCode: String,
    val userCode: String,
    val clientId: String,
    val scope: String? = null,
    val resource: List<String>? = null,
    val audience: List<String>? = null,
    val state: DeviceAuthorizationState = DeviceAuthorizationState.PENDING,
    val createdAt: Instant,
    val expiresAt: Instant,
    val intervalSeconds: Int,
    val lastPolledAt: Instant? = null,
    val approvedSub: String? = null,
    val approvedAuthTime: Instant? = null,
    val approvedSessionId: String? = null,
    val grantedScope: String? = null,
)

/**
 * Lifecycle state of a [DeviceAuthorizationRecord].
 *
 * - [PENDING]: created at `/device_authorization`, awaiting user action at the verification URI.
 * - [APPROVED]: user approved at the verification UI; awaits a poll at `/token` to consume.
 * - [DENIED]: user denied at the verification UI; subsequent polls return `access_denied`.
 * - [EXPIRED]: aged past `expiresAt` without approval; subsequent polls return `expired_token`.
 * - [CONSUMED]: an approved record was exchanged for a token at `/token` (terminal).
 */
enum class DeviceAuthorizationState {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED,
    CONSUMED,
}
