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
 * Marker interface for repositories that expose mutating methods
 * — the **Command** side of CQRS.
 *
 * Implementations always resolve their database driver via the WRITE
 * access mode — the primary endpoint — regardless of whether read
 * replicas are configured.
 *
 * Lives in IDK alongside [QueryRepository] for symmetry; consumed by
 * the EDK validator in `lib-cqrs-api`.
 */
interface WriteRepository
