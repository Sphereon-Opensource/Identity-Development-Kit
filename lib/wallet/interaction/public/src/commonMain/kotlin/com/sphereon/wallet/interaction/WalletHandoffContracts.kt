/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.wallet.interaction

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Hand-written wire contracts for the credential hand-off session family defined in
 * wallet-interaction-components.yml. These are the counterparts the conformance suite
 * (`ContractConformanceTestWidened`) compares against the generated models: same names,
 * same enum wire values, same property names.
 *
 * Enforcement summary that this family encodes: phases advance only server side, snapshots
 * and stream events are served from the store read model only, and role projections omit
 * rather than hide. The raw exchange URI appears in no type here; it travels only through
 * [HandoffSessionApiConstants] consume endpoint semantics.
 */

/** Life cycle phase of a hand-off session. `connected` is the wire value; consumers map it into their own vocabulary at their port. */
@Serializable
enum class HandoffSessionPhase {
    @SerialName("waiting")
    WAITING,

    @SerialName("connected")
    CONNECTED,

    @SerialName("succeeded")
    SUCCEEDED,

    @SerialName("failed")
    FAILED,

    @SerialName("expired")
    EXPIRED,
}

/** Role a bound screen plays in a hand-off session. */
@Serializable
enum class HandoffScreenRole {
    @SerialName("operator")
    OPERATOR,

    @SerialName("display")
    DISPLAY,

    @SerialName("conduit")
    CONDUIT,
}

/** Delivery channel a session exposes to an eligible role. */
@Serializable
enum class HandoffChannelKind {
    @SerialName("copy_link")
    COPY_LINK,

    @SerialName("open_wallet_app")
    OPEN_WALLET_APP,

    @SerialName("send_email")
    SEND_EMAIL,
}

/** What the stage area of a screen renders for the current phase. */
@Serializable
enum class HandoffSummaryKind {
    @SerialName("qr")
    QR,

    @SerialName("countdown")
    COUNTDOWN,

    @SerialName("outcome")
    OUTCOME,
}

/** Where a configured note slot sits relative to the stage content. */
@Serializable
enum class HandoffNotePosition {
    @SerialName("above")
    ABOVE,

    @SerialName("below")
    BELOW,
}

/** One operator-configured text slot above or below the stage content. */
@Serializable
data class HandoffNoteSlot(
    val position: HandoffNotePosition,
    val text: String,
)

/** View-definition slots a renderer fills for one role. */
@Serializable
data class HandoffViewSlots(
    val summaryKind: HandoffSummaryKind,
    val notes: List<HandoffNoteSlot> = emptyList(),
    val labels: Map<String, String> = emptyMap(),
)

/** Configured post-success redirect target with its countdown duration. */
@Serializable
data class HandoffRedirectTarget(
    val uri: String,
    val countdownSeconds: Long? = null,
)

/**
 * Terminal outcome detail. kind and animationKind are safe for every role. code and message are
 * operator-facing resolution detail and are stripped server side from display and conduit
 * projections by omission. No claim data of any kind appears here at any role.
 */
@Serializable
data class HandoffOutcomeDetail(
    val kind: Kind,
    val animationKind: AnimationKind,
    val code: String? = null,
    val message: String? = null,
) {
    @Serializable
    enum class Kind {
        @SerialName("completed")
        COMPLETED,

        @SerialName("failed")
        FAILED,

        @SerialName("cancelled")
        CANCELLED,

        @SerialName("expired")
        EXPIRED,
    }

    @Serializable
    enum class AnimationKind {
        @SerialName("success")
        SUCCESS,

        @SerialName("failure")
        FAILURE,

        @SerialName("neutral")
        NEUTRAL,
    }
}

/** Presence of one bound screen. Stale screens stay listed so an operator can see them. */
@Serializable
data class HandoffPresenceEntry(
    val screenId: String,
    val label: String? = null,
    val role: HandoffScreenRole,
    val lastSeenAtEpochSeconds: Long,
    val stale: Boolean,
)

/**
 * What one role may see and do for the session. A display projection carries NO link text, NO
 * email action channel, and NO result claim data; the raw exchange URI appears in no projection
 * at any role. Client hiding is convenience only and is never the boundary.
 */
