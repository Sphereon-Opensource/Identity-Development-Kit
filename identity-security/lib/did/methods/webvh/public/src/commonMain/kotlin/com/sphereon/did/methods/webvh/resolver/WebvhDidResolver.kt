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

package com.sphereon.did.methods.webvh.resolver

import com.sphereon.did.resolver.DidResolver

/**
 * `DidResolver` specialization for the `did:webvh` method.
 *
 * The implementation lives in `:lib:did:methods:webvh:resolver` and is
 * picked up automatically by `DidResolverRegistryImpl` via Metro
 * multibinding (`@ContributesIntoSet`). Verifier-only assemblies inject
 * this interface to confirm the webvh method is supported in the deployment.
 */
interface WebvhDidResolver : DidResolver
