/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.conf.yaml

import com.sphereon.core.api.conf.PropertySource

/**
 * Property source backed by YAML configuration files.
 *
 * Follows the same scoped-directory authoring model as properties files:
 *
 * ```
 * config/
 *   application.yml              # APP scope base
 *   application-{profile}.yml    # APP scope profile override
 *   tenant/{tenantId}/
 *     tenant.yml                 # TENANT scope base
 *     tenant-{profile}.yml       # TENANT scope profile override
 *     principal/{principalId}/
 *       principal.yml            # PRINCIPAL scope base
 *       principal-{profile}.yml  # PRINCIPAL scope profile override
 * ```
 *
 * YAML files use bare keys only (no `sphereon.app.*` prefixes).
 * Keys are flattened to dot-notation and normalized.
 * Protection prefixes (final., protected.) are parsed and stored as metadata.
 */
interface YamlPropertySource : PropertySource<MutableMap<String, Any>>
