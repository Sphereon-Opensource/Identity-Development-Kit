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
 *
 */

package com.sphereon.trust.etsi.lote.model

import kotlinx.serialization.Serializable

/**
 * Policy or legal notice entry per ETSI TS 119 602.
 *
 * Can contain either a textual notice or a URI pointing to a policy document.
 */
@Serializable
data class PolicyOrLegalNoticeEntry(
    val lang: String? = null,
    val notice: String? = null,
    val noticeURI: String? = null,
)
