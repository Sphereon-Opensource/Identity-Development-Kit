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

package com.sphereon.core.api.repository

/**
 * Marker interface for repositories that expose only read-only methods
 * — the **Query** side of CQRS.
 *
 * In deployments with read replicas configured, implementations resolve
 * their database driver via the routing layer's READ access mode so reads
 * are routed to a replica. When a per-call CQRS override forces WRITE
 * access (for `ConsistencyHint.ReadAfterWrite`), implementations honour
 * the override and route to the primary instead.
 *
 * The EDK startup validator (`ReadCommandRepoValidator` in `lib-cqrs-api`)
 * flags any READ-tagged `ServiceCommand` that injects a [WriteRepository]
 * in violation of CQRS separation.
 *
 * Lives in IDK so in-memory IDK stores can adopt the convention without
 * pulling EDK persistence machinery.
 */
interface QueryRepository
