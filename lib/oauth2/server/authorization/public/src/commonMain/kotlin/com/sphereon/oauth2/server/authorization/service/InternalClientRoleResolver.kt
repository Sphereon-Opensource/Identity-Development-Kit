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

package com.sphereon.oauth2.server.authorization.service

import com.sphereon.core.compat.JsExportCompat

/**
 * Resolves the public client id assigned to a server-owned internal workload role.
 *
 * This deliberately exposes no credential material. Hosted-AS consumers such as an in-process
 * resource server need their caller identity for authorization, while the corresponding secret
 * may be held by an opaque secret authority and must never be projected into a typed config DTO.
 */
@JsExportCompat
fun interface InternalClientRoleResolver {
    fun resolveClientId(role: String): String?
}
