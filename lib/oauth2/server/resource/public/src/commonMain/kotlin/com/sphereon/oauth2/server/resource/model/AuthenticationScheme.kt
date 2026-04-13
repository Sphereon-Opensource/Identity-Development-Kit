/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.oauth2.server.resource.model

// Re-export from common for backward compatibility
// This enum is now in oauth2-common since it's used by clients, AS, and RS
@Deprecated(
    "Use com.sphereon.oauth2.common.model.AuthenticationScheme instead",
    ReplaceWith("com.sphereon.oauth2.common.model.AuthenticationScheme")
)
typealias AuthenticationScheme = com.sphereon.oauth2.common.model.AuthenticationScheme
