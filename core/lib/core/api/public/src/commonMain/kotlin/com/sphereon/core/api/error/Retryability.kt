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

package com.sphereon.core.api.error

import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Classification of an [IdkErrorType] for retry-decision purposes.
 *
 * The runtime combines this with the command's [com.sphereon.core.api.service.contract.ExecutionTraits.isIdempotent]
 * to decide whether a failed call should be retried. Retry happens only when the
 * command is idempotent AND the error is [TRANSIENT] (or [CONDITIONAL] with the
 * specific condition met).
 *
 * Conservative default: [NONE]. Authors must explicitly opt errors into retry
 * eligibility via an override of [IdkErrorType.retryability].
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("Retryability", exact = true)
@JsExportCompat
enum class Retryability {
    /** Retry will not help. Default. */
    NONE,

    /** Likely to succeed on a subsequent attempt (network blip, lock conflict, rate limit). */
    TRANSIENT,

    /** May be retryable depending on caller-side context (e.g. only after refreshing credentials). */
    CONDITIONAL,
}