@Serializable
data class HandoffScreenProjection(
    val role: HandoffScreenRole,
    val enabledChannels: List<HandoffChannelKind> = emptyList(),
    val views: HandoffViewSlots,
    val presence: List<HandoffPresenceEntry> = emptyList(),
)

/**
 * Server-owned state of a credential hand-off session. revision is monotonic per session and
 * doubles as the snapshot revision. On expiry the correlationId is invalidated upstream and
 * regenerate starts a successor linked through supersedesSessionId and successorSessionId.
 */
@Serializable
data class HandoffSessionState(
    val sessionId: String,
    val correlationId: String,
    val phase: HandoffSessionPhase,
    val revision: Long = 0L,
    val createdAtEpochSeconds: Long? = null,
    val expiresAtEpochSeconds: Long? = null,
    val redirectTarget: HandoffRedirectTarget? = null,
    val outcome: HandoffOutcomeDetail? = null,
    val supersedesSessionId: String? = null,
    val successorSessionId: String? = null,
)

/**
 * Point-in-time read model served from the session store only. viewer is the projection for the
 * caller's role; enforcement by omission applies to everything reachable from this document.
 */
@Serializable
data class HandoffSessionSnapshot(
    val state: HandoffSessionState,
    val viewer: HandoffScreenProjection,
)

/** Starts a hand-off session wrapping one offer or request run for a business wallet unit. */
@Serializable
data class StartHandoffSessionRequest(
    val walletUnitId: String,
    val interactionSessionId: String? = null,
    val ttlSeconds: Int? = null,
    val redirectTarget: HandoffRedirectTarget? = null,
    val notes: List<HandoffNoteSlot> = emptyList(),
    val labels: Map<String, String> = emptyMap(),
)

/** The started session seen through the starting caller's role projection. */
@Serializable
data class StartHandoffSessionResult(
    val snapshot: HandoffSessionSnapshot,
)

/** Result of regenerating: the returned snapshot belongs to the successor session. */
@Serializable
data class RegenerateHandoffSessionResult(
    val snapshot: HandoffSessionSnapshot,
)

/**
 * Binds the authenticated screen to a live session. The caller authenticates with an access
 * token its owning OAuth2 authorization server minted for the hand-off audience.
 */
@Serializable
data class BindHandoffScreenRequest(
    val sessionId: String,
)

/** The session snapshot for the freshly bound screen's role. */
@Serializable
data class BindHandoffScreenResult(
    val snapshot: HandoffSessionSnapshot,
)

/** Presence heartbeat from an authenticated, already bound screen. */
@Serializable
data class HeartbeatHandoffScreenRequest(
    val sessionId: String,
)

/** Acknowledged presence. stale reports whether the threshold was crossed before this heartbeat landed. */
@Serializable
data class HeartbeatHandoffScreenResult(
    val lastSeenAtEpochSeconds: Long,
    val stale: Boolean,
)

/** The guarded exchange URI for this session on its one legitimate outbound path. Never logged. */
@Serializable
data class ConsumeHandoffExchangeUriResult(
    val uri: String,
)

/** Recorded hand-off session event kinds replayed on the events stream. */
@Serializable
enum class HandoffSessionEventType {
    @SerialName("phase_changed")
    PHASE_CHANGED,

    @SerialName("screen_joined")
    SCREEN_JOINED,

    @SerialName("screen_left")
    SCREEN_LEFT,

    @SerialName("expiry_imminent")
    EXPIRY_IMMINENT,

    @SerialName("terminal_reached")
    TERMINAL_REACHED,
}

/**
 * One recorded hand-off session event. On the events stream this object is the SSE data payload,
 * the SSE id field carries sequence, and the event name is handoff-session-event. Served from the
 * session store read model only.
 */
@Serializable
data class HandoffSessionEvent(
    val sequence: Long,
    val type: HandoffSessionEventType,
    val occurredAtEpochSeconds: Long,
    val sessionId: String,
    val phase: HandoffSessionPhase? = null,
    val screen: HandoffPresenceEntry? = null,
    val state: HandoffSessionState? = null,
    val successorSessionId: String? = null,
)
