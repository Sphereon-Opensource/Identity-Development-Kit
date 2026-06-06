/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.credential.issuance.pipeline

import com.sphereon.core.compat.JsExportCompat

/**
 * Lifecycle status of an [IssuancePipelineSession] — the EDK pipeline's own, finer-grained state,
 * distinct from the issuer-core `IssuanceSessionStatus` and the REST `CredentialOfferSessionStatus`.
 *
 * `AWAITING_DEFERRED` and `AWAITING_APPROVAL` are EDK-pipeline-internal refinements: both surface
 * to the issuer core as its single `DEFERRED` value.
 */
@JsExportCompat
enum class IssuancePipelineStatus {
    /** Session created; no phase has run yet. */
    CREATED,

    /** A phase's sources are currently executing. */
    PHASE_EXECUTING,

    /** A phase finished; the session is between phases. */
    PHASE_COMPLETED,

    /** `/credential` returned 202; waiting for deferred attribute ingress. */
    AWAITING_DEFERRED,

    /** Required attributes are complete but an approver has not yet decided. */
    AWAITING_APPROVAL,

    /** All bindings complete and approved (if required); claims can be assembled. */
    READY,

    /** Issuance finished for every bound credential. */
    COMPLETED,

    /** The session failed terminally. */
    FAILED,

    /** The session passed its `expiresAt` without completing. */
    EXPIRED,
}
